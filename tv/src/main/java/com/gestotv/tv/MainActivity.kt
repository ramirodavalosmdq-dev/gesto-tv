package com.gestotv.tv

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/** Pantalla del TV: muestra si todo está listo, la dirección para el celular y el código. */
class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var stepA11y: TextView
    private lateinit var stepServer: TextView
    private lateinit var urlView: TextView
    private lateinit var pinView: TextView
    private lateinit var clientsView: TextView
    private lateinit var qr: ImageView
    private lateinit var btnA11y: Button
    private lateinit var logView: TextView
    private var lastQrUrl = ""

    private val BG = Color.rgb(11, 13, 18)
    private val PANEL = Color.rgb(21, 26, 36)
    private val ACCENT = Color.rgb(56, 189, 248)
    private val MUTED = Color.rgb(139, 147, 167)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dp = resources.displayMetrics.density

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(BG)
            setPadding((48 * dp).toInt(), (32 * dp).toInt(), (48 * dp).toInt(), (32 * dp).toInt())
        }

        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.4f))

        left.addView(text("✋ Gesto TV", 34f, Color.WHITE, bold = true))
        left.addView(text("Controlá este TV con gestos, usando tu celular como cámara.", 15f, MUTED).apply { setPadding(0, 0, 0, (20 * dp).toInt()) })

        stepA11y = text("", 18f, Color.WHITE)
        left.addView(stepA11y)
        btnA11y = button("Abrir Accesibilidad") { openA11y() }
        left.addView(btnA11y)

        stepServer = text("", 18f, Color.WHITE).apply { setPadding(0, (18 * dp).toInt(), 0, 0) }
        left.addView(stepServer)
        urlView = text("", 26f, ACCENT, bold = true)
        left.addView(urlView)
        left.addView(text("Abrila en Chrome del celular (o escaneá el QR). Si Chrome avisa \"conexión no privada\": Configuración avanzada → Continuar.", 14f, MUTED))

        pinView = text("", 22f, Color.WHITE, bold = true).apply { setPadding(0, (18 * dp).toInt(), 0, 0) }
        left.addView(pinView)
        clientsView = text("", 16f, MUTED).apply { setPadding(0, (10 * dp).toInt(), 0, (10 * dp).toInt()) }
        left.addView(clientsView)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(button("Probar cursor") { testCursor() })
        row.addView(button("Ver apps del TV") { showApps() })
        left.addView(row)

        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        qr = ImageView(this)
        val qrSize = (260 * dp).toInt()
        right.addView(qr, LinearLayout.LayoutParams(qrSize, qrSize))
        right.addView(text("Escaneá con la cámara del celular", 14f, MUTED).apply { gravity = Gravity.CENTER })
        root.addView(right, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))

        logView = text("", 12f, MUTED).apply { setPadding(0, (10 * dp).toInt(), 0, 0) }
        left.addView(logView)

        setContentView(root)
        btnA11y.requestFocus()
        ServerService.start(this)
    }

    override fun onResume() { super.onResume(); refresh.run() }
    override fun onPause() { super.onPause(); handler.removeCallbacks(refresh) }

    private val refresh = object : Runnable {
        override fun run() {
            val a11y = Status.serviceRunning
            stepA11y.text = if (a11y) "1. Servicio de gestos: ✅ ACTIVO" else "1. Servicio de gestos: ❌ APAGADO — activalo en Accesibilidad → Gesto TV"
            btnA11y.visibility = if (a11y) View.GONE else View.VISIBLE
            val url = Status.url()
            stepServer.text = when {
                !a11y && !Status.serverRunning -> "2. Dirección para el celular: (se activa con el paso 1)"
                Status.serverRunning -> "2. Dirección para el celular: ✅"
                Status.serverError != null -> "2. Error del servidor: ${Status.serverError}"
                else -> "2. Preparando conexión segura…"
            }
            urlView.text = if (Status.serverRunning) url else ""
            pinView.text = "Código: ${Status.pin(this@MainActivity)}"
            val ago = if (Status.lastCommandAt > 0) " · última orden: ${Status.lastCommand}" else ""
            clientsView.text = "📱 Celulares conectados: ${Status.clients}$ago   ·   v${BuildConfigInfo.version(this@MainActivity)}"
            if (Status.serverRunning && url != lastQrUrl && url.startsWith("https")) { qr.setImageBitmap(qrBitmap(url)); lastQrUrl = url }
            if (!Status.serverRunning) { qr.setImageDrawable(null); lastQrUrl = "" }
            logView.text = Status.logText()
            handler.postDelayed(this, 1000)
        }
    }

    private fun openA11y() {
        try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        catch (e: Exception) { Toast.makeText(this, "Este TV no tiene el menú de Accesibilidad a mano. Lo activamos desde la PC.", Toast.LENGTH_LONG).show() }
    }

    private fun testCursor() {
        val s = GestoAccessibilityService.instance
        if (s == null) { Toast.makeText(this, "Primero activá el servicio (paso 1)", Toast.LENGTH_LONG).show(); return }
        s.showCursor(true)
        val steps = listOf(300f to 0f, 0f to 200f, -300f to 0f, 0f to -200f)
        steps.forEachIndexed { i, (dx, dy) ->
            for (k in 1..10) handler.postDelayed({ s.move(dx / 10, dy / 10) }, (i * 10 + k) * 40L)
        }
    }

    private fun showApps() {
        val s = GestoAccessibilityService.instance
        if (s == null) { Toast.makeText(this, "Primero activá el servicio (paso 1)", Toast.LENGTH_LONG).show(); return }
        val names = s.launchableApps().joinToString(", ") { it.first }
        Toast.makeText(this, names.ifEmpty { "No encontré apps" }, Toast.LENGTH_LONG).show()
    }

    private fun qrBitmap(content: String): Bitmap {
        val size = 512
        val m = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) for (y in 0 until size) bmp.setPixel(x, y, if (m[x, y]) Color.BLACK else Color.WHITE)
        return bmp
    }

    private fun text(s: String, sp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = sp; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 16f
        setTextColor(Color.WHITE)
        val dp = resources.displayMetrics.density
        val normal = GradientDrawable().apply { cornerRadius = 12 * dp; setColor(PANEL); setStroke((2 * dp).toInt(), Color.rgb(38, 43, 56)) }
        background = normal
        setOnFocusChangeListener { _, has ->
            (background as GradientDrawable).setStroke((3 * dp).toInt(), if (has) ACCENT else Color.rgb(38, 43, 56))
            setTextColor(if (has) ACCENT else Color.WHITE)
        }
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, (8 * dp).toInt(), (12 * dp).toInt(), 0)
        }
        setPadding((20 * dp).toInt(), (10 * dp).toInt(), (20 * dp).toInt(), (10 * dp).toInt())
    }
}
