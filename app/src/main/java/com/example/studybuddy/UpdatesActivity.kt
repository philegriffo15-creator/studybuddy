package com.example.studybuddy

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class UpdatesActivity : AppCompatActivity() {

    private lateinit var adapter: SimpleUpdateAdapter
    private val updatesList = mutableListOf<UpdateItem>()
    private val currentUid = FirebaseAuth.getInstance().currentUser?.uid

    data class UpdateItem(val text: String, val type: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_updates)

        val toolbar = findViewById<MaterialToolbar>(R.id.updatesToolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val rvUpdates = findViewById<RecyclerView>(R.id.rvUpdates)
        
        adapter = SimpleUpdateAdapter(updatesList) { item ->
            handleUpdateClick(item)
        }
        rvUpdates.layoutManager = LinearLayoutManager(this)
        rvUpdates.adapter = adapter

        loadAllUpdates()
    }

    private fun loadAllUpdates() {
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        val dbShared = FirebaseDatabase.getInstance().getReference("SharedTasks").child(today)
        val dbRoom = FirebaseDatabase.getInstance().getReference("room_messages")

        // Load Shared Tasks
        dbShared.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Clear and re-add fixed items
                updatesList.removeAll { it.type == "task" || it.type == "meta" }
                updatesList.add(UpdateItem("🔥 Keep your streak alive! Complete all tasks today.", "meta"))
                
                for (data in snapshot.children) {
                    val task = data.getValue(StudyTask::class.java)
                    if (task != null) {
                        val prefix = if (task.creatorUid == currentUid) "✅ Your Group Task: " else "👥 Shared Task: "
                        updatesList.add(UpdateItem("$prefix ${task.subject} - ${task.goal}", "task"))
                    }
                }
                adapter.notifyDataSetChanged()
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // Load Study Room Messages as notifications
        dbRoom.limitToLast(5).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                updatesList.removeAll { it.type == "room" }
                for (data in snapshot.children) {
                    val msg = data.getValue(Message::class.java)
                    if (msg != null && msg.sender != currentUid) {
                        val preview = when (msg.type) {
                            "text" -> msg.message
                            "image" -> "📷 Sent an image"
                            "audio" -> "🎤 Sent a voice note"
                            else -> "New message"
                        }
                        updatesList.add(UpdateItem("💬 Room: $preview", "room"))
                    }
                }
                adapter.notifyDataSetChanged()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun handleUpdateClick(item: UpdateItem) {
        when (item.type) {
            "room" -> {
                startActivity(Intent(this, StudyRoomActivity::class.java))
            }
            "task" -> {
                startActivity(Intent(this, PlannerActivity::class.java))
            }
        }
    }

    class SimpleUpdateAdapter(
        private val list: List<UpdateItem>,
        private val onClick: (UpdateItem) -> Unit
    ) : RecyclerView.Adapter<SimpleUpdateAdapter.VH>() {
        
        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val text: TextView = view.findViewById(R.id.tvUpdateText)
            val icon: android.widget.ImageView = view.findViewById(R.id.ivUpdateIcon)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_update, parent, false)
            return VH(view)
        }
        
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = list[position]
            holder.text.text = item.text
            
            val iconRes = when(item.type) {
                "room" -> android.R.drawable.stat_notify_chat
                "task" -> R.drawable.ic_calendar
                "meta" -> android.R.drawable.star_on
                else -> R.drawable.ic_bell
            }
            holder.icon.setImageResource(iconRes)

            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = list.size
    }
}
