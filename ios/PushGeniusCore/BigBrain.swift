import Foundation

/// Primary public surface of the SDK.
/// Call `BigBrain.setHost` (or `PushGenius.setHost`) before `initialize`.
public enum BigBrain {
  public static var User: UserNamespace { PushGenius.User }
  public static var Notifications: NotificationsNamespace { PushGenius.Notifications }

  public static func initialize(_ appId: String, withLaunchOptions launchOptions: [AnyHashable: Any]? = nil) {
    PushGenius.initialize(appId, launchOptions: launchOptions)
  }

  public static func login(_ externalId: String) {
    PushGenius.login(externalId)
  }

  public static func logout() {
    PushGenius.logout()
  }

  public static func setHost(_ url: String) {
    PushGenius.setHost(url)
  }
}

public enum PushGenius {
  static var appId: String = ""
  static var host: String = "https://api.pushgeni.us"
  static var hostSet = false
  public static var User = UserNamespace()
  public static var Notifications = NotificationsNamespace()

  public static func setHost(_ url: String) {
    host = url.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
    hostSet = true
  }

  public static func initialize(_ appId: String, launchOptions: [AnyHashable: Any]? = nil) {
    self.appId = appId
    Client.shared.setApp(appId)
    Client.shared.saveHost(hostSet ? host : nil)
    DeviceTokenStore.shared.start()
    Task { await Client.shared.register() }
  }

  public static func login(_ externalId: String) {
    Task { await Client.shared.login(externalId: externalId) }
  }

  public static func logout() {
    Task { await Client.shared.logout() }
  }
}
