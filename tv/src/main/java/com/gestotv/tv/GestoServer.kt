package com.gestotv.tv

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import fi.iki.elonen.NanoWSD.WebSocketFrame
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Servidor HTTPS + WebSocket dentro del TV (puerto 8443).
 *  GET /     -> la página de gestos para el celular (va incluida en la app)
 *  WS  /ws   -> canal de órdenes. Primer mensaje: {"t":"hello","pin":"1234"}
 */
class GestoServer(private val ctx: android.content.Context, port: Int) : NanoWSD(port) {

    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private fun toast(msg: String) = main.post { android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show() }

    override fun serveHttp(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val path = when (session.uri) { "/", "/index.html" -> "web/index.html"; else -> null }
            ?: return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, "text/plain", "No encontrado")
        return try {
            val bytes = ctx.assets.open(path).use { it.readBytes() }
            NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/html; charset=utf-8", bytes.inputStream(), bytes.size.toLong()).apply {
                addHeader("Cache-Control", "no-store")
            }
        } catch (e: IOException) {
            NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "text/plain", e.toString())
        }
    }

    override fun openWebSocket(handshake: NanoHTTPD.IHTTPSession): NanoWSD.WebSocket = PhoneSocket(handshake)

    private inner class PhoneSocket(h: NanoHTTPD.IHTTPSession) : NanoWSD.WebSocket(h) {
        private var authed = false

        override fun onOpen() {}

        override fun onClose(code: WebSocketFrame.CloseCode?, reason: String?, initiatedByRemote: Boolean) {
            if (authed) {
                Status.clients = (Status.clients - 1).coerceAtLeast(0)
                toast("📱 Celular desconectado"); Status.log("Celular desconectado")
            }
        }

        override fun onPong(pong: WebSocketFrame?) {}

        override fun onException(exception: IOException?) {
            Log.w("GestoTV", "WS: $exception")
        }

        private fun reply(o: JSONObject) {
            try { send(o.toString()) } catch (_: IOException) {}
        }

        private fun info(msg: String) = reply(JSONObject().put("t", "info").put("msg", msg))

        override fun onMessage(frame: WebSocketFrame) {
            val m = try { JSONObject(frame.textPayload) } catch (e: Exception) { return }
            val t = m.optString("t")

            if (!authed) {
                if (t == "hello" && m.optString("pin") == Status.pin(ctx)) {
                    authed = true
                    Status.clients += 1
                    reply(JSONObject().put("t", "ok").put("version", BuildConfigInfo.version(ctx)))
                    toast("📱 Celular conectado"); Status.log("Celular conectado")
                } else {
                    reply(JSONObject().put("t", "badpin"))
                    try { close(WebSocketFrame.CloseCode.PolicyViolation, "pin", false) } catch (_: IOException) {}
                }
                return
            }

            if (t != "move" && t != "ping") { Status.lastCommand = t + (m.optString("k").takeIf { it.isNotEmpty() }?.let { " $it" } ?: ""); Status.lastCommandAt = System.currentTimeMillis() }

            if (t == "ping") { reply(JSONObject().put("t", "pong").put("id", m.opt("id")).put("a11y", Status.serviceRunning)); return }
            val svc = GestoAccessibilityService.instance
            if (svc == null) { info("El servicio de gestos está apagado en el TV (Accesibilidad → Gesto TV)"); return }

            try { when (t) {
                "move" -> svc.move(m.optDouble("dx", 0.0).toFloat(), m.optDouble("dy", 0.0).toFloat())
                "cursor" -> svc.showCursor(m.optBoolean("show", true))
                "click" -> svc.click()
                "dpad" -> svc.dpad(m.optString("dir", "center"))
                "long" -> svc.longPress()
                "drag" -> svc.setDragging(m.optBoolean("on", false))
                "scroll" -> svc.scroll(m.optDouble("dx", 0.0).toFloat(), m.optDouble("dy", 0.0).toFloat())
                "key" -> {
                    val k = m.optString("k")
                    if (!svc.key(k, m.optInt("n", 1))) {
                        if (k.startsWith("ch_")) info("Cambiar de canal llega en la próxima versión")
                        else info("Tecla no disponible: $k")
                    }
                }
                "digits" -> info("Ir al canal ${m.optString("v")}: llega en la próxima versión")
                "app" -> {
                    val name = m.optString("name")
                    val opened = svc.launchApp(name)
                    if (opened != null) { toast("▶ $opened"); info("Abriendo $opened") }
                    else info("No encontré la app \"$name\" en el TV")
                }
                "apps" -> {
                    val arr = JSONArray()
                    svc.launchableApps().forEach { arr.put(JSONObject().put("name", it.first).put("pkg", it.second)) }
                    reply(JSONObject().put("t", "apps").put("list", arr))
                }
            } } catch (e: Throwable) { Status.log("Error en orden $t: ${e.javaClass.simpleName}") }
        }
    }
}
