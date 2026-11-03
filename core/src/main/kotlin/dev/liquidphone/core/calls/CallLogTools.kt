// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone.core.calls

import dev.liquidphone.core.phone.PhoneNumbers
import dev.liquidphone.core.sim.AccountKey

enum class CallType {
    INCOMING, OUTGOING, MISSED, VOICEMAIL, REJECTED, BLOCKED, ANSWERED_ELSEWHERE, OTHER;

    companion object {
        /** Los valores de CallLog.Calls.TYPE. */
        fun fromProvider(value: Int): CallType = when (value) {
            1 -> INCOMING
            2 -> OUTGOING
            3 -> MISSED
            4 -> VOICEMAIL
            5 -> REJECTED
            6 -> BLOCKED
            7 -> ANSWERED_ELSEWHERE
            else -> OTHER
        }
    }
}

enum class Presentation {
    ALLOWED, RESTRICTED, UNKNOWN, PAYPHONE;

    companion object {
        /** Los valores de CallLog.Calls.NUMBER_PRESENTATION. */
        fun fromProvider(value: Int): Presentation = when (value) {
            1 -> ALLOWED
            2 -> RESTRICTED
            4 -> PAYPHONE
            else -> UNKNOWN
        }
    }
}

/** Una fila del registro del sistema. [account] es null si la fila no dice de qué chip es. */
data class CallRow(
    val id: Long,
    val number: String,
    val type: CallType,
    val dateMillis: Long,
    val durationSeconds: Long = 0,
    val account: AccountKey? = null,
    val presentation: Presentation = Presentation.ALLOWED,
) {
    /** Se le puede devolver la llamada: tiene un número visible. */
    val callable: Boolean get() = presentation == Presentation.ALLOWED && number.any(Char::isDigit)
}

/** Llamadas consecutivas del mismo número y del mismo chip. [rows] va de la más nueva a la más vieja. */
data class CallGroup(val rows: List<CallRow>) {
    val latest: CallRow get() = rows.first()
    val number: String get() = latest.number
    val account: AccountKey? get() = latest.account
    val count: Int get() = rows.size
    val hasMissed: Boolean get() = rows.any { it.type == CallType.MISSED }
    val ids: List<Long> get() = rows.map { it.id }

    /** Clave estable para la lista. */
    val key: Long get() = latest.id
}

/** Filtro de recientes por chip. */
sealed interface ChipFilter {
    data object All : ChipFilter
    data class Only(val account: AccountKey) : ChipFilter

    /** Filas sin chip o de un chip que ya no está: no se adivina de cuál son. */
    data class NoLongerPresent(val present: Set<AccountKey>) : ChipFilter
}

object CallLogTools {
    /** Agrupa filas ya ordenadas de la más nueva a la más vieja. */
    fun group(rows: List<CallRow>, numbers: PhoneNumbers): List<CallGroup> {
        val cache = HashMap<String, String>()
        fun identity(row: CallRow): String = when {
            // Sin número visible no se puede saber si es la misma persona, salvo que venga igual.
            !row.callable -> "hidden:${row.presentation}:${row.number}"
            else -> cache.getOrPut(row.number) { numbers.exact(row.number) ?: row.number.filter(Char::isDigit) }
        }

        val groups = ArrayList<MutableList<CallRow>>()
        var lastIdentity: String? = null
        var lastAccount: AccountKey? = null
        for (row in rows) {
            val id = identity(row)
            val current = groups.lastOrNull()
            if (current != null && id == lastIdentity && row.account == lastAccount) {
                current.add(row)
            } else {
                groups.add(arrayListOf(row))
                lastIdentity = id
                lastAccount = row.account
            }
        }
        return groups.map(::CallGroup)
    }

    /** Se filtra antes de agrupar: sin las filas del otro chip, las que quedan pasan a ser consecutivas. */
    fun filter(rows: List<CallRow>, filter: ChipFilter): List<CallRow> = when (filter) {
        ChipFilter.All -> rows
        is ChipFilter.Only -> rows.filter { it.account == filter.account }
        is ChipFilter.NoLongerPresent -> rows.filter { it.account == null || it.account !in filter.present }
    }

    /**
     * Chip de la última llamada saliente a [number], o null si no hay historial o esa fila no dice
     * con qué chip salió (no se sigue buscando en otras más viejas: no sería la última).
     */
    fun lastOutgoing(rows: List<CallRow>, number: String, numbers: PhoneNumbers): AccountKey? {
        val wanted = numbers.exact(number)
        val wantedDigits = number.filter(Char::isDigit)
        if (wantedDigits.isEmpty()) return null
        return rows
            .filter { it.type == CallType.OUTGOING && it.callable }
            .filter { row ->
                val exact = numbers.exact(row.number)
                if (wanted != null && exact != null) exact == wanted else row.number.filter(Char::isDigit) == wantedDigits
            }
            .maxByOrNull { it.dateMillis }
            ?.account
    }
}
