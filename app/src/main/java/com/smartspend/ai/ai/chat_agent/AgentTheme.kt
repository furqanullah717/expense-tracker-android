package com.smartspend.ai.ai.chat_agent

import androidx.compose.ui.graphics.Color

object AgentPalette {
    val geminiOriginalIconTint = Color.Unspecified // Gemini original color icon tint
    val lightChatBackgroundColor = Color(0xFFF5F7FA) // `Scaffold` ?�체 채팅 ?�면???�이??모드 쿨그?�이 배경
    val darkChatBackgroundColor = Color(0xFF0F172A) // `Scaffold` ?�체 채팅 ?�면???�크 모드 ???�이�?배경
    val lightUserMessageBubbleColor = Color(0xFFD9F2F0) // `ChatBubble` ?�용?��? 보낸 메시지???�이??모드 민트 버블
    val darkUserMessageBubbleColor = Color(0xFF164E63) // `ChatBubble` ?�용?��? 보낸 메시지???�크 모드 ????버블
    val lightUserMessageTextColor = Color(0xFF0F4C5C) // ?�이??모드 ?�용??버블 ?�의 ?�반 Markdown ?�스??
    val darkUserMessageTextColor = Color(0xFFD9F2F0) // ?�크 모드 ?�용??버블 ?�의 ?�반 Markdown ?�스??
    val lightAssistantMessageBubbleColor = Color(0xFFE9EEF3) // `ChatBubble` AI ?��????�이??모드 ?�레?�트 그레??버블
    val darkAssistantMessageBubbleColor = Color(0xFF1E293B) // `ChatBubble` AI ?��????�크 모드 ?�레?�트 버블
    val metadataTextColor = Color(0xFF64748B) // ?�단 ?�계, ?�간, 메�??�이?�의 보조 ?�스??
    val lightEmphasisTextColor = Color(0xFF1565C0) // ?�이??모드 `**강조 ?�스??*` ?�상
    val darkEmphasisTextColor = Color(0xFF90CAF9) // ?�크 모드 `**강조 ?�스??*` ?�상
    val lightPrimaryTextColor = Color(0xFF1565C0) // ?�이??모드 ?�목, 링크, ?�라??코드 ?�스??
    val darkPrimaryTextColor = Color(0xFF90CAF9) // ?�크 모드 ?�목, 링크, ?�라??코드 ?�스??
    val lightSurfaceColor = Color(0xFFF8FAFC) // ?�이??모드 ?�력�?보조 ?�면
    val darkSurfaceColor = Color(0xFF1E293B) // ?�크 모드 ?�력�?보조 ?�면
    val lightVariantTextColor = Color(0xFF475569) // ?�이??모드 보조 ?�스??
    val darkVariantTextColor = Color(0xFFCBD5E1) // ?�크 모드 보조 ?�스??
    val lightErrorContainerColor = Color(0xFFFFE4E6) // ?�이??모드 ?�류 ?�력�?
    val darkErrorContainerColor = Color(0xFF7F1D1D) // ?�크 모드 ?�류 ?�력�?
    val codeBackgroundColor = Color(0xFFC7CDD4) // 코드/Mermaid WebView???�이??모드 배경
    val codeDarkBackgroundColor = Color(0xFF1E1E1E) // 코드/Mermaid WebView???�크 모드 배경
    const val codeBackgroundHex = "#C7CDD4" // 코드/Mermaid HTML???�이??배경
    const val codeDarkBackgroundHex = "#1E1E1E" // 코드/Mermaid HTML???�크 배경
    const val mermaidErrorHex = "#FF5252" // Mermaid ?�더�??�류 문구
    val attachmentRemoveIconColor = Color.White // 첨�? ?��?지 ?�거 버튼 ?�이�?
}

object AgentPaletteLegacy {
    const val darkTextHex = "#E0E0E0" // ?�거???�더???�크 모드 ?�반 ?�스??
    const val lightTextHex = "#212121" // ?�거???�더???�이??모드 ?�반 ?�스??
    const val darkCodeBackgroundHex = "#2D2D2D" // ?�거???�더???�크 모드 코드 배경
    const val lightCodeBackgroundHex = "#F5F5F5" // ?�거???�더???�이??모드 코드 배경
    const val darkBorderHex = "#444444" // ?�거???�더???�크 모드 ?�두�?
    const val lightBorderHex = "#E0E0E0" // ?�거???�더???�이??모드 ?�두�?
    const val inlineCodeTextHex = "#D81B60" // ?�거???�더???�라??코드 ?�스??
}
