// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone.ui.screens

import dev.liquidphone.ui.glass.SettingRow
import dev.liquidphone.ui.glass.LocalDialog
import dev.liquidphone.ui.glass.GlassButton
import dev.liquidphone.ui.glass.DialogSpec
import dev.liquidphone.ui.components.BarItem
import dev.liquidphone.ui.components.DangerButton
import dev.liquidphone.ui.components.LocalSelectionBottom
import dev.liquidphone.ui.components.SelectionBottom
import dev.liquidphone.ui.components.SelectionTopState
import androidx.compose.ui.semantics.selected
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.liquidphone.R
import dev.liquidphone.core.calls.CallGroup
import dev.liquidphone.core.calls.CallLogTools
import dev.liquidphone.core.calls.CallRow
import dev.liquidphone.core.calls.CallType
import dev.liquidphone.core.calls.ChipFilter
import dev.liquidphone.core.calls.Presentation
import dev.liquidphone.core.model.ContactSummary
import dev.liquidphone.core.sim.AccountKey
import dev.liquidphone.core.sim.CallTarget
import dev.liquidphone.core.sim.SimChip
import dev.liquidphone.data.Repo
import dev.liquidphone.ui.components.ContactAvatar
import dev.liquidphone.ui.components.dangerText
import dev.liquidphone.ui.glass.GlassIconButton
import dev.liquidphone.ui.glass.LocalGlass
import dev.liquidphone.ui.glass.LocalTabActive
import dev.liquidphone.ui.glass.readableOn
import dev.liquidphone.ui.glass.ScreenTitle
import dev.liquidphone.ui.glass.styled
import dev.liquidphone.util.plural
import dev.liquidphone.util.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/** Lo que muestra una fila, ya calculado fuera del hilo principal. */
class RecentItem(val group: CallGroup, val contact: ContactSummary?, val shownNumber: String) {
    val title: String get() = contact?.displayName ?: shownNumber
}

/** El chip de una fila, o null si ya no está. Nunca se adivina. */
fun chipOf(account: AccountKey?, chips: List<SimChip>): SimChip? = account?.let { key -> chips.firstOrNull { it.account == key } }

private const val FILTER_ALL = -2
private const val FILTER_GONE = -3

/** Pestaña de recientes: del registro del sistema, con filtro por chip y por perdidas. */
@Composable
fun RecentsTab(padding: PaddingValues, onSettings: () -> Unit, onOpen: (CallGroup) -> Unit) {
    PermissionGate(
        Manifest.permission.READ_CALL_LOG,
        str(R.string.permission_log_title),
        str(R.string.permission_log_text),
        str(R.string.tab_recents),
        Modifier.padding(padding),
    ) {
        RecentsList(padding, onSettings, onOpen)
    }
}

@Composable
private fun RecentsList(padding: PaddingValues, onSettings: () -> Unit, onOpen: (CallGroup) -> Unit) {
    val rows by Repo.calls.collectAsStateWithLifecycle()
    val book by Repo.book.collectAsStateWithLifecycle()
    val numbers by Repo.numbers.collectAsStateWithLifecycle()
    val sims by Repo.sims.state.collectAsStateWithLifecycle()
    var chipFilter by rememberSaveable { mutableIntStateOf(FILTER_ALL) }
    var missedOnly by rememberSaveable { mutableStateOf(false) }
    val caller = LocalCaller.current
    val dialog = LocalDialog.current
    val deleteCalls = rememberCallDeleter()

    val present = remember(sims) { sims.chips.mapNotNull { it.account }.toSet() }
    val hasGone = remember(rows, present) { rows.any { it.account == null || it.account !in present } }
    val selectedChip = sims.chips.firstOrNull { it.slotIndex == chipFilter }
    // Una función, no un valor: la ventana de filtros queda abierta mientras cambia lo elegido.
    fun currentFilter(): ChipFilter {
        val account = sims.chips.firstOrNull { it.slotIndex == chipFilter }?.account
        return when {
            chipFilter == FILTER_GONE -> ChipFilter.NoLongerPresent(present)
            account != null -> ChipFilter.Only(account)
            else -> ChipFilter.All
        }
    }
    val filter = currentFilter()
    val items by produceState<List<RecentItem>?>(null, rows, book, numbers, filter, missedOnly) {
        value = withContext(Dispatchers.Default) {
            CallLogTools.group(visibleRows(rows, filter, missedOnly), numbers).map { group ->
                RecentItem(group, if (group.latest.callable) Repo.contactFor(group.number) else null, shownNumber(group.latest, numbers::format))
            }
        }
    }

    // Selección múltiple, por entrada de la lista: la clave deja de valer si esa entrada cambia.
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val list = items.orEmpty()
    val chosen = list.filter { it.group.key in selected }
    val selecting = selected.isNotEmpty()
    LaunchedEffect(items) { if (items != null) selected = chosen.mapTo(HashSet()) { it.group.key } }
    // Al irse de la pestaña, con una notificación por ejemplo, la selección no sigue abierta.
    val tabActive = LocalTabActive.current
    LaunchedEffect(tabActive) { if (!tabActive) selected = emptySet() }
    if (selecting) {
        val ids = chosen.flatMap { it.group.ids }
        val top = rememberUpdatedState(
            SelectionTopState(chosen.size, chosen.size < list.size, { selected = list.mapTo(HashSet()) { it.group.key } }, { selected = emptySet() }),
        )
        val bar = rememberUpdatedState(
            listOf(
                BarItem(Icons.Rounded.DeleteOutline, str(R.string.delete), str(R.string.delete), ids.isNotEmpty()) {
                    deleteCalls(
                        DeleteCalls(plural(R.plurals.delete_selected_title, chosen.size, chosen.size), plural(R.plurals.delete_calls_text, ids.size, ids.size), ids) { selected = emptySet() },
                    )
                },
            ),
        )
        val publish = LocalSelectionBottom.current
        DisposableEffect(Unit) {
            publish(SelectionBottom(top, bar))
            onDispose { publish(null) }
        }
    }

    Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
        // Lo que se está filtrando queda escrito: si no, parecería que faltan llamadas.
        val active = listOfNotNull(
            str(R.string.filter_missed).takeIf { missedOnly },
            selectedChip?.let(::simLabel),
            str(R.string.filter_other_sim).takeIf { chipFilter == FILTER_GONE },
        ).joinToString(" · ").ifEmpty { null }
        Box(Modifier.padding(horizontal = 16.dp)) {
            ScreenTitle(
                str(R.string.tab_recents),
                subtitle = active,
                actions = {
                    GlassIconButton(
                        Icons.Rounded.FilterList,
                        str(R.string.filter_title),
                        {
                            dialog(
                                DialogSpec(str(R.string.filter_title)) { dismiss ->
                                    Text(str(R.string.filter_type), Modifier.semantics { heading() }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LocalGlass.current.contentSecondary)
                                    FilterChoice(str(R.string.filter_type_all), !missedOnly) { missedOnly = false }
                                    FilterChoice(str(R.string.filter_type_missed), missedOnly) { missedOnly = true }
                                    if (sims.multiple || hasGone) {
                                        Text(str(R.string.filter_sim), Modifier.padding(top = 14.dp).semantics { heading() }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LocalGlass.current.contentSecondary)
                                        FilterChoice(str(R.string.filter_all_sims), chipFilter == FILTER_ALL) { chipFilter = FILTER_ALL }
                                        sims.chips.forEach { chip ->
                                            FilterChoice(simName(chip), chipFilter == chip.slotIndex, chip = chip) { chipFilter = chip.slotIndex }
                                        }
                                        if (hasGone) FilterChoice(str(R.string.filter_other_sim), chipFilter == FILTER_GONE, subtitle = str(R.string.filter_other_sim_hint)) { chipFilter = FILTER_GONE }
                                    }
                                    // Se ve antes de cerrar cuántas llamadas deja el filtro.
                                    val now = currentFilter()
                                    val shown = remember(rows, now, missedOnly) { visibleRows(rows, now, missedOnly).size }
                                    GlassButton(
                                        if (shown == 0) str(R.string.filter_none) else plural(R.plurals.filter_show, shown, shown),
                                        dismiss,
                                        Modifier.fillMaxWidth().padding(top = 16.dp),
                                        prominent = true,
                                    )
                                    if (missedOnly || chipFilter != FILTER_ALL) {
                                        GlassButton(
                                            str(R.string.filter_clear),
                                            {
                                                missedOnly = false
                                                chipFilter = FILTER_ALL
                                            },
                                            Modifier.fillMaxWidth().padding(top = 8.dp),
                                        )
                                    }
                                },
                            )
                        },
                        prominent = active != null,
                    )
                    GlassIconButton(
                        Icons.Rounded.DeleteOutline,
                        str(R.string.delete_history),
                        {
                            // Lo que se borra es lo que se ve: con un filtro, solo lo filtrado.
                            val ids = visibleRows(rows, filter, missedOnly).map { it.id }
                            deleteCalls(
                                DeleteCalls(
                                    str(if (active == null) R.string.delete_history_title else R.string.delete_filtered_title),
                                    plural(R.plurals.delete_calls_text, ids.size, ids.size),
                                    ids,
                                ),
                            )
                        },
                        enabled = list.isNotEmpty(),
                    )
                    GlassIconButton(Icons.Rounded.Settings, str(R.string.settings), onSettings)
                },
            )
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = padding.calculateBottomPadding()),
        ) {
            if (items != null && list.isEmpty()) {
                item("vacio") {
                    EmptyState(str(R.string.recents_empty), str(if (active == null) R.string.recents_empty_hint else R.string.filter_none_hint), Modifier.padding(top = 40.dp))
                }
            }
            items(list, key = { it.group.key }) { item ->
                val key = item.group.key
                val toggle = { selected = if (key in selected) selected - key else selected + key }
                RecentRow(
                    item,
                    chipOf(item.group.account, sims.chips),
                    showChip = sims.multiple || item.group.account !in present,
                    selected = if (selecting) key in selected else null,
                    onCall = {
                        when {
                            selecting -> toggle()
                            item.group.latest.callable -> caller(item.group.number, CallTarget.UsePolicy)
                            else -> onOpen(item.group)
                        }
                    },
                    onOpen = { onOpen(item.group) },
                    onSelect = toggle,
                )
            }
        }
    }
}

/** Las filas que deja ver el filtro, antes de agrupar. */
private fun visibleRows(rows: List<CallRow>, filter: ChipFilter, missedOnly: Boolean): List<CallRow> =
    CallLogTools.filter(rows, filter).let { list -> if (missedOnly) list.filter { it.type == CallType.MISSED } else list }

/** Filas del registro para borrar, con lo que se le pregunta al usuario. [after] corre al terminar. */
class DeleteCalls(val title: String, val text: String, val ids: List<Long>, val after: () -> Unit = {})

/**
 * Borra del registro del sistema siempre igual: el permiso se pide recién acá, con su explicación,
 * y nada se borra sin confirmar.
 */
@Composable
fun rememberCallDeleter(): (DeleteCalls) -> Unit {
    val p = LocalGlass.current
    val dialog = LocalDialog.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    var waiting by remember { mutableStateOf<DeleteCalls?>(null) }

    fun run(request: DeleteCalls) {
        scope.launch {
            val deleted = withContext(Dispatchers.IO) { runCatching { Repo.deleteCalls(request.ids) }.getOrNull() }
            toast(if (deleted == null) str(R.string.delete_calls_failed) else plural(R.plurals.calls_deleted, deleted, deleted))
            request.after()
        }
    }
    fun confirm(request: DeleteCalls) = dialog(
        DialogSpec(request.title) { dismiss ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(request.text, color = p.content, fontSize = 14.sp)
                DangerButton(str(R.string.delete), { dismiss(); run(request) }, Modifier.fillMaxWidth())
                GlassButton(str(R.string.cancel), dismiss, Modifier.fillMaxWidth())
            }
        },
    )
    val writeLog = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val request = waiting
        waiting = null
        if (!ok) toast(str(R.string.permission_delete_denied)) else if (request != null) confirm(request)
    }
    return { request ->
        if (Repo.granted(Manifest.permission.WRITE_CALL_LOG)) {
            confirm(request)
        } else {
            waiting = request
            dialog(
                DialogSpec(str(R.string.permission_delete_title)) { dismiss ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(str(R.string.permission_delete_text), color = p.content, fontSize = 14.sp)
                        GlassButton(str(R.string.permission_continue), { dismiss(); writeLog.launch(Manifest.permission.WRITE_CALL_LOG) }, Modifier.fillMaxWidth(), prominent = true)
                        GlassButton(str(R.string.cancel), dismiss, Modifier.fillMaxWidth())
                    }
                },
            )
        }
    }
}

/** Una opción de la ventana de filtros: se elige una sola por grupo, y el círculo lo deja claro. */
@Composable
private fun FilterChoice(title: String, chosen: Boolean, chip: SimChip? = null, subtitle: String? = null, onClick: () -> Unit) {
    val p = LocalGlass.current
    SettingRow(
        title,
        Modifier.heightIn(min = 48.dp).semantics { selected = chosen },
        subtitle = subtitle,
        onClick = onClick,
        leading = {
            Icon(
                styled(if (chosen) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked),
                null,
                Modifier.size(22.dp),
                tint = if (chosen) p.accentFg else p.contentSecondary,
            )
            Spacer(Modifier.width(12.dp))
            if (chip != null) SimBadge(chip, Modifier.padding(end = 10.dp))
        },
    )
}

fun shownNumber(row: CallRow, format: (String) -> String): String = when {
    row.callable -> format(row.number)
    row.presentation == Presentation.RESTRICTED -> str(R.string.number_hidden)
    row.presentation == Presentation.PAYPHONE -> str(R.string.number_payphone)
    else -> str(R.string.number_unknown)
}

fun callTypeLabel(type: CallType): String = str(
    when (type) {
        CallType.INCOMING -> R.string.call_incoming
        CallType.OUTGOING -> R.string.call_outgoing
        CallType.MISSED -> R.string.call_missed
        CallType.VOICEMAIL -> R.string.call_voicemail
        CallType.REJECTED -> R.string.call_rejected
        CallType.BLOCKED -> R.string.call_blocked
        CallType.ANSWERED_ELSEWHERE -> R.string.call_elsewhere
        CallType.OTHER -> R.string.call_other
    },
)

/** Ícono del tipo de llamada. Además del color, la flecha y el texto dicen lo mismo. */
@Composable
fun CallTypeIcon(type: CallType, modifier: Modifier = Modifier) {
    val p = LocalGlass.current
    val icon = when (type) {
        CallType.OUTGOING -> Icons.AutoMirrored.Rounded.CallMade
        CallType.MISSED, CallType.REJECTED -> Icons.AutoMirrored.Rounded.CallMissed
        CallType.BLOCKED -> Icons.Rounded.Block
        else -> Icons.AutoMirrored.Rounded.CallReceived
    }
    val tint: Color = if (type == CallType.MISSED) p.dangerText else p.contentSecondary
    Icon(styled(icon), null, modifier.size(16.dp), tint = tint)
}

/**
 * Fila plana: sin cristal, como en Contactos. Tocar llama con la política; el ícono abre el detalle y
 * mantener apretado selecciona. [selected] es null mientras la lista no está seleccionando.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentRow(item: RecentItem, chip: SimChip?, showChip: Boolean, selected: Boolean?, onCall: () -> Unit, onOpen: () -> Unit, onSelect: () -> Unit) {
    val p = LocalGlass.current
    val context = LocalContext.current
    val group = item.group
    val latest = group.latest
    val time = callTime(context, latest.dateMillis)
    val missed = latest.type == CallType.MISSED
    val chipText = when {
        !showChip -> null
        chip != null -> simLabel(chip)
        else -> str(R.string.sim_gone)
    }
    val count = group.count.takeIf { it > 1 }
    val description = listOfNotNull(
        item.title,
        callTypeLabel(latest.type),
        count?.let { str(R.string.call_count, it) },
        time,
        chipText,
    ).joinToString(", ")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                role = Role.Button,
                onClickLabel = if (selected != null) str(R.string.select) else if (latest.callable) str(R.string.call) else str(R.string.details),
                onLongClickLabel = str(R.string.select),
                onLongClick = onSelect,
                onClick = onCall,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = description
                if (selected != null) this.selected = selected
            }
            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected == true) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(p.accentFg), contentAlignment = Alignment.Center) {
                Icon(styled(Icons.Rounded.Check), null, Modifier.size(26.dp), tint = readableOn(p.accentFg))
            }
        } else {
            ContactAvatar(item.contact?.displayName ?: "", photoUri = item.contact?.photoUri)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.title,
                    Modifier.weight(1f, fill = false),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (missed) p.dangerText else p.content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (count != null) Text(" ($count)", fontSize = 15.sp, color = p.contentSecondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                CallTypeIcon(latest.type)
                Spacer(Modifier.width(6.dp))
                Text(time, fontSize = 14.sp, color = p.contentSecondary, maxLines = 1)
                if (showChip) {
                    Spacer(Modifier.width(8.dp))
                    SimBadge(chip)
                }
            }
        }
        // Al seleccionar, toda la fila selecciona; el lugar queda para que nada salte.
        if (selected != null) {
            Spacer(Modifier.size(48.dp))
        } else {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .combinedClickable(role = Role.Button, onClick = onOpen)
                    .semantics { contentDescription = str(R.string.details_of, item.title) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(styled(Icons.Rounded.Info), null, Modifier.size(22.dp), tint = p.contentSecondary)
            }
        }
    }
}

/** Hoy: la hora. Ayer: "Ayer". Este año: día y mes. Antes: con el año. */
fun callTime(context: android.content.Context, millis: Long): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when {
        date == today -> DateFormat.getTimeFormat(context).format(Date(millis))
        date == today.minusDays(1) -> str(R.string.yesterday)
        date.year == today.year -> DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(Locale.getDefault(), "dMMM")).format(date)
        else -> DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(Locale.getDefault(), "dMMMy")).format(date)
    }
}
