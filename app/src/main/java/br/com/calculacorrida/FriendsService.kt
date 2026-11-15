package br.com.calculacorrida

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.*
import android.os.*
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Executors

/** Explicitly started by the driver. Never restarted by Android or at boot. */
class FriendsService : Service(), LocationListener {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var client: FriendsClient
    private lateinit var locations: LocationManager
    private val sid = UUID.randomUUID().toString()
    private val deadlines = mutableMapOf<String, Long>()
    private val stoppingPeers = mutableSetOf<String>()
    private var active = true
    private var listening = false
    private var busy = false
    private var token: String? = null
    private var lastSent = 0L
    private var lastMessage: String? = null
    private val pulse = object : Runnable {
        override fun run() {
            if (!active) return
            reconcileLocation()
            if (!busy) {
                busy = true
                request("heartbeat", JSONObject().put("session", sid)) { result ->
                    busy = false
                    if (result == null) { connected = false; reconcileLocation(); notice("Sem conexão • posição pode estar desatualizada") }
                    else if (result.optBoolean("session_ended")) stopSelf()
                    else { connected = true; applyState(result) }
                }
            }
            main.postDelayed(this, 20000)
        }
    }
    private val expiry = object : Runnable {
        override fun run() { if (active) { reconcileLocation(); main.postDelayed(this, 1000) } }
    }
    override fun onCreate() {
        super.onCreate()
        logoutToken = null
        token = SessionManager(this).getAccessToken()
        client = FriendsClient(this)
        locations = getSystemService(LocationManager::class.java)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Amigos em atividade", NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(CHAT, "Mensagens de amigos", NotificationManager.IMPORTANCE_DEFAULT))
        notice("Conectando aos amigos…")
        running = true
        main.post(expiry)
        request("online", JSONObject().put("session", sid)) { result ->
            if (result == null) { stopSelf(); return@request }
            sessionId = sid; connected = true; applyState(result)
            main.postDelayed(pulse, 20000)
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            STOP -> stopSelf()
            SHARE -> {
                val peer = intent.getStringExtra("peer") ?: return START_NOT_STICKY
                val enabled = intent.getBooleanExtra("enabled", false)
                if (!enabled) { stoppingPeers.add(peer); deadlines.remove(peer); reconcileLocation() }
                if (enabled && (!connected || sessionId != sid || !permitted())) return START_NOT_STICKY
                // Upgrade the foreground service while the permission-granting activity is visible.
                if (enabled) notice("Preparando compartilhamento…", true)
                request("share", JSONObject().put("peer", peer).put("session", sid).put("enabled", enabled)
                    .put("minutes", intent.getIntExtra("minutes", 15))) { result ->
                    if (result != null) { if (enabled) stoppingPeers.remove(peer); applyState(result) }
                    else { reconcileLocation(); notice("Não foi possível alterar o compartilhamento. Abra Amigos.") }
                }
            }
        }
        return START_NOT_STICKY
    }
    private fun request(action: String, data: JSONObject, done: (JSONObject?) -> Unit) {
        io.execute {
            val response = runCatching { client.call(action, data) }.getOrNull()
            main.post { if (active) done(response) }
        }
    }
    private fun applyState(json: JSONObject) {
        deadlines.clear()
        val shares = json.optJSONArray("shares")
        val now = json.optLong("now_ms")
        if (shares != null) for (i in 0 until shares.length()) {
            val share = shares.getJSONObject(i); val peer = share.optString("peer")
            val remaining = FriendLocationPolicy.remaining(share.optLong("until_ms"), now)
            if (remaining > 0 && peer !in stoppingPeers) deadlines[peer] = SystemClock.elapsedRealtime() + remaining
        }
        reconcileLocation()
        notice(if (listening) "Online • localização compartilhada" else "Online • localização privada")
        val message = json.optJSONObject("inbox")
        if (message != null && message.optString("id") != lastMessage) {
            lastMessage = message.optString("id")
            val open = PendingIntent.getActivity(this, 702, Intent(this, FriendsActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            getSystemService(NotificationManager::class.java).notify(701, Notification.Builder(this, CHAT)
                .setSmallIcon(android.R.drawable.sym_action_chat).setContentTitle("Mensagem de amigo")
                .setContentText("Abra Amigos para ler e responder.").setVisibility(Notification.VISIBILITY_PRIVATE)
                .setContentIntent(open).setAutoCancel(true).build())
        }
    }
    private fun permitted() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun reconcileLocation() {
        deadlines.entries.removeAll { it.value <= SystemClock.elapsedRealtime() }
        sharing = deadlines.isNotEmpty() && permitted() && connected
        if (!sharing) {
            if (listening) locations.removeUpdates(this)
            listening = false
            return
        }
        if (!listening) {
            try {
                // GPS when precise permission exists; approximate location remains supported.
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && locations.isProviderEnabled(LocationManager.GPS_PROVIDER))
                    locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000, 0f, this, Looper.getMainLooper())
                if (locations.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                    locations.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10000, 0f, this, Looper.getMainLooper())
                listening = true
            } catch (_: SecurityException) { listening = false; sharing = false; deadlines.clear() }
        }
    }
    override fun onLocationChanged(location: Location) {
        reconcileLocation()
        if (!active || !sharing || !connected || SystemClock.elapsedRealtime() - lastSent < 9000) return
        val age = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1000000
        if (!location.hasAccuracy() || !FriendLocationPolicy.valid(location.latitude, location.longitude, location.accuracy, age)) return
        lastSent = SystemClock.elapsedRealtime()
        request("location", JSONObject().put("session", sid).put("lat", location.latitude).put("lng", location.longitude)
            .put("accuracy", location.accuracy.toDouble()).put("age_ms", age)) { json ->
            if (json?.optBoolean("no_shares") == true) { deadlines.clear(); reconcileLocation() }
        }
    }
    override fun onProviderDisabled(provider: String) { notice("Localização desligada no aparelho") }
    override fun onProviderEnabled(provider: String) { if (listening) locations.removeUpdates(this); listening = false; reconcileLocation() }
    @Deprecated("Legacy Android callback") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    private fun notice(text: String, prepareLocation: Boolean = false) {
        val open = PendingIntent.getActivity(this, 700, Intent(this, FriendsActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 703, Intent(this, FriendsService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("JHONDRIVER • Amigos").setContentText(text).setOngoing(true).setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(open).addAction(Notification.Action.Builder(null, "Ficar offline", stop).build()).build()
        if (Build.VERSION.SDK_INT >= 29) {
            var type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING else 0
            if ((listening || prepareLocation) && permitted()) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            startForeground(700, notification, type)
        } else startForeground(700, notification)
    }
    override fun onDestroy() {
        active = false; running = false; connected = false; sharing = false; sessionId = null
        main.removeCallbacksAndMessages(null)
        locations.removeUpdates(this)
        val captured = logoutToken ?: SessionManager(this).getAccessToken() ?: token
        if (captured != null) io.execute { runCatching { FriendsClient(this, captured).call("offline", JSONObject().put("session", sid)) } }
        io.shutdown()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        const val STOP = "br.com.calculacorrida.friends.STOP"
        const val SHARE = "br.com.calculacorrida.friends.SHARE"
        private const val CHANNEL = "friends_online"
        private const val CHAT = "friends_messages"
        @Volatile var running = false; private set
        @Volatile var connected = false; private set
        @Volatile var sharing = false; private set
        @Volatile var sessionId: String? = null; private set
        @Volatile private var logoutToken: String? = null
        fun signOut(context: Context, token: String?) { logoutToken = token; stop(context) }
        fun stop(context: Context) { context.stopService(Intent(context, FriendsService::class.java)) }
    }
}
