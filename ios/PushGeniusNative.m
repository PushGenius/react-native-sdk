#import "PushGeniusNative.h"
#if __has_include(<PushGeniusReactNative/PushGeniusReactNative-Swift.h>)
#import <PushGeniusReactNative/PushGeniusReactNative-Swift.h>
#else
#import "PushGeniusReactNative-Swift.h"
#endif

static NSString *const PushGeniusClickEvent = @"BigBrain-notificationClicked";

// Attach before JS loads so a notification tap that cold-starts the app isn't missed.
// (RCT_EXPORT_MODULE already defines +load, so this runs as a load-time constructor instead.)
__attribute__((constructor)) static void PushGeniusAttachAtLaunch(void) {
  [[NSNotificationCenter defaultCenter] addObserverForName:UIApplicationDidFinishLaunchingNotification
                                                    object:nil
                                                     queue:[NSOperationQueue mainQueue]
                                                usingBlock:^(__unused NSNotification *note) {
    [PushGeniusBridge attachEarly];
  }];
}

@implementation PushGeniusNative

RCT_EXPORT_MODULE();

+ (BOOL)requiresMainQueueSetup {
  return NO;
}

- (NSArray<NSString *> *)supportedEvents {
  return @[PushGeniusClickEvent];
}

- (void)startObserving {
  __weak PushGeniusNative *weakSelf = self;
  [PushGeniusBridge startListening:^(NSDictionary<NSString *, id> *event) {
    [weakSelf sendEventWithName:PushGeniusClickEvent body:event];
  }];
}

- (void)stopObserving {
  [PushGeniusBridge stopListening];
}

RCT_EXPORT_METHOD(setHost:(NSString *)url) {
  [PushGeniusBridge setHost:url];
}

RCT_EXPORT_METHOD(initialize:(NSString *)appId) {
  [PushGeniusBridge startWithAppId:appId];
}

RCT_EXPORT_METHOD(login:(NSString *)externalId) {
  [PushGeniusBridge login:externalId];
}

RCT_EXPORT_METHOD(logout) {
  [PushGeniusBridge logout];
}

RCT_EXPORT_METHOD(addTags:(NSDictionary *)tags) {
  [PushGeniusBridge addTags:tags];
}

RCT_EXPORT_METHOD(removeTags:(NSArray<NSString *> *)keys) {
  [PushGeniusBridge removeTags:keys];
}

RCT_EXPORT_METHOD(addAlias:(NSString *)label id:(NSString *)aliasId) {
  [PushGeniusBridge addAliasWithLabel:label id:aliasId];
}

RCT_EXPORT_METHOD(optIn) {
  [PushGeniusBridge optIn];
}

RCT_EXPORT_METHOD(optOut) {
  [PushGeniusBridge optOut];
}

RCT_EXPORT_METHOD(requestPermission:(BOOL)fallback
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
  [PushGeniusBridge requestPermission:fallback completion:^(BOOL granted) {
    resolve(@(granted));
  }];
}

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(getSubscriptionId) {
  return PushGeniusBridge.subscriptionId ?: (id)[NSNull null];
}

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(getToken) {
  return PushGeniusBridge.token ?: (id)[NSNull null];
}

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(getOptedIn) {
  return @(PushGeniusBridge.optedIn);
}

@end
