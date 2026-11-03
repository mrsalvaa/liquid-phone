// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone.core.calls

import dev.liquidphone.core.phone.PhoneNumbers
import dev.liquidphone.core.sim.AccountKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CallLogToolsTest {
    private val numbers = PhoneNumbers("AR")
    private val a = AccountKey("falsa", "a")
    private val b = AccountKey("falsa", "b")

    private var nextId = 1L
    private fun row(number: String, type: CallType = CallType.INCOMING, account: AccountKey? = a, date: Long? = null, presentation: Presentation = Presentation.ALLOWED): CallRow {
        val id = nextId++
        return CallRow(id, number, type, date ?: (1_000_000 - id), account = account, presentation = presentation)
    }

    private fun sizes(rows: List<CallRow>) = CallLogTools.group(rows, numbers).map { it.count }

    @Test fun `agrupa consecutivas del mismo numero y chip`() {
        assertEquals(listOf(3), sizes(listOf(row("01113456789"), row("01113456789", CallType.MISSED), row("01113456789", CallType.OUTGOING))))
    }

    @Test fun `las formas argentinas del mismo numero son una sola`() {
        val rows = listOf(row("011 15 1345-6789"), row("+54 9 11 1345-6789"), row("+54 11 1345-6789"), row("01113456789"))
        assertEquals(listOf(4), sizes(rows))
    }

    @Test fun `el mismo numero con otro chip no se agrupa`() {
        assertEquals(listOf(1, 1), sizes(listOf(row("01113456789", account = a), row("01113456789", account = b))))
    }

    @Test fun `otra llamada en el medio corta el grupo`() {
        assertEquals(listOf(1, 1, 1), sizes(listOf(row("01113456789"), row("03514567890"), row("01113456789"))))
    }

    @Test fun `distinto codigo de area no se agrupa`() {
        assertEquals(listOf(1, 1), sizes(listOf(row("01113456789"), row("03514567890"))))
    }

    @Test fun `chip desconocido se agrupa aparte y no se confunde`() {
        assertEquals(listOf(1, 1, 1), sizes(listOf(row("01113456789", account = a), row("01113456789", account = null), row("01113456789", account = b))))
        assertEquals(listOf(2), sizes(listOf(row("01113456789", account = null), row("01113456789", account = null))))
    }

    @Test fun `numeros ocultos consecutivos se agrupan y no se pueden llamar`() {
        val rows = listOf(row("", presentation = Presentation.RESTRICTED), row("", presentation = Presentation.RESTRICTED), row("", presentation = Presentation.UNKNOWN))
        val groups = CallLogTools.group(rows, numbers)
        assertEquals(listOf(2, 1), groups.map { it.count })
        assertFalse(groups[0].latest.callable)
    }

    @Test fun `numero oculto no se agrupa con uno visible vacio`() {
        assertEquals(listOf(1, 1), sizes(listOf(row("", presentation = Presentation.RESTRICTED), row("01113456789"))))
    }

    @Test fun `numeros incompletos se comparan por digitos`() {
        assertEquals(listOf(2, 1), sizes(listOf(row("1467-8901"), row("14678901"), row("14678902"))))
    }

    @Test fun `el grupo informa perdidas, ids y la mas nueva`() {
        val rows = listOf(row("01113456789", CallType.OUTGOING), row("01113456789", CallType.MISSED))
        val group = CallLogTools.group(rows, numbers).single()
        assertTrue(group.hasMissed)
        assertEquals(rows.map { it.id }, group.ids)
        assertEquals(rows[0], group.latest)
        assertEquals(rows[0].id, group.key)
    }

    @Test fun `lista vacia`() {
        assertEquals(emptyList(), CallLogTools.group(emptyList(), numbers))
    }

    // --- Filtro por chip ---

    @Test fun `al filtrar por chip las que quedan se agrupan`() {
        val rows = listOf(row("01113456789", account = a), row("01113456789", account = b), row("01113456789", account = a))
        assertEquals(listOf(1, 1, 1), sizes(rows))
        assertEquals(listOf(2), sizes(CallLogTools.filter(rows, ChipFilter.Only(a))))
    }

    @Test fun `filtro de chips que ya no estan incluye las filas sin chip`() {
        val gone = AccountKey("falsa", "retirado")
        val rows = listOf(row("1", account = a), row("2", account = gone), row("3", account = null))
        val kept = CallLogTools.filter(rows, ChipFilter.NoLongerPresent(setOf(a, b)))
        assertEquals(listOf("2", "3"), kept.map { it.number })
    }

    @Test fun `sin filtro quedan todas`() {
        val rows = listOf(row("1"), row("2", account = null))
        assertEquals(rows, CallLogTools.filter(rows, ChipFilter.All))
    }

    // --- Última saliente ---

    @Test fun `ultima saliente devuelve su chip, en cualquier formato`() {
        val rows = listOf(row("+54 9 11 1345-6789", CallType.OUTGOING, b, date = 200), row("011 1345-6789", CallType.OUTGOING, a, date = 100))
        assertEquals(b, CallLogTools.lastOutgoing(rows, "011 15 1345-6789", numbers))
    }

    @Test fun `ignora entrantes y perdidas`() {
        val rows = listOf(row("01113456789", CallType.INCOMING, b, date = 300), row("01113456789", CallType.MISSED, b, date = 200), row("01113456789", CallType.OUTGOING, a, date = 100))
        assertEquals(a, CallLogTools.lastOutgoing(rows, "01113456789", numbers))
    }

    @Test fun `sin salientes a ese numero no hay historial`() {
        val rows = listOf(row("01113456789", CallType.INCOMING, b), row("03514567890", CallType.OUTGOING, b))
        assertNull(CallLogTools.lastOutgoing(rows, "01113456789", numbers))
    }

    @Test fun `no depende del orden de las filas`() {
        val rows = listOf(row("01113456789", CallType.OUTGOING, a, date = 100), row("01113456789", CallType.OUTGOING, b, date = 200))
        assertEquals(b, CallLogTools.lastOutgoing(rows, "01113456789", numbers))
    }

    @Test fun `si la ultima saliente no dice el chip no se adivina con una mas vieja`() {
        val rows = listOf(row("01113456789", CallType.OUTGOING, null, date = 200), row("01113456789", CallType.OUTGOING, b, date = 100))
        assertNull(CallLogTools.lastOutgoing(rows, "01113456789", numbers))
    }

    @Test fun `numero vacio no tiene historial`() {
        assertNull(CallLogTools.lastOutgoing(listOf(row("", CallType.OUTGOING)), "", numbers))
    }

    @Test fun `tipos y presentacion del proveedor`() {
        assertEquals(CallType.MISSED, CallType.fromProvider(3))
        assertEquals(CallType.OUTGOING, CallType.fromProvider(2))
        assertEquals(CallType.OTHER, CallType.fromProvider(99))
        assertEquals(Presentation.RESTRICTED, Presentation.fromProvider(2))
        assertEquals(Presentation.UNKNOWN, Presentation.fromProvider(0))
    }
}
