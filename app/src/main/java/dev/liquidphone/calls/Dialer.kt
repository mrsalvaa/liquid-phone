// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone.calls

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import dev.liquidphone.DevTools
import dev.liquidphone.core.calls.SystemRoute
import dev.liquidphone.core.calls.SystemRoutes
import dev.liquidphone.core.sim.CallDecision
import dev.liquidphone.core.sim.DialerReason
import dev.liquidphone.core.sim.SimChip
import dev.liquidphone.sim.SystemSims

/** Qué pasó al ejecutar una decisión. */
sealed interface DialOutcome {
    /** Se le pasó al sistema. */
    data object Sent : DialOutcome

    /** Simulacro de debug: no se marcó nada. [chip] es null si no se eligió chip. */
    data class Simulated(val number: String, val chip: SimChip?, val systemDialer: Boolean) : DialOutcome

    /** No hay marcador en el sistema. */
    data object Failed : DialOutcome
}

/**
 * Ejecuta lo que decidió CallDecider, siempre por el flujo estándar de Telecom. Es el único lugar
 * de la app que marca.
 *
 * Las emergencias van antes que el simulacro: el de una build de prueba no puede frenarlas. Siendo el
 * marcador predeterminado, salen por Telecom sin elegir chip, porque ACTION_DIAL volvería a esta app;
 * si no lo es, abren el marcador. Nunca se bloquean ni se interceptan. Ver SystemRoutes.
 */
class Dialer(private val sims: SystemSims) {
    @SuppressLint("MissingPermission") // CallDecider solo devuelve Place y SystemChooses con CALL_PHONE.
    fun execute(context: Context, decision: CallDecision): DialOutcome {
        if (decision is CallDecision.OpenSystemDialer && decision.reason == DialerReason.EMERGENCY) {
            return when (SystemRoutes.of(decision.reason, decision.number, isDefaultDialer(context))) {
                SystemRoute.PLACE_WITHOUT_ACCOUNT -> place(context, decision.number, Bundle())
                else -> openSystemDialer(context, decision.number)
            }
        }
        if (DevTools.simulating()) {
            return when (decision) {
                is CallDecision.Place -> simulated(decision.number, decision.chip, false, "place slot=${decision.chip.slotNumber}")
                is CallDecision.SystemChooses -> simulated(decision.number, null, false, "system")
                is CallDecision.OpenSystemDialer -> simulated(decision.number, null, true, "dialer ${decision.reason}")
                else -> DialOutcome.Failed
            }
        }
        return when (decision) {
            is CallDecision.Place -> {
                val handle = sims.handleOf(decision.chip.account)
                    // El chip desapareció justo ahora: no se llama con otro. Lo resuelve el marcador.
                    ?: return openSystemDialer(context, decision.number)
                place(context, decision.number, Bundle().apply { putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle) })
            }
            is CallDecision.SystemChooses -> place(context, decision.number, Bundle())
            is CallDecision.OpenSystemDialer -> when (SystemRoutes.of(decision.reason, decision.number, isDefaultDialer(context))) {
                SystemRoute.MMI -> mmi(context, decision.number)
                SystemRoute.SPECIAL_CODE -> specialCode(context, decision.number)
                else -> openSystemDialer(context, decision.number)
            }
            else -> DialOutcome.Failed
        }
    }

    /** Códigos con asterisco y numeral: los resuelve el sistema. La app nunca lee lo que respondan. */
    private fun mmi(context: Context, number: String): DialOutcome = try {
        val telecom = context.getSystemService(TelecomManager::class.java) ?: throw SecurityException()
        if (telecom.handleMmi(number)) DialOutcome.Sent else openSystemDialer(context, number)
    } catch (e: SecurityException) {
        openSystemDialer(context, number)
    }

    private fun specialCode(context: Context, number: String): DialOutcome = try {
        val telephony = context.getSystemService(TelephonyManager::class.java) ?: throw SecurityException()
        telephony.sendDialerSpecialCode(SystemRoutes.specialCode(number) ?: throw SecurityException())
        DialOutcome.Sent
    } catch (e: SecurityException) {
        openSystemDialer(context, number)
    }

    private fun simulated(number: String, chip: SimChip?, systemDialer: Boolean, what: String): DialOutcome {
        // Solo la decisión, nunca el número: con el modo seguro apagado podría ser real.
        DevTools.logDecision(what)
        return DialOutcome.Simulated(number, chip, systemDialer)
    }

    @SuppressLint("MissingPermission")
    private fun place(context: Context, number: String, extras: Bundle): DialOutcome = try {
        val telecom = context.getSystemService(TelecomManager::class.java) ?: throw SecurityException()
        telecom.placeCall(telUri(number), extras)
        DialOutcome.Sent
    } catch (e: SecurityException) {
        openSystemDialer(context, number)
    }

    fun isDefaultDialer(context: Context): Boolean =
        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage == context.packageName

    /**
     * Esta app también atiende ACTION_DIAL, y no puede mandarse a sí misma: va al predeterminado o, si
     * lo es ella, al marcador del sistema. Si ese no abre, ACTION_DIAL sin destino, que nunca marca solo.
     */
    private fun openSystemDialer(context: Context, number: String): DialOutcome {
        val telecom = context.getSystemService(TelecomManager::class.java)
        val target = telecom?.defaultDialerPackage?.takeIf { it != context.packageName } ?: telecom?.systemDialerPackage
        fun intent() = Intent(Intent.ACTION_DIAL, telUri(number)).apply {
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent().apply { if (target != null && target != context.packageName) setPackage(target) })
            DialOutcome.Sent
        } catch (e: ActivityNotFoundException) {
            try {
                context.startActivity(intent())
                DialOutcome.Sent
            } catch (e2: ActivityNotFoundException) {
                DialOutcome.Failed
            }
        }
    }

    private fun telUri(number: String): Uri = Uri.fromParts("tel", number, null)
}
