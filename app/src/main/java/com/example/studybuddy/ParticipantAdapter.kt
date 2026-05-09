package com.example.studybuddy

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class ParticipantAdapter(
    private val participantList: List<User>,
    private val onItemClick: (User) -> Unit
) : RecyclerView.Adapter<ParticipantAdapter.ParticipantViewHolder>() {

    class ParticipantViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivAvatar: ImageView = view.findViewById(R.id.ivParticipantAvatar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParticipantViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_participant_circle, parent, false)
        return ParticipantViewHolder(view)
    }

    override fun onBindViewHolder(holder: ParticipantViewHolder, position: Int) {
        val user = participantList[position]
        if (!user.profileImageUrl.isNullOrEmpty()) {
            Glide.with(holder.itemView.context).load(user.profileImageUrl).circleCrop().into(holder.ivAvatar)
        } else {
            holder.ivAvatar.setImageResource(R.drawable.ic_launcher_foreground)
        }
        holder.itemView.setOnClickListener { onItemClick(user) }
    }

    override fun getItemCount(): Int = participantList.size
}
