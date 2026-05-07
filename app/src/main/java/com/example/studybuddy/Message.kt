package com.example.studybuddy

data class Message(
    val sender: String? = null,
    val message: String? = null,
    val imageUrl: String? = null,
    val audioUrl: String? = null,
    val type: String? = "text",
    val timestamp: Long = System.currentTimeMillis()
)
