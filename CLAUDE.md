# Gesto TV — contexto completo del proyecto

Leé esto entero antes de tocar nada. El dueño es **Rami** (Mar del Plata, Argentina). Habla en español
rioplatense, no es programador: explicale en lenguaje simple, paso a paso, y preguntale por foto/video lo que
pasa en el TV. Los comentarios del código y los textos de la app van en español.

## Qué es
Controlar un Smart TV **con las manos, a distancia**, usando una cámara (hoy: el celular apoyado en el TV).
Visión de producto (de Rami): una caja con **una cámara inteligente** (a futuro **dos cámaras**, una a cada lado
del TV, triangulando el dedo en 3D) + una app en el TV. El corazón: una **pantalla táctil invisible** frente al
usuario — el punto azul aparece en el TV donde apunta, y "tocás" empujando la mano. Regla de diseño n.º 1:
**la pantalla invisible sigue a la persona** (se arma donde activa con la palma, esté en el sillón o en la mesa).
Nombre comercial: **Co-Vi TV** (siglas de control virtual). Slogan: "Todo al alcance de tu mano". (El paquete Android sigue siendo com.gestotv.tv para que se instale encima.)

## Hardware real de prueba
- TV **TCL "Smart TV Pro", Google TV / Android TV 11** (API 30), IP en la casa `192.168.100.2`.
  Opciones de desarrollador activadas. Puerto 8443 lo ocupa un servicio de TCL → el server usa **9443**.
- Celular Android de Rami como cámara (Chrome). Hay también un TV **Samsung** (Tizen) — todavía sin soporte.
- Notebook Windows de Rami (usuario `valen`), misma red que el TV.

## Arquitectura (todo en este repo)
```
Celular (Chrome)                                   TV (app Android "Gesto TV", com.gestotv.tv)
tv/src/main/assets/web/index.html  ──WSS:9443──▶  ServerService (servicio en primer plano, START_STICKY)
  MediaPipe Hand Landmarker (CDN)                  └─ GestoServer.kt (NanoHTTPD+WebSocket, TLS propio, PIN)
  máquina de estados de gestos                         └─ GestoAccessibilityService.kt (cursor overlay,
  la página la SIRVE el propio TV                         clic/scroll por accesibilidad, gestos, apps, volumen)
                                                          └─ KeyBridge.kt (teclas reales: adb a 127.0.0.1:5555)
```
- **La página del celular vive DENTRO del APK** (`tv/src/main/assets/web/index.html`): el celular abre
  `https://IP-DEL-TV:9443`, acepta el aviso de certificado (es del TV) y escribe el código de 4 dígitos que muestra
  el TV. HTTPS es obligatorio para que Chrome permita la cámara.
- `Tls.kt` genera el certificado en el TV (BouncyCastle) la primera vez. `Status.kt`: PIN, IP, puertos, registro
  que se ve en la pantalla de la app (las últimas líneas: muy útil para depurar con fotos de Rami).
- Accesibilidad: `res/xml/accessibility_config.xml` (gestos + contenido de ventana + `typeViewScrolled`). **Nunca**
  filtrar teclas (puede trabar el control remoto). Si se cambia este XML, Rami tiene que desactivar y volver a activar
  "Gesto TV" en Ajustes → Accesibilidad.
- Android 11 NO deja inyectar flechas por accesibilidad (eso es API 33+). Por eso: `KeyBridge` usa la depuración del
  propio TV (`input keyevent`), y si no está, mueve el foco por accesibilidad (`focusSearch`).
- El handler principal del servicio atrapa excepciones (si una orden revienta, Android desactiva el servicio).

## Protocolo celular → TV (JSON por WebSocket)
`hello{pin}`→`ok|badpin` · `ping{id}`→`pong{id,a11y}` · `move{dx,dy}` (pantalla ref. 1920×1080, relativo) ·
`pos{x,y,p,target?}` (absoluto 0..1, p = cercanía al plano 0..1) · `tdown` / `tup` (toque de pantalla táctil) ·
`linked` (animación + voz "Enlace gestual activado" en el TV) · `click` · `long` · `drag{on}` · `scroll{dx,dy}` · `dpad{dir: up|down|left|right|center}` ·
`key{k: back|home|recents|vol_up|vol_down|mute|ch_up|ch_down, n}` · `app{name}` · `apps` · `cursor{show}` ·
`digits{v}`. TV → celular: `info{msg}`.

## Gestos actuales (página del celular)
- ✋ Palma quieta 2 s: activa/apaga. Al activar, el TV muestra la animación HUD "Enlace gestual activado" (`LinkView.kt`) y lo dice una voz femenina por el parlante del TV (`Voz.kt`, TextToSpeech, canal multimedia).
  Marca: el producto se llama **Co-Vi TV** ("control virtual"), slogan "Todo al alcance de tu mano". **Al activar, ese punto queda como centro de la pantalla invisible** (ANCLA;
  ruedita verde en el celular + anillo que se cierra en el centro del TV). Apagado automático (ajustable, 20 s).
- 🤙 L + subir/bajar: volumen (perilla, 5 puntos por paso). ✊ Puño: mute/sonido (alterna).
- 👉/👈 barrido rápido con mano estirada: canal ± (en el TV todavía no implementado: falta tecla CHANNEL por KeyBridge).
- 🤘 modo número (dígitos con dedos, se confirma solo) — en el TV todavía no implementado.
- 🅰️ (V hacia abajo) modo apps: 1 Netflix · 2 Flow · 3 Disney+ · 4 YouTube.
- ☝️ Índice solo: **pantalla táctil** (o mouse clásico, a elección en ⚙️). En táctil: el dedo se ubica absoluto
  alrededor del ANCLA (tamaño en "largos de mano"); profundidad = tamaño aparente de la palma vs. el ancla;
  empujar = `tdown`, retirar = `tup`. En el TV: toque corto = clic (accesibilidad, si no toque); apoyado y moviendo =
  trazo continuo (`continueStroke`); si no llega `TYPE_VIEW_SCROLLED`, convierte el arrastre en flechas.
  Alternativa: esquinas calibradas (3 pasos) y tocar con pinza.
- ✌️ (V hacia arriba): **modo flechas** como la perilla del volumen (cada ~4 cm de movimiento decidido de la palma
  abierta = 1 casillero; movimientos lentos no cuentan); ✊ cerrar la mano = OK y salir.
- 👍 Pulgar al costado 0,5 s: Atrás. ✊ Puño 0,7 s: salir de puntero/táctil.
- Botón 🔍 zoom 1x–3x (recorta la imagen para ver la mano de lejos; el ancla se borra al cambiar el zoom).

## Lo que Rami confirmó en el TV real
Funciona: conexión estable aun con otras apps abiertas; palma on/off; L volumen ("perfecto"); puño mute;
puntero + clic con pinza; salir con puño ("perfecto"); modo flechas "funciona muy bien" (antes era muy sensible —
se pasó a perilla); Atrás. **No anduvo**: el arrastre/scroll con pinza (falló casi siempre) y el aplauso (se sacó).
**Sin probar todavía**: pantalla táctil (v0.1.12) y el ancla de la palma (v0.1.13).

## En espera (Rami lo está pensando — NO tocar hasta que decida)
- **Contradicción de la palma**: hoy palma 2 s con el control encendido = APAGA, pero el manual (pág. 5) dice que
  repetir la palma "muda la pantalla". Propuesta hecha: palma 2 s = ruedita verde (activar o re-ubicar el ancla);
  seguir 2 s más = ruedita roja = apagar ("Enlace gestual desactivado"). Alternativas: otro gesto para apagar, o
  que la pantalla siga sola. Cuando decida: cambiar la página del celular + LinkView + el manual juntos.

## Manual del producto
Manual de la caja "Co-Vi TV" (8 páginas A5, también exportado a PDF A4): artifact de Claude Design
https://claude.ai/artifact/FKa7PjA6SN5GiMPky5Hd1t (privado de Rami). Texto al activar: "¡Listo! Enlace gestual
activado." — Rami descartó "biometría" (Co-Vi no identifica personas; no prometer biometría).
Ideas de producto charladas: la caja podría también hacerse pasar por mouse/control Bluetooth (HID) para andar en
cualquier tele sin instalar app.

## Pendientes (en orden)
0. Que Rami pruebe v0.1.15: animación HUD + voz femenina "Enlace gestual activado" (no vista en TV real todavía;
   si la voz sale masculina o en otro idioma, ver la línea "Voz: …" del registro en la pantalla de la app).
1. Que Rami pruebe la pantalla táctil + ancla; ajustar profundidad (`pushPct`), tamaño (`touchSize`), umbrales.
2. Depurar el arrastre real en el TV **con logcat** (ver abajo): ¿`continueStroke` se cancela? ¿la app no acepta
   toques? ¿llega `TYPE_VIEW_SCROLLED`?
3. Canal ± y número de canal vía KeyBridge (KEYCODE_CHANNEL_UP/DOWN, dígitos).
4. Que la pantalla invisible siga a la persona sin re-activar (detección de cuerpo/hombro) y "quién manda" con
   varias personas (el que hace la palma toma el control).
5. **Modo estéreo**: dos celulares a los costados del TV, triangulación del dedo (calibración automática moviendo
   la mano, sincronización por tiempo). Requiere un segundo Android.
6. Más adelante: app nativa en el celular, Samsung (Tizen), producto con cámara de profundidad (p. ej. OAK-D Lite).

## Cómo se compila y publica
- Cada push a `main` dispara `.github/workflows/build.yml` → APK firmado con `tv/debug.keystore` (llave fija:
  las versiones se instalan encima) → GitHub Release `v0.1.<run>` con `gesto-tv.apk`.
- Link corto para el TV: **gestotv.netlify.app/tv** (sitio Netlify "gestotv" con `_redirects` →
  `releases/latest/download/gesto-tv.apk`). En el TV: app Downloader → ese link → Instalar.
  **Netlify: Rami cuida sus créditos de deploy — no redeployar salvo que sea imprescindible.** No hace falta para
  actualizar la app.
- La página del celular NO se publica aparte: viaja dentro del APK. Después de instalar, recargar en el celular.

## Ciclo rápido desde la notebook (Claude Code) — recomendado
Ya instalado (8/11/2026) con `Instalar-CoVi-ClaudeCode.bat`: Git, GitHub CLI (logueado como ramirodavalosmdq-dev),
adb (alias de winget) y Claude Code. Proyecto en `C:\Users\valen\Proyectos\gesto-tv`. Verificar `adb devices`
la primera vez (no se confirmó si el TV aceptó "Permitir depuración").
Requisitos en Windows: Git, GitHub CLI y adb (`winget install Git.Git GitHub.cli Google.PlatformTools`).
```
adb connect 192.168.100.2:5555          # la 1.ª vez el TV pregunta "¿Permitir depuración?" → Permitir siempre
gh run watch                            # esperar el build del último push
gh release download --pattern gesto-tv.apk --clobber
adb install -r gesto-tv.apk             # instala directo, sin Downloader
adb logcat -s GestoTV                   # registro interno en vivo mientras Rami prueba gestos
adb shell settings get secure enabled_accessibility_services   # ¿está activo el servicio?
```
Si `adb connect` falla: en el TV, Opciones de desarrollador → Depuración por red / USB activadas.

## Probar SIN el TV (hacerlo antes de cada push)
`tools/mock-tv` = TV simulado (mismo protocolo) que sirve la página real con la IA de manos reemplazada por un
simulador; `tools/tests/*.py` = Playwright con cámara falsa y mano simulada (`hand.js`:
`mkHand(pose, ox, oy, escala)`; poses open, point, pinch, fist, two, L, thumbside, thumbup, horns, three).
Ver `tools/mock-tv/README.md`. Validar también la sintaxis del módulo de la página (`node --check`).
Ojo: la mano simulada no reemplaza la prueba real — siempre decirle a Rami qué se probó y qué no.

## Convenciones
- Commits en español, con el detalle del porqué. Versionado = número de corrida de Actions.
- No romper lo que Rami ya confirmó que anda. Cambios de gestos: avisar en la guía (❔) de la página.
- Antes de editar, `git pull`: hubo sesiones paralelas que commitearon cambios.
