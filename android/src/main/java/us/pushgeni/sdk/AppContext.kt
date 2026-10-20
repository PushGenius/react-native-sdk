package us.pushgeni.sdk

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import java.lang.ref.WeakReference

/** Holds the application context and the activity currently in the foreground. */
internal object AppContext {
    @Volatile var context: Context? = null
        private set
    @Volatile private var resumed: WeakReference<Activity>? = null

    val foregroundActivity: Activity? get() = resumed?.get()

    fun attach(context: Context) {
        val app = context.applicationContext
        if (this.context != null) return
        this.context = app
        Client.load(app)
        (app as? Application)?.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                resumed = WeakReference(activity)
            }
            override fun onActivityPaused(activity: Activity) {
                if (resumed?.get() === activity) resumed = null
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}

/**
 * Runs at app start (declared in the manifest), so the SDK has a context and its saved state
 * before the app calls initialize. Same approach Firebase uses.
 */
class PushGeniusInitProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        context?.let { AppContext.attach(it) }
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
