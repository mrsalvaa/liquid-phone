// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone.ui.screens

import android.Manifest
import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.liquidphone.DevTools
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
import dev.liquidphone.ui.glass.GlassChip
import dev.liquidphone.ui.glass.GlassIconButton
import dev.liquidphone.ui.glass.LocalGlass
import dev.liquidphone.ui.glass.ScreenTitle
import dev.liquidphone.ui.glass.styled
import dev.liquidphone.util.str
import kotlinx.coroutines.Dispatchers
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

    val present = remember(sims) { sims.chips.mapNotNull { it.account }.toSet() }
    val hasGone = remember(rows, present) { rows.any { it.account == null || it.account !in present } }
    val selectedChip = sims.chips.firstOrNull { it.slotIndex == chipFilter }
    val filter: ChipFilter = when {
        chipFilter == FILTER_GONE -> ChipFilter.NoLongerPresent(present)
        selectedChip?.account != null -> ChipFilter.Only(selectedChip.account!!)
        else -> ChipFilter.All
    }
    val items by produceState<List<RecentItem>?>(null, rows, book, numbers, filter, missedOnly) {
        value = withContext(Dispatchers.Default) {
            val filtered = CallLogTools.filter(rows, filter).let { list -> if (missedOnly) list.filter { it.type == CallType.MISSED } else list }
            CallLogTools.group(filtered, numbers).map { group ->
                RecentItem(group, if (group.latest.callable) Repo.contactFor(group.number) else null, shownNumber(group.latest, numbers::format))
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
        Box(Modifier.padding(horizontal = 16.dp)) {
            ScreenTitle(str(R.string.tab_recents), actions = { GlassIconButton(Icons.Rounded.Settings, str(R.string.settings), onSettings) })
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlassChip(str(R.string.filter_missed), missedOnly, { missedOnly = !missedOnly })
            if (sims.multiple || hasGone) {
                GlassChip(str(R.string.filter_all_sims), chipFilter == FILTER_ALL, { chipFilter = FILTER_ALL })
                sims.chips.forEach { chip ->
                    GlassChip(simLabel(chip), chipFilter == chip.slotIndex, { chipFilter = chip.slotIndex })
                }
                if (hasGone) GlassChip(str(R.string.filter_other_sim), chipFilter == FILTER_GONE, { chipFilter = FILTER_GONE })
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = padding.calculateBottomPadding()),
        ) {
            item("aviso") { Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) { DevTools.FakeDataBanner() } }
            val list = items
            if (list != null && list.isEmpty()) {
                item("vacio") { EmptyState(str(R.string.recents_empty), str(R.string.recents_empty_hint), Modifier.padding(top = 40.dp)) }
            }
            items(list.orEmpty(), key = { it.group.key }) { item ->
                RecentRow(
                    item,
                    chipOf(item.group.account, sims.chips),
                    showChip = sims.multiple || item.group.account !in present,
                    onCall = { if (item.group.latest.callable) caller(item.group.number, CallTarget.UsePolicy) else onOpen(item.group) },
                    onOpen = { onOpen(item.group) },
                )
            }
        }
    }
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

/** Fila plana: sin cristal, como en Contactos. Tocar llama con la política; el ícono abre el detalle. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentRow(item: RecentItem, chip: SimChip?, showChip: Boolean, onCall: () -> Unit, onOpen: () -> Unit) {
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
                onClickLabel = if (latest.callable) str(R.string.call) else str(R.string.details),
                onLongClickLabel = str(R.string.details),
                onLongClick = onOpen,
                onClick = onCall,
            )
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContactAvatar(item.contact?.displayName ?: "", photoUri = item.contact?.photoUri)
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
