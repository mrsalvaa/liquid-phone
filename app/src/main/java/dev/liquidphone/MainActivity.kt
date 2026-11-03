// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import dev.liquidphone.data.Repo
import dev.liquidphone.data.Stores
import dev.liquidphone.ui.glass.GlassRoot
import dev.liquidphone.ui.glass.GlassTheme

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        Repo.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by Stores.settings.state.collectAsStateWithLifecycle()
            DevTools.WindowGuard()
            GlassTheme(settings.glassStyle) {
                GlassRoot(animated = settings.animatedBackground, floating = {}) {}
            }
        }
    }
}
