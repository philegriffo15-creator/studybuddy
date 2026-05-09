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
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.io.IOException

class StudyRoomActivity : AppCompatActivity() {

    private var database: DatabaseReference? = null
    private var currentParticipantsRef: DatabaseReference? = null
    private val usersRef = FirebaseDatabase.getInstance().getReference("Users")
    private val storage = FirebaseStorage.getInstance().getReference("room_attachments")
    private val currentUser = FirebaseAuth.getInstance().currentUser?.uid

    private lateinit var messageAdapter: MessageAdapter
    private lateinit var participantAdapter: ParticipantAdapter
    private val messageList = mutableListOf<Message>()
    private val participantList = mutableListOf<User>()
    private lateinit var speechToTextHelper: SpeechToTextHelper

    private var currentUserName: String? = null
    private var currentUserProfilePic: String? = null
    private var userCourse: String? = null
    private var hasGlobalAccess: Boolean = false

    private var mediaRecorder: MediaRecorder? = null
    private var audioFilePath: String = ""
    private var isRecording = false
    private var recordedAudioUri: android.net.Uri? = null

    private var lastClickTime: Long = 0
    private var currentChannel = "" // Start empty to force first switch
    private var replyingToMessage: Message? = null
    private var editingMessage: Message? = null
    private var recordingTimer: java.util.Timer? = null
    private var recordTime = 0
    private lateinit var toggleGroup: com.google.android.material.button.MaterialButtonToggleGroup

    private val pdfPicker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri: android.net.Uri? ->
        uri?.let { uploadPdf(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_study_room)

        val etMessage = findViewById<EditText>(R.id.etMessage)
        val btnSend = findViewById<FloatingActionButton>(R.id.btnSend)
        val btnMic = findViewById<ImageButton>(R.id.btnMic)
        val btnAttachment = findViewById<ImageButton>(R.id.btnAttachment)
        val tvTopic = findViewById<TextView>(R.id.tvTopicName)
        val rvChat = findViewById<RecyclerView>(R.id.rvChat)
        val rvParticipantsHorizontal = findViewById<RecyclerView>(R.id.rvParticipantsHorizontal)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val cardParticipants = findViewById<MaterialCardView>(R.id.cardParticipants)
        toggleGroup = findViewById(R.id.toggleGroupChannel)
        val layoutCancel = findViewById<View>(R.id.layoutSlideToCancel)
        val layoutSendVoice = findViewById<View>(R.id.layoutSendVoice)

        layoutCancel.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            if (isRecording) {
                cancelRecording(btnMic)
            }
        }

        layoutSendVoice.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            if (isRecording) {
                stopRecording(btnMic)
                uploadAudioAndSend()
            }
        }

        val replyLayout = findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.replyLayout)
        val tvReplyName = findViewById<TextView>(R.id.tvReplyName)
        val tvReplyMessage = findViewById<TextView>(R.id.tvReplyMessage)
        val btnCancelReply = findViewById<ImageButton>(R.id.btnCancelReply)

        val btnAudioCall = findViewById<ImageButton>(R.id.btnRoomAudioCall)
        val btnVideoCall = findViewById<ImageButton>(R.id.btnRoomVideoCall)

        btnBack.setOnClickListener { 
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            finish() 
        }
        tvTopic.text = "Study Session"
        speechToTextHelper = SpeechToTextHelper(this, etMessage)

        // Initial setup for RecyclerViews
        messageAdapter = MessageAdapter(messageList, "room_messages") { msg, action ->
            when (action) {
                "reply" -> {
                    replyingToMessage = msg
                    tvReplyName.text = "Replying to ${msg.senderName ?: "User"}"
                    tvReplyMessage.text = getMessageSummary(msg)
                    replyLayout.visibility = View.VISIBLE
                }
                "edit" -> {
                    editingMessage = msg
                    etMessage.setText(msg.message)
                    etMessage.requestFocus()
                    val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.showSoftInput(etMessage, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                }
                "forward" -> showForwardDialog(msg)
            }
        }
        rvChat.layoutManager = LinearLayoutManager(this)
        rvChat.adapter = messageAdapter

        participantAdapter = ParticipantAdapter(participantList) { selectedUser ->
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("receiver_uid", selectedUser.uid)
            intent.putExtra("receiver_name", selectedUser.fullName)
            startActivity(intent)
        }
        rvParticipantsHorizontal.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvParticipantsHorizontal.adapter = participantAdapter

        // Swipe to reply
        val swipeToReplyCallback = SwipeToReplyCallback(this) { position ->
            val message = messageList[position]
            replyingToMessage = message
            tvReplyName.text = "Replying to ${message.senderName ?: "User"}"
            tvReplyMessage.text = getMessageSummary(message)
            replyLayout.visibility = View.VISIBLE
            messageAdapter.notifyItemChanged(position)
        }
        androidx.recyclerview.widget.ItemTouchHelper(swipeToReplyCallback).attachToRecyclerView(rvChat)

        btnCancelReply.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            replyingToMessage = null
            replyLayout.visibility = View.GONE
        }

        // Fetch User Info
        currentUser?.let { uid ->
            usersRef.child(uid).get().addOnSuccessListener { snapshot ->
                val user = snapshot.getValue(User::class.java)
                currentUserName = user?.fullName
                currentUserProfilePic = user?.profileImageUrl
                userCourse = user?.course
                hasGlobalAccess = user?.hasGlobalAccess ?: false
                
                // Keep generic title as requested
                tvTopic.text = "Study Session"

                // Once user info is loaded, set initial channel
                switchChannel("course")
                
                // Initialize participant loading
                loadParticipants(participantAdapter)
            }
        }

        // Channel Toggling with Payment Check
        toggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                group.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                if (checkedId == R.id.btnChannelAll) {
                    if (hasGlobalAccess) {
                        switchChannel("global")
                    } else {
                        showPaymentDialog()
                        group.post { group.check(R.id.btnChannelCourse) }
                    }
                } else if (checkedId == R.id.btnChannelCourse) {
                    switchChannel("course")
                }
            }
        }

        btnSend.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            if (recordedAudioUri != null) {
                uploadAudioAndSend()
            } else {
                val text = etMessage.text.toString().trim()
                if (text.isNotEmpty()) {
                    if (editingMessage != null) {
                        val updatedMsg = editingMessage!!.copy(message = text, edited = true)
                        database?.child(editingMessage!!.messageId!!)?.setValue(updatedMsg)
                        editingMessage = null
                    } else {
                        val msg = Message(
                            sender = currentUser,
                            senderName = currentUserName,
                            senderProfilePic = currentUserProfilePic,
                            message = text,
                            type = "text",
                            replyToId = replyingToMessage?.messageId,
                            replyToText = getMessageSummary(replyingToMessage)
                        )
                        database?.push()?.setValue(msg)
                    }
                    etMessage.text.clear()
                    recordedAudioUri = null
                    replyingToMessage = null
                    findViewById<View>(R.id.replyLayout).visibility = View.GONE
                }
            }
        }

        btnAttachment.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val options = arrayOf("Image", "PDF")
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Select Attachment Type")
                .setItems(options) { _, which ->
                    if (which == 0) {
                        val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
                        startActivityForResult(intent, 101)
                    } else {
                        pdfPicker.launch("application/pdf")
                    }
                }.show()
        }

        // Optimized Tap Logic for Mic
        btnMic.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val clickTime = System.currentTimeMillis()
            if (clickTime - lastClickTime < 300) { // Reduced window for faster double-tap response
                if (!isRecording) {
                    if (checkPermissions()) startRecording(btnMic) else requestPermissions()
                } else {
                    stopRecording(btnMic)
                }
            } else {
                speechToTextHelper.startListening()
            }
            lastClickTime = clickTime
        }

        btnAudioCall.setOnClickListener { 
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            startGroupCall("audio") 
        }
        btnVideoCall.setOnClickListener { 
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            startGroupCall("video") 
        }
        cardParticipants.setOnClickListener { 
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showParticipantsDialog() 
        }
    }

    private fun switchChannel(type: String) {
        if (currentChannel == type) return // Response optimization: Avoid redundant reloads
        
        currentChannel = type
        val sanitizedCourse = userCourse?.replace(" ", "_") ?: "General"
        
        val msgPath = if (type == "course") {
            "room_messages/course_$sanitizedCourse"
        } else {
            "room_messages/global"
        }

        val partPath = if (type == "course") {
            "room_participants/course_$sanitizedCourse"
        } else {
            "room_participants/global"
        }
        
        database?.removeEventListener(messageListener)
        currentParticipantsRef?.child(currentUser ?: "")?.removeValue()
        currentParticipantsRef?.removeEventListener(participantListener)

        database = FirebaseDatabase.getInstance().getReference(msgPath)
        currentParticipantsRef = FirebaseDatabase.getInstance().getReference(partPath)

        currentUser?.let { uid ->
            currentParticipantsRef?.child(uid)?.setValue(true)
            currentParticipantsRef?.child(uid)?.onDisconnect()?.removeValue()
        }

        messageAdapter.updatePath(msgPath)
        loadMessages()
        loadParticipants(participantAdapter)
        
        val channelName = if (type == "course") "Course Chat ($userCourse)" else "Global Chat (Premium)"
        Toast.makeText(this, "Switched to $channelName", Toast.LENGTH_SHORT).show()
    }

    private val participantListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            participantList.clear()
            val totalCount = snapshot.childrenCount
            if (totalCount == 0L) {
                participantAdapter.notifyDataSetChanged()
                return
            }
            
            var loadedCount = 0
            for (child in snapshot.children) {
                val uid = child.key ?: continue
                usersRef.child(uid).get().addOnSuccessListener { userSnap ->
                    val user = userSnap.getValue(User::class.java)?.copy(uid = uid)
                    if (user != null && uid != currentUser) {
                        if (!participantList.any { it.uid == uid }) {
                            participantList.add(user)
                        }
                    }
                    loadedCount++
                    if (loadedCount.toLong() == totalCount) {
                        participantAdapter.notifyDataSetChanged()
                    }
                }
            }
        }
        override fun onCancelled(error: DatabaseError) {}
    }

    private val messageListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            messageList.clear()
            for (data in snapshot.children) {
                if (data.key == "typing" || data.key == "pinnedMessage") continue
                val message = data.getValue(Message::class.java)?.copy(messageId = data.key)
                if (message != null) messageList.add(message)
            }
            messageAdapter.notifyDataSetChanged()
            findViewById<RecyclerView>(R.id.rvChat).scrollToPosition(messageList.size - 1)
        }
        override fun onCancelled(error: DatabaseError) {}
    }

    private fun loadMessages() {
        database?.addValueEventListener(messageListener)
    }

    private fun showPaymentDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_mpesa_payment, null)
        val etPhone = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPhoneNumber)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Global Chat Access")
            .setView(dialogView)
            .setPositiveButton("Pay with M-PESA") { _, _ ->
                val phone = etPhone.text.toString()
                if (phone.length >= 10) {
                    simulateMpesaPush(phone)
                } else {
                    Toast.makeText(this, "Invalid phone number", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun simulateMpesaPush(phone: String) {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setMessage("Requesting STK Push to $phone...")
            .setCancelable(false)
            .create()
        dialog.show()

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!isFinishing && !isDestroyed) {
                dialog.dismiss()
                
                val pinEntryView = layoutInflater.inflate(R.layout.dialog_mpesa_pin, null)
                val etPin = pinEntryView.findViewById<EditText>(R.id.etMpesaPin)

                val pinDialog = androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("M-PESA SIMULATOR")
                    .setView(pinEntryView)
                    .setCancelable(false)
                    .setPositiveButton("Enter PIN", null)
                    .setNegativeButton("Cancel") { d, _ ->
                        Toast.makeText(this, "Transaction cancelled by user", Toast.LENGTH_SHORT).show()
                        d.dismiss()
                    }
                    .create()

                pinDialog.show()

                pinDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    val pin = etPin.text.toString()
                    if (pin == "1234") {
                        pinDialog.dismiss()
                        verifyPayment(phone)
                    } else if (pin.isEmpty()) {
                        etPin.error = "Enter PIN"
                    } else {
                        Toast.makeText(this, "Incorrect PIN. Try 1234 for simulation.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }, 2000)
    }

    private fun verifyPayment(phone: String) {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setMessage("Verifying transaction with Safaricom...")
            .setCancelable(false)
            .create()
        dialog.show()

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!isFinishing && !isDestroyed) {
                dialog.dismiss()
                currentUser?.let { uid ->
                    usersRef.child(uid).child("hasGlobalAccess").setValue(true).addOnSuccessListener {
                        hasGlobalAccess = true
                        Toast.makeText(this, "Transaction Verified! Global Chat Unlocked.", Toast.LENGTH_LONG).show()
                        toggleGroup.check(R.id.btnChannelAll)
                        switchChannel("global")
                    }
                }
            }
        }, 4000)
    }

    private fun startGroupCall(type: String) {
        val intent = Intent(this, CallActivity::class.java).apply {
            putExtra("CALL_TYPE", type)
            putExtra("IS_GROUP_CALL", true)
            putExtra("ROOM_ID", if (currentChannel == "course") userCourse ?: "general" else "global")
        }
        startActivity(intent)
    }

    private fun getMessageSummary(message: Message?): String? {
        return when (message?.type) {
            "image" -> "📷 Photo"
            "audio" -> "🎤 Voice message"
            "pdf" -> "📄 PDF: ${message.pdfName ?: "Document"}"
            else -> message?.message
        }
    }

    private fun startRecording(btn: ImageButton) {
        val layoutInput = findViewById<View>(R.id.layoutInput)
        val layoutRecording = findViewById<View>(R.id.layoutRecording)
        val tvTimer = findViewById<TextView>(R.id.tvRecordTimer)
        val waveform = findViewById<AudioWaveformView>(R.id.audioWaveform)

        audioFilePath = "${externalCacheDir?.absolutePath}/room_voice_${System.currentTimeMillis()}.3gp"
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

            btn.setColorFilter(Color.RED)
            Toast.makeText(this, "Recording... Double tap again to stop", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            Log.e("StudyRoomActivity", "Recording failed: ${e.message}")
        }
    }

    private fun stopRecording(btn: ImageButton) {
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

        btn.setColorFilter(Color.WHITE)
        recordedAudioUri = android.net.Uri.fromFile(File(audioFilePath))
        Toast.makeText(this, "Voice recorded! Click the send button to share.", Toast.LENGTH_SHORT).show()
        findViewById<FloatingActionButton>(R.id.btnSend).setImageResource(android.R.drawable.ic_menu_send)
    }

    private fun cancelRecording(btn: ImageButton) {
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

        btn.setColorFilter(Color.WHITE)
        recordedAudioUri = null
        Toast.makeText(this, "Recording cancelled", Toast.LENGTH_SHORT).show()
    }

    private fun uploadAudioAndSend() {
        if (recordedAudioUri == null) return
        val ref = storage.child("audio/${System.currentTimeMillis()}.3gp")
        ref.putFile(recordedAudioUri!!).addOnSuccessListener {
            ref.downloadUrl.addOnSuccessListener { uri ->
                val msg = Message(
                    sender = currentUser,
                    senderName = currentUserName,
                    senderProfilePic = currentUserProfilePic,
                    audioUrl = uri.toString(),
                    type = "audio",
                    replyToId = replyingToMessage?.messageId,
                    replyToText = getMessageSummary(replyingToMessage)
                )
                database?.push()?.setValue(msg)
                recordedAudioUri = null
                replyingToMessage = null
                findViewById<View>(R.id.replyLayout).visibility = View.GONE
                findViewById<FloatingActionButton>(R.id.btnSend).setImageResource(R.drawable.ic_send)
                Toast.makeText(this, "Voice message shared", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Upload failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadPdf(uri: android.net.Uri) {
        val fileName = getFileName(uri) ?: "Document.pdf"
        val ref = storage.child("pdfs/${System.currentTimeMillis()}_$fileName")
        
        Toast.makeText(this, "Sharing PDF with room...", Toast.LENGTH_SHORT).show()
        
        ref.putFile(uri).addOnSuccessListener {
            ref.downloadUrl.addOnSuccessListener { downloadUri ->
                val msg = Message(
                    sender = currentUser,
                    senderName = currentUserName,
                    senderProfilePic = currentUserProfilePic,
                    pdfUrl = downloadUri.toString(),
                    pdfName = fileName,
                    type = "pdf",
                    replyToId = replyingToMessage?.messageId,
                    replyToText = getMessageSummary(replyingToMessage)
                )
                database?.push()?.setValue(msg)
                replyingToMessage = null
                findViewById<View>(R.id.replyLayout).visibility = View.GONE
            }
        }.addOnFailureListener {
            Toast.makeText(this, "PDF Upload Failed", Toast.LENGTH_SHORT).show()
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

    private fun showParticipantsDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_participants, null)
        val rvParticipants = view.findViewById<RecyclerView>(R.id.rvParticipants)
        val adapter = UserAdapter(participantList) { selectedUser ->
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("receiver_uid", selectedUser.uid)
            intent.putExtra("receiver_name", selectedUser.fullName)
            startActivity(intent)
            dialog.dismiss()
        }
        rvParticipants.layoutManager = LinearLayoutManager(this)
        rvParticipants.adapter = adapter
        
        loadParticipants(adapter)
        
        dialog.setContentView(view)
        dialog.show()
    }

    private fun showForwardDialog(message: Message) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_participants, null)
        val rvParticipants = view.findViewById<RecyclerView>(R.id.rvParticipants)
        
        val forwardList = mutableListOf<User>()
        val adapter = UserAdapter(forwardList) { selectedUser ->
            val myUid = currentUser ?: return@UserAdapter
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
        
        usersRef.get().addOnSuccessListener { snapshot ->
            forwardList.clear()
            for (data in snapshot.children) {
                val user = data.getValue(User::class.java)?.copy(uid = data.key)
                if (user != null && user.uid != currentUser) {
                    forwardList.add(user)
                }
            }
            adapter.notifyDataSetChanged()
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun loadParticipants(adapter: RecyclerView.Adapter<*>) {
        currentParticipantsRef?.addValueEventListener(participantListener)
    }

    private fun checkPermissions() = ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    private fun requestPermissions() = ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 200)

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101 && resultCode == RESULT_OK && data != null) {
            val fileUri = data.data ?: return
            val ref = storage.child("images/${System.currentTimeMillis()}.jpg")
            ref.putFile(fileUri).addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { uri ->
                    val msg = Message(
                        sender = currentUser,
                        senderName = currentUserName,
                        senderProfilePic = currentUserProfilePic,
                        imageUrl = uri.toString(),
                        type = "image",
                        replyToId = replyingToMessage?.messageId,
                        replyToText = getMessageSummary(replyingToMessage)
                    )
                    database?.push()?.setValue(msg)
                    replyingToMessage = null
                    findViewById<View>(R.id.replyLayout).visibility = View.GONE
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechToTextHelper.destroy()
        currentUser?.let { currentParticipantsRef?.child(it)?.removeValue() }
    }
}
