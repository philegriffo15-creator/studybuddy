package com.example.studybuddy

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import jp.wasabeef.blurry.Blurry

class CallActivity : AppCompatActivity() {

    private lateinit var ivBlurBackground: ImageView
    private lateinit var tvCallStatus: TextView
    private var mediaPlayer: android.media.MediaPlayer? = null
    private val database = FirebaseDatabase.getInstance().reference
    private val currentUid = FirebaseAuth.getInstance().currentUser?.uid
    private var isMuted = false
    private var isSpeakerOn = false
    private var lensFacing = CameraSelector.LENS_FACING_FRONT
    private var isVideoCall = false
    private var isIncoming = false
    private var callerName: String? = null

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        
        if (isVideoCall && cameraGranted) {
            startCamera()
        }
        if (audioGranted) {
            enableMicrophone(true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)

        val callType = intent.getStringExtra("CALL_TYPE")
        val otherUid = intent.getStringExtra("RECEIVER_UID")
        isIncoming = intent.getBooleanExtra("IS_INCOMING", false)
        callerName = intent.getStringExtra("CALLER_NAME")
        isVideoCall = callType == "video"
        isSpeakerOn = isVideoCall // Default speaker ON for video, OFF for audio

        ivBlurBackground = findViewById(R.id.ivBlurBackground)
        tvCallStatus = findViewById(R.id.tvCallStatus)
        val btnMute = findViewById<FloatingActionButton>(R.id.btnMute)
        val btnSpeaker = findViewById<FloatingActionButton>(R.id.btnSpeaker)
        val btnFlipCamera = findViewById<FloatingActionButton>(R.id.btnFlipCamera)
        val btnEndCall = findViewById<FloatingActionButton>(R.id.btnEndCall)

        tvCallStatus.text = if (isIncoming) "On Call" else "Calling ${otherUid ?: "User"}..."

        // Initialize speaker state
        toggleSpeaker(isSpeakerOn)
        if (isSpeakerOn) {
            btnSpeaker.setImageResource(R.drawable.ic_speaker)
            btnSpeaker.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#448AFF"))
        } else {
            btnSpeaker.setImageResource(R.drawable.ic_speaker_off)
            btnSpeaker.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#44FFFFFF"))
        }

        // Outgoing call should play a ringing sound until accepted
        if (!isIncoming) {
            startRingingSound(false) // false means outgoing "waiting" tone
        }

        // Simulated blurred background
        Glide.with(this)
            .load(R.drawable.ic_launcher_foreground) // Placeholder
            .into(ivBlurBackground)
        
        ivBlurBackground.post {
            Blurry.with(this).radius(25).sampling(2).capture(ivBlurBackground).into(ivBlurBackground)
        }

        if (!isVideoCall) {
            findViewById<View>(R.id.localVideoCard).visibility = View.GONE
            btnFlipCamera.visibility = View.GONE
        }

        checkAndRequestPermissions()

        btnEndCall.setOnClickListener {
            endCall(otherUid)
        }

        btnMute.setOnClickListener {
            isMuted = !isMuted
            enableMicrophone(!isMuted)
            if (isMuted) {
                btnMute.setImageResource(R.drawable.ic_mic_off)
                btnMute.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E91E63")) 
                Toast.makeText(this, "Muted", Toast.LENGTH_SHORT).show()
            } else {
                btnMute.setImageResource(R.drawable.ic_mic)
                btnMute.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#44FFFFFF"))
                Toast.makeText(this, "Unmuted", Toast.LENGTH_SHORT).show()
            }
        }

        btnSpeaker.setOnClickListener {
            isSpeakerOn = !isSpeakerOn
            toggleSpeaker(isSpeakerOn)
            if (isSpeakerOn) {
                btnSpeaker.setImageResource(R.drawable.ic_speaker)
                btnSpeaker.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#448AFF"))
                Toast.makeText(this, "Speaker On", Toast.LENGTH_SHORT).show()
            } else {
                btnSpeaker.setImageResource(R.drawable.ic_speaker_off)
                btnSpeaker.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#44FFFFFF"))
                Toast.makeText(this, "Speaker Off", Toast.LENGTH_SHORT).show()
            }
        }

        btnFlipCamera.setOnClickListener {
            lensFacing = if (CameraSelector.LENS_FACING_FRONT == lensFacing) {
                CameraSelector.LENS_FACING_BACK
            } else {
                CameraSelector.LENS_FACING_FRONT
            }
            startCamera()
        }

        if (isIncoming) {
            listenForCallStatus(otherUid)
        } else {
            startSignaling(otherUid, callType)
        }
    }

    private fun startRingingSound(isIncoming: Boolean) {
        try {
            var notificationUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
            if (notificationUri == null) {
                notificationUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
            }
            
            mediaPlayer = android.media.MediaPlayer().apply {
                setDataSource(this@CallActivity, notificationUri)
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepareAsync()
                setOnPreparedListener { 
                    it.start() 
                    Log.d("CallActivity", "Ringing sound started")
                }
            }
        } catch (e: Exception) {
            Log.e("CallActivity", "Error playing ringtone", e)
        }
    }

    private fun stopRingingSound() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun toggleSpeaker(enabled: Boolean) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.isSpeakerphoneOn = enabled
        // Use MODE_IN_COMMUNICATION for VOIP calls
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (isVideoCall && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }

        if (permissions.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissions.toTypedArray())
        } else {
            if (isVideoCall) startCamera()
            enableMicrophone(true)
        }
    }

    private fun enableMicrophone(enabled: Boolean) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.isMicrophoneMute = !enabled
    }

    private fun startCamera() {
        if (!isVideoCall) return
        
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    val previewView = findViewById<PreviewView>(R.id.localPreviewView)
                    if (previewView != null) {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                }

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview)
            } catch (exc: Exception) {
                Log.e("CallActivity", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun listenForCallStatus(otherUid: String?) {
        if (otherUid == null || currentUid == null) return
        // For incoming call, the record is at calls/$currentUid/$otherUid
        val callRef = database.child("calls").child(currentUid).child(otherUid)
        callRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.child("status").value == "ended") {
                    stopRingingSound()
                    finish()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun startSignaling(receiverUid: String?, type: String?) {
        if (receiverUid == null || currentUid == null) return
        
        val callRef = database.child("calls").child(receiverUid).child(currentUid)
        val callData = mapOf(
            "type" to type,
            "status" to "ringing",
            "callerName" to (callerName ?: "StudyBuddy User")
        )
        callRef.setValue(callData)
        
        // Listen for acceptance
        callRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.child("status").value
                if (status == "accepted") {
                    tvCallStatus.text = "On Call"
                    stopRingingSound()
                } else if (status == "ended") {
                    stopRingingSound()
                    finish()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun endCall(otherUid: String?) {
        stopRingingSound()
        enableMicrophone(true)
        if (otherUid != null && currentUid != null) {
            if (isIncoming) {
                database.child("calls").child(currentUid).child(otherUid).child("status").setValue("ended")
            } else {
                database.child("calls").child(otherUid).child(currentUid).child("status").setValue("ended")
            }
        }
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRingingSound()
        enableMicrophone(true)
    }
}
