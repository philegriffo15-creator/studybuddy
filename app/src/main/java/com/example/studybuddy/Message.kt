package com.example.studybuddy

data class Message(
    val messageId: String? = null,
    val sender: String? = null,
    val senderName: String? = null,
    val senderProfilePic: String? = null,
    val message: String? = null,
    val imageUrl: String? = null,
    val audioUrl: String? = null,
    val pdfUrl: String? = null,
    val pdfName: String? = null,
    val type: String? = "text",
    val reaction: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val replyToId: String? = null,
    val replyToText: String? = null,
    var edited: Boolean = false,
    var forwarded: Boolean = false
)
