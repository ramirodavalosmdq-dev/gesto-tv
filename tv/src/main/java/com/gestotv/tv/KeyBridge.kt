package com.gestotv.tv

import android.content.Context
import dadb.AdbKeyPair
import dadb.AdbShellStream
import dadb.Dadb
import java.io.File
import java.util.concurrent.Executors

/**
 * Teclas reales del control remoto (flechas, OK) usando la depuración del propio TV.
 * Se conecta a sí mismo (127.0.0.1:5555) como si fuera la PC con adb, y manda "input keyevent".
 * Así funciona en cualquier pantalla, aunque la app no muestre sus elementos (Flow, YouTube…).
 * La primera vez el TV pregunta "¿Permitir depuración?": hay que marcar "Permitir siempre".
 * Si la depuración está apagada, Gesto TV sigue usando el método anterior.
 */
object KeyBridge {
    @Volatile var ready = false
        private set
    @Volatile private var connecting = false
    private var dadb: Dadb? = null
    private var shell: AdbShellStream? = null
    private val worker = Executors.newSingleThreadExecutor()

    const val UP = 19; const val DOWN = 20; const val LEFT = 21; const val RIGHT = 22; const val CENTER = 23

    /** Intenta conectarse en segundo plano (no bloquea). */
    fun start(ctx: Context) {
        if (ready || connecting) return
        connecting = true
        val dir = ctx.applicationContext.filesDir
        Thread {
            try {
                Status.log("Teclas reales: conectando (si el TV pregunta, elegí Permitir siempre)")
                open(dir)
                ready = true
                Status.log("Teclas reales del control: activas ✅")
                startTouch(ctx)
            } catch (e: Throwable) {
                close()
                Status.log("Teclas reales no disponibles (${e.javaClass.simpleName}); uso el método anterior")
            } finally { connecting = false }
        }.start()
    }

    private fun keys(dir: File): AdbKeyPair {
        val priv = File(dir, "adbkey"); val pub = File(dir, "adbkey.pub")
        if (!priv.exists() || !pub.exists()) AdbKeyPair.generate(priv, pub)
        return AdbKeyPair.read(priv, pub)
    }

    private fun open(dir: File) {
        val d = Dadb.create("127.0.0.1", 5555, keys(dir))
        val s = d.openShell()
        dadb = d; shell = s
        // descarta lo que devuelva la consola para que nunca se llene
        Thread { try { while (true) s.read() } catch (_: Throwable) {} }.apply { isDaemon = true }.start()
    }

    private fun close() {
        try { inj?.close() } catch (_: Throwable) {}
        inj = null; touchReady = false
        try { shell?.close() } catch (_: Throwable) {}
        try { dadb?.close() } catch (_: Throwable) {}
        shell = null; dadb = null
    }

    // ---------------- DEDO REAL (toques de pantalla táctil inyectados, ver Injector.java) ----------------
    @Volatile var touchReady = false
        private set
    @Volatile private var touchStarting = false
    private var inj: AdbShellStream? = null
    private val touchWorker = Executors.newSingleThreadExecutor()

    /** Arranca el inyector de toques (una vez, en segundo plano). Requiere que la depuración ya esté conectada. */
    fun startTouch(ctx: Context) {
        if (touchReady || touchStarting) return
        val d = dadb ?: return
        touchStarting = true
        val apk = ctx.applicationInfo.sourceDir
        touchWorker.execute {
            try {
                val s = d.openShell("CLASSPATH=$apk exec app_process /system/bin com.gestotv.tv.Injector")
                inj = s
                Thread {
                    try {
                        while (true) {
                            val pkt = s.read()
                            if (pkt is dadb.AdbShellPacket.Exit) { touchReady = false; inj = null; Status.log("Dedo real: se cerró"); break }
                            val txt = String(pkt.payload).trim()
                            if (txt.isEmpty()) continue
                            if (txt.contains("listo")) { touchReady = true; Status.log("Dedo real (toques de pantalla): activo ✅") }
                            txt.lines().filter { it.startsWith("err") || it.contains("Exception") }.take(3).forEach { Status.log("Dedo real: $it") }
                        }
                    } catch (_: Throwable) { touchReady = false; inj = null }
                }.apply { isDaemon = true }.start()
            } catch (e: Throwable) {
                inj = null; Status.log("Dedo real no disponible (${e.javaClass.simpleName})")
            } finally { touchStarting = false }
        }
    }

    /** Manda una orden al dedo real: 'd' apoyar, 'm' mover, 'u' levantar, 'c' cancelar. false = no disponible. */
    fun touch(ctx: Context, c: Char, x: Float, y: Float): Boolean {
        val s = inj
        if (!touchReady || s == null) { if (ready) startTouch(ctx) else start(ctx); return false }
        touchWorker.execute {
            try { s.write("$c ${x.toInt()} ${y.toInt()}\n") }
            catch (e: Throwable) { touchReady = false; inj = null; Status.log("Dedo real: se cortó (${e.javaClass.simpleName})") }
        }
        return true
    }

    /** Aprieta una tecla del control. Devuelve false si no está disponible (usar el método anterior). */
    fun key(ctx: Context, code: Int): Boolean {
        if (!ready) { start(ctx); return false }
        val dir = ctx.applicationContext.filesDir
        worker.execute {
            try {
                shell!!.write("input keyevent $code\n")
            } catch (e: Throwable) {
                close()
                try { open(dir); shell!!.write("input keyevent $code\n") }
                catch (e2: Throwable) { ready = false; close(); Status.log("Teclas reales: se cortó (${e2.javaClass.simpleName})") }
            }
        }
        return true
    }
}
