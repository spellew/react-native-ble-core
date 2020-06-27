//
//  BLECore.swift
//  CastYourself
//
//  Created by Shamroy Pellew on 6/24/20.
//  Copyright © 2020 Facebook. All rights reserved.
//

import UIKit
import CoreBluetooth


@objc(BLECore)
class BLECore: NSObject, CBCentralManagerDelegate, CBPeripheralManagerDelegate, CBPeripheralDelegate {
    var cbCentralManager: CBCentralManager?
    var cbPeripheralManager: CBPeripheralManager?
    var cbPeripheral: CBPeripheral?
    //  backed getters and setters and throw errors when nil
    
    var cbCentralOptions: [String : Any]?
    var cbPeripheralOptions: [String : Any]?
    var isReady: [GenericAccessProfileRole: Bool] = [:]
    var resolveBlock: RCTPromiseResolveBlock?
    
    let MY_UUID = CBUUID(string: "0aae7a9d-be24-438d-9338-f8812c69c177")
    let SERVICE_UUID = CBUUID(string: "30730665-27be-4695-a6fe-6c3ba237070b")
    let CHARACTERISTIC_UUID = CBUUID(string: "400408ca-d099-4be1-a72c-7cdcb11a6eb7")
    let TAG = " _ CY_BLUETOOTH "
    
//    Store resolves in variables for each role

    @objc
    func initialize(_ roles: NSArray, options: NSDictionary, resolve: @escaping RCTPromiseResolveBlock, rejecter reject: RCTPromiseRejectBlock) {
        let roles = roles.compactMap({ GenericAccessProfileRole(rawValue: $0 as? Int ?? -1) })
        if roles.contains(GenericAccessProfileRole.PERIPHERAL) {
            cbPeripheralOptions = options[GenericAccessProfileRole.PERIPHERAL] as? [String : Any]
            cbPeripheralManager = CBPeripheralManager(delegate: self, queue: nil, options: cbPeripheralOptions)
            isReady[GenericAccessProfileRole.PERIPHERAL] = false
        }
        
        if roles.contains(GenericAccessProfileRole.CENTRAL) {
            cbCentralOptions = options[GenericAccessProfileRole.CENTRAL] as? [String : Any]
            cbCentralManager = CBCentralManager(delegate: self, queue: nil, options: cbCentralOptions)
            isReady[GenericAccessProfileRole.CENTRAL] = false
        }
        
        if isReady.isEmpty {
            return reject("E_INIT_ROLES_NIL", "BLECore was not initialized with any valid GAP roles!", nil)
        }
        
        resolveBlock = resolve
    }
    
    func startScanning() {
        print("Starting to scan for peripherals!" + TAG)
        cbCentralManager!.scanForPeripherals(withServices: [MY_UUID], options: cbCentralOptions)
    }
    
    func startAdvertising() {
        print("Starting to advertise to peripherals!" + TAG)
        let service = CBMutableService(type: SERVICE_UUID, primary: true)
        let cbProperties: CBCharacteristicProperties = [.notify, .read]
        let cbPermissions: CBAttributePermissions = [.readable]
        let data = UUID().uuidString.suffix(12).lowercased()
        print("Random: " + data + TAG)
        let characteristic = CBMutableCharacteristic(type: CHARACTERISTIC_UUID, properties: cbProperties, value: nil, permissions: cbPermissions)
        service.characteristics = [characteristic]
        cbPeripheralManager!.add(service)
        cbPeripheralManager!.startAdvertising([CBAdvertisementDataServiceUUIDsKey: [MY_UUID]])
        characteristic.value = Data(data.utf8)
    }
    
    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String : Any], rssi RSSI: NSNumber) {
        print("Found peripheral!" + TAG)
        cbPeripheral = peripheral
        peripheral.delegate = self
        
        print("Connecting!" + TAG)
        cbCentralManager!.stopScan()
        cbCentralManager!.connect(peripheral, options: cbCentralOptions)
    }
    
    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        print("Connected!" + TAG)
        print("Discovering services!" + TAG)
        peripheral.discoverServices([SERVICE_UUID])
    }
    
    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        print("Disconnected!" + TAG)
        print("Starting to scan for peripherals!" + TAG)
        cbCentralManager!.scanForPeripherals(withServices: [MY_UUID], options: cbCentralOptions)
    }
    
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        print("CBCentralManager state did change!" + TAG)
        switch central.state {
        case CBManagerState.poweredOn:
            print("Bluetooth is enabled!" + TAG)
            isReady[GenericAccessProfileRole.CENTRAL] = true
            print("managersReady" + isReady.description + TAG)
            let managersReady = isReady.reduce(true) { (result, tuple) in
                return result && tuple.value
            }
            
            if managersReady {
                if resolveBlock != nil {
                    resolveBlock!("scanning - all ready")
                } else {
                    print("scanning - something really weird happened" + TAG)
                }
            }

            startScanning()
            break
        case CBManagerState.poweredOff:
            print("Bluetooth is disabled!" + TAG)
            break
        default:
            break
        }
    }
    
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        print("Discovered services!" + TAG)
        for service in peripheral.services! {
            if service.uuid == SERVICE_UUID {
                print("Discovering characteristic!" + TAG)
                peripheral.discoverCharacteristics([CHARACTERISTIC_UUID], for: service)
            }
        }
    }
    
    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        print("Discovered characteristics!" + TAG)
        for characteristic in service.characteristics! {
            print(characteristic.description + TAG)
            if characteristic.uuid == CHARACTERISTIC_UUID {
                print("Reading value!" + TAG)
                peripheral.readValue(for: characteristic)
            }
        }
    }
    
    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        print("Read value!" + TAG)
        if characteristic.uuid == CHARACTERISTIC_UUID {
            let value = String(bytes: characteristic.value!, encoding: .utf8)!
            print(value.description + TAG)
        }
    }
    
    func peripheralManagerDidStartAdvertising(_ peripheral: CBPeripheralManager, error: Error?) {
        print("Advertising " + MY_UUID.description + TAG)
    }
    
    func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral, didSubscribeTo characteristic: CBCharacteristic) {
        print("A device did connect!" + TAG)
    }
    
    func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral, didUnsubscribeFrom characteristic: CBCharacteristic) {
        print("A device did disconnect!" + TAG)
    }
    
    func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveRead request: CBATTRequest) {
        print("A read request was received!" + TAG)
        let value = String(decoding: request.characteristic.value ?? Data(), as: UTF8.self)
        print("Value: " + value + TAG)
        request.value = request.characteristic.value
        cbPeripheralManager!.respond(to: request, withResult: CBATTError.success)
    }
    
    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        print("CBPeripheralManager state did change!" + TAG)
        switch peripheral.state {
        case CBManagerState.poweredOn:
            print("CBPeripheralManager is in powered on state!" + TAG)
            isReady[GenericAccessProfileRole.PERIPHERAL] = true
            print("managersReady" + isReady.description + TAG)
            let managersReady = isReady.reduce(true) { (result, tuple) in
                return result && tuple.value
            }
            
            if managersReady {
                if resolveBlock != nil {
                    resolveBlock!("advertiser - all ready")
                } else {
                    print("advertiser - something really weird happened" + TAG)
                }
            }
            
            startAdvertising()
            break
        default:
            break
        }
    }
}
