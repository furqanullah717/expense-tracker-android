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

@OptIn(InternalSerializationApi::class)
@Serializable
data class AiSecondPassResponse(
    val relevant_names: List<String> = emptyList(),
    val operation: String? = null,
    val action: String? = null,
    val update_field: String? = null,
    val reasoning: String? = null
)
