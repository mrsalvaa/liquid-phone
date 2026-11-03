// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone.core.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Todos los chips son falsos; el "número de emergencia" de estas pruebas es inventado. */
class CallDeciderTest {
    private val keyA = AccountKey("telefonia/falsa", "cuenta-a")
    private val keyB = AccountKey("telefonia/falsa", "cuenta-b")
    private val sim1 = SimChip(0, "Chip A", 1, keyA, SimStatus.IN_SERVICE)
    private val sim2 = SimChip(1, "Chip B", 2, keyB, SimStatus.IN_SERVICE)
    private val number = "011 1345-6789"
    private val fakeEmergency = "55501"
    private val notEmergency: (String) -> Boolean = { false }
    private val emergency: (String) -> Boolean = { it == fakeEmergency }

    private fun decide(
        target: CallTarget = CallTarget.UsePolicy,
        chips: List<SimChip> = listOf(sim1, sim2),
        policy: SimPolicy = SimPolicy.PRIMARY,
        n: String = number,
        airplane: Boolean = false,
        last: AccountKey? = null,
        default: AccountKey? = null,
        primarySlot: Int = 0,
    ) = CallDecider.decide(
        n,
        target,
        CallContext(chips, policy = policy, primarySlot = primarySlot, airplaneMode = airplane, lastOutgoing = last, systemDefault = default),
        emergency,
    )

    // --- Política del chip principal ---

    @Test fun `por defecto llama con la SIM 1`() {
        assertEquals(CallDecision.Place(number, sim1), decide())
    }

    @Test fun `SIM 1 sin servicio pregunta y no cae a la SIM 2`() {
        val down = sim1.copy(status = SimStatus.NO_SERVICE)
        val d = assertIs<CallDecision.Ask>(decide(chips = listOf(down, sim2)))
        assertEquals(AskReason.PRIMARY_UNAVAILABLE, d.reason)
        assertEquals(down, d.suggested)
        assertEquals(sim2, d.other)
        assertEquals(listOf(down, sim2), d.options)
    }

    @Test fun `modo avion pregunta aunque el chip diga que tiene servicio`() {
        assertEquals(AskReason.PRIMARY_UNAVAILABLE, assertIs<CallDecision.Ask>(decide(airplane = true)).reason)
    }

    @Test fun `estado desconocido no se toma como falta de servicio`() {
        val unknown = sim1.copy(status = SimStatus.UNKNOWN)
        assertEquals(CallDecision.Place(number, unknown), decide(chips = listOf(unknown, sim2)))
    }

    @Test fun `un solo chip en la ranura 1 llama sin preguntar con cualquier politica`() {
        for (policy in SimPolicy.entries) {
            val d = decide(chips = listOf(sim1), policy = policy, default = keyA)
            assertEquals(CallDecision.Place(number, sim1), d, "$policy")
        }
    }

    @Test fun `un solo chip en la ranura 2 con la SIM 1 como principal confirma en cada llamada`() {
        val targets = listOf(CallTarget.UsePolicy, CallTarget.Slot(1), CallTarget.Original(keyB))
        for (target in targets) for (policy in SimPolicy.entries) {
            val d = assertIs<CallDecision.Ask>(decide(target, listOf(sim2), policy, last = keyB, default = keyB), "$target $policy")
            assertEquals(AskReason.PRIMARY_UNAVAILABLE, d.reason)
            assertEquals(listOf(sim2), d.options)
            assertEquals(sim2, d.other)
            assertNull(d.suggested)
        }
    }

    @Test fun `dos chips sin ninguno en la ranura principal no elige ni nombra a ninguno`() {
        val a = sim1.copy(slotIndex = 1)
        val b = sim2.copy(slotIndex = 2)
        val d = assertIs<CallDecision.Ask>(decide(chips = listOf(a, b)))
        assertEquals(AskReason.PRIMARY_UNAVAILABLE, d.reason)
        assertNull(d.other)
        assertEquals(listOf(a, b), d.options)
    }

    @Test fun `ranuras repetidas o invalidas no se adivinan`() {
        val repeated = listOf(sim1, sim2.copy(slotIndex = 0))
        assertEquals(AskReason.AMBIGUOUS_SLOT, assertIs<CallDecision.Ask>(decide(chips = repeated)).reason)
        val invalid = listOf(sim1.copy(slotIndex = -1))
        assertEquals(AskReason.AMBIGUOUS_SLOT, assertIs<CallDecision.Ask>(decide(chips = invalid)).reason)
    }

    @Test fun `eSIM en la ranura 1 es la SIM 1`() {
        val esim = sim1.copy(embedded = true)
        assertEquals(CallDecision.Place(number, esim), decide(chips = listOf(sim2, esim)))
    }

    @Test fun `chip activo sin cuenta de llamada no se ofrece ni se usa`() {
        val noAccount = sim1.copy(account = null)
        val d = assertIs<CallDecision.Ask>(decide(chips = listOf(noAccount, sim2)))
        assertEquals(AskReason.PRIMARY_UNAVAILABLE, d.reason)
        assertEquals(listOf(sim2), d.options)
        assertEquals(sim2, d.other)
        assertNull(d.suggested)
    }

    // --- El chip principal se elige: SIM 1 o SIM 2, cruzado con disponibilidad y políticas ---

    /** (ranura principal, chip principal, el otro chip). */
    private val primaries = listOf(Triple(0, sim1, sim2), Triple(1, sim2, sim1))

    @Test fun `con los dos disponibles, cada politica respeta el principal elegido`() {
        for ((slot, primary, other) in primaries) {
            val chips = listOf(sim1, sim2)
            assertEquals(CallDecision.Place(number, primary), decide(chips = chips, primarySlot = slot))
            val asked = assertIs<CallDecision.Ask>(decide(chips = chips, policy = SimPolicy.ASK, primarySlot = slot))
            assertEquals(AskReason.ALWAYS, asked.reason)
            assertEquals(primary, asked.suggested)
            assertEquals(CallDecision.Place(number, other), decide(chips = chips, policy = SimPolicy.SYSTEM_DEFAULT, default = other.account, primarySlot = slot))
            assertEquals(CallDecision.SystemChooses(number), decide(chips = chips, policy = SimPolicy.SYSTEM_DEFAULT, primarySlot = slot))
            assertEquals(CallDecision.Place(number, primary), decide(chips = chips, policy = SimPolicy.LAST_USED, primarySlot = slot))
            assertEquals(CallDecision.Place(number, other), decide(chips = chips, policy = SimPolicy.LAST_USED, last = other.account, primarySlot = slot))
        }
    }

    @Test fun `principal sin servicio nunca cae en silencio al otro`() {
        for ((slot, primary, other) in primaries) {
            val down = primary.copy(status = SimStatus.NO_SERVICE)
            val chips = listOf(down, other).sortedBy { it.slotIndex }
            val cases = listOf(
                decide(chips = chips, primarySlot = slot),
                decide(chips = chips, policy = SimPolicy.SYSTEM_DEFAULT, default = primary.account, primarySlot = slot),
                decide(chips = chips, policy = SimPolicy.LAST_USED, primarySlot = slot),
                decide(chips = chips, policy = SimPolicy.LAST_USED, last = primary.account, primarySlot = slot),
                decide(CallTarget.Slot(slot), chips, primarySlot = slot),
                decide(CallTarget.Original(primary.account), chips, primarySlot = slot),
            )
            for (d in cases) {
                val ask = assertIs<CallDecision.Ask>(d)
                assertEquals(AskReason.PRIMARY_UNAVAILABLE, ask.reason)
                assertEquals(other, ask.other)
                assertEquals(down, ask.suggested)
                assertEquals(chips, ask.options)
            }
            assertEquals(AskReason.ALWAYS, assertIs<CallDecision.Ask>(decide(chips = chips, policy = SimPolicy.ASK, primarySlot = slot)).reason)
        }
    }

    @Test fun `con el principal caido, lo que pide el otro chip a proposito se respeta`() {
        for ((slot, primary, other) in primaries) {
            val chips = listOf(primary.copy(status = SimStatus.NO_SERVICE), other)
            assertEquals(CallDecision.Place(number, other), decide(CallTarget.Slot(other.slotIndex), chips, primarySlot = slot))
            assertEquals(CallDecision.Place(number, other), decide(CallTarget.Original(other.account), chips, primarySlot = slot))
            assertEquals(CallDecision.Place(number, other), decide(chips = chips, policy = SimPolicy.LAST_USED, last = other.account, primarySlot = slot))
            assertEquals(CallDecision.Place(number, other), decide(chips = chips, policy = SimPolicy.SYSTEM_DEFAULT, default = other.account, primarySlot = slot))
        }
    }

    @Test fun `modo avion pregunta con cualquier principal y cualquier politica`() {
        for ((slot, primary, _) in primaries) for (policy in SimPolicy.entries) {
            val d = decide(policy = policy, primarySlot = slot, airplane = true, default = primary.account, last = primary.account)
            assertIs<CallDecision.Ask>(d, "$slot $policy")
        }
    }

    @Test fun `un solo chip que es el principal llama sin preguntar con cualquier politica`() {
        for ((slot, primary, _) in primaries) for (policy in SimPolicy.entries) {
            val d = decide(chips = listOf(primary), policy = policy, primarySlot = slot, default = primary.account)
            assertEquals(CallDecision.Place(number, primary), d, "$slot $policy")
            assertEquals(CallDecision.Place(number, primary), decide(CallTarget.Slot(slot), listOf(primary), policy, primarySlot = slot))
        }
    }

    @Test fun `un solo chip que no es el principal confirma nombrandolo, con cualquier pedido y politica`() {
        for ((slot, _, other) in primaries) {
            val targets = listOf(CallTarget.UsePolicy, CallTarget.Slot(other.slotIndex), CallTarget.Slot(slot), CallTarget.Original(other.account))
            for (target in targets) for (policy in SimPolicy.entries) {
                val d = assertIs<CallDecision.Ask>(decide(target, listOf(other), policy, primarySlot = slot, last = other.account, default = other.account), "$slot $target $policy")
                assertEquals(AskReason.PRIMARY_UNAVAILABLE, d.reason)
                assertEquals(other, d.other)
                assertEquals(listOf(other), d.options)
            }
        }
    }

    @Test fun `los botones eligen ese chip sea cual sea el principal`() {
        for ((slot, primary, other) in primaries) for (policy in SimPolicy.entries) {
            assertEquals(CallDecision.Place(number, primary), decide(CallTarget.Slot(slot), policy = policy, primarySlot = slot))
            assertEquals(CallDecision.Place(number, other), decide(CallTarget.Slot(other.slotIndex), policy = policy, primarySlot = slot))
        }
    }

    @Test fun `un principal invalido en el ajuste pregunta con cualquier pedido`() {
        for (slot in listOf(-1, 2, 99)) for (policy in SimPolicy.entries) {
            for (target in listOf(CallTarget.UsePolicy, CallTarget.Slot(0), CallTarget.Original(keyA))) {
                val d = assertIs<CallDecision.Ask>(decide(target, policy = policy, primarySlot = slot), "$slot $target $policy")
                assertEquals(AskReason.AMBIGUOUS_SLOT, d.reason)
                assertEquals(listOf(sim1, sim2), d.options)
            }
        }
    }

    @Test fun `ranuras repetidas preguntan con cualquier principal`() {
        val repeated = listOf(sim1.copy(slotIndex = 1), sim2)
        for ((slot, _, _) in primaries) {
            assertEquals(AskReason.AMBIGUOUS_SLOT, assertIs<CallDecision.Ask>(decide(chips = repeated, primarySlot = slot)).reason)
        }
    }

    @Test fun `la emergencia no depende del principal`() {
        for (slot in listOf(0, 1, 99)) {
            assertEquals(CallDecision.OpenSystemDialer(fakeEmergency, DialerReason.EMERGENCY), decide(n = fakeEmergency, primarySlot = slot))
        }
    }

    // --- Las otras políticas ---

    @Test fun `preguntar siempre con dos chips muestra los dos`() {
        val d = assertIs<CallDecision.Ask>(decide(policy = SimPolicy.ASK))
        assertEquals(AskReason.ALWAYS, d.reason)
        assertEquals(listOf(sim1, sim2), d.options)
    }

    @Test fun `predeterminada del sistema usa ese chip`() {
        assertEquals(CallDecision.Place(number, sim2), decide(policy = SimPolicy.SYSTEM_DEFAULT, default = keyB))
    }

    @Test fun `sin predeterminada decide el sistema`() {
        assertEquals(CallDecision.SystemChooses(number), decide(policy = SimPolicy.SYSTEM_DEFAULT))
    }

    @Test fun `predeterminada sin servicio pregunta`() {
        val down = sim2.copy(status = SimStatus.NO_SERVICE)
        val d = assertIs<CallDecision.Ask>(decide(chips = listOf(sim1, down), policy = SimPolicy.SYSTEM_DEFAULT, default = keyB))
        assertEquals(AskReason.NO_SERVICE, d.reason)
    }

    @Test fun `historial con el otro chip usa ese chip`() {
        assertEquals(CallDecision.Place(number, sim2), decide(policy = SimPolicy.LAST_USED, last = keyB))
    }

    @Test fun `sin historial usa el chip principal`() {
        assertEquals(CallDecision.Place(number, sim1), decide(policy = SimPolicy.LAST_USED))
    }

    @Test fun `el chip del historial ya no esta y pregunta`() {
        val gone = AccountKey("telefonia/falsa", "retirado")
        assertEquals(AskReason.CHIP_GONE, assertIs<CallDecision.Ask>(decide(policy = SimPolicy.LAST_USED, last = gone)).reason)
    }

    @Test fun `el chip del historial sin servicio pregunta`() {
        val down = sim2.copy(status = SimStatus.NO_SERVICE)
        val d = assertIs<CallDecision.Ask>(decide(chips = listOf(sim1, down), policy = SimPolicy.LAST_USED, last = keyB))
        assertEquals(AskReason.NO_SERVICE, d.reason)
    }

    // --- Elección explícita ---

    @Test fun `el boton de la SIM 2 pasa por encima de la politica`() {
        for (policy in SimPolicy.entries) {
            assertEquals(CallDecision.Place(number, sim2), decide(CallTarget.Slot(1), policy = policy, last = keyA, default = keyA), "$policy")
        }
    }

    @Test fun `el boton de un chip sin servicio ofrece ese chip, el otro o cancelar`() {
        val down = sim2.copy(status = SimStatus.NO_SERVICE)
        val d = assertIs<CallDecision.Ask>(decide(CallTarget.Slot(1), listOf(sim1, down)))
        assertEquals(AskReason.NO_SERVICE, d.reason)
        assertEquals(down, d.suggested)
        assertEquals(listOf(sim1, down), d.options)
    }

    @Test fun `devolver la llamada usa el chip original`() {
        assertEquals(CallDecision.Place(number, sim2), decide(CallTarget.Original(keyB)))
    }

    @Test fun `devolver la llamada con un chip que ya no esta pregunta`() {
        val gone = AccountKey("telefonia/falsa", "retirado")
        assertEquals(AskReason.CHIP_GONE, assertIs<CallDecision.Ask>(decide(CallTarget.Original(gone))).reason)
        assertEquals(AskReason.CHIP_GONE, assertIs<CallDecision.Ask>(decide(CallTarget.Original(null))).reason)
    }

    // --- Nunca un cambio silencioso ---

    /**
     * Recorre todas las combinaciones. Si la decisión es llamar, el chip tiene que ser exactamente
     * el que corresponde al pedido y a la política, y tiene que poder usarse.
     */
    @Test fun `nunca hay un cambio silencioso de chip`() {
        val gone = AccountKey("telefonia/falsa", "retirado")
        val statuses = SimStatus.entries
        val chipSets = buildList {
            for (s1 in statuses) {
                add(listOf(sim1.copy(status = s1)))
                add(listOf(sim2.copy(status = s1)))
                for (s2 in statuses) {
                    add(listOf(sim1.copy(status = s1), sim2.copy(status = s2)))
                    add(listOf(sim2.copy(status = s2), sim1.copy(status = s1)))
                }
            }
            add(listOf(sim1.copy(account = null), sim2))
            add(listOf(sim1, sim2.copy(account = null)))
        }
        val keys = listOf(null, keyA, keyB, gone)
        val targets = listOf(CallTarget.UsePolicy, CallTarget.Slot(0), CallTarget.Slot(1)) + keys.map { CallTarget.Original(it) }
        var places = 0
        for (slot in listOf(0, 1)) for (chips in chipSets) for (target in targets) for (policy in SimPolicy.entries) for (last in keys) for (default in keys) for (airplane in listOf(false, true)) {
            val d = decide(target, chips, policy, airplane = airplane, last = last, default = default, primarySlot = slot)
            if (d !is CallDecision.Place) continue
            places++
            val primary = chips.firstOrNull { it.slotIndex == slot }
            assertTrue(chips.size > 1 || d.chip == primary, "único chip sin confirmar: principal=$slot $chips")
            val expected: SimChip? = when (target) {
                is CallTarget.Slot -> chips.firstOrNull { it.slotIndex == target.slotIndex }
                is CallTarget.Original -> chips.firstOrNull { it.account == target.account }
                CallTarget.UsePolicy -> when (policy) {
                    SimPolicy.PRIMARY -> primary
                    SimPolicy.ASK -> primary.takeIf { chips.size == 1 }
                    SimPolicy.SYSTEM_DEFAULT -> chips.firstOrNull { it.account == default }
                    SimPolicy.LAST_USED -> if (last == null) primary else chips.firstOrNull { it.account == last }
                }
            }
            assertEquals(expected, d.chip, "principal=$slot pedido=$target política=$policy chips=$chips")
            assertFalse(airplane)
            assertTrue(d.chip.status != SimStatus.NO_SERVICE && d.chip.account != null, "chip inutilizable: $chips")
        }
        assertTrue(places > 1000, "la prueba tiene que recorrer casos reales: $places")
    }

    // --- Emergencias, códigos y permisos ---

    @Test fun `emergencia va al marcador del sistema sin chip, con cualquier estado`() {
        val down = listOf(sim1.copy(status = SimStatus.NO_SERVICE))
        val cases = listOf(
            CallContext(listOf(sim1, sim2)),
            CallContext(emptyList()),
            CallContext(down, airplaneMode = true),
            CallContext(listOf(sim2), policy = SimPolicy.ASK),
            CallContext(emptyList(), chipsKnown = false, canCall = false),
        )
        val targets = listOf(CallTarget.UsePolicy, CallTarget.Slot(1), CallTarget.Original(keyB))
        for (context in cases) for (target in targets) {
            assertEquals(
                CallDecision.OpenSystemDialer(fakeEmergency, DialerReason.EMERGENCY),
                CallDecider.decide(fakeEmergency, target, context, emergency),
            )
        }
    }

    @Test fun `emergencia se reconoce tambien con separadores`() {
        assertEquals(CallDecision.OpenSystemDialer("555-01", DialerReason.EMERGENCY), decide(n = "555-01"))
    }

    @Test fun `si la comprobacion de emergencia falla se abre el marcador del sistema`() {
        val d = CallDecider.decide(number, CallTarget.UsePolicy, CallContext(listOf(sim1)), { throw IllegalStateException() })
        assertEquals(CallDecision.OpenSystemDialer(number, DialerReason.EMERGENCY), d)
    }

    @Test fun `codigos con asterisco o numeral van al marcador del sistema`() {
        assertEquals(CallDecision.OpenSystemDialer("*123#", DialerReason.CODE), decide(n = "*123#"))
        assertEquals(CallDecision.OpenSystemDialer("#", DialerReason.CODE), decide(n = "#"))
    }

    @Test fun `sin permiso de llamar cae a ACTION_DIAL`() {
        val d = CallDecider.decide(number, CallTarget.Slot(1), CallContext(listOf(sim1, sim2), canCall = false), notEmergency)
        assertEquals(CallDecision.OpenSystemDialer(number, DialerReason.NO_CALL_PERMISSION), d)
    }

    @Test fun `sin permiso para ver los chips decide el sistema`() {
        val d = CallDecider.decide(number, CallTarget.UsePolicy, CallContext(emptyList(), chipsKnown = false), notEmergency)
        assertEquals(CallDecision.SystemChooses(number), d)
    }

    @Test fun `sin chips abre el marcador del sistema`() {
        assertEquals(CallDecision.OpenSystemDialer(number, DialerReason.NO_CHIPS), decide(chips = emptyList()))
    }

    @Test fun `sin nada que marcar no hace nada`() {
        for (empty in listOf("", "   ", "+", "()-")) assertEquals(CallDecision.NoNumber, decide(n = empty))
    }

    // --- Privacidad y otros ---

    @Test fun `la clave de cuenta no se revela al imprimirla`() {
        val secret = "8954000000000000000"
        val text = SimChip(0, "Chip A", 1, AccountKey("componente", secret)).toString() + AccountKey("componente", secret)
        assertFalse(secret in text, text)
        assertFalse("componente" in text, text)
    }

    @Test fun `politica desconocida vuelve al chip principal`() {
        assertEquals(SimPolicy.PRIMARY, SimPolicy.fromValue(99))
        for (p in SimPolicy.entries) assertEquals(p, SimPolicy.fromValue(p.value))
    }

    @Test fun `dos SIM del mismo pais dan ese pais`() {
        assertEquals(SimCountry.One("AR"), SimCountry.of(listOf("ar", "AR")))
        assertEquals(SimCountry.One("AR"), SimCountry.of(listOf("ar", "")))
    }

    @Test fun `dos SIM de paises distintos no se adivinan`() {
        assertEquals(SimCountry.Conflict(listOf("AR", "UY")), SimCountry.of(listOf("uy", "ar")))
    }

    @Test fun `sin datos de pais queda desconocido`() {
        assertEquals(SimCountry.Unknown, SimCountry.of(listOf("", "x")))
    }
}
