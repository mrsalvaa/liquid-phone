// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone.calls

import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract
import android.provider.BlockedNumberContract.BlockedNumbers

/**
 * Bloqueo de números con la lista del propio celu (no sube a ningún lado). Es la del sistema porque
 * Telecom corta ahí la llamada antes de que suene, y porque el sistema suspende el bloqueo después
 * de una emergencia, para que puedan devolver el llamado. Solo el marcador predeterminado la puede
 * usar. Hace E/S: fuera del hilo principal.
 */
class SystemBlocked(context: Context) {
    private val app = context.applicationContext

    fun available(): Boolean = try {
        BlockedNumberContract.canCurrentUserBlockNumbers(app)
    } catch (e: RuntimeException) {
        false
    }

    fun list(): List<String> {
        if (!available()) return emptyList()
        return try {
            app.contentResolver.query(BlockedNumbers.CONTENT_URI, arrayOf(BlockedNumbers.COLUMN_ORIGINAL_NUMBER), null, null, null)?.use { c ->
                buildList { while (c.moveToNext()) c.getString(0)?.let(::add) }
            }.orEmpty()
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    fun isBlocked(number: String): Boolean = available() && try {
        BlockedNumberContract.isBlocked(app, number)
    } catch (e: RuntimeException) {
        false
    }

    /** false si no se pudo, por ejemplo sin el rol. */
    fun block(number: String): Boolean {
        if (!available()) return false
        return try {
            val values = ContentValues().apply { put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number) }
            app.contentResolver.insert(BlockedNumbers.CONTENT_URI, values) != null
        } catch (e: RuntimeException) {
            false
        }
    }

    fun unblock(number: String): Boolean {
        if (!available()) return false
        return try {
            BlockedNumberContract.unblock(app, number) > 0
        } catch (e: RuntimeException) {
            false
        }
    }
}
