// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Revisa el código fuente: lo que no debe usarse nunca se detecta antes de compilar. Las reglas de
 * fondo están en el README; esto evita que alguien las rompa sin darse cuenta.
 */
class PrivacyGuardTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }

    private fun sources(): List<File> = listOf("app/src/main", "app/src/debug", "app/src/release", "core/src/main")
        .flatMap { File(root, it).walkTopDown().filter { f -> f.extension == "kt" }.toList() }

    @Test
    fun nuncaSeLeenIdentificadoresDelChipNiElNumeroPropio() {
        // Los nombres de las API, partidos para que este archivo no se detecte a sí mismo.
        val forbidden = listOf("icc" + "Id", "getIcc" + "Id", "subscriber" + "Id", "getSubscriber" + "Id", "get" + "Imei", ".im" + "ei", "getMei" + "d", "getLine1" + "Number", "line1" + "Number", "getPhone" + "Number(", "getDevice" + "Id", ".nu" + "mber?.toString")
        val hits = sources().flatMap { f -> forbidden.filter { it in f.readText() }.map { "${f.name}: $it" } }
        assertEquals(emptyList(), hits)
    }

    @Test
    fun nadaDeRedNiUbicacionNiServiciosDeGoogle() {
        val forbidden = listOf("java.net." + "URL", "HttpURL" + "Connection", "okhttp" + "3", "com.google.android." + "gms", "fire" + "base", "data" + "transport", "android.location." + "Location")
        val hits = sources().flatMap { f -> forbidden.filter { it in f.readText() }.map { "${f.name}: $it" } }
        assertEquals(emptyList(), hits)
    }

    @Test
    fun elManifestPrincipalPideSoloLosCincoPermisos() {
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        val requested = Regex("""<uses-permission\s+android:name="([^"]+)"\s*/>""").findAll(manifest).map { it.groupValues[1] }.toSet()
        assertEquals(
            setOf(
                "android.permission.READ_CONTACTS",
                "android.permission.READ_CALL_LOG",
                "android.permission.WRITE_CALL_LOG",
                "android.permission.READ_PHONE_STATE",
                "android.permission.CALL_PHONE",
            ),
            requested,
        )
        assertTrue("""android:allowBackup="false"""" in manifest)
        for (blocked in listOf("INTERNET", "ACCESS_COARSE_LOCATION", "ACCESS_FINE_LOCATION")) {
            assertTrue(Regex("""android\.permission\.$blocked"\s+tools:node="remove"""").containsMatchIn(manifest), blocked)
        }
    }

    @Test
    fun laEtapa1NoAtiendeLlamadasDeOtrasApps() {
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        for (filter in listOf("android.intent.action.DIAL", "android.intent.action.CALL", "android:scheme=\"tel\"", "InCallService", "android.intent.action.VIEW")) {
            assertFalse(filter in manifest, filter)
        }
    }

    @Test
    fun elSimulacroNoFrenaLasEmergencias() {
        val dialer = File(root, "app/src/main/java/dev/liquidphone/calls/Dialer.kt").readText()
        assertTrue(dialer.indexOf("DialerReason.EMERGENCY") in 0 until dialer.indexOf("DevTools.simulating()"))
    }
}
