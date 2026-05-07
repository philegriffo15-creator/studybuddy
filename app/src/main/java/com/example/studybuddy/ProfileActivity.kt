package com.example.studybuddy // Ensure this matches your package name!

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class ProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        
        val tvName = findViewById<TextView>(R.id.tvProfileName)
        val tvEmail = findViewById<TextView>(R.id.tvUserEmail)
        val tvCourse = findViewById<TextView>(R.id.tvCourseDetail)
        val tvStreak = findViewById<TextView>(R.id.tvStreakMetric)
        val btnLogout = findViewById<Button>(R.id.btnLogout)

        if (currentUser != null) {
            tvEmail.text = currentUser.email
            
            database = FirebaseDatabase.getInstance().getReference("Users").child(currentUser.uid)
            database.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val user = snapshot.getValue(User::class.java)
                    if (user != null) {
                        tvName.text = user.fullName
                        tvCourse.text = getString(R.string.course_label, user.course)
                        tvStreak.text = user.streak.toString()
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }

        btnLogout.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}
