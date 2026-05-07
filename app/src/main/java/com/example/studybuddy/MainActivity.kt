package com.example.studybuddy

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // This activity is just a "traffic controller"
        // It immediately sends the user to the Splash Screen
        val intent = Intent(this, SplashActivity::class.java)
        startActivity(intent)
        finish()
    }
}