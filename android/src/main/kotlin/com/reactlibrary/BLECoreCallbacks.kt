package com.reactlibrary

import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableNativeMap
import java.util.*


class BLECoreCallbacks(private val context: Context, private val scanner: BLECoreModule.Scanner?, private val advertiser: BLECoreModule.Advertiser?, private val gattServer: BLECoreModule.GattServer?) {

    // TODO: create a queue for tasks and the likes
    // TODO: local cache so devices aren't rediscovered
    inner class AdvertiseLECallback: AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d(BLECoreModule.TAG, "Advertise server started successfully!")
            super.onStartSuccess(settingsInEffect)
        }

        override fun onStartFailure(errorCode: Int) {
            Log.d(BLECoreModule.TAG, "Advertise server failed: $errorCode")
            super.onStartFailure(errorCode)
        }
    }

    inner class GattClientLECallback(): BluetoothGattCallback() {
        init {
            Log.d(BLECoreModule.TAG, "GattClient created successfully!")
        }

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(BLECoreModule.TAG, "Connected to discovered device's GATT server! (${gatt.device.address})")
                // TODO: Instead have a log of connected devices and don't connect to anything in log
                gattServer?.stop()
                advertiser?.stop()
                scanner?.stop()

                Log.d(BLECoreModule.TAG, "Now discovering new device's services!")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(BLECoreModule.TAG, "Disconnected from discovered device's GATT server! (${gatt.device.address})")
                gatt.close()
            }

            super.onConnectionStateChange(gatt, status, newState)
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            Log.d(BLECoreModule.TAG, "Status: $status")
            Log.d(BLECoreModule.TAG, "Read characteristic value from device: " + characteristic.value)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                scanner?.sendCharacteristicReadPromise(gatt.device.hashCode(), characteristic.value)
            }

            super.onCharacteristicRead(gatt, characteristic, status)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d(BLECoreModule.TAG, "Discovered new device's services!")
            scanner?.sendServicesDiscoveredPromise(gatt.device.hashCode(), gatt.services)
            super.onServicesDiscovered(gatt, status)
        }
    }

    inner class GattServerLECallback(): BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(BLECoreModule.TAG, "New device connected to Gatt server! (${device?.address})")
                Log.d(BLECoreModule.TAG, "gattServer: $gattServer")
                gattServer?.handleCentralConnected(device as BluetoothDevice)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(BLECoreModule.TAG, "Device disconnected from Gatt server! (${device?.address})")
                Log.d(BLECoreModule.TAG, "gattServer: $gattServer")
                gattServer?.handleCentralDisconnected(device as BluetoothDevice)
            }

            super.onConnectionStateChange(device, status, newState)
        }

        override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
            Log.d(BLECoreModule.TAG, "Request to read characteristic! (${device.address})")
            gattServer?.sendReadRequestEvent(device.hashCode(), requestId, offset, characteristic)
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
        }
    }

    inner class ScanLECallback(private val serviceUUIDs: List<UUID>, private val options: ReadableMap?): ScanCallback() {
        init {
            Log.d(BLECoreModule.TAG, "Scanning started successfully!")
        }

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            Log.d(BLECoreModule.TAG, "options: $options")
            Log.d(BLECoreModule.TAG, "options.CENTRAL: ${options?.getMap(BLECoreModule.GenericAccessProfileRole.CENTRAL.ordinal.toString())}")
            val shouldPause = options
                    ?.getMap(BLECoreModule.GenericAccessProfileRole.CENTRAL.ordinal.toString())
                    ?.getBoolean("pauseScanBetweenPeripherals")

            Log.d(BLECoreModule.TAG, "shouldPause: $shouldPause")
            if (shouldPause != null && shouldPause) {
                scanner?.stop()
            }

            scanner?.sendPeripheralDiscoveredEvent(result.device)
            Log.d(BLECoreModule.TAG,"Found advertisement!")
            super.onScanResult(callbackType, result)
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            Log.d(BLECoreModule.TAG, "Got batch scan results?")
            super.onBatchScanResults(results)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(BLECoreModule.TAG, "Scan failed: $errorCode")
            super.onScanFailed(errorCode)
        }
    }
}
