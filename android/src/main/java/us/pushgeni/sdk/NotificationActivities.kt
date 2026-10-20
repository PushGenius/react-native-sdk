package us.pushgeni.sdk

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/** Intent action the server sets as the notification's click_action. */
const val NOTIFICATION_OPENED_ACTION = "us.pushgeni.sdk.NOTIFICATION_OPENED"

/**
 * Invisible activity that every PushGenius notification opens. It reports the click, then opens
 * the app the same way its launcher icon would, so this works for cold and warm starts alike.
 */
class NotificationOpenedActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
        finish()
    }

    private fun handle(intent: Intent?) {
        AppContext.attach(applicationContext)
        val extras = intent?.extras
        val data = extras?.keySet()
            ?.filterNot { it.startsWith("google.") || it.startsWith("gcm.") || it == "from" || it == "collapse_key" }
            ?.mapNotNull { key -> extras.getString(key)?.let { key to it } }
            ?.toMap()
            .orEmpty()
        data["notification_id"]?.let { id ->
            Client.emitClick(NotificationClickEvent(id, data))
            Client.track("clicked", id)
        }
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            extras?.let { launch.putExtras(it) }
            startActivity(launch)
        }
    }
}

/** Shows a notification that arrived while the app was in the foreground. */
internal object NotificationDisplay {
    private const val DEFAULT_CHANNEL = "pushgenius_default"

    fun show(context: Context, title: String?, body: String?, channelId: String?, data: Map<String, String>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = resolveChannel(context, manager, channelId)

        val open = Intent(context, NotificationOpenedActivity::class.java).setAction(NOTIFICATION_OPENED_ACTION)
        data.forEach { (key, value) -> open.putExtra(key, value) }
        val requestCode = (data["notification_id"] ?: System.currentTimeMillis().toString()).hashCode()
        val pending = PendingIntent.getActivity(
            context, requestCode, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        @Suppress("DEPRECATION")
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, channel) else Notification.Builder(context)
        val notification = builder
            .setSmallIcon(smallIcon(context))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        try {
            manager.notify(requestCode, notification)
        } catch (_: SecurityException) {
            // Permission was revoked between the check and the call.
        }
    }

    private fun resolveChannel(context: Context, manager: NotificationManager, requested: String?): String {
        if (Build.VERSION.SDK_INT < 26) return DEFAULT_CHANNEL
        if (requested != null && manager.getNotificationChannel(requested) != null) return requested
        if (manager.getNotificationChannel(DEFAULT_CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(DEFAULT_CHANNEL, "Notifications", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        return DEFAULT_CHANNEL
    }

    /** Same icon Firebase uses for background notifications: its meta-data setting, else the app icon. */
    private fun smallIcon(context: Context): Int {
        val info = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        val icon = info.metaData?.getInt("com.google.firebase.messaging.default_notification_icon", 0) ?: 0
        return if (icon != 0) icon else info.icon
    }
}

/** Invisible activity that asks for the Android 13+ notification permission and reports the result. */
class PermissionActivity : Activity() {
    companion object {
        private const val EXTRA_FALLBACK = "fallback_to_settings"
        private const val PREF_ASKED = "asked_notification_permission"
        private val callbacks = mutableListOf<(Boolean) -> Unit>()
        private val main = Handler(Looper.getMainLooper())

        fun request(context: Context, fallbackToSettings: Boolean, callback: (Boolean) -> Unit) {
            synchronized(callbacks) {
                callbacks.add(callback)
                if (callbacks.size > 1) return // a prompt is already showing; share its result
            }
            val from = AppContext.foregroundActivity
            val intent = Intent(from ?: context, PermissionActivity::class.java).putExtra(EXTRA_FALLBACK, fallbackToSettings)
            if (from == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            (from ?: context).startActivity(intent)
        }

        private fun finishWith(granted: Boolean) {
            val pending = synchronized(callbacks) { callbacks.toList().also { callbacks.clear() } }
            main.post { pending.forEach { it(granted) } }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return // recreated mid-prompt; the result still arrives below
        if (Build.VERSION.SDK_INT < 33) {
            finishWith(TokenStore.notificationsEnabled(this))
            finish()
            return
        }
        val permission = Manifest.permission.POST_NOTIFICATIONS
        val prefs = getSharedPreferences("us.pushgeni.sdk", MODE_PRIVATE)
        // After two denials Android stops showing the prompt; the only way back is the settings screen.
        if (prefs.getBoolean(PREF_ASKED, false) && !shouldShowRequestPermissionRationale(permission)) {
            if (intent.getBooleanExtra(EXTRA_FALLBACK, true)) openSettings()
            finishWith(false)
            finish()
            return
        }
        prefs.edit().putBoolean(PREF_ASKED, true).apply()
        requestPermissions(arrayOf(permission), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        finishWith(grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
        finish()
    }

    private fun openSettings() {
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        val intent = if (Build.VERSION.SDK_INT >= 26) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        } else {
            details
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(details)
        }
    }
}
