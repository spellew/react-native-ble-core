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
class BLECore: RCTEventEmitter, CBCentralManagerDelegate, CBPeripheralManagerDelegate, CBPeripheralDelegate {
    var cbCentralManager: CBCentralManager?
    var cbPeripheralManager: CBPeripheralManager?
    
    var managersReady: [GenericAccessProfileRole: Bool] = [:]
    var scanningParameters: ([CBUUID], [String: Any])?
    var discoveredDevices: [Int: CBPeripheral] = [:]
    var receivedRequests: [Int: CBATTRequest] = [:]
    var sendingCharacteristics: [Int: Bool] = [:]
    var readingRequests: [Int: Bool] = [:]
    var readingValues: [Int: Bool] = [:]
    var options: [String: [String: Any]] = [:]
    
    let TAG = " _ CY_BLUETOOTH "
    var resolveBlocks: [Pair<BLEFunction, Int>: RCTPromiseResolveBlock] = [:]
    var rejectBlocks: [Pair<BLEFunction, Int>: RCTPromiseRejectBlock] = [:]
    
    
    @objc
    func _initialize(_ roles: NSArray, _options: NSDictionary, resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        let roles = roles.compactMap({ GenericAccessProfileRole(rawValue: $0 as? Int ?? -1) })
        options = _options as? [String : [String : Any]] ?? [:]
        
        if roles.contains(GenericAccessProfileRole.PERIPHERAL) {
            let cbPeripheralOptions = options[GenericAccessProfileRole.PERIPHERAL.rawValue.description]
            cbPeripheralManager = CBPeripheralManager(delegate: self, queue: nil, options: cbPeripheralOptions)
            managersReady[GenericAccessProfileRole.PERIPHERAL] = false
        }
    
        if roles.contains(GenericAccessProfileRole.CENTRAL) {
            let cbCentralOptions = options[GenericAccessProfileRole.CENTRAL.rawValue.description]
            cbCentralManager = CBCentralManager(delegate: self, queue: nil, options: cbCentralOptions)
            managersReady[GenericAccessProfileRole.CENTRAL] = false
        }
        
        if managersReady.isEmpty {
            return reject("E_INIT_ROLES_NIL", "BLECore was not initialized with any valid GAP roles!", nil)
        }
        
        resolveBlocks[Pair(first: ._initialize, second: -1)] = resolve
        rejectBlocks[Pair(first: ._initialize, second: -1)] = reject
    }
    
    @objc
    func _startScanning(_ serviceUUIDs: NSArray, options: NSDictionary, resolve: RCTPromiseResolveBlock, rejecter reject: RCTPromiseRejectBlock) {
        if cbCentralManager == nil {
            reject("E_CENTRAL_NULL", "Central's scanner was never initialized!", nil)
            return
        }
        
        let withServices = serviceUUIDs
            .compactMap({ $0 as? String })
            .compactMap({ CBUUID(string: $0) })
        
        let scanningUUIDs = withServices.isEmpty ? [] : withServices
        let scanningOptions = options as? [String : Any] ?? [:]
        
        scanningParameters = (scanningUUIDs, scanningOptions)
        cbCentralManager!.scanForPeripherals(withServices: scanningUUIDs, options: scanningOptions)
        
        resolve(NSNull())
    }
    
    @objc
    func _stopScanning(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        cbCentralManager!.stopScan()
        resolve(NSNull())
    }
        
    @objc
    func _startAdvertising(_ services: NSArray, resolve: RCTPromiseResolveBlock, rejecter reject: RCTPromiseRejectBlock) {
        if cbPeripheralManager == nil {
            reject("E_PERIPHERAL_NULL", "Peripheral's advertiser was never initialized!", nil)
            return
        }
        
        var bleServices: Array<BLEService> = []
        services.forEach {
            if let _service = $0 as? NSDictionary ?? nil {
                let bleService = BLEService(_service: _service)
                cbPeripheralManager?.add(bleService.service)
                bleServices.append(bleService)
            }
        }
        
        let serviceUUIDs = bleServices.compactMap({ $0.UUID })
        cbPeripheralManager!.startAdvertising([CBAdvertisementDataServiceUUIDsKey: serviceUUIDs])
        bleServices.forEach {
            $0.initValues()
        }
        
        resolve(NSNull())
    }
    
    @objc
    func _stopAdvertising(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        cbPeripheralManager!.stopAdvertising()
        resolve(NSNull())
    }
    
    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String : Any], rssi RSSI: NSNumber) {
        peripheral.delegate = self
        //  TODO: Maybe do peripheral.identifier for a UUID id instead of hash?
        discoveredDevices[peripheral.hash] = peripheral

        let cbCentralOptions = options[GenericAccessProfileRole.CENTRAL.rawValue.description]
        if cbCentralOptions?["pauseScanBetweenPeripherals"] as? Bool == true {
            cbCentralManager!.stopScan()
        }
    
        self.sendEvent(withName: "peripheralDiscovered", body: [
            "id": peripheral.hash,
            "name": peripheral.name,
            "state": peripheral.state.rawValue,
            "services": nil
        ])
    }
    
    @objc
    func _connectToPeripheral(_ peripheralId: NSInteger, options: NSDictionary, resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        let peripheral = getPeripheral(peripheralId: peripheralId, reject: reject)!
        resolveBlocks[Pair(first: ._connectToPeripheral, second: peripheralId)] = resolve
        rejectBlocks[Pair(first: ._connectToPeripheral, second: peripheralId)] = reject
        
        cbCentralManager!.connect(peripheral, options: options as? [String : Any])
    }
    
    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        let resolve = resolveBlocks[Pair(first: ._connectToPeripheral, second: peripheral.hash)]
        return resolve!(NSNull())
    }
    
    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        resolveBlocks.forEach {
            let (key, _) = $0
            if key.second == peripheral.hash {
                rejectBlocks.removeValue(forKey: key)
                resolveBlocks.removeValue(forKey: key)
            }
        }
        
        discoveredDevices.removeValue(forKey: peripheral.hash)
        self.sendEvent(withName: "peripheralDisconnected", body: [
            "id": peripheral.hash,
            "name": peripheral.name as Any,
            "state": peripheral.state.rawValue,
            "services": peripheral.services?.compactMap({ $0.uuid.description }) as Any
        ])
        
        let scanningUUIDs = scanningParameters?.0
        let scanningOptions = scanningParameters?.1
        cbCentralManager!.scanForPeripherals(withServices: scanningUUIDs, options: scanningOptions)
    }
    
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        let resolve = resolveBlocks[Pair(first: ._initialize, second: -1)]!
        let reject = rejectBlocks[Pair(first: ._initialize, second: -1)]!
        
        switch central.state {
            case CBManagerState.poweredOn:
                handleManagerReady(manager: GenericAccessProfileRole.CENTRAL, resolve: resolve)
                break
            case CBManagerState.poweredOff:
                reject("E_BLUETOOTH_DISABLED", "The device's bluetooth adapter is powered off!", nil)
                break
            default:
                break
        }
    }
    
    @objc
    func _discoverPeripheralServices(_ peripheralId: NSInteger, serviceUUIDs: NSArray, resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        let peripheral = discoveredDevices[peripheralId] ?? nil
        if peripheral == nil {
            return reject("E_UNKNOWN_PERIPHERAL_SERVICES", "Attempted to discover an unknown and undiscovered peripheral's services!", nil)
        }
        
        let serviceUUIDs = serviceUUIDs
            .compactMap({ $0 as? String })
            .compactMap({ CBUUID(string: $0) })
        
        resolveBlocks[Pair(first: ._discoverPeripheralServices, second: peripheralId)] = resolve
        rejectBlocks[Pair(first: ._discoverPeripheralServices, second: peripheralId)] = reject
        
        peripheral!.discoverServices(serviceUUIDs.isEmpty ? nil : serviceUUIDs)
    }
    
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        let resolve = resolveBlocks[Pair(first: ._discoverPeripheralServices, second: peripheral.hash)]!
        
        return resolve(peripheral.services?.compactMap({ [
            "uuid": $0.uuid.description,
            "peripheralId": peripheral.hash,
            "isPrimary": $0.isPrimary,
            "characteristics": nil
        ]}))
    }
    
    @objc
    func _discoverPeripheralCharacteristics(_ peripheralId: NSInteger, serviceUUID: NSString, characteristicUUIDs: NSArray, resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        let peripheral = discoveredDevices[peripheralId] ?? nil
        if peripheral == nil {
            return reject("E_UNKNOWN_PERIPHERAL_CHARACTERISTICS", "Attempted to discover an unknown and undiscovered peripheral's characteristics!", nil)
        }
        
        let characteristicUUIDs = characteristicUUIDs
            .compactMap({ $0 as? String })
            .compactMap({ CBUUID(string: $0) })
        
        for service in peripheral!.services ?? [] {
            if service.uuid.description.lowercased() == serviceUUID as String {
                resolveBlocks[Pair(first: ._discoverPeripheralCharacteristics, second: peripheralId)] = resolve
                rejectBlocks[Pair(first: ._discoverPeripheralCharacteristics, second: peripheralId)] = reject
                peripheral!.discoverCharacteristics(characteristicUUIDs.isEmpty ? nil : characteristicUUIDs, for: service)
            }
        }
    }
    
    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        let resolve = resolveBlocks[Pair(first: ._discoverPeripheralCharacteristics, second: peripheral.hash)]
        
        if sendingCharacteristics[peripheral.hash] == true {
            return
        }
        
        sendingCharacteristics[peripheral.hash] = true
        
        return resolve!(service.characteristics?.compactMap({[
            "uuid": $0.uuid.description,
            "properties": $0.properties.rawValue,
            "isNotifying": $0.isNotifying,
            "value": nil
        ]}))
    }

    @objc
    func _readCharacteristicValueForPeripheral(_ peripheralId: NSInteger, serviceUUID: NSString, characteristicUUID: NSString, resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        let peripheral = discoveredDevices[peripheralId] ?? nil
        if peripheral == nil {
            return reject("E_UNKNOWN_PERIPHERAL_CHARACTERISTIC_VALUE", "Attempted to discover the characteristic value of an unknown and undiscovered peripheral!", nil)
        }
        
        if readingValues[peripheralId] == true {
            return
        }
        
        readingValues[peripheralId] = true
        
        for service in peripheral!.services! {
            if service.uuid.description.lowercased() == serviceUUID as String {
                for characteristic in service.characteristics ?? [] {
                    if characteristic.uuid.description.lowercased() == characteristicUUID as String {
                        resolveBlocks[Pair(first: ._readCharacteristicValueForPeripheral, second: peripheralId)] = resolve
                        rejectBlocks[Pair(first: ._readCharacteristicValueForPeripheral, second: peripheralId)] = reject
                        peripheral!.readValue(for: characteristic)
                    }
                }
            }
        }
    }
    
    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        let resolve = resolveBlocks[Pair(first: ._readCharacteristicValueForPeripheral, second: peripheral.hash)]!
        resolve(String(bytes: characteristic.value!, encoding: .utf8))
    }
    
    func handleManagerReady(manager: GenericAccessProfileRole, resolve: RCTPromiseResolveBlock) {
        managersReady[manager] = true
        let isReady = managersReady.reduce(true) { (result, tuple) in return result && tuple.value }
        if isReady { resolve(NSNull()) }
    }
    
    func getPeripheral(peripheralId: Int, reject: RCTPromiseRejectBlock?) -> CBPeripheral? {
        let peripheral = discoveredDevices[peripheralId] ?? nil
        if peripheral == nil {
            reject!("E_UNKNOWN_PERIPHERAL", "Attempted to connect to an unknown and undiscovered peripheral!", nil)
            return nil
        }
        
        return peripheral
    }
    
    func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral, didSubscribeTo characteristic: CBCharacteristic) {
        self.sendEvent(withName: "centralConnected", body: [
            "id": central.hash
        ])
    }
    
    func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral, didUnsubscribeFrom characteristic: CBCharacteristic) {
        self.sendEvent(withName: "centralDisconnected", body: [
            "id": central.hash
        ])
    }
    
    @objc
    func _respondToReadRequest(_ requestId: NSInteger, accept: NSNumber, resolve: RCTPromiseResolveBlock, rejecter reject: RCTPromiseRejectBlock) {
        let request = receivedRequests[requestId]
        if request == nil {
            reject("E_UNKNOWN_REQUEST", "Attempted to respond to an unknown request!", nil)
            return
        }
        
        if readingRequests[requestId] == true {
            return
        }
        
        readingRequests[requestId] = true
        let _accept = accept.boolValue
        if _accept { request!.value = request!.characteristic.value }
        cbPeripheralManager!.respond(to: request!, withResult: _accept ? CBATTError.success : CBATTError.readNotPermitted)
        resolve(NSNull())
    }
    
    func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveRead request: CBATTRequest) {
        if receivedRequests[request.hash] != nil { return }
        
        receivedRequests[request.hash] = request
        let characteristicValue = String(decoding: request.characteristic.value ?? Data(), as: UTF8.self)
        
        self.sendEvent(withName: "receivedReadRequest", body: [
            "id": request.hash,
            "centralId": request.central.hash,
            "characteristicId": request.characteristic.hash,
            "characteristicValue": characteristicValue
        ])
    }
    
    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        let resolve = resolveBlocks[Pair(first: ._initialize, second: -1)]!
        let reject = rejectBlocks[Pair(first: ._initialize, second: -1)]!

        switch peripheral.state {
            case CBManagerState.poweredOn:
                handleManagerReady(manager: GenericAccessProfileRole.PERIPHERAL, resolve: resolve)
                break
            case CBManagerState.poweredOff:
                reject("E_BLUETOOTH_DISABLED", "The device's bluetooth adapter is powered off!", nil)
                break
            default:
                break
        }
    }
    
    // TODO: Rename all options to be camel-cased
    // TODO: Handle errors in a non silent way?
    // TODO: Maybe add timers and time outs to some of these bad boys
    // TODO: explicit errors so you know you're doing something wrong
    // TODO: See if you can change some of the types from non optional
    // TODO: This hashing is going to run into collisions, if multiple from same peripheral
    // TODO: Store BLEServices and stuffs in dictionary?? (prob not)
    // TODO: RCTConvert? turn json into objects and stuffs
    
    override func supportedEvents() -> [String]! {
        return [
            "centralConnected",
            "centralDisconnected",
            "receivedReadRequest",
            "peripheralDiscovered",
            "peripheralDisconnected",
        ]
    }
}


class BLEService {
    var UUID: CBUUID
    var service: CBMutableService
    var bleCharacteristics: [BLECharacteristic] = []

    init(_service: NSDictionary) {
        let uuid = _service.object(forKey: "uuid") as! String
        let isPrimary = _service.object(forKey: "isPrimary") as? Bool ?? false
        let _characteristics = _service.object(forKey: "characteristics") as? Array<NSDictionary> ?? []
        
        UUID = CBUUID(string: uuid)
        service = CBMutableService(type: UUID, primary: isPrimary)
        bleCharacteristics = _characteristics.compactMap({ BLECharacteristic(_characteristic: $0) })
        service.characteristics = bleCharacteristics.compactMap({ $0.characteristic })
    }
    
    func initValues() {
        bleCharacteristics.forEach({
            $0.initValue()
        })
    }
}

class BLECharacteristic {
    var UUID: CBUUID
    var properties: CBCharacteristicProperties
    var permissions: CBAttributePermissions
    var characteristic: CBMutableCharacteristic
    var data: Data?
    
    init(_characteristic: NSDictionary) {
        let uuid = _characteristic.object(forKey: "uuid") as! String
        let _data = _characteristic.object(forKey: "data") as! String
        //  let _properties = _characteristic.object(forKey: "properties") as? Array<UInt> ?? []
        //  let _permissions = _characteristic.object(forKey: "permissions") as? Array<UInt> ?? []

        UUID = CBUUID(string: uuid)
        // TODO: Figure out a way to parse these properly, probably enum or something that maps from integers
        properties = [.notify, .read]
        permissions = [.readable]
        
        characteristic = CBMutableCharacteristic(type: UUID, properties: properties, value: nil, permissions: permissions)
        if (!_data.isEmpty) { data = Data(_data.utf8) }
    }
    
    func initValue() {
        if (data != nil) { characteristic.value = data }
    }
}


enum BLEFunction: String {
    case _initialize = "_initialize"
    case _connectToPeripheral = "_connectToPeripheral"
    case _discoverPeripheralServices = "_discoverPeripheralServices"
    case _discoverPeripheralCharacteristics = "_discoverPeripheralCharacteristics"
    case _readCharacteristicValueForPeripheral = "_readCharacteristicValueForPeripheral"
}

struct BLEError: Error {
    let message: String
    init(_ message: String) {
        self.message = message
    }
    
    public var localizedDescription: String {
        return message
    }
}

struct Pair<A: Hashable, B: Hashable>: Hashable {
  let first: A
  let second: B
}
