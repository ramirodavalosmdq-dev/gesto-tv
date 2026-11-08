package com.gestotv.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * "Enlace gestual activado": la animación que aparece en el TV cuando se completa la ruedita verde.
 * Estilo HUD (interfaz de ciencia ficción), con alto contraste sobre cualquier imagen:
 *  1. se oscurece el centro de la pantalla (para que el texto se lea sobre cualquier cosa),
 *  2. ondas de energía salen del centro, donde quedó anclada la pantalla invisible,
 *  3. un anillo de mira con marcas gira y se completa,
 *  4. el texto se "decodifica" letra por letra y una línea de escaneo lo barre,
 *  5. todo se desvanece y el anillo se cierra sobre sí mismo.
 * Dura TOTAL ms y después avisa con [onDone] para que el servicio saque la ventana.
 */
class LinkView(ctx: Context, private val onDone: () -> Unit) : View(ctx) {
    companion object { const val TOTAL = 2600L }

    private val t0 = SystemClock.uptimeMillis()
    private var finished = false

    private val CYAN = Color.rgb(56, 189, 248)
    private val GLOW = Color.rgb(125, 211, 252)

    private val scrim = Paint()
    private val wave = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = CYAN }
    private val hud = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = CYAN; strokeCap = Paint.Cap.ROUND }
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GLOW; strokeCap = Paint.Cap.ROUND }
    private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        letterSpacing = 0.38f
    }
    private val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CYAN; textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        letterSpacing = 0.62f
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CYAN }
    private val rect = RectF()

    private val L1 = "ENLACE GESTUAL"
    private val L2 = "ACTIVADO"
    private val GLYPHS = "01<>/\\|+=#*:"

    /** 0..1 dentro de una ventana [a, b] (en ms), con suavizado. */
    private fun seg(t: Float, a: Float, b: Float): Float {
        val x = ((t - a) / (b - a)).coerceIn(0f, 1f)
        return x * x * (3 - 2 * x)
    }

    /** Texto que se decodifica de izquierda a derecha: las letras sin resolver muestran símbolos al azar. */
    private fun decode(s: String, k: Float, t: Long): String {
        val solved = (k * s.length).toInt()
        val sb = StringBuilder(s.length)
        for (i in s.indices) {
            val ch = s[i]
            sb.append(if (i < solved || ch == ' ') ch else if (k <= 0f) ' ' else GLYPHS[((t / 45 + i * 7) % GLYPHS.length).toInt()])
        }
        return sb.toString()
    }

    override fun onDraw(c: Canvas) {
        val now = SystemClock.uptimeMillis()
        val tl = now - t0
        val t = tl.toFloat()
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2f; val cy = h / 2f
        val u = min(w, h)                                   // unidad: alto de la pantalla
        val fadeIn = seg(t, 0f, 280f)
        val fadeOut = 1f - seg(t, TOTAL - 650f, TOTAL.toFloat())
        val vis = fadeIn * fadeOut

        // 1) velo oscuro radial: el contraste lo garantiza esto, no el color de lo que haya atrás
        scrim.shader = RadialGradient(cx, cy, u * 0.95f,
            intArrayOf(Color.argb((215 * vis).toInt(), 2, 6, 20), Color.argb((150 * vis).toInt(), 2, 6, 20), Color.argb((70 * vis).toInt(), 2, 6, 20)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, scrim)

        // 2) ondas de energía (3, escalonadas)
        for (i in 0 until 3) {
            val k = seg(t, 80f + i * 170f, 1100f + i * 170f)
            if (k <= 0f || k >= 1f) continue
            val r = u * (0.04f + 0.62f * k)
            val a = (1f - k) * fadeOut
            wave.strokeWidth = u * 0.012f * (1f - k) + 1.5f
            wave.alpha = (200 * a).toInt(); c.drawCircle(cx, cy, r, wave)
            wave.strokeWidth *= 4f; wave.alpha = (40 * a).toInt(); c.drawCircle(cx, cy, r, wave)   // halo
        }

        // 3) anillo de mira (HUD): se dibuja, gira y al final se cierra sobre el centro
        val draw = seg(t, 200f, 1000f)
        val close = seg(t, TOTAL - 650f, TOTAL.toFloat())
        val R = u * 0.19f * (1f - 0.55f * close)
        val rot = t * 0.06f
        rect.set(cx - R, cy - R, cx + R, cy + R)
        hud.strokeWidth = u * 0.0045f
        hud.alpha = (230 * vis).toInt()
        for (q in 0 until 4) c.drawArc(rect, rot + q * 90f + 8f, 74f * draw, false, hud)   // 4 arcos con cortes
        val R2 = R * 1.13f
        rect.set(cx - R2, cy - R2, cx + R2, cy + R2)
        hud.strokeWidth = u * 0.0022f; hud.alpha = (140 * vis).toInt()
        c.drawArc(rect, -rot * 1.6f, 300f * draw, false, hud)                              // arco exterior, gira al revés
        tick.strokeWidth = u * 0.003f
        val nt = (48 * draw).toInt()
        for (i in 0 until nt) {                                                             // marcas de la mira
            val ang = Math.toRadians((i * 7.5f - rot * 0.5f).toDouble())
            val long = i % 6 == 0
            val r1 = R * 0.86f; val r2 = R * (if (long) 0.76f else 0.81f)
            tick.alpha = ((if (long) 220 else 110) * vis).toInt()
            c.drawLine(cx + r1 * cos(ang).toFloat(), cy + r1 * sin(ang).toFloat(), cx + r2 * cos(ang).toFloat(), cy + r2 * sin(ang).toFloat(), tick)
        }
        // núcleo: el punto donde nació tu pantalla invisible
        val pulse = 1f + 0.25f * sin(t / 140f)
        core.alpha = (255 * vis).toInt(); c.drawCircle(cx, cy, u * 0.009f * pulse, core)
        core.alpha = (60 * vis).toInt(); c.drawCircle(cx, cy, u * 0.024f * pulse, core)

        // 4) texto debajo del anillo, decodificándose
        val ty = cy + R * 1.13f + u * 0.11f
        title.textSize = u * 0.052f
        title.alpha = (255 * vis).toInt()
        title.setShadowLayer(u * 0.018f, 0f, 0f, Color.argb((200 * vis).toInt(), 56, 189, 248))
        c.drawText(decode(L1, seg(t, 380f, 1150f), tl), cx, ty, title)
        sub.textSize = u * 0.030f
        sub.alpha = (255 * vis * seg(t, 900f, 1250f)).toInt()
        sub.setShadowLayer(u * 0.014f, 0f, 0f, Color.argb((230 * vis).toInt(), 56, 189, 248))
        c.drawText(decode(L2, seg(t, 1000f, 1450f), tl), cx, ty + u * 0.065f, sub)

        // línea de escaneo que barre el texto una vez
        val sk = seg(t, 1150f, 1750f)
        if (sk > 0f && sk < 1f) {
            val half = u * 0.42f
            val sx = cx - half + 2 * half * sk
            line.alpha = (210 * (1f - kotlin.math.abs(sk - 0.5f) * 2f) * fadeOut).toInt()
            c.drawRect(sx - 1.5f, ty - u * 0.07f, sx + 1.5f, ty + u * 0.085f, line)
        }
        // subrayado fino que se extiende desde el centro
        val ul = seg(t, 1100f, 1600f) * u * 0.30f
        line.alpha = (170 * vis).toInt()
        c.drawRect(cx - ul, ty + u * 0.098f, cx + ul, ty + u * 0.098f + u * 0.0022f, line)

        if (tl < TOTAL) postInvalidateOnAnimation()
        else if (!finished) { finished = true; post { onDone() } }
    }
}
