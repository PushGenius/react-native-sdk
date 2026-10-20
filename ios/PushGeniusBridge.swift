import Foundation

/// Objective-C facade over the vendored PushGenius iOS SDK (ios/PushGeniusCore),
/// called by the React Native module in PushGeniusNative.m.
@objc(PushGeniusBridge)
public final class PushGeniusBridge: NSObject {
  private static let lock = NSLock()
  private static var listener: (([String: Any]) -> Void)?
  private static var pendingClicks: [[String: Any]] = []

  /// Runs on the main thread when the app finishes launching. Hooks notification callbacks and
  /// keeps click events until JS subscribes, so a tap that cold-starts the app reaches JS.
  @objc public static func attachEarly() {
    BigBrain.Notifications.addClickListener { event in
      deliver(["notificationId": event.notificationId, "additionalData": event.additionalData])
    }
    DeviceTokenStore.installHooks()
  }

  private static func deliver(_ event: [String: Any]) {
    lock.lock()
    let current = listener
    if current == nil { pendingClicks.append(event) }
    lock.unlock()
    current?(event)
  }

  @objc public static func startListening(_ handler: @escaping ([String: Any]) -> Void) {
    lock.lock()
    listener = handler
    let pending = pendingClicks
    pendingClicks = []
    lock.unlock()
    pending.forEach(handler)
  }

  @objc public static func stopListening() {
    lock.lock()
    listener = nil
    lock.unlock()
  }

  @objc public static func setHost(_ url: String) {
    PushGenius.setHost(url)
  }

  @objc public static func start(appId: String) {
    BigBrain.initialize(appId)
  }

  @objc public static func login(_ externalId: String) {
    BigBrain.login(externalId)
  }

  @objc public static func logout() {
    BigBrain.logout()
  }

  @objc public static func addTags(_ tags: [String: Any]) {
    BigBrain.User.addTags(tags.mapValues { "\($0)" })
  }

  @objc public static func removeTags(_ keys: [String]) {
    BigBrain.User.removeTags(keys)
  }

  @objc public static func addAlias(label: String, id: String) {
    BigBrain.User.addAlias(label: label, id: id)
  }

  @objc public static func optIn() {
    BigBrain.User.pushSubscription.optIn()
  }

  @objc public static func optOut() {
    BigBrain.User.pushSubscription.optOut()
  }

  @objc public static func requestPermission(_ fallbackToSettings: Bool, completion: @escaping (Bool) -> Void) {
    BigBrain.Notifications.requestPermission(fallbackToSettings, completion: completion)
  }

  @objc public static var subscriptionId: String? { BigBrain.User.pushSubscription.id }
  @objc public static var token: String? { BigBrain.User.pushSubscription.token }
  @objc public static var optedIn: Bool { BigBrain.User.pushSubscription.optedIn }
}
