package com.example.tasknewcode

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ipAddress: String,
    var text: String,
    val isSender: Boolean,
    val filePath: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
