package com.reactlibrary

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.Nullable
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule.RCTDeviceEventEmitter
import java.util.*


class BLECoreModule(private val reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    private val bluetoothManager: BluetoothManager = reactContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    init {
        if (bluetoothManager.adapter == null) throw Exception("Bluetooth Adapter is null!")
    }

    private val adapter: BluetoothAdapter = bluetoothManager.adapter

    // PERIPHERAL
    private var gattServer: GattServer? = null
    private var advertiser: Advertiser? = null

    //  CENTRAL
    private var scanner: Scanner? = null

    private var discoveredDevices: MutableMap<Int, BLEPeripheral> = mutableMapOf()
    private var promises: MutableMap<Pair<BLEFunction, Int>, Promise> = mutableMapOf()
    private var receivedRequests: MutableMap<Int, BLEReadRequest> = mutableMapOf()

    private var scanningUUIDs = mutableListOf<UUID>()
    private var advertisingUUIDs = mutableListOf<UUID>()

    private val callbacks = BLECoreCallbacks(reactContext, scanner, advertiser, gattServer)
    private fun sendEvent(eventName: String, @Nullable params: WritableMap) {
        reactContext
                .getJSModule(RCTDeviceEventEmitter::class.java)
                .emit(eventName, params)
    }

    private fun getPeripheral(peripheralId: Int, promise: Promise? = null): BLEPeripheral {
        val peripheral = discoveredDevices[peripheralId]
        if (peripheral == null) {
            promise?.reject("E_UNKNOWN_PERIPHERAL", "Attempted to get an unknown and undiscovered peripheral!", null)
            throw Throwable("E_UNKNOWN_PERIPHERAL")
        }

        return peripheral
    }

    override fun getName(): String {
        return "BLECore"
    }

    // TODO: ENsure all the promises on iOS end are prmises here too, and ensure all the rejections and resolves are there
    //  TODO: Headless
    //  TODO: Return status codes instead of booleans for each specific scenario
    //  TODO: Require this to be called first with packageManager passed in

    @ReactMethod
    fun _initialize(roles: ReadableArray, options: ReadableArray, promise: Promise) {
        for (i in 0 until roles.size()) {
            when (roles.getInt(i)) {
                GenericAccessProfileRole.PERIPHERAL.ordinal -> {
                    print("GenericAccessProfileRole.PERIPHERAL")
                    gattServer = GattServer()
                    advertiser = Advertiser()
                }

                GenericAccessProfileRole.CENTRAL.ordinal -> {
                    print("GenericAccessProfileRole.CENTRAL")
                    scanner = Scanner()
                }
            }
        }

        promise.resolve(null)
    }

    @ReactMethod
    fun _startScanning(serviceUUIDs: ReadableArray, options: ReadableMap) {
        val UUIDs = mutableListOf<UUID>()
        for (i in 0 until serviceUUIDs.size()) {
            UUIDs.add(UUID.fromString(serviceUUIDs.getString(i)))
        }

        scanningUUIDs = UUIDs
        scanner?.start(UUIDs, callbacks.ScanLECallback(UUIDs, options))
    }

    @ReactMethod
    fun _startAdvertising(services: ReadableArray) {
        val UUIDs = mutableListOf<UUID>()
        for (i in 0 until services.size()) {
            val bleService = BLEService(services.getMap(i) as ReadableMap)
            gattServer?.addService(bleService)
            UUIDs.add(bleService.uuid)
        }

        advertisingUUIDs = UUIDs
        gattServer?.start()
        advertiser?.start(AdvertiseSettings.ADVERTISE_MODE_BALANCED, true, 0, AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM, advertisingUUIDs)
    }

    @ReactMethod
    fun _connectToPeripheral(peripheralId: Int, options: ReadableMap, promise: Promise) {
        val peripheral = getPeripheral(peripheralId, promise)
        val mBluetoothGatt = peripheral.device.connectGatt(reactContext, false, callbacks.GattClientLECallback(), BluetoothDevice.TRANSPORT_LE)
        if (mBluetoothGatt == null) {
            promise.reject("E_CONNECT_FAILED", "Failed connecting to peripheral!", null)
            advertiser?.start(AdvertiseSettings.ADVERTISE_MODE_BALANCED, true, 0, AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM, advertisingUUIDs)
            scanner?.start(scanningUUIDs)
            return
        }

        peripheral.setGatt(mBluetoothGatt)
        promise.resolve(null)
    }

    @ReactMethod
    fun _discoverPeripheralServices(peripheralId: Int, serviceUUIDs: ReadableArray, promise: Promise) {
        val peripheral = getPeripheral(peripheralId, promise)
        val gatt = peripheral.getGatt()

        if (gatt.discoverServices()) promises[Pair(BLEFunction._discoverPeripheralServices, peripheralId)] = promise
        else return promise.reject("E_DISCOVER_FAILED", "Failed attempted to discover peripheral's services!", null)
    }

    @ReactMethod
    fun  _discoverPeripheralCharacteristics(peripheralId: Int, serviceUUID: String, characteristicUUIDs: ReadableArray, promise: Promise) {
        val peripheral = getPeripheral(peripheralId, promise)
        val gatt = peripheral.getGatt()

        val UUIDs = mutableListOf<UUID>()
        for (i in 0 until characteristicUUIDs.size()) {
            UUIDs.add(UUID.fromString(characteristicUUIDs.getString(i)))
        }

        val characteristics = WritableNativeArray()
        val service = gatt.services.find { it.uuid.toString() == serviceUUID }
                ?: return promise.reject("E_CHARACTERISTICS_DISCOVER_FAILED", "Failed discovering peripheral's characteristics, couldn't find service!!", null)

        service.characteristics.forEach { characteristic ->
            if (UUIDs.contains(characteristic.uuid))  {
                val willRead = gatt.readCharacteristic(characteristic)
                Log.i(TAG, "Is about to attempt to read characteristic: $willRead")

                val params = WritableNativeMap()
                params.putString("uuid", characteristic.uuid.toString())
                params.putInt("properties", characteristic.properties)
                params.putNull("value")
                characteristics.pushMap(params)
            }
        }

        promise.resolve(characteristics)
    }

    @ReactMethod
    fun _readCharacteristicValueForPeripheral(peripheralId: Int, serviceUUID: String, characteristicUUID: String, promise: Promise) {
        val peripheral = getPeripheral(peripheralId, promise)
        val gatt = peripheral.getGatt()

        val service = gatt.services.find { it.uuid.toString() == serviceUUID }
                ?: return promise.reject("E_CHARACTERISTICS_READ_FAILED", "Failed reading peripheral's characteristics, couldn't find service!", null)

        val characteristic = service.characteristics.find { it.uuid.toString() == characteristicUUID }
                ?: return promise.reject("E_CHARACTERISTICS_READ_FAILED", "Failed reading peripheral's characteristics, couldn't find characteristic!", null)

        promises[Pair(BLEFunction._readCharacteristicValueForPeripheral, peripheralId)] = promise
        gatt.readCharacteristic(characteristic)
    }

    @ReactMethod
    fun _respondToReadRequest(requestId: Int, accept: Boolean, promise: Promise) {
        val request = receivedRequests[requestId]
                ?: return promise.reject("E_UNKNOWN_REQUEST", "Attempted to get an unknown read request!", null)

        gattServer?.respond(request.central.device,
                requestId,
                if (accept) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_READ_NOT_PERMITTED,
                request.offset,
                if (accept) request.characteristic.value else ByteArray(0))

        promise.resolve(null)
    }

    fun isLEEnabled(packageManager: PackageManager): Boolean {
        val hasBluetoothLe = packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        if (!hasBluetoothLe) return false
        if (!adapter.isEnabled) return false
        return true
    }

    fun getRequestEnableIntent(): Intent {
        return Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
    }

    inner class Scanner {
        init {
            if (bluetoothManager.adapter.bluetoothLeScanner == null) throw Exception("Bluetooth Low Energy Scanner is null!")
        }

        private val leScanner: BluetoothLeScanner = bluetoothManager.adapter.bluetoothLeScanner
        private var callback: ScanCallback? = null

        fun start(serviceUUIDs: List<UUID>, callback: ScanCallback? = null) {
            val scanFilterBuilder = ScanFilter.Builder()
            for (serviceUUID in serviceUUIDs) {
                scanFilterBuilder.setServiceUuid(ParcelUuid(serviceUUID))
            }

            val scanSettings = ScanSettings.Builder().build()
            val scanFilters = listOf(scanFilterBuilder.build())

            if (this.callback == null && callback == null) throw Exception("No previous callback found, Scanner.start requires a callback!")
            if (callback != null) this.callback = callback
            leScanner.startScan(scanFilters, scanSettings, this.callback)
            Log.i(TAG, "Scanning about to start for $serviceUUIDs")
        }

        fun stop() {
            callback?.let {
                leScanner.stopScan(it)
            }
        }

        fun sendPeripheralDiscoveredEvent(peripheral: BluetoothDevice) {
            val params = WritableNativeMap()
            val id = peripheral.hashCode()

            params.putInt("id", id)
            params.putString("name", peripheral.name)
            params.putInt("state", peripheral.bondState)
            params.putNull("services")

            discoveredDevices[id] = BLEPeripheral(peripheral)
            sendEvent("peripheralDiscovered", params)
        }

        fun sendServicesDiscoveredPromise(peripheralId: Int, services: MutableList<BluetoothGattService>) {
            val promise = promises[Pair(BLEFunction._discoverPeripheralServices, peripheralId)]
                    ?: throw Throwable("E_UNKNOWN_PROMISE - sendServicesDiscoveredPromise")

            promise.resolve(services.map { service ->
                val params = WritableNativeMap()
                params.putString("uuid", service.uuid.toString())
                params.putBoolean("IsPrimary", service.type == BluetoothGattService.SERVICE_TYPE_PRIMARY)
                params.putInt("peripheralId", peripheralId)
                params.putNull("characteristics")
                params
            })
        }

        fun sendCharacteristicReadPromise(peripheralId: Int, value: ByteArray) {
            val promise = promises[Pair(BLEFunction._discoverPeripheralServices, peripheralId)]
                    ?: throw Throwable("E_UNKNOWN_PROMISE - sendCharacteristicReadPromise")

            promise.resolve(String(value))
        }
    }

    inner class Advertiser {
        init {
            if (bluetoothManager.adapter.bluetoothLeAdvertiser == null) throw Exception("Bluetooth Low Energy Advertiser is null!")
        }

        private val leAdvertiser = bluetoothManager.adapter.bluetoothLeAdvertiser
        private var callback: AdvertiseCallback? = null

        // TODO: You can have multiple service UUIDs, so pass in several services and add them all? Have an advertise boolean in dictionary and use that for advertisements
        // TODO: Try removing the GATT server? It's gonna get bundled with advertiser later
        fun start(advertisementMode: Int, connectible: Boolean, timeout: Int, txPowerLevel: Int, serviceUUIDs: List<UUID>) {
            val settings = AdvertiseSettings.Builder()
                    .setAdvertiseMode(advertisementMode)
                    .setTxPowerLevel(txPowerLevel)
                    .setConnectable(connectible)
                    .setTimeout(timeout)
                    .build()

            val dataBuilder = AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .setIncludeTxPowerLevel(false)

            serviceUUIDs.forEach { serviceUUID ->
                dataBuilder.addServiceUuid(ParcelUuid(serviceUUID))
            }

            val data = dataBuilder.build()
            callback = callbacks.AdvertiseLECallback()
            leAdvertiser.startAdvertising(settings, data, callback)
            Log.i(TAG, "Advertising about to start for $serviceUUIDs")
        }

        fun stop() {
            if (callback != null) leAdvertiser.stopAdvertising(callback)
        }
    }

    inner class GattServer {
        private var server: BluetoothGattServer? = null

        fun start() {
            server = bluetoothManager.openGattServer(reactContext, callbacks.GattServerLECallback())
            if (server != null) Log.i(TAG, "Own gattServer started successfully!")
        }

        fun stop() {
            server?.close()
            server = null
            Log.i(TAG, "Closing own gattServer!")
        }

        fun addService(bleService: BLEService) {
            server?.let { gattServer ->
                gattServer.addService(bleService.service)
                Log.i(TAG, "Added service: ${bleService.uuid}")
            }

            Log.i(TAG, "Attempted to add service to null server: ${bleService.uuid}")
            return
        }

        fun respond(device: BluetoothDevice, requestId: Int, status: Int, offset: Int, value: ByteArray) {
            server?.sendResponse(device, requestId, status, offset, value)
        }

        fun sendCentralConnectedEvent(central: BluetoothDevice) {
            val params = WritableNativeMap()
            params.putInt("id", central.hashCode())
            sendEvent("centralConnected", params)
        }

        fun sendCentralDisconnectedEvent(central: BluetoothDevice) {
            val params = WritableNativeMap()
            params.putInt("id", central.hashCode())
            sendEvent("centralDisconnected", params)
        }

        fun sendReadRequestEvent(centralId: Int, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
            val params = WritableNativeMap()
            params.putInt("id", requestId)
            params.putInt("centralId", centralId)
            params.putString("characteristicId", characteristic.uuid.toString())
            params.putString("characteristicValue", String(characteristic.value))
            sendEvent("receivedReadRequest", params)

            val central = getPeripheral(centralId)
            receivedRequests[requestId] = BLEReadRequest(requestId, central, offset, characteristic)
        }
    }

    inner class BLEPeripheral(val device: BluetoothDevice) {
        private var gatt: BluetoothGatt? = null
        fun setGatt(value: BluetoothGatt) {
            gatt = value
        }

        fun getGatt(): BluetoothGatt {
            if (gatt == null) throw Throwable("Attempted to get null client from BLEPeripheral! (id: ${device.hashCode()})")
            return gatt as BluetoothGatt
        }
    }

    inner class BLEService(_service: ReadableMap) {
        val uuid: UUID = UUID.fromString(_service.getString("uuid"))
        val bleCharacteristics: MutableList<BLECharacteristic> = mutableListOf()
        var service: BluetoothGattService

        init {
            val characteristics = _service.getArray("characteristics") as ReadableArray
            val isPrimary: Boolean = _service.getBoolean("isPrimary")
            val serviceType = if (isPrimary) BluetoothGattService.SERVICE_TYPE_PRIMARY
            else BluetoothGattService.SERVICE_TYPE_SECONDARY


            service = BluetoothGattService(uuid, serviceType)
            for (i in 0 until characteristics.size()) {
                val bleCharacteristic = BLECharacteristic(characteristics.getMap(i) as ReadableMap)
                Log.i(TAG, "Added characteristic: ${bleCharacteristic.uuid}")
                service.addCharacteristic(bleCharacteristic.characteristic)
                bleCharacteristics.add(bleCharacteristic)
            }
        }
    }

    inner class BLECharacteristic(_characteristic: ReadableMap) {
        val uuid: UUID = UUID.fromString(_characteristic.getString("uuid"))
        val properties = BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ
        val permissions = BluetoothGattCharacteristic.PERMISSION_READ
        val data = _characteristic.getString("data")?.toByteArray()
        var characteristic: BluetoothGattCharacteristic

        init {
            characteristic = BluetoothGattCharacteristic(uuid, properties, permissions)
            characteristic.value = data
        }
    }

    inner class BLEReadRequest(val requestId: Int, val central: BLEPeripheral, val offset: Int, val characteristic: BluetoothGattCharacteristic)

    enum class GenericAccessProfileRole(value: Int) {
        PERIPHERAL(0),
        CENTRAL(1)
    }

    enum class BLEFunction(value: String) {
        _discoverPeripheralServices("_discoverPeripheralServices"),
        _readCharacteristicValueForPeripheral("_readCharacteristicValueForPeripheral")
    }

    companion object {
        const val TAG = " __ CY_BLUETOOTH __ "
    }
}
