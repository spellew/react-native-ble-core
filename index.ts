import { NativeModules, NativeEventEmitter } from 'react-native';

const { BLECore } = NativeModules;
const BLECoreEmitter = new NativeEventEmitter(BLECore); 
const peripheralStates = ["disconnected", "connecting", "connected", "disconnecting"];


const init = async (roles: GenericAccessProfileRole[], options?: InitializationOptions) => {
  await BLECore._initialize(roles, options);
};

const startScanning = async (serviceUUIDs: string[], options?: { [key: string]: any }) => {
  await BLECore._startScanning(serviceUUIDs || [], options);
};

const stopScanning = async () => {
  await BLECore._stopScanning();
}

const onPeripheralDiscovered = (handlePeripheralDiscovered: (peripheral: Peripheral) => Promise<void>) => {
  return BLECoreEmitter.addListener("peripheralDiscovered", async (peripheral: Peripheral) => {
    peripheral.state = peripheralStates[peripheral.state as unknown as number];
    await handlePeripheralDiscovered(peripheral);
  });
};

const onPeripheralDisconnected = (handlePeripheralDisconnected: (peripheral: Peripheral) => Promise<void>) => {
  return BLECoreEmitter.addListener("peripheralDisconnected", async (peripheral: Peripheral) => {
    peripheral.state = peripheralStates[peripheral.state as unknown as number];
    await handlePeripheralDisconnected(peripheral);
  });
};

const connectToPeripheral = async ({ id }: Peripheral, options?: { [key: string]: any }) => {
  return await BLECore._connectToPeripheral(id, options);
};

const discoverPeripheralServices = async ({ id }: Peripheral, serviceUUIDs: string[]) => {
  return await BLECore._discoverPeripheralServices(id, serviceUUIDs);
};

const discoverPeripheralCharacteristics = async ({ id }: Peripheral, serviceUUID: string, characteristicsUUIDs: string[]) => {
  return await BLECore._discoverPeripheralCharacteristics(id, serviceUUID, characteristicsUUIDs);
};

const readCharacteristicValueForPeripheral = async ({ id }: Peripheral, serviceUUID: string, characteristicUUID: string) => {
  return await BLECore._readCharacteristicValueForPeripheral(id, serviceUUID, characteristicUUID);
};

const startAdvertising = async (services: Service[]) => {
  await BLECore._startAdvertising(services);
};

const stopAdvertising = async () => {
  await BLECore._stopAdvertising();
}

const onCentralConnected = (handleCentralConnected: (central: Central) => Promise<void>) => {
  return BLECoreEmitter.addListener("centralConnected", async (central: Central) => {
    await handleCentralConnected(central);
  });
};

const onCentralDisconnected = (handleCentralDisconnected: (central: Central) => Promise<void>) => {
  return BLECoreEmitter.addListener("centralDisconnected", async (central: Central) => {
    await handleCentralDisconnected(central);
  });
};

const onReadRequestReceived = (handleReadRequestReceived: (request: ReadRequest) => Promise<void>) => {
  return BLECoreEmitter.addListener("receivedReadRequest", async (request: ReadRequest) => {
    await handleReadRequestReceived(request);
  });
};

const respondToReadRequest = async (requestId: number, accept: boolean) => {
  return await BLECore._respondToReadRequest(requestId, accept);
};


export default {
  init,
  startScanning,
  stopScanning,
  onPeripheralDiscovered,
  onPeripheralDisconnected,
  connectToPeripheral,
  discoverPeripheralServices,
  discoverPeripheralCharacteristics,
  readCharacteristicValueForPeripheral,
  startAdvertising,
  stopAdvertising,
  onCentralConnected,
  onCentralDisconnected,
  onReadRequestReceived,
  respondToReadRequest
};


export enum GenericAccessProfileRole {
  PERIPHERAL = 0,
  CENTRAL = 1
};

// TODO: This can be used to pass options on iOS managers, but this should probably
// be unified to minimize the number of platform specfic options. So, also try 
// to find which options are about the same on iOS and Android. Also, write options
// interfaces for startScanning and startAdvertising.
export interface InitializationOptions {
  [GenericAccessProfileRole.CENTRAL]?: CentralInitializationOptions,
  [GenericAccessProfileRole.PERIPHERAL]?: PeripheralInitializationOptions
}

export interface PeripheralInitializationOptions {
  [key: string]: any;
}

export interface CentralInitializationOptions {
  [key: string]: any;
  pauseScanBetweenPeripherals?: boolean;
}

export interface Peripheral {
  id: number;
  name: string;
  state: string;
  services: Service[] | null;
}

export interface Central {
  id: number;
}

export interface Service {
  uuid: string;
  isPrimary: boolean;
  characteristics: Characteristic[] | null;
}

export interface Characteristic {
  uuid: string;
  properties: number[];
  permissions: number[];
  value: string | null;
}

export interface ReadRequest {
  id: number;
  centralId: number;
  characteristicId: string;
  characteristicValue: string;
}
