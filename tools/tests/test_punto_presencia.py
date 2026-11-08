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
        # Regla de Rami: con el control encendido, el punto se ve SIEMPRE y sigue a la mano (sin tener que hacer el índice)
        await H('open', 0.1, 0.05); await pg.wait_for_timeout(2700)
        print("1) modo:", await pg.inner_text("#modeText"))
        await hold('two', 1900, 0.1, 0.05); await pg.evaluate("window.__hand=null"); await pg.wait_for_timeout(500)   # ✌️ quietos = ubicar la pantalla
        await hold('open', 700, 0.1, 0.05)          # mano donde activé → punto al centro
        await hold('open', 700, 0.1+0.08, 0.05)     # mano a tu izquierda → punto a la izquierda
        await hold('L', 700, 0.1, 0.05)             # otro gesto: el punto sigue
        got = await pg.evaluate("fetch('/__got').then(r=>r.json())")
        i = next(i for i,g in enumerate(got) if g['t']=='linked')
        poss = [g for g in got[i+1:] if g['t']=='pos']
        print("pos después de activar:", len(poss), "| todas con p=0:", all(g['p']==0 for g in poss))
        xs = [round(g['x'],2) for g in poss]
        print("x (centro → izquierda → centro):", xs[2], "…", min(xs), "…", xs[-1])
        print(xs)
        assert len(poss) > 20 and min(xs) < 0.4 and abs(xs[2]-0.5) < 0.08, "el punto no siguió a la mano"
        print("OK punto de presencia | errors:", errs); await b.close()
asyncio.run(main())
