import Foundation

public final class UserNamespace {
  public let pushSubscription = PushSubscription()

  public func addTag(key: String, value: String) {
    addTags([key: value])
  }

  public func addTags(_ tags: [String: String]) {
    Task { await Client.shared.patch(properties: ["tags": tags]) }
  }

  public func removeTag(_ key: String) {
    removeTags([key])
  }

  public func removeTags(_ keys: [String]) {
    var tags: [String: String] = [:]
    for k in keys { tags[k] = "" }
    Task { await Client.shared.patch(properties: ["tags": tags]) }
  }

  public func addAlias(label: String, id: String) {
    Task { await Client.shared.patch(identity: [label: id]) }
  }

  public var bigBrainId: String? { Client.shared.bigBrainId }
  public var externalId: String? { Client.shared.externalId }
}

public final class PushSubscription {
  public var id: String? { Client.shared.subscriptionId }
  public var token: String? { DeviceTokenStore.shared.token }
  public var optedIn: Bool { Client.shared.optedIn }

  public func optIn() {
    Task { await Client.shared.setPermission(true) }
  }

  public func optOut() {
    Task { await Client.shared.setPermission(false) }
  }
}

public final class NotificationsNamespace {
  public func requestPermission(_ fallbackToSettings: Bool = true, completion: ((Bool) -> Void)? = nil) {
    DeviceTokenStore.shared.requestPermission(fallbackToSettings: fallbackToSettings) { granted in
      Task { await Client.shared.setPermission(granted) }
      completion?(granted)
    }
  }

  public func addClickListener(_ listener: @escaping (NotificationClickEvent) -> Void) {
    Client.shared.addClickListener(listener)
  }

  public func addForegroundLifecycleListener(_ listener: @escaping (NotificationWillDisplayEvent) -> Void) {
    Client.shared.addForegroundListener(listener)
  }
}

public struct NotificationClickEvent {
  public let notificationId: String
  public let additionalData: [String: Any]
}

public struct NotificationWillDisplayEvent {
  public let notificationId: String
  public let body: String
}
