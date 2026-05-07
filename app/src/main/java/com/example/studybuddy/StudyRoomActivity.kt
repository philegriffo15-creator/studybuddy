package com.example.studybuddy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.io.IOException

class StudyRoomActivity : AppCompatActivity() {

    private val database = FirebaseDatabase.getInstance().getReference("room_messages")
    private val storage = FirebaseStorage.getInstance().getReference("room_attachments")
    private val currentUser = FirebaseAuth.getInstance().currentUser?.uid

    private lateinit var messageAdapter: MessageAdapter
    private val messageList = mutableListOf<Message>()

    private var mediaRecorder: MediaRecorder? = null
    private var audioFilePath: String = ""
    private var isRecording = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_study_room)

        val etMessage = findViewById<EditText>(R.id.etMessage)
        val btnSend = findViewById<FloatingActionButton>(R.id.btnSend)
        val btnMic = findViewById<ImageButton>(R.id.btnMic)
        val btnAttachment = findViewById<ImageButton>(R.id.btnAttachment)
        val tvTopic = findViewById<TextView>(R.id.tvTopicName)
        val rvChat = findViewById<RecyclerView>(R.id.rvChat)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)

        btnBack.setOnClickListener { finish() }

        tvTopic.text = intent.getStringExtra("TOPIC_NAME") ?: "Study Session"

        messageAdapter = MessageAdapter(messageList)
        rvChat.layoutManager = LinearLayoutManager(this)
        rvChat.adapter = messageAdapter

        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                messageList.clear()
                for (data in snapshot.children) {
                    val message = data.getValue(Message::class.java)
                    if (message != null) messageList.add(message)
                }
                messageAdapter.notifyDataSetChanged()
                rvChat.scrollToPosition(messageList.size - 1)
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                database.push().setValue(Message(sender = currentUser, message = text, type = "text"))
                etMessage.text.clear()
            }
        }

        btnAttachment.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
            startActivityForResult(intent, 101)
        }

        btnMic.setOnClickListener {
            if (checkPermissions()) {
                if (!isRecording) {
                    startRecording(btnMic)
                } else {
                    stopAndUploadRecording(btnMic)
                }
            } else {
                requestPermissions()
            }
        }
    }

    private fun checkPermissions() = ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 200)
    }

    private fun startRecording(btn: ImageButton) {
        audioFilePath = "${externalCacheDir?.absolutePath}/room_voice.3gp"
        
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            MediaRecorder()
        }

        try {
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(audioFilePath)
                prepare()
                start()
            }
            isRecording = true
            btn.setColorFilter(Color.RED)
            Toast.makeText(this, "Recording Room Voice...", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            Log.e("StudyRoomActivity", "prepare() failed: ${e.message}")
            Toast.makeText(this, "Recording failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopAndUploadRecording(btn: ImageButton) {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e("StudyRoomActivity", "stop() failed: ${e.message}")
        }
        mediaRecorder = null
        isRecording = false
        btn.setColorFilter(Color.WHITE)
        uploadAudio()
    }

    private fun uploadAudio() {
        val file = android.net.Uri.fromFile(File(audioFilePath))
        val ref = storage.child("audio/${System.currentTimeMillis()}.3gp")
        ref.putFile(file).addOnSuccessListener {
            ref.downloadUrl.addOnSuccessListener { uri ->
                database.push().setValue(Message(sender = currentUser, audioUrl = uri.toString(), type = "audio"))
                Toast.makeText(this, "Voice shared with room", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Upload failed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101 && resultCode == RESULT_OK && data != null) {
            val fileUri = data.data ?: return
            val ref = storage.child("images/${System.currentTimeMillis()}.jpg")
            ref.putFile(fileUri).addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { uri ->
                    database.push().setValue(Message(sender = currentUser, imageUrl = uri.toString(), type = "image"))
                }
            }
        }
    }
}
