// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.liquidphone.data.Repo
import dev.liquidphone.data.Stores
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Solo debug y profile. Desde adb (nunca desde otra app, ver el manifest de debug):
 *   adb shell am broadcast -f 0x20 -a dev.liquidphone.DEV_STATUS -p dev.liquidphone.debug
 *   adb shell am broadcast -f 0x20 -a dev.liquidphone.DEV_GENERATE -p dev.liquidphone.debug
 *   adb shell am broadcast -f 0x20 -a dev.liquidphone.DEV_CLEAR -p dev.liquidphone.debug
 *   adb shell am broadcast -f 0x20 -a dev.liquidphone.DEV_MEMORY -p dev.liquidphone.debug --ei on 1
 *   adb shell am broadcast -f 0x20 -a dev.liquidphone.DEV_ACCOUNT_CHECK -p dev.liquidphone.debug
 *   adb shell am broadcast -f 0x20 -a dev.liquidphone.DEV_CONTACT_CHECK -p dev.liquidphone.debug
 *   adb shell am broadcast -f 0x20 -a dev.liquidphone.DEV_STYLE -p dev.liquidphone.debug --ei background 0 --ei ui 0 --ei theme 1
 * El resultado sale en `adb logcat -d -s LiquidPhoneDev:I`: cantidades, nunca nombres ni números.
 *
 * A propósito no hay ninguna orden para apagar el modo seguro o el simulacro: eso se hace a mano.
 */
class DevReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sims = Repo.sims.state.value
                val result = when (intent.action) {
                    "dev.liquidphone.DEV_STATUS" -> DevTools.status(sims.chips, sims.known, sims.airplane)
                    "dev.liquidphone.DEV_GENERATE" -> DevTools.generate(context, sims.chips)
                    "dev.liquidphone.DEV_CLEAR" -> DevTools.clear(context)
                    "dev.liquidphone.DEV_ACCOUNT_CHECK" -> DevTools.accountCheck(context, sims.chips)
                    "dev.liquidphone.DEV_CONTACT_CHECK" -> DevTools.contactCheck(context)
                    "dev.liquidphone.DEV_MEMORY" -> {
                        val on = intent.getIntExtra("on", 0) == 1
                        DevTools.setMemoryLists(on)
                        "memoria=$on"
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
