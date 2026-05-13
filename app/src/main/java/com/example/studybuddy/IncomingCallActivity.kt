package com.example.studybuddy

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class IncomingCallActivity : AppCompatActivity() {

    private val database = FirebaseDatabase.getInstance().reference
    private val currentUid = FirebaseAuth.getInstance().currentUser?.uid
    private var callerUid: String? = null
    private var callType: String? = null
    private var callRef: DatabaseReference? = null
    private var mediaPlayer: android.media.MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incoming_call)

        callerUid = intent.getStringExtra("CALLER_UID")
        val callerName = intent.getStringExtra("CALLER_NAME")
        callType = intent.getStringExtra("CALL_TYPE")

        findViewById<TextView>(R.id.tvCallerName).text = callerName ?: "Incoming Call"
        findViewById<TextView>(R.id.tvCallType).text = "StudyBuddy ${callType?.capitalize()} Call"

        startRingtone()

        if (currentUid != null && callerUid != null) {
            callRef = database.child("calls").child(currentUid!!).child(callerUid!!)
            
            // Listen if caller cancels the call
            callRef?.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val status = snapshot.child("status").value as? String
                    if (status == "ended") {
                        stopRingtone()
                        finish()
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }

        findViewById<FloatingActionButton>(R.id.btnAccept).setOnClickListener {
            acceptCall()
        }

        findViewById<FloatingActionButton>(R.id.btnDecline).setOnClickListener {
            declineCall()
        }
    }

    private fun startRingtone() {
        try {
            var notificationUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
            if (notificationUri == null) {
                notificationUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
            }

            mediaPlayer = android.media.MediaPlayer().apply {
                setDataSource(this@IncomingCallActivity, notificationUri)
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepareAsync()
                setOnPreparedListener { it.start() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopRingtone() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun acceptCall() {
        stopRingtone()
        callRef?.child("status")?.setValue("accepted")?.addOnSuccessListener {
            val intent = Intent(this, CallActivity::class.java).apply {
                putExtra("CALL_TYPE", callType)
                putExtra("RECEIVER_UID", callerUid) // From receiver's perspective, caller is the other party
                putExtra("IS_INCOMING", true)
            }
            startActivity(intent)
            finish()
        }
    }

    private fun declineCall() {
        stopRingtone()
        callRef?.child("status")?.setValue("ended")?.addOnSuccessListener {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRingtone()
    }
}
