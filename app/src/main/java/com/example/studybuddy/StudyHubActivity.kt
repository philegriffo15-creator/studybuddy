package com.example.studybuddy

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
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
                    badge.visibility = View.GONE
                } else if (count > lastMessageCount) {
                    val newMessages = count - lastMessageCount
                    badge.text = newMessages.toString()
                    badge.visibility = View.VISIBLE
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // Handle Logout Button in Toolbar
        findViewById<LinearLayout>(R.id.btnLogoutToolbar).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showLogoutConfirmation()
        }

        val profileBtn = findViewById<ImageView>(R.id.btnProfileToolbar)
        auth.currentUser?.uid?.let { uid ->
            database.child("Users").child(uid).child("profileImageUrl")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val imageUrl = snapshot.getValue(String::class.java)
                        if (!imageUrl.isNullOrEmpty()) {
                            Glide.with(this@StudyHubActivity)
                                .load(imageUrl)
                                .circleCrop()
                                .placeholder(R.drawable.ic_launcher_foreground)
                                .into(profileBtn)
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
        }

        profileBtn.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        findViewById<FrameLayout>(R.id.btnNotificationsToolbar).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            startActivity(Intent(this, UpdatesActivity::class.java))
            badge.visibility = View.GONE
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
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val intent = Intent(this, StudyRoomActivity::class.java)
            startActivity(intent)
        }

        btnPrivateChat.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val intent = Intent(this, UsersActivity::class.java)
            startActivity(intent)
        }

        cardPdf.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            startActivity(Intent(this, PdfNotesActivity::class.java))
        }

        cardPlanner.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            // Functionality: Open Planner Activity
            startActivity(Intent(this, PlannerActivity::class.java))
        }

        cardNotify.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
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
