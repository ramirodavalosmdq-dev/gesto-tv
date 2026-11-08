package com.gestotv.tv;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

/**
 * "Dedo real" para el TV: NO corre dentro de la app, sino como programa aparte lanzado desde la depuración
 * del propio TV (KeyBridge), con el permiso de la consola (shell), que sí puede inyectar toques de pantalla.
 * Es la misma técnica que usa scrcpy para manejar un Android desde la PC.
 *
 *   CLASSPATH=<apk de Gesto TV> app_process /system/bin com.gestotv.tv.Injector
 *
 * Lee órdenes por la entrada estándar, una por línea:  d X Y (apoyar) · m X Y (mover) · u X Y (levantar) · c (cancelar)
 * Coordenadas en píxeles reales de la pantalla. Contesta "listo" al arrancar y "err ..." si algo falla.
 *
 * ¿Por qué? El arrastre por Accesibilidad (dispatchGesture/continueStroke) este TV lo cancela en milisegundos
 * (registro del 8/11: 12 de 12 intentos cancelados). Un toque inyectado así es indistinguible de una pantalla táctil.
 */
public final class Injector {
    private Injector() {}

    public static void main(String[] args) {
        Object im;
        Method inject;
        try {
            Class<?> cls;
            Object inst;
            try {
                cls = Class.forName("android.hardware.input.InputManagerGlobal");      // Android 14+
                inst = cls.getMethod("getInstance").invoke(null);
            } catch (Throwable t) {
                cls = Class.forName("android.hardware.input.InputManager");            // Android 13 y anteriores
                inst = cls.getMethod("getInstance").invoke(null);
            }
            im = inst;
            inject = cls.getMethod("injectInputEvent", InputEvent.class, int.class);
        } catch (Throwable t) {
            System.out.println("err init " + t);
            System.out.flush();
            return;
        }
        System.out.println("listo");
        System.out.flush();

        long downTime = 0;
        boolean down = false;
        float lx = 0, ly = 0;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] p = line.split(" ");
                char c = p[0].charAt(0);
                float x = p.length > 2 ? Float.parseFloat(p[1]) : lx;
                float y = p.length > 2 ? Float.parseFloat(p[2]) : ly;
                int action;
                switch (c) {
                    case 'd':
                        if (down) send(im, inject, downTime, MotionEvent.ACTION_CANCEL, lx, ly);
                        downTime = SystemClock.uptimeMillis(); down = true; action = MotionEvent.ACTION_DOWN; break;
                    case 'm':
                        if (!down) continue;
                        action = MotionEvent.ACTION_MOVE; break;
                    case 'u':
                        if (!down) continue;
                        down = false; action = MotionEvent.ACTION_UP; break;
                    case 'c':
                        if (!down) continue;
                        down = false; action = MotionEvent.ACTION_CANCEL; break;
                    case 'q':
                        return;
                    default:
                        continue;
                }
                lx = x; ly = y;
                if (!send(im, inject, downTime, action, x, y)) {
                    System.out.println("err inject " + c);
                    System.out.flush();
                }
            }
        } catch (Throwable t) {
            System.out.println("err " + t);
            System.out.flush();
        }
    }

    private static boolean send(Object im, Method inject, long downTime, int action, float x, float y) {
        MotionEvent ev = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0);
        ev.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try {
            Object ok = inject.invoke(im, ev, 0);   // 0 = asincrónico (no espera a la app)
            return !(ok instanceof Boolean) || (Boolean) ok;
        } catch (Throwable t) {
            return false;
        } finally {
            ev.recycle();
        }
    }
}
