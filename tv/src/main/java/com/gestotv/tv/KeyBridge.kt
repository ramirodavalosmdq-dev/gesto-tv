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
        try { shell?.close() } catch (_: Throwable) {}
        try { dadb?.close() } catch (_: Throwable) {}
        shell = null; dadb = null
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
