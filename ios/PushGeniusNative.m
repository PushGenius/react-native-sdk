#import "PushGeniusNative.h"
#if __has_include(<PushGenius/PushGenius-Swift.h>)
#import <PushGenius/PushGenius-Swift.h>
#endif

@implementation PushGeniusNative
RCT_EXPORT_MODULE();

- (NSArray<NSString *> *)supportedEvents {
  return @[@"BigBrain-notificationClicked"];
}

RCT_EXPORT_METHOD(setHost:(NSString *)url) {
  // Host is forwarded to the native PushGenius module when linked.
}

RCT_EXPORT_METHOD(initialize:(NSString *)appId) {}
RCT_EXPORT_METHOD(login:(NSString *)externalId) {}
RCT_EXPORT_METHOD(logout) {}
RCT_EXPORT_METHOD(addTags:(NSDictionary *)tags) {}
RCT_EXPORT_METHOD(removeTags:(NSArray *)keys) {}
RCT_EXPORT_METHOD(addAlias:(NSString *)label id:(NSString *)aliasId) {}
RCT_EXPORT_METHOD(optIn) {}
RCT_EXPORT_METHOD(optOut) {}
RCT_EXPORT_METHOD(requestPermission:(BOOL)fallback resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
  resolve(@YES);
}

@end
