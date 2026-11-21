package ru.monthly.reminder

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ru.monthly.reminder.databinding.ItemTaskBinding
import java.time.format.DateTimeFormatter
import java.util.Locale

class TaskAdapter(
    private val onClick: (Task) -> Unit,
    private val onToggle: (Task, Boolean) -> Unit,
    private val onLongClick: (Task) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    private val dateFormat = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale("ru"))
    private var items: List<Task> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<Task>) {
        items = list
        notifyDataSetChanged()
    }

    inner class TaskViewHolder(val binding: ItemTaskBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder =
        TaskViewHolder(ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = items[position]
        val binding = holder.binding
        val context = binding.root.context

        binding.title.text = task.title
        binding.subtitle.text = if (task.enabled) {
            val time = String.format(Locale.getDefault(), "%02d:%02d", task.hour, task.minute)
            val next = AlarmScheduler.nextTrigger(task).format(dateFormat)
            context.getString(R.string.every_month, task.dayOfMonth, time) +
                "\n" + context.getString(R.string.next_run_short, next)
        } else {
            context.getString(R.string.disabled)
        }
        binding.root.alpha = if (task.enabled) 1f else 0.45f

        binding.toggle.setOnCheckedChangeListener(null)
        binding.toggle.isChecked = task.enabled
        binding.toggle.setOnCheckedChangeListener { _, checked -> onToggle(task, checked) }

        binding.root.setOnClickListener { onClick(task) }
        binding.root.setOnLongClickListener {
            onLongClick(task)
            true
        }
    }
}
