package com.reactlibrary

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import android.widget.Toast
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
    private var initializationOptions: ReadableMap? = null

    private lateinit var callbacks: BLECoreCallbacks
    private fun sendEvent(eventName: String, @Nullable params: WritableMap) {
        val jsModule = reactContext.getJSModule(RCTDeviceEventEmitter::class.java)
        jsModule.emit(eventName, params)
    }

    private fun getPeripheral(peripheralId: Int, promise: Promise? = null): BLEPeripheral? {
        Log.d(TAG, "Attempting to get peripheral: $peripheralId")
        val peripheral = discoveredDevices[peripheralId]
        if (peripheral == null) {
            promise?.reject("E_UNKNOWN_PERIPHERAL", "Attempted to get an unknown and undiscovered peripheral!", null)
            return null
        }

        return peripheral
    }

    override fun getName(): String {
        return "BLECore"
    }

    //  TODO: Headless
    //  TODO: Return status codes instead of booleans for each specific scenario

    @ReactMethod
    fun _initialize(roles: ReadableArray, options: ReadableMap, promise: Promise) {
        for (i in 0 until roles.size()) {
            try {
                when (roles.getInt(i)) {
                    GenericAccessProfileRole.PERIPHERAL.ordinal -> {
                        gattServer = GattServer()
                        advertiser = Advertiser()
                    }

                    GenericAccessProfileRole.CENTRAL.ordinal -> {
                        scanner = Scanner()
                    }
                }
            } catch (e: Exception) {
                continue
            }
        }

        initializationOptions = options
        callbacks = BLECoreCallbacks(reactContext, scanner, advertiser, gattServer)
        promise.resolve(null)
    }

    @ReactMethod
    fun _startScanning(serviceUUIDs: ReadableArray, options: ReadableMap?, promise: Promise) {
        if (scanner == null) {
            promise.reject("E_CENTRAL_NULL", "Central's scanner was never initialized!", null)
            return
        }

        val UUIDs = mutableListOf<UUID>()
        for (i in 0 until serviceUUIDs.size()) {
            UUIDs.add(UUID.fromString(serviceUUIDs.getString(i)?.toUpperCase(Locale.getDefault())))
        }

        scanningUUIDs = UUIDs
        scanner?.start(UUIDs, callbacks.ScanLECallback(UUIDs, initializationOptions))

        promise.resolve(null)
    }

    @ReactMethod
    fun _startAdvertising(services: ReadableArray, promise: Promise) {
        if (advertiser == null || gattServer == null) {
            promise.reject("E_PERIPHERAL_NULL", "Peripheral's advertiser was never initialized!", null)
            return
        }

        val UUIDs = mutableListOf<UUID>()
        val bleServices = mutableListOf<BLEService>()
        for (i in 0 until services.size()) {
            val bleService = BLEService(services.getMap(i) as ReadableMap)
            bleServices.add(bleService)
            UUIDs.add(bleService.uuid)
        }

        advertisingUUIDs = UUIDs
        advertiser?.start(AdvertiseSettings.ADVERTISE_MODE_BALANCED, true, 0, AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM, advertisingUUIDs)
        gattServer?.start()

        for (bleService in bleServices) {
            gattServer?.addService(bleService)
        }

        promise.resolve(null)
    }

    @ReactMethod
    fun _connectToPeripheral(peripheralId: Int, options: ReadableMap?, promise: Promise) {
        val peripheral = getPeripheral(peripheralId, promise)
        val mBluetoothGatt = peripheral?.device?.connectGatt(reactContext, false, callbacks.GattClientLECallback(), BluetoothDevice.TRANSPORT_LE)
        if (mBluetoothGatt == null) {
            promise.reject("E_CONNECT_FAILED", "Failed connecting to peripheral!", null)
            scanner?.start(scanningUUIDs)
            return
        }

        peripheral.setGatt(mBluetoothGatt)
        promise.resolve(null)
    }

    @ReactMethod
    fun _discoverPeripheralServices(peripheralId: Int, serviceUUIDs: ReadableArray, promise: Promise) {
        val peripheral = getPeripheral(peripheralId, promise)
        val gatt = peripheral?.getGatt()

        if (gatt != null && gatt.discoverServices()) promises[Pair(BLEFunction._discoverPeripheralServices, peripheralId)] = promise
        else return promise.reject("E_DISCOVER_FAILED", "Failed attempted to discover peripheral's services!", null)
    }

    @ReactMethod
    fun  _discoverPeripheralCharacteristics(peripheralId: Int, serviceUUID: String, characteristicUUIDs: ReadableArray, promise: Promise) {
        val peripheral = getPeripheral(peripheralId, promise)
        val gatt = peripheral?.getGatt()

        val UUIDs = mutableListOf<UUID>()
        for (i in 0 until characteristicUUIDs.size()) {
            UUIDs.add(UUID.fromString(characteristicUUIDs.getString(i)))
        }

        val characteristics = WritableNativeArray()
        val service = gatt?.services?.find { it.uuid.toString() == serviceUUID }
                ?: return promise.reject("E_CHARACTERISTICS_DISCOVER_FAILED", "Failed discovering peripheral's characteristics, couldn't find service!!", null)

        service.characteristics.forEach { characteristic ->
            if (UUIDs.contains(characteristic.uuid))  {
                val willRead = gatt.readCharacteristic(characteristic)
                Log.d(TAG, "Is about to attempt to read characteristic: $willRead")

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
        val gatt = peripheral?.getGatt()

        val service = gatt?.services?.find { it.uuid.toString() == serviceUUID }
                ?: return promise.reject("E_CHARACTERISTICS_READ_FAILED", "Failed reading peripheral's characteristics, couldn't find service!", null)

        val characteristic = service.characteristics.find { it.uuid.toString() == characteristicUUID }
                ?: return promise.reject("E_CHARACTERISTICS_READ_FAILED", "Failed reading peripheral's characteristics, couldn't find characteristic!", null)

        Log.d(TAG, "Want to read: ${promise.hashCode()}")
        promises[Pair(BLEFunction._readCharacteristicValueForPeripheral, peripheralId)] = promise
        gatt.readCharacteristic(characteristic)
    }

    @ReactMethod
    fun _respondToReadRequest(requestId: Int, accept: Boolean, promise: Promise) {
        Log.d(TAG, "receivedRequests: $receivedRequests")
        Log.d(TAG, "Attempting to get requestId: $requestId")
        val request = receivedRequests[requestId]
                ?: return promise.reject("E_UNKNOWN_REQUEST", "Attempted to get an unknown read request!", null)

        gattServer?.respond(request.central.device,
                requestId,
                if (accept) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_READ_NOT_PERMITTED,
                request.offset,
                if (accept) request.characteristic.value else ByteArray(0))

        promise.resolve(null)
    }

    fun isLEEnabled(): Boolean {
        if (!reactContext.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) return false
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
            val scanFilters = mutableListOf<ScanFilter>()
            for (serviceUUID in serviceUUIDs) {
                val scanFilterBuilder = ScanFilter.Builder()
                scanFilterBuilder.setServiceUuid(ParcelUuid(serviceUUID))
                scanFilters.add(scanFilterBuilder.build())
            }

            val scanSettings = ScanSettings.Builder().build()
            if (this.callback == null && callback == null) throw Exception("No previous callback found, Scanner.start requires a callback!")
            if (callback != null) this.callback = callback

            leScanner.startScan(scanFilters, scanSettings, this.callback)
            Log.d(TAG, "Scanning about to start for $serviceUUIDs")
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

            val params = WritableNativeArray()
            services.forEach { service ->
                val map = WritableNativeMap()
                map.putString("uuid", service.uuid.toString())
                map.putBoolean("IsPrimary", service.type == BluetoothGattService.SERVICE_TYPE_PRIMARY)
                map.putInt("peripheralId", peripheralId)
                map.putNull("characteristics")
                params.pushMap(map)
            }

            promise.resolve(params)
        }

        fun sendCharacteristicReadPromise(peripheralId: Int, value: ByteArray?) {
            val promise = promises[Pair(BLEFunction._readCharacteristicValueForPeripheral, peripheralId)]
                    ?: throw Throwable("E_UNKNOWN_PROMISE - sendCharacteristicReadPromise")

            Log.d(TAG, "Have read: ${promise.hashCode()}")
            Log.d(TAG, "byteArray value: $value")
            if (value == null) return promise.resolve(null)
            Log.d(TAG, "string value: ${String(value)}")
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
            Log.d(TAG, "Advertising about to start for $serviceUUIDs")
        }

        fun stop() {
            if (callback != null) leAdvertiser.stopAdvertising(callback)
        }
    }

    inner class GattServer {
        private var server: BluetoothGattServer? = null

        fun start() {
            server = bluetoothManager.openGattServer(reactContext, callbacks.GattServerLECallback())
            if (server != null) Log.d(TAG, "Own gattServer started successfully!")
        }

        fun stop() {
            server?.close()
            server = null
            Log.d(TAG, "Closing own gattServer!")
        }

        fun addService(bleService: BLEService) {
            server?.let { gattServer ->
                gattServer.addService(bleService.service)
                Log.d(TAG, "Added service: ${bleService.uuid}")
                return
            }

            Log.d(TAG, "Attempted to add service to null server: ${bleService.uuid}")
        }

        fun respond(device: BluetoothDevice, requestId: Int, status: Int, offset: Int, value: ByteArray) {
            server?.sendResponse(device, requestId, status, offset, value)
        }

        fun handleCentralConnected(central: BluetoothDevice) {
            discoveredDevices[central.hashCode()] = BLEPeripheral(central)
            sendCentralConnectedEvent(central)
        }

        fun handleCentralDisconnected(central: BluetoothDevice) {
            discoveredDevices.remove(central.hashCode())
            sendCentralDisconnectedEvent(central)
        }

        private fun sendCentralConnectedEvent(central: BluetoothDevice) {
            val params = WritableNativeMap()
            params.putInt("id", central.hashCode())
            sendEvent("centralConnected", params)
            Log.d(TAG, "Sending centralConnected")
        }

        private fun sendCentralDisconnectedEvent(central: BluetoothDevice) {
            val params = WritableNativeMap()
            params.putInt("id", central.hashCode())
            sendEvent("centralDisconnected", params)
            Log.d(TAG, "Sending centralDisconnected")
        }

        fun sendReadRequestEvent(centralId: Int, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
            val params = WritableNativeMap()
            params.putInt("id", requestId)
            params.putInt("centralId", centralId)
            params.putString("characteristicId", characteristic.uuid.toString())
            params.putString("characteristicValue", String(characteristic.value))
            sendEvent("receivedReadRequest", params)

            Log.d(TAG, "attempting to get central! $centralId")
            getPeripheral(centralId)?.let { central ->
                Log.d(TAG, "readRequest got peripheral successfully!")
                receivedRequests[requestId] = BLEReadRequest(requestId, central, offset, characteristic)
                Log.d(TAG, "Sending receivedReadRequest")
            }
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
        val uuid: UUID = UUID.fromString(_service.getString("uuid")?.toUpperCase(Locale.getDefault()))
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
                Log.d(TAG, "Added characteristic: ${bleCharacteristic.uuid}")
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
