package ru.monthly.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * После перезагрузки телефона, обновления приложения или смены часового пояса
 * все будильники в системе сбрасываются. Восстанавливаем их.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        TaskStore(context).all()
            .filter { it.enabled }
            .forEach { AlarmScheduler.schedule(context, it) }
    }
}
