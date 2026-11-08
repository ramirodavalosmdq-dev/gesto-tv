import asyncio, sys, json
from collections import Counter
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
        await hold('open', 2600); await pg.evaluate("window.__hand=null"); await pg.wait_for_timeout(1700)
        await hold('point', 1500); print("modo:", await pg.inner_text("#modeText"))
        # pinza corta con un poco de movimiento natural -> debe ser CLIC
        await H('pinch',0,0); await pg.wait_for_timeout(100)
        for i in range(4): await H('pinch',0.005*i,0.004*i); await pg.wait_for_timeout(40)
        await hold('point', 500, 0.02, 0.016)
        # pinza larga + movimiento grande hacia arriba -> ARRASTRE con scroll
        await hold('pinch', 400, 0.02, 0.016)
        for i in range(20): await H('pinch',0.02,0.016-0.02*i); await pg.wait_for_timeout(33)
        await hold('point', 500, 0.02, -0.38)
        # menu de velocidad
        print("speed visible:", await pg.evaluate("getComputedStyle(document.getElementById('speed')).display"))
        await pg.click("#speedBtn"); print("opts open:", await pg.evaluate("document.getElementById('speedOpts').classList.contains('open')"))
        await pg.click("#speedOpts button[data-l='4']"); await pg.wait_for_timeout(3300)
        print("opts after 3s:", await pg.evaluate("document.getElementById('speedOpts').classList.contains('open')"), "| btn:", await pg.inner_text("#speedBtn"))
        got = json.loads(await pg.evaluate("fetch('/__got').then(r=>r.text())"))
        print("TV recibió:", Counter([g['t'] for g in got]))
        print("scrolls:", [ (g['dx'],g['dy']) for g in got if g['t']=='scroll'])
        print("log:", (await pg.inner_text("#log")).replace("\n"," | ")[:300])
        print("errors:", errs); await b.close()
asyncio.run(main())
