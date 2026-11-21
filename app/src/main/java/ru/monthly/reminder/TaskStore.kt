package ru.monthly.reminder

import android.content.Context
import org.json.JSONArray

/**
 * Хранилище задач. Намеренно без базы данных: список маленький,
 * SharedPreferences + JSON полностью хватает и не тянет лишние зависимости.
 */
class TaskStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    fun all(): List<Task> {
        val raw = prefs.getString(KEY_TASKS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { Task.fromJson(array.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun get(id: Int): Task? = all().firstOrNull { it.id == id }

    fun add(task: Task): Task {
        val list = all().toMutableList()
        val id = prefs.getInt(KEY_NEXT_ID, 1)
        val saved = task.copy(id = id)
        list.add(saved)
        save(list)
        prefs.edit().putInt(KEY_NEXT_ID, id + 1).apply()
        return saved
    }

    fun update(task: Task) {
        save(all().map { if (it.id == task.id) task else it })
    }

    fun delete(id: Int) {
        save(all().filterNot { it.id == id })
    }

    private fun save(tasks: List<Task>) {
        val array = JSONArray()
        tasks.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_TASKS, array.toString()).apply()
    }

    private companion object {
        const val KEY_TASKS = "tasks_json"
        const val KEY_NEXT_ID = "next_id"
    }
}
