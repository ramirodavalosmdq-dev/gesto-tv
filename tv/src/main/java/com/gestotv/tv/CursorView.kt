package com.gestotv.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/**
 * El puntero que se dibuja encima de cualquier app.
 *  - Modo mouse: un punto celeste.
 *  - Modo táctil: un anillo que se cierra a medida que la mano se acerca a la "pantalla invisible";
 *    al tocarla se llena de azul.
 */
class CursorView(ctx: Context) : View(ctx) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 56, 189, 248) }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(110, 0, 0, 0); style = Paint.Style.STROKE }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val touchFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(37, 99, 235) }
    private val ringBlue = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(56, 189, 248); style = Paint.Style.STROKE }

    var dragging = false
        set(v) { field = v; fill.color = if (v) Color.argb(200, 245, 158, 11) else Color.argb(150, 56, 189, 248); invalidate() }

    /** true = modo táctil (anillo de profundidad). */
    var ringMode = false
        set(v) { if (field != v) { field = v; invalidate() } }
    /** 0 = lejos del plano, 1 = sobre el plano. */
    var progress = 0f
        set(v) { val c = v.coerceIn(0f, 1f); if (kotlin.math.abs(c - field) > 0.01f) { field = c; invalidate() } }
    var touching = false
        set(v) { if (field != v) { field = v; invalidate() } }

    override fun onDraw(c: Canvas) {
        val r = width / 2f
        if (!ringMode) {
            val rr = r * 0.45f
            ring.strokeWidth = rr * 0.18f; shadow.strokeWidth = rr * 0.30f
            c.drawCircle(r, r, rr * 0.78f, shadow)
            c.drawCircle(r, r, rr * 0.78f, fill)
            c.drawCircle(r, r, rr * 0.78f, ring)
            c.drawCircle(r, r, rr * 0.12f, dot)
            return
        }
        if (touching) {
            // apoyado: punto azul lleno con borde blanco
            shadow.strokeWidth = r * 0.16f
            c.drawCircle(r, r, r * 0.42f, shadow)
            c.drawCircle(r, r, r * 0.42f, touchFill)
            ring.strokeWidth = r * 0.08f
            c.drawCircle(r, r, r * 0.42f, ring)
            c.drawCircle(r, r, r * 0.10f, dot)
            return
        }
        // flotando: el anillo se achica al acercarse al plano
        val rr = r * (0.92f - 0.45f * progress)
        shadow.strokeWidth = r * 0.12f
        ringBlue.strokeWidth = r * (0.05f + 0.07f * progress)
        ringBlue.alpha = (120 + 135 * progress).toInt()
        c.drawCircle(r, r, rr, shadow)
        c.drawCircle(r, r, rr, ringBlue)
        c.drawCircle(r, r, r * 0.11f, shadow)
        c.drawCircle(r, r, r * 0.11f, dot)
    }

    fun pulse() {
        animate().cancel()
        scaleX = 0.6f; scaleY = 0.6f
        animate().scaleX(1f).scaleY(1f).setDuration(180).start()
    }
}
