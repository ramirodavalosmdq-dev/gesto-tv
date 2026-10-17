package com.gestotv.tv

import android.content.Context
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlin.random.Random

/** Estado compartido entre el servicio (que recibe órdenes) y la pantalla principal. */
object Status {
    const val PORT = 8443

    @Volatile var serviceRunning = false
    @Volatile var serverRunning = false
    @Volatile var serverError: String? = null
    @Volatile var clients = 0
    @Volatile var lastCommand: String = "—"
    @Volatile var lastCommandAt = 0L

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

    fun url(): String = localIps().firstOrNull()?.let { "https://$it:$PORT" } ?: "(sin red)"
}
