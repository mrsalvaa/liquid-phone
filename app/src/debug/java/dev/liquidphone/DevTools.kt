// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Debug y profile. Hasta tener la herramienta de pruebas, la app no ve ningún dato y no marca.
 */
object DevTools {
    const val available = true

    fun allowedContacts(context: Context): Set<Long>? = emptySet()
    fun allowedCalls(context: Context): Set<Long>? = emptySet()
    fun simulating(): Boolean = true
    fun logDecision(what: String) = Unit
    fun changes(): Flow<Unit> = emptyFlow()
}
