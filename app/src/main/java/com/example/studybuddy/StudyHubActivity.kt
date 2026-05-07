package com.example.studybuddy

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class StudyHubActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private var lastMessageCount = 0
    private val quotes = listOf(
        "🚀 Success is the sum of small efforts, repeated day in and day out.",
        "📚 The expert in anything was once a beginner. Keep studying!",
        "🔥 Your only limit is you. Push your boundaries today!",
        "🎯 Don't stop until you're proud. Your goals are within reach.",
        "✨ Believe you can and you're halfway there!",
        "📈 Small progress is still progress. Keep going!"
    )

    private val pdfPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            Toast.makeText(this, "PDF Selected: ${it.path}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_study_hub)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

        // Listen for new room messages to update badge
        val badge = findViewById<TextView>(R.id.tvBadgeCount)
        database.child("room_messages").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val count = snapshot.childrenCount.toInt()
                if (lastMessageCount == 0) {
                    lastMessageCount = count
                    badge.visibility = android.view.View.GONE
                } else if (count > lastMessageCount) {
                    val newMessages = count - lastMessageCount
                    badge.text = newMessages.toString()
                    badge.visibility = android.view.View.VISIBLE
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // Handle Logout Button in Toolbar
        findViewById<LinearLayout>(R.id.btnLogoutToolbar).setOnClickListener {
            showLogoutConfirmation()
        }

        findViewById<FrameLayout>(R.id.btnNotificationsToolbar).setOnClickListener {
            startActivity(Intent(this, UpdatesActivity::class.java))
            badge.visibility = android.view.View.GONE
            // Fetch current count to 'clear' notifications
            database.child("room_messages").get().addOnSuccessListener { 
                lastMessageCount = it.childrenCount.toInt()
            }
        }

        val cardPdf = findViewById<MaterialCardView>(R.id.cardPdfNotes)
        val cardPlanner = findViewById<MaterialCardView>(R.id.cardPlanner)
        val cardNotify = findViewById<MaterialCardView>(R.id.cardNotifications)
        val tvBannerTitle = findViewById<TextView>(R.id.tvBannerTitle)
        val tvBannerText = findViewById<TextView>(R.id.tvBannerText)
        
        // Dynamic banner update
        val randomQuote = quotes.random()
        tvBannerText.text = randomQuote
        tvBannerTitle.text = getString(R.string.daily_motivation)

        val btnEnterRoom = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnEnterRoom)
        val btnPrivateChat = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnPrivateChat)

        btnEnterRoom.setOnClickListener {
            val intent = Intent(this, StudyRoomActivity::class.java)
            startActivity(intent)
        }

        btnPrivateChat.setOnClickListener {
            val intent = Intent(this, UsersActivity::class.java)
            startActivity(intent)
        }

        cardPdf.setOnClickListener {
            // Functionality: Open PDF Picker
            pdfPicker.launch("application/pdf")
        }

        cardPlanner.setOnClickListener {
            // Functionality: Open Planner Activity
            startActivity(Intent(this, PlannerActivity::class.java))
        }

        cardNotify.setOnClickListener {
            // Functional: Execute a 'Check-In' and show motivation
            val quote = quotes.random()
            AlertDialog.Builder(this)
                .setTitle(R.string.motivation_title)
                .setMessage(getString(R.string.motivation_prompt, quote))
                .setPositiveButton(R.string.lets_go) { _, _ ->
                    startActivity(Intent(this, UpdatesActivity::class.java))
                }
                .setNegativeButton(R.string.maybe_later, null)
                .show()
        }

        if (auth.currentUser == null) {
            goToLogin()
        }
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(R.string.logout)
            .setMessage(R.string.logout_confirm_msg)
            .setPositiveButton(R.string.yes) { _, _ ->
                auth.signOut()
                goToLogin()
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
