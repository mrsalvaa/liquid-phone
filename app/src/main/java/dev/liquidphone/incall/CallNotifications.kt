// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone.incall

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.TelecomManager
import dev.liquidphone.DevTools
import dev.liquidphone.MainActivity
import dev.liquidphone.R
import dev.liquidphone.core.calls.CallPhase
import dev.liquidphone.core.calls.MissedSummary
import dev.liquidphone.core.sim.SimChip
import dev.liquidphone.data.Repo
import dev.liquidphone.ui.screens.simDescription
import dev.liquidphone.util.plural
import dev.liquidphone.util.str
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Entrante, en curso y perdida. El canal de la entrante no tiene sonido: suena el sistema. */
object CallNotifications {
    private const val CHANNEL_INCOMING = "calls_incoming"
    private const val CHANNEL_ONGOING = "calls_ongoing"
    private const val CHANNEL_MISSED = "calls_missed"
    private const val ID_INCOMING = 1
    private const val ID_ONGOING = 2
    private const val ID_MISSED = 3

    const val ACTION_DECLINE = "dev.liquidphone.action.DECLINE"
    const val ACTION_HANGUP = "dev.liquidphone.action.HANGUP"
    const val EXTRA_CALL = "call"

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)

    fun enabled(context: Context): Boolean = manager(context)?.areNotificationsEnabled() == true

    private fun channels(context: Context) {
        val m = manager(context) ?: return
        m.createNotificationChannel(
            NotificationChannel(CHANNEL_INCOMING, str(R.string.channel_incoming), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        m.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, str(R.string.channel_ongoing), NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
        )
        m.createNotificationChannel(NotificationChannel(CHANNEL_MISSED, str(R.string.channel_missed), NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun screen(context: Context, code: Int, answerId: Int? = null): PendingIntent {
        val intent = Intent(context, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (answerId != null) intent.putExtra(InCallActivity.EXTRA_ANSWER, answerId)
        return PendingIntent.getActivity(context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun action(context: Context, action: String, id: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        action.hashCode(),
        Intent(context, CallActionReceiver::class.java).setAction(action).putExtra(EXTRA_CALL, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun chipText(chip: SimChip?, unknown: Boolean): String? = when {
        chip != null -> simDescription(chip)
        unknown -> str(R.string.sim_gone)
        else -> null
    }

    fun update(context: Context, ui: InCallUi) {
        channels(context)
        val m = manager(context) ?: return
        val main = ui.primary
        val ringing = main?.takeIf { it.live.phase == CallPhase.RINGING }
        if (ringing != null) {
            m.cancel(ID_ONGOING)
            val id = ringing.live.id
            val person = Person.Builder().setName(ringing.title).setImportant(true).build()
            val chip = chipText(ringing.chip, ringing.chipUnknown)
            m.notify(
                ID_INCOMING,
                Notification.Builder(context, CHANNEL_INCOMING)
                    .setSmallIcon(R.drawable.ic_notification_call)
                    .setCategory(Notification.CATEGORY_CALL)
                    .setOngoing(true)
                    .setContentIntent(screen(context, 1))
                    .setFullScreenIntent(screen(context, 1), true)
                    // La línea de texto de la notificación: el chip por el que entra la llamada.
                    .apply { if (chip != null) setContentText(chip) }
                    .setStyle(
                        Notification.CallStyle.forIncomingCall(person, action(context, ACTION_DECLINE, id), screen(context, 2, answerId = id))
                            .apply { if (chip != null) setVerificationText(chip) },
                    )
                    .build(),
            )
            return
        }
        m.cancel(ID_INCOMING)
        if (main == null || main.live.phase in listOf(CallPhase.DISCONNECTED, CallPhase.DISCONNECTING)) {
            m.cancel(ID_ONGOING)
            return
        }
        val chip = chipText(main.chip, main.chipUnknown)
        m.notify(
            ID_ONGOING,
            Notification.Builder(context, CHANNEL_ONGOING)
                .setSmallIcon(R.drawable.ic_notification_call)
                .setCategory(Notification.CATEGORY_CALL)
                .setOngoing(true)
                .setContentTitle(main.title)
                .setContentText(chip ?: str(R.string.call_in_progress))
                .setUsesChronometer(main.connectedAt > 0)
                .setWhen(if (main.connectedAt > 0) main.connectedAt else System.currentTimeMillis())
                .setContentIntent(screen(context, 3))
                .addAction(Notification.Action.Builder(null, str(R.string.call_hangup), action(context, ACTION_HANGUP, main.live.id)).build())
                .build(),
        )
    }

    fun cancelAll(context: Context) {
        manager(context)?.run {
            cancel(ID_INCOMING)
            cancel(ID_ONGOING)
        }
    }

    /** Las perdidas sin ver, con el chip de la última. Sale del registro: lo que no esté filtrado. */
    fun showMissed(context: Context) {
        channels(context)
        val summary = MissedSummary.of(Repo.callLog.newMissed())
        val m = manager(context) ?: return
        if (summary == null) {
            m.cancel(ID_MISSED)
            return
        }
        val latest = summary.latest
        val raw = latest.number.takeIf { latest.callable }?.let { DevTools.maskNumber(it) }
        val who = raw?.let { Repo.contactFor(it)?.displayName ?: Repo.numbers.value.format(it) } ?: str(R.string.number_hidden)
        val chips = Repo.sims.state.value.chips
        val chip = latest.account?.let { key -> chips.firstOrNull { it.account == key } }
        val chipLine = chip?.let(::simDescription) ?: latest.account?.let { str(R.string.sim_gone) }
        val open = PendingIntent.getActivity(
            context,
            4,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_RECENTS, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = Notification.Builder(context, CHANNEL_MISSED)
            .setSmallIcon(R.drawable.ic_notification_call)
            .setCategory(Notification.CATEGORY_MISSED_CALL)
            .setContentTitle(plural(R.plurals.missed_calls, summary.count))
            .setContentText(listOfNotNull(if (summary.sameNumber) who else str(R.string.missed_several_numbers), chipLine).joinToString(" · "))
            .setWhen(latest.dateMillis)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
        if (raw != null && summary.sameNumber) {
            val dial = PendingIntent.getActivity(
                context,
                5,
                Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_DIAL, raw).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(Notification.Action.Builder(null, str(R.string.call), dial).build())
        }
        m.notify(ID_MISSED, builder.build())
        DevTools.logCall("perdidas=${summary.count} chip=${chip?.slotNumber ?: "?"}")
    }

    /** Se vieron las perdidas: sale la notificación y dejan de ser nuevas. */
    @SuppressLint("MissingPermission") // El marcador predeterminado también puede, y si no, SecurityException.
    fun missedSeen(context: Context) {
        manager(context)?.cancel(ID_MISSED)
        CoroutineScope(Dispatchers.IO).launch {
            Repo.callLog.markMissedSeen()
            try {
                context.getSystemService(TelecomManager::class.java)?.cancelMissedCallsNotification()
            } catch (e: SecurityException) {
                // Sin el rol, de esa notificación se encarga el marcador que lo tenga.
            }
        }
    }
}

/** Botones de las notificaciones de llamada. No exportado. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(CallNotifications.EXTRA_CALL, 0)
        when (intent.action) {
            CallNotifications.ACTION_DECLINE -> CallManager.reject(id)
            CallNotifications.ACTION_HANGUP -> CallManager.hangup(id)
        }
    }
}

/**
 * Telecom le avisa al marcador predeterminado que hay perdidas, y entonces no muestra la suya. No se
 * usa nada del intent: cualquiera podría mandarlo, y todo sale del registro de llamadas.
 */
class MissedCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                CallNotifications.showMissed(context.applicationContext)
            } catch (e: SecurityException) {
                DevTools.logCall("perdidas: falta un permiso")
            } finally {
                pending.finish()
            }
        }
    }
}
