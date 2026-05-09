package com.example.studybuddy

data class PdfFile(
    val id: String? = null,
    val fileName: String? = null,
    val downloadUrl: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val uploaderId: String? = null
)
