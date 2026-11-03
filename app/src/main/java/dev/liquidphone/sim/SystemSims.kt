// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone.sim

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.ServiceState
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import dev.liquidphone.core.sim.AccountKey
import dev.liquidphone.core.sim.SimChip
import dev.liquidphone.core.sim.SimStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Lo que se sabe de los chips ahora. [known] es false si falta READ_PHONE_STATE. */
data class SimSnapshot(
    val chips: List<SimChip> = emptyList(),
    val known: Boolean = false,
    val airplane: Boolean = false,
    val countries: List<String> = emptyList(),
) {
    /** Con un solo chip o sin datos, se oculta todo lo relacionado con elegir chip. */
    val multiple: Boolean get() = chips.size > 1
}

/**
 * Chips activos y cuentas de llamada del sistema. Solo se usan el nombre visible, el color, la
 * ranura y el estado de servicio; ICCID, IMSI, IMEI y el número de línea nunca se piden.
 * SubscriptionInfo y PhoneAccountHandle no salen de esta clase y no se imprimen.
 *
 * "SIM 1" es la ranura lógica 0 (getSimSlotIndex), tanto física como eSIM.
 *
 * El estado de servicio viene de [TelephonyCallback.ServiceStateListener], que no pide ubicación:
 * sin ella, el sistema solo le quita los datos de ubicación. Esta app jamás pide esos permisos.
 */
@SuppressLint("MissingPermission") // Todo pasa por hasPermission() y además atrapa SecurityException.
class SystemSims(context: Context) {
    private val app = context.applicationContext
    private val subs = app.getSystemService(SubscriptionManager::class.java)
    private val telecom = app.getSystemService(TelecomManager::class.java)
    private val telephony = app.getSystemService(TelephonyManager::class.java)

    private val _state = MutableStateFlow(SimSnapshot(airplane = airplaneMode()))
    val state: StateFlow<SimSnapshot> = _state.asStateFlow()

    private val status = HashMap<Int, SimStatus>()
    private val callbacks = HashMap<Int, Pair<TelephonyManager, TelephonyCallback>>()
    private var handles: Map<AccountKey, PhoneAccountHandle> = emptyMap()
    private var airplaneRegistered = false
    private var subsRegistered = false

    private val subsListener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
        override fun onSubscriptionsChanged() = refresh()
    }

    fun hasPermission(): Boolean = app.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    /** Se llama al arrancar y al volver a la app. Solo desde el hilo principal. */
    fun start() {
        if (!airplaneRegistered) {
            airplaneRegistered = true
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = refresh()
            }
            val filter = IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                app.registerReceiver(receiver, filter)
            }
        }
        if (!subsRegistered && hasPermission()) {
            subsRegistered = true
            subs?.addOnSubscriptionsChangedListener(app.mainExecutor, subsListener)
        }
        refresh()
    }

    private fun airplaneMode(): Boolean = Settings.Global.getInt(app.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0

    private fun refresh() {
        val airplane = airplaneMode()
        if (!hasPermission()) {
            unwatchAll()
            handles = emptyMap()
            _state.value = SimSnapshot(airplane = airplane)
            return
        }
        try {
            val infos = subs?.activeSubscriptionInfoList.orEmpty().filter { !it.isOpportunistic }
            val bySub = HashMap<Int, Pair<AccountKey, PhoneAccountHandle>>()
            for (handle in telecom?.callCapablePhoneAccounts.orEmpty()) {
                val subId = telephony?.getSubscriptionId(handle) ?: continue
                if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    bySub[subId] = AccountKey(handle.componentName.flattenToString(), handle.id) to handle
                }
            }
            watch(infos.map { it.subscriptionId }.toSet())
            handles = bySub.values.toMap()
            _state.value = SimSnapshot(
                chips = infos.map { info ->
                    SimChip(
                        slotIndex = info.simSlotIndex,
                        displayName = info.displayName?.toString().orEmpty(),
                        color = info.iconTint,
                        account = bySub[info.subscriptionId]?.first,
                        status = status[info.subscriptionId] ?: SimStatus.UNKNOWN,
                        embedded = info.isEmbedded,
                    )
                }.sortedBy { it.slotIndex },
                known = true,
                airplane = airplane,
                countries = infos.mapNotNull { it.countryIso },
            )
        } catch (e: SecurityException) {
            handles = emptyMap()
            _state.value = SimSnapshot(airplane = airplane)
        }
    }

    private fun watch(subIds: Set<Int>) {
        (callbacks.keys - subIds).toList().forEach(::unwatch)
        for (subId in subIds - callbacks.keys) {
            val manager = telephony?.createForSubscriptionId(subId) ?: continue
            val callback = object : TelephonyCallback(), TelephonyCallback.ServiceStateListener {
                override fun onServiceStateChanged(serviceState: ServiceState) {
                    val new = if (serviceState.state == ServiceState.STATE_IN_SERVICE) SimStatus.IN_SERVICE else SimStatus.NO_SERVICE
                    if (status.put(subId, new) != new) refresh()
                }
            }
            try {
                manager.registerTelephonyCallback(app.mainExecutor, callback)
                callbacks[subId] = manager to callback
            } catch (e: SecurityException) {
                // Sin datos de servicio: el chip queda como "desconocido", que no bloquea nada.
            }
        }
    }

    private fun unwatch(subId: Int) {
        callbacks.remove(subId)?.let { (manager, callback) -> manager.unregisterTelephonyCallback(callback) }
        status.remove(subId)
    }

    private fun unwatchAll() = callbacks.keys.toList().forEach(::unwatch)

    /** La cuenta predeterminada del sistema; null si no hay o no se puede saber. */
    fun systemDefault(): AccountKey? = try {
        if (!hasPermission()) null else telecom?.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL)?.let { AccountKey(it.componentName.flattenToString(), it.id) }
    } catch (e: SecurityException) {
        null
    }

    fun handleOf(key: AccountKey?): PhoneAccountHandle? = key?.let { handles[it] }

    /**
     * El sistema decide qué es una emergencia. Si no puede responder, tira una excepción y
     * CallDecider lo trata como emergencia.
     */
    fun isEmergency(number: String): Boolean = telephony?.isEmergencyNumber(number) ?: throw IllegalStateException()
}
