// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.liquidphone.R
import dev.liquidphone.core.calls.CallGroup
import dev.liquidphone.core.calls.CallRow
import dev.liquidphone.core.model.Contact
import dev.liquidphone.core.model.LabelKind
import dev.liquidphone.core.model.Phone
import dev.liquidphone.core.phone.PhoneNumbers
import dev.liquidphone.core.sim.CallTarget
import dev.liquidphone.core.sim.SimChip
import dev.liquidphone.data.Repo
import dev.liquidphone.ui.components.ContactAvatar
import dev.liquidphone.ui.components.DangerButton
import dev.liquidphone.ui.glass.DialogSpec
import dev.liquidphone.ui.glass.GlassButton
import dev.liquidphone.ui.glass.GlassCard
import dev.liquidphone.ui.glass.GlassChip
import dev.liquidphone.ui.glass.LocalDialog
import dev.liquidphone.ui.glass.LocalGlass
import dev.liquidphone.ui.glass.SectionLabel
import dev.liquidphone.util.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Una llamada del historial: flecha, fecha, duración y chip. */
@Composable
private fun CallLine(row: CallRow, chips: List<SimChip>, showChip: Boolean) {
    val p = LocalGlass.current
    val context = LocalContext.current
    val chip = chipOf(row.account, chips)
    val time = callTime(context, row.dateMillis)
    val duration = row.durationSeconds.takeIf { it > 0 }?.let { "%d:%02d".format(it / 60, it % 60) }
    val chipText = if (!showChip) null else chip?.let(::simLabel) ?: str(R.string.sim_gone)
    val text = listOfNotNull(callTypeLabel(row.type), time, duration).joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).semantics(mergeDescendants = true) {
            contentDescription = listOfNotNull(callTypeLabel(row.type), time, duration, chipText).joinToString(", ")
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CallTypeIcon(row.type)
        Spacer(Modifier.width(10.dp))
        Text(text, Modifier.weight(1f), fontSize = 14.sp, color = p.content)
        if (showChip) SimBadge(chip)
    }
}

/**
 * Detalle de una entrada de recientes: los dos botones de llamar, devolver la llamada con el chip
 * original, agregar a contactos y borrar.
 */
@Composable
fun CallDetailScreen(group: CallGroup, padding: PaddingValues, onBack: () -> Unit, onContact: (Long) -> Unit) {
    val p = LocalGlass.current
    val context = LocalContext.current
    val toast = LocalToast.current
    val dialog = LocalDialog.current
    val caller = LocalCaller.current
    val scope = rememberCoroutineScope()
    val numbers by Repo.numbers.collectAsStateWithLifecycle()
    val sims by Repo.sims.state.collectAsStateWithLifecycle()
    val book by Repo.book.collectAsStateWithLifecycle()
    val latest = group.latest
    val contact = remember(book, group) { if (latest.callable) Repo.contactFor(group.number) else null }
    val shown = remember(numbers, group) { shownNumber(latest, numbers::format) }
    val original = chipOf(group.account, sims.chips)

    fun delete() {
        scope.launch {
            withContext(Dispatchers.IO) { Repo.deleteCalls(group.ids) }
            onBack()
        }
    }
    fun confirmDelete() = dialog(
        DialogSpec(str(R.string.delete_entry_title)) { dismiss ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(str(R.string.delete_entry_text), color = p.content, fontSize = 14.sp)
                DangerButton(str(R.string.delete), { dismiss(); delete() }, Modifier.fillMaxWidth())
                GlassButton(str(R.string.cancel), dismiss, Modifier.fillMaxWidth())
            }
        },
    )
    val writeLog = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) confirmDelete() else toast(str(R.string.permission_delete_denied))
    }

    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        BackTitle(contact?.displayName ?: shown, onBack, subtitle = shown.takeIf { contact != null })
        if (latest.callable) {
            CallButtons(group.number, Modifier.padding(vertical = 8.dp))
            GlassButton(
                if (original != null) str(R.string.call_back_with, simLabel(original)) else str(R.string.call_back_original),
                { caller(group.number, CallTarget.Original(group.account)) },
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
            if (contact != null) {
                GlassButton(str(R.string.open_contact), { onContact(contact.id) }, Modifier.fillMaxWidth().padding(bottom = 8.dp))
            } else {
                GlassButton(
                    str(R.string.add_to_contacts),
                    { if (!addToContacts(context, group.number)) toast(str(R.string.no_contacts_app)) },
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    icon = Icons.Rounded.PersonAdd,
                )
            }
        }
        if (group.account != null && original == null) {
            Text(str(R.string.sim_gone_hint), fontSize = 13.sp, color = p.contentSecondary, modifier = Modifier.padding(bottom = 8.dp))
        }
        SectionLabel(str(R.string.history))
        GlassCard {
            group.rows.forEach { CallLine(it, sims.chips, showChip = sims.multiple || original == null) }
        }
        Spacer(Modifier.padding(top = 12.dp))
        DangerButton(
            str(R.string.delete_entry),
            {
                if (Repo.granted(Manifest.permission.WRITE_CALL_LOG)) {
                    confirmDelete()
                } else {
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
            },
            Modifier.fillMaxWidth(),
            icon = Icons.Rounded.Delete,
        )
        Spacer(Modifier.padding(top = 24.dp))
    }
}

private fun phoneLabel(phone: Phone): String = str(
    when (phone.kind) {
        LabelKind.MOBILE -> R.string.phone_mobile
        LabelKind.HOME -> R.string.phone_home
        LabelKind.WORK -> R.string.phone_work
        LabelKind.MAIN -> R.string.phone_main
        LabelKind.FAX_WORK, LabelKind.FAX_HOME -> R.string.phone_fax
        LabelKind.PAGER -> R.string.phone_pager
        else -> R.string.phone_other
    },
).let { if (phone.kind == LabelKind.CUSTOM && !phone.label.isNullOrBlank()) phone.label!! else it }

/** Las llamadas con alguno de estos números, según la misma coincidencia de siempre. */
private fun historyOf(rows: List<CallRow>, phones: List<Phone>, numbers: PhoneNumbers): List<CallRow> {
    val keys = phones.map { numbers.exact(it.number) ?: it.number.filter(Char::isDigit) }.toSet()
    return rows.filter { it.callable && (numbers.exact(it.number) ?: it.number.filter(Char::isDigit)) in keys }
}

/** Detalle de un contacto: sus números, los dos botones de llamar y su historial. */
@Composable
fun ContactDetailScreen(contactId: Long, padding: PaddingValues, onBack: () -> Unit) {
    val p = LocalGlass.current
    val context = LocalContext.current
    val toast = LocalToast.current
    val book by Repo.book.collectAsStateWithLifecycle()
    val numbers by Repo.numbers.collectAsStateWithLifecycle()
    val sims by Repo.sims.state.collectAsStateWithLifecycle()
    val rows by Repo.calls.collectAsStateWithLifecycle()
    val contact by produceState<Contact?>(null, contactId, book) {
        value = withContext(Dispatchers.IO) { Repo.contacts.load(contactId) }
    }
    var selected by rememberSaveable { mutableStateOf(0) }
    val current = contact

    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        BackTitle(current?.displayName.orEmpty(), onBack)
        if (current == null) return@Column
        ContactAvatar(current.displayName, Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp), size = 88.dp, photoUri = book.rows.firstOrNull { it.id == contactId }?.photoUri)
        val phones = current.phones
        if (phones.isEmpty()) {
            Text(str(R.string.contact_no_phones), color = p.contentSecondary, fontSize = 15.sp, modifier = Modifier.padding(vertical = 12.dp))
        } else {
            val phone = phones[selected.coerceIn(phones.indices)]
            if (phones.size > 1) SectionLabel(str(R.string.phones))
            GlassCard {
                phones.forEachIndexed { i, ph ->
                    val line = numbers.format(ph.number)
                    if (phones.size > 1) {
                        GlassChip("$line · ${phoneLabel(ph)}", i == selected, { selected = i }, Modifier.padding(vertical = 4.dp))
                    } else {
                        Text(line, fontSize = 20.sp, color = p.content)
                        Text(phoneLabel(ph), fontSize = 13.sp, color = p.contentSecondary)
                    }
                }
            }
            CallButtons(phone.number, Modifier.padding(vertical = 10.dp))
            val history = remember(rows, phones, numbers) { historyOf(rows, phones, numbers).take(30) }
            if (history.isNotEmpty()) {
                SectionLabel(str(R.string.history))
                GlassCard {
                    history.forEach { CallLine(it, sims.chips, showChip = sims.multiple) }
                }
            }
        }
        GlassButton(
            str(R.string.open_in_contacts),
            {
                val uri = Repo.contacts.lookupUri(contactId)
                try {
                    if (uri != null) context.startActivity(Intent(Intent.ACTION_VIEW, uri)) else toast(str(R.string.no_contacts_app))
                } catch (e: ActivityNotFoundException) {
                    toast(str(R.string.no_contacts_app))
                }
            },
            Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        Spacer(Modifier.padding(top = 24.dp))
    }
}
