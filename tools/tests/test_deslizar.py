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
        H = lambda pose,ox=0,oy=0,sc=1: pg.evaluate(f"window.__hand = mkHand('{pose}',{ox},{oy},{sc})")
        async def hold(pose, ms, ox=0, oy=0, sc=1): await H(pose,ox,oy,sc); await pg.wait_for_timeout(ms)
        # Pedido de Rami: ✌️ dos dedos juntos y mover la mano = deslizar la pantalla (sin flechas)
        await H('open', 0.1, 0.05); await pg.wait_for_timeout(2700)
        print("1) modo:", await pg.inner_text("#modeText"))
        await hold('open', 600, 0.1, 0.05)
        await hold('two', 500, 0.1, 0.05)            # agarrar en el centro
        for k in range(1, 9):                         # arrastrar hacia arriba de a poco
            await hold('two', 70, 0.1, 0.05 - k*0.02)
        await hold('open', 600, 0.1, -0.11)          # soltar
        got = await pg.evaluate("fetch('/__got').then(r=>r.json())")
        i = next(i for i,g in enumerate(got) if g['t']=='linked')
        seq = [g for g in got[i+1:] if g['t'] in ('sdown','sup','dpad','tdown','tup')]
        print("órdenes:", [g['t'] for g in seq])
        ia = next(k for k,g in enumerate(got) if g['t']=='sdown'); ib = next(k for k,g in enumerate(got) if g['t']=='sup')
        ys = [round(g['y'],2) for g in got[ia:ib] if g['t']=='pos']
        print("punto durante el arrastre (y):", ys[0], "→", ys[-1], f"({len(ys)} posiciones)")
        assert [g['t'] for g in seq] == ['sdown','sup'] and ys[-1] < ys[0] - 0.2, "no deslizó"
        print("modo final:", await pg.inner_text("#modeText"), "| OK deslizar | errors:", errs); await b.close()
asyncio.run(main())
