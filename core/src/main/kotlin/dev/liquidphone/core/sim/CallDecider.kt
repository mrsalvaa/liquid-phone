// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone.core.sim

/**
 * Identifica una cuenta de llamada del sistema (componente + id de PhoneAccountHandle). Se trata
 * como opaca: según el celu, el id podría ser un identificador del chip, así que solo se compara.
 * Nunca se muestra, nunca se guarda y [toString] no lo revela (por si termina en un registro).
 */
class AccountKey(val component: String, val id: String) {
    override fun equals(other: Any?): Boolean = other is AccountKey && other.component == component && other.id == id
    override fun hashCode(): Int = 31 * component.hashCode() + id.hashCode()
    override fun toString(): String = "AccountKey(oculta)"
}

enum class SimStatus {
    IN_SERVICE,
    /** Fuera de servicio, solo emergencias o radio apagada. */
    NO_SERVICE,
    /** El sistema todavía no informó nada: no se asume que falte servicio. */
    UNKNOWN,
}

/**
 * Un chip activo, solo con lo que ya muestra el sistema. [slotIndex] es
 * SubscriptionInfo.getSimSlotIndex(): 0 es la ranura 1 ("SIM 1"), tanto física como eSIM.
 */
data class SimChip(
    val slotIndex: Int,
    val displayName: String,
    val color: Int,
    val account: AccountKey?,
    val status: SimStatus = SimStatus.UNKNOWN,
    val embedded: Boolean = false,
) {
    /** El número de ranura que ve el usuario. */
    val slotNumber: Int get() = slotIndex + 1
}

/** Política para un toque sin chip elegido. Los valores se guardan en Ajustes (`simPolicy`). */
enum class SimPolicy(val value: Int) {
    PRIMARY(0),
    ASK(1),
    SYSTEM_DEFAULT(2),
    LAST_USED(3);

    companion object {
        /** Lo desconocido vuelve al valor por defecto: el chip principal. */
        fun fromValue(value: Int): SimPolicy = entries.firstOrNull { it.value == value } ?: PRIMARY
    }
}

/** Qué pidió el usuario. */
sealed interface CallTarget {
    /** Un toque en recientes o favoritos: decide la política. */
    data object UsePolicy : CallTarget

    /** Uno de los dos botones de llamar: pasa por encima de la política. */
    data class Slot(val slotIndex: Int) : CallTarget

    /** Devolver la llamada con el chip original de una entrada; null si la entrada no lo dice. */
    data class Original(val account: AccountKey?) : CallTarget
}

/** Lo que se sabe del celu en el momento de llamar. */
data class CallContext(
    val chips: List<SimChip>,
    /** false si falta READ_PHONE_STATE: no se sabe qué chips hay. */
    val chipsKnown: Boolean = true,
    val airplaneMode: Boolean = false,
    /** true si hay CALL_PHONE. */
    val canCall: Boolean = true,
    val policy: SimPolicy = SimPolicy.PRIMARY,
    val systemDefault: AccountKey? = null,
    /** Chip de la última llamada saliente a este número (ver CallLogTools.lastOutgoing). */
    val lastOutgoing: AccountKey? = null,
)

enum class DialerReason { EMERGENCY, CODE, NO_CALL_PERMISSION, NO_CHIPS }

enum class AskReason {
    /** La política es preguntar siempre. */
    ALWAYS,

    /** Hay un solo chip y no es la SIM 1: "SIM 1 no está. ¿Llamar con SIM 2?". */
    ONLY_SECONDARY,

    /** El chip que tocaba está activo pero sin servicio o en modo avión. */
    NO_SERVICE,

    /** El chip que tocaba ya no está. */
    CHIP_GONE,

    /** El sistema informa ranuras repetidas o inválidas: no se adivina cuál es cuál. */
    AMBIGUOUS_SLOT,
}

sealed interface CallDecision {
    /** No hay nada que marcar. */
    data object NoNumber : CallDecision

    /** ACTION_DIAL, sin chip: el marcador del sistema se encarga. */
    data class OpenSystemDialer(val number: String, val reason: DialerReason) : CallDecision

    /** Llamar sin elegir cuenta: decide el sistema. */
    data class SystemChooses(val number: String) : CallDecision

    data class Place(val number: String, val chip: SimChip) : CallDecision

    /**
     * Preguntar. [options] son los chips que se pueden elegir; [suggested] es el que tocaba, si
     * sigue ahí (se ofrece igual por si hay llamadas por Wi-Fi).
     */
    data class Ask(val number: String, val options: List<SimChip>, val reason: AskReason, val suggested: SimChip? = null) : CallDecision
}

/**
 * Decide cómo se hace una llamada. La regla central: **nunca hay un cambio silencioso de chip**.
 * [CallDecision.Place] solo sale con el chip exacto que corresponde al pedido y a la política; si
 * ese chip no se puede usar, se pregunta. Las emergencias van primero y no dependen de nada más.
 */
object CallDecider {
    fun decide(
        rawNumber: String,
        target: CallTarget,
        context: CallContext,
        isEmergency: (String) -> Boolean,
    ): CallDecision {
        val number = rawNumber.trim()
        val digits = number.filter(Char::isDigit)
        val isCode = number.any { it == '*' || it == '#' }
        if (digits.isEmpty() && !isCode) return CallDecision.NoNumber

        // Si no se puede saber, se trata como emergencia: abrir el marcador del sistema no marca.
        val emergency = try {
            isEmergency(number) || isEmergency(digits)
        } catch (e: RuntimeException) {
            true
        }
        if (emergency) return CallDecision.OpenSystemDialer(number, DialerReason.EMERGENCY)
        if (isCode) return CallDecision.OpenSystemDialer(number, DialerReason.CODE)
        if (!context.canCall) return CallDecision.OpenSystemDialer(number, DialerReason.NO_CALL_PERMISSION)
        if (!context.chipsKnown) return CallDecision.SystemChooses(number)

        val chips = context.chips
        if (chips.none { it.account != null }) return CallDecision.OpenSystemDialer(number, DialerReason.NO_CHIPS)

        fun ask(reason: AskReason, suggested: SimChip? = null) =
            CallDecision.Ask(number, chips.filter { it.account != null }, reason, suggested?.takeIf { it.account != null })

        if (hasAmbiguousSlots(chips)) return ask(AskReason.AMBIGUOUS_SLOT)
        chips.singleOrNull()?.let { if (it.slotIndex != 0) return ask(AskReason.ONLY_SECONDARY, it) }

        val primary = chips.firstOrNull { it.slotIndex == 0 }
        val wanted: SimChip = when (target) {
            is CallTarget.Slot -> chips.firstOrNull { it.slotIndex == target.slotIndex }
            is CallTarget.Original -> target.account?.let { key -> chips.firstOrNull { it.account == key } }
            CallTarget.UsePolicy -> when (context.policy) {
                SimPolicy.PRIMARY -> primary
                SimPolicy.ASK -> if (chips.size > 1) return ask(AskReason.ALWAYS) else primary
                SimPolicy.SYSTEM_DEFAULT -> {
                    val default = context.systemDefault ?: return CallDecision.SystemChooses(number)
                    // Una cuenta que no es de un chip (p. ej. SIP): la eligió el usuario en el sistema.
                    chips.firstOrNull { it.account == default } ?: return CallDecision.SystemChooses(number)
                }
                SimPolicy.LAST_USED -> {
                    val last = context.lastOutgoing
                    if (last == null) primary else chips.firstOrNull { it.account == last }
                }
            }
        } ?: return ask(AskReason.CHIP_GONE)

        return if (usable(wanted, context)) CallDecision.Place(number, wanted) else ask(AskReason.NO_SERVICE, wanted)
    }

    fun usable(chip: SimChip, context: CallContext): Boolean =
        chip.account != null && chip.status != SimStatus.NO_SERVICE && !context.airplaneMode

    /** Ranuras inválidas o repetidas. La documentación avisa que el índice puede no ser único. */
    fun hasAmbiguousSlots(chips: List<SimChip>): Boolean =
        chips.any { it.slotIndex < 0 } || chips.map { it.slotIndex }.distinct().size != chips.size
}

/** País para los números sin prefijo, a partir de los países de los chips. */
sealed interface SimCountry {
    data object Unknown : SimCountry
    data class One(val iso: String) : SimCountry

    /** Los chips son de países distintos: no se adivina, se le pregunta al usuario. */
    data class Conflict(val isos: List<String>) : SimCountry

    companion object {
        fun of(isos: List<String>): SimCountry {
            val known = isos.map { it.trim().uppercase() }.filter { it.length == 2 }.distinct().sorted()
            return when (known.size) {
                0 -> Unknown
                1 -> One(known[0])
                else -> Conflict(known)
            }
        }
    }
}
