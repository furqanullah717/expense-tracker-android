package com.codewithfk.expensetracker.android.ai.gateway

/**
 * AI 모델 메타정보의 단일 출처(Single Source of Truth).
 * 엔티티 기본값, 비용 계산, UI 모델 선택 등에서 구현체(FirebaseAiGateway) 없이 참조해야 하는 값들을 보관한다.
 */
object AiModelCatalog {
    const val liteModelName = "gemini-3.5-flash-lite"
    const val modelName = "gemini-3.5-flash-lite"
    const val smartModelName = "gemini-3.7-flash"
    const val provider = "Firebase"
    const val agentVersion = "v2.2"

    data class ModelPricing(
        val inputUsdPerMillion: Double,
        val outputUsdPerMillion: Double
    )

    fun pricingFor(modelName: String): ModelPricing {
        return when (modelName) {
            // Gemini 3.5 Flash-Lite
            liteModelName -> ModelPricing(
                inputUsdPerMillion = 0.10,
                outputUsdPerMillion = 0.40
            )

            // Gemini 3.5 Flash-Lite
            modelName -> ModelPricing(
                inputUsdPerMillion = 0.10,
                outputUsdPerMillion = 0.40
            )

            // Gemini 3.1 Pro Preview
            // 입력 프롬프트가 20만 토큰 이하인 일반적인 앱 요청 기준
            smartModelName -> ModelPricing(
                inputUsdPerMillion = 0.38,
                outputUsdPerMillion = 1.88
            )

            else -> error("지원하지 않는 모델입니다: $modelName")
        }
    }

    fun calculateCostUsd(
        modelName: String,
        promptTokens: Int,
        candidatesTokens: Int,
        thoughtsTokens: Int = 0
    ): Double {
        val pricing = pricingFor(modelName)
        val inputCost = promptTokens * pricing.inputUsdPerMillion / 1_000_000
        val outputCost = (candidatesTokens + thoughtsTokens) *
                pricing.outputUsdPerMillion / 1_000_000

        return inputCost + outputCost
    }
}
