// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone.ui.screens

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import dev.liquidphone.ui.glass.GlassButton
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.liquidphone.R
import dev.liquidphone.core.phone.PhoneNumbers
import dev.liquidphone.core.sim.CallDecider
import dev.liquidphone.core.sim.SimPolicy
import dev.liquidphone.core.text.Fold
import dev.liquidphone.data.AppSettings
import dev.liquidphone.data.Repo
import dev.liquidphone.data.Stores
import dev.liquidphone.ui.components.SwitchRow
import dev.liquidphone.ui.glass.DialogSpec
import dev.liquidphone.ui.glass.GlassCard
import dev.liquidphone.ui.glass.GlassChip
import dev.liquidphone.ui.glass.GlassTextField
import dev.liquidphone.ui.glass.LocalDialog
import dev.liquidphone.ui.glass.LocalGlass
import dev.liquidphone.ui.glass.SectionLabel
import dev.liquidphone.ui.glass.SettingRow
import dev.liquidphone.ui.glass.styled
import dev.liquidphone.util.plural
import dev.liquidphone.util.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(padding: PaddingValues, onBack: () -> Unit, toAreaCode: Boolean = false, onBlocked: () -> Unit = {}) {
    val p = LocalGlass.current
    val dialog = LocalDialog.current
    val s by Stores.settings.state.collectAsStateWithLifecycle()
    val book by Repo.book.collectAsStateWithLifecycle()
    val sims by Repo.sims.state.collectAsStateWithLifecycle()
    val edit: ((AppSettings) -> AppSettings) -> Unit = { Stores.settings.update(it) }

    // Contactos con números sin código de área, y cuántos completaría el código escrito.
    var area by remember { mutableStateOf(s.defaultAreaCode) }
    var missing by remember { mutableIntStateOf(0) }
    var completed by remember { mutableIntStateOf(0) }
    LaunchedEffect(book, s.defaultCountry, area) {
        val country = s.defaultCountry.ifEmpty { "AR" }
        val bare = PhoneNumbers(country)
        val withArea = area.takeIf { it.isNotBlank() }?.let { PhoneNumbers(country, it) }
        withContext(Dispatchers.Default) {
            val affected = book.rows.filter { r -> r.phones.any(bare::missingAreaCode) }
            missing = affected.size
            completed = if (withArea == null) 0 else affected.count { r -> r.phones.filter(bare::missingAreaCode).all { withArea.exact(it) != null } }
        }
    }

    // Desde el aviso de la lista se llega directo al campo.
    val areaCodeField = remember { BringIntoViewRequester() }
    if (toAreaCode) {
        LaunchedEffect(Unit) {
            delay(250)
            areaCodeField.bringIntoView()
        }
    }

    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        BackTitle(str(R.string.settings), onBack)

        SectionLabel(str(R.string.appearance))
        AppearanceCard(s, edit)

        SectionLabel(str(R.string.default_dialer_section))
        DefaultDialerCard(onBlocked)

        SectionLabel(str(R.string.calls_section))
        GlassCard {
            // Siempre las dos ranuras, por si el chip principal todavía no está puesto.
            Text(str(R.string.primary_sim), fontSize = 16.sp)
            Text(str(R.string.primary_sim_hint), fontSize = 12.sp, color = p.contentSecondary, modifier = Modifier.padding(bottom = 4.dp))
            CallDecider.VALID_PRIMARY_SLOTS.forEach { slot ->
                val chip = sims.chips.firstOrNull { it.slotIndex == slot }
                val chosen = s.primarySimSlot == slot
                val state = listOfNotNull(
                    str(R.string.primary_sim_marked).takeIf { chosen },
                    str(R.string.primary_sim_absent).takeIf { chip == null && sims.known },
                )
                SettingRow(
                    chip?.let(::simName) ?: str(R.string.sim_label, slot + 1),
                    Modifier.semantics { selected = chosen },
                    subtitle = state.joinToString(" · ").ifEmpty { null },
                    onClick = { edit { it.copy(primarySimSlot = slot) } },
                    leading = chip?.let { { SimBadge(it, Modifier.padding(end = 12.dp)) } },
                    trailing = { if (chosen) Icon(styled(Icons.Rounded.Check), null, tint = p.accentFg) },
                )
            }
            Spacer(Modifier.height(10.dp))
            // Con un solo chip no hay nada que elegir.
            if (sims.multiple) {
                Text(str(R.string.sim_policy), fontSize = 16.sp)
                Text(str(R.string.sim_policy_hint), fontSize = 12.sp, color = p.contentSecondary, modifier = Modifier.padding(bottom = 4.dp))
                val policies = listOf(
                    SimPolicy.PRIMARY to (R.string.sim_policy_primary to R.string.sim_policy_primary_hint),
                    SimPolicy.ASK to (R.string.sim_policy_ask to R.string.sim_policy_ask_hint),
                    SimPolicy.SYSTEM_DEFAULT to (R.string.sim_policy_system to R.string.sim_policy_system_hint),
                    SimPolicy.LAST_USED to (R.string.sim_policy_last to R.string.sim_policy_last_hint),
                )
                policies.forEach { (policy, texts) ->
                    val chosen = s.simPolicy == policy.value
                    SettingRow(
                        str(texts.first),
                        Modifier.semantics { selected = chosen },
                        subtitle = str(texts.second),
                        onClick = { edit { it.copy(simPolicy = policy.value) } },
                        trailing = { if (chosen) Icon(styled(Icons.Rounded.Check), null, tint = p.accentFg) },
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            Text(str(R.string.start_tab), fontSize = 16.sp)
            FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(R.string.tab_keypad, R.string.tab_recents, R.string.tab_favorites).forEachIndexed { i, label ->
                    GlassChip(str(label), s.startTab == i, { edit { it.copy(startTab = i) } })
                }
            }
            SwitchRow(str(R.string.keypad_haptics), s.keypadHaptics, { on -> edit { it.copy(keypadHaptics = on) } })
        }

        SectionLabel(str(R.string.numbers))
        GlassCard {
            SettingRow(
                str(R.string.default_country),
                subtitle = countryName(s.defaultCountry) + " (+" + PhoneNumbers.callingCode(s.defaultCountry.ifEmpty { "AR" }) + ")",
                onClick = {
                    dialog(DialogSpec(str(R.string.default_country)) { dismiss -> CountryPicker { c -> edit { it.copy(defaultCountry = c) }; dismiss() } })
                },
            )
            Text(str(R.string.default_country_hint), fontSize = 12.sp, color = p.contentSecondary)
            // El código de área por defecto, por ahora, solo existe para Argentina.
            if (PhoneNumbers(s.defaultCountry.ifEmpty { "AR" }).supportsAreaCode) {
                Column(Modifier.bringIntoViewRequester(areaCodeField)) {
                    Spacer(Modifier.height(14.dp))
                    Text(str(R.string.default_area_code), fontSize = 16.sp)
                    GlassTextField(
                        area,
                        { v ->
                            val digits = v.filter(Char::isDigit).take(5)
                            area = digits
                            edit { it.copy(defaultAreaCode = digits.trimStart('0')) }
                        },
                        str(R.string.default_area_code_hint_field),
                        Modifier.padding(vertical = 6.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Text(
                        when {
                            missing == 0 -> str(R.string.area_code_none_missing)
                            area.isBlank() -> plural(R.plurals.area_code_missing, missing)
                            else -> plural(R.plurals.area_code_completes, missing, completed, missing)
                        },
                        fontSize = 13.sp,
                        color = p.contentSecondary,
                    )
                    Text(str(R.string.default_area_code_hint), Modifier.padding(top = 6.dp), fontSize = 12.sp, color = p.contentSecondary)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Ser el marcador predeterminado lo decide el usuario con el diálogo del sistema: nada lo pide solo.
 * Después se piden las notificaciones, que hacen falta para la entrante y las perdidas.
 */
@Composable
private fun DefaultDialerCard(onBlocked: () -> Unit) {
    val context = LocalContext.current
    val p = LocalGlass.current
    val tick by Repo.permissionTick.collectAsStateWithLifecycle()
    var held by remember { mutableStateOf(false) }
    LaunchedEffect(tick) { held = Repo.isDefaultDialer() }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val role = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        held = Repo.isDefaultDialer()
        Repo.start()
        if (held) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    GlassCard {
        Text(str(if (held) R.string.default_dialer_on else R.string.default_dialer_off), fontSize = 16.sp)
        Text(str(R.string.default_dialer_hint), fontSize = 12.sp, color = p.contentSecondary, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
        val manager = context.getSystemService(RoleManager::class.java)
        if (!held && manager?.isRoleAvailable(RoleManager.ROLE_DIALER) == true) {
            GlassButton(str(R.string.default_dialer_action), { role.launch(manager.createRequestRoleIntent(RoleManager.ROLE_DIALER)) }, Modifier.fillMaxWidth())
        } else if (held) {
            GlassButton(
                str(R.string.default_dialer_change),
                { context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) },
                Modifier.fillMaxWidth(),
            )
            SettingRow(str(R.string.blocked_title), Modifier.padding(top = 6.dp), onClick = onBlocked)
        }
    }
}

/** Nombre del país en el idioma del teléfono. */
fun countryName(code: String): String =
    Locale("", code.ifEmpty { "AR" }).getDisplayCountry(Locale.getDefault()).ifEmpty { code }

/** Lista de países con búsqueda (sin tildes), con su código de llamada. */
@Composable
private fun CountryPicker(onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val all = remember {
        PhoneNumbers.regions().map { it to countryName(it) }.sortedWith { a, b -> dev.liquidphone.core.text.SpanishOrder().compare(a.second, b.second) }
    }
    val shown = remember(query) {
        val q = Fold.fold(query.trim())
        if (q.isEmpty()) all else all.filter { (code, name) -> Fold.fold(name).contains(q) || code.equals(q, ignoreCase = true) }
    }
    GlassTextField(query, { query = it }, str(R.string.search_country))
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp).padding(top = 8.dp)) {
        items(shown, key = { it.first }) { (code, name) ->
            SettingRow(name, subtitle = "+" + PhoneNumbers.callingCode(code), onClick = { onPick(code) })
        }
    }
}
