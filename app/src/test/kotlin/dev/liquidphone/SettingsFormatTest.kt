// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone

import dev.liquidphone.data.AppSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

/** settings.json mantiene los nombres y valores por defecto de toda la suite. */
class SettingsFormatTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun losOchoCamposDeLaSuite() {
        val fields = json.encodeToJsonElement(AppSettings.serializer(), AppSettings()).jsonObject
        val expected = mapOf(
            "themeMode" to "0",
            "backgroundMode" to "2",
            "colorSet" to "0",
            "accentColor" to 0xFF0EA5E9.toString(),
            "animatedBackground" to "true",
            "uiStyle" to "0",
            "solidAccent" to 0xFF0EA5E9.toString(),
            "appIcon" to "0",
        )
        for ((name, value) in expected) assertEquals(value, fields[name].toString(), name)
    }

    @Test
    fun porDefectoLlamaConLaSim1() {
        assertEquals(0, AppSettings().simPolicy)
    }
}
