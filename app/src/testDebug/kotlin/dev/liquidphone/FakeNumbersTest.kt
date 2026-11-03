// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone

import dev.liquidphone.core.phone.PhoneNumbers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeNumbersTest {
    private val all = (0 until 500).map(FakeNumbers::number)

    @Test
    fun todosEstanEnRangosReservados() {
        for (n in all) assertTrue(FakeNumbers.isReserved(n), n)
    }

    @Test
    fun ningunoEsArgentino() {
        for (n in all) assertFalse(n.filter(Char::isDigit).startsWith("54"), n)
    }

    @Test
    fun noSeRepiten() {
        val numbers = PhoneNumbers("AR")
        assertEquals(all.size, all.map { it.filter(Char::isDigit) }.toSet().size)
        assertEquals(all.size, all.map { numbers.key(it).digits }.toSet().size)
    }

    @Test
    fun losDeEstadosUnidosSonValidosParaLaApp() {
        val numbers = PhoneNumbers("AR")
        for (n in all.filter { it.startsWith("+1") }) assertNotNull(numbers.exact(n), n)
        assertEquals(numbers.exact("+1 202 555-0123"), numbers.exact("+12025550123"))
    }

    /** libphonenumber no da por válido este rango: se muestra tal cual y se compara por la cola. */
    @Test
    fun losDelReinoUnidoSeMuestranTalCualYCoincidenPorLaCola() {
        val numbers = PhoneNumbers("AR")
        assertNull(numbers.exact("+44 7700 900123"))
        assertEquals("+44 7700 900123", numbers.format("+44 7700 900123"))
        assertEquals(numbers.key("+44 7700 900123").tail, numbers.key("+447700900123").tail)
    }

    @Test
    fun rechazaLoQueNoEstaReservado() {
        val rejected = listOf(
            "", "+54 9 11 5550-0100", "011 15 5550-0100", "15 5550-0100", "0351 555-0100",
            "+1 202 555-0200", "+1 202 555-0099", "+44 7700 901000", "+44 7700 9001", "12025550123", "447700900123",
        )
        for (n in rejected) assertFalse(FakeNumbers.isReserved(n), n)
    }
}
