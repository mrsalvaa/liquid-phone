// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.liquidphone.core.calls.CallPhase
import dev.liquidphone.core.calls.LiveCall
import dev.liquidphone.data.Stores
import dev.liquidphone.incall.CallManager
import dev.liquidphone.incall.CallNotifications
import dev.liquidphone.incall.CallView
import dev.liquidphone.incall.InCallUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Solo debug y profile. Desde adb (nunca desde otra app, ver el manifest de debug):
 *   adb shell am broadcast -f 0x20 -a dev.liquidphone.DEV_STATUS -p dev.liquidphone.debug
 *   adb shell am broadcast -f 0x20 -a dev.liquidphone.DEV_GENERATE -p dev.liquidphone.debug
 *   adb shell am broadcast -f 0x20 -a dev.liquidphone.DEV_CLEAR -p dev.liquidphone.debug
 *   adb shell am broadcast -f 0x20 -a dev.liquidphone.DEV_STYLE -p dev.liquidphone.debug --ei background 0 --ei ui 0 --ei theme 1
 * El resultado sale en `adb logcat -d -s LiquidPhoneDev:I`: cantidades, nunca nombres ni números.
 */
class DevReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = when (intent.action) {
                    "dev.liquidphone.DEV_STATUS" -> DevTools.status(context)
                    "dev.liquidphone.DEV_GENERATE" -> DevTools.generate(context)
                    "dev.liquidphone.DEV_CLEAR" -> DevTools.clear(context)
                    // Publica la notificación de una entrante, sin llamada, para ver si el sistema la acepta.
                    "dev.liquidphone.DEV_RING" -> {
                        val quiet = intent.getIntExtra("quiet", 1) == 1
                        val ui = InCallUi(calls = listOf(CallView(LiveCall(1, CallPhase.RINGING), "Prueba", null, null, null, false, 0L)))
                        withContext(Dispatchers.Main) {
                            if (quiet) CallManager.expectScreen()
                            CallNotifications.update(context, ui)
                        }
                        "notificada quiet=$quiet"
                    }
                    "dev.liquidphone.DEV_RING_END" -> {
                        withContext(Dispatchers.Main) { CallNotifications.cancelAll(context) }
                        "cancelada"
                    }
                    "dev.liquidphone.DEV_STYLE" -> {
                        val bg = intent.getIntExtra("background", 2)
                        val ui = intent.getIntExtra("ui", 0)
                        val theme = intent.getIntExtra("theme", 0)
                        Stores.settings.update { it.copy(backgroundMode = bg, uiStyle = ui, themeMode = theme) }
                        "estilo=$bg/$ui/$theme"
                    }
                    else -> "desconocido"
                }
                Log.i("LiquidPhoneDev", "${intent.action}: $result")
            } catch (e: SecurityException) {
                // Sin el mensaje: podría nombrar datos del proveedor.
                Log.i("LiquidPhoneDev", "${intent.action}: falta un permiso")
            } finally {
                pending.finish()
            }
        }
    }
}
