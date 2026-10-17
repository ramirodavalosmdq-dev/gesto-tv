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
class GestoServer(private val svc: GestoAccessibilityService, port: Int) : NanoWSD(port) {

    override fun serveHttp(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val path = when (session.uri) { "/", "/index.html" -> "web/index.html"; else -> null }
            ?: return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, "text/plain", "No encontrado")
        return try {
            val bytes = svc.assets.open(path).use { it.readBytes() }
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
                svc.toast("📱 Celular desconectado")
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
                if (t == "hello" && m.optString("pin") == Status.pin(svc)) {
                    authed = true
                    Status.clients += 1
                    reply(JSONObject().put("t", "ok").put("version", BuildConfigInfo.version(svc)))
                    svc.toast("📱 Celular conectado")
                } else {
                    reply(JSONObject().put("t", "badpin"))
                    try { close(WebSocketFrame.CloseCode.PolicyViolation, "pin", false) } catch (_: IOException) {}
                }
                return
            }

            if (t != "move" && t != "ping") { Status.lastCommand = t + (m.optString("k").takeIf { it.isNotEmpty() }?.let { " $it" } ?: ""); Status.lastCommandAt = System.currentTimeMillis() }

            when (t) {
                "ping" -> reply(JSONObject().put("t", "pong").put("id", m.opt("id")))
                "move" -> svc.move(m.optDouble("dx", 0.0).toFloat(), m.optDouble("dy", 0.0).toFloat())
                "cursor" -> svc.showCursor(m.optBoolean("show", true))
                "click" -> svc.click()
                "long" -> svc.longPress()
                "drag" -> svc.setDragging(m.optBoolean("on", false))
                "scroll" -> svc.scroll(m.optDouble("dy", 0.0).toFloat())
                "key" -> {
                    val k = m.optString("k")
                    if (!svc.key(k)) {
                        if (k.startsWith("ch_")) info("Cambiar de canal llega en la próxima versión")
                        else info("Tecla no disponible: $k")
                    }
                }
                "digits" -> info("Ir al canal ${m.optString("v")}: llega en la próxima versión")
                "app" -> {
                    val name = m.optString("name")
                    val opened = svc.launchApp(name)
                    if (opened != null) { svc.toast("▶ $opened"); info("Abriendo $opened") }
                    else info("No encontré la app \"$name\" en el TV")
                }
                "apps" -> {
                    val arr = JSONArray()
                    svc.launchableApps().forEach { arr.put(JSONObject().put("name", it.first).put("pkg", it.second)) }
                    reply(JSONObject().put("t", "apps").put("list", arr))
                }
            }
        }
    }
}
