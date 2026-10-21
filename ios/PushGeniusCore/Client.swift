import Foundation

/// Sent with every registration.
let sdkVersion = "0.2.0"

/// SDK state is read synchronously by the public API and written from network callbacks,
/// so it lives behind a lock rather than in an actor.
final class Client: @unchecked Sendable {
  static let shared = Client()

  private let lock = NSLock()
  private var _bigBrainId: String?
  private var _externalId: String?
  private var _subscriptionId: String?
  private var _optedIn = true
  private var _clickListeners: [(NotificationClickEvent) -> Void] = []
  // Clicks that arrive before any listener exists (e.g. the tap that cold-started the app).
  private var _pendingClicks: [NotificationClickEvent] = []
  private var _foregroundListeners: [(NotificationWillDisplayEvent) -> Void] = []
  private var _registering = false
  private var _registerAgain = false
  private let defaults = UserDefaults(suiteName: "us.pushgeni.sdk") ?? .standard

  private init() {
    // Restore the IDs saved by a previous launch, so the device keeps the same user.
    _bigBrainId = defaults.string(forKey: "pushgenius_id")
    _externalId = defaults.string(forKey: "external_id")
    _subscriptionId = defaults.string(forKey: "subscription_id")
    _optedIn = defaults.object(forKey: "opted_in") as? Bool ?? true
  }

  /// Forgets saved IDs when the app switches to a different PushGenius app ID.
  func setApp(_ appId: String) {
    guard defaults.string(forKey: "app_id") != appId else { return }
    locked {
      _bigBrainId = nil
      _externalId = nil
      _subscriptionId = nil
      _optedIn = true
    }
    for key in ["pushgenius_id", "external_id", "subscription_id", "opted_in"] {
      defaults.removeObject(forKey: key)
    }
    defaults.set(appId, forKey: "app_id")
  }

  // A notification tap can start the app and report the click before the app calls initialize,
  // so fall back to the app ID and host saved by the previous launch.
  private var appId: String {
    PushGenius.appId.isEmpty ? (defaults.string(forKey: "app_id") ?? "") : PushGenius.appId
  }

  private var host: String {
    PushGenius.hostSet ? PushGenius.host : (defaults.string(forKey: "host") ?? PushGenius.host)
  }

  func saveHost(_ host: String?) {
    defaults.set(host, forKey: "host")
  }

  /// Call with the lock held.
  private func saveLocked() {
    defaults.set(_bigBrainId, forKey: "pushgenius_id")
    defaults.set(_externalId, forKey: "external_id")
    defaults.set(_subscriptionId, forKey: "subscription_id")
    defaults.set(_optedIn, forKey: "opted_in")
  }

  private func locked<T>(_ body: () -> T) -> T {
    lock.lock()
    defer { lock.unlock() }
    return body()
  }

  var bigBrainId: String? { locked { _bigBrainId } }
  var externalId: String? { locked { _externalId } }
  var subscriptionId: String? { locked { _subscriptionId } }
  var optedIn: Bool { locked { _optedIn } }

  func addClickListener(_ listener: @escaping (NotificationClickEvent) -> Void) {
    let pending: [NotificationClickEvent] = locked {
      _clickListeners.append(listener)
      defer { _pendingClicks = [] }
      return _pendingClicks
    }
    if !pending.isEmpty { DispatchQueue.main.async { pending.forEach(listener) } }
  }

  func addForegroundListener(_ listener: @escaping (NotificationWillDisplayEvent) -> Void) {
    locked { _foregroundListeners.append(listener) }
  }

  /// Registrations run one at a time; a request made while one is in flight runs once it finishes,
  /// so two concurrent calls on first launch can't create two users.
  func register() async {
    let start: Bool = locked {
      if _registering {
        _registerAgain = true
        return false
      }
      _registering = true
      return true
    }
    guard start else { return }
    while true {
      locked { _registerAgain = false }
      await registerOnce()
      let again: Bool = locked {
        if !_registerAgain { _registering = false }
        return _registerAgain
      }
      if !again { return }
    }
  }

  private func registerOnce() async {
    guard !appId.isEmpty else { return }
    let (optedIn, externalId, bigBrainId) = locked { (_optedIn, _externalId, _bigBrainId) }
    var subscriptions: [[String: Any]] = []
    if let token = DeviceTokenStore.shared.token {
      subscriptions.append([
        "type": "iOSPush",
        "token": token,
        "enabled": optedIn,
        "notification_types": optedIn ? 1 : -2,
        "sdk": "pushgenius/\(sdkVersion)",
        "device_os": "iOS",
      ])
    }
    var identity: [String: String] = [:]
    if let externalId { identity["external_id"] = externalId }
    if let bigBrainId { identity["pushgenius_id"] = bigBrainId }
    let body: [String: Any] = [
      "identity": identity,
      "subscriptions": subscriptions,
    ]
    if let json = await post(path: "/sdk/apps/\(appId)/users", body: body) {
      apply(json)
    }
  }

  func login(externalId: String) async {
    locked {
      _externalId = externalId
      saveLocked()
    }
    var body: [String: Any] = ["external_id": externalId]
    if let subscriptionId { body["subscription_id"] = subscriptionId }
    if let json = await post(path: "/sdk/apps/\(appId)/login", body: body) {
      apply(json)
    }
  }

  func logout() async {
    locked {
      _externalId = nil
      _bigBrainId = nil
      saveLocked()
    }
    await register()
  }

  func patch(properties: [String: Any]? = nil, identity: [String: String]? = nil) async {
    var body: [String: Any] = [:]
    if let properties { body["properties"] = properties }
    if let identity { body["identity"] = identity }
    // Before the first registration finishes there is no user yet; create it, then apply the change.
    if bigBrainId == nil { await register() }
    guard let bigBrainId else { return }
    _ = await patch(
      path: "/sdk/apps/\(appId)/users/by/legacy_id/\(bigBrainId)",
      body: body
    )
  }

  func setPermission(_ granted: Bool) async {
    locked {
      _optedIn = granted
      saveLocked()
    }
    if let subscriptionId {
      _ = await post(path: "/sdk/apps/\(appId)/events", body: [
        "event": "permission",
        "subscription_id": subscriptionId,
        "permission": granted,
      ])
    }
    await register()
  }

  func emitClick(_ event: NotificationClickEvent) {
    let listeners: [(NotificationClickEvent) -> Void] = locked {
      if _clickListeners.isEmpty { _pendingClicks.append(event) }
      return _clickListeners
    }
    DispatchQueue.main.async { listeners.forEach { $0(event) } }
  }

  func emitForeground(_ event: NotificationWillDisplayEvent) {
    let listeners = locked { _foregroundListeners }
    DispatchQueue.main.async { listeners.forEach { $0(event) } }
  }

  /// The notification's own app and subscription IDs win over the saved ones.
  func track(event: String, notificationId: String, subscriptionId: String? = nil, appId: String? = nil) {
    Task {
      guard let subscriptionId = subscriptionId ?? self.subscriptionId else { return }
      let appId = appId ?? self.appId
      guard !appId.isEmpty else { return }
      _ = await self.post(path: "/sdk/apps/\(appId)/events", body: [
        "event": event,
        "notification_id": notificationId,
        "subscription_id": subscriptionId,
      ])
    }
  }

  private func apply(_ json: [String: Any]) {
    locked {
      if let identity = json["identity"] as? [String: Any] {
        _bigBrainId = identity["pushgenius_id"] as? String
        _externalId = identity["external_id"] as? String
      }
      if let subs = json["subscriptions"] as? [[String: Any]], let first = subs.first {
        _subscriptionId = first["id"] as? String
        if let enabled = first["enabled"] as? Bool { _optedIn = enabled }
      }
      saveLocked()
    }
  }

  private func post(path: String, body: [String: Any]) async -> [String: Any]? {
    await request(method: "POST", path: path, body: body)
  }

  private func patch(path: String, body: [String: Any]) async -> [String: Any]? {
    await request(method: "PATCH", path: path, body: body)
  }

  private func request(method: String, path: String, body: [String: Any]) async -> [String: Any]? {
    guard let url = URL(string: host + path) else { return nil }
    var req = URLRequest(url: url)
    req.httpMethod = method
    req.setValue("application/json", forHTTPHeaderField: "Content-Type")
    req.httpBody = try? JSONSerialization.data(withJSONObject: body)
    do {
      let (data, response) = try await URLSession.shared.data(for: req)
      guard let status = (response as? HTTPURLResponse)?.statusCode, (200..<300).contains(status) else { return nil }
      return (try JSONSerialization.jsonObject(with: data) as? [String: Any])
    } catch {
      return nil
    }
  }
}
