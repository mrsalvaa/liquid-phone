// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.liquidphone.data.Repo
import dev.liquidphone.data.Stores
import dev.liquidphone.incall.IncomingScreen
import dev.liquidphone.incall.IncomingSlider
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
                GlassRoot(
                    animated = if (intent.hasExtra("fondo")) intent.getIntExtra("fondo", 2) == 0 else settings.animatedBackground,
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
                        chip = sims.chips.lastOrNull(),
                        chipUnknown = false,
                        emergency = false,
                        other = null,
                    )
                }
            }
        }
    }
}
