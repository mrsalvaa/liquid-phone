// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone.core.dial

import com.google.i18n.phonenumbers.PhoneNumberUtil
import dev.liquidphone.core.text.Fold

/**
 * Teclas del teclado telefónico en español: la ñ va con la 6 y las tildes no cuentan. [Fold] ya
 * quita tildes, diéresis y eñes, así que "Núñez" y "Nunez" dan lo mismo.
 */
object T9 {
    /** Letras que muestra cada tecla. */
    val KEY_LETTERS: Map<Char, String> = mapOf(
        '2' to "ABC",
        '3' to "DEF",
        '4' to "GHI",
        '5' to "JKL",
        '6' to "MNOÑ",
        '7' to "PQRS",
        '8' to "TUV",
        '9' to "WXYZ",
    )

    private val DIGIT: Map<Char, Char> = buildMap {
        for ((digit, letters) in KEY_LETTERS) for (c in letters) if (c != 'Ñ') put(c.lowercaseChar(), digit)
    }

    /**
     * Los dígitos de un texto ya pasado por [Fold]. Los números quedan como están y lo que no tiene
     * tecla (otros alfabetos, símbolos) no se incluye.
     */
    private fun foldedToDigits(folded: String): String = buildString(folded.length) {
        for (c in folded) {
            when {
                c in '0'..'9' -> append(c)
                else -> DIGIT[c]?.let(::append)
            }
        }
    }

    fun digitsOf(text: String): String = foldedToDigits(Fold.fold(text))

    /** Para cada palabra del nombre, los dígitos desde ahí hasta el final, sin espacios. */
    fun wordStarts(name: String): List<String> {
        val words = Fold.fold(name).split(SEPARATORS).filter { it.isNotEmpty() }.map(::foldedToDigits).filter { it.isNotEmpty() }
        return words.indices.map { i -> words.drop(i).joinToString("") }
    }

    private val SEPARATORS = Regex("[\\s,.;:()\\-_/'\"]+")
}

/** Un contacto listo para buscar desde el teclado, con todo calculado una sola vez. */
class DialEntry<T>(val item: T, name: String, phones: Collection<String>, phoneForms: (String) -> List<String>) {
    internal val nameStarts: List<String> = T9.wordStarts(name)
    internal val forms: List<String> = phones.flatMap(phoneForms).distinct()
}

/** Búsqueda desde el teclado: por letras (T9) o por número. */
object DialSearch {
    /** 0 si no coincide; más alto cuanto mejor. */
    fun score(digits: String, entry: DialEntry<*>): Int {
        if (digits.isEmpty()) return 0
        val starts = entry.nameStarts
        if (starts.firstOrNull()?.startsWith(digits) == true) return 4
        if (starts.any { it.startsWith(digits) }) return 3
        if (entry.forms.any { it.startsWith(digits) }) return 2
        if (entry.forms.any { it.contains(digits) }) return 1
        return 0
    }

    /** Los que coinciden, los mejores primero; a igual puntaje queda el orden recibido (alfabético). */
    fun <T> search(digits: String, entries: List<DialEntry<T>>): List<T> {
        val needle = digits.filter(Char::isDigit)
        return entries
            .map { it to score(needle, it) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first.item }
    }
}

/** Lo que se va escribiendo en el teclado, sin cursor: siempre se escribe y se borra al final. */
object DialBuffer {
    const val MAX_LENGTH = 40

    fun type(text: String, key: Char): String =
        if (text.length >= MAX_LENGTH || (key !in '0'..'9' && key != '*' && key != '#')) text else text + key

    /** Mantener el 0: el "+" solo tiene sentido adelante, se haya escrito algo o no. */
    fun plus(text: String): String = when {
        text.startsWith("+") || text.length >= MAX_LENGTH -> text
        else -> "+$text"
    }

    fun backspace(text: String): String = text.dropLast(1)

    /** Mantener borrar. */
    fun clear(): String = ""

    /** Solo lo que acepta el teclado, para un número que viene de otra parte (p. ej. pegar). */
    fun sanitize(raw: String): String {
        val kept = raw.filter { it in '0'..'9' || it == '*' || it == '#' || it == '+' }
        val plus = kept.startsWith("+")
        return ((if (plus) "+" else "") + kept.filter { it != '+' }).take(MAX_LENGTH)
    }
}

/** Formato mientras se escribe, según el país por defecto. */
class DialFormat(region: String) {
    private val formatter = PhoneNumberUtil.getInstance().getAsYouTypeFormatter(region)

    fun format(text: String): String {
        // Los códigos con * y # se muestran tal cual: no son números.
        if (text.any { it == '*' || it == '#' }) return text
        formatter.clear()
        var out = text
        for (c in text) out = formatter.inputDigit(c)
        return out
    }
}
