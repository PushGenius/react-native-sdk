package us.pushgeni.sdk

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

internal object TokenStore {
    @Volatile var token: String? = null

    fun start() {
        FirebaseMessaging.getInstance().token.addOnSuccessListener {
            token = it
            Client.register()
        }
    }

    fun requestPermission(fallbackToSettings: Boolean, callback: (Boolean) -> Unit) {
        val context = AppContext.context ?: return callback(false)
        if (Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            // No runtime permission before Android 13; the user can still turn notifications off in settings.
            start()
            callback(notificationsEnabled(context))
            return
        }
        PermissionActivity.request(context, fallbackToSettings) { granted ->
            if (granted) start()
            callback(granted)
        }
    }

    fun notificationsEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 24) return true
        return context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() ?: true
    }
}

class PushGeniusMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        TokenStore.token = token
        Client.register()
    }

    /**
     * PushGenius sends data-only messages, which arrive here whether the app is open, in the background,
     * or not running, so the SDK displays every notification and can report each one as received.
     * Messages sent with a notification block (to older SDKs) only arrive here while the app is open.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val id = data["notification_id"] ?: return
        AppContext.attach(applicationContext)
        val title = message.notification?.title ?: data["pg_title"]
        val body = message.notification?.body ?: data["pg_body"]
        if (title == null && body == null) return
        val channel = message.notification?.channelId ?: data["pg_channel"]
        if (NotificationDisplay.show(this, title, body, channel, data)) {
            Client.track("received", id, data["subscription_id"], data["app_id"])
        }
    }
}
