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
        toFile(msg)
    }

    // ---------------- REGISTRO PERMANENTE (pedido de Rami: que todo quede grabado desde que se enciende la app) ----
    // Archivo dentro del TV, siempre activo, sin depender de la notebook. Se descarga con
    //   https://IP-DEL-TV:9443/registro?pin=CODIGO      (o adb pull /sdcard/Android/data/com.gestotv.tv/files/registro.txt)
    // Cuando pasa de ~3 MB se renombra a registro-anterior.txt y empieza uno nuevo.
    @Volatile private var logFile: java.io.File? = null
    private val fileWorker = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val fmtFile = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US)

    fun initFile(ctx: Context) {
        if (logFile != null) return
        val dir = ctx.applicationContext.getExternalFilesDir(null) ?: ctx.applicationContext.filesDir
        logFile = java.io.File(dir, "registro.txt")
        toFile("===== App iniciada (v${BuildConfigInfo.version(ctx)}) =====")
    }
    fun logFile(): java.io.File? = logFile

    /** Solo al archivo (y logcat), sin ocupar las 6 líneas de la pantalla: para cada orden que llega. */
    fun trace(msg: String) { android.util.Log.d("GestoTV", msg); toFile(msg) }

    private fun toFile(msg: String) {
        val f = logFile ?: return
        val line = fmtFile.format(java.util.Date()) + "  " + msg + "\n"
        fileWorker.execute {
            try {
                if (f.length() > 3_000_000L) { val old = java.io.File(f.parentFile, "registro-anterior.txt"); old.delete(); f.renameTo(old) }
                f.appendText(line)
            } catch (_: Throwable) {}
        }
    }
    fun logText(): String = synchronized(logLines) { logLines.joinToString("\n") }

    /** Guarda la causa si la app se cierra por un error, para mostrarla la próxima vez. */
    fun installCrashLog(ctx: Context) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        if (prev is CrashLogger) return
        Thread.setDefaultUncaughtExceptionHandler(CrashLogger(ctx.applicationContext, prev))
    }
    private class CrashLogger(val ctx: Context, val prev: Thread.UncaughtExceptionHandler?) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread, e: Throwable) {
            try {
                val where = e.stackTrace.firstOrNull { it.className.startsWith("com.gestotv") }?.let { "${it.fileName}:${it.lineNumber}" } ?: ""
                ctx.getSharedPreferences("gesto", Context.MODE_PRIVATE).edit()
                    .putString("lastCrash", "${e.javaClass.simpleName} $where").commit()
            } catch (_: Throwable) {}
            prev?.uncaughtException(t, e)
        }
    }
    fun reportLastCrash(ctx: Context) {
        val p = ctx.getSharedPreferences("gesto", Context.MODE_PRIVATE)
        p.getString("lastCrash", null)?.let { log("Último cierre: $it"); p.edit().remove("lastCrash").apply() }
    }

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
