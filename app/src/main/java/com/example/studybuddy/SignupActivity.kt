package com.example.studybuddy

import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class SignupActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        auth = FirebaseAuth.getInstance()

        val etFullName = findViewById<EditText>(R.id.etFullName)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val etCourse = findViewById<AutoCompleteTextView>(R.id.etCourse)
        val btnCreate = findViewById<MaterialButton>(R.id.btnCreate)
        val tvLoginRedirect = findViewById<TextView>(R.id.tvLoginRedirect)
        val btnBack = findViewById<android.widget.ImageButton>(R.id.btnBack)

        AnimationHelper.applyScaleAnimation(btnCreate)
        
        // Staggered animation
        AnimationHelper.staggeredFadeIn(
            findViewById(R.id.tvBrand),
            findViewById(R.id.tvTitle),
            findViewById(R.id.tvDescription),
            findViewById(R.id.tilFullName),
            findViewById(R.id.tilEmail),
            findViewById(R.id.tilPassword),
            findViewById(R.id.tilCourse),
            btnCreate,
            tvLoginRedirect
        )

        btnBack.setOnClickListener { 
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            finish() 
        }

        // Setup Course List Adapter
        val courses = resources.getStringArray(R.array.course_list)
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, courses)
        etCourse.setAdapter(adapter)

        btnCreate.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val name = etFullName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val course = etCourse.text.toString().trim()

            if (name.isEmpty() || email.isEmpty() || password.isEmpty() || course.isEmpty()) {
                Toast.makeText(this, getString(R.string.error_fill_all_fields), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, getString(R.string.error_password_length), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val userId = auth.currentUser?.uid
                        val userMap = mapOf(
                            "fullName" to name,
                            "email" to email,
                            "course" to course
                        )

                        if (userId != null) {
                            FirebaseDatabase.getInstance().getReference("Users")
                                .child(userId)
                                .setValue(userMap)
                                .addOnCompleteListener { dbTask ->
                                    if (dbTask.isSuccessful) {
                                        Toast.makeText(this, getString(R.string.success_account_created), Toast.LENGTH_SHORT).show()
                                        startActivity(Intent(this, StudyHubActivity::class.java))
                                        finish()
                                    } else {
                                        val errorMsg = getString(R.string.error_database, dbTask.exception?.message)
                                        Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                        }
                    } else {
                        val errorMsg = getString(R.string.error_signup_failed, task.exception?.message)
                        Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
                    }
                }
        }

        tvLoginRedirect.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
