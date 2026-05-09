package com.example.studybuddy

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class PlannerActivity : AppCompatActivity() {

    private lateinit var database: DatabaseReference
    private val currentUid = FirebaseAuth.getInstance().currentUser?.uid
    private var selectedDate: String = ""

    private lateinit var taskAdapter: TaskAdapter
    private val taskList = mutableListOf<StudyTask>()
    private var taskListener: ValueEventListener? = null
    private var streakListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_planner)

        val toolbar = findViewById<MaterialToolbar>(R.id.plannerToolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val calendar = findViewById<CalendarView>(R.id.calendarView)
        val tvDateTitle = findViewById<TextView>(R.id.tvDateTitle)
        val rvTasks = findViewById<RecyclerView>(R.id.rvTasks)
        val fabAdd = findViewById<FloatingActionButton>(R.id.fabAddTask)

        // Initialize with today's date
        selectedDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        tvDateTitle.text = getString(R.string.tasks_for_date, selectedDate)

        // Setup RecyclerView
        taskAdapter = TaskAdapter(taskList) { task, isChecked ->
            updateTaskStatus(task, isChecked)
        }
        rvTasks.layoutManager = LinearLayoutManager(this)
        rvTasks.adapter = taskAdapter

        // Load tasks and streak
        loadTasks()
        loadStreak()

        calendar.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val calendarInstance = Calendar.getInstance()
            calendarInstance.set(year, month, dayOfMonth)
            selectedDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(calendarInstance.time)
            tvDateTitle.text = getString(R.string.tasks_for_date, selectedDate)
            loadTasks()
        }

        fabAdd.setOnClickListener {
            showAddTaskDialog()
        }
    }

    private fun loadTasks() {
        val uid = currentUid ?: return
        
        // Remove old listener if exists
        taskListener?.let { database.removeEventListener(it) }
        
        database = FirebaseDatabase.getInstance().getReference("Tasks").child(uid).child(selectedDate)
        
        taskListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                taskList.clear()
                for (data in snapshot.children) {
                    val task = data.getValue(StudyTask::class.java)?.copy(id = data.key)
                    if (task != null) taskList.add(task)
                }
                taskAdapter.notifyDataSetChanged()
                updateProgress()
                checkAndUpdateStreak()
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        database.addValueEventListener(taskListener!!)
    }

    private fun updateProgress() {
        val tvProgress = findViewById<TextView>(R.id.tvProgressPercent)
        val progressBar = findViewById<ProgressBar>(R.id.plannerProgressBar)
        
        if (taskList.isEmpty()) {
            tvProgress.text = getString(R.string.percent_format, 0)
            progressBar.progress = 0
            return
        }
        val completed = taskList.count { it.status == "completed" }
        val percent = (completed * 100) / taskList.size
        tvProgress.text = getString(R.string.percent_format, percent)
        progressBar.progress = percent
    }

    private fun loadStreak() {
        val uid = currentUid ?: return
        
        streakListener?.let { 
            FirebaseDatabase.getInstance().getReference("Users").child(uid).child("streak").removeEventListener(it) 
        }

        streakListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val streak = snapshot.getValue(Int::class.java) ?: 0
                findViewById<TextView>(R.id.tvStreakCount).text = getString(R.string.days_count, streak)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        
        FirebaseDatabase.getInstance().getReference("Users").child(uid)
            .child("streak").addValueEventListener(streakListener!!)
    }

    private fun checkAndUpdateStreak() {
        val uid = currentUid ?: return
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        
        // Only update streak if we are looking at today and tasks are completed
        if (selectedDate == today && taskList.isNotEmpty() && taskList.all { it.status == "completed" }) {
            val userRef = FirebaseDatabase.getInstance().getReference("Users").child(uid)
            userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val user = snapshot.getValue(User::class.java)
                    if (user != null && user.lastCompletedDate != today) {
                        val newStreak = user.streak + 1
                        userRef.child("streak").setValue(newStreak)
                        userRef.child("lastCompletedDate").setValue(today)
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }

    private fun showAddTaskDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_task, null)
        val etSubject = view.findViewById<EditText>(R.id.etSubject)
        val etGoal = view.findViewById<EditText>(R.id.etGoal)
        val etTime = view.findViewById<EditText>(R.id.etTime)
        val cbGroup = view.findViewById<CheckBox>(R.id.cbIsGroupTask)

        AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton(R.string.add) { _, _ ->
                val subject = etSubject.text.toString().trim()
                val goal = etGoal.text.toString().trim()
                val time = etTime.text.toString().trim()
                val isGroup = cbGroup.isChecked

                if (subject.isNotEmpty() && goal.isNotEmpty()) {
                    val taskId = database.push().key ?: return@setPositiveButton
                    val task = StudyTask(taskId, subject, goal, time, selectedDate, "pending", isGroup, currentUid)
                    database.child(taskId).setValue(task)
                    
                    if (isGroup) {
                        // Also add to a shared nodes for others to see if needed
                        FirebaseDatabase.getInstance().getReference("SharedTasks")
                            .child(selectedDate).child(taskId).setValue(task)
                    }
                } else {
                    Toast.makeText(this, getString(R.string.error_fill_subject_goal), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateTaskStatus(task: StudyTask, isChecked: Boolean) {
        val newStatus = if (isChecked) "completed" else "pending"
        val uid = currentUid
        if (task.id != null && uid != null) {
            FirebaseDatabase.getInstance().getReference("Tasks")
                .child(uid)
                .child(selectedDate)
                .child(task.id)
                .child("status")
                .setValue(newStatus)
            
            if (task.isGroupTask) {
                FirebaseDatabase.getInstance().getReference("SharedTasks")
                    .child(selectedDate)
                    .child(task.id)
                    .child("status")
                    .setValue(newStatus)
            }
        }
    }
}
