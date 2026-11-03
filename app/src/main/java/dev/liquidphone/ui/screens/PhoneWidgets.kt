// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.liquidphone.DevTools
import dev.liquidphone.R
import dev.liquidphone.calls.DialOutcome
import dev.liquidphone.core.sim.AskReason
import dev.liquidphone.core.sim.CallDecision
import dev.liquidphone.core.sim.CallTarget
import dev.liquidphone.core.sim.DialerReason
import dev.liquidphone.core.sim.SimChip
import dev.liquidphone.core.sim.SimStatus
import dev.liquidphone.data.Repo
import dev.liquidphone.data.Stores
import dev.liquidphone.ui.glass.DialogSpec
import dev.liquidphone.ui.glass.GlassButton
import dev.liquidphone.ui.glass.GlassCard
import dev.liquidphone.ui.glass.GlassSurface
import dev.liquidphone.ui.glass.LocalDialog
import dev.liquidphone.ui.glass.LocalGlass
import dev.liquidphone.ui.glass.ScreenTitle
import dev.liquidphone.ui.glass.accentFill
import dev.liquidphone.ui.glass.readableOn
import dev.liquidphone.ui.glass.styled
import dev.liquidphone.util.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** "SIM 2". Es la ranura, tal como la nombra el sistema: 0 es la SIM 1, física o eSIM. */
fun simLabel(chip: SimChip): String = str(R.string.sim_label, chip.slotNumber)

/** Lo que dice cualquier botón o lista sobre un chip: nombre y, si hace falta, que no tiene servicio. */
fun simDescription(chip: SimChip): String =
    listOfNotNull(simLabel(chip), chip.displayName.takeIf { it.isNotBlank() }, str(R.string.sim_no_service).takeIf { chip.status == SimStatus.NO_SERVICE }).joinToString(" · ")

/** El chip dicho en una frase: "Nombre (SIM 2)", o solo "SIM 2" si no tiene nombre. */
fun simName(chip: SimChip): String =
    chip.displayName.takeIf { it.isNotBlank() }?.let { "$it (${simLabel(chip)})" } ?: simLabel(chip)

/** Círculo del color del chip con el número de ranura. Null: una llamada de un chip desconocido. */
@Composable
fun SimBadge(chip: SimChip?, modifier: Modifier = Modifier) {
    val p = LocalGlass.current
    val color = chip?.let { Color(it.color).copy(alpha = 1f) } ?: p.contentSecondary
    Box(
        modifier.size(20.dp).clip(CircleShape).background(color).clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(chip?.slotNumber?.toString() ?: "?", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = readableOn(color))
    }
}

/** Botón de llamar. Con chip dice cuál; sin chip decide la política o el sistema. */
@Composable
fun ChipCallButton(chip: SimChip?, prominent: Boolean, enabled: Boolean, modifier: Modifier = Modifier, isPrimary: Boolean = false, onClick: () -> Unit) {
    val p = LocalGlass.current
    val textColor = if (prominent) p.onAccent else p.content
    // TalkBack: "Llamar con SIM 2, chip principal, Nombre, sin servicio".
    val description = if (chip != null) {
        str(
            R.string.call_with,
            listOfNotNull(
                simLabel(chip),
                str(R.string.sim_primary_mark).takeIf { isPrimary },
                chip.displayName.takeIf { it.isNotBlank() },
                str(R.string.sim_no_service).takeIf { chip.status == SimStatus.NO_SERVICE },
            ).joinToString(", "),
        )
    } else {
        str(R.string.call)
    }
    GlassSurface(
        modifier
            .heightIn(min = 56.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = description
            },
        shape = CircleShape,
        strong = true,
        tint = if (prominent) p.accentFill() else null,
        onClick = if (enabled) onClick else null,
    ) {
        Row(
            Modifier.align(Alignment.Center).padding(horizontal = 14.dp, vertical = 8.dp).clearAndSetSemantics { },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (chip != null) {
                SimBadge(chip)
            } else {
                Icon(styled(Icons.Rounded.Call), null, Modifier.size(20.dp), tint = textColor)
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(chip?.let(::simLabel) ?: str(R.string.call), color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                val detail = chip?.let { if (it.status == SimStatus.NO_SERVICE) str(R.string.sim_no_service) else it.displayName }
                if (!detail.isNullOrBlank()) {
                    Text(detail, color = textColor.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * Los botones de llamar del teclado y del detalle. Con dos chips, siempre los dos: primero y destacado
 * el principal, después el otro. Son una elección explícita, por encima de la política. Con un solo
 * chip o sin poder verlos, un solo botón.
 */
@Composable
fun CallButtons(number: String, modifier: Modifier = Modifier) {
    val caller = LocalCaller.current
    val sims by Repo.sims.state.collectAsStateWithLifecycle()
    val settings by Stores.settings.state.collectAsStateWithLifecycle()
    val primarySlot = settings.primarySimSlot
    val enabled = number.any { it.isDigit() || it == '*' || it == '#' }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (sims.chips.isEmpty()) {
            ChipCallButton(null, prominent = true, enabled = enabled, modifier = Modifier.weight(1f)) { caller(number, CallTarget.UsePolicy) }
        } else {
            sims.chips.sortedBy { if (it.slotIndex == primarySlot) -1 else it.slotIndex }.forEach { chip ->
                val isPrimary = chip.slotIndex == primarySlot
                ChipCallButton(
                    chip,
                    prominent = isPrimary || sims.chips.size == 1,
                    enabled = enabled,
                    modifier = Modifier.weight(if (isPrimary) 1.15f else 1f),
                    isPrimary = isPrimary,
                ) {
                    caller(number, CallTarget.Slot(chip.slotIndex))
                }
            }
        }
    }
}

/** Llamar desde cualquier pantalla: `caller(número, a quién le toca elegir el chip)`. */
val LocalCaller = staticCompositionLocalOf<(String, CallTarget) -> Unit> { { _, _ -> } }

/**
 * Hace las llamadas de toda la app: decide fuera del hilo principal, pregunta si hace falta y explica
 * el permiso de llamar antes del diálogo del sistema. Si se niega, cae a ACTION_DIAL.
 */
@Composable
fun CallerHost(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dialog = LocalDialog.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Pair<String, CallTarget>?>(null) }
    var asked by remember { mutableStateOf(false) }

    fun execute(decision: CallDecision) {
        when (val outcome = Repo.dialer.execute(context, decision)) {
            DialOutcome.Sent -> Unit
            DialOutcome.Failed -> toast(str(R.string.no_dialer))
            is DialOutcome.Simulated -> DevTools.simulatedText(outcome)?.let(toast)
        }
    }

    fun call(number: String, target: CallTarget, afterPermission: Boolean, onPermission: () -> Unit) {
        scope.launch {
            val decision = withContext(Dispatchers.Default) { Repo.decide(number, target) }
            when {
                decision is CallDecision.NoNumber -> Unit
                decision is CallDecision.Ask -> dialog(
                    DialogSpec(askTitle(decision)) { dismiss ->
                        ChipChoice(decision, dismiss) { chip ->
                            dismiss()
                            execute(CallDecision.Place(decision.number, chip))
                        }
                    },
                )
                decision is CallDecision.OpenSystemDialer && decision.reason == DialerReason.NO_CALL_PERMISSION && !afterPermission && !asked -> {
                    pending = number to target
                    dialog(
                        DialogSpec(str(R.string.permission_call_title)) { dismiss ->
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(str(R.string.permission_call_text), color = LocalGlass.current.content, fontSize = 14.sp)
                                GlassButton(str(R.string.permission_continue), { dismiss(); onPermission() }, Modifier.fillMaxWidth(), prominent = true)
                                GlassButton(str(R.string.use_system_dialer), { dismiss(); pending = null; execute(decision) }, Modifier.fillMaxWidth())
                            }
                        },
                    )
                }
                else -> execute(decision)
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        asked = true
        Repo.start()
        pending?.let { (number, target) -> call(number, target, afterPermission = true) {} }
        pending = null
    }
    val caller: (String, CallTarget) -> Unit = remember {
        { number, target -> call(number, target, afterPermission = false) { launcher.launch(Manifest.permission.CALL_PHONE) } }
    }
    CompositionLocalProvider(LocalCaller provides caller, content = content)
}

private fun askTitle(decision: CallDecision.Ask): String {
    val airplane = Repo.sims.state.value.airplane
    return when (decision.reason) {
        AskReason.ALWAYS -> str(R.string.ask_always)
        AskReason.PRIMARY_UNAVAILABLE -> {
            val other = decision.other?.let(::simName)
            when {
                airplane -> str(R.string.ask_airplane)
                decision.suggested == null && other != null -> str(R.string.ask_primary_gone_other, other)
                decision.suggested == null -> str(R.string.ask_primary_gone)
                other != null -> str(R.string.ask_primary_no_service_other, other)
                else -> str(R.string.ask_primary_no_service)
            }
        }
        AskReason.NO_SERVICE -> {
            val suggested = decision.suggested
            when {
                airplane -> str(R.string.ask_airplane)
                suggested != null -> str(R.string.ask_no_service, simLabel(suggested))
                else -> str(R.string.ask_always)
            }
        }
        AskReason.CHIP_GONE -> str(R.string.ask_chip_gone)
        AskReason.AMBIGUOUS_SLOT -> str(R.string.ask_ambiguous)
    }
}

/**
 * Un botón por chip, con nombre y color, más cancelar. Primero el chip que nombra la pregunta; si no
 * nombra ninguno, el que tocaba. Sin ninguno de los dos, nada se destaca: no se sugiere un chip al azar.
 */
@Composable
private fun ChipChoice(decision: CallDecision.Ask, dismiss: () -> Unit, onPick: (SimChip) -> Unit) {
    val settings by Stores.settings.state.collectAsStateWithLifecycle()
    val first = decision.other ?: decision.suggested
    val ordered = decision.options.sortedBy { if (it == first) -1 else it.slotIndex }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ordered.forEach { chip ->
            ChipCallButton(
                chip,
                prominent = chip == first,
                enabled = true,
                modifier = Modifier.fillMaxWidth(),
                isPrimary = chip.slotIndex == settings.primarySimSlot,
            ) { onPick(chip) }
        }
        GlassButton(str(R.string.cancel), dismiss, Modifier.fillMaxWidth())
    }
}

/**
 * Pantalla que explica para qué es un permiso antes del diálogo del sistema. Mientras falte, en vez
 * de [content] se ve la explicación; la app sigue funcionando en las otras pestañas.
 */
@Composable
fun PermissionGate(
    permission: String,
    title: String,
    explanation: String,
    screenTitle: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val tick by Repo.permissionTick.collectAsStateWithLifecycle()
    val granted = remember(tick) { Repo.granted(permission) }
    if (granted) {
        content()
        return
    }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        denied = !ok
        Repo.start()
    }
    val p = LocalGlass.current
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        ScreenTitle(screenTitle)
        GlassCard {
            Text(title, fontSize = 20.sp, color = p.content)
            Text(explanation, Modifier.padding(top = 10.dp), fontSize = 15.sp, color = p.contentSecondary)
            if (denied) {
                Text(str(R.string.permission_denied), Modifier.padding(top = 10.dp), fontSize = 14.sp, color = p.contentSecondary)
            }
            Spacer(Modifier.height(18.dp))
            GlassButton(str(R.string.permission_continue), { launcher.launch(permission) }, Modifier.fillMaxWidth(), prominent = true)
        }
    }
}

/** "Agregar a contactos" por el intent estándar: abre Liquid Contacts o la app que corresponda. */
fun addToContacts(context: Context, number: String): Boolean = try {
    context.startActivity(
        Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
            type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, number)
        },
    )
    true
} catch (e: ActivityNotFoundException) {
    false
}
