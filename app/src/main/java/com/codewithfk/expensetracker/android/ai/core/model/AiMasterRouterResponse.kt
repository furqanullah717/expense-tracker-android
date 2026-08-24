package com.codewithfk.expensetracker.android.ai.core.model

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(InternalSerializationApi::class)
@Serializable
data class AiMasterRouterResponse(
    val intent: String,
    val sub_categories: List<String> = emptyList(),
    val start_date: String? = null,
    val end_date: String? = null,
    val confidence_score: Double,
    val reasoning: String,
    val reply_message: String? = null
)
