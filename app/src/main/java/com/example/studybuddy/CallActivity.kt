package com.example.studybuddy

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import jp.wasabeef.blurry.Blurry

class CallActivity : AppCompatActivity() {

    private lateinit var ivBlurBackground: ImageView
    private lateinit var tvCallStatus: TextView
    private val database = FirebaseDatabase.getInstance().reference
    private val currentUid = FirebaseAuth.getInstance().currentUser?.uid

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)

        val callType = intent.getStringExtra("CALL_TYPE")
        val receiverUid = intent.getStringExtra("RECEIVER_UID")

        ivBlurBackground = findViewById(R.id.ivBlurBackground)
        tvCallStatus = findViewById(R.id.tvCallStatus)
        val btnMute = findViewById<FloatingActionButton>(R.id.btnMute)
        val btnFlipCamera = findViewById<FloatingActionButton>(R.id.btnFlipCamera)
        val btnEndCall = findViewById<FloatingActionButton>(R.id.btnEndCall)

        tvCallStatus.text = "Calling ${receiverUid ?: "User"}..."

        // Simulated blurred background
        // In a real app, fetch receiver's profile pic URL
        Glide.with(this)
            .load(R.drawable.ic_launcher_foreground) // Placeholder
            .into(ivBlurBackground)
        
        ivBlurBackground.post {
            Blurry.with(this).radius(25).sampling(2).capture(ivBlurBackground).into(ivBlurBackground)
        }

        if (callType == "audio") {
            findViewById<View>(R.id.localVideoCard).visibility = View.GONE
            btnFlipCamera.visibility = View.GONE
        }

        btnEndCall.setOnClickListener {
            endCall(receiverUid)
        }

        btnMute.setOnClickListener {
            Toast.makeText(this, "Muted", Toast.LENGTH_SHORT).show()
        }

        btnFlipCamera.setOnClickListener {
            Toast.makeText(this, "Camera Flipped", Toast.LENGTH_SHORT).show()
        }

        startSignaling(receiverUid, callType)
    }

    private fun startSignaling(receiverUid: String?, type: String?) {
        if (receiverUid == null || currentUid == null) return
        
        val callRef = database.child("calls").child(receiverUid).child(currentUid)
        val callData = mapOf(
            "type" to type,
            "status" to "ringing",
            "callerName" to "StudyBuddy User"
        )
        callRef.setValue(callData)
        
        // Listen for acceptance
        callRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                if (snapshot.child("status").value == "accepted") {
                    tvCallStatus.text = "On Call"
                } else if (snapshot.child("status").value == "ended") {
                    finish()
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })
    }

    private fun endCall(receiverUid: String?) {
        if (receiverUid != null && currentUid != null) {
            database.child("calls").child(receiverUid).child(currentUid).child("status").setValue("ended")
        }
        finish()
    }
}
