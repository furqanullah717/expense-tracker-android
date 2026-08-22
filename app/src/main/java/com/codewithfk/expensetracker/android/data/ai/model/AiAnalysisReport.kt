package com.codewithfk.expensetracker.android.data.ai.model

import android.annotation.SuppressLint
import kotlinx.serialization.Serializable

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class AiAnalysisReport @SuppressLint("UnsafeOptInUsageError") constructor(
    val summary: String,
    val insights: List<String>,
    val savingTips: List<String>,
    val period: String
)
