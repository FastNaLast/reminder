package ru.monthly.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Принимает будильник, показывает уведомление и сразу планирует следующий месяц.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getIntExtra(EXTRA_TASK_ID, -1)
        if (taskId < 0) return

        val task = TaskStore(context).get(taskId) ?: return
        if (!task.enabled) return

        val isSnooze = intent.data?.path?.endsWith("snooze") == true

        Notifications.show(context, task, snooze = isSnooze)

        // Ежемесячную цепочку продолжаем только для «настоящего» срабатывания.
        if (!isSnooze) {
            AlarmScheduler.schedule(context, task)
        }
    }

    companion object {
        const val ACTION_REMIND = "ru.monthly.reminder.ACTION_REMIND"
        const val EXTRA_TASK_ID = "task_id"
    }
}
