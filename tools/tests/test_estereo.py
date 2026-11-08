# Modo estéreo: un segundo celular (?cam=2) manda su vista de la mano; el principal toca por profundidad real.
import asyncio, sys
from playwright.async_api import async_playwright
JS_HAND = open(sys.argv[1]).read()
async def main():
    async with async_playwright() as p:
        b = await p.chromium.launch(args=["--use-fake-device-for-media-stream","--use-fake-ui-for-media-stream"])
        errs = []
        async def page(url):
            ctx = await b.new_context(viewport={"width":412,"height":915}, permissions=["camera"], ignore_https_errors=True)
            pg = await ctx.new_page(); pg.on("pageerror", lambda e: errs.append(str(e)))
            pg.on("dialog", lambda d: asyncio.ensure_future(d.accept("4321")))
            await pg.goto(url); await pg.add_script_tag(content=JS_HAND)
            await pg.click("#btnStart"); await pg.wait_for_timeout(2500); return pg
        A = await page("https://127.0.0.1:8443/")          # principal
        B = await page("https://127.0.0.1:8443/?cam=2")    # cámara 2 (ve la mano corrida 0,15)
        async def both(pose, ms, sc=1.0, disp=0.15):
            await A.evaluate(f"window.__hand = mkHand('{pose}',0.1,0.05,{sc})")
            await B.evaluate(f"window.__hand = mkHand('{pose}',0.1-{disp},0.05,{sc})")
            await A.wait_for_timeout(ms)
        print("cámara 2:", await B.inner_text("#readout"))
        await both('open', 2700)
        print("1) modo:", await A.inner_text("#modeText"), "|", (await A.inner_text("#readout"))[:20])
        await both('open', 400); await A.evaluate("window.__hand=null"); await B.evaluate("window.__hand=null"); await A.wait_for_timeout(1500)
        await both('point', 1400); print("2) modo:", await A.inner_text("#modeText"))
        await both('point', 600)
        # empujar: el dedo se acerca → la disparidad crece (y la palma un poco)
        for k in range(1, 11): await both('point', 60, 1.0 + 0.012*k, 0.15*(1 + 0.02*k))
        await both('point', 500, 1.12, 0.18)
        for k in range(1, 6): await both('point', 60, 1.12 - 0.024*k, 0.18 - 0.006*k)
        await both('point', 600, 1.0, 0.15)
        got = await A.evaluate("fetch('/__got').then(r=>r.json())")
        print("órdenes:", [g['t'] for g in got if g['t'] in ('linked','tdown','tup','cursor')])
        log = await A.evaluate("document.body.innerText")
        print("estéreo anclado:", "profundidad anclada" in log, "| datos cam2:", "Llegan datos de la cámara 2" in log)
        assert any(g['t']=='tdown' for g in got) and any(g['t']=='tup' for g in got), "no tocó con estéreo"
        print("OK estéreo | errors:", errs); await b.close()
asyncio.run(main())
