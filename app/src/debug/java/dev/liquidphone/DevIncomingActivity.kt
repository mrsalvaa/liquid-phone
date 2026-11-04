// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.liquidphone.core.calls.CallPhase
import dev.liquidphone.core.calls.InCallControls
import dev.liquidphone.core.calls.LiveCall
import dev.liquidphone.data.Repo
import dev.liquidphone.data.Stores
import dev.liquidphone.incall.CallView
import dev.liquidphone.incall.InCallUi
import dev.liquidphone.incall.IncomingScreen
import dev.liquidphone.incall.IncomingSlider
import dev.liquidphone.incall.OngoingLayout
import dev.liquidphone.incall.OngoingOverlay
import dev.liquidphone.incall.OngoingScreen
import dev.liquidphone.ui.glass.GlassRoot
import dev.liquidphone.ui.glass.GlassTheme
import dev.liquidphone.ui.glass.isDarkTheme
import java.io.File

/**
 * Solo debug y profile: la pantalla de llamada entrante sin ninguna llamada, con datos inventados,
 * para ver el diseño y el gesto. No lee la agenda. Atender y rechazar solo escriben en el registro.
 *   adb shell am start -n dev.liquidphone.debug/dev.liquidphone.DevIncomingActivity --ez foto true
 * Con --ei tema (1 claro, 2 oscuro), --ei estilo (0 Liquid, 1 sólido) y --ei fondo (0 en movimiento,
 * 2 liso) se ve otro aspecto sin cambiar los ajustes guardados.
 */
// Con --es pantalla curso se ve la llamada en curso: sus botones no actúan sobre ninguna llamada.
class DevIncomingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Repo.start()
        val photo = if (intent.getBooleanExtra("foto", false)) {
            val file = File(cacheDir, "vista-previa.jpg")
            file.writeBytes(DevTools.photo(0xFF9C4F96.toInt(), "E"))
            Uri.fromFile(file).toString()
        } else {
            null
        }
        val number = FakeNumbers.number(0)
        setContent {
            val settings by Stores.settings.state.collectAsStateWithLifecycle()
            val sims by Repo.sims.state.collectAsStateWithLifecycle()
            val style = settings.glassStyle.let { s ->
                s.copy(
                    themeMode = intent.getIntExtra("tema", s.themeMode),
                    uiStyle = intent.getIntExtra("estilo", s.uiStyle),
                    backgroundMode = intent.getIntExtra("fondo", s.backgroundMode),
                )
            }
            SystemBarsFor(isDarkTheme(style.themeMode))
            GlassTheme(style) {
                val chip = sims.chips.lastOrNull()
                if (intent.getStringExtra("pantalla") == "curso") {
                    val ui = InCallUi(
                        calls = listOf(CallView(LiveCall(1, CallPhase.ACTIVE), "Elena Müller (prueba)", number, photo, chip, false, System.currentTimeMillis())),
                        controls = InCallControls(mute = true, route = true, hold = true, dtmf = true, add = true, hangup = true),
                    )
                    var keypad by rememberSaveable { mutableStateOf(false) }
                    val layout = remember { OngoingLayout() }
                    GlassRoot(
                        animated = style.backgroundMode == 0,
                        floating = { OngoingOverlay(true, ui, ui.calls.first(), keypad, { keypad = !keypad }, layout) {} },
                    ) {
                        OngoingScreen(ui, ui.calls.first(), keypad, layout)
                    }
                } else {
                    GlassRoot(
                        animated = style.backgroundMode == 0,
                        floating = {
                            IncomingSlider(
                                true,
                                { Log.i("LiquidPhoneDev", "vista previa: rechazar") },
                                { Log.i("LiquidPhoneDev", "vista previa: atender") },
                            )
                        },
                    ) {
                        IncomingScreen(
                            title = "Elena Müller (prueba)",
                            subtitle = number,
                            photoUri = photo,
                            chip = chip,
                            chipUnknown = false,
                            emergency = false,
                            other = null,
                        )
                    }
                }
            }
        }
    }
}
