package com.codewithfk.expensetracker.android.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.codewithfk.expensetracker.android.ai.chat_agent.data.ChatSessionEntity
import com.codewithfk.expensetracker.android.data.model.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_session_table ORDER BY lastMessageTime DESC")
    fun getAllSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_session_table WHERE userId = :userId ORDER BY lastMessageTime DESC")
    fun getSessionsForUser(userId: String): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_session_table WHERE userId = :userId")
    suspend fun getSessionsForUserList(userId: String): List<ChatSessionEntity>

    @Query("SELECT * FROM chat_session_table WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun getSessionByFirestoreId(firestoreId: String): ChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<ChatSessionEntity>)

    @Query("SELECT * FROM chat_session_table WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Int): ChatSessionEntity?

    @Query("UPDATE chat_session_table SET title = :title, lastMessageTime = :timestamp WHERE id = :sessionId")
    suspend fun updateSessionInfo(sessionId: Int, title: String, timestamp: Long)

    @Query("UPDATE chat_session_table SET lastMessageTime = :timestamp WHERE id = :sessionId")
    suspend fun updateSessionLastTime(sessionId: Int, timestamp: Long)

    @Query("DELETE FROM chat_session_table WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Int)

    @Query("DELETE FROM chat_message_table WHERE sessionId = :sessionId")
    suspend fun deleteMessagesBySession(sessionId: Int)

    @Query("SELECT * FROM chat_message_table WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesBySession(sessionId: Int): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_message_table WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun getMessageByFirestoreId(firestoreId: String): ChatMessageEntity?

    @Query("SELECT * FROM chat_message_table WHERE sessionId = :sessionId")
    suspend fun getMessagesBySessionList(sessionId: Int): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)

    @Query("UPDATE chat_message_table SET content = :content WHERE id = :messageId")
    suspend fun updateMessageContent(messageId: Int, content: String)

    @Query("""
        UPDATE chat_message_table 
        SET promptTokens = :promptTokens, 
            candidatesTokens = :candidatesTokens, 
            totalTokens = :totalTokens, 
            responseTimeMs = :responseTimeMs, 
            estimatedCostUsd = :estimatedCostUsd, 
            estimatedCostKrw = :estimatedCostKrw,
            modelName = :modelName,
            agentVersion = :agentVersion,
            provider = :provider,
            appCheckStatus = :appCheckStatus,
            deviceModel = :deviceModel,
            osVersion = :osVersion
        WHERE id = :messageId
    """)
    suspend fun updateMessageMetadata(
        messageId: Int,
        promptTokens: Int,
        candidatesTokens: Int,
        totalTokens: Int,
        responseTimeMs: Long,
        estimatedCostUsd: Double,
        estimatedCostKrw: Double,
        modelName: String,
        agentVersion: String,
        provider: String,
        appCheckStatus: String,
        deviceModel: String,
        osVersion: String
    )

    @Query("SELECT * FROM chat_message_table WHERE userId = :userId")
    suspend fun getAllMessagesForUserList(userId: String): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_message_table WHERE id = :messageId")
    suspend fun getMessageById(messageId: Int): ChatMessageEntity?

    @Query("DELETE FROM chat_message_table")
    suspend fun deleteAllMessages()

    @Query("DELETE FROM chat_session_table")
    suspend fun deleteAllSessions()
}