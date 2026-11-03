// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone.incall

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import dev.liquidphone.data.Repo

/**
 * Telecom solo se conecta a este servicio si la app es el marcador predeterminado. No declara
 * IN_CALL_SERVICE_RINGING: el tono y la vibración de la entrante los pone el sistema, con el tono
 * elegido por el usuario, su volumen y No molestar.
 */
class PhoneInCallService : InCallService() {
    override fun onCreate() {
        super.onCreate()
        Repo.start()
        CallManager.attach(this)
    }

    override fun onDestroy() {
        CallManager.detach()
        CallNotifications.cancelAll(this)
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        CallManager.onAdded(call)
        // Una entrante se anuncia por la notificación de pantalla completa. Sin permiso de
        // notificaciones no aparecería nada: ahí se abre directo.
        if (call.details.state != Call.STATE_RINGING || !CallNotifications.enabled(this)) {
            startActivity(Intent(this, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override fun onCallRemoved(call: Call) = CallManager.onRemoved(call)

    override fun onCallAudioStateChanged(audioState: CallAudioState) = CallManager.publish()

    override fun onCanAddCallChanged(canAddCall: Boolean) = CallManager.publish()
}
