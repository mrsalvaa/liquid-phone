// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * En la release no existen el modo seguro, el simulacro ni los datos inventados: sin filtro y las
 * llamadas son de verdad. La versión de debug y profile está en src/debug.
 */
object DevTools {
    const val available = false

    fun allowedContacts(context: Context): Set<Long>? = null
    fun allowedCalls(context: Context): Set<Long>? = null
    fun simulating(): Boolean = false
    fun logDecision(what: String) = Unit
    fun changes(): Flow<Unit> = emptyFlow()
}
