import Foundation
#if canImport(UIKit)
import ObjectiveC
import UIKit
#endif
#if canImport(UserNotifications)
import UserNotifications
#endif

final class DeviceTokenStore: @unchecked Sendable {
  static let shared = DeviceTokenStore()

  private let lock = NSLock()
  private var _token: String?

  var token: String? {
    lock.lock()
    defer { lock.unlock() }
    return _token
  }

  func start() {
#if canImport(UIKit)
    DispatchQueue.main.async {
      if UIApplication.shared.delegate != nil {
        Self.attach()
      } else {
        // initialize was called before the app finished launching; wait for the delegate.
        // didFinishLaunching posts once, so the observer never fires again.
        NotificationCenter.default.addObserver(
          forName: UIApplication.didFinishLaunchingNotification, object: nil, queue: .main
        ) { _ in
          Self.attach()
        }
      }
    }
#endif
  }

#if canImport(UIKit)
  private static func attach() {
    installHooks()
    UIApplication.shared.registerForRemoteNotifications()
  }

  /// Hooks the APNs token and notification callbacks without registering. Wrappers call this
  /// when the app finishes launching, so a tap that cold-starts the app is not missed.
  /// Must run on the main thread.
  static func installHooks() {
    AppDelegateHook.install()
    NotificationDelegate.shared.install()
  }
#endif

  func requestPermission(fallbackToSettings: Bool, completion: @escaping (Bool) -> Void) {
#if canImport(UserNotifications) && canImport(UIKit)
    let center = UNUserNotificationCenter.current()
    center.getNotificationSettings { settings in
      // iOS only prompts once; after a denial the only way back is the Settings app.
      if settings.authorizationStatus == .denied {
        DispatchQueue.main.async {
          if fallbackToSettings, let url = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(url)
          }
          completion(false)
        }
        return
      }
      center.requestAuthorization(options: [.alert, .badge, .sound]) { granted, _ in
        DispatchQueue.main.async {
          UIApplication.shared.registerForRemoteNotifications()
          completion(granted)
        }
      }
    }
#else
    completion(false)
#endif
  }

  func didRegister(deviceToken: Data) {
    let hex = deviceToken.map { String(format: "%02x", $0) }.joined()
    lock.lock()
    let changed = _token != hex
    _token = hex
    lock.unlock()
    if changed { Task { await Client.shared.register() } }
  }
}

#if canImport(UIKit)
/// Wraps the app delegate's APNs token callback so host apps don't have to forward the token.
/// The app's own implementation, if any, still runs first.
enum AppDelegateHook {
  private static var installed = false
  private typealias TokenCallback = @convention(c) (AnyObject, Selector, UIApplication, Data) -> Void

  static func install() {
    guard !installed, let delegate = UIApplication.shared.delegate else { return }
    installed = true
    let cls: AnyClass = type(of: delegate)
    let selector = #selector(UIApplicationDelegate.application(_:didRegisterForRemoteNotificationsWithDeviceToken:))
    let method = class_getInstanceMethod(cls, selector)
    let original = method.map(method_getImplementation)
    let block: @convention(block) (AnyObject, UIApplication, Data) -> Void = { target, application, token in
      if let original {
        unsafeBitCast(original, to: TokenCallback.self)(target, selector, application, token)
      }
      DeviceTokenStore.shared.didRegister(deviceToken: token)
    }
    let replacement = imp_implementationWithBlock(block)
    // Adding succeeds when the class doesn't define the method itself (even if a superclass does);
    // otherwise replace the class's own implementation.
    if !class_addMethod(cls, selector, replacement, "v@:@@"), let method {
      method_setImplementation(method, replacement)
    }
  }
}
#endif

#if canImport(UserNotifications) && canImport(UIKit)
/// Becomes the notification center delegate and forwards every callback to whichever
/// delegate the app (or its framework, e.g. Capacitor) had already installed.
final class NotificationDelegate: NSObject, UNUserNotificationCenterDelegate {
  static let shared = NotificationDelegate()
  private weak var previous: UNUserNotificationCenterDelegate?

  func install() {
    let center = UNUserNotificationCenter.current()
    guard center.delegate !== self else { return }
    previous = center.delegate
    center.delegate = self
  }

  private static func notificationId(_ userInfo: [AnyHashable: Any]) -> String? {
    let data = userInfo["data"] as? [String: Any]
    let id = (data?["notification_id"] ?? userInfo["notification_id"]) as? String
    return id?.isEmpty == false ? id : nil
  }

  func userNotificationCenter(
    _ center: UNUserNotificationCenter,
    willPresent notification: UNNotification,
    withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
  ) {
    if let id = Self.notificationId(notification.request.content.userInfo) {
      Client.shared.emitForeground(NotificationWillDisplayEvent(notificationId: id, body: notification.request.content.body))
      let data = notification.request.content.userInfo["data"] as? [String: Any]
      Client.shared.track(
        event: "received", notificationId: id,
        subscriptionId: data?["subscription_id"] as? String, appId: data?["app_id"] as? String
      )
    }
    let selector = #selector(UNUserNotificationCenterDelegate.userNotificationCenter(_:willPresent:withCompletionHandler:))
    if let previous, previous.responds(to: selector) {
      previous.userNotificationCenter?(center, willPresent: notification, withCompletionHandler: completionHandler)
    } else {
      completionHandler([.banner, .list, .sound, .badge])
    }
  }

  func userNotificationCenter(
    _ center: UNUserNotificationCenter,
    didReceive response: UNNotificationResponse,
    withCompletionHandler completionHandler: @escaping () -> Void
  ) {
    let userInfo = response.notification.request.content.userInfo
    if let id = Self.notificationId(userInfo) {
      let extra = (userInfo["data"] as? [String: Any]) ?? [:]
      Client.shared.emitClick(NotificationClickEvent(notificationId: id, additionalData: extra))
      Client.shared.track(
        event: "clicked", notificationId: id,
        subscriptionId: extra["subscription_id"] as? String, appId: extra["app_id"] as? String
      )
    }
    let selector = #selector(UNUserNotificationCenterDelegate.userNotificationCenter(_:didReceive:withCompletionHandler:))
    if let previous, previous.responds(to: selector) {
      previous.userNotificationCenter?(center, didReceive: response, withCompletionHandler: completionHandler)
    } else {
      completionHandler()
    }
  }
}
#endif
