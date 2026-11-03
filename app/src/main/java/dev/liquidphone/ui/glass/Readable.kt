// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone.ui.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Texto legible sobre un fondo de ese color. Con negro puro y 0,179, donde blanco y negro dan el mismo
 * contraste, siempre hay al menos 4,5:1. Contacts usa casi negro y 0,45, que daba 2,35:1 en tema claro.
 */
fun readableOn(color: Color): Color = if (color.luminance() > 0.179f) Color.Black else Color.White
