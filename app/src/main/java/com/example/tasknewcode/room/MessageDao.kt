package com.example.tasknewcode.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.tasknewcode.ChatMessage


@Dao
interface MessageDao {
    @Insert
    suspend fun insertMessage(message: ChatMessage)

    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    suspend fun getAllMessages(): List<ChatMessage>

    @Query("SELECT * FROM messages WHERE text = :content LIMIT 1")
    suspend fun getMessageByContent(content: String): ChatMessage?
}
