package com.gestotv.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** El puntero que se dibuja encima de cualquier app. */
class CursorView(ctx: Context) : View(ctx) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 56, 189, 248) }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE
    }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(110, 0, 0, 0); style = Paint.Style.STROKE
    }
    var dragging = false
        set(v) { field = v; fill.color = if (v) Color.argb(200, 245, 158, 11) else Color.argb(150, 56, 189, 248); invalidate() }

    override fun onDraw(c: Canvas) {
        val r = width / 2f
        ring.strokeWidth = r * 0.18f
        shadow.strokeWidth = r * 0.30f
        c.drawCircle(r, r, r * 0.78f, shadow)
        c.drawCircle(r, r, r * 0.78f, fill)
        c.drawCircle(r, r, r * 0.78f, ring)
        c.drawCircle(r, r, r * 0.12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
    }

    fun pulse() {
        animate().cancel()
        scaleX = 0.6f; scaleY = 0.6f
        animate().scaleX(1f).scaleY(1f).setDuration(180).start()
    }
}
