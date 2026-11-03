// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone.ui.screens

import android.Manifest
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.liquidphone.R
import dev.liquidphone.core.model.ContactSummary
import dev.liquidphone.core.sim.CallTarget
import dev.liquidphone.core.text.ContactSearch
import dev.liquidphone.core.text.SpanishOrder
import dev.liquidphone.data.Repo
import dev.liquidphone.data.Stores
import dev.liquidphone.ui.components.AlphabetIndex
import dev.liquidphone.ui.components.ContactRow
import dev.liquidphone.ui.components.LetterHeader
import dev.liquidphone.ui.components.NoticeCard
import dev.liquidphone.ui.components.SearchField
import dev.liquidphone.ui.glass.GlassIconButton
import dev.liquidphone.ui.glass.ScreenTitle
import dev.liquidphone.util.plural
import dev.liquidphone.util.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Una fila de contacto con el botón de llamar. Con varios números, se elige en el detalle. */
@Composable
private fun PhoneContactRow(contact: ContactSummary, format: (String) -> String, onOpen: (Long) -> Unit) {
    val caller = LocalCaller.current
    val phone = contact.phones.firstOrNull()
    ContactRow(
        contact.displayName,
        onClick = { onOpen(contact.id) },
        subtitle = when {
            phone == null -> null
            contact.phones.size > 1 -> plural(R.plurals.phone_count, contact.phones.size)
            else -> format(phone)
        },
        photoUri = contact.photoUri,
        trailing = phone?.let {
            {
                RowCallButton(str(R.string.call_contact, contact.displayName)) {
                    if (contact.phones.size > 1) onOpen(contact.id) else caller(phone, CallTarget.UsePolicy)
                }
            }
        },
    )
}

@Composable
fun FavoritesTab(padding: PaddingValues, onSettings: () -> Unit, onOpen: (Long) -> Unit) {
    PermissionGate(
        Manifest.permission.READ_CONTACTS,
        str(R.string.permission_contacts_title),
        str(R.string.permission_contacts_text),
        str(R.string.tab_favorites),
        Modifier.padding(padding),
    ) {
        val book by Repo.book.collectAsStateWithLifecycle()
        val numbers by Repo.numbers.collectAsStateWithLifecycle()
        val favorites = remember(book) { book.favorites }
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            Box(Modifier.padding(horizontal = 16.dp)) {
                ScreenTitle(str(R.string.tab_favorites), actions = { GlassIconButton(Icons.Rounded.Settings, str(R.string.settings), onSettings) })
            }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
                contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
            ) {
                if (favorites.isEmpty()) {
                    item("vacio") { EmptyState(str(R.string.favorites_empty), str(R.string.favorites_empty_hint), Modifier.padding(top = 40.dp)) }
                }
                items(favorites, key = { it.id }) { PhoneContactRow(it, numbers::format, onOpen) }
            }
        }
    }
}

@Composable
fun ContactsTab(padding: PaddingValues, onSettings: () -> Unit, onAreaCode: () -> Unit, onOpen: (Long) -> Unit) {
    PermissionGate(
        Manifest.permission.READ_CONTACTS,
        str(R.string.permission_contacts_title),
        str(R.string.permission_contacts_text),
        str(R.string.tab_contacts),
        Modifier.padding(padding),
    ) {
        val book by Repo.book.collectAsStateWithLifecycle()
        val numbers by Repo.numbers.collectAsStateWithLifecycle()
        val settings by Stores.settings.state.collectAsStateWithLifecycle()
        var query by rememberSaveable { mutableStateOf("") }
        val focus = LocalFocusManager.current
        val list = rememberLazyListState()
        val scope = rememberCoroutineScope()
        val open: (Long) -> Unit = { focus.clearFocus(); onOpen(it) }

        val shown by produceState(book.rows, query, book, numbers) {
            value = withContext(Dispatchers.Default) {
                val search = ContactSearch(query, numbers::searchForms)
                if (search.isEmpty) book.rows else book.rows.filter { search.matches(it.displayName, it.phones, it.emails) }
            }
        }
        val missingArea by produceState(0, book, numbers) {
            value = withContext(Dispatchers.Default) { book.rows.count { c -> c.phones.any(numbers::missingAreaCode) } }
        }
        val grouped = remember(shown) { shown.groupBy { SpanishOrder.section(it.displayName) } }
        val showNotice = missingArea > 0 && settings.defaultAreaCode.isEmpty() && !settings.areaCodeNoticeDismissed && query.isEmpty()

        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            Box(Modifier.padding(horizontal = 16.dp)) {
                ScreenTitle(str(R.string.tab_contacts), actions = { GlassIconButton(Icons.Rounded.Settings, str(R.string.settings), onSettings) })
            }
            SearchField(query, { query = it }, str(R.string.search_contacts), str(R.string.clear_search), Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    Modifier.fillMaxSize().padding(start = 8.dp, end = 28.dp),
                    state = list,
                    contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
                ) {
                    if (showNotice) {
                        item("aviso") {
                            NoticeCard(
                                plural(R.plurals.area_code_notice, missingArea),
                                str(R.string.area_code_notice_action),
                                onAreaCode,
                                str(R.string.dismiss),
                                { Stores.settings.update { it.copy(areaCodeNoticeDismissed = true) } },
                                Modifier.padding(8.dp),
                            )
                        }
                    }
                    if (shown.isEmpty()) {
                        item("vacio") { EmptyState(str(R.string.contacts_empty), str(R.string.contacts_empty_hint), Modifier.padding(top = 40.dp)) }
                    }
                    grouped.forEach { (letter, contacts) ->
                        item("letra-$letter") { LetterHeader(letter) }
                        items(contacts, key = { it.id }) { PhoneContactRow(it, numbers::format, open) }
                    }
                }
                if (query.isEmpty() && shown.isNotEmpty()) {
                    AlphabetIndex(
                        SpanishOrder.SECTIONS,
                        grouped.keys,
                        onLetter = { letter ->
                            var index = if (showNotice) 1 else 0
                            for ((key, contacts) in grouped) {
                                if (key == letter) {
                                    scope.launch { list.scrollToItem(index) }
                                    break
                                }
                                index += contacts.size + 1
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterEnd).padding(bottom = padding.calculateBottomPadding()),
                        jumpLabel = { str(R.string.jump_to, it) },
                    )
                }
            }
        }
    }
}
