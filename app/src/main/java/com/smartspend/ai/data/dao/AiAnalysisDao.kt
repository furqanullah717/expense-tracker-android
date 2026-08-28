package com.smartspend.ai.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smartspend.ai.data.model.AiAnalysisEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiAnalysisDao {

    @Query("SELECT * FROM ai_analysis_history WHERE userId = :userId ORDER BY createdAt DESC")
    fun getReportsForUser(userId: String): Flow<List<AiAnalysisEntity>>

    @Query("SELECT * FROM ai_analysis_history WHERE userId = :userId")
    suspend fun getReportsForUserList(userId: String): List<AiAnalysisEntity>

    @Query("SELECT * FROM ai_analysis_history ORDER BY createdAt DESC")
    fun getAllReports(): Flow<List<AiAnalysisEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: AiAnalysisEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReports(reports: List<AiAnalysisEntity>)

    @Query("SELECT * FROM ai_analysis_history WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun getReportByFirestoreId(firestoreId: String): AiAnalysisEntity?

    @Delete
    suspend fun deleteReport(report: AiAnalysisEntity)

    @Query("DELETE FROM ai_analysis_history WHERE id = :id")
    suspend fun deleteReportById(id: Long)

    @Query("DELETE FROM ai_analysis_history WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Query("DELETE FROM ai_analysis_history WHERE firestoreId = '' OR firestoreId IS NULL")
    suspend fun deleteTemporaryReports()
}