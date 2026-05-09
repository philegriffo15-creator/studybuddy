package com.example.studybuddy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class MessageAdapter(
    private val messageList: List<Message>,
    private var chatPath: String,
    private val onMessageAction: (Message, String) -> Unit // "reply", "edit", etc.
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    private var mediaPlayer: MediaPlayer? = null
    private val currentUid = FirebaseAuth.getInstance().currentUser?.uid
    private var database = FirebaseDatabase.getInstance().getReference(chatPath)

    fun updatePath(newPath: String) {
        chatPath = newPath
        database = FirebaseDatabase.getInstance().getReference(chatPath)
    }

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val parentLayout: ConstraintLayout = itemView.findViewById(R.id.parentLayout)
        val container: LinearLayout = itemView.findViewById(R.id.messageContainer)
        val tvSender: TextView = itemView.findViewById(R.id.tvSender)
        val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        val ivImage: ImageView = itemView.findViewById(R.id.ivMessageImage)
        val layoutAudio: LinearLayout = itemView.findViewById(R.id.layoutAudio)
        val ivPlayPause: ImageView = itemView.findViewById(R.id.ivPlayPause)
        val tvReaction: TextView = itemView.findViewById(R.id.tvReaction)
        val tvForwarded: TextView = itemView.findViewById(R.id.tvForwarded)
        val tvEdited: TextView = itemView.findViewById(R.id.tvEdited)
        val replyContainer: LinearLayout = itemView.findViewById(R.id.replyContainer)
        val tvReplyPreview: TextView = itemView.findViewById(R.id.tvReplyPreview)
        val ivSenderProfile: ImageView = itemView.findViewById(R.id.ivSenderProfile)
        val layoutPdf: LinearLayout = itemView.findViewById(R.id.layoutPdf)
        val tvPdfName: TextView = itemView.findViewById(R.id.tvPdfName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val msg = messageList[position]
        
        // Alignment logic
        val lp = holder.container.layoutParams as ConstraintLayout.LayoutParams

        if (msg.sender == currentUid) {
            lp.startToEnd = ConstraintLayout.LayoutParams.UNSET
            lp.startToStart = ConstraintLayout.LayoutParams.UNSET
            lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            holder.container.gravity = Gravity.END
            holder.tvMessage.setBackgroundResource(R.drawable.input_field_background)
            // Instagram-like Purple Gradient Color
            holder.tvMessage.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#833AB4"))
            holder.tvSender.visibility = View.GONE
            holder.ivSenderProfile.visibility = View.GONE
            holder.tvMessage.setTextColor(android.graphics.Color.WHITE)
        } else {
            lp.endToEnd = ConstraintLayout.LayoutParams.UNSET
            lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            lp.startToEnd = ConstraintLayout.LayoutParams.UNSET
            holder.container.gravity = Gravity.START
            holder.tvMessage.setBackgroundResource(R.drawable.input_field_background)
            holder.tvMessage.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#1F2440"))
            
            holder.tvSender.visibility = View.VISIBLE
            // Use fullName or default to User
            holder.tvSender.text = msg.senderName ?: "Buddy"
            holder.tvMessage.setTextColor(android.graphics.Color.WHITE)
            
            holder.ivSenderProfile.visibility = View.VISIBLE
            if (!msg.senderProfilePic.isNullOrEmpty()) {
                Glide.with(holder.itemView.context).load(msg.senderProfilePic).circleCrop().into(holder.ivSenderProfile)
            } else {
                holder.ivSenderProfile.setImageResource(R.drawable.ic_launcher_foreground)
            }
        }
        holder.container.layoutParams = lp

        // Set click and long click on the container and the text message
        val longClickListener = View.OnLongClickListener {
            try {
                showMessageOptions(holder.itemView.context, msg)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            true
        }
        
        holder.tvMessage.setOnLongClickListener(longClickListener)
        holder.ivImage.setOnLongClickListener(longClickListener)
        holder.layoutAudio.setOnLongClickListener(longClickListener)
        holder.layoutPdf.setOnLongClickListener(longClickListener)
        holder.container.setOnLongClickListener(longClickListener)
        
        // Final catch-all removed from itemView to avoid issues with parent layout selection
        // holder.itemView.setOnLongClickListener(longClickListener)

        // Reply visibility
        if (!msg.replyToText.isNullOrBlank()) {
            holder.replyContainer.visibility = View.VISIBLE
            holder.tvReplyPreview.text = msg.replyToText
        } else {
            holder.replyContainer.visibility = View.GONE
        }

        // Reset visibilities
        holder.tvMessage.visibility = View.GONE
        holder.ivImage.visibility = View.GONE
        holder.layoutAudio.visibility = View.GONE
        holder.layoutPdf.visibility = View.GONE
        holder.tvReaction.visibility = View.GONE
        holder.tvForwarded.visibility = if (msg.forwarded) View.VISIBLE else View.GONE
        holder.tvEdited.visibility = if (msg.edited) View.VISIBLE else View.GONE

        // Show Reaction if present
        if (!msg.reaction.isNullOrEmpty()) {
            holder.tvReaction.visibility = View.VISIBLE
            holder.tvReaction.text = msg.reaction
        }

        when (msg.type) {
            "text" -> {
                holder.tvMessage.visibility = View.VISIBLE
                holder.tvMessage.text = msg.message
            }
            "image" -> {
                holder.ivImage.visibility = View.VISIBLE
                Glide.with(holder.itemView.context).load(msg.imageUrl).into(holder.ivImage)
                holder.ivImage.setOnClickListener {
                    if (holder.itemView.context is AppCompatActivity) {
                        val activity = holder.itemView.context as AppCompatActivity
                        val intent = Intent(activity, ImageDetailActivity::class.java)
                        intent.putExtra("IMAGE_URL", msg.imageUrl)

                        val options = androidx.core.app.ActivityOptionsCompat.makeSceneTransitionAnimation(
                            activity, holder.ivImage, "image_message"
                        )
                        activity.startActivity(intent, options.toBundle())
                    }
                }
            }
            "audio" -> {
                holder.layoutAudio.visibility = View.VISIBLE
                holder.layoutAudio.setOnClickListener {
                    playAudio(msg.audioUrl, holder.ivPlayPause)
                }
            }
            "pdf" -> {
                holder.layoutPdf.visibility = View.VISIBLE
                holder.tvPdfName.text = msg.pdfName ?: "PDF Document"
                holder.layoutPdf.setOnClickListener {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(msg.pdfUrl))
                    holder.itemView.context.startActivity(intent)
                }
            }
        }

        holder.itemView.setOnLongClickListener {
            try {
                showMessageOptions(holder.itemView.context, msg)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            true
        }
    }

    private fun showMessageOptions(context: Context, message: Message) {
        val dialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_message_options, null)

        // REPLY FUNCTION
        view.findViewById<View>(R.id.optionReply)?.setOnClickListener {
            onMessageAction(message, "reply")
            dialog.dismiss()
        }

        // EDIT FUNCTION
        view.findViewById<View>(R.id.optionEdit)?.setOnClickListener {
            if (message.sender == currentUid && message.type == "text") {
                onMessageAction(message, "edit")
            } else {
                Toast.makeText(context, "You can only edit your own text messages", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        // FORWARD FUNCTION
        view.findViewById<View>(R.id.optionForward)?.setOnClickListener {
            onMessageAction(message, "forward")
            dialog.dismiss()
        }

        // PIN FUNCTION
        view.findViewById<View>(R.id.optionPin)?.setOnClickListener {
            database.parent?.child("pinnedMessage")?.setValue(message.messageId)
            Toast.makeText(context, "Message pinned", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        // COPY FUNCTION
        view.findViewById<View>(R.id.optionCopy)?.setOnClickListener {
            val textToCopy = message.message ?: message.pdfName ?: "Media Message"
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("message", textToCopy)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        // TRANSLATE FUNCTION
        view.findViewById<View>(R.id.optionTranslate)?.setOnClickListener {
            if (message.type == "text" && !message.message.isNullOrEmpty()) {
                Toast.makeText(context, "Translating: ${message.message?.take(20)}...", Toast.LENGTH_SHORT).show()
                // Placeholder for translation logic
            } else {
                Toast.makeText(context, "Cannot translate this message", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        // UNSEND (DELETE) FUNCTION
        view.findViewById<View>(R.id.optionUnsend)?.setOnClickListener {
            if (message.sender == currentUid && message.messageId != null) {
                database.child(message.messageId).removeValue().addOnSuccessListener {
                    Toast.makeText(context, "Message unsent", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "You can only unsend your own messages", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        // REACTIONS QUICK BAR
        val reactionIds = listOf(R.id.reactLike, R.id.reactLaugh, R.id.reactSurprise, R.id.reactSad, R.id.reactFire)
        val emojis = listOf("❤️", "😂", "😮", "😢", "🔥")

        reactionIds.forEachIndexed { index, id ->
            view.findViewById<TextView>(id)?.setOnClickListener {
                if (message.messageId != null) {
                    database.child(message.messageId).child("reaction").setValue(emojis[index])
                }
                dialog.dismiss()
            }
        }

        // Add more reactions (like a full keypad)
        // Add more reactions (like a full keypad)
        val reactionsContainer = view.findViewById<View>(R.id.reactLike).parent as? ViewGroup
        if (reactionsContainer != null) {
            val btnMoreReactions = TextView(context).apply {
                text = "➕"
                textSize = 24f
                setPadding(20, 0, 20, 0)
                isClickable = true
                isFocusable = true
                setBackgroundResource(android.R.drawable.list_selector_background)
            }
            btnMoreReactions.setOnClickListener {
                dialog.dismiss()
                showFullEmojiPicker(context, message)
            }
            reactionsContainer.addView(btnMoreReactions)
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun showFullEmojiPicker(context: Context, message: Message) {
        val emojis = listOf(
            "❤️", "😂", "😮", "😢", "🔥", "👍", "🙌", "🙏", "👏", "✨", 
            "🎉", "💡", "✅", "❌", "💯", "🚀", "🤔", "😎", "😅", "😍",
            "🥺", "😭", "😡", "😴", "💪", "👀", "📍", "📚", "💻", "🎓"
        )
        
        val dialog = BottomSheetDialog(context)
        val gridView = android.widget.GridView(context).apply {
            numColumns = 6
            horizontalSpacing = 10
            verticalSpacing = 10
            setPadding(20, 20, 20, 20)
            adapter = object : android.widget.BaseAdapter() {
                override fun getCount(): Int = emojis.size
                override fun getItem(position: Int): Any = emojis[position]
                override fun getItemId(position: Int): Long = position.toLong()
                override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                    val tv = TextView(context).apply {
                        text = emojis[position]
                        textSize = 30f
                        gravity = Gravity.CENTER
                        setPadding(10, 10, 10, 10)
                    }
                    return tv
                }
            }
        }
        
        gridView.setOnItemClickListener { _, _, position, _ ->
            if (message.messageId != null) {
                database.child(message.messageId).child("reaction").setValue(emojis[position])
            }
            dialog.dismiss()
        }

        dialog.setContentView(gridView)
        dialog.show()
    }

    private fun playAudio(audioUrl: String?, playIcon: ImageView) {
        if (audioUrl == null) return
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(audioUrl)
            prepareAsync()
            setOnPreparedListener { 
                start()
                playIcon.setImageResource(android.R.drawable.ic_media_pause)
            }
            setOnCompletionListener {
                playIcon.setImageResource(android.R.drawable.ic_media_play)
            }
        }
    }

    override fun getItemCount(): Int = messageList.size
}
