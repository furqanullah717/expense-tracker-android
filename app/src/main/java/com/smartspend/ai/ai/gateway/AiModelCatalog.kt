package com.smartspend.ai.ai.gateway

/**
 * AI ëª¨ë¸ ë©”í??•ë³´???¨ì¼ ì¶œì²˜(Single Source of Truth).
 * ?”í‹°??ê¸°ë³¸ê°? ë¹„ìš© ê³„ì‚°, UI ëª¨ë¸ ? íƒ ?±ì—??êµ¬í˜„ì²?FirebaseAiGateway) ?†ì´ ì°¸ì¡°?´ì•¼ ?˜ëŠ” ê°’ë“¤??ë³´ê??œë‹¤.
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
            // ?…ë ¥ ?„ë¡¬?„íŠ¸ê°€ 20ë§?? í° ?´í•˜???¼ë°˜?ì¸ ???”ì²­ ê¸°ì?
            smartModelName -> ModelPricing(
                inputUsdPerMillion = 0.38,
                outputUsdPerMillion = 1.88
            )

            else -> error("ì§€?í•˜ì§€ ?ŠëŠ” ëª¨ë¸?…ë‹ˆ?? $modelName")
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
