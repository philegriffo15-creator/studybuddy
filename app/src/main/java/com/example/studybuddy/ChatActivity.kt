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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.io.IOException

class ChatActivity : AppCompatActivity() {
    private lateinit var database: DatabaseReference
    private val storage = FirebaseStorage.getInstance().getReference("chats")
    private val currentUid = FirebaseAuth.getInstance().currentUser?.uid

    private lateinit var messageAdapter: MessageAdapter
    private val messageList = mutableListOf<Message>()
    private var chatRoomId: String? = null

    private var mediaRecorder: MediaRecorder? = null
    private var audioFilePath: String = ""
    private var isRecording = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        val receiverUid = intent.getStringExtra("receiver_uid")
        val receiverName = intent.getStringExtra("receiver_name")

        findViewById<TextView>(R.id.userName).text = receiverName ?: "Chat"

        if (currentUid != null && receiverUid != null) {
            chatRoomId = if (currentUid < receiverUid) "${currentUid}_${receiverUid}" else "${receiverUid}_${currentUid}"
            database = FirebaseDatabase.getInstance().getReference("chats").child(chatRoomId!!)
        } else {
            database = FirebaseDatabase.getInstance().getReference("messages")
        }

        val messageBox = findViewById<EditText>(R.id.messageBox)
        val btnSend = findViewById<ImageButton>(R.id.sendButton)
        val btnImage = findViewById<ImageButton>(R.id.attachmentButton)
        val btnVoice = findViewById<ImageButton>(R.id.voiceMessageButton)
        val rvMessages = findViewById<RecyclerView>(R.id.rvMessages)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)

        btnBack.setOnClickListener { finish() }

        messageAdapter = MessageAdapter(messageList)
        rvMessages.layoutManager = LinearLayoutManager(this)
        rvMessages.adapter = messageAdapter

        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                messageList.clear()
                for (data in snapshot.children) {
                    val message = data.getValue(Message::class.java)
                    if (message != null) messageList.add(message)
                }
                messageAdapter.notifyDataSetChanged()
                rvMessages.scrollToPosition(messageList.size - 1)
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        btnSend.setOnClickListener {
            val text = messageBox.text.toString().trim()
            if (text.isNotEmpty()) {
                database.push().setValue(Message(sender = currentUid, message = text, type = "text"))
                messageBox.setText("")
            }
        }

        btnImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
            startActivityForResult(intent, 100)
        }

        btnVoice.setOnClickListener {
            if (checkPermissions()) {
                if (!isRecording) {
                    startRecording(btnVoice)
                } else {
                    stopAndUploadRecording(btnVoice)
                }
            } else {
                requestPermissions()
            }
        }

        findViewById<Button>(R.id.joinRoomButton).setOnClickListener {
            startActivity(Intent(this, StudyRoomActivity::class.java).apply { putExtra("TOPIC_NAME", "Mobile App Dev") })
        }
    }

    private fun checkPermissions() = ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 200)
    }

    private fun startRecording(btn: ImageButton) {
        audioFilePath = "${externalCacheDir?.absolutePath}/voice_message.3gp"
        
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
            Toast.makeText(this, "Recording started...", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            Log.e("ChatActivity", "prepare() failed: ${e.message}")
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
            Log.e("ChatActivity", "stop() failed: ${e.message}")
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
                database.push().setValue(Message(sender = currentUid, audioUrl = uri.toString(), type = "audio"))
                Toast.makeText(this, "Voice message sent", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Upload failed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            val fileUri = data.data ?: return
            val ref = storage.child("images/${System.currentTimeMillis()}.jpg")
            ref.putFile(fileUri).addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { uri ->
                    database.push().setValue(Message(sender = currentUid, imageUrl = uri.toString(), type = "image"))
                }
            }
        }
    }
}
