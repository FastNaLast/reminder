package ru.monthly.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Кнопки в уведомлении: «Готово» и «Через час».
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getIntExtra(ReminderReceiver.EXTRA_TASK_ID, -1)
        if (taskId < 0) return

        Notifications.cancel(context, taskId)

        if (intent.action == ACTION_SNOOZE) {
            val task = TaskStore(context).get(taskId) ?: return
            AlarmScheduler.scheduleSnooze(context, task, minutes = 60)
        }
        // ACTION_DONE: следующий месяц уже запланирован при срабатывании будильника.
    }

    companion object {
        const val ACTION_DONE = "ru.monthly.reminder.ACTION_DONE"
        const val ACTION_SNOOZE = "ru.monthly.reminder.ACTION_SNOOZE"
    }
}
