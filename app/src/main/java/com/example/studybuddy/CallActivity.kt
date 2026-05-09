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
    private val database = FirebaseDatabase.getInstance().reference
    private val currentUid = FirebaseAuth.getInstance().currentUser?.uid
    private var isMuted = false
    private var lensFacing = CameraSelector.LENS_FACING_FRONT
    private var isVideoCall = false

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
        val receiverUid = intent.getStringExtra("RECEIVER_UID")
        isVideoCall = callType == "video"

        ivBlurBackground = findViewById(R.id.ivBlurBackground)
        tvCallStatus = findViewById(R.id.tvCallStatus)
        val btnMute = findViewById<FloatingActionButton>(R.id.btnMute)
        val btnFlipCamera = findViewById<FloatingActionButton>(R.id.btnFlipCamera)
        val btnEndCall = findViewById<FloatingActionButton>(R.id.btnEndCall)

        tvCallStatus.text = "Calling ${receiverUid ?: "User"}..."

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
            endCall(receiverUid)
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

        btnFlipCamera.setOnClickListener {
            lensFacing = if (CameraSelector.LENS_FACING_FRONT == lensFacing) {
                CameraSelector.LENS_FACING_BACK
            } else {
                CameraSelector.LENS_FACING_FRONT
            }
            startCamera()
        }

        startSignaling(receiverUid, callType)
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
        callRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.child("status").value == "accepted") {
                    tvCallStatus.text = "On Call"
                } else if (snapshot.child("status").value == "ended") {
                    finish()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun endCall(receiverUid: String?) {
        enableMicrophone(true) // Reset mute state on exit
        if (receiverUid != null && currentUid != null) {
            database.child("calls").child(receiverUid).child(currentUid).child("status").setValue("ended")
        }
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        enableMicrophone(true) // Ensure mic is not left muted
    }
}
