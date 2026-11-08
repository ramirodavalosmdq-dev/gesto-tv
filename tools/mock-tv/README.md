# TV simulado para probar sin el TV

```
cd tools/mock-tv
npm install
openssl req -x509 -newkey rsa:2048 -nodes -keyout key.pem -out cert.pem -days 30 -subj "/CN=Gesto TV"
node server.js            # https://127.0.0.1:8443  (código: 4321)
```
`GET /__got` devuelve todas las órdenes que recibió el "TV". Los tests de `tools/tests` (Playwright, Python)
abren la página real con una cámara falsa y una mano simulada (`hand.js`, `mkHand(pose, ox, oy, escala)`).
```
python -m pip install playwright && python -m playwright install chromium
python tools/tests/test_pantalla_tactil.py tools/tests/hand.js
```
