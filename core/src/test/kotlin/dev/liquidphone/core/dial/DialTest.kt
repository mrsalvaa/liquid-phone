// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone.core.dial

import dev.liquidphone.core.phone.PhoneNumbers
import kotlin.test.Test
import kotlin.test.assertEquals

class DialTest {
    private val numbers = PhoneNumbers("AR")

    private fun entry(name: String, vararg phones: String) = DialEntry(name, name, phones.toList(), numbers::searchForms)

    // --- T9 ---

    @Test fun `la enie va con la 6`() {
        assertEquals("7362", T9.digitsOf("Peña"))
        assertEquals("62638", T9.digitsOf("ÑANDÚ"))
    }

    @Test fun `las tildes y la dieresis no cuentan`() {
        assertEquals(T9.digitsOf("Nunez"), T9.digitsOf("Núñez"))
        assertEquals("68639", T9.digitsOf("Núñez"))
        assertEquals("5673", T9.digitsOf("José"))
        assertEquals("264352", T9.digitsOf("Ángela"))
        assertEquals("685537", T9.digitsOf("Müller"))
        assertEquals("483637", T9.digitsOf("Güemes"))
    }

    @Test fun `la enie escrita como dos caracteres da lo mismo`() {
        assertEquals(T9.digitsOf("Peña"), T9.digitsOf("Peña"))
    }

    @Test fun `todas las letras caen en su tecla`() {
        val expected = "22233344455566677778889999"
        assertEquals(expected, T9.digitsOf(('a'..'z').joinToString("")))
        assertEquals(expected, T9.digitsOf(('A'..'Z').joinToString("")))
    }

    @Test fun `lo que no tiene tecla no se incluye y los numeros quedan`() {
        assertEquals("262", T9.digitsOf("A-n a!"))
        assertEquals("2622", T9.digitsOf("Ana2"))
        assertEquals("", T9.digitsOf("日本語 😀"))
    }

    @Test fun `las teclas muestran la enie en la 6`() {
        assertEquals("MNOÑ", T9.KEY_LETTERS['6'])
        assertEquals(8, T9.KEY_LETTERS.size)
    }

    // --- Búsqueda ---

    @Test fun `busca por el principio del nombre`() {
        assertEquals(listOf("Ana Pérez"), DialSearch.search("262", listOf(entry("Ana Pérez"), entry("Bruno Díaz"))))
    }

    @Test fun `busca por el principio de cualquier palabra`() {
        assertEquals(listOf("Ana Pérez"), DialSearch.search("7373", listOf(entry("Ana Pérez"))))
    }

    @Test fun `no busca en el medio de una palabra`() {
        assertEquals(emptyList(), DialSearch.search("373", listOf(entry("Pérez"))))
    }

    @Test fun `sigue de una palabra a la siguiente`() {
        assertEquals(listOf("Ana Pérez"), DialSearch.search("262737", listOf(entry("Ana Pérez"))))
    }

    @Test fun `apostrofo y guion separan palabras`() {
        assertEquals(listOf("O'Brien"), DialSearch.search("27436", listOf(entry("O'Brien"))))
        assertEquals(listOf("María-José"), DialSearch.search("5673", listOf(entry("María-José"))))
    }

    @Test fun `ñ se encuentra con la 6`() {
        assertEquals(listOf("Señor Núñez"), DialSearch.search("68639", listOf(entry("Señor Núñez"))))
    }

    @Test fun `busca por numero en cualquier forma argentina`() {
        val e = listOf(entry("Ana", "+54 9 11 2345-6789"))
        for (typed in listOf("2345", "1123456789", "5491123456789", "6789")) {
            assertEquals(listOf("Ana"), DialSearch.search(typed, e), typed)
        }
    }

    @Test fun `el nombre al principio gana a la palabra y al numero`() {
        val list = listOf(entry("Zoe", "22334455"), entry("Pedro Ana"), entry("Ana"))
        assertEquals(listOf("Ana", "Pedro Ana"), DialSearch.search("262", list))
        val withNumber = listOf(entry("Zoe", "01126255555"), entry("Ana"))
        assertEquals(listOf("Ana", "Zoe"), DialSearch.search("262", withNumber))
    }

    @Test fun `a igual puntaje conserva el orden recibido`() {
        assertEquals(listOf("Ana", "Anabela", "Anahí"), DialSearch.search("262", listOf(entry("Ana"), entry("Anabela"), entry("Anahí"))))
    }

    @Test fun `sin digitos no devuelve nada`() {
        assertEquals(emptyList(), DialSearch.search("", listOf(entry("Ana"))))
        assertEquals(emptyList(), DialSearch.search("*#", listOf(entry("Ana"))))
    }

    // --- Teclado ---

    @Test fun `escribe digitos y simbolos`() {
        assertEquals("1*#", DialBuffer.type(DialBuffer.type(DialBuffer.type("", '1'), '*'), '#'))
        assertEquals("1", DialBuffer.type("1", 'a'))
    }

    @Test fun `mantener 0 pone el mas adelante una sola vez`() {
        assertEquals("+", DialBuffer.plus(""))
        assertEquals("+54", DialBuffer.plus("54"))
        assertEquals("+54", DialBuffer.plus("+54"))
    }

    @Test fun `borrar y mantener borrar`() {
        assertEquals("12", DialBuffer.backspace("123"))
        assertEquals("", DialBuffer.backspace(""))
        assertEquals("", DialBuffer.clear())
    }

    @Test fun `tiene un tope de largo`() {
        val full = "1".repeat(DialBuffer.MAX_LENGTH)
        assertEquals(full, DialBuffer.type(full, '2'))
        assertEquals(full, DialBuffer.plus(full))
    }

    @Test fun `limpia lo que viene de otra parte`() {
        assertEquals("+5491123456789", DialBuffer.sanitize(" +54 9 (11) 2345-6789 "))
        assertEquals("54", DialBuffer.sanitize("5+4"))
        assertEquals("*123#", DialBuffer.sanitize("*123#"))
    }

    // --- Formato ---

    @Test fun `formato mientras se escribe en Argentina`() {
        val f = DialFormat("AR")
        assertEquals("011 2345-6789", f.format("01123456789"))
        assertEquals("+54 9 11 2345-6789", f.format("+5491123456789"))
        assertEquals("0351 456-7890", f.format("03514567890"))
    }

    @Test fun `formato con numeros incompletos y vacio`() {
        val f = DialFormat("AR")
        assertEquals("", f.format(""))
        assertEquals("01", f.format("01"))
        assertEquals("+", f.format("+"))
    }

    @Test fun `formato de otro pais segun el pais por defecto`() {
        assertEquals("(201) 555-0123", DialFormat("US").format("2015550123"))
    }

    @Test fun `los codigos no se formatean`() {
        assertEquals("*123#", DialFormat("AR").format("*123#"))
    }

    @Test fun `el formateador se puede reusar`() {
        val f = DialFormat("AR")
        f.format("01123456789")
        assertEquals("03", f.format("03"))
    }

    @Test fun `formato para leer un numero guardado`() {
        assertEquals("011 15-2345-6789", numbers.format("+54 9 11 2345-6789"))
    }
}
