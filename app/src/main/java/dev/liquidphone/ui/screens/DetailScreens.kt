// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone.ui.screens

import android.provider.ContactsContract
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.input.KeyboardType
import dev.liquidphone.ui.glass.GlassTextField
import dev.liquidphone.ui.glass.SettingRow
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import dev.liquidphone.core.model.ContactEvent
import dev.liquidphone.core.model.Email
import dev.liquidphone.core.model.EventKind
import dev.liquidphone.core.model.PostalAddress
import dev.liquidphone.ui.glass.GlassIconButton
import dev.liquidphone.ui.glass.styled
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
            // Con un solo chip, devolver con el original es lo mismo que el botón de llamar.
            if (sims.multiple) {
                GlassButton(
                    if (original != null) str(R.string.call_back_with, simLabel(original)) else str(R.string.call_back_original),
                    { caller(group.number, CallTarget.Original(group.account)) },
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
            }
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
        if (latest.callable) BlockButton(group.number)
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

/**
 * Ficha de un contacto, con el mismo orden y las mismas filas que la de Liquid Contacts, y lo propio
 * de esta app: elegir número y chip, llamar y ver el historial. Acá no se escribe en la agenda: la
 * estrella, abrir y editar llevan a Liquid Contacts, o a la app de contactos que haya.
 */
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
    var picked by rememberSaveable { mutableStateOf<Int?>(null) }
    val current = contact

    fun openInContactsApp(action: String) {
        val uri = Repo.contacts.lookupUri(contactId)
        val intent = uri?.let { Intent(action).setDataAndType(it, ContactsContract.Contacts.CONTENT_ITEM_TYPE) }
        if (intent == null || !openInContacts(context, intent)) toast(str(R.string.no_contacts_app))
    }
    fun launch(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast(str(R.string.no_app))
        }
    }

    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        BackTitle("", onBack) {
            if (current != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassIconButton(
                        if (current.starred) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                        str(if (current.starred) R.string.favorite_remove_in_contacts else R.string.favorite_add_in_contacts),
                        { openInContactsApp(Intent.ACTION_VIEW) },
                    )
                    GlassIconButton(Icons.AutoMirrored.Rounded.OpenInNew, str(R.string.open_in_contacts), { openInContactsApp(Intent.ACTION_VIEW) })
                    GlassIconButton(Icons.Rounded.Edit, str(R.string.edit_contact), { openInContactsApp(Intent.ACTION_EDIT) })
                }
            }
        }
        if (current == null) return@Column

        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ContactAvatar(current.displayName, size = 112.dp, photoUri = book.rows.firstOrNull { it.id == contactId }?.photoUri)
            Text(
                current.displayName,
                Modifier.padding(top = 14.dp).semantics { heading() },
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = p.content,
                textAlign = TextAlign.Center,
            )
            val work = listOfNotNull(current.jobTitle, current.organization).filter { it.isNotBlank() }.joinToString(" · ")
            if (work.isNotEmpty()) Text(work, fontSize = 15.sp, color = p.contentSecondary, textAlign = TextAlign.Center)
            if (!current.nickname.isNullOrBlank()) Text("“${current.nickname}”", fontSize = 15.sp, color = p.contentSecondary)
        }

        val phones = current.phones
        if (phones.isEmpty()) {
            Text(str(R.string.contact_no_phones), color = p.contentSecondary, fontSize = 15.sp, modifier = Modifier.padding(vertical = 12.dp))
        } else {
            val selected = (picked ?: phones.indexOfFirst { it.primary }).takeIf { it in phones.indices } ?: 0
            SectionLabel(str(R.string.phones))
            GlassCard {
                phones.forEachIndexed { i, ph ->
                    val line = numbers.format(ph.number)
                    PhoneRow(line, phoneLabel(ph), selected = i == selected, canPick = phones.size > 1, onPick = { picked = i }) {
                        GlassIconButton(
                            Icons.AutoMirrored.Rounded.Message,
                            str(R.string.message_number, line),
                            { launch(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", ph.number, null))) },
                            size = 44.dp,
                        )
                    }
                }
            }
            CallButtons(phones[selected].number, Modifier.padding(top = 12.dp, bottom = 4.dp))
            val history = remember(rows, phones, numbers) { historyOf(rows, phones, numbers).take(30) }
            if (history.isNotEmpty()) {
                SectionLabel(str(R.string.history))
                GlassCard {
                    history.forEach { CallLine(it, sims.chips, showChip = sims.multiple) }
                }
            }
        }

        if (current.emails.isNotEmpty()) {
            SectionLabel(str(R.string.emails))
            GlassCard {
                current.emails.forEach { e ->
                    DetailRow(e.address, emailLabel(e)) { launch(Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", e.address, null))) }
                }
            }
        }
        if (current.addresses.isNotEmpty()) {
            SectionLabel(str(R.string.addresses))
            GlassCard {
                current.addresses.forEach { a ->
                    DetailRow(a.formatted, addressLabel(a)) { launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(a.formatted)))) }
                }
            }
        }
        if (current.events.isNotEmpty()) {
            SectionLabel(str(R.string.dates))
            GlassCard { current.events.forEach { e -> DetailRow(eventDate(e), eventLabel(e), null) } }
        }
        if (current.websites.isNotEmpty()) {
            SectionLabel(str(R.string.websites))
            GlassCard {
                current.websites.forEach { w ->
                    val url = safeWebUri(w)
                    DetailRow(w, null, url?.let { { launch(Intent(Intent.ACTION_VIEW, it)) } })
                }
            }
        }
        if (current.groups.isNotEmpty()) {
            SectionLabel(str(R.string.labels_section))
            GlassCard { Text(current.groups.sorted().joinToString(" · "), Modifier.padding(vertical = 8.dp), fontSize = 16.sp, color = p.content) }
        }
        if (!current.note.isNullOrBlank()) {
            SectionLabel(str(R.string.note))
            GlassCard { Text(current.note!!, Modifier.padding(vertical = 8.dp), fontSize = 16.sp, color = p.content) }
        }
        Spacer(Modifier.padding(top = 24.dp))
    }
}

/** Un número de la ficha. Con varios, tocarlo elige con cuál llamar; nunca llama solo. */
@Composable
private fun PhoneRow(number: String, label: String, selected: Boolean, canPick: Boolean, onPick: () -> Unit, trailing: @Composable () -> Unit) {
    val p = LocalGlass.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .then(
                    if (canPick) {
                        Modifier.selectable(selected, remember { MutableInteractionSource() }, null, role = Role.RadioButton, onClick = onPick)
                    } else {
                        Modifier
                    },
                )
                .semantics(mergeDescendants = true) { contentDescription = "$number, $label" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (canPick) {
                Icon(
                    styled(if (selected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked),
                    null,
                    Modifier.padding(end = 12.dp).size(22.dp),
                    tint = if (selected) p.accentFg else p.contentSecondary,
                )
            }
            Column {
                Text(number, fontSize = 17.sp, color = p.content)
                Text(label, fontSize = 13.sp, color = p.contentSecondary)
            }
        }
        trailing()
    }
}

/** Fila de un dato: valor grande, tipo abajo; tocarla hace la acción. La misma que en Liquid Contacts. */
@Composable
private fun DetailRow(value: String, label: String?, onClick: (() -> Unit)?) {
    val p = LocalGlass.current
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(remember { MutableInteractionSource() }, null, role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = listOfNotNull(value, label).joinToString(", ") },
        verticalArrangement = Arrangement.Center,
    ) {
        Text(value, fontSize = 17.sp, color = p.content)
        if (!label.isNullOrBlank()) Text(label, fontSize = 13.sp, color = p.contentSecondary)
    }
}

/** Solo http y https: una agenda puede traer cualquier cosa como "sitio web" (javascript:, intent:). */
private fun safeWebUri(url: String): Uri? {
    val text = url.trim()
    if (text.isEmpty() || text.any(Char::isWhitespace)) return null
    return when (Uri.parse(text).scheme?.lowercase()) {
        null -> Uri.parse("https://$text")
        "http", "https" -> Uri.parse(text)
        else -> null
    }
}

private fun emailLabel(email: Email): String =
    email.label?.takeIf { email.kind == LabelKind.CUSTOM && it.isNotBlank() } ?: str(
        when (email.kind) {
            LabelKind.HOME -> R.string.email_personal
            LabelKind.WORK -> R.string.phone_work
            LabelKind.MOBILE -> R.string.phone_mobile
            else -> R.string.phone_other
        },
    )

private fun addressLabel(address: PostalAddress): String =
    address.label?.takeIf { address.kind == LabelKind.CUSTOM && it.isNotBlank() } ?: str(
        when (address.kind) {
            LabelKind.HOME -> R.string.phone_home
            LabelKind.WORK -> R.string.phone_work
            else -> R.string.phone_other
        },
    )

private fun eventLabel(event: ContactEvent): String =
    event.label?.takeIf { event.kind == EventKind.CUSTOM && it.isNotBlank() } ?: str(
        when (event.kind) {
            EventKind.BIRTHDAY -> R.string.event_birthday
            EventKind.ANNIVERSARY -> R.string.event_anniversary
            else -> R.string.event_other
        },
    )

/** Fecha en el formato del idioma del teléfono ("14 de marzo de 1990"); sin año, solo día y mes. */
private fun eventDate(event: ContactEvent): String {
    val locale = java.util.Locale.getDefault()
    return if (event.year != null) {
        java.time.LocalDate.of(event.year!!, event.month, event.day).format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG).withLocale(locale))
    } else {
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "dMMMM")
        java.time.MonthDay.of(event.month, event.day).format(java.time.format.DateTimeFormatter.ofPattern(pattern, locale))
    }
}

/** Bloquear o desbloquear este número. Solo aparece siendo el marcador predeterminado. */
@Composable
private fun BlockButton(number: String) {
    val dialog = LocalDialog.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val p = LocalGlass.current
    var state by remember(number) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(number) {
        state = withContext(Dispatchers.IO) { if (Repo.blocked.available()) Repo.blocked.isBlocked(number) else null }
    }
    val blocked = state ?: return
    fun apply(block: Boolean) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { if (block) Repo.blocked.block(number) else Repo.blocked.unblock(number) }
            if (ok) state = block else toast(str(R.string.blocked_failed))
        }
    }
    GlassButton(
        str(if (blocked) R.string.unblock_number else R.string.block_number),
        {
            if (blocked) {
                apply(false)
            } else {
                dialog(
                    DialogSpec(str(R.string.block_title)) { dismiss ->
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(str(R.string.block_text), color = p.content, fontSize = 14.sp)
                            DangerButton(str(R.string.block_number), { dismiss(); apply(true) }, Modifier.fillMaxWidth())
                            GlassButton(str(R.string.cancel), dismiss, Modifier.fillMaxWidth())
                        }
                    },
                )
            }
        },
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
    )
}

/** La lista de bloqueados: agregar y sacar. */
@Composable
fun BlockedScreen(padding: PaddingValues, onBack: () -> Unit) {
    val p = LocalGlass.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val numbers by Repo.numbers.collectAsStateWithLifecycle()
    var available by remember { mutableStateOf(true) }
    var list by remember { mutableStateOf(emptyList<String>()) }
    var typed by remember { mutableStateOf("") }
    suspend fun reload() {
        val (a, l) = withContext(Dispatchers.IO) { Repo.blocked.available() to Repo.blocked.list() }
        available = a
        list = l
    }
    LaunchedEffect(Unit) { reload() }
    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        BackTitle(str(R.string.blocked_title), onBack)
        Text(str(if (available) R.string.blocked_hint else R.string.blocked_needs_role), fontSize = 13.sp, color = p.contentSecondary, modifier = Modifier.padding(bottom = 10.dp))
        if (!available) return@Column
        GlassCard {
            GlassTextField(
                typed,
                { typed = it },
                str(R.string.blocked_add_hint),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
            GlassButton(
                str(R.string.block_number),
                {
                    val number = typed.trim()
                    scope.launch {
                        val ok = number.any(Char::isDigit) && withContext(Dispatchers.IO) { Repo.blocked.block(number) }
                        if (ok) {
                            typed = ""
                            reload()
                        } else {
                            toast(str(R.string.blocked_failed))
                        }
                    }
                },
                Modifier.fillMaxWidth().padding(top = 10.dp),
                enabled = typed.any(Char::isDigit),
            )
        }
        Spacer(Modifier.padding(top = 12.dp))
        if (list.isEmpty()) {
            Text(str(R.string.blocked_empty), fontSize = 14.sp, color = p.contentSecondary)
        } else {
            GlassCard {
                list.forEach { number ->
                    SettingRow(
                        numbers.format(number),
                        trailing = {
                            GlassButton(str(R.string.unblock_number), {
                                scope.launch {
                                    withContext(Dispatchers.IO) { Repo.blocked.unblock(number) }
                                    reload()
                                }
                            })
                        },
                    )
                }
            }
        }
    }
}
