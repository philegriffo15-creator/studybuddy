package com.example.studybuddy

import android.media.MediaPlayer
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth

class MessageAdapter(private val messageList: List<Message>) :
    RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    private var mediaPlayer: MediaPlayer? = null
    private val currentUid = FirebaseAuth.getInstance().currentUser?.uid

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val parentLayout: ConstraintLayout = itemView.findViewById(R.id.parentLayout)
        val container: LinearLayout = itemView.findViewById(R.id.messageContainer)
        val tvSender: TextView = itemView.findViewById(R.id.tvSender)
        val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        val ivImage: ImageView = itemView.findViewById(R.id.ivMessageImage)
        val layoutAudio: LinearLayout = itemView.findViewById(R.id.layoutAudio)
        val ivPlayPause: ImageView = itemView.findViewById(R.id.ivPlayPause)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val msg = messageList[position]
        
        // Alignment logic: Sent messages (Right), Received (Left)
        val lp = holder.container.layoutParams as ConstraintLayout.LayoutParams
        if (msg.sender == currentUid) {
            lp.startToStart = ConstraintLayout.LayoutParams.UNSET
            lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            holder.container.gravity = Gravity.END
            holder.tvMessage.setBackgroundResource(R.drawable.input_field_background)
            holder.tvMessage.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#D81B60"))
            holder.tvSender.visibility = View.GONE // Hide your own name
        } else {
            lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            lp.endToEnd = ConstraintLayout.LayoutParams.UNSET
            holder.container.gravity = Gravity.START
            holder.tvMessage.setBackgroundResource(R.drawable.input_field_background)
            holder.tvMessage.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#1F2440"))
            holder.tvSender.visibility = View.VISIBLE
            holder.tvSender.text = msg.sender // In a real app, look up the name
        }
        holder.container.layoutParams = lp

        // Reset visibilities
        holder.tvMessage.visibility = View.GONE
        holder.ivImage.visibility = View.GONE
        holder.layoutAudio.visibility = View.GONE

        when (msg.type) {
            "text" -> {
                holder.tvMessage.visibility = View.VISIBLE
                holder.tvMessage.text = msg.message
            }
            "image" -> {
                holder.ivImage.visibility = View.VISIBLE
            }
            "audio" -> {
                holder.layoutAudio.visibility = View.VISIBLE
                holder.layoutAudio.setOnClickListener {
                    playAudio(msg.audioUrl, holder.ivPlayPause)
                }
            }
        }
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
