package com.gestotv.tv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Servicio permanente (en primer plano) que mantiene vivo el servidor aunque
 * el TV pase a YouTube, Netflix, etc. Android no lo pausa ni lo cierra para liberar memoria.
 */
class ServerService : Service() {

    companion object {
        private const val TAG = "GestoTV"
        private const val CHANNEL = "gesto_server"
        @Volatile private var server: GestoServer? = null

        fun start(ctx: Context) {
            val i = Intent(ctx, ServerService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
            } catch (e: Exception) {
                Status.log("No pude iniciar el servicio: $e")
            }
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    private val watch = Handler(Looper.getMainLooper())
    private var downSince = 0L
    private var lastFix = 0L
    private val watchdog = object : Runnable {
        override fun run() {
            try { checkA11y() } catch (e: Throwable) { Status.log("Vigilancia: ${e.javaClass.simpleName}") }
            watch.postDelayed(this, 3000)
        }
    }

    /**
     * Si el servicio de gestos se cayó, lo vuelve a activar solo (apagar y prender en Accesibilidad).
     * Necesita un permiso que se da una sola vez desde la PC:
     *   adb shell pm grant com.gestotv.tv android.permission.WRITE_SECURE_SETTINGS
     */
    private fun checkA11y() {
        if (GestoAccessibilityService.instance != null) { downSince = 0; return }
        val now = System.currentTimeMillis()
        if (downSince == 0L) { downSince = now; return }
        if (now - downSince < 4000 || now - lastFix < 15000) return
        lastFix = now
        if (checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Status.log("Servicio de gestos caído: reactivalo en Accesibilidad")
            return
        }
        val me = "$packageName/${GestoAccessibilityService::class.java.name}"
        val cr = contentResolver
        val key = Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        val others = (Settings.Secure.getString(cr, key) ?: "").split(':')
            .filter { it.isNotBlank() && !it.equals(me, true) && !it.equals("$packageName/.GestoAccessibilityService", true) }
        Settings.Secure.putString(cr, key, others.joinToString(":"))
        watch.postDelayed({
            try {
                Settings.Secure.putString(cr, key, (others + me).joinToString(":"))
                Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
                Status.log("Servicio de gestos reactivado solo")
            } catch (e: Throwable) { Status.log("No pude reactivar: ${e.javaClass.simpleName}") }
        }, 700)
    }

    override fun onCreate() {
        super.onCreate()
        Status.installCrashLog(this)
        goForeground()
        watch.postDelayed(watchdog, 5000)
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GestoTV:server").apply { setReferenceCounted(false); acquire() }
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "GestoTV:wifi").apply { setReferenceCounted(false); acquire() }
        } catch (e: Exception) { Status.log("Locks: $e") }
        startServer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        if (server == null) startServer()
        return START_STICKY  // si el sistema lo cierra, lo vuelve a levantar
    }

    override fun onDestroy() {
        Status.log("Servicio detenido por el sistema")
        watch.removeCallbacksAndMessages(null)
        try { server?.stop() } catch (_: Exception) {}
        server = null
        Status.serverRunning = false
        try { wakeLock?.release() } catch (_: Exception) {}
        try { wifiLock?.release() } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun goForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Gesto TV", NotificationManager.IMPORTANCE_LOW))
        }
        val n: Notification = (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this))
            .setContentTitle("Gesto TV activo")
            .setContentText("Esperando gestos del celular")
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true)
            .build()
        startForeground(1, n)
    }

    private fun startServer() {
        if (server != null) return
        Thread {
            synchronized(ServerService::class.java) {
                if (server != null) return@Thread
                val factory = try { Tls.serverSocketFactory(this) } catch (e: Exception) {
                    Status.serverError = "Certificado: $e"; Status.log("TLS: $e"); return@Thread
                }
                var lastError: Exception? = null
                for (p in Status.PORTS) {
                    try {
                        val s = GestoServer(this, p)
                        s.makeSecure(factory, null)
                        s.start(0, false)
                        server = s
                        Status.port = p
                        Status.serverRunning = true
                        Status.serverError = null
                        Status.log("Servidor listo en ${Status.url()}")
                        return@Thread
                    } catch (e: Exception) {
                        lastError = e
                        Log.w(TAG, "Puerto $p ocupado: $e")
                    }
                }
                Status.serverRunning = false
                Status.serverError = lastError?.toString() ?: "sin puertos libres"
                Status.log("Sin servidor: ${Status.serverError}")
            }
        }.start()
    }
}
