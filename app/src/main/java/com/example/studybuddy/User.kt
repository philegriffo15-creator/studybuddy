package com.example.studybuddy

data class User(
    val uid: String? = null,
    val fullName: String? = null,
    val email: String? = null,
    val course: String? = null,
    val bio: String? = "Hey there! I am using StudyBuddy.",
    val profileImageUrl: String? = null,
    val streak: Int = 0,
    val lastCompletedDate: String? = null,
    val studyHours: Int = 0,
    val groupsJoined: Int = 0,
    val residence: String? = "Not set",
    val hasGlobalAccess: Boolean = false
)
