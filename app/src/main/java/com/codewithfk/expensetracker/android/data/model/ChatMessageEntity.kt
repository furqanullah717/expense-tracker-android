package com.codewithfk.expensetracker.android.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_message_table")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int? = null,
    val sessionId: Int,
    val userId: String = "",
    val firestoreId: String = "",
    val content: String,
    val role: String, // "user" or "assistant"
    val timestamp: Long = System.currentTimeMillis(),
    val promptTokens: Int? = null,
    val candidatesTokens: Int? = null,
    val totalTokens: Int? = null,
    val responseTimeMs: Long? = null,
    val estimatedCostUsd: Double? = null,
    val estimatedCostKrw: Double? = null,
    val modelName: String? = null,
    val agentVersion: String? = null,
    val provider: String? = null,
    val appCheckStatus: String? = null,
    val deviceModel: String? = null,
    val osVersion: String? = null,
    val detailsJson: String? = null,
    val firstPassPrompt: String? = null,
    val firstPassResponse: String? = null,
    val secondPassPrompt: String? = null,
    val secondPassResponse: String? = null,
    val thoughtsTokens: Int? = null
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
    }
}
