// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone.ui.screens

import dev.liquidphone.ui.glass.TitleTopPadding
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.liquidphone.DevTools
import dev.liquidphone.R
import dev.liquidphone.core.dial.DialBuffer
import dev.liquidphone.core.dial.DialFormat
import dev.liquidphone.core.dial.DialSearch
import dev.liquidphone.core.dial.T9
import dev.liquidphone.core.model.ContactSummary
import dev.liquidphone.core.sim.CallTarget
import dev.liquidphone.data.Repo
import dev.liquidphone.data.Stores
import dev.liquidphone.ui.components.ContactRow
import dev.liquidphone.ui.components.NoticeCard
import dev.liquidphone.ui.glass.GlassCard
import dev.liquidphone.ui.glass.GlassChip
import dev.liquidphone.ui.glass.GlassIconButton
import dev.liquidphone.ui.glass.LocalGlass
import dev.liquidphone.ui.glass.styled
import dev.liquidphone.util.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val KEY_NAMES = mapOf(
    '1' to R.string.key_1, '2' to R.string.key_2, '3' to R.string.key_3, '4' to R.string.key_4, '5' to R.string.key_5,
    '6' to R.string.key_6, '7' to R.string.key_7, '8' to R.string.key_8, '9' to R.string.key_9, '0' to R.string.key_0,
    '*' to R.string.key_star, '#' to R.string.key_hash,
)

/** Pestaña del teclado: lo escrito, lo que coincide en la agenda, las teclas y los botones de llamar. */
@Composable
fun DialTab(padding: PaddingValues, onSettings: () -> Unit, onContact: (Long) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val book by Repo.book.collectAsStateWithLifecycle()
    val numbers by Repo.numbers.collectAsStateWithLifecycle()
    val settings by Stores.settings.state.collectAsStateWithLifecycle()
    val pendingDial by Repo.pendingDial.collectAsStateWithLifecycle()
    LaunchedEffect(pendingDial) {
        pendingDial?.let {
            text = DialBuffer.sanitize(it)
            Repo.requestDial(null)
        }
    }
    val digits = text.filter(Char::isDigit)
    val results by produceState(emptyList<ContactSummary>(), digits, book) {
        value = withContext(Dispatchers.Default) { DialSearch.search(digits, book.entries).take(50) }
    }
    val format = remember(settings.defaultCountry) { DialFormat(settings.defaultCountry.ifEmpty { "AR" }) }
    val shown = remember(text, format) { format.format(text) }
    val haptics = LocalHapticFeedback.current
    fun tap() {
        if (settings.keypadHaptics) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    val onKey: (Char) -> Unit = { tap(); text = DialBuffer.type(text, it) }
    val onPlus: () -> Unit = { tap(); text = DialBuffer.plus(text) }
    val onBackspace: () -> Unit = { tap(); text = DialBuffer.backspace(text) }
    val onClear: () -> Unit = { tap(); text = DialBuffer.clear() }

    BoxWithConstraints(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
        // En horizontal no entran las cuatro filas de teclas debajo de la lista.
        if (maxHeight < 560.dp) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f).fillMaxSize()) {
                    NumberDisplay(shown, text.isNotEmpty(), onBackspace, onClear, onSettings)
                    DialResults(text, results, numbers::format, onContact, Modifier.weight(1f))
                    CallButtons(text)
                }
                Keypad(onKey, onPlus, Modifier.width(280.dp), keyHeight = 48.dp)
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(top = TitleTopPadding), horizontalArrangement = Arrangement.End) {
                    GlassIconButton(Icons.Rounded.Settings, str(R.string.settings), onSettings)
                }
                DialResults(text, results, numbers::format, onContact, Modifier.weight(1f))
                NumberDisplay(shown, text.isNotEmpty(), onBackspace, onClear, null)
                Keypad(onKey, onPlus, Modifier.fillMaxWidth(), keyHeight = 60.dp)
                Spacer(Modifier.height(10.dp))
                CallButtons(text)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NumberDisplay(shown: String, hasText: Boolean, onBackspace: () -> Unit, onClear: () -> Unit, onSettings: (() -> Unit)?) {
    val p = LocalGlass.current
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onSettings != null) GlassIconButton(Icons.Rounded.Settings, str(R.string.settings), onSettings) else Spacer(Modifier.width(48.dp))
        Text(
            shown,
            Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            fontSize = if (shown.length > 14) 24.sp else 32.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.StartEllipsis,
            color = p.content,
        )
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .then(
                    if (hasText) {
                        Modifier.combinedClickable(
                            role = Role.Button,
                            onLongClickLabel = str(R.string.key_clear_all),
                            onLongClick = onClear,
                            onClick = onBackspace,
                        )
                    } else {
                        Modifier
                    },
                )
                .semantics { if (hasText) contentDescription = str(R.string.key_backspace) },
            contentAlignment = Alignment.Center,
        ) {
            if (hasText) Icon(styled(Icons.AutoMirrored.Rounded.Backspace), null, Modifier.size(24.dp), tint = p.contentSecondary)
        }
    }
}

/** Una sola superficie de cristal para las 12 teclas: las teclas son planas. */
@Composable
private fun Keypad(onKey: (Char) -> Unit, onPlus: () -> Unit, modifier: Modifier, keyHeight: Dp) {
    GlassCard(modifier) {
        for (row in listOf("123", "456", "789", "*0#")) {
            Row(Modifier.fillMaxWidth()) {
                for (key in row) {
                    Key(key, onKey, if (key == '0') onPlus else null, Modifier.weight(1f), keyHeight)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Key(key: Char, onKey: (Char) -> Unit, onLong: (() -> Unit)?, modifier: Modifier, height: Dp) {
    val p = LocalGlass.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val letters = if (key == '0') "+" else T9.KEY_LETTERS[key].orEmpty()
    val name = str(KEY_NAMES.getValue(key))
    Column(
        modifier
            .height(height)
            .clip(RoundedCornerShape(18.dp))
            .background(if (pressed) p.content.copy(alpha = 0.12f) else p.content.copy(alpha = 0f))
            .combinedClickable(
                interaction,
                null,
                role = Role.Button,
                onLongClickLabel = if (onLong != null) str(R.string.key_plus) else null,
                onLongClick = onLong,
                onClick = { onKey(key) },
            )
            .clearAndSetSemantics { contentDescription = name },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(key.toString(), fontSize = if (height < 56.dp) 22.sp else 28.sp, color = p.content, fontWeight = FontWeight.Medium)
        // La línea se reserva también sin letras, para que todos los dígitos queden alineados.
        if (height >= 56.dp) {
            Text(letters.ifEmpty { " " }, fontSize = 11.sp, color = p.contentSecondary, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun DialResults(text: String, results: List<ContactSummary>, formatNumber: (String) -> String, onContact: (Long) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val toast = LocalToast.current
    val caller = LocalCaller.current
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
        if (text.isEmpty()) {
            item("avisos") { DialNotices() }
        } else if (text.count(Char::isDigit) >= 3 && Repo.contactFor(text) == null) {
            item("agregar") {
                GlassChip(
                    str(R.string.add_to_contacts),
                    selected = false,
                    onClick = { if (!addToContacts(context, text)) toast(str(R.string.no_contacts_app)) },
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
            }
        }
        items(results, key = { it.id }) { contact ->
            val phone = contact.phones.firstOrNull()
            ContactRow(
                contact.displayName,
                onClick = { onContact(contact.id) },
                subtitle = phone?.let(formatNumber),
                photoUri = contact.photoUri,
                trailing = phone?.let {
                    { RowCallButton(str(R.string.call_contact, contact.displayName)) { caller(phone, CallTarget.UsePolicy) } }
                },
            )
        }
    }
}

/** Botón plano de llamar al final de una fila, de 48 dp. */
@Composable
fun RowCallButton(description: String, onClick: () -> Unit) {
    val p = LocalGlass.current
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .combinedClickableButton(description, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(styled(Icons.Rounded.Call), null, Modifier.size(22.dp), tint = p.accentFg)
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableButton(description: String, onClick: () -> Unit): Modifier =
    combinedClickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description }

/** Lo que hay que saber antes de escribir: datos ficticios y permisos que faltan, con su motivo. */
@Composable
private fun DialNotices() {
    val tick by Repo.permissionTick.collectAsStateWithLifecycle()
    var hideContacts by rememberSaveable { mutableStateOf(false) }
    var hideChips by rememberSaveable { mutableStateOf(false) }
    val contacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { Repo.start() }
    val chips = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { Repo.start() }
    val needContacts = remember(tick) { !Repo.granted(Manifest.permission.READ_CONTACTS) }
    val needChips = remember(tick) { !Repo.granted(Manifest.permission.READ_PHONE_STATE) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DevTools.FakeDataBanner()
        if (needChips && !hideChips) {
            NoticeCard(str(R.string.permission_chips_text), str(R.string.permission_continue), { chips.launch(Manifest.permission.READ_PHONE_STATE) }, str(R.string.dismiss), { hideChips = true })
        }
        if (needContacts && !hideContacts) {
            NoticeCard(str(R.string.permission_t9_text), str(R.string.permission_continue), { contacts.launch(Manifest.permission.READ_CONTACTS) }, str(R.string.dismiss), { hideContacts = true })
        }
    }
}
