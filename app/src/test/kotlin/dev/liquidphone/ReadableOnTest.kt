// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.liquidphone.ui.glass.readableOn
import kotlin.test.Test
import kotlin.test.assertTrue

class ReadableOnTest {
    private fun contrast(a: Color, b: Color): Float {
        val hi = maxOf(a.luminance(), b.luminance())
        val lo = minOf(a.luminance(), b.luminance())
        return (hi + 0.05f) / (lo + 0.05f)
    }

    /** Recorre el espacio de colores: sobre cualquier fondo, el texto elegido llega a 4,5:1. */
    @Test
    fun siempreLlegaACuatroYMedio() {
        for (r in 0..255 step 15) for (g in 0..255 step 15) for (b in 0..255 step 15) {
            val background = Color(r, g, b)
            val c = contrast(readableOn(background), background)
            assertTrue(c >= 4.5f, "($r, $g, $b): $c")
        }
    }
}
