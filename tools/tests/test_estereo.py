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
        await both('two', 1900)                                   # ✌️ quietos: las dos cámaras ubican la pantalla
        await A.evaluate("window.__hand=null"); await B.evaluate("window.__hand=null"); await A.wait_for_timeout(600)
        await both('two', 600)
        # empujar los dos dedos: se acercan a la tele → la disparidad crece (la palma, igual)
        for k in range(1, 11): await both('two', 60, 1.0, 0.15*(1 + 0.02*k))
        await both('two', 400, 1.0, 0.18)
        for k in range(1, 6): await both('two', 60, 1.0, 0.18 - 0.006*k)
        await both('open', 400, 1.0, 0.15)
        got = await A.evaluate("fetch('/__got').then(r=>r.json())")
        print("órdenes:", [g['t'] for g in got if g['t'] in ('linked','click','sdown','tdown','tup')])
        log = await A.evaluate("document.body.innerText")
        print("calibrada con 2 cámaras:", "con las dos cámaras" in log, "| datos cam2:", "Llegan datos de la cámara 2" in log)
        assert "con las dos cámaras" in log and [g['t'] for g in got if g['t'] in ('click','sdown')] == ['click'], "no hizo clic con estéreo"
        print("OK estéreo | errors:", errs); await b.close()
asyncio.run(main())
