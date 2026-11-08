import asyncio, sys, json
from playwright.async_api import async_playwright
JS_HAND = open(sys.argv[1]).read()
async def main():
    async with async_playwright() as p:
        b = await p.chromium.launch(args=["--use-fake-device-for-media-stream","--use-fake-ui-for-media-stream"])
        ctx = await b.new_context(viewport={"width":412,"height":915}, permissions=["camera"], ignore_https_errors=True)
        pg = await ctx.new_page(); errs=[]; pg.on("pageerror", lambda e: errs.append(str(e)))
        pg.on("dialog", lambda d: asyncio.ensure_future(d.accept("4321")))
        await pg.goto("https://127.0.0.1:8443/"); await pg.add_script_tag(content=JS_HAND)
        await pg.click("#btnStart"); await pg.wait_for_timeout(2500)
        H = lambda pose,ox=0,oy=0: pg.evaluate(f"window.__hand = mkHand('{pose}',{ox},{oy})")
        async def hold(pose, ms, ox=0, oy=0): await H(pose,ox,oy); await pg.wait_for_timeout(ms)
        async def path(pose, x0,y0,x1,y1,n):
            for i in range(n+1): await H(pose, x0+(x1-x0)*i/n, y0+(y1-y0)*i/n); await pg.wait_for_timeout(33)
        await hold('open', 2600); await pg.evaluate("window.__hand=null"); await pg.wait_for_timeout(1700)
        await hold('two', 1300); print("modo:", await pg.inner_text("#modeText"))
        await hold('open', 1500)                       # quieto: no debe moverse nada
        await path('open', 0,0, -0.21,0, 6)            # rápido a la derecha del usuario: 2 pasos
        await hold('open', 500, -0.21, 0)
        await path('open', -0.21,0, 0,0, 60)           # vuelta lenta al centro: 0 pasos
        await hold('open', 500)
        await path('open', 0,0, 0,-0.16, 4)            # rápido arriba: 1 paso
        await hold('open', 400, 0, -0.16)
        await hold('fist', 800, 0, -0.16); await hold('open', 300, 0, -0.16)
        print("modo final:", await pg.inner_text("#modeText"))
        got = json.loads(await pg.evaluate("fetch('/__got').then(r=>r.text())"))
        print("TV recibió:", [g['t'] + ':' + (g.get('dir') or g.get('k') or '') for g in got if g['t'] in ('dpad','key')])
        print("errors:", errs); await b.close()
asyncio.run(main())
