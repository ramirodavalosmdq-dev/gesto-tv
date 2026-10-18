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
import android.os.PowerManager
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

    override fun onCreate() {
        super.onCreate()
        goForeground()
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
