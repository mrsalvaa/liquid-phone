// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.liquidphone.core.calls.CallRow
import dev.liquidphone.core.model.ContactSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * En la release no existen el modo seguro, el simulacro ni los datos ficticios: sin filtro y las
 * llamadas son de verdad. La versión de debug y profile está en src/debug.
 */
object DevTools {
    const val available = false

    fun init(context: Context) = Unit
    fun allowedContacts(context: Context): Set<Long>? = null
    fun allowedCalls(context: Context): Set<Long>? = null
    fun memoryContacts(): List<ContactSummary>? = null
    fun memoryCalls(): List<CallRow>? = null
    fun forgetCalls(ids: Collection<Long>) = Unit
    fun simulating(): Boolean = false
    fun simulatedText(outcome: dev.liquidphone.calls.DialOutcome.Simulated): String? = null
    fun logDecision(what: String) = Unit
    fun logCall(what: String) = Unit
    fun maskNumber(number: String): String? = number
    fun changes(): Flow<Unit> = emptyFlow()

    @Composable
    fun WindowGuard() = Unit

    @Composable
    fun FakeDataBanner(modifier: Modifier = Modifier) = Unit

    @Composable
    fun SettingsCard() = Unit

    @Composable
    fun SafeModeHint(modifier: Modifier = Modifier) = Unit
}
