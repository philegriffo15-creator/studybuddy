package com.example.studybuddy

data class User(
    val uid: String? = null,
    val fullName: String? = null,
    val email: String? = null,
    val course: String? = null,
    val streak: Int = 0,
    val lastCompletedDate: String? = null
)
