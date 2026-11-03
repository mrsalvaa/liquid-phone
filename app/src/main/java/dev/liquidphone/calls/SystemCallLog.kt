// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone.calls

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog.Calls
import dev.liquidphone.core.calls.CallRow
import dev.liquidphone.core.calls.CallType
import dev.liquidphone.core.calls.Presentation
import dev.liquidphone.core.sim.AccountKey
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * El registro de llamadas del sistema. La app no guarda copia. [allowedCalls] devuelve los ids que
 * se pueden ver y borrar, o null si no hay filtro; va dentro del SQL (ver el modo seguro de debug).
 * Todas las funciones hacen E/S: llamarlas fuera del hilo principal.
 */
class SystemCallLog(context: Context, private val allowedCalls: () -> Set<Long>?) {
    private val resolver = context.contentResolver

    /** De la más nueva a la más vieja. */
    fun rows(): List<CallRow> {
        val allowed = allowedCalls()
        val selection = allowed?.let { "${Calls._ID} IN (${it.ifEmpty { setOf(-1L) }.joinToString(",")})" }
        val uri = Calls.CONTENT_URI.buildUpon().appendQueryParameter(Calls.LIMIT_PARAM_KEY, MAX_ROWS.toString()).build()
        return resolver.query(uri, PROJECTION, selection, null, "${Calls.DATE} DESC, ${Calls._ID} DESC")?.use { c ->
            buildList(c.count) {
                while (c.moveToNext()) {
                    val component = c.getString(5)
                    val accountId = c.getString(6)
                    add(
                        CallRow(
                            id = c.getLong(0),
                            number = c.getString(1).orEmpty(),
                            type = CallType.fromProvider(c.getInt(2)),
                            dateMillis = c.getLong(3),
                            durationSeconds = c.getLong(4),
                            account = if (component.isNullOrEmpty() || accountId.isNullOrEmpty()) null else AccountKey(component, accountId),
                            presentation = Presentation.fromProvider(c.getInt(7)),
                        ),
                    )
                }
            }
        }.orEmpty()
    }

    /** Perdidas sin ver, de la más nueva a la más vieja. Con filtro, solo las permitidas. */
    fun newMissed(): List<CallRow> =
        rows().filter { it.type == CallType.MISSED }.let { missed ->
            val fresh = resolver.query(Calls.CONTENT_URI, arrayOf(Calls._ID), newMissedSelection(), null, null)?.use { c ->
                buildSet { while (c.moveToNext()) add(c.getLong(0)) }
            }.orEmpty()
            missed.filter { it.id in fresh }
        }

    /** Dejan de ser nuevas. Con filtro, solo las permitidas: lo real no se toca. */
    fun markMissedSeen() {
        val values = android.content.ContentValues().apply { put(Calls.NEW, 0) }
        try {
            resolver.update(Calls.CONTENT_URI, values, newMissedSelection(), null)
        } catch (e: SecurityException) {
            // Sin permiso de escribir, solo sigue figurando como nueva.
        }
    }

    private fun newMissedSelection(): String {
        val base = "${Calls.TYPE} = ${Calls.MISSED_TYPE} AND ${Calls.NEW} = 1"
        val allowed = allowedCalls() ?: return base
        return "$base AND ${Calls._ID} IN (${allowed.ifEmpty { setOf(-1L) }.joinToString(",")})"
    }

    /** Borra esas filas, una por una. Con filtro, solo las permitidas. Devuelve cuántas borró. */
    fun delete(ids: Collection<Long>): Int {
        val allowed = allowedCalls()
        val targets = if (allowed == null) ids else ids.filter { it in allowed }
        return targets.sumOf { id -> resolver.delete(Calls.CONTENT_URI, "${Calls._ID} = ?", arrayOf(id.toString())) }
    }

    /** Emite cada vez que cambia el registro. */
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(Calls.CONTENT_URI, true, observer)
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.conflate()

    private companion object {
        const val MAX_ROWS = 2000
        val PROJECTION = arrayOf(
            Calls._ID, Calls.NUMBER, Calls.TYPE, Calls.DATE, Calls.DURATION,
            Calls.PHONE_ACCOUNT_COMPONENT_NAME, Calls.PHONE_ACCOUNT_ID, Calls.NUMBER_PRESENTATION,
        )
    }
}
