package com.codewithfk.expensetracker.android.data.repository

import android.util.Log
import com.codewithfk.expensetracker.android.data.dao.ChatDao
import com.codewithfk.expensetracker.android.data.model.ChatMessageEntity
import com.codewithfk.expensetracker.android.data.model.ChatSessionEntity
import com.codewithfk.expensetracker.android.utils.Utils
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao
) {
    private val TAG = "ChatRepository"

    private fun getCurrentUserId(): String {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
        Log.d(TAG, "Current User ID: $uid")
        return uid
    }

    private fun getUserSessionsCollection(userId: String) =
        Firebase.firestore.collection("users").document(userId).collection("chat_sessions")

    private fun getUserMessagesCollection(userId: String, sessionFirestoreId: String) =
        getUserSessionsCollection(userId).document(sessionFirestoreId).collection("messages")

    fun getSessions(): Flow<List<ChatSessionEntity>> {
        val uid = getCurrentUserId()
        return if (uid == "anonymous") chatDao.getAllSessions() else chatDao.getSessionsForUser(uid)
    }

    fun getMessages(sessionId: Int): Flow<List<ChatMessageEntity>> = chatDao.getMessagesBySession(sessionId)

    suspend fun insertSession(session: ChatSessionEntity): Long {
        val uid = getCurrentUserId()
        val firestoreId = if (session.firestoreId.isNotBlank()) session.firestoreId else UUID.randomUUID().toString()
        val sessionWithUid = session.copy(userId = uid, firestoreId = firestoreId)
        
        val localId = chatDao.insertSession(sessionWithUid)
        Log.d(TAG, "Session saved locally: $localId, FirestoreID: $firestoreId")
        
        if (uid != "anonymous") {
            try {
                val data = mapOf(
                    "userId" to uid,
                    "firestoreId" to firestoreId,
                    "title" to session.title,
                    "lastMessageTime" to session.lastMessageTime
                )
                getUserSessionsCollection(uid).document(firestoreId).set(data).await()
                Log.d(TAG, "Session synced to Firestore: $firestoreId")
            } catch (e: Exception) {
                Log.e(TAG, "FAILED to sync session to Firestore: ${e.message}", e)
            }
        }
        return localId
    }

    suspend fun updateSessionInfo(sessionId: Int, title: String, timestamp: Long) {
        chatDao.updateSessionInfo(sessionId, title, timestamp)
        val uid = getCurrentUserId()
        if (uid != "anonymous") {
            try {
                val session = chatDao.getSessionById(sessionId)
                if (session?.firestoreId?.isNotBlank() == true) {
                    getUserSessionsCollection(uid).document(session.firestoreId).update(
                        "title", title,
                        "lastMessageTime", timestamp
                    ).await()
                    Log.d(TAG, "Session info updated in Firestore: ${session.firestoreId}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating session in Firestore: ${e.message}")
            }
        }
    }

    suspend fun renameSession(sessionId: Int, title: String) {
        val session = chatDao.getSessionById(sessionId) ?: return
        chatDao.updateSessionInfo(sessionId, title, session.lastMessageTime)
        val uid = getCurrentUserId()
        if (uid != "anonymous" && session.firestoreId.isNotBlank()) {
            try {
                getUserSessionsCollection(uid).document(session.firestoreId)
                    .update("title", title).await()
                Log.d(TAG, "Session renamed in Firestore: ${session.firestoreId}")
            } catch (e: Exception) {
                Log.e(TAG, "Error renaming session in Firestore: ${e.message}")
            }
        }
    }

    suspend fun updateSessionLastTime(sessionId: Int, timestamp: Long) {
        chatDao.updateSessionLastTime(sessionId, timestamp)
        val uid = getCurrentUserId()
        if (uid != "anonymous") {
            try {
                val session = chatDao.getSessionById(sessionId)
                if (session?.firestoreId?.isNotBlank() == true) {
                    getUserSessionsCollection(uid).document(session.firestoreId).update(
                        "lastMessageTime", timestamp
                    ).await()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating session time in Firestore: ${e.message}")
            }
        }
    }

    suspend fun insertMessage(message: ChatMessageEntity): Long {
        val uid = getCurrentUserId()
        val firestoreId = if (message.firestoreId.isNotBlank()) message.firestoreId else UUID.randomUUID().toString()
        val messageWithUid = message.copy(userId = uid, firestoreId = firestoreId)
        
        val localId = chatDao.insertMessage(messageWithUid)
        Log.d(TAG, "Message saved locally: $localId, FirestoreID: $firestoreId")
        
        if (uid != "anonymous") {
            try {
                // Find session using robust local lookup
                val session = chatDao.getSessionById(message.sessionId)
                if (session?.firestoreId?.isNotBlank() == true) {
                    val data = mutableMapOf(
                        "userId" to uid,
                        "firestoreId" to firestoreId,
                        "content" to message.content,
                        "role" to message.role,
                        "timestamp" to message.timestamp,
                        "promptTokens" to message.promptTokens,
                        "candidatesTokens" to message.candidatesTokens,
                        "totalTokens" to message.totalTokens,
                        "responseTimeMs" to message.responseTimeMs,
                        "estimatedCostUsd" to message.estimatedCostUsd,
                        "estimatedCostKrw" to message.estimatedCostKrw,
                        "modelName" to message.modelName,
                        "agentVersion" to message.agentVersion,
                        "provider" to message.provider,
                        "appCheckStatus" to message.appCheckStatus,
                        "deviceModel" to message.deviceModel,
                        "osVersion" to message.osVersion,
                        "detailsJson" to message.detailsJson,
                        "firstPassPrompt" to message.firstPassPrompt,
                        "firstPassResponse" to message.firstPassResponse,
                        "secondPassPrompt" to message.secondPassPrompt,
                        "secondPassResponse" to message.secondPassResponse,
                        "thoughtsTokens" to message.thoughtsTokens
                    )
                    getUserMessagesCollection(uid, session.firestoreId).document(firestoreId).set(data).await()
                    Log.d(TAG, "Message synced to Firestore: $firestoreId")
                } else {
                    Log.w(TAG, "Could not find FirestoreID for session ${message.sessionId}. Message not synced.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "FAILED to sync message to Firestore: ${e.message}", e)
            }
        }
        
        return localId
    }

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
        osVersion: String,
        firstPassPrompt: String? = null,
        firstPassResponse: String? = null,
        secondPassPrompt: String? = null,
        secondPassResponse: String? = null,
        thoughtsTokens: Int? = null
    ) {
        chatDao.updateMessageMetadata(
            messageId, promptTokens, candidatesTokens, totalTokens,
            responseTimeMs, estimatedCostUsd, estimatedCostKrw,
            modelName, agentVersion, provider, appCheckStatus, deviceModel, osVersion,
            firstPassPrompt, firstPassResponse, secondPassPrompt, secondPassResponse,
            thoughtsTokens
        )
        
        val uid = getCurrentUserId()
        if (uid != "anonymous") {
            try {
                val message = chatDao.getMessageById(messageId)
                if (message != null && message.firestoreId.isNotBlank()) {
                    val session = chatDao.getSessionById(message.sessionId)
                    if (session?.firestoreId?.isNotBlank() == true) {
                        val updates = mapOf(
                            "content" to message.content,
                            "promptTokens" to promptTokens,
                            "candidatesTokens" to candidatesTokens,
                            "totalTokens" to totalTokens,
                            "responseTimeMs" to responseTimeMs,
                            "estimatedCostUsd" to estimatedCostUsd,
                            "estimatedCostKrw" to estimatedCostKrw,
                            "modelName" to modelName,
                            "agentVersion" to agentVersion,
                            "provider" to provider,
                            "appCheckStatus" to appCheckStatus,
                            "deviceModel" to deviceModel,
                            "osVersion" to osVersion,
                            "detailsJson" to message.detailsJson,
                            "firstPassPrompt" to firstPassPrompt,
                            "firstPassResponse" to firstPassResponse,
                            "secondPassPrompt" to secondPassPrompt,
                            "secondPassResponse" to secondPassResponse,
                            "thoughtsTokens" to thoughtsTokens
                        )
                        getUserMessagesCollection(uid, session.firestoreId).document(message.firestoreId).update(updates).await()
                        Log.d(TAG, "Metadata synced to Firestore for message: ${message.firestoreId}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating metadata in Firestore: ${e.message}")
            }
        }
    }

    suspend fun updateMessageContent(messageId: Int, content: String) {
        chatDao.updateMessageContent(messageId, content)
    }

    suspend fun updateMessageDetails(messageId: Int, detailsJson: String) {
        chatDao.updateMessageDetails(messageId, detailsJson)
        val uid = getCurrentUserId()
        if (uid != "anonymous") {
            try {
                val message = chatDao.getMessageById(messageId)
                if (message != null && message.firestoreId.isNotBlank()) {
                    val session = chatDao.getSessionById(message.sessionId)
                    if (session?.firestoreId?.isNotBlank() == true) {
                        getUserMessagesCollection(uid, session.firestoreId)
                            .document(message.firestoreId)
                            .update("detailsJson", detailsJson)
                            .await()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing detailsJson to Firestore: ${e.message}")
            }
        }
    }

    suspend fun updateMessageContentAndSync(messageId: Int, content: String) {
        chatDao.updateMessageContent(messageId, content)
        val uid = getCurrentUserId()
        if (uid == "anonymous") return

        try {
            val message = chatDao.getMessageById(messageId) ?: return
            val session = chatDao.getSessionById(message.sessionId) ?: return
            if (message.firestoreId.isNotBlank() && session.firestoreId.isNotBlank()) {
                getUserMessagesCollection(uid, session.firestoreId)
                    .document(message.firestoreId)
                    .update("content", content)
                    .await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync attachment links: ${e.message}", e)
        }
    }

    suspend fun deleteSession(sessionId: Int) {
        val uid = getCurrentUserId()
        val session = chatDao.getSessionById(sessionId)
        chatDao.deleteSession(sessionId)
        chatDao.deleteMessagesBySession(sessionId)
        
        if (uid != "anonymous" && session?.firestoreId?.isNotBlank() == true) {
            try {
                getUserSessionsCollection(uid).document(session.firestoreId).delete().await()
                Log.d(TAG, "Session deleted from Firestore: ${session.firestoreId}")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting session from Firestore: ${e.message}")
            }
        }
    }

    suspend fun clearChat() {
        val uid = getCurrentUserId()
        chatDao.deleteAllMessages()
        chatDao.deleteAllSessions()
        
        if (uid != "anonymous") {
            try {
                val snapshot = getUserSessionsCollection(uid).get().await()
                for (doc in snapshot.documents) {
                    doc.reference.delete().await()
                }
                Log.d(TAG, "All chat data cleared from Firestore")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing chat in Firestore: ${e.message}")
            }
        }
    }

    suspend fun syncFromFirestore(userId: String = getCurrentUserId()) {
        if (userId == "anonymous" || userId.isBlank()) {
            Log.d(TAG, "Sync skipped: User anonymous")
            return
        }
        Log.d(TAG, "Syncing from Firestore for user: $userId")
        try {
            val sessionsSnapshot = getUserSessionsCollection(userId).get().await()
            val existingSessions = chatDao.getSessionsForUserList(userId)
            val sessionMap = existingSessions.associateBy { it.firestoreId }

            for (sessionDoc in sessionsSnapshot.documents) {
                val sFirestoreId = sessionDoc.getString("firestoreId") ?: sessionDoc.id
                val existingSession = sessionMap[sFirestoreId]
                
                val sessionEntity = ChatSessionEntity(
                    id = existingSession?.id,
                    userId = userId,
                    firestoreId = sFirestoreId,
                    title = sessionDoc.getString("title") ?: "",
                    lastMessageTime = sessionDoc.getLong("lastMessageTime") ?: System.currentTimeMillis()
                )
                
                val localSessionId = chatDao.insertSession(sessionEntity).toInt()
                
                val messagesSnapshot = getUserMessagesCollection(userId, sFirestoreId).get().await()
                val existingMessages = chatDao.getMessagesBySessionList(localSessionId)
                val messageMap = existingMessages.associateBy { it.firestoreId }
                
                val messageEntities = messagesSnapshot.documents.mapNotNull { msgDoc ->
                    val mFirestoreId = msgDoc.getString("firestoreId") ?: msgDoc.id
                    val existingMsg = messageMap[mFirestoreId]
                    
                    ChatMessageEntity(
                        id = existingMsg?.id,
                        sessionId = localSessionId,
                        userId = userId,
                        firestoreId = mFirestoreId,
                        content = msgDoc.getString("content") ?: "",
                        role = msgDoc.getString("role") ?: "user",
                        timestamp = msgDoc.getLong("timestamp") ?: System.currentTimeMillis(),
                        promptTokens = msgDoc.getLong("promptTokens")?.toInt(),
                        candidatesTokens = msgDoc.getLong("candidatesTokens")?.toInt(),
                        totalTokens = msgDoc.getLong("totalTokens")?.toInt(),
                        responseTimeMs = msgDoc.getLong("responseTimeMs"),
                        estimatedCostUsd = msgDoc.getDouble("estimatedCostUsd"),
                        estimatedCostKrw = msgDoc.getDouble("estimatedCostKrw"),
                        modelName = msgDoc.getString("modelName"),
                        agentVersion = msgDoc.getString("agentVersion"),
                        provider = msgDoc.getString("provider"),
                        appCheckStatus = msgDoc.getString("appCheckStatus"),
                        deviceModel = msgDoc.getString("deviceModel"),
                        osVersion = msgDoc.getString("osVersion"),
                        detailsJson = msgDoc.getString("detailsJson"),
                        firstPassPrompt = msgDoc.getString("firstPassPrompt"),
                        firstPassResponse = msgDoc.getString("firstPassResponse"),
                        secondPassPrompt = msgDoc.getString("secondPassPrompt"),
                        secondPassResponse = msgDoc.getString("secondPassResponse"),
                        thoughtsTokens = msgDoc.getLong("thoughtsTokens")?.toInt()
                    )
                }
                if (messageEntities.isNotEmpty()) {
                    chatDao.insertMessages(messageEntities)
                }
            }
            Log.d(TAG, "Sync complete. Found ${sessionsSnapshot.size()} sessions.")
        } catch (e: Exception) {
            Log.e(TAG, "Sync FAILED: ${e.message}", e)
        }
    }
}
