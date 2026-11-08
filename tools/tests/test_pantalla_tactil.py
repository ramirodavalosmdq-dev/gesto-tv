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
        await H('open', 0.1, 0.05); await pg.wait_for_timeout(1300)
        color = await pg.evaluate("document.getElementById('holdBar').style.stroke")
        await pg.wait_for_timeout(1300); print("1) modo:", await pg.inner_text("#modeText"), "| ruedita:", color)
        await pg.evaluate("window.__hand=null"); await pg.wait_for_timeout(1700)
        await hold('point', 1300, 0.1, 0.05); print("2) modo:", await pg.inner_text("#modeText"))
        await hold('point', 600, 0.1, 0.05)                 # en el centro elegido
        await hold('point', 600, 0.1+0.08, 0.05)            # mano a tu izquierda
        await hold('point', 600, 0.1, 0.05, 1.12)           # empujar en el centro
        await hold('point', 600, 0.1, 0.05, 1.0)            # retirar
        await hold('fist', 1100)
        got = await pg.evaluate("fetch('/__got').then(r=>r.json())")
        ring = [g['p'] for g in got if g['t']=='pos'][:12]
        print("ruedita en el TV (p):", ring)
        i = next(i for i,g in enumerate(got) if g['t']=='linked'); print('linked ✔ (animación Enlace gestual)')
        rest = got[i+1:]
        seq=[]
        for g in rest:
            k = g['t'] if g['t']!='pos' else f"pos({g['x']:.2f},{g['y']:.2f},{g['p']})"
            seq.append(k)
        print("después de activar:", seq[:3], "…", [k for k in seq if not k.startswith('pos')])
        poss = [g for g in rest if g['t']=='pos']
        print("centro ≈", (round(poss[8]['x'],2), round(poss[8]['y'],2)), "| izquierda ≈", round([g for g in poss][28]['x'],2) if len(poss)>28 else None)
        print("errors:", errs); await b.close()
asyncio.run(main())
