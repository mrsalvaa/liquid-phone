// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone.data

import dev.liquidphone.ui.glass.GlassStyle
import kotlinx.serialization.Serializable

/**
 * Ajustes guardados en files/settings.json. Los 8 primeros son los de personalización de la suite:
 * mismos nombres, valores y valores por defecto que Liquid Calendar y Liquid Contacts. Los de
 * números vienen de Contacts y los del teléfono van después.
 */
@Serializable
data class AppSettings(
    /** 0 = según el sistema, 1 = claro, 2 = oscuro. */
    val themeMode: Int = 0,
    /** 0 = colores en movimiento (COLOR_SETS), 1 = color de énfasis, 2 = liso con acento azul. */
    val backgroundMode: Int = 2,
    /** Índice en COLOR_SETS. */
    val colorSet: Int = 0,
    /** Color de énfasis en ARGB, para backgroundMode = 1. */
    val accentColor: Long = 0xFF0EA5E9,
    val animatedBackground: Boolean = true,
    /** Estilo de interfaz: 0 = Liquid Glass, 1 = sólido. */
    val uiStyle: Int = 0,
    /** Color de énfasis del estilo sólido, en ARGB (aparte del de Liquid Glass). */
    val solidAccent: Long = 0xFF0EA5E9,
    /** Índice del icono de la app (ver AppIcons). */
    val appIcon: Int = 0,

    // --- Números (como en Contacts) ---

    /**
     * País (ISO 3166, p. ej. "AR") para los números sin prefijo internacional. Vacío hasta la
     * primera vez que se abre la app: ahí se toma el de la SIM (no el de la red, que cambia al
     * viajar). Si los chips son de países distintos no se adivina: se pregunta. Se puede cambiar
     * en Ajustes.
     */
    val defaultCountry: String = "",
    /** Código de área para los números guardados sin él (sin el 0). Vacío = no se completa. */
    val defaultAreaCode: String = "",
    /** El aviso de "números sin código de área" se descartó y no vuelve a salir. */
    val areaCodeNoticeDismissed: Boolean = false,

    // --- Teléfono ---

    /** Política para un toque sin chip elegido (ver SimPolicy): 0 = SIM 1, el valor por defecto. */
    val simPolicy: Int = 0,
    /** Pestaña al abrir: 0 = teclado, 1 = recientes, 2 = favoritos. */
    val startTab: Int = 0,
    /** Vibración leve al tocar las teclas. */
    val keypadHaptics: Boolean = true,
) {
    val glassStyle: GlassStyle get() = GlassStyle(themeMode, backgroundMode, colorSet, accentColor, uiStyle, solidAccent)
}
