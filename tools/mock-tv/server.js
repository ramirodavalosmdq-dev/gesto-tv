// TV simulado: mismo protocolo que tv/.../GestoServer.kt. Sirve la página real del repo con la IA de manos
// reemplazada por stub.mjs (la mano la "dibuja" el test). Uso: node tools/mock-tv/server.js  ->  https://127.0.0.1:8443
const https = require("https"), fs = require("fs"), path = require("path"), { WebSocketServer } = require("ws");
const PAGE = path.join(__dirname, "../../tv/src/main/assets/web/index.html");
const CDN = "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.14/vision_bundle.mjs";
const page = () => fs.readFileSync(PAGE, "utf8").replace(CDN, "./stub.mjs");
const PIN = "4321"; const got = [];
const srv = https.createServer({ key: fs.readFileSync(path.join(__dirname, "key.pem")), cert: fs.readFileSync(path.join(__dirname, "cert.pem")) }, (req, res) => {
  if (req.url === "/" ) { res.writeHead(200, {"content-type":"text/html"}); return res.end(page()); }
  if (req.url === "/stub.mjs") { res.writeHead(200, {"content-type":"text/javascript"}); return res.end(fs.readFileSync(path.join(__dirname, "stub.mjs"))); }
  if (req.url === "/__got") { res.writeHead(200, {"content-type":"application/json"}); return res.end(JSON.stringify(got)); }
  res.writeHead(404); res.end();
});
const wss = new WebSocketServer({ server: srv, path: "/ws" });
wss.on("connection", ws => { let authed = false;
  ws.on("message", d => { const m = JSON.parse(d);
    if (!authed) { if (m.t === "hello" && m.pin === PIN) { authed = true; ws.send(JSON.stringify({t:"ok",version:"0.1.test"})); } else { ws.send(JSON.stringify({t:"badpin"})); ws.close(); } return; }
    if (m.t === "ping") return ws.send(JSON.stringify({t:"pong", id: m.id}));
    got.push(m);
    if (m.t === "key" && m.k.startsWith("ch_")) ws.send(JSON.stringify({t:"info", msg:"Cambiar de canal llega en la próxima versión"}));
  });
});
srv.listen(8443, () => console.log("mock up"));
