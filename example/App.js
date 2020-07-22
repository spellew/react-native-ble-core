/**
 * Sample React Native App
 *
 * adapted from App.js generated by the following command:
 *
 * react-native init example
 *
 * https://github.com/facebook/react-native
 */

import React, { Component } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import BLECore from 'react-native-ble-core';


export default class App extends Component {
  state = {
    status: 'starting',
    message: '--'
  };
  async componentDidMount() {
    const SERVICE_UUID = "30730665-27be-4695-a6fe-6c3ba237070b";
    const CHARACTERISTIC_UUID = "400408ca-d099-4be1-a72c-7cdcb11a6eb7";

    await BLECore.init([{}, null, 0, 1, 978], {
      1: {
        pauseScanBetweenPeripherals: true
      }
    });

    BLECore.startScanning([SERVICE_UUID], undefined);
    console.log("started scanning for peripherals");

    BLECore.onPeripheralDiscovered(async (peripheral) => {
      console.log("peripheral", peripheral);
      await BLECore.connectToPeripheral(peripheral);
      console.log("connected to peripheral");
      const services = await BLECore.discoverPeripheralServices(peripheral, [SERVICE_UUID]);
      console.log("got services", services);
      const characteristics = await BLECore.discoverPeripheralCharacteristics(peripheral, SERVICE_UUID, [CHARACTERISTIC_UUID]);
      console.log("got characteristics", characteristics);
      const value = await BLECore.readCharacteristicValueForPeripheral(peripheral, SERVICE_UUID, CHARACTERISTIC_UUID);
      console.log("got value", value);
    });

    BLECore.onPeripheralDisconnected(peripheral => {
      console.log("disconnected peripheral", peripheral);
    });

    const randomData = Math.random().toString(36).substring(2, 15) + Math.random().toString(36).substring(2, 15);
    console.log("randomData", randomData);
    BLECore.startAdvertising([{
      uuid: SERVICE_UUID,
      isPrimary: true,
      characteristics: [{
        uuid: CHARACTERISTIC_UUID,
        // permissions: [1],
        // properties: [2, 16],
        data: randomData
      }]
    }]);
    console.log("started advertising");
    
    BLECore.onCentralConnected((central) => {
      console.log("central connected", central);
    });
    
    BLECore.onCentralDisconnected((central) => {
      console.log("central disconnected", central);
    });
    
    BLECore.onReadRequestReceived(async (readRequest) => {
      console.log("read request", readRequest);
      await BLECore.respondToReadRequest(readRequest.id, true);
    });
  }
  render() {
    return (
      <View style={styles.container}>
        <Text style={styles.welcome}>BLECore example☆</Text>
        <Text style={styles.instructions}>STATUS: {this.state.status}</Text>
        <Text style={styles.welcome}>☆NATIVE CALLBACK MESSAGE☆</Text>
        <Text style={styles.instructions}>{this.state.message}</Text>
      </View>
    );
  }
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#F5FCFF',
  },
  welcome: {
    fontSize: 20,
    textAlign: 'center',
    margin: 10,
  },
  instructions: {
    textAlign: 'center',
    color: '#333333',
    marginBottom: 5,
  },
});
