package com.gestotv.tv

import android.content.Context
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlin.random.Random

/** Estado compartido entre el servicio (que recibe órdenes) y la pantalla principal. */
object Status {
    /** Puertos a probar en orden: algunos TV ya usan el 8443 para servicios propios. */
    val PORTS = listOf(8443, 9443, 10443, 18443, 28443, 38443)
    @Volatile var port = PORTS[0]

    @Volatile var serviceRunning = false
    @Volatile var serverRunning = false
    @Volatile var serverError: String? = null
    @Volatile var clients = 0
    @Volatile var lastCommand: String = "—"
    @Volatile var lastCommandAt = 0L

    private val logLines = ArrayDeque<String>()
    fun log(msg: String) {
        val t = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        synchronized(logLines) { logLines.addLast("$t $msg"); while (logLines.size > 6) logLines.removeFirst() }
        android.util.Log.i("GestoTV", msg)
    }
    fun logText(): String = synchronized(logLines) { logLines.joinToString("\n") }

    fun pin(ctx: Context): String {
        val prefs = ctx.getSharedPreferences("gesto", Context.MODE_PRIVATE)
        return prefs.getString("pin", null) ?: Random.nextInt(1000, 9999).toString().also {
            prefs.edit().putString("pin", it).apply()
        }
    }

    /** IPs locales (Wi-Fi o cable) donde el celular puede encontrar al TV. */
    fun localIps(): List<String> = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .map { it.hostAddress ?: "" }
            .filter { it.isNotEmpty() }
    } catch (e: Exception) { emptyList() }

    fun url(): String = localIps().firstOrNull()?.let { "https://$it:$port" } ?: "(sin red)"
}
