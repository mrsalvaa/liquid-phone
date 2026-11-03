// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone.calls

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import dev.liquidphone.DevTools
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
 * Las emergencias siempre abren el marcador del sistema, incluso con el simulacro encendido:
 * ACTION_DIAL no marca solo, y el simulacro de una build de prueba no puede frenar una emergencia.
 */
class Dialer(private val sims: SystemSims) {
    @SuppressLint("MissingPermission") // CallDecider solo devuelve Place y SystemChooses con CALL_PHONE.
    fun execute(context: Context, decision: CallDecision): DialOutcome {
        if (decision is CallDecision.OpenSystemDialer && decision.reason == DialerReason.EMERGENCY) {
            return openSystemDialer(context, decision.number)
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
            is CallDecision.OpenSystemDialer -> openSystemDialer(context, decision.number)
            else -> DialOutcome.Failed
        }
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

    private fun openSystemDialer(context: Context, number: String): DialOutcome = try {
        val intent = Intent(Intent.ACTION_DIAL, telUri(number))
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        DialOutcome.Sent
    } catch (e: ActivityNotFoundException) {
        DialOutcome.Failed
    }

    private fun telUri(number: String): Uri = Uri.fromParts("tel", number, null)
}
