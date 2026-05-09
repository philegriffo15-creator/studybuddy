package com.example.studybuddy

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvSignupRedirect = findViewById<TextView>(R.id.tvSignupRedirect)
        val btnBack = findViewById<android.widget.ImageButton>(R.id.btnBack)

        AnimationHelper.applyScaleAnimation(btnLogin)

        // Staggered animation
        AnimationHelper.staggeredFadeIn(
            findViewById(R.id.tvBrand),
            findViewById(R.id.tvTitle),
            findViewById(R.id.tvDescription),
            findViewById(R.id.tilEmail),
            findViewById(R.id.tilPassword),
            btnLogin,
            tvSignupRedirect
        )

        btnBack.setOnClickListener { finish() }

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            startActivity(Intent(this, StudyHubActivity::class.java))
                            finish()
                        } else {
                            val errorMessage = getString(R.string.error_login_failed, task.exception?.message)
                            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
                        }
                    }
            } else {
                Toast.makeText(this, getString(R.string.error_fill_all_fields), Toast.LENGTH_SHORT).show()
            }
        }

        tvSignupRedirect.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
            finish()
        }
    }
}