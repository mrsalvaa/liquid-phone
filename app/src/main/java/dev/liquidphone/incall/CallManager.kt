// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone.incall

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.widget.Toast
import dev.liquidphone.DevTools
import dev.liquidphone.R
import dev.liquidphone.core.calls.AudioRoute
import dev.liquidphone.core.calls.CallPhase
import dev.liquidphone.core.calls.InCallControls
import dev.liquidphone.core.calls.InCallRules
import dev.liquidphone.core.calls.LiveCall
import dev.liquidphone.core.sim.AccountKey
import dev.liquidphone.core.sim.SimChip
import dev.liquidphone.data.Repo
import dev.liquidphone.util.str
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Una llamada lista para mostrar. Nunca se registra: tiene nombre y número. */
data class CallView(
    val live: LiveCall,
    val title: String,
    /** El número, si [title] es el nombre del contacto. */
    val subtitle: String?,
    /** La foto del contacto, si tiene. */
    val photoUri: String?,
    val chip: SimChip?,
    /** La llamada dice de qué cuenta es y esa cuenta no está entre los chips activos. */
    val chipUnknown: Boolean,
    val connectedAt: Long,
)

data class InCallUi(
    val calls: List<CallView> = emptyList(),
    val controls: InCallControls = InCallControls(),
    val muted: Boolean = false,
    val route: AudioRoute = AudioRoute.EARPIECE,
    val routes: Set<AudioRoute> = setOf(AudioRoute.EARPIECE, AudioRoute.SPEAKER),
) {
    val primary: CallView? get() = InCallRules.primary(calls.map { it.live })?.let { p -> calls.first { it.live.id == p.id } }
    val secondary: CallView? get() = InCallRules.secondary(calls.map { it.live })?.let { s -> calls.first { it.live.id == s.id } }
}

/**
 * Las llamadas de Telecom, para la interfaz y las notificaciones. Lo alimenta PhoneInCallService;
 * todo corre en el hilo principal. Esta es la única clase, junto con Dialer, que actúa sobre una
 * llamada, y las decisiones de qué ofrecer están en InCallRules.
 */
object CallManager {
    private val calls = LinkedHashMap<Int, Call>()
    private var service: InCallService? = null
    private var proximity: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())

    /** La pantalla de llamada está a la vista: la notificación de la entrante no tiene que saltar encima. */
    var screenShown = false
        private set

    /** Hasta cuándo se espera que abra la pantalla antes de avisar con la notificación flotante. */
    var screenExpectedUntil = 0L
        private set

    private val _ui = MutableStateFlow(InCallUi())
    val ui: StateFlow<InCallUi> = _ui.asStateFlow()

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            if (state == Call.STATE_DISCONNECTED) endTone(call)
            publish()
        }
        override fun onDetailsChanged(call: Call, details: Call.Details) = publish()
        override fun onConferenceableCallsChanged(call: Call, conferenceable: List<Call>) = publish()
        override fun onParentChanged(call: Call, parent: Call?) = publish()
        override fun onChildrenChanged(call: Call, children: List<Call>) = publish()
    }

    private fun idOf(call: Call) = System.identityHashCode(call)

    /** Se abre la pantalla de una entrante. Si no aparece a tiempo, avisa la notificación flotante. */
    fun expectScreen() {
        screenExpectedUntil = SystemClock.uptimeMillis() + SCREEN_WAIT_MILLIS
        handler.postDelayed({ publish() }, SCREEN_WAIT_MILLIS + 50)
    }

    fun setScreenShown(shown: Boolean) {
        screenShown = shown
        publish()
    }

    fun attach(s: InCallService) {
        service = s
    }

    fun detach() {
        service = null
        calls.clear()
        publish()
    }

    fun onAdded(call: Call) {
        calls[idOf(call)] = call
        call.registerCallback(callback)
        publish()
    }

    fun onRemoved(call: Call) {
        call.unregisterCallback(callback)
        endTone(call)
        explainFailure(call)
        calls.remove(idOf(call))
        publish()
    }

    /** Llamadas que ya sonaron al cortar: el aviso llega por el estado y otra vez al quitarla. */
    private val toned = HashSet<Int>()

    /**
     * Un tono corto al terminar una llamada que llegó a conectarse, corte quien corte. No suena al
     * rechazar ni en una perdida, ni por cada parte de una conferencia.
     */
    private fun endTone(call: Call) {
        val id = idOf(call)
        if (call.details.connectTimeMillis <= 0L || call.parent != null || !toned.add(id)) return
        handler.postDelayed({ toned.remove(id) }, 10_000)
        try {
            val tone = ToneGenerator(AudioManager.STREAM_VOICE_CALL, END_TONE_VOLUME)
            tone.startTone(ToneGenerator.TONE_PROP_PROMPT, END_TONE_MILLIS)
            handler.postDelayed({ tone.release() }, END_TONE_MILLIS + 300L)
        } catch (e: RuntimeException) {
            // Sin audio disponible: se corta en silencio.
        }
    }

    /** Una saliente que no salió no puede cortarse sin decir nada: se muestra la causa de la red. */
    private fun explainFailure(call: Call) {
        val cause = call.details.disconnectCause ?: return
        if (cause.code !in listOf(DisconnectCause.ERROR, DisconnectCause.BUSY, DisconnectCause.RESTRICTED)) return
        val context = service ?: return
        val text = cause.description?.toString()?.takeIf { it.isNotBlank() }
            ?: str(if (cause.code == DisconnectCause.BUSY) R.string.call_busy else R.string.call_failed)
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        DevTools.logCall("fallo=${cause.code}")
    }

    /** Vuelve a calcular lo que se muestra: también cuando cambia la agenda o los chips. */
    fun publish() {
        val ctx = service
        val audio = ctx?.callAudioState
        val live = calls.values.map(::toLive)
        val next = InCallUi(
            calls = calls.values.zip(live).map { (call, l) -> toView(call, l) },
            controls = InCallRules.controls(live, ctx?.canAddCall() == true),
            muted = audio?.isMuted == true,
            route = audio?.let { routeOf(it.route) } ?: AudioRoute.EARPIECE,
            routes = audio?.let { routesOf(it.supportedRouteMask) } ?: setOf(AudioRoute.EARPIECE, AudioRoute.SPEAKER),
        )
        val phases = live.map { it.phase }
        if (phases != _ui.value.calls.map { it.live.phase }) DevTools.logCall("fases=$phases")
        _ui.value = next
        // Sin saber todavía la ruta de audio no se apaga la pantalla: podría ser el altavoz.
        updateProximity(ctx, audio != null && InCallRules.proximity(live, next.route))
        ctx?.let { CallNotifications.update(it, next) }
    }

    private fun phaseOf(state: Int): CallPhase = when (state) {
        Call.STATE_RINGING -> CallPhase.RINGING
        Call.STATE_SELECT_PHONE_ACCOUNT -> CallPhase.SELECT_ACCOUNT
        Call.STATE_DIALING, Call.STATE_PULLING_CALL -> CallPhase.DIALING
        Call.STATE_NEW, Call.STATE_CONNECTING, Call.STATE_AUDIO_PROCESSING, Call.STATE_SIMULATED_RINGING -> CallPhase.CONNECTING
        Call.STATE_ACTIVE -> CallPhase.ACTIVE
        Call.STATE_HOLDING -> CallPhase.HOLDING
        Call.STATE_DISCONNECTING -> CallPhase.DISCONNECTING
        else -> CallPhase.DISCONNECTED
    }

    private fun numberOf(call: Call): String = call.details.handle?.schemeSpecificPart.orEmpty()

    private fun toLive(call: Call): LiveCall {
        val d = call.details
        val handle = d.accountHandle
        val number = numberOf(call)
        return LiveCall(
            id = idOf(call),
            phase = phaseOf(d.state),
            parentId = call.parent?.let(::idOf),
            isConference = d.hasProperty(Call.Details.PROPERTY_CONFERENCE),
            canHold = d.can(Call.Details.CAPABILITY_HOLD) && d.can(Call.Details.CAPABILITY_SUPPORT_HOLD),
            canMergeWithOther = call.conferenceableCalls.isNotEmpty(),
            canMergeConference = d.can(Call.Details.CAPABILITY_MERGE_CONFERENCE),
            canSwapConference = d.can(Call.Details.CAPABILITY_SWAP_CONFERENCE),
            account = handle?.let { AccountKey(it.componentName.flattenToString(), it.id) },
            emergency = d.hasProperty(Call.Details.PROPERTY_EMERGENCY_CALLBACK_MODE) || isEmergency(number),
        )
    }

    private fun isEmergency(number: String): Boolean = number.isNotEmpty() && try {
        Repo.sims.isEmergency(number)
    } catch (e: RuntimeException) {
        false
    }

    private fun toView(call: Call, live: LiveCall): CallView {
        val d = call.details
        val raw = numberOf(call)
        val visible = d.handlePresentation == TelecomManager.PRESENTATION_ALLOWED && raw.isNotEmpty()
        val shown = raw.takeIf { visible }
        val contact = shown?.let { Repo.contactFor(it) }
        val number = shown?.let { Repo.numbers.value.format(it) }
        val chips = Repo.sims.state.value.chips
        val chip = live.account?.let { key -> chips.firstOrNull { it.account == key } }
        return CallView(
            live = live,
            title = when {
                live.isConference -> str(R.string.call_conference)
                contact != null -> contact.displayName
                number != null -> number
                else -> str(R.string.number_hidden)
            },
            subtitle = number.takeIf { contact != null },
            photoUri = contact?.photoUri,
            chip = chip,
            chipUnknown = live.account != null && chip == null && Repo.sims.state.value.known,
            connectedAt = d.connectTimeMillis,
        )
    }

    private fun routeOf(route: Int): AudioRoute = when (route) {
        CallAudioState.ROUTE_SPEAKER -> AudioRoute.SPEAKER
        CallAudioState.ROUTE_BLUETOOTH -> AudioRoute.BLUETOOTH
        CallAudioState.ROUTE_WIRED_HEADSET -> AudioRoute.WIRED
        else -> AudioRoute.EARPIECE
    }

    private fun routesOf(mask: Int): Set<AudioRoute> = buildSet {
        if (mask and CallAudioState.ROUTE_EARPIECE != 0) add(AudioRoute.EARPIECE)
        if (mask and CallAudioState.ROUTE_SPEAKER != 0) add(AudioRoute.SPEAKER)
        if (mask and CallAudioState.ROUTE_BLUETOOTH != 0) add(AudioRoute.BLUETOOTH)
        if (mask and CallAudioState.ROUTE_WIRED_HEADSET != 0) add(AudioRoute.WIRED)
    }

    @SuppressLint("WakelockTimeout") // Dura lo que la llamada; se suelta al terminar o al dejar el auricular.
    private fun updateProximity(context: Context?, wanted: Boolean) {
        if (wanted && context != null) {
            val lock = proximity ?: context.getSystemService(PowerManager::class.java)
                ?.takeIf { it.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) }
                ?.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "liquidphone:proximity")
                ?.also { it.setReferenceCounted(false); proximity = it }
            if (lock != null && !lock.isHeld) {
                lock.acquire()
                DevTools.logCall("proximidad=on")
            }
        } else if (proximity?.isHeld == true) {
            // Si sigue tapado, la pantalla vuelve recién al sacarlo de la oreja.
            proximity?.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
            DevTools.logCall("proximidad=off")
        }
    }

    private fun call(id: Int) = calls[id]

    fun answer(id: Int) {
        call(id)?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    /** Rechazar sin mensaje de texto: la app no manda SMS. */
    fun reject(id: Int) {
        call(id)?.reject(false, null)
    }

    fun hangup(id: Int) {
        call(id)?.disconnect()
    }

    fun toggleHold(id: Int) {
        val c = call(id) ?: return
        if (c.details.state == Call.STATE_HOLDING) c.unhold() else c.hold()
    }

    fun swap() {
        val live = calls.values.firstOrNull { it.details.state == Call.STATE_ACTIVE && it.parent == null }
        if (live != null && live.details.can(Call.Details.CAPABILITY_SWAP_CONFERENCE)) {
            live.swapConference()
            return
        }
        // Telecom retiene la activa al recuperar la otra.
        calls.values.firstOrNull { it.details.state == Call.STATE_HOLDING && it.parent == null }?.unhold()
    }

    fun merge(id: Int) {
        val c = call(id) ?: return
        val other = c.conferenceableCalls.firstOrNull()
        if (other != null) c.conference(other) else if (c.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE)) c.mergeConference()
    }

    fun setMuted(muted: Boolean) {
        service?.setMuted(muted)
    }

    fun setRoute(route: AudioRoute) {
        service?.setAudioRoute(
            when (route) {
                AudioRoute.SPEAKER -> CallAudioState.ROUTE_SPEAKER
                AudioRoute.BLUETOOTH -> CallAudioState.ROUTE_BLUETOOTH
                AudioRoute.WIRED, AudioRoute.EARPIECE -> CallAudioState.ROUTE_WIRED_OR_EARPIECE
            },
        )
    }

    /** Un tono corto por tecla. Solo se registra que hubo un tono, nunca cuál: puede ser una clave. */
    fun dtmf(id: Int, key: Char) {
        if (!InCallRules.isDtmf(key)) return
        val c = call(id) ?: return
        c.playDtmfTone(key)
        handler.postDelayed({ c.stopDtmfTone() }, DTMF_MILLIS)
        DevTools.logCall("dtmf")
    }

    /** Elige el chip de una llamada que el sistema dejó esperando. */
    fun selectAccount(id: Int, chip: SimChip) {
        val handle = Repo.sims.handleOf(chip.account) ?: return
        call(id)?.phoneAccountSelected(handle, false)
    }

    private const val DTMF_MILLIS = 150L
    private const val END_TONE_MILLIS = 200
    private const val END_TONE_VOLUME = 80
    private const val SCREEN_WAIT_MILLIS = 1500L
}
