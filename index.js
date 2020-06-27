import { NativeModules } from 'react-native';

const { BLECore } = NativeModules;


BLECore.init = async (roles, options = null) =>
  await BLECore.initialize(roles, options);

export default BLECore;
