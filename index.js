import { NativeModules, NativeEventEmitter } from 'react-native';

const { BLECore } = NativeModules;
const BLECoreEmitter = new NativeEventEmitter(BLECore); 
const peripheralStates = ["disconnected", "connecting", "connected", "disconnecting"];


BLECore.init = async (roles, options = undefined) =>
  await BLECore._initialize(roles, options);

BLECore.startScanning = (serviceUUIDs, options) =>
  BLECore._startScanning(serviceUUIDs || [], options);

BLECore.onPeripheralDiscovered = (handlePeripheralDiscovered) => {
  return BLECoreEmitter.addListener("peripheralDiscovered", async (peripheral) => {
    peripheral.state = peripheralStates[peripheral.state];
    await handlePeripheralDiscovered(peripheral);
  });
};

BLECore.onPeripheralDisconnected = (handlePeripheralDisconnected) => {
  return BLECoreEmitter.addListener("peripheralDisconnected", async (peripheral) => {
    peripheral.state = peripheralStates[peripheral.state];
    await handlePeripheralDisconnected(peripheral);
  });
};

BLECore.connectToPeripheral = async ({ id }, options = undefined) => 
  await BLECore._connectToPeripheral(id, options);

BLECore.discoverPeripheralServices = async ({ id }, serviceUUIDs) => 
  await BLECore._discoverPeripheralServices(id, serviceUUIDs);

BLECore.discoverPeripheralCharacteristics = async ({ id }, serviceUUID, characteristicsUUIDs) => 
  await BLECore._discoverPeripheralCharacteristics(id, serviceUUID, characteristicsUUIDs);

BLECore.readCharacteristicValueForPeripheral = async ({ id }, serviceUUID, characteristicUUID) => 
  await BLECore._readCharacteristicValueForPeripheral(id, serviceUUID, characteristicUUID);

BLECore.startAdvertising = (services) => {
  BLECore._startAdvertising(services);
};

BLECore.onCentralConnected = (handleCentralConnected) => {
  return BLECoreEmitter.addListener("centralConnected", async (central) => {
    await handleCentralConnected(central);
  });
};

BLECore.onCentralDisconnected = (handleCentralDisconnected) => {
  return BLECoreEmitter.addListener("centralDisconnected", async (central) => {
    await handleCentralDisconnected(central);
  });
};

BLECore.onReadRequestReceived = (handleReadRequestReceived) => {
  return BLECoreEmitter.addListener("receivedReadRequest", async (request) => {
    await handleReadRequestReceived(request);
  });
}

BLECore.respondToReadRequest = async (requestId, accept) => 
  await BLECore._respondToReadRequest(requestId, accept);

export default BLECore;
