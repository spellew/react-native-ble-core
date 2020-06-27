#import "React/RCTBridgeModule.h"
#import <Foundation/Foundation.h>


@interface RCT_EXTERN_MODULE(BLECore, NSObject)

_RCT_EXTERN_REMAP_METHOD(
    initialize,
    initialize: (NSArray *)roles
    options: (NSDictionary *)options
    resolve: (RCTPromiseResolveBlock)resolve
    rejecter: (RCTPromiseRejectBlock)reject,
    false
)

@end
