package com.codewithfk.expensetracker.android.data.repository

import android.util.Log
import com.codewithfk.expensetracker.android.ai.core.FirebaseAiGateway
import com.codewithfk.expensetracker.android.data.dao.AiAnalysisDao
import com.codewithfk.expensetracker.android.data.model.AiAnalysisEntity
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiHistoryRepository @Inject constructor(
    private val aiAnalysisDao: AiAnalysisDao
) {
    private val TAG = "AiHistoryRepository"

    fun getCurrentUserId(): String {
        return Firebase.auth.currentUser?.uid ?: "anonymous"
    }

    private fun getUserCollection(userId: String) =
        Firebase.firestore.collection("users").document(userId).collection("ai_analysis_history")

    suspend fun saveReport(entity: AiAnalysisEntity, shouldSaveToFirestore: Boolean = true): Long {
        Log.d(TAG, "saveReport: shouldSaveToFirestore=$shouldSaveToFirestore, title=${entity.title}")
        val uid = if (entity.userId.isNotBlank()) entity.userId else getCurrentUserId()
        
        // 1. 서버 저장 여부에 따라 firestoreId 결정
        val docId = if (shouldSaveToFirestore) {
            if (entity.firestoreId.isNotBlank()) entity.firestoreId else UUID.randomUUID().toString()
        } else {
            "" // 서버 저장 안 할 경우 명시적으로 빈 값
        }
        
        val updatedEntity = entity.copy(userId = uid, firestoreId = docId)

        // 2. 중복 체크 (서버 저장할 경우에만 firestoreId로 체크)
        val existing = if (docId.isNotBlank()) aiAnalysisDao.getReportByFirestoreId(docId) else null
        val finalEntityToSave = if (existing != null && entity.id == 0L) {
            updatedEntity.copy(id = existing.id)
        } else {
            updatedEntity
        }

        // 3. 로컬 DB(Room) 저장
        val localId = aiAnalysisDao.insertReport(finalEntityToSave)
        val finalEntity = finalEntityToSave.copy(id = localId)

        // 4. Firestore 저장 (조건 엄격화)
        if (shouldSaveToFirestore && docId.isNotBlank() && uid != "anonymous") {
            try {
                val firestoreData = hashMapOf(
                    "userId" to uid,
                    "firestoreId" to docId,
                    "title" to finalEntity.title,
                    "startDate" to finalEntity.startDate,
                    "endDate" to finalEntity.endDate,
                    "transactionCount" to finalEntity.transactionCount,
                    "totalExpenseSum" to finalEntity.totalExpenseSum,
                    "totalIncomeSum" to finalEntity.totalIncomeSum,
                    "geminiPrompt" to finalEntity.geminiPrompt,
                    "rawResponseJson" to finalEntity.rawResponseJson,
                    "responseTimeMs" to finalEntity.responseTimeMs,
                    "promptTokens" to finalEntity.promptTokens,
                    "candidatesTokens" to finalEntity.candidatesTokens,
                    "totalTokens" to finalEntity.totalTokens,
                    "estimatedCostUsd" to finalEntity.estimatedCostUsd,
                    "estimatedCostKrw" to finalEntity.estimatedCostKrw,
                    "modelName" to finalEntity.modelName,
                    "agentVersion" to finalEntity.agentVersion,
                    "provider" to finalEntity.provider,
                    "authMethod" to finalEntity.authMethod,
                    "appCheckStatus" to finalEntity.appCheckStatus,
                    "networkType" to finalEntity.networkType,
                    "deviceModel" to finalEntity.deviceModel,
                    "osVersion" to finalEntity.osVersion,
                    "promptCharLength" to finalEntity.promptCharLength,
                    "responseCharLength" to finalEntity.responseCharLength,
                    "finishReason" to finalEntity.finishReason,
                    "summary" to finalEntity.summary,
                    "insights" to finalEntity.insights,
                    "savingTips" to finalEntity.savingTips,
                    "createdAt" to finalEntity.createdAt
                )
                getUserCollection(uid).document(docId).set(firestoreData).await()
                Log.d(TAG, "Successfully saved AI analysis to Firestore for user $uid: $docId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save AI analysis to Firestore: ${e.message}", e)
            }
        } else {
            Log.d(TAG, "Skipping Firestore save. shouldSave=$shouldSaveToFirestore, docId=$docId, uid=$uid")
        }

        return localId
    }

    suspend fun deleteReport(entity: AiAnalysisEntity) {
        val uid = if (entity.userId.isNotBlank()) entity.userId else getCurrentUserId()

        // 1. Delete from local Room DB
        aiAnalysisDao.deleteReport(entity)

        // 2. Delete from Firestore user subcollection
        if (entity.firestoreId.isNotBlank()) {
            try {
                getUserCollection(uid).document(entity.firestoreId).delete().await()
                Log.d(TAG, "Successfully deleted AI analysis from Firestore for user $uid: ${entity.firestoreId}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete AI analysis from Firestore: ${e.message}", e)
            }
        }
    }

    suspend fun syncFromFirestore(userId: String = getCurrentUserId()) {
        if (userId == "anonymous" || userId.isBlank()) return
        try {
            val snapshot = getUserCollection(userId).get().await()
            val existingList = aiAnalysisDao.getReportsForUserList(userId)

            // Deduplicate existing local rows by firestoreId if any duplicates exist
            val groupedByFirestoreId = existingList.filter { it.firestoreId.isNotBlank() }.groupBy { it.firestoreId }
            for ((_, duplicates) in groupedByFirestoreId) {
                if (duplicates.size > 1) {
                    for (extra in duplicates.drop(1)) {
                        aiAnalysisDao.deleteReport(extra)
                    }
                }
            }

            val existingMap = groupedByFirestoreId.mapValues { it.value.first() }

            val entities = snapshot.documents.mapNotNull { doc ->
                try {
                    val firestoreId = doc.getString("firestoreId") ?: doc.id
                    // Preserve the local SQLite auto-increment primary key ID so Room replaces instead of inserting new rows
                    val existingLocalId = existingMap[firestoreId]?.id ?: 0L

                    AiAnalysisEntity(
                        id = existingLocalId,
                        userId = doc.getString("userId") ?: userId,
                        firestoreId = firestoreId,
                        title = doc.getString("title") ?: "",
                        startDate = doc.getString("startDate") ?: "",
                        endDate = doc.getString("endDate") ?: "",
                        transactionCount = (doc.getLong("transactionCount") ?: 0L).toInt(),
                        totalExpenseSum = doc.getDouble("totalExpenseSum") ?: 0.0,
                        totalIncomeSum = doc.getDouble("totalIncomeSum") ?: 0.0,
                        geminiPrompt = doc.getString("geminiPrompt") ?: "",
                        rawResponseJson = doc.getString("rawResponseJson") ?: "",
                        responseTimeMs = doc.getLong("responseTimeMs") ?: 0L,
                        promptTokens = (doc.getLong("promptTokens") ?: 0L).toInt(),
                        candidatesTokens = (doc.getLong("candidatesTokens") ?: 0L).toInt(),
                        totalTokens = (doc.getLong("totalTokens") ?: 0L).toInt(),
                        estimatedCostUsd = doc.getDouble("estimatedCostUsd") ?: 0.0,
                        estimatedCostKrw = doc.getDouble("estimatedCostKrw") ?: 0.0,
                        modelName = doc.getString("modelName") ?: FirebaseAiGateway.modelName,
                        agentVersion = doc.getString("agentVersion") ?: FirebaseAiGateway.agentVersion,
                        provider = doc.getString("provider") ?: FirebaseAiGateway.provider,
                        authMethod = doc.getString("authMethod") ?: "Firebase App Check (Debug/Integrity)",
                        appCheckStatus = doc.getString("appCheckStatus") ?: "Verified",
                        networkType = doc.getString("networkType") ?: "Unknown",
                        deviceModel = doc.getString("deviceModel") ?: "",
                        osVersion = doc.getString("osVersion") ?: "",
                        promptCharLength = (doc.getLong("promptCharLength") ?: 0L).toInt(),
                        responseCharLength = (doc.getLong("responseCharLength") ?: 0L).toInt(),
                        finishReason = doc.getString("finishReason") ?: "STOP",
                        summary = doc.getString("summary") ?: "",
                        insights = doc.getString("insights") ?: "",
                        savingTips = doc.getString("savingTips") ?: "",
                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error mapping document ${doc.id}: ${e.message}")
                    null
                }
            }

            if (entities.isNotEmpty()) {
                aiAnalysisDao.insertReports(entities)
                Log.d(TAG, "Synced ${entities.size} records from Firestore for user $userId to Room DB.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync from Firestore for user $userId: ${e.message}", e)
        }
    }
}
