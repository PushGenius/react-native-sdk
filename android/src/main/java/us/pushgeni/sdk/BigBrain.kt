package us.pushgeni.sdk

object BigBrain {
    @JvmStatic
    val User: UserNamespace get() = PushGenius.User
    @JvmStatic
    val Notifications: NotificationsNamespace get() = PushGenius.Notifications

    @JvmStatic
    fun initialize(appId: String) = PushGenius.initialize(appId)

    @JvmStatic
    fun login(externalId: String) = PushGenius.login(externalId)

    @JvmStatic
    fun logout() = PushGenius.logout()

    @JvmStatic
    fun setHost(url: String) = PushGenius.setHost(url)
}

object PushGenius {
    internal var appId: String = ""
    internal var host: String = "https://api.pushgeni.us"
    internal var hostSet = false
    @JvmField val User = UserNamespace()
    @JvmField val Notifications = NotificationsNamespace()

    @JvmStatic
    fun setHost(url: String) {
        host = url.trimEnd('/')
        hostSet = true
    }

    @JvmStatic
    fun initialize(appId: String) {
        this.appId = appId
        Client.setApp(appId)
        Client.saveHost(if (hostSet) host else null)
        TokenStore.start()
        Client.register()
    }

    @JvmStatic
    fun login(externalId: String) = Client.login(externalId)

    @JvmStatic
    fun logout() = Client.logout()
}
