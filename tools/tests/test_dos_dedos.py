# Definición de Rami (8/11): palma = encender; ✌️ dos dedos juntos = calibrar (quietos), deslizar (mover), clic (empujar)
import asyncio, sys
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
        H = lambda pose,ox=0,oy=0,sc=1: pg.evaluate(f"window.__hand = mkHand('{pose}',{ox},{oy},{sc})")
        async def hold(pose, ms, ox=0, oy=0, sc=1): await H(pose,ox,oy,sc); await pg.wait_for_timeout(ms)
        got = lambda: pg.evaluate("fetch('/__got').then(r=>r.json())")
        await hold('open', 2700, 0.1, 0.05); await hold('open', 500, 0.1, 0.05)
        print("1) palma → modo:", await pg.inner_text("#modeText")); print("ERR:", errs)
        await pg.evaluate("window.__hand=null"); await pg.wait_for_timeout(800)
        n0 = len(await got())
        await hold('open', 600, 0.1, 0.05)
        sin = [g for g in (await got())[n0:] if g['t']=='pos']
        print("2) sin calibrar, punto en el TV:", len(sin), "(tiene que ser 0)")
        await hold('two', 1900, 0.1, 0.05)                          # ✌️ quietos → calibrar
        log = await pg.evaluate("document.body.innerText")
        print("3) calibrada:", "Pantalla calibrada" in log)
        await pg.evaluate("window.__hand=null"); await pg.wait_for_timeout(600)
        # deslizar: dos dedos y mover hacia arriba
        await hold('two', 300, 0.1, 0.05)
        for k in range(1, 9): await hold('two', 70, 0.1, 0.05 - k*0.02)
        await hold('open', 500, 0.1, -0.11)
        # clic: dos dedos quietos y empujar (la mano se agranda)
        await pg.evaluate("window.__hand=null"); await pg.wait_for_timeout(600)
        await hold('two', 500, 0.1, 0.05)
        for k in range(1, 6): await hold('two', 60, 0.1, 0.05, 1 + 0.025*k)
        await hold('two', 300, 0.1, 0.05, 1.12); await hold('two', 400, 0.1, 0.05, 1.0)
        await hold('open', 400, 0.1, 0.05)
        seq = [g['t'] for g in await got() if g['t'] in ('linked','sdown','sup','click','dpad','tdown')]
        print("órdenes:", seq)
        assert sin == [] and "Pantalla calibrada" in log and seq == ['linked','sdown','sup','click'], "no cumple la definición"
        print("OK dos dedos | errors:", errs); await b.close()
asyncio.run(main())
