package ru.monthly.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * Планирование будильников через AlarmManager.
 *
 * Используется setExactAndAllowWhileIdle, поэтому напоминание приходит
 * даже в Doze-режиме. Точность на некоторых прошивках (HyperOS, One UI)
 * всё равно требует отключения оптимизации батареи.
 */
object AlarmScheduler {

    private const val SNOOZE_REQUEST_BASE = 100_000

    /** Ближайший момент срабатывания задачи. */
    fun nextTrigger(task: Task, now: LocalDateTime = LocalDateTime.now()): LocalDateTime {
        var month = YearMonth.from(now)
        repeat(24) {
            val day = task.dayOfMonth.coerceAtMost(month.lengthOfMonth())
            val candidate = month.atDay(day).atTime(task.hour, task.minute)
            if (candidate.isAfter(now)) return candidate
            month = month.plusMonths(1)
        }
        // Теоретически недостижимо, но пусть будет безопасный запас.
        return now.plusMonths(1)
    }

    /** Ставит ежемесячный будильник (или снимает, если задача выключена). */
    fun schedule(context: Context, task: Task) {
        if (!task.enabled) {
            cancel(context, task.id)
            return
        }
        val at = nextTrigger(task)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        setAlarm(context, at, pendingIntent(context, task.id, snooze = false))
    }

    /** Разовое напоминание «через час». */
    fun scheduleSnooze(context: Context, task: Task, minutes: Long = 60) {
        val at = System.currentTimeMillis() + minutes * 60_000L
        setAlarm(context, at, pendingIntent(context, task.id, snooze = true))
    }

    fun cancel(context: Context, taskId: Int) {
        val alarmManager = alarmManager(context)
        val monthly = pendingIntent(context, taskId, snooze = false)
        alarmManager.cancel(monthly)
        monthly.cancel()
        val snooze = pendingIntent(context, taskId, snooze = true)
        alarmManager.cancel(snooze)
        snooze.cancel()
    }

    private fun setAlarm(context: Context, at: Long, pendingIntent: PendingIntent) {
        val alarmManager = alarmManager(context)
        try {
            if (canScheduleExact(alarmManager)) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent)
            }
        } catch (e: SecurityException) {
            // Разрешение на точные будильники отозвали уже после установки.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent)
        }
    }

    private fun alarmManager(context: Context) =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun canScheduleExact(alarmManager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun pendingIntent(context: Context, taskId: Int, snooze: Boolean): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMIND
            data = Uri.parse("monthly://task/$taskId" + if (snooze) "/snooze" else "")
            putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
        }
        val requestCode = if (snooze) SNOOZE_REQUEST_BASE + taskId else taskId
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
