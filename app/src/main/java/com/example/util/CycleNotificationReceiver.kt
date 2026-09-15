package com.example.util

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.entity.UserSettingsEntity
import com.example.domain.CyclePredictionState
import java.time.LocalDate
import java.time.ZoneId

/**
 * On-device local broadcast receiver for cycle alerts (Period soon, phase starts, daily check-in).
 * Never uses internet; all triggers are set via Android's local AlarmManager.
 */
class CycleNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Cycle"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "Your cycle update is ready."
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 1001)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val channelId = "cycle_alerts"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Cycle & Health Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Private on-device reminders for cycle phases and symptoms"
                setShowBadge(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

        const val ID_PERIOD_REMINDER = 2001
        const val ID_FERTILE_WINDOW = 2002
        const val ID_DAILY_DIGEST = 2003
        const val ID_PHASE_MENSTRUAL = 2010
        const val ID_PHASE_FOLLICULAR = 2011
        const val ID_PHASE_OVULATORY = 2012
        const val ID_PHASE_LUTEAL = 2013

        fun scheduleNotification(
            context: Context,
            targetDate: LocalDate,
            title: String,
            message: String,
            notificationId: Int
        ) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

            val intent = Intent(context, CycleNotificationReceiver::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MESSAGE, message)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Set alarm for 9:00 AM on target date
            val triggerTime = targetDate.atTime(9, 0)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

            if (triggerTime > System.currentTimeMillis()) {
                try {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } catch (_: SecurityException) {
                    // Handled safely if exact alarm permission requires user toggle
                }
            }
        }

        fun cancelNotification(context: Context, notificationId: Int) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, CycleNotificationReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }

        fun sendImmediateNotification(
            context: Context,
            title: String,
            message: String,
            notificationId: Int = (System.currentTimeMillis() % 10000).toInt() + 3000
        ) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val channelId = "cycle_alerts"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Cycle & Health Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Private on-device reminders for cycle phases and symptoms"
                    setShowBadge(true)
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(notificationId, notification)
        }

        fun scheduleAllAlerts(
            context: Context,
            predictionState: CyclePredictionState,
            settings: UserSettingsEntity
        ) {
            if (!settings.notificationsEnabled) {
                cancelNotification(context, ID_PERIOD_REMINDER)
                cancelNotification(context, ID_FERTILE_WINDOW)
                cancelNotification(context, ID_DAILY_DIGEST)
                cancelNotification(context, ID_PHASE_MENSTRUAL)
                cancelNotification(context, ID_PHASE_FOLLICULAR)
                cancelNotification(context, ID_PHASE_OVULATORY)
                cancelNotification(context, ID_PHASE_LUTEAL)
                return
            }

            // 1. Period Reminder (2 days before predicted start)
            if (predictionState is CyclePredictionState.Predicted) {
                val periodReminderDate = predictionState.predictedPeriodStart.minusDays(2)
                scheduleNotification(
                    context,
                    periodReminderDate,
                    "Period expected in 2 days",
                    "Your period is estimated to begin in 2 days. Prepare comfort items and track any early symptoms.",
                    ID_PERIOD_REMINDER
                )

                // 2. Fertile Window Alert
                if (settings.fertileWindowAlertEnabled) {
                    scheduleNotification(
                        context,
                        predictionState.fertileWindow.startDate,
                        "Fertile window has started",
                        "Your estimated fertile window begins today. Tap to view your cycle calendar.",
                        ID_FERTILE_WINDOW
                    )
                } else {
                    cancelNotification(context, ID_FERTILE_WINDOW)
                }

                // 3. New Phase Expectations Notifications
                if (settings.phaseChangeAlertEnabled) {
                    val periodLength = (predictionState.predictedPeriodEnd.toEpochDay() - predictionState.predictedPeriodStart.toEpochDay() + 1).coerceAtLeast(1)
                    val cycleStart = predictionState.currentCycleStartDate ?: predictionState.predictedPeriodStart.minusDays(predictionState.currentCycleDay.toLong() - 1)

                    // Menstrual Phase
                    scheduleNotification(
                        context,
                        predictionState.predictedPeriodStart,
                        "Menstrual Phase starting",
                        "What to expect: Rest and ease tension. Gentle movement & hydration help lingering aches. Remember to log your symptoms in the app.",
                        ID_PHASE_MENSTRUAL
                    )

                    // Follicular Phase (after predicted period ends)
                    val follicularStart = cycleStart.plusDays(periodLength)
                    scheduleNotification(
                        context,
                        follicularStart,
                        "Follicular Phase starting",
                        "What to expect: Estrogen is rising with renewed mental clarity and energy. Take a moment to log your symptoms in the app.",
                        ID_PHASE_FOLLICULAR
                    )

                    // Ovulation Phase
                    scheduleNotification(
                        context,
                        predictionState.fertileWindow.startDate,
                        "Ovulation Phase starting",
                        "What to expect: Peak vitality, energy, and social ease. Log today's symptoms and notes in the app.",
                        ID_PHASE_OVULATORY
                    )

                    // Luteal Phase
                    val lutealStart = predictionState.fertileWindow.endDate.plusDays(1)
                    scheduleNotification(
                        context,
                        lutealStart,
                        "Luteal Phase starting",
                        "What to expect: Progesterone rises as body winds down. Gentle pacing & nourishing foods support you. Log symptoms to track your patterns.",
                        ID_PHASE_LUTEAL
                    )
                } else {
                    cancelNotification(context, ID_PHASE_MENSTRUAL)
                    cancelNotification(context, ID_PHASE_FOLLICULAR)
                    cancelNotification(context, ID_PHASE_OVULATORY)
                    cancelNotification(context, ID_PHASE_LUTEAL)
                }
            }
        }
    }
}
