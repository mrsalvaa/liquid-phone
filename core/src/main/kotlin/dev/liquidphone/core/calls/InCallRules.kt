// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone.core.calls

import dev.liquidphone.core.sim.AccountKey
import dev.liquidphone.core.sim.DialerReason

enum class CallPhase { RINGING, SELECT_ACCOUNT, DIALING, CONNECTING, ACTIVE, HOLDING, DISCONNECTING, DISCONNECTED }

/** Una llamada en curso, sin nada de Android, con lo que dice Telecom que se puede hacer con ella. */
data class LiveCall(
    val id: Int,
    val phase: CallPhase,
    /** La conferencia de la que forma parte; esas no se muestran como llamadas aparte. */
    val parentId: Int? = null,
    val isConference: Boolean = false,
    val canHold: Boolean = false,
    /** Hay otra llamada con la que se puede combinar. */
    val canMergeWithOther: Boolean = false,
    val canMergeConference: Boolean = false,
    val canSwapConference: Boolean = false,
    val account: AccountKey? = null,
    val emergency: Boolean = false,
)

enum class AudioRoute { EARPIECE, SPEAKER, BLUETOOTH, WIRED }

/** Lo que muestra la pantalla de llamada. Lo que no se puede usar, no se ofrece. */
data class InCallControls(
    val answer: Boolean = false,
    val selectAccount: Boolean = false,
    val mute: Boolean = false,
    val route: Boolean = false,
    val hold: Boolean = false,
    val dtmf: Boolean = false,
    val add: Boolean = false,
    val swap: Boolean = false,
    val merge: Boolean = false,
    val hangup: Boolean = false,
)

object InCallRules {
    /** Hasta dos llamadas a la vez, como una línea común: una en curso y una retenida. */
    private const val MAX_CALLS = 2

    private val priority = listOf(
        CallPhase.RINGING, CallPhase.SELECT_ACCOUNT, CallPhase.ACTIVE, CallPhase.DIALING,
        CallPhase.CONNECTING, CallPhase.HOLDING, CallPhase.DISCONNECTING, CallPhase.DISCONNECTED,
    )

    fun visible(calls: List<LiveCall>): List<LiveCall> =
        calls.filter { it.parentId == null && it.phase != CallPhase.DISCONNECTED }

    /** La llamada de la que habla la pantalla: una entrante siempre pasa adelante. */
    fun primary(calls: List<LiveCall>): LiveCall? =
        visible(calls).minByOrNull { priority.indexOf(it.phase) }

    fun secondary(calls: List<LiveCall>): LiveCall? {
        val main = primary(calls) ?: return null
        return visible(calls).firstOrNull { it.id != main.id }
    }

    fun controls(calls: List<LiveCall>, canAddCall: Boolean): InCallControls {
        val shown = visible(calls)
        val main = primary(calls) ?: return InCallControls()
        return when (main.phase) {
            CallPhase.RINGING -> InCallControls(answer = true)
            // Colgar es cancelar: sin chip elegido todavía no salió nada.
            CallPhase.SELECT_ACCOUNT -> InCallControls(selectAccount = true, hangup = true)
            CallPhase.DISCONNECTING, CallPhase.DISCONNECTED -> InCallControls()
            CallPhase.DIALING, CallPhase.CONNECTING -> InCallControls(mute = true, route = true, hangup = true)
            CallPhase.ACTIVE, CallPhase.HOLDING -> {
                val two = shown.size >= MAX_CALLS
                val otherHeld = two && shown.any { it.phase == CallPhase.ACTIVE } && shown.any { it.phase == CallPhase.HOLDING }
                InCallControls(
                    mute = true,
                    route = true,
                    // Una emergencia no se retiene, no se combina ni se le suma otra llamada.
                    hold = !two && !main.emergency && (main.canHold || main.phase == CallPhase.HOLDING),
                    dtmf = main.phase == CallPhase.ACTIVE,
                    add = !two && canAddCall && !main.emergency,
                    swap = !main.emergency && (otherHeld || main.canSwapConference),
                    merge = !main.emergency && (main.canMergeWithOther || main.canMergeConference),
                    hangup = true,
                )
            }
        }
    }

    /** El sensor de proximidad apaga la pantalla solo con el celu en la oreja, y nunca mientras suena. */
    fun proximity(calls: List<LiveCall>, route: AudioRoute): Boolean {
        val shown = visible(calls)
        if (route != AudioRoute.EARPIECE || shown.any { it.phase == CallPhase.RINGING || it.phase == CallPhase.SELECT_ACCOUNT }) return false
        return shown.any { it.phase in listOf(CallPhase.ACTIVE, CallPhase.DIALING, CallPhase.CONNECTING, CallPhase.HOLDING) }
    }

    /** Solo altavoz y auricular: el botón alterna. Con Bluetooth hay que elegir. */
    fun routeNeedsPicker(available: Set<AudioRoute>): Boolean = AudioRoute.BLUETOOTH in available

    /** A dónde va al tocar el botón de audio sin Bluetooth. */
    fun toggledRoute(current: AudioRoute, available: Set<AudioRoute>): AudioRoute = when {
        current != AudioRoute.SPEAKER -> AudioRoute.SPEAKER
        AudioRoute.WIRED in available -> AudioRoute.WIRED
        else -> AudioRoute.EARPIECE
    }

    /** Solo dígitos, asterisco y numeral: un tono por tecla. */
    fun isDtmf(key: Char): Boolean = key in '0'..'9' || key == '*' || key == '#'
}

/** Por dónde va lo que no marca la app con un chip. Ver DESIGN.md, "Emergencias y marcador". */
enum class SystemRoute {
    /** Telecom, sin elegir chip: el sistema usa el que pueda. */
    PLACE_WITHOUT_ACCOUNT,

    /** ACTION_DIAL al marcador predeterminado o, si es esta app, al del sistema. */
    SYSTEM_DIALER,
    MMI,
    SPECIAL_CODE,
}

object SystemRoutes {
    private val secretCode = Regex("""\*#\*#([0-9A-Za-z]+)#\*#\*""")

    fun of(reason: DialerReason, number: String, isDefaultDialer: Boolean): SystemRoute = when {
        !isDefaultDialer -> SystemRoute.SYSTEM_DIALER
        reason == DialerReason.EMERGENCY -> SystemRoute.PLACE_WITHOUT_ACCOUNT
        reason == DialerReason.CODE -> if (secretCode.matches(number.trim())) SystemRoute.SPECIAL_CODE else SystemRoute.MMI
        else -> SystemRoute.SYSTEM_DIALER
    }

    /** El contenido de un código secreto (`*#*#N#*#*`), o null si no lo es. */
    fun specialCode(number: String): String? = secretCode.matchEntire(number.trim())?.groupValues?.get(1)
}

/** La notificación de perdidas: cuántas, la última y si todas son del mismo número. */
data class MissedSummary(val count: Int, val latest: CallRow, val sameNumber: Boolean) {
    companion object {
        /** [newest] va de la más nueva a la más vieja, como en el registro. */
        fun of(newest: List<CallRow>): MissedSummary? {
            val missed = newest.filter { it.type == CallType.MISSED }
            val latest = missed.firstOrNull() ?: return null
            return MissedSummary(missed.size, latest, missed.all { it.number == latest.number })
        }
    }
}
