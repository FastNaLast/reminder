package ru.monthly.reminder

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import ru.monthly.reminder.databinding.ActivityMainBinding
import ru.monthly.reminder.databinding.DialogPermissionsBinding
import ru.monthly.reminder.databinding.DialogTaskBinding
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var store: TaskStore
    private lateinit var adapter: TaskAdapter

    private val dateFormat = DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale("ru"))

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        Notifications.ensureChannel(this)
        store = TaskStore(this)

        adapter = TaskAdapter(
            onClick = { showTaskDialog(it) },
            onToggle = ::setEnabled,
            onLongClick = { confirmDelete(it) }
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        binding.fab.setOnClickListener { showTaskDialog(null) }

        askNotificationsIfNeeded()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_permissions -> {
            showPermissionsDialog()
            true
        }
        R.id.action_about -> {
            showAboutDialog()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    // ---------------------------------------------------------------- список

    private fun refresh() {
        // Включённые — сверху, ближайшие по времени — первыми.
        val tasks = store.all().sortedWith(
            compareBy<Task>({ !it.enabled }, { AlarmScheduler.nextTrigger(it) })
        )
        adapter.submit(tasks)
        val empty = tasks.isEmpty()
        binding.empty.visibility = if (empty) View.VISIBLE else View.GONE
        binding.recycler.visibility = if (empty) View.GONE else View.VISIBLE

        // Пересчитываем будильники: setExact заменяет предыдущий с тем же PendingIntent.
        tasks.filter { it.enabled }.forEach { AlarmScheduler.schedule(this, it) }
    }

    private fun setEnabled(task: Task, enabled: Boolean) {
        val updated = task.copy(enabled = enabled)
        store.update(updated)
        if (enabled) AlarmScheduler.schedule(this, updated) else AlarmScheduler.cancel(this, updated.id)
        refresh()
    }

    private fun confirmDelete(task: Task) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_title)
            .setMessage(getString(R.string.delete_message, task.title))
            .setPositiveButton(R.string.delete) { _, _ ->
                AlarmScheduler.cancel(this, task.id)
                Notifications.cancel(this, task.id)
                store.delete(task.id)
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------------------------------------------------------------- диалог задачи

    private fun showTaskDialog(existing: Task?) {
        val dialogBinding = DialogTaskBinding.inflate(layoutInflater)
        var day = existing?.dayOfMonth ?: 1
        var hour = existing?.hour ?: 9
        var minute = existing?.minute ?: 0

        dialogBinding.inputTitle.setText(existing?.title.orEmpty())

        fun updatePreview() {
            dialogBinding.btnDay.text = getString(R.string.day_value, day)
            dialogBinding.btnTime.text =
                String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
            val preview = AlarmScheduler.nextTrigger(
                Task(id = 0, title = "", dayOfMonth = day, hour = hour, minute = minute),
                LocalDateTime.now()
            )
            dialogBinding.tvNext.text = getString(R.string.next_run, preview.format(dateFormat))
        }
        updatePreview()

        dialogBinding.btnDay.setOnClickListener {
            showDayPicker(day) { picked ->
                day = picked
                updatePreview()
            }
        }
        dialogBinding.btnTime.setOnClickListener {
            showTimePicker(hour, minute) { pickedHour, pickedMinute ->
                hour = pickedHour
                minute = pickedMinute
                updatePreview()
            }
        }

        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) R.string.new_task else R.string.edit_task)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)

        if (existing != null) {
            builder.setNeutralButton(R.string.delete, null)
        }

        val dialog = builder.create()

        dialog.setOnShowListener {
            if (existing != null) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    dialog.dismiss()
                    confirmDelete(existing)
                }
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = dialogBinding.inputTitle.text?.toString()?.trim().orEmpty()
                if (title.isEmpty()) {
                    dialogBinding.titleLayout.error = getString(R.string.title_required)
                    return@setOnClickListener
                }
                val task = Task(
                    id = existing?.id ?: 0,
                    title = title,
                    dayOfMonth = day,
                    hour = hour,
                    minute = minute,
                    enabled = existing?.enabled ?: true
                )
                if (existing == null) {
                    val saved = store.add(task)
                    AlarmScheduler.schedule(this, saved)
                } else {
                    store.update(task)
                    AlarmScheduler.schedule(this, task)
                }
                dialog.dismiss()
                refresh()
            }
        }
        dialog.show()
    }

    private fun showDayPicker(current: Int, onPicked: (Int) -> Unit) {
        val view = layoutInflater.inflate(R.layout.dialog_number, null)
        val picker = view.findViewById<NumberPicker>(R.id.picker)
        picker.minValue = 1
        picker.maxValue = 31
        picker.value = current
        picker.wrapSelectorWheel = true

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.day_of_month)
            .setView(view)
            .setPositiveButton(R.string.ok) { _, _ -> onPicked(picker.value) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showTimePicker(hour: Int, minute: Int, onPicked: (Int, Int) -> Unit) {
        val is24Hour = android.text.format.DateFormat.is24HourFormat(this)
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(if (is24Hour) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
            .setHour(hour)
            .setMinute(minute)
            .setTitleText(R.string.time)
            .build()
        picker.addOnPositiveButtonClickListener { onPicked(picker.hour, picker.minute) }
        picker.show(supportFragmentManager, "time_picker")
    }

    // ---------------------------------------------------------------- разрешения

    private fun askNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsGranted()) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun notificationsGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            Notifications.areEnabled(this)
        }

    private fun exactAlarmsGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        } else {
            true
        }

    private fun batteryUnrestricted(): Boolean =
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    private fun showPermissionsDialog() {
        val dialogBinding = DialogPermissionsBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.permissions)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.close, null)
            .create()

        fun render() {
            setStatus(dialogBinding.statusNotifications, notificationsGranted())
            setStatus(dialogBinding.statusExact, exactAlarmsGranted())
            setStatus(dialogBinding.statusBattery, batteryUnrestricted())
        }

        dialogBinding.btnNotifications.setOnClickListener {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            openSettings(intent)
            dialog.dismiss()
        }

        dialogBinding.btnExact.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                openSettings(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        "package:$packageName".toUri()
                    )
                )
            } else {
                openSettings(Intent(Settings.ACTION_SETTINGS))
            }
            dialog.dismiss()
        }

        dialogBinding.btnBattery.setOnClickListener {
            val request = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                "package:$packageName".toUri()
            )
            if (!tryOpen(request)) {
                openSettings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
            dialog.dismiss()
        }

        dialogBinding.btnTestNotification.setOnClickListener {
            if (!notificationsGranted()) {
                Toast.makeText(this, R.string.test_notification_blocked, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val sample = Task(
                id = TEST_NOTIFICATION_ID,
                title = getString(R.string.test_notification_title),
                dayOfMonth = 1,
                hour = 9,
                minute = 0,
                enabled = true
            )
            Notifications.show(this, sample, snooze = false)
            Toast.makeText(this, R.string.test_notification_sent, Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        render()
        dialog.show()
    }

    private fun setStatus(view: TextView, granted: Boolean) {
        view.setText(if (granted) R.string.status_on else R.string.status_off)
        view.setTextColor(
            ContextCompat.getColor(this, if (granted) R.color.status_ok else R.color.status_bad)
        )
    }

    private fun tryOpen(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }

    private fun openSettings(intent: Intent) {
        if (!tryOpen(intent)) {
            Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.about_title)
            .setMessage(R.string.about_text)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    companion object {
        /** Идентификатор «задачи» для проверочного уведомления. */
        private const val TEST_NOTIFICATION_ID = 999_999
    }
}
