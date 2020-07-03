#import "React/RCTBridgeModule.h"
#import "Foundation/Foundation.h"


@interface RCT_EXTERN_MODULE(BLECore, NSObject)

RCT_EXTERN_METHOD(
    _initialize: (NSArray *)roles
    _options: (NSDictionary *)_options
    resolve: (RCTPromiseResolveBlock)resolve
    rejecter: (RCTPromiseRejectBlock)reject
)

RCT_EXTERN_METHOD(
    _startScanning: (NSArray *)serviceUUIDs
    options: (NSDictionary *)options
    resolve: (RCTPromiseResolveBlock)resolve
    rejecter: (RCTPromiseRejectBlock)reject
)

RCT_EXTERN_METHOD(
    _startAdvertising: (NSArray *)services
    resolve: (RCTPromiseResolveBlock)resolve
    rejecter: (RCTPromiseRejectBlock)reject
)

RCT_EXTERN_METHOD(
    _connectToPeripheral: (NSInteger *)peripheralId
    options: (NSDictionary *)options
    resolve: (RCTPromiseResolveBlock)resolve
    rejecter: (RCTPromiseRejectBlock)reject
)

RCT_EXTERN_METHOD(
    _discoverPeripheralServices: (NSInteger *)peripheralId
    serviceUUIDs: (NSArray *)serviceUUIDs
    resolve: (RCTPromiseResolveBlock)resolve
    rejecter: (RCTPromiseRejectBlock)reject
)

RCT_EXTERN_METHOD(
    _discoverPeripheralCharacteristics: (NSInteger *)peripheralId
    serviceUUID: (NSString *)serviceUUIDs
    characteristicUUIDs: (NSArray *)characteristicsUUIDs
    resolve: (RCTPromiseResolveBlock)resolve
    rejecter: (RCTPromiseRejectBlock)reject
)

RCT_EXTERN_METHOD(
    _readCharacteristicValueForPeripheral: (NSInteger *)peripheralId
    serviceUUID: (NSString *)serviceUUIDs
    characteristicUUID: (NSString *)characteristicUUID
    resolve: (RCTPromiseResolveBlock)resolve
    rejecter: (RCTPromiseRejectBlock)reject
)

RCT_EXTERN_METHOD(
    _respondToReadRequest: (NSInteger *)requestId
    accept: (nonnull NSNumber *)accept
    resolve: (RCTPromiseResolveBlock)resolve
    rejecter: (RCTPromiseRejectBlock)reject
)

@end
