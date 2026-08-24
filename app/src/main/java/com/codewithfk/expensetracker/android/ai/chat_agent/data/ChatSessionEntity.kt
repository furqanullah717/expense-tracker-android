package com.codewithfk.expensetracker.android.ai.chat_agent.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_session_table")
data class ChatSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int? = null,
    val userId: String = "",
    val firestoreId: String = "",
    val title: String,
    val lastMessageTime: Long = System.currentTimeMillis()
)
