package us.pushgeni.sdk

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

internal object Client {
    @Volatile var bigBrainId: String? = null
    @Volatile var externalId: String? = null
    @Volatile var subscriptionId: String? = null
    @Volatile var optedIn: Boolean = true
    private val clickListeners = CopyOnWriteArrayList<(NotificationClickEvent) -> Unit>()
    // Clicks that arrive before any listener exists (e.g. the tap that cold-started the app).
    private val pendingClicks = mutableListOf<NotificationClickEvent>()
    // One thread, so registrations and updates run in order and never race to create two users.
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var prefs: SharedPreferences? = null

    /** Restores the IDs saved by a previous launch, so the device keeps the same user. */
    fun load(context: Context) {
        val p = context.getSharedPreferences("us.pushgeni.sdk", Context.MODE_PRIVATE)
        prefs = p
        bigBrainId = p.getString("pushgenius_id", null)
        externalId = p.getString("external_id", null)
        subscriptionId = p.getString("subscription_id", null)
        optedIn = p.getBoolean("opted_in", true)
    }

    /** Forgets saved IDs when the app switches to a different PushGenius app ID. */
    fun setApp(appId: String) {
        val p = prefs ?: return
        if (p.getString("app_id", null) == appId) return
        bigBrainId = null
        externalId = null
        subscriptionId = null
        optedIn = true
        p.edit().clear().putString("app_id", appId).apply()
    }

    // A notification tap can start the app and report the click before the app calls initialize,
    // so fall back to the app ID and host saved by the previous launch.
    private val appId: String get() = PushGenius.appId.ifEmpty { prefs?.getString("app_id", null).orEmpty() }
    private val host: String get() =
        if (PushGenius.hostSet) PushGenius.host else prefs?.getString("host", null) ?: PushGenius.host

    fun saveHost(host: String?) {
        prefs?.edit()?.apply { if (host == null) remove("host") else putString("host", host) }?.apply()
    }

    private fun save() {
        prefs?.edit()
            ?.putString("pushgenius_id", bigBrainId)
            ?.putString("external_id", externalId)
            ?.putString("subscription_id", subscriptionId)
            ?.putBoolean("opted_in", optedIn)
            ?.apply()
    }

    fun register() {
        io.execute { registerNow() }
    }

    /** Must run on [io]. */
    private fun registerNow() {
        if (appId.isEmpty()) return
        val subs = JSONArray()
        TokenStore.token?.let { token ->
            subs.put(JSONObject().apply {
                put("type", "AndroidPush")
                put("token", token)
                put("enabled", optedIn)
                put("notification_types", if (optedIn) 1 else -2)
                put("sdk", "pushgenius/0.1.0")
                put("device_os", "Android")
            })
        }
        val identity = JSONObject()
        externalId?.let { identity.put("external_id", it) }
        bigBrainId?.let { identity.put("pushgenius_id", it) }
        val body = JSONObject()
            .put("identity", identity)
            .put("subscriptions", subs)
        apply(post("/sdk/apps/$appId/users", body))
    }

    fun login(externalId: String) {
        this.externalId = externalId
        save()
        io.execute {
            val body = JSONObject().put("external_id", externalId)
            subscriptionId?.let { body.put("subscription_id", it) }
            apply(post("/sdk/apps/$appId/login", body))
        }
    }

    fun logout() {
        externalId = null
        bigBrainId = null
        save()
        register()
    }

    fun patch(properties: Map<String, Any>? = null, identity: Map<String, String>? = null) {
        io.execute {
            // Before the first registration finishes there is no user yet; create it, then apply the change.
            if (bigBrainId == null) registerNow()
            val id = bigBrainId ?: return@execute
            val body = JSONObject()
            properties?.let { body.put("properties", JSONObject(it)) }
            identity?.let { body.put("identity", JSONObject(it)) }
            apply(request("PATCH", "/sdk/apps/$appId/users/by/legacy_id/$id", body))
        }
    }

    fun setPermission(granted: Boolean) {
        optedIn = granted
        save()
        io.execute {
            subscriptionId?.let {
                post(
                    "/sdk/apps/$appId/events",
                    JSONObject()
                        .put("event", "permission")
                        .put("subscription_id", it)
                        .put("permission", granted),
                )
            }
            registerNow()
        }
    }

    fun track(event: String, notificationId: String) {
        io.execute {
            val sub = subscriptionId ?: return@execute
            post(
                "/sdk/apps/$appId/events",
                JSONObject()
                    .put("event", event)
                    .put("notification_id", notificationId)
                    .put("subscription_id", sub),
            )
        }
    }

    fun addClickListener(listener: (NotificationClickEvent) -> Unit) {
        val pending = synchronized(pendingClicks) {
            clickListeners.add(listener)
            pendingClicks.toList().also { pendingClicks.clear() }
        }
        main.post { pending.forEach(listener) }
    }

    fun emitClick(event: NotificationClickEvent) {
        synchronized(pendingClicks) {
            if (clickListeners.isEmpty()) {
                pendingClicks.add(event)
                return
            }
        }
        main.post { clickListeners.forEach { it(event) } }
    }

    private fun apply(json: JSONObject?) {
        json ?: return
        json.optJSONObject("identity")?.let {
            bigBrainId = it.optString("pushgenius_id", bigBrainId ?: "").ifEmpty { null }
            externalId = if (it.has("external_id")) it.optString("external_id") else null
        }
        json.optJSONArray("subscriptions")?.optJSONObject(0)?.let {
            subscriptionId = it.optString("id").ifEmpty { null }
            optedIn = it.optBoolean("enabled", true)
        }
        save()
    }

    private fun post(path: String, body: JSONObject) = request("POST", path, body)

    private fun request(method: String, path: String, body: JSONObject): JSONObject? {
        return try {
            val conn = URL(host + path).openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        } catch (_: Exception) {
            null
        }
    }
}
