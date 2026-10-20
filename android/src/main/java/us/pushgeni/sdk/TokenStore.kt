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
     * Only called while the app is in the foreground (in the background, Android shows the
     * notification itself). Android doesn't display foreground notifications, so the SDK does.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        val id = message.data["notification_id"] ?: return
        AppContext.attach(applicationContext)
        Client.track("received", id)
        val notification = message.notification ?: return
        NotificationDisplay.show(this, notification.title, notification.body, notification.channelId, message.data)
    }
}
