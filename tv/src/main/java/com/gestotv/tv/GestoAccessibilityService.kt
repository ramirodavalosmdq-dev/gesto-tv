package com.gestotv.tv

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import kotlin.math.abs

/**
 * Corazón de la app en el TV. Android lo mantiene vivo mientras esté activado en Accesibilidad.
 *  - Dibuja el cursor encima de cualquier app.
 *  - Ejecuta toques, presión larga y scroll en cualquier punto de la pantalla.
 *  - Atrás / Inicio, volumen y abrir apps.
 *  - Levanta el servidor que recibe las órdenes del celular.
 * El celular manda coordenadas en una pantalla de referencia de 1920x1080; acá se escalan a la real.
 */
class GestoAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "GestoTV"
        const val REF_W = 1920f
        const val REF_H = 1080f
        @Volatile var instance: GestoAccessibilityService? = null
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private var cursor: CursorView? = null
    private var lp: WindowManager.LayoutParams? = null

    private var sw = 1920
    private var sh = 1080
    private var x = 960f
    private var y = 540f
    private val hideCursor = Runnable { cursor?.visibility = View.GONE }
    private var lastListScroll = 0L

    // arrastre continuo: un "dedo" apoyado que sigue a la mano hasta que se abre la pinza
    private var dragActive = false
    private var dragEnding = false
    private var dragBusy = false
    private var dragStroke: GestureDescription.StrokeDescription? = null
    private var dragLastX = 0f
    private var dragLastY = 0f

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Status.serviceRunning = true
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val m = DisplayMetrics()
        @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m)
        sw = m.widthPixels; sh = m.heightPixels
        x = sw / 2f; y = sh / 2f
        addCursor()
        Status.log("Servicio de gestos conectado")
        ServerService.start(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        Status.serviceRunning = false
        Status.log("Servicio de gestos desconectado")
        removeCursor()
        instance = null
        super.onDestroy()
    }

    // ---------------- cursor ----------------
    private fun addCursor() {
        val size = (sh * 0.05f).toInt().coerceAtLeast(32)
        val p = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        val v = CursorView(this)
        v.visibility = View.GONE
        try { wm.addView(v, p); cursor = v; lp = p } catch (e: Exception) { Log.e(TAG, "Sin cursor", e) }
    }

    private fun removeCursor() {
        cursor?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        cursor = null
    }

    private fun placeCursor(show: Boolean = true) {
        val c = cursor ?: return
        val p = lp ?: return
        p.x = (x - p.width / 2f).toInt()
        p.y = (y - p.height / 2f).toInt()
        try { wm.updateViewLayout(c, p) } catch (_: Exception) {}
        if (show) {
            c.visibility = View.VISIBLE
            main.removeCallbacks(hideCursor)
            main.postDelayed(hideCursor, 8000)
        }
    }

    fun showCursor(show: Boolean) = main.post {
        if (show) { x = sw / 2f; y = sh / 2f; placeCursor(true) }
        else { main.removeCallbacks(hideCursor); cursor?.visibility = View.GONE }
    }

    fun move(dx: Float, dy: Float) = main.post {
        x = (x + dx * sw / REF_W).coerceIn(0f, sw - 1f)
        y = (y + dy * sh / REF_H).coerceIn(0f, sh - 1f)
        placeCursor(true)
        if (dragActive) pumpDrag()
    }

    // ---------------- toques ----------------
    private fun stroke(path: Path, durationMs: Long) {
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        dispatchGesture(g, null, null)
    }

    /**
     * Elemento más chico de la pantalla, bajo el cursor, que cumple [pred].
     * Recorre las ventanas de arriba hacia abajo (un menú encima gana sobre la app de atrás).
     */
    private fun nodeAt(px: Int, py: Int, pred: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val r = Rect()
        val roots: List<AccessibilityNodeInfo> = try {
            windows.filter { it.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY }
                .sortedByDescending { it.layer }
                .filter { it.getBoundsInScreen(r); r.contains(px, py) }
                .mapNotNull { it.root }
        } catch (e: Exception) { emptyList() }
        val all = roots.ifEmpty { listOfNotNull(rootInActiveWindow) }
        for (root in all) {
            var best: AccessibilityNodeInfo? = null
            var bestArea = Long.MAX_VALUE
            fun walk(n: AccessibilityNodeInfo, depth: Int) {
                if (depth > 45) return
                n.getBoundsInScreen(r)
                if (!r.contains(px, py)) return
                if (n.isVisibleToUser && pred(n)) {
                    val a = r.width().toLong() * r.height()
                    if (a in 1 until bestArea) { bestArea = a; best = n }
                }
                for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
            }
            walk(root, 0)
            if (best != null) return best
        }
        return null
    }

    fun click() = main.post {
        cursor?.pulse()
        val node = try { nodeAt(x.toInt(), y.toInt()) { it.isClickable } } catch (e: Exception) { null }
        val ok = node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
        if (!ok) stroke(Path().apply { moveTo(x, y) }, 60)
        Status.log(if (ok) "Clic en elemento" else "Clic por toque")
    }

    fun longPress() = main.post {
        cursor?.pulse()
        val node = try { nodeAt(x.toInt(), y.toInt()) { it.isLongClickable } } catch (e: Exception) { null }
        val ok = node?.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK) ?: false
        if (!ok) stroke(Path().apply { moveTo(x, y) }, 900)
    }

    fun setDragging(d: Boolean) = main.post {
        cursor?.dragging = d
        if (Build.VERSION.SDK_INT < 26) return@post
        if (d) {
            if (dragActive) return@post
            dragActive = true; dragEnding = false; dragStroke = null
            dragLastX = x; dragLastY = y
            // apoyar el dedo donde está el cursor (el trazo queda "abierto" para continuar)
            val path = Path().apply { moveTo(x, y); lineTo(x + 0.5f, y) }
            dispatchDrag(GestureDescription.StrokeDescription(path, 0, 30, true), x + 0.5f, y)
            Status.log("Arrastre: dedo apoyado")
        } else if (dragActive) {
            dragEnding = true
            pumpDrag()
        }
    }

    private fun dispatchDrag(stroke: GestureDescription.StrokeDescription, endX: Float, endY: Float) {
        if (Build.VERSION.SDK_INT < 26) return
        dragBusy = true
        dragStroke = stroke
        val ok = dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) {
                dragBusy = false
                dragLastX = endX; dragLastY = endY
                if (stroke.willContinue()) pumpDrag() else { dragActive = false; dragStroke = null; Status.log("Arrastre: dedo levantado") }
            }
            override fun onCancelled(g: GestureDescription?) {
                dragBusy = false; dragActive = false; dragStroke = null
                Status.log("Arrastre cancelado por el sistema")
            }
        }, main)
        if (!ok) { dragBusy = false; dragActive = false; dragStroke = null; Status.log("Arrastre no disponible") }
    }

    /** Extiende el trazo hasta la posición actual del cursor (o lo termina si se abrió la pinza). */
    private fun pumpDrag() {
        if (Build.VERSION.SDK_INT < 26 || dragBusy || !dragActive) return
        val prev = dragStroke ?: return
        val still = abs(x - dragLastX) < 1f && abs(y - dragLastY) < 1f
        if (still && !dragEnding) return
        val ex = if (still) dragLastX + 0.5f else x
        val ey = if (still) dragLastY else y
        val path = Path().apply { moveTo(dragLastX, dragLastY); lineTo(ex, ey) }
        val next = try { prev.continueStroke(path, 0, 30, !dragEnding) } catch (e: Exception) {
            dragActive = false; dragStroke = null; Status.log("Arrastre: $e"); return
        }
        dispatchDrag(next, ex, ey)
    }

    /**
     * Arrastre en el punto del cursor (como deslizar el dedo). dx/dy en pantalla de referencia 1920x1080.
     * Arrastrar hacia arriba o hacia la izquierda = avanzar en la lista.
     * 1° intenta desplazar la lista que está bajo el cursor; si no hay, simula el deslizamiento.
     */
    fun scroll(dxRef: Float, dyRef: Float) = main.post {
        val dx = dxRef * sw / REF_W
        val dy = dyRef * sh / REF_H
        if (abs(dx) < 8f && abs(dy) < 8f) return@post
        val horizontal = abs(dx) > abs(dy)
        val forward = if (horizontal) dx < 0 else dy < 0
        val node = try { nodeAt(x.toInt(), y.toInt()) { it.isScrollable } } catch (e: Exception) { null }
        val now = android.os.SystemClock.uptimeMillis()
        if (node != null && now - lastListScroll < 450) return@post   // un salto de lista cada ~0,5 s, no 10 seguidos
        if (node != null) lastListScroll = now
        val ok = node?.performAction(
            if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        ) ?: false
        if (!ok) {
            val x0 = (x - dx / 2f).coerceIn(2f, sw - 3f); val x1 = (x + dx / 2f).coerceIn(2f, sw - 3f)
            val y0 = (y - dy / 2f).coerceIn(2f, sh - 3f); val y1 = (y + dy / 2f).coerceIn(2f, sh - 3f)
            val len = kotlin.math.hypot(x1 - x0, y1 - y0)
            val dur = (len / 0.5f).toLong().coerceIn(120L, 700L) // lento = sin "lanzar" la lista
            stroke(Path().apply { moveTo(x0, y0); lineTo(x1, y1) }, dur)
        }
    }

    // ---------------- teclas / sistema ----------------
    fun key(k: String, n: Int = 1): Boolean {
        var ok = true
        main.post {
            repeat(n.coerceIn(1, 20)) { when (k) {
                "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
                "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
                "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
                "vol_up" -> volume(AudioManager.ADJUST_RAISE)
                "vol_down" -> volume(AudioManager.ADJUST_LOWER)
                "mute" -> if (it == 0) volume(AudioManager.ADJUST_TOGGLE_MUTE)
                else -> {}
            } }
        }
        if (k !in setOf("back", "home", "recents", "vol_up", "vol_down", "mute")) ok = false
        return ok
    }

    private fun volume(direction: Int) {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }

    /** Abre una app por nombre ("Netflix", "Flow", "Disney+", "YouTube"...). Devuelve el nombre real o null. */
    fun launchApp(name: String): String? {
        val pm = packageManager
        val q = name.lowercase().replace("+", "").trim()
        val known = mapOf(
            "netflix" to "com.netflix.ninja",
            "youtube" to "com.google.android.youtube.tv",
            "disney" to "com.disney.disneyplus"
        )
        val apps = launchableApps()
        val knownPkg = known.entries.firstOrNull { q.contains(it.key) }?.value
        val app = apps.firstOrNull { it.second == knownPkg }
            ?: apps.firstOrNull { it.first.lowercase().replace("+", "").contains(q) }
            ?: return null
        val intent = pm.getLeanbackLaunchIntentForPackage(app.second)
            ?: pm.getLaunchIntentForPackage(app.second)
            ?: return null
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        main.post { startActivity(intent) }
        return app.first
    }

    /** (nombre visible, paquete) de las apps que se pueden abrir. */
    fun launchableApps(): List<Pair<String, String>> {
        val pm = packageManager
        val result = LinkedHashMap<String, String>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val i = Intent(Intent.ACTION_MAIN).addCategory(cat)
            @Suppress("DEPRECATION")
            val list = pm.queryIntentActivities(i, PackageManager.MATCH_ALL)
            for (ri in list) {
                val pkg = ri.activityInfo.packageName
                if (pkg == packageName || result.containsKey(pkg)) continue
                result[pkg] = ri.loadLabel(pm).toString()
            }
        }
        return result.map { it.value to it.key }
    }

    fun toast(msg: String) = main.post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
}
