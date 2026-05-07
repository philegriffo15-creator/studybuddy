package com.example.studybuddy

data class StudyTask(
    val id: String? = null,
    val subject: String? = null,
    val goal: String? = null,
    val time: String? = null,
    val date: String? = null, // Format: dd-MM-yyyy
    val status: String? = "pending", // pending, completed
    val isGroupTask: Boolean = false,
    val creatorUid: String? = null
)
