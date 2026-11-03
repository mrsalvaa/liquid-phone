// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone.core.calls

import dev.liquidphone.core.sim.AccountKey
import dev.liquidphone.core.sim.DialerReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InCallRulesTest {
    private fun call(id: Int, phase: CallPhase, canHold: Boolean = true) = LiveCall(id, phase, canHold = canHold)

    @Test fun `una entrante pasa adelante de la que esta en curso`() {
        val calls = listOf(call(1, CallPhase.ACTIVE), call(2, CallPhase.RINGING))
        assertEquals(2, InCallRules.primary(calls)?.id)
        assertEquals(1, InCallRules.secondary(calls)?.id)
        assertEquals(InCallControls(answer = true), InCallRules.controls(calls, true))
    }

    @Test fun `sin llamadas no hay controles`() {
        assertNull(InCallRules.primary(emptyList()))
        assertEquals(InCallControls(), InCallRules.controls(emptyList(), true))
    }

    @Test fun `las que forman parte de una conferencia no se muestran aparte`() {
        val calls = listOf(LiveCall(1, CallPhase.ACTIVE, isConference = true), LiveCall(2, CallPhase.ACTIVE, parentId = 1), LiveCall(3, CallPhase.ACTIVE, parentId = 1))
        assertEquals(listOf(1), InCallRules.visible(calls).map { it.id })
        assertNull(InCallRules.secondary(calls))
    }

    @Test fun `una terminada no cuenta`() {
        assertEquals(listOf(2), InCallRules.visible(listOf(call(1, CallPhase.DISCONNECTED), call(2, CallPhase.ACTIVE))).map { it.id })
    }

    @Test fun `una llamada en curso ofrece todo menos cambiar y combinar`() {
        val c = InCallRules.controls(listOf(call(1, CallPhase.ACTIVE)), canAddCall = true)
        assertEquals(InCallControls(mute = true, route = true, hold = true, dtmf = true, add = true, hangup = true), c)
    }

    @Test fun `si la red no deja retener, no se ofrece`() {
        assertFalse(InCallRules.controls(listOf(call(1, CallPhase.ACTIVE, canHold = false)), true).hold)
    }

    @Test fun `una retenida se puede recuperar aunque no diga que se puede retener`() {
        assertTrue(InCallRules.controls(listOf(call(1, CallPhase.HOLDING, canHold = false)), true).hold)
    }

    @Test fun `retenida no manda tonos`() {
        assertFalse(InCallRules.controls(listOf(call(1, CallPhase.HOLDING)), true).dtmf)
    }

    @Test fun `marcando se puede silenciar, cambiar el audio y colgar, nada mas`() {
        val c = InCallRules.controls(listOf(call(1, CallPhase.DIALING)), true)
        assertEquals(InCallControls(mute = true, route = true, hangup = true), c)
    }

    @Test fun `con dos llamadas se cambia, no se retiene ni se agrega`() {
        val calls = listOf(call(1, CallPhase.ACTIVE), call(2, CallPhase.HOLDING))
        val c = InCallRules.controls(calls, true)
        assertTrue(c.swap)
        assertFalse(c.hold)
        assertFalse(c.add)
    }

    @Test fun `combinar solo si Telecom dice que se puede`() {
        val without = listOf(call(1, CallPhase.ACTIVE), call(2, CallPhase.HOLDING))
        assertFalse(InCallRules.controls(without, true).merge)
        val with = listOf(LiveCall(1, CallPhase.ACTIVE, canMergeWithOther = true), call(2, CallPhase.HOLDING))
        assertTrue(InCallRules.controls(with, true).merge)
    }

    @Test fun `una emergencia solo silencia, cambia el audio, manda tonos y cuelga`() {
        val c = InCallRules.controls(listOf(LiveCall(1, CallPhase.ACTIVE, canHold = true, canMergeWithOther = true, emergency = true)), true)
        assertEquals(InCallControls(mute = true, route = true, dtmf = true, hangup = true), c)
    }

    @Test fun `sin chip elegido se elige o se cancela`() {
        assertEquals(InCallControls(selectAccount = true, hangup = true), InCallRules.controls(listOf(call(1, CallPhase.SELECT_ACCOUNT)), true))
    }

    @Test fun `si Telecom no deja agregar, no se ofrece`() {
        assertFalse(InCallRules.controls(listOf(call(1, CallPhase.ACTIVE)), canAddCall = false).add)
    }

    @Test fun `el sensor solo con auricular y sin que suene`() {
        val active = listOf(call(1, CallPhase.ACTIVE))
        assertTrue(InCallRules.proximity(active, AudioRoute.EARPIECE))
        assertTrue(InCallRules.proximity(listOf(call(1, CallPhase.DIALING)), AudioRoute.EARPIECE))
        for (route in listOf(AudioRoute.SPEAKER, AudioRoute.BLUETOOTH, AudioRoute.WIRED)) assertFalse(InCallRules.proximity(active, route), "$route")
        assertFalse(InCallRules.proximity(listOf(call(1, CallPhase.RINGING)), AudioRoute.EARPIECE))
        assertFalse(InCallRules.proximity(active + call(2, CallPhase.RINGING), AudioRoute.EARPIECE))
        assertFalse(InCallRules.proximity(emptyList(), AudioRoute.EARPIECE))
    }

    @Test fun `audio alterna sin Bluetooth y pregunta con Bluetooth`() {
        val basic = setOf(AudioRoute.EARPIECE, AudioRoute.SPEAKER)
        assertFalse(InCallRules.routeNeedsPicker(basic))
        assertEquals(AudioRoute.SPEAKER, InCallRules.toggledRoute(AudioRoute.EARPIECE, basic))
        assertEquals(AudioRoute.EARPIECE, InCallRules.toggledRoute(AudioRoute.SPEAKER, basic))
        assertEquals(AudioRoute.WIRED, InCallRules.toggledRoute(AudioRoute.SPEAKER, setOf(AudioRoute.WIRED, AudioRoute.SPEAKER)))
        assertTrue(InCallRules.routeNeedsPicker(basic + AudioRoute.BLUETOOTH))
    }

    @Test fun `tonos solo con teclas validas`() {
        for (k in "0123456789*#") assertTrue(InCallRules.isDtmf(k), "$k")
        for (k in "aA+ -") assertFalse(InCallRules.isDtmf(k), "$k")
    }

    // --- Emergencias y marcador. Los números de emergencia de estas pruebas son inventados. ---

    private val fakeEmergency = "55501"

    @Test fun `siendo predeterminada, la emergencia va por Telecom y sin chip`() {
        assertEquals(SystemRoute.PLACE_WITHOUT_ACCOUNT, SystemRoutes.of(DialerReason.EMERGENCY, fakeEmergency, isDefaultDialer = true))
    }

    @Test fun `sin ser predeterminada, la emergencia sigue yendo al marcador`() {
        assertEquals(SystemRoute.SYSTEM_DIALER, SystemRoutes.of(DialerReason.EMERGENCY, fakeEmergency, isDefaultDialer = false))
    }

    @Test fun `los codigos solo los resuelve la app si es predeterminada`() {
        assertEquals(SystemRoute.SYSTEM_DIALER, SystemRoutes.of(DialerReason.CODE, "*123#", false))
        assertEquals(SystemRoute.MMI, SystemRoutes.of(DialerReason.CODE, "*123#", true))
        assertEquals(SystemRoute.SPECIAL_CODE, SystemRoutes.of(DialerReason.CODE, "*#*#4636#*#*", true))
        assertEquals("4636", SystemRoutes.specialCode("*#*#4636#*#*"))
        assertNull(SystemRoutes.specialCode("*123#"))
    }

    @Test fun `sin permiso o sin chips, el marcador`() {
        for (default in listOf(true, false)) for (reason in listOf(DialerReason.NO_CALL_PERMISSION, DialerReason.NO_CHIPS)) {
            assertEquals(SystemRoute.SYSTEM_DIALER, SystemRoutes.of(reason, "+1 202 555 0143", default))
        }
    }

    // --- Perdidas ---

    private val key = AccountKey("telefonia/falsa", "cuenta-a")
    private fun row(number: String, type: CallType = CallType.MISSED) = CallRow(1, number, type, 0, account = key)

    @Test fun `resumen de perdidas`() {
        assertNull(MissedSummary.of(emptyList()))
        assertNull(MissedSummary.of(listOf(row("+1 202 555 0143", CallType.INCOMING))))
        val one = MissedSummary.of(listOf(row("+1 202 555 0143"), row("+1 202 555 0143")))!!
        assertEquals(2, one.count)
        assertTrue(one.sameNumber)
        assertEquals(key, one.latest.account)
        assertFalse(MissedSummary.of(listOf(row("+1 202 555 0143"), row("+1 202 555 0144")))!!.sameNumber)
    }
}
