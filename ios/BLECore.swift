//
//  BLECore.swift
//  CastYourself
//
//  Created by Shamroy Pellew on 6/24/20.
//  Copyright © 2020 Facebook. All rights reserved.
//

import UIKit
import CoreBluetooth


@objc class BLECore: UIViewController, CBCentralManagerDelegate, CBPeripheralManagerDelegate, CBPeripheralDelegate {
  var centralManager: CBCentralManager?
  var peripheralManager: CBPeripheralManager?
  var cbPeripheral: CBPeripheral?

  let MY_UUID = CBUUID(string: "0aae7a9d-be24-438d-9338-f8812c69c177")
  let SERVICE_UUID = CBUUID(string: "30730665-27be-4695-a6fe-6c3ba237070b")
  let CHARACTERISTIC_UUID = CBUUID(string: "400408ca-d099-4be1-a72c-7cdcb11a6eb7")
  let TAG = " _ CY_BLUETOOTH "
  
  
  func setUp() {
    // TODO: add queue so app doesn't stay stuck here
    // This is the scanner and client, discovers services and reads them
    centralManager = CBCentralManager(delegate: self, queue: nil)
    print("Created CBCentralManager" + TAG)
    // Does the serving and advertising? A service was added to this
    peripheralManager = CBPeripheralManager(delegate: self, queue: nil)
    print("Created CBPeripheralManager" + TAG)
  }
  
  func startScanning() {
    print("Starting to scan for peripherals!" + TAG)
    centralManager?.scanForPeripherals(withServices: [MY_UUID])
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
    peripheralManager?.add(service)
    peripheralManager?.startAdvertising([CBAdvertisementDataServiceUUIDsKey: [MY_UUID]])
    characteristic.value = Data(data.utf8)
  }
  
  func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String : Any], rssi RSSI: NSNumber) {
    print("Found peripheral!" + TAG)
    cbPeripheral = peripheral
    peripheral.delegate = self
    
    print("Connecting!" + TAG)
    centralManager?.stopScan()
    centralManager?.connect(peripheral)
  }
  
  func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
    print("Connected!" + TAG)
    print("Discovering services!" + TAG)
    peripheral.discoverServices([SERVICE_UUID])
  }
  
  func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
    print("Disconnected!" + TAG)
    print("Starting to scan for peripherals!" + TAG)
    centralManager?.scanForPeripherals(withServices: [MY_UUID])
  }
  
  func centralManagerDidUpdateState(_ central: CBCentralManager) {
    print("CBCentralManager state did change!" + TAG)
    switch central.state {
      case CBManagerState.poweredOn:
        print("Bluetooth is enabled!" + TAG)
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
  
//  peripheral:didModifyServices:
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
    peripheralManager?.respond(to: request, withResult: CBATTError.success)
  }

  func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
    print("CBPeripheralManager state did change!" + TAG)
    switch peripheral.state {
      case CBManagerState.poweredOn:
        print("CBPeripheralManager is in powered on state!" + TAG)
        startAdvertising()
        break
      default:
        break
    }
  }
}
