package com.gestotv.tv

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Path
import android.graphics.PixelFormat
import android.media.AudioManager
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
    }

    // ---------------- toques ----------------
    private fun stroke(path: Path, durationMs: Long) {
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        dispatchGesture(g, null, null)
    }

    fun click() = main.post {
        cursor?.pulse()
        stroke(Path().apply { moveTo(x, y) }, 40)
    }

    fun longPress() = main.post {
        cursor?.pulse()
        stroke(Path().apply { moveTo(x, y) }, 900)
    }

    fun setDragging(d: Boolean) = main.post { cursor?.dragging = d }

    /** Arrastre vertical en el punto del cursor (como deslizar el dedo): dy > 0 = el contenido baja. */
    fun scroll(dyRef: Float) = main.post {
        val d = dyRef * sh / REF_H
        if (abs(d) < 8f) return@post
        val y0 = (y - d / 2f).coerceIn(2f, sh - 3f)
        val y1 = (y + d / 2f).coerceIn(2f, sh - 3f)
        val dur = (abs(y1 - y0) / 0.5f).toLong().coerceIn(120L, 700L) // lento = sin "lanzar" la lista
        stroke(Path().apply { moveTo(x, y0); lineTo(x, y1) }, dur)
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
