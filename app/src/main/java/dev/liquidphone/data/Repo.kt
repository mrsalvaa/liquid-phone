// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import dev.liquidphone.DevTools
import dev.liquidphone.calls.Dialer
import dev.liquidphone.calls.SystemCallLog
import dev.liquidphone.contacts.SystemContacts
import dev.liquidphone.core.calls.CallLogTools
import dev.liquidphone.core.calls.CallRow
import dev.liquidphone.core.dial.DialEntry
import dev.liquidphone.core.model.ContactSummary
import dev.liquidphone.core.phone.PhoneMatch
import dev.liquidphone.core.phone.PhoneMatcher
import dev.liquidphone.core.phone.PhoneNumbers
import dev.liquidphone.core.sim.CallContext
import dev.liquidphone.core.sim.CallDecider
import dev.liquidphone.core.sim.CallDecision
import dev.liquidphone.core.sim.CallTarget
import dev.liquidphone.core.sim.SimCountry
import dev.liquidphone.core.sim.SimPolicy
import dev.liquidphone.core.text.SpanishOrder
import dev.liquidphone.sim.SimSnapshot
import dev.liquidphone.sim.SystemSims
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale

/** La agenda lista para usar: en orden alfabético, con la coincidencia de números y el T9 calculados. */
class Book(
    val rows: List<ContactSummary> = emptyList(),
    val matcher: PhoneMatcher<ContactSummary>? = null,
    val entries: List<DialEntry<ContactSummary>> = emptyList(),
) {
    val favorites: List<ContactSummary> get() = rows.filter { it.starred }
}

/** Todo lo que lee la app, siempre desde los proveedores del sistema. No hay base de datos propia. */
object Repo {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var app: Context

    lateinit var contacts: SystemContacts
        private set
    lateinit var callLog: SystemCallLog
        private set
    lateinit var sims: SystemSims
        private set
    lateinit var dialer: Dialer
        private set

    private val _numbers = MutableStateFlow(PhoneNumbers())
    /** Normalizador con el país y el código de área de los ajustes. */
    val numbers: StateFlow<PhoneNumbers> = _numbers.asStateFlow()

    private val _book = MutableStateFlow(Book())
    val book: StateFlow<Book> = _book.asStateFlow()

    private val _calls = MutableStateFlow<List<CallRow>>(emptyList())
    val calls: StateFlow<List<CallRow>> = _calls.asStateFlow()

    private val _countryConflict = MutableStateFlow<List<String>>(emptyList())
    /** Los chips son de países distintos y todavía no se eligió: la interfaz pregunta. */
    val countryConflict: StateFlow<List<String>> = _countryConflict.asStateFlow()

    private val _permissionTick = MutableStateFlow(0)
    /** Cambia cada vez que se revisan los permisos: las pantallas vuelven a mirar cuáles hay. */
    val permissionTick: StateFlow<Int> = _permissionTick.asStateFlow()

    private var contactsJob: Job? = null
    private var callsJob: Job? = null

    fun init(context: Context) {
        app = context.applicationContext
        contacts = SystemContacts(app) { DevTools.allowedContacts(app) }
        callLog = SystemCallLog(app) { DevTools.allowedCalls(app) }
        sims = SystemSims(app)
        dialer = Dialer(sims)
        scope.launch {
            Stores.settings.state.map { it.defaultCountry to it.defaultAreaCode }.distinctUntilChanged().collect { (country, area) ->
                _numbers.value = PhoneNumbers(country.ifEmpty { "AR" }, area.ifEmpty { null })
            }
        }
        // Lo que muestra el modo seguro de debug cambia sin que cambie el proveedor.
        scope.launch { DevTools.changes().collect { reloadAll() } }
    }

    fun granted(permission: String): Boolean = app.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /** Se llama al abrir la app, al volver a ella y después de cada permiso. Solo desde el hilo principal. */
    fun start() {
        _permissionTick.value++
        sims.start()
        chooseCountry(sims.state.value)
        if (contactsJob == null && granted(Manifest.permission.READ_CONTACTS)) {
            contactsJob = scope.launch {
                combine(contacts.changes(), numbers) { _, n -> n }.collect { loadBook(it) }
            }
        }
        if (callsJob == null && granted(Manifest.permission.READ_CALL_LOG)) {
            callsJob = scope.launch { callLog.changes().collect { loadCalls() } }
        }
    }

    private fun reloadAll() {
        if (contactsJob != null) scope.launch(Dispatchers.IO) { loadBook(numbers.value) }
        if (callsJob != null) scope.launch(Dispatchers.IO) { loadCalls() }
    }

    private fun loadBook(numbers: PhoneNumbers) {
        val rows = DevTools.memoryContacts() ?: contacts.summaries().sortedWith(compareBy(SpanishOrder()) { it.displayName })
        _book.value = Book(
            rows = rows,
            matcher = PhoneMatcher(numbers, rows) { it.phones },
            entries = rows.map { DialEntry(it, it.displayName, it.phones, numbers::searchForms) },
        )
    }

    private fun loadCalls() {
        _calls.value = DevTools.memoryCalls() ?: callLog.rows()
    }

    /** Borra entradas de recientes. Devuelve cuántas borró. */
    fun deleteCalls(ids: Collection<Long>): Int {
        val deleted = callLog.delete(ids)
        DevTools.forgetCalls(ids)
        return deleted
    }

    /** El contacto de un número, si es uno solo. Con varios candidatos no se elige ninguno. */
    fun contactFor(number: String): ContactSummary? =
        when (val m = _book.value.matcher?.match(number)) {
            is PhoneMatch.One -> m.item
            else -> null
        }

    /** Decide cómo llamar con lo que se sabe en este momento. */
    fun decide(number: String, target: CallTarget): CallDecision {
        val snapshot: SimSnapshot = sims.state.value
        val context = CallContext(
            chips = snapshot.chips,
            chipsKnown = snapshot.known,
            airplaneMode = snapshot.airplane,
            canCall = granted(Manifest.permission.CALL_PHONE),
            policy = SimPolicy.fromValue(Stores.settings.value.simPolicy),
            systemDefault = sims.systemDefault(),
            lastOutgoing = CallLogTools.lastOutgoing(_calls.value, number, numbers.value),
        )
        return CallDecider.decide(number, target, context, sims::isEmergency)
    }

    /**
     * País por defecto, la primera vez: el de los chips si coinciden; si son distintos no se
     * adivina y la interfaz pregunta. Sin ver los chips, como en Contacts: el de la SIM principal.
     */
    private fun chooseCountry(snapshot: SimSnapshot) {
        if (Stores.settings.value.defaultCountry.isNotEmpty()) return
        when (val c = SimCountry.of(snapshot.countries)) {
            is SimCountry.One -> Stores.settings.update { it.copy(defaultCountry = c.iso) }
            is SimCountry.Conflict -> _countryConflict.value = c.isos
            SimCountry.Unknown -> Stores.settings.update { it.copy(defaultCountry = simCountry(app)) }
        }
    }

    fun setCountry(iso: String) {
        _countryConflict.value = emptyList()
        Stores.settings.update { it.copy(defaultCountry = iso) }
    }

    /** País de la SIM en mayúsculas (sin permisos), o el del idioma si no hay SIM. */
    fun simCountry(context: Context): String {
        val tm = context.getSystemService(TelephonyManager::class.java)
        val sim = tm?.simCountryIso?.takeIf { it.length == 2 }
        return (sim ?: Locale.getDefault().country.takeIf { it.length == 2 } ?: "AR").uppercase(Locale.ROOT)
    }
}
