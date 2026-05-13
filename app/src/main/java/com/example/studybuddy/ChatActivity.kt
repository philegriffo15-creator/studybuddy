package com.example.studybuddy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
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
    private lateinit var speechToTextHelper: SpeechToTextHelper
    private var replyingToMessage: Message? = null
    private var editingMessage: Message? = null

    private var mediaRecorder: MediaRecorder? = null
    private var audioFilePath: String = ""
    private var isRecording = false
    private var recordedAudioUri: android.net.Uri? = null
    private var currentUserName: String? = null
    private var currentUserProfilePic: String? = null
    
    private var lastClickTime: Long = 0
    private var recordingTimer: java.util.Timer? = null
    private var recordTime = 0
    private var messageListener: ValueEventListener? = null

    private val pdfPicker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri: android.net.Uri? ->
        uri?.let { uploadPdf(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        val receiverUid = intent.getStringExtra("receiver_uid")
        val receiverName = intent.getStringExtra("receiver_name")

        findViewById<TextView>(R.id.userName).text = receiverName ?: "Chat"
        val ivReceiverProfile = findViewById<ImageView>(R.id.profileImage)

        if (receiverUid != null) {
            FirebaseDatabase.getInstance().getReference("Users").child(receiverUid)
                .child("profileImageUrl").addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val imageUrl = snapshot.getValue(String::class.java)
                        if (!imageUrl.isNullOrEmpty()) {
                            Glide.with(this@ChatActivity)
                                .load(imageUrl)
                                .circleCrop()
                                .placeholder(R.drawable.ic_launcher_foreground)
                                .into(ivReceiverProfile)
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
        }

        if (currentUid != null && receiverUid != null) {
            chatRoomId = if (currentUid < receiverUid) "${currentUid}_${receiverUid}" else "${receiverUid}_${currentUid}"
            database = FirebaseDatabase.getInstance().getReference("chats").child(chatRoomId!!)
        } else {
            database = FirebaseDatabase.getInstance().getReference("messages")
        }

        val messageBox = findViewById<EditText>(R.id.messageBox)
        val btnSend = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.sendButton)
        val btnImage = findViewById<ImageButton>(R.id.attachmentButton)
        val rvMessages = findViewById<RecyclerView>(R.id.rvMessages)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnJoinRoom = findViewById<com.google.android.material.button.MaterialButton>(R.id.joinRoomButton)
        
        btnJoinRoom.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val intent = Intent(this, StudyRoomActivity::class.java)
            startActivity(intent)
        }

        val replyLayout = findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.replyLayout)
        val tvReplyName = findViewById<TextView>(R.id.tvReplyName)
        val tvReplyMessage = findViewById<TextView>(R.id.tvReplyMessage)
        val btnCancelReply = findViewById<ImageButton>(R.id.btnCancelReply)
        val btnMic = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.btnMic)
        val layoutCancel = findViewById<View>(R.id.layoutSlideToCancel)
        val layoutSendVoice = findViewById<View>(R.id.layoutSendVoice)

        layoutCancel.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            if (isRecording) {
                cancelRecording()
            }
        }

        layoutSendVoice.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            if (isRecording) {
                stopRecording()
                uploadAudio()
            }
        }

        val btnAudioCall = findViewById<ImageButton>(R.id.btnAudioCall)
        val btnVideoCall = findViewById<ImageButton>(R.id.btnVideoCall)

        btnAudioCall.setOnClickListener { 
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            startCall("audio", receiverUid) 
        }
        btnVideoCall.setOnClickListener { 
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            startCall("video", receiverUid) 
        }

        // Fetch current user info
        currentUid?.let { uid ->
            FirebaseDatabase.getInstance().getReference("Users").child(uid).get().addOnSuccessListener { snapshot ->
                val user = snapshot.getValue(User::class.java)
                currentUserName = user?.fullName
                currentUserProfilePic = user?.profileImageUrl
            }
        }

        btnBack.setOnClickListener { 
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            finish() 
        }
        speechToTextHelper = SpeechToTextHelper(this, messageBox)

        messageAdapter = MessageAdapter(messageList, "chats/${chatRoomId ?: "default"}") { msg, action ->
            when (action) {
                "reply" -> {
                    replyingToMessage = msg
                    tvReplyName.text = "Replying to ${msg.senderName ?: "User"}"
                    tvReplyMessage.text = getMessageSummary(msg)
                    replyLayout.visibility = View.VISIBLE
                }
                "edit" -> {
                    editingMessage = msg
                    messageBox.setText(msg.message)
                    messageBox.requestFocus()
                    val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.showSoftInput(messageBox, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                }
                "forward" -> showForwardDialog(msg)
            }
        }
        rvMessages.layoutManager = LinearLayoutManager(this)
        rvMessages.adapter = messageAdapter

        // Swipe to reply
        val swipeToReplyCallback = SwipeToReplyCallback(this) { position ->
            val message = messageList[position]
            replyingToMessage = message
            tvReplyName.text = "Replying to ${message.senderName ?: "User"}"
            tvReplyMessage.text = getMessageSummary(message)
            replyLayout.visibility = View.VISIBLE
            messageAdapter.notifyItemChanged(position)
        }
        androidx.recyclerview.widget.ItemTouchHelper(swipeToReplyCallback).attachToRecyclerView(rvMessages)

        btnCancelReply.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            replyingToMessage = null
            replyLayout.visibility = View.GONE
        }

        messageListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                messageList.clear()
                for (data in snapshot.children) {
                    if (data.key == "typing" || data.key == "pinnedMessage") continue
                    val message = data.getValue(Message::class.java)?.copy(messageId = data.key)
                    if (message != null) messageList.add(message)
                }
                messageAdapter.notifyDataSetChanged()
                rvMessages.scrollToPosition(messageList.size - 1)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        database.addValueEventListener(messageListener!!)

        btnSend.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            if (recordedAudioUri != null) {
                uploadAudio()
            } else {
                val text = messageBox.text.toString().trim()
                if (text.isNotEmpty()) {
                    if (editingMessage != null) {
                        val updatedMsg = editingMessage!!.copy(message = text, edited = true)
                        database.child(editingMessage!!.messageId!!).setValue(updatedMsg)
                        editingMessage = null
                    } else {
                        val newMessage = Message(
                            sender = currentUid,
                            senderName = currentUserName,
                            senderProfilePic = currentUserProfilePic,
                            message = text,
                            type = "text",
                            replyToId = replyingToMessage?.messageId,
                            replyToText = getMessageSummary(replyingToMessage)
                        )
                        database.push().setValue(newMessage)
                    }
                    messageBox.setText("")
                    recordedAudioUri = null // Clear audio state
                    replyingToMessage = null
                    replyLayout.visibility = View.GONE
                }
            }
        }

        btnImage.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val options = arrayOf("Image", "PDF")
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Select Attachment Type")
                .setItems(options) { _, which ->
                    if (which == 0) {
                        val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
                        startActivityForResult(intent, 100)
                    } else {
                        pdfPicker.launch("application/pdf")
                    }
                }.show()
        }

        // Optimized tap window for mic
        btnMic.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val clickTime = System.currentTimeMillis()
            if (clickTime - lastClickTime < 300) { // Reduced window
                if (!isRecording) {
                    if (checkPermissions()) startRecording() else requestPermissions()
                } else {
                    stopRecording()
                }
            } else {
                speechToTextHelper.startListening()
            }
            lastClickTime = clickTime
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::speechToTextHelper.isInitialized) {
            speechToTextHelper.destroy()
        }
        messageListener?.let { database.removeEventListener(it) }
    }

    private fun startCall(type: String, receiverUid: String?) {
        val intent = Intent(this, CallActivity::class.java).apply {
            putExtra("CALL_TYPE", type)
            putExtra("RECEIVER_UID", receiverUid)
            putExtra("CALLER_NAME", currentUserName)
        }
        startActivity(intent)
    }

    private fun checkPermissions() = ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    private fun requestPermissions() = ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 200)

    private fun startRecording() {
        val layoutInput = findViewById<View>(R.id.layoutInput)
        val layoutRecording = findViewById<View>(R.id.layoutRecording)
        val tvTimer = findViewById<TextView>(R.id.tvRecordTimer)
        val waveform = findViewById<AudioWaveformView>(R.id.audioWaveform)

        audioFilePath = "${externalCacheDir?.absolutePath}/voice_${System.currentTimeMillis()}.3gp"
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else MediaRecorder()
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
            layoutInput.visibility = View.GONE
            layoutRecording.visibility = View.VISIBLE
            waveform.clear()
            
            recordTime = 0
            recordingTimer = java.util.Timer()
            recordingTimer?.scheduleAtFixedRate(object : java.util.TimerTask() {
                override fun run() {
                    runOnUiThread {
                        recordTime++
                        val mins = recordTime / 60
                        val secs = recordTime % 60
                        tvTimer.text = String.format("%d:%02d", mins, secs)
                        mediaRecorder?.let {
                            waveform.addAmplitude(it.maxAmplitude.toFloat())
                        }
                    }
                }
            }, 0, 100)

            findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.btnMic).setColorFilter(Color.RED)
            Toast.makeText(this, "Recording... Double tap again to stop", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            Log.e("ChatActivity", "Recording failed: ${e.message}")
        }
    }

    private fun stopRecording() {
        val layoutInput = findViewById<View>(R.id.layoutInput)
        val layoutRecording = findViewById<View>(R.id.layoutRecording)
        
        recordingTimer?.cancel()
        recordingTimer = null

        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (e: Exception) {}
        mediaRecorder = null
        isRecording = false
        
        layoutInput.visibility = View.VISIBLE
        layoutRecording.visibility = View.GONE

        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.btnMic).setColorFilter(Color.WHITE)
        recordedAudioUri = android.net.Uri.fromFile(File(audioFilePath))
        Toast.makeText(this, "Voice recorded! Click send to share.", Toast.LENGTH_SHORT).show()
    }

    private fun cancelRecording() {
        val layoutInput = findViewById<View>(R.id.layoutInput)
        val layoutRecording = findViewById<View>(R.id.layoutRecording)

        recordingTimer?.cancel()
        recordingTimer = null

        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (e: Exception) {}
        mediaRecorder = null
        isRecording = false

        layoutInput.visibility = View.VISIBLE
        layoutRecording.visibility = View.GONE

        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.btnMic).setColorFilter(Color.WHITE)
        recordedAudioUri = null
        Toast.makeText(this, "Recording cancelled", Toast.LENGTH_SHORT).show()
    }

    private fun uploadAudio() {
        if (recordedAudioUri == null) return
        val ref = storage.child("audio/${System.currentTimeMillis()}.3gp")
        ref.putFile(recordedAudioUri!!).addOnSuccessListener {
            ref.downloadUrl.addOnSuccessListener { uri ->
                val newMessage = Message(
                    sender = currentUid,
                    senderName = currentUserName,
                    senderProfilePic = currentUserProfilePic,
                    audioUrl = uri.toString(),
                    type = "audio",
                    replyToId = replyingToMessage?.messageId,
                    replyToText = getMessageSummary(replyingToMessage)
                )
                database.push().setValue(newMessage)
                recordedAudioUri = null
                replyingToMessage = null
                findViewById<View>(R.id.replyLayout).visibility = View.GONE
            }
        }
    }

    private fun uploadPdf(uri: android.net.Uri) {
        val fileName = getFileName(uri) ?: "Document.pdf"
        val ref = storage.child("pdfs/${System.currentTimeMillis()}_$fileName")
        
        Toast.makeText(this, "Sharing PDF...", Toast.LENGTH_SHORT).show()
        
        ref.putFile(uri).addOnSuccessListener {
            ref.downloadUrl.addOnSuccessListener { downloadUri ->
                val newMessage = Message(
                    sender = currentUid,
                    senderName = currentUserName,
                    senderProfilePic = currentUserProfilePic,
                    pdfUrl = downloadUri.toString(),
                    pdfName = fileName,
                    type = "pdf",
                    replyToId = replyingToMessage?.messageId,
                    replyToText = getMessageSummary(replyingToMessage)
                )
                database.push().setValue(newMessage)
                replyingToMessage = null
                findViewById<View>(R.id.replyLayout).visibility = View.GONE
            }
        }
    }

    private fun getFileName(uri: android.net.Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = cursor.getString(index)
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) result = result?.substring(cut + 1)
        }
        return result
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            val fileUri = data.data ?: return
            val ref = storage.child("images/${System.currentTimeMillis()}.jpg")
            ref.putFile(fileUri).addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { uri ->
                    val newMessage = Message(
                        sender = currentUid,
                        senderName = currentUserName,
                        senderProfilePic = currentUserProfilePic,
                        imageUrl = uri.toString(),
                        type = "image",
                        replyToId = replyingToMessage?.messageId,
                        replyToText = getMessageSummary(replyingToMessage)
                    )
                    database.push().setValue(newMessage)
                    replyingToMessage = null
                    findViewById<View>(R.id.replyLayout).visibility = View.GONE
                }
            }
        }
    }

    private fun getMessageSummary(message: Message?): String? {
        return when (message?.type) {
            "image" -> "📷 Photo"
            "audio" -> "🎤 Voice message"
            "pdf" -> "📄 PDF: ${message.pdfName ?: "Document"}"
            else -> message?.message
        }
    }

    private fun showForwardDialog(message: Message) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_participants, null)
        val rvParticipants = view.findViewById<RecyclerView>(R.id.rvParticipants)
        
        val participantsList = mutableListOf<User>()
        val adapter = UserAdapter(participantsList) { selectedUser ->
            val myUid = currentUid ?: return@UserAdapter
            val targetUid = selectedUser.uid ?: return@UserAdapter

            val forwardChatId = if (myUid < targetUid) "${myUid}_$targetUid" else "${targetUid}_$myUid"
            val forwardRef = FirebaseDatabase.getInstance().getReference("chats").child(forwardChatId)
            
            val forwardedMsg = message.copy(
                messageId = null,
                sender = myUid,
                senderName = currentUserName ?: "User",
                senderProfilePic = currentUserProfilePic,
                timestamp = System.currentTimeMillis(),
                forwarded = true,
                replyToId = null,
                replyToText = null
            )
            
            forwardRef.push().setValue(forwardedMsg).addOnSuccessListener {
                Toast.makeText(this, "Message forwarded to ${selectedUser.fullName}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
        
        rvParticipants.layoutManager = LinearLayoutManager(this)
        rvParticipants.adapter = adapter
        
        FirebaseDatabase.getInstance().getReference("Users").get().addOnSuccessListener { snapshot ->
            participantsList.clear()
            for (data in snapshot.children) {
                val user = data.getValue(User::class.java)?.copy(uid = data.key)
                if (user != null && user.uid != currentUid) {
                    participantsList.add(user)
                }
            }
            adapter.notifyDataSetChanged()
        }

        dialog.setContentView(view)
        dialog.show()
    }
}
