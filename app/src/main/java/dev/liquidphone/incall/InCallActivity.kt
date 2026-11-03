// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone.incall

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMerge
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SwapCalls
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.liquidphone.DevTools
import dev.liquidphone.MainActivity
import dev.liquidphone.R
import dev.liquidphone.core.calls.AudioRoute
import dev.liquidphone.core.calls.CallPhase
import dev.liquidphone.core.calls.InCallRules
import dev.liquidphone.data.Repo
import dev.liquidphone.data.Stores
import dev.liquidphone.ui.glass.GlassButton
import dev.liquidphone.ui.glass.GlassCard
import dev.liquidphone.ui.glass.GlassRoot
import dev.liquidphone.ui.glass.GlassTheme
import dev.liquidphone.ui.glass.LocalGlass
import dev.liquidphone.SystemBarsFor
import dev.liquidphone.ui.glass.isDarkTheme
import dev.liquidphone.ui.glass.readableOn
import dev.liquidphone.ui.screens.ChipCallButton
import dev.liquidphone.ui.screens.KEY_NAMES
import dev.liquidphone.ui.screens.SimBadge
import dev.liquidphone.ui.screens.simName
import dev.liquidphone.util.str
import kotlinx.coroutines.delay

// Colores fijos: tienen que distinguirse igual en cualquier tema. Con texto blanco, más de 4,5:1.
private val HangupRed = Color(0xFFC62828)
private val AnswerGreen = Color(0xFF1B7F3B)

/**
 * La llamada entrante (también sobre la pantalla de bloqueo) y la llamada en curso. Solo muestra lo
 * que dice CallManager y se cierra sola cuando no hay más llamadas.
 */
class InCallActivity : ComponentActivity() {
    companion object {
        const val EXTRA_ANSWER = "answer"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        setContent {
            val settings by Stores.settings.state.collectAsStateWithLifecycle()
            val ui by CallManager.ui.collectAsStateWithLifecycle()
            DevTools.WindowGuard()
            SystemBarsFor(isDarkTheme(settings.themeMode))
            GlassTheme(settings.glassStyle) {
                GlassRoot(animated = settings.animatedBackground) {
                    InCallScreen(ui) {
                        // "Agregar" siempre lleva al teclado, sea cual sea la última pantalla abierta.
                        startActivity(Intent(this@InCallActivity, MainActivity::class.java).putExtra(MainActivity.EXTRA_DIAL, "").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
            // Puede abrirse un instante antes de que llegue la llamada: se espera antes de cerrar.
            var seen by remember { mutableStateOf(false) }
            LaunchedEffect(ui.primary == null) {
                if (ui.primary != null) {
                    seen = true
                } else {
                    if (!seen) delay(1500)
                    if (CallManager.ui.value.primary == null) finish()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    /** "Atender" de la notificación. */
    private fun handle(intent: Intent?) {
        val id = intent?.getIntExtra(EXTRA_ANSWER, 0) ?: 0
        if (id != 0) {
            CallManager.answer(id)
            intent?.removeExtra(EXTRA_ANSWER)
        }
    }
}

/**
 * Colgar y atender nunca se desplazan: están siempre a la vista, aunque el teclado de tonos o la letra
 * grande no dejen lugar para lo demás. En horizontal, dos columnas.
 */
@Composable
private fun InCallScreen(ui: InCallUi, onAdd: () -> Unit) {
    val main = ui.primary ?: return
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val dir = LocalLayoutDirection.current
    var keypad by rememberSaveable { mutableStateOf(false) }
    val body: @Composable () -> Unit = {
        when {
            ui.controls.answer -> Unit
            ui.controls.selectAccount -> SelectChip(main.live.id)
            else -> OngoingControls(ui, main.live.id, keypad, { keypad = !keypad }, onAdd)
        }
    }
    val actions: @Composable () -> Unit = {
        when {
            ui.controls.answer -> IncomingButtons(main.live.id)
            ui.controls.hangup -> {
                val label = str(if (ui.controls.selectAccount) R.string.cancel else R.string.call_hangup)
                CallRoundButton(label, Icons.Rounded.CallEnd, HangupRed) { CallManager.hangup(main.live.id) }
            }
            // Mientras termina no se ofrece nada, y el texto de estado lo dice.
            else -> Spacer(Modifier.height(72.dp))
        }
    }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(
                top = insets.calculateTopPadding() + 12.dp,
                bottom = insets.calculateBottomPadding() + 12.dp,
                start = insets.calculateStartPadding(dir) + 20.dp,
                end = insets.calculateEndPadding(dir) + 20.dp,
            ),
    ) {
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                        CallHeader(main)
                        OtherCall(main, ui.secondary)
                    }
                    Box(Modifier.padding(top = 8.dp)) { actions() }
                }
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Column(Modifier.verticalScroll(rememberScrollState())) { body() }
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(12.dp))
                CallHeader(main)
                OtherCall(main, ui.secondary)
                Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.BottomCenter) {
                    Column(Modifier.verticalScroll(rememberScrollState())) { body() }
                }
                Box(Modifier.padding(bottom = if (ui.controls.answer) 32.dp else 0.dp)) { actions() }
            }
        }
    }
}

@Composable
private fun OtherCall(main: CallView, other: CallView?) {
    other ?: return
    GlassCard(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        val text = when {
            main.live.phase == CallPhase.RINGING -> str(R.string.call_waiting_with, other.title)
            other.live.phase == CallPhase.HOLDING -> str(R.string.call_other_held, other.title)
            else -> str(R.string.call_other_active, other.title)
        }
        Text(text, fontSize = 14.sp, color = LocalGlass.current.content, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CallHeader(call: CallView) {
    val p = LocalGlass.current
    Text(
        call.title,
        Modifier.fillMaxWidth().semantics { heading() },
        fontSize = 32.sp,
        fontWeight = FontWeight.SemiBold,
        color = p.content,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    call.subtitle?.let { Text(it, Modifier.padding(top = 4.dp), fontSize = 17.sp, color = p.contentSecondary) }
    StatusLine(call)
    // El chip por el que entra o sale la llamada, con su nombre y color.
    val chip = call.chip
    if (chip != null || call.chipUnknown) {
        val text = chip?.let(::simName) ?: str(R.string.sim_gone)
        Row(
            Modifier.padding(top = 12.dp).semantics(mergeDescendants = true) { },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SimBadge(chip)
            Text(text, fontSize = 15.sp, color = p.content)
        }
    }
}

@Composable
private fun StatusLine(call: CallView) {
    val p = LocalGlass.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val ticking = call.live.phase == CallPhase.ACTIVE && call.connectedAt > 0
    LaunchedEffect(ticking) {
        while (ticking) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val status = when (call.live.phase) {
        CallPhase.RINGING -> str(R.string.call_screen_incoming)
        CallPhase.SELECT_ACCOUNT, CallPhase.DIALING -> str(R.string.call_dialing)
        CallPhase.CONNECTING -> str(R.string.call_connecting)
        CallPhase.HOLDING -> str(R.string.call_on_hold)
        CallPhase.DISCONNECTING, CallPhase.DISCONNECTED -> str(R.string.call_ending)
        CallPhase.ACTIVE -> if (ticking) elapsed(now - call.connectedAt) else str(R.string.call_in_progress)
    }
    val spoken = if (call.live.phase == CallPhase.ACTIVE) str(R.string.call_in_progress) else status
    Text(
        status,
        // TalkBack anuncia los cambios de estado, no cada segundo del contador.
        Modifier.padding(top = 8.dp).semantics {
            contentDescription = spoken
            liveRegion = LiveRegionMode.Polite
        },
        fontSize = 17.sp,
        color = p.contentSecondary,
    )
    if (call.live.emergency) {
        Text(str(R.string.call_emergency), Modifier.padding(top = 6.dp), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = p.danger)
    }
}

private fun elapsed(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

/** Botón redondo de color fijo, con su texto abajo. Nunca menos de 72 dp. */
@Composable
private fun CallRoundButton(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    val p = LocalGlass.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(color)
                // Sobre un fondo oscuro el relleno solo no llega a 3:1 con lo que lo rodea.
                .then(if (p.dark) Modifier.border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape) else Modifier)
                .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(32.dp), tint = readableOn(color))
        }
        Text(label, Modifier.padding(top = 8.dp).clearAndSetSemantics { }, fontSize = 15.sp, color = LocalGlass.current.content)
    }
}

@Composable
private fun IncomingButtons(id: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        CallRoundButton(str(R.string.call_reject), Icons.Rounded.CallEnd, HangupRed) { CallManager.reject(id) }
        CallRoundButton(str(R.string.call_answer), Icons.Rounded.Call, AnswerGreen) { CallManager.answer(id) }
    }
}

/** El sistema dejó la llamada esperando un chip. Colgar la cancela: todavía no salió. */
@Composable
private fun SelectChip(id: Int) {
    val sims by Repo.sims.state.collectAsStateWithLifecycle()
    val settings by Stores.settings.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(str(R.string.call_select_sim), fontSize = 18.sp, color = LocalGlass.current.content)
        sims.chips.filter { it.account != null }.sortedBy { if (it.slotIndex == settings.primarySimSlot) -1 else it.slotIndex }.forEach { chip ->
            val primary = chip.slotIndex == settings.primarySimSlot
            ChipCallButton(chip, prominent = primary, enabled = true, modifier = Modifier.fillMaxWidth(), isPrimary = primary) { CallManager.selectAccount(id, chip) }
        }
    }
}

@Composable
private fun OngoingControls(ui: InCallUi, id: Int, keypad: Boolean, onKeypad: () -> Unit, onAdd: () -> Unit) {
    val c = ui.controls
    var picker by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (keypad && c.dtmf) DtmfPad(id)
        if (picker) RoutePicker(ui) { picker = false }
        val items = buildList<@Composable () -> Unit> {
            if (c.mute) add {
                ToggleControl(str(if (ui.muted) R.string.call_unmute else R.string.call_mute), if (ui.muted) Icons.Rounded.MicOff else Icons.Rounded.Mic, ui.muted) { CallManager.setMuted(!ui.muted) }
            }
            if (c.route && InCallRules.routeCanChange(ui.routes)) add {
                ToggleControl(routeLabel(ui.route), routeIcon(ui.route), ui.route == AudioRoute.SPEAKER, description = str(R.string.call_route_now, routeLabel(ui.route))) {
                    if (InCallRules.routeNeedsPicker(ui.routes)) picker = !picker else CallManager.setRoute(InCallRules.toggledRoute(ui.route, ui.routes))
                }
            }
            if (c.dtmf) add {
                ToggleControl(str(if (keypad) R.string.call_hide_keypad else R.string.call_keypad), Icons.Rounded.Dialpad, keypad, onClick = onKeypad)
            }
            if (c.hold) add {
                val held = ui.primary?.live?.phase == CallPhase.HOLDING
                ToggleControl(str(if (held) R.string.call_unhold else R.string.call_hold), if (held) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, held) { CallManager.toggleHold(id) }
            }
            if (c.add) add { ToggleControl(str(R.string.call_add), Icons.Rounded.Add, false, onClick = onAdd) }
            if (c.swap) add { ToggleControl(str(R.string.call_swap), Icons.Rounded.SwapCalls, false) { CallManager.swap() } }
            if (c.merge) add { ToggleControl(str(R.string.call_merge), Icons.AutoMirrored.Rounded.CallMerge, false) { CallManager.merge(id) } }
        }
        items.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { item -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { item() } }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private fun routeLabel(route: AudioRoute): String = str(
    when (route) {
        AudioRoute.SPEAKER -> R.string.call_route_speaker
        AudioRoute.BLUETOOTH -> R.string.call_route_bluetooth
        AudioRoute.WIRED -> R.string.call_route_wired
        AudioRoute.EARPIECE -> R.string.call_route_earpiece
    },
)

private fun routeIcon(route: AudioRoute): ImageVector = when (route) {
    AudioRoute.SPEAKER -> Icons.AutoMirrored.Rounded.VolumeUp
    AudioRoute.BLUETOOTH -> Icons.Rounded.Bluetooth
    AudioRoute.WIRED -> Icons.Rounded.Headset
    AudioRoute.EARPIECE -> Icons.Rounded.PhoneInTalk
}

@Composable
private fun RoutePicker(ui: InCallUi, dismiss: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(str(R.string.call_route_title), fontSize = 15.sp, color = LocalGlass.current.contentSecondary)
        ui.routes.forEach { route ->
            GlassButton(routeLabel(route), { CallManager.setRoute(route); dismiss() }, Modifier.fillMaxWidth().padding(top = 8.dp), prominent = route == ui.route)
        }
    }
}

/** Un botón de la grilla. El estado (activado o no) lo anuncia TalkBack y se ve en el relleno. */
@Composable
private fun ToggleControl(label: String, icon: ImageVector, on: Boolean, description: String = label, onClick: () -> Unit) {
    val p = LocalGlass.current
    val fill = if (on) p.content else p.content.copy(alpha = 0.12f)
    val tint = if (on) p.base else p.content
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(fill)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics {
                    contentDescription = description
                    if (on) stateDescription = str(R.string.call_control_on)
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(26.dp), tint = tint)
        }
        Text(label, Modifier.padding(top = 6.dp).clearAndSetSemantics { }, fontSize = 13.sp, color = p.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Tonos durante la llamada. No escribe en ningún lado que quede guardado. */
@Composable
private fun DtmfPad(id: Int) {
    val p = LocalGlass.current
    var typed by rememberSaveable { mutableStateOf("") }
    GlassCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(
            typed,
            Modifier.fillMaxWidth().heightIn(min = 32.dp).semantics { liveRegion = LiveRegionMode.Polite },
            fontSize = 22.sp,
            color = p.content,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.StartEllipsis,
        )
        for (row in listOf("123", "456", "789", "*0#")) {
            Row(Modifier.fillMaxWidth()) {
                for (key in row) {
                    val name = str(KEY_NAMES.getValue(key))
                    Box(
                        Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) {
                                typed += key
                                CallManager.dtmf(id, key)
                            }
                            .clearAndSetSemantics {
                                contentDescription = name
                                role = Role.Button
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(key.toString(), fontSize = 24.sp, fontWeight = FontWeight.Medium, color = p.content)
                    }
                }
            }
        }
    }
}
