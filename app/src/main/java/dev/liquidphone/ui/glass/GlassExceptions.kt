// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone.ui.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Lo único de ui/glass que difiere de Liquid Contacts: dos ajustes de contraste para tema claro. El
 * detalle y el motivo están en DESIGN.md, "Excepciones locales de ui/glass".
 *
 * Para volver al aspecto de la suite: [ENABLED] = false. Para sacarlas del todo: borrar este archivo
 * y las dos llamadas en Theme.kt. Cuando Contacts y Calendar adopten estos valores, esta capa sobra.
 */
object GlassExceptions {
    const val ENABLED = true

    /** Negro puro y 0,179, donde blanco y negro dan el mismo contraste: siempre al menos 4,5:1. */
    fun readableOn(color: Color): Color = if (color.luminance() > 0.179f) Color.Black else Color.White

    /** Opacidad del texto secundario en las paletas claras (Contacts: 0,58 y 0,55). */
    private const val LIGHT_SECONDARY_ALPHA = 0.66f

    fun palette(p: GlassPalette): GlassPalette =
        if (ENABLED && !p.dark) p.copy(contentSecondary = p.contentSecondary.copy(alpha = LIGHT_SECONDARY_ALPHA)) else p
}
