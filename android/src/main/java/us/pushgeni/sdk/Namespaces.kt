package us.pushgeni.sdk

class UserNamespace {
    val pushSubscription = PushSubscription()

    fun addTag(key: String, value: String) = addTags(mapOf(key to value))
    fun addTags(tags: Map<String, String>) = Client.patch(properties = mapOf("tags" to tags))
    fun removeTag(key: String) = removeTags(listOf(key))
    fun removeTags(keys: List<String>) = addTags(keys.associateWith { "" })
    fun addAlias(label: String, id: String) = Client.patch(identity = mapOf(label to id))
    val bigBrainId: String? get() = Client.bigBrainId
    val externalId: String? get() = Client.externalId
}

class PushSubscription {
    val id: String? get() = Client.subscriptionId
    val token: String? get() = TokenStore.token
    val optedIn: Boolean get() = Client.optedIn
    fun optIn() = Client.setPermission(true)
    fun optOut() = Client.setPermission(false)
}

class NotificationsNamespace {
    fun requestPermission(fallbackToSettings: Boolean = true, callback: ((Boolean) -> Unit)? = null) {
        TokenStore.requestPermission(fallbackToSettings) { granted ->
            Client.setPermission(granted)
            callback?.invoke(granted)
        }
    }

    fun addClickListener(listener: (NotificationClickEvent) -> Unit) {
        Client.addClickListener(listener)
    }
}

data class NotificationClickEvent(val notificationId: String, val additionalData: Map<String, Any?>)
