package ru.monthly.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object Notifications {

    const val CHANNEL_ID = "monthly_reminders"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_desc)
                enableVibration(true)
                setShowBadge(true)
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    fun show(context: Context, task: Task, snooze: Boolean) {
        ensureChannel(context)
        if (!areEnabled(context)) return

        val openApp = PendingIntent.getActivity(
            context,
            task.id,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val doneAction = actionPendingIntent(context, task.id, NotificationActionReceiver.ACTION_DONE)
        val snoozeAction = actionPendingIntent(context, task.id, NotificationActionReceiver.ACTION_SNOOZE)
        val text = context.getString(if (snooze) R.string.notif_snooze_text else R.string.notif_text)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(task.title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .addAction(0, context.getString(R.string.action_done), doneAction)
            .addAction(0, context.getString(R.string.action_snooze), snoozeAction)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(task.id, notification)
        } catch (e: SecurityException) {
            // Пользователь запретил уведомления — молча выходим.
        }
    }

    fun cancel(context: Context, taskId: Int) {
        NotificationManagerCompat.from(context).cancel(taskId)
    }

    fun areEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun actionPendingIntent(context: Context, taskId: Int, action: String): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            data = Uri.parse("monthly://action/$action/$taskId")
            putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
        }
        val requestCode =
            if (action == NotificationActionReceiver.ACTION_SNOOZE) 300_000 + taskId
            else 200_000 + taskId
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
