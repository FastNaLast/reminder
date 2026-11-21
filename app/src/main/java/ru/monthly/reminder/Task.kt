package ru.monthly.reminder

import org.json.JSONObject

/**
 * Одно ежемесячное напоминание.
 *
 * @param dayOfMonth день месяца 1..31. Если в месяце меньше дней (например, 31 февраля),
 *                   сработает в последний день месяца.
 */
data class Task(
    val id: Int,
    val title: String,
    val dayOfMonth: Int,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true
) {

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("day", dayOfMonth)
        put("hour", hour)
        put("minute", minute)
        put("enabled", enabled)
    }

    companion object {
        fun fromJson(json: JSONObject): Task = Task(
            id = json.optInt("id"),
            title = json.optString("title"),
            dayOfMonth = json.optInt("day", 1),
            hour = json.optInt("hour", 9),
            minute = json.optInt("minute", 0),
            enabled = json.optBoolean("enabled", true)
        )
    }
}
