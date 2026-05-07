package com.example.studybuddy

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TaskAdapter(
    private val taskList: List<StudyTask>,
    private val onTaskStatusChanged: (StudyTask, Boolean) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    class TaskViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSubject: TextView = view.findViewById(R.id.tvSubject)
        val tvGroupTag: TextView = view.findViewById(R.id.tvGroupTag)
        val tvGoal: TextView = view.findViewById(R.id.tvGoal)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
        val cbDone: CheckBox = view.findViewById(R.id.cbDone)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_study_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = taskList[position]
        holder.tvSubject.text = task.subject
        holder.tvGoal.text = task.goal
        holder.tvTime.text = task.time
        holder.cbDone.isChecked = task.status == "completed"

        holder.tvGroupTag.visibility = if (task.isGroupTask) View.VISIBLE else View.GONE

        holder.cbDone.setOnCheckedChangeListener(null)
        holder.cbDone.isChecked = task.status == "completed"
        holder.cbDone.setOnCheckedChangeListener { _, isChecked ->
            onTaskStatusChanged(task, isChecked)
        }
    }

    override fun getItemCount(): Int = taskList.size
}
