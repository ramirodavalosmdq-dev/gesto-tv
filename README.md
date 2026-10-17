# Gesto TV

Controlá tu Smart TV Android con gestos de las manos, usando el celular como cámara.

## Cómo funciona (v0.1)
- **App en el TV** (`tv/`): servicio de accesibilidad que dibuja un cursor, hace clics, scroll,
  Atrás/Inicio, volumen y abre apps. Levanta un servidor seguro en `https://IP-DEL-TV:8443`.
- **Celular**: abrís esa dirección en Chrome. La página reconoce tu mano (MediaPipe) y manda
  las órdenes al TV por tu Wi-Fi. Nada pasa por internet salvo la descarga del modelo de manos.

## Instalar en el TV
1. Instalar **Downloader** (Play Store del TV) y permitirle instalar apps (Fuentes desconocidas).
2. En Downloader abrir:
   `https://github.com/ramirodavalosmdq-dev/gesto-tv/releases/latest/download/gesto-tv.apk`
3. Abrir **Gesto TV** y activar el servicio en Configuración → Accesibilidad → Gesto TV.

Cada cambio en `main` compila un APK nuevo automáticamente (GitHub Actions → Releases).
