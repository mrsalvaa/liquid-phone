// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone

import android.app.Activity
import android.content.ContentProviderOperation
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.CallLog.Calls
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.PhoneLookup
import android.provider.ContactsContract.RawContacts
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.liquidphone.core.calls.CallRow
import dev.liquidphone.core.calls.CallType
import dev.liquidphone.core.model.ContactSummary
import dev.liquidphone.core.sim.AccountKey
import dev.liquidphone.core.sim.SimChip
import dev.liquidphone.data.JsonStore
import dev.liquidphone.data.Repo
import dev.liquidphone.ui.components.DangerButton
import dev.liquidphone.ui.components.SwitchRow
import dev.liquidphone.ui.glass.DialogSpec
import dev.liquidphone.ui.glass.GlassButton
import dev.liquidphone.ui.glass.GlassCard
import dev.liquidphone.ui.glass.LocalDialog
import dev.liquidphone.ui.glass.LocalGlass
import dev.liquidphone.ui.glass.SectionLabel
import dev.liquidphone.util.str
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File
import kotlin.random.Random

/**
 * Solo debug y profile: modo seguro, simulacro de llamadas y datos ficticios. La release no lo tiene.
 *
 * - **Modo seguro** (encendido por defecto): la app solo ve los contactos con origen
 *   "liquidphone-debug-N" y las llamadas que insertó esta herramienta. El filtro va dentro del SQL.
 *   Solo se apaga a mano desde Ajustes; ninguna orden por adb lo cambia. Apagado, la ventana lleva
 *   FLAG_SECURE y las capturas salen en negro.
 * - **Simulacro** (encendido por defecto): llamar no marca, muestra qué se habría hecho. También se
 *   apaga solo a mano. Las emergencias no pasan por acá (ver Dialer).
 * - **Datos ficticios:** solo se borra lo que creó esta herramienta, fila por fila.
 *
 * En el registro van cantidades y decisiones, nunca nombres ni números.
 */
object DevTools {
    const val available = true
    private const val TAG = "LiquidPhoneDev"
    private const val SOURCE = "liquidphone-debug-"

    /** Tope de filas por chip entre reales y ficticias: Android borra las más viejas a partir de 500. */
    private const val CALL_LIMIT = 450

    @Serializable
    data class FakeCall(val id: Long, val number: String, val date: Long)

    @Serializable
    data class DevState(
        val safeMode: Boolean = true,
        val simulate: Boolean = true,
        val calls: List<FakeCall> = emptyList(),
    )

    data class FakeCounts(val calls: Int = 0, val contacts: Int = 0) {
        val any: Boolean get() = calls > 0 || contacts > 0
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var app: Context
    private lateinit var store: JsonStore<DevState>
    private val changed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val counts = MutableStateFlow(FakeCounts())

    /** Listas de 500 filas solo en memoria, para medir el rendimiento sin tocar el registro. */
    @Volatile
    private var memoryLists = false

    /** Hasta poder leer el estado, y si algo falla, el modo seguro y el simulacro están encendidos. */
    private val safe: Boolean get() = if (this::store.isInitialized) store.value.safeMode else true

    fun init(context: Context) {
        app = context.applicationContext
        store = JsonStore(File(app.filesDir, "dev_state.json"), DevState.serializer(), DevState())
        scope.launch { refreshCounts() }
    }

    fun changes(): Flow<Unit> = changed.asSharedFlow()

    private fun notifyChanged() {
        refreshCounts()
        changed.tryEmit(Unit)
    }

    // ---------------------------------------------------------------- filtro

    fun allowedContacts(context: Context): Set<Long>? = if (safe) ownContacts(context) else null

    fun allowedCalls(context: Context): Set<Long>? = if (safe) store.value.calls.map { it.id }.toSet() else null

    /**
     * Los contactos que son solo de esta herramienta. Si Android juntara una ficha ficticia con una
     * real, ese contacto no se muestra: tendría datos reales.
     */
    private fun ownContacts(context: Context): Set<Long> = try {
        val resolver = context.contentResolver
        val own = resolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts.CONTACT_ID),
            "${RawContacts.SOURCE_ID} LIKE ? AND ${RawContacts.DELETED} = 0",
            arrayOf("$SOURCE%"),
            null,
        )?.use { c -> buildSet { while (c.moveToNext()) if (!c.isNull(0)) add(c.getLong(0)) } }.orEmpty()
        if (own.isEmpty()) {
            emptySet()
        } else {
            own - mixedContacts(context, own)
        }
    } catch (e: SecurityException) {
        emptySet()
    }

    /** De esos contactos, los que también tienen fichas ajenas. Solo se piden los ids. */
    private fun mixedContacts(context: Context, own: Set<Long>): Set<Long> =
        context.contentResolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts.CONTACT_ID),
            "${RawContacts.CONTACT_ID} IN (${own.joinToString(",")}) AND ${RawContacts.DELETED} = 0 AND (${RawContacts.SOURCE_ID} IS NULL OR ${RawContacts.SOURCE_ID} NOT LIKE ?)",
            arrayOf("$SOURCE%"),
            null,
        )?.use { c -> buildSet { while (c.moveToNext()) add(c.getLong(0)) } }.orEmpty()

    fun memoryContacts(): List<ContactSummary>? = if (memoryLists && safe) fakeSummaries else null

    fun memoryCalls(): List<CallRow>? = if (memoryLists && safe) fakeRows(Repo.sims.state.value.chips) else null

    fun forgetCalls(ids: Collection<Long>) {
        if (safe) store.update { s -> s.copy(calls = s.calls.filter { it.id !in ids }) }
        refreshCounts()
    }

    fun setMemoryLists(on: Boolean) {
        memoryLists = on
        notifyChanged()
    }

    // ---------------------------------------------------------------- simulacro

    fun simulating(): Boolean = if (this::store.isInitialized) store.value.simulate else true

    fun logDecision(what: String) {
        Log.i(TAG, "simulacro: $what")
    }

    // ---------------------------------------------------------------- datos ficticios

    private val given = listOf("Ángela", "José", "Ñusta", "Beatriz", "Damián", "Inés", "Julián", "Lucía", "Martín", "Noelia", "Óscar", "Paula")
    private val family = listOf("Núñez", "Peña", "Muñoz", "Ibáñez", "Müller", "O'Brien", "Giménez", "Sánchez")

    /** Con "(prueba)" el nombre nunca coincide con el de un contacto real ni se confunde con uno. */
    private fun fakeName(i: Int) = "${given[i % given.size]} ${family[i % family.size]} (prueba)"

    /** Los formatos que aparecen en una agenda argentina: con 9, sin 9, con 15, fijo y sin área. */
    private fun fakePhone(i: Int): String {
        val n = 100 + i
        return when (i % 5) {
            0 -> "+54 9 11 5550-0$n"
            1 -> "011 15 5550-0$n"
            2 -> "+54 11 5550-0$n"
            3 -> "0351 555-0$n"
            else -> "15 5550-0$n"
        }
    }

    private val fakeSummaries: List<ContactSummary> by lazy {
        (0 until 500).map { i ->
            ContactSummary(id = -1L - i, lookupKey = "memoria-$i", displayName = fakeName(i), starred = true, phones = listOf(fakePhone(i)))
        }.sortedWith(compareBy(dev.liquidphone.core.text.SpanishOrder()) { it.displayName })
    }

    private fun fakeRows(chips: List<SimChip>): List<CallRow> {
        val random = Random(7)
        val keys: List<AccountKey?> = chips.mapNotNull { it.account }.ifEmpty { listOf(null) }
        val now = System.currentTimeMillis()
        return (0 until 500).map { i ->
            CallRow(
                id = -1L - i,
                number = fakePhone(random.nextInt(60)),
                type = listOf(CallType.INCOMING, CallType.OUTGOING, CallType.MISSED)[i % 3],
                dateMillis = now - i * 3_600_000L,
                durationSeconds = random.nextLong(600),
                account = keys[i % keys.size],
            )
        }
    }

    private fun collides(context: Context, number: String): Boolean =
        context.contentResolver.query(
            Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
            arrayOf(PhoneLookup._ID),
            null,
            null,
            null,
        )?.use { it.count > 0 } == true

    private fun countFor(context: Context, key: AccountKey): Int =
        context.contentResolver.query(
            Calls.CONTENT_URI,
            arrayOf(Calls._ID),
            "${Calls.PHONE_ACCOUNT_COMPONENT_NAME} = ? AND ${Calls.PHONE_ACCOUNT_ID} = ?",
            arrayOf(key.component, key.id),
            null,
        )?.use { it.count } ?: 0

    private fun localAccount(): Pair<String?, String?> {
        val type = RawContacts.getLocalAccountType(app)
        if (type != null) return type to RawContacts.getLocalAccountName(app)
        return if (Build.MANUFACTURER.equals("samsung", ignoreCase = true)) "vnd.sec.contact.phone" to "vnd.sec.contact.phone" else null to null
    }

    /** Crea los datos ficticios. Devuelve el resultado para el registro y la pantalla. */
    @Synchronized
    fun generate(context: Context, chips: List<SimChip>, contactCount: Int = 24, callCount: Int = 40): String {
        if (!safe) return "rechazado: modo seguro apagado"
        val counted = counts()
        if (counted.any) return "rechazado: ya hay datos ficticios (${counted.contacts} contactos, ${counted.calls} llamadas)"
        val keys = chips.mapNotNull { it.account }
        if (keys.isEmpty()) return "rechazado: no se ven los chips"
        // Un número que ya está en la agenda no se usa: en otras apps aparecería como ese contacto.
        val pool = (0 until 200).asSequence().map(::fakePhone).filter { !collides(context, it) }.take(contactCount + 12).toList()
        if (pool.size < contactCount + 12) return "rechazado: pocos números libres"
        val rowsPerKey = callCount / keys.size + 1
        for (key in keys) {
            if (countFor(context, key) + rowsPerKey > CALL_LIMIT) return "rechazado: ese chip superaría $CALL_LIMIT filas"
        }

        val (type, name) = localAccount()
        var contacts = 0
        for (i in 0 until contactCount) {
            val ops = arrayListOf(
                ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                    .withValue(RawContacts.ACCOUNT_TYPE, type)
                    .withValue(RawContacts.ACCOUNT_NAME, name)
                    .withValue(RawContacts.SOURCE_ID, "$SOURCE$i")
                    // DISABLED dejaría la ficha fuera de la tabla de contactos: no se vería.
                    .withValue(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_SUSPENDED)
                    .withValue(RawContacts.STARRED, if (i % 4 == 0) 1 else 0)
                    .build(),
                ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                    .withValue(Data.MIMETYPE, CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(CommonDataKinds.StructuredName.DISPLAY_NAME, fakeName(i))
                    .build(),
                ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                    .withValue(Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(CommonDataKinds.Phone.NUMBER, pool[i])
                    .withValue(CommonDataKinds.Phone.TYPE, CommonDataKinds.Phone.TYPE_MOBILE)
                    .build(),
            )
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            contacts++
        }

        val random = Random(11)
        val now = System.currentTimeMillis()
        val types = listOf(Calls.INCOMING_TYPE, Calls.OUTGOING_TYPE, Calls.MISSED_TYPE)
        val gone = AccountKey(keys.first().component, "$SOURCE" + "retirado")
        var calls = 0
        fun insert(number: String, callType: Int, date: Long, key: AccountKey?, presentation: Int = Calls.PRESENTATION_ALLOWED) {
            val values = android.content.ContentValues().apply {
                put(Calls.NUMBER, number)
                put(Calls.TYPE, callType)
                put(Calls.DATE, date)
                put(Calls.DURATION, if (callType == Calls.MISSED_TYPE) 0 else random.nextInt(600))
                put(Calls.NEW, 0)
                put(Calls.IS_READ, 1)
                put(Calls.NUMBER_PRESENTATION, presentation)
                if (key != null) {
                    put(Calls.PHONE_ACCOUNT_COMPONENT_NAME, key.component)
                    put(Calls.PHONE_ACCOUNT_ID, key.id)
                }
            }
            val uri = context.contentResolver.insert(Calls.CONTENT_URI, values) ?: return
            val id = android.content.ContentUris.parseId(uri)
            // Se anota enseguida: si esto se corta, lo creado hasta acá se puede borrar igual.
            store.update { it.copy(calls = it.calls + FakeCall(id, number, date)) }
            calls++
        }
        var date = now - 10 * 60_000L
        var i = 0
        while (calls < callCount - 4) {
            val number = pool[if (i % 3 == 2) contactCount + i % 12 else i % contactCount]
            val key = keys[(i / 2) % keys.size]
            // Algunas repetidas seguidas, para ver los grupos.
            repeat(if (i % 4 == 0) 3 else 1) {
                if (calls < callCount - 4) {
                    insert(number, types[(i + it) % types.size], date, key)
                    date -= 25 * 60_000L + random.nextLong(5 * 3_600_000L)
                }
            }
            i++
        }
        // Casos de borde: un chip que ya no está, una fila sin chip y un número oculto.
        insert(pool[1], Calls.INCOMING_TYPE, date, gone)
        insert(pool[2], Calls.MISSED_TYPE, date - 60_000L, gone)
        insert(pool[3], Calls.OUTGOING_TYPE, date - 120_000L, null)
        insert("", Calls.INCOMING_TYPE, date - 180_000L, keys.first(), Calls.PRESENTATION_RESTRICTED)
        notifyChanged()
        return "creados: $contacts contactos, $calls llamadas, mezclados=${mixedOwnCount(context)}"
    }

    private fun mixedOwnCount(context: Context): Int {
        val own = context.contentResolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts.CONTACT_ID),
            "${RawContacts.SOURCE_ID} LIKE ? AND ${RawContacts.DELETED} = 0",
            arrayOf("$SOURCE%"),
            null,
        )?.use { c -> buildSet { while (c.moveToNext()) if (!c.isNull(0)) add(c.getLong(0)) } }.orEmpty()
        return if (own.isEmpty()) 0 else mixedContacts(context, own).size
    }

    /** Borra todo lo que creó la herramienta, fila por fila, y nada más. */
    @Synchronized
    fun clear(context: Context): String {
        val resolver = context.contentResolver
        var calls = 0
        for (call in store.value.calls) {
            // Id, número y fecha: tiene que ser exactamente la fila que se creó.
            calls += resolver.delete(
                Calls.CONTENT_URI,
                "${Calls._ID} = ? AND ${Calls.NUMBER} = ? AND ${Calls.DATE} = ?",
                arrayOf(call.id.toString(), call.number, call.date.toString()),
            )
            store.update { s -> s.copy(calls = s.calls.filter { it.id != call.id }) }
        }
        var contacts = 0
        val raw = resolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts._ID, RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME),
            "${RawContacts.SOURCE_ID} LIKE ?",
            arrayOf("$SOURCE%"),
            null,
        )?.use { c -> buildList { while (c.moveToNext()) add(Triple(c.getLong(0), c.getString(1), c.getString(2))) } }.orEmpty()
        for ((id, type, name) in raw) {
            // Como adaptador de sincronización: la ficha se borra de verdad, sin quedar marcada.
            val builder = RawContacts.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
            if (type != null && name != null) {
                builder.appendQueryParameter(RawContacts.ACCOUNT_TYPE, type).appendQueryParameter(RawContacts.ACCOUNT_NAME, name)
            }
            contacts += resolver.delete(builder.build(), "${RawContacts._ID} = ? AND ${RawContacts.SOURCE_ID} LIKE ?", arrayOf(id.toString(), "$SOURCE%"))
        }
        notifyChanged()
        return "borrados: $contacts contactos, $calls llamadas"
    }

    private fun counts(): FakeCounts {
        val contacts = try {
            app.contentResolver.query(RawContacts.CONTENT_URI, arrayOf(RawContacts._ID), "${RawContacts.SOURCE_ID} LIKE ?", arrayOf("$SOURCE%"), null)?.use { it.count } ?: 0
        } catch (e: SecurityException) {
            0
        }
        return FakeCounts(store.value.calls.size, contacts)
    }

    private fun refreshCounts() {
        counts.value = counts()
    }

    /** Estado para adb. Solo cantidades y estados, nada que identifique a nadie. */
    fun status(chips: List<SimChip>, known: Boolean, airplane: Boolean): String {
        val c = counts()
        return "safe=$safe sim=${simulating()} ownContacts=${c.contacts} ownCalls=${c.calls} known=$known airplane=$airplane memory=$memoryLists " +
            "shownContacts=${Repo.book.value.rows.size} shownCalls=${Repo.calls.value.size} " +
            "chips=${chips.size} slots=${chips.map { it.slotIndex }} embedded=${chips.map { it.embedded }} service=${chips.map { it.status }} accounts=${chips.map { it.account != null }}"
    }

    /**
     * ¿Las filas reales usan las mismas cuentas que los chips? Las ficticias no sirven para saberlo
     * porque las escribe esta herramienta. Solo se leen las dos columnas de cuenta y solo salen
     * cantidades. También dice si el id de cuenta parece un identificador largo, sin mostrarlo.
     */
    fun accountCheck(context: Context, chips: List<SimChip>): String {
        val keys = chips.mapNotNull { it.account }.toSet()
        val fake = store.value.calls.map { it.id }.toSet()
        var matched = 0
        var other = 0
        var empty = 0
        context.contentResolver.query(
            Calls.CONTENT_URI,
            arrayOf(Calls._ID, Calls.PHONE_ACCOUNT_COMPONENT_NAME, Calls.PHONE_ACCOUNT_ID),
            null,
            null,
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getLong(0) in fake) continue
                val component = c.getString(1)
                val id = c.getString(2)
                when {
                    component.isNullOrEmpty() || id.isNullOrEmpty() -> empty++
                    AccountKey(component, id) in keys -> matched++
                    else -> other++
                }
            }
        }
        val longIds = keys.count { key -> key.id.length >= 18 && key.id.all(Char::isDigit) }
        return "filasReales: coinciden=$matched otraCuenta=$other sinCuenta=$empty; chipsConIdLargo=$longIds"
    }

    // ---------------------------------------------------------------- interfaz

    /** FLAG_SECURE con el modo seguro apagado: las capturas salen en negro. */
    @Composable
    fun WindowGuard() {
        val state by store.state.collectAsStateWithLifecycle()
        val activity: Activity? = LocalActivity.current
        LaunchedEffect(state.safeMode, activity) {
            val window = activity?.window ?: return@LaunchedEffect
            if (state.safeMode) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    /** Aviso visible mientras haya datos ficticios en los proveedores del sistema. */
    @Composable
    fun FakeDataBanner(modifier: Modifier = Modifier) {
        val c by counts.collectAsStateWithLifecycle()
        if (!c.any) return
        val p = LocalGlass.current
        GlassCard(modifier.fillMaxWidth()) {
            Text(
                str(R.string.dev_fake_banner, c.calls, c.contacts),
                color = p.content,
                fontSize = 13.sp,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            DangerButton(str(R.string.dev_clear), { scope.launch { log(clear(app)) } }, Modifier.padding(top = 8.dp).fillMaxWidth())
        }
    }

    private fun log(text: String) {
        Log.i(TAG, text)
    }

    /** Tarjeta de pruebas de Ajustes. Lo único que apaga el modo seguro y el simulacro es esto. */
    @Composable
    fun SettingsCard() {
        val state by store.state.collectAsStateWithLifecycle()
        val c by counts.collectAsStateWithLifecycle()
        val dialog = LocalDialog.current
        val p = LocalGlass.current
        SectionLabel(str(R.string.dev_section))
        GlassCard {
            SwitchRow(
                str(R.string.dev_safe_mode),
                state.safeMode,
                { on ->
                    if (on) {
                        store.update { it.copy(safeMode = true) }
                        notifyChanged()
                    } else {
                        dialog(
                            DialogSpec(str(R.string.dev_safe_mode_off_title)) { dismiss ->
                                ConfirmBody(str(R.string.dev_safe_mode_off_text), str(R.string.dev_turn_off), dismiss) {
                                    store.update { it.copy(safeMode = false) }
                                    notifyChanged()
                                }
                            },
                        )
                    }
                },
                subtitle = str(R.string.dev_safe_mode_hint),
            )
            SwitchRow(
                str(R.string.dev_simulation),
                state.simulate,
                { on ->
                    if (on) {
                        store.update { it.copy(simulate = true) }
                    } else {
                        dialog(
                            DialogSpec(str(R.string.dev_simulation_off_title)) { dismiss ->
                                val text = if (c.any) str(R.string.dev_simulation_off_fake) else str(R.string.dev_simulation_off_text)
                                ConfirmBody(text, str(R.string.dev_turn_off), dismiss) {
                                    store.update { it.copy(simulate = false) }
                                }
                            },
                        )
                    }
                },
                subtitle = str(R.string.dev_simulation_hint),
            )
            Text(str(R.string.dev_counts, c.contacts, c.calls), fontSize = 12.sp, color = p.contentSecondary, modifier = Modifier.padding(vertical = 8.dp))
            GlassButton(str(R.string.dev_create), { scope.launch { log(generate(app, Repo.sims.state.value.chips)) } }, Modifier.fillMaxWidth(), enabled = !c.any && state.safeMode)
            if (c.any) DangerButton(str(R.string.dev_clear), { scope.launch { log(clear(app)) } }, Modifier.padding(top = 8.dp).fillMaxWidth())
        }
    }

    @Composable
    private fun ConfirmBody(text: String, action: String, dismiss: () -> Unit, onConfirm: () -> Unit) {
        val p = LocalGlass.current
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text, color = p.content, fontSize = 14.sp)
            DangerButton(action, { onConfirm(); dismiss() }, Modifier.fillMaxWidth())
            GlassButton(str(R.string.cancel), dismiss, Modifier.fillMaxWidth())
        }
    }
}
