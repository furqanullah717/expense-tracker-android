package com.codewithfk.expensetracker.android.ai.chat_agent

import androidx.compose.ui.graphics.Color

object AgentPalette {
    val geminiOriginalIconTint = Color.Unspecified // Gemini original color icon tint
    val lightChatBackgroundColor = Color(0xFFF5F7FA) // `Scaffold` 전체 채팅 화면의 라이트 모드 쿨그레이 배경
    val darkChatBackgroundColor = Color(0xFF0F172A) // `Scaffold` 전체 채팅 화면의 다크 모드 딥 네이비 배경
    val lightUserMessageBubbleColor = Color(0xFFD9F2F0) // `ChatBubble` 사용자가 보낸 메시지의 라이트 모드 민트 버블
    val darkUserMessageBubbleColor = Color(0xFF164E63) // `ChatBubble` 사용자가 보낸 메시지의 다크 모드 딥 틸 버블
    val lightUserMessageTextColor = Color(0xFF0F4C5C) // 라이트 모드 사용자 버블 안의 일반 Markdown 텍스트
    val darkUserMessageTextColor = Color(0xFFD9F2F0) // 다크 모드 사용자 버블 안의 일반 Markdown 텍스트
    val lightAssistantMessageBubbleColor = Color(0xFFE9EEF3) // `ChatBubble` AI 답변의 라이트 모드 슬레이트 그레이 버블
    val darkAssistantMessageBubbleColor = Color(0xFF1E293B) // `ChatBubble` AI 답변의 다크 모드 슬레이트 버블
    val metadataTextColor = Color(0xFF64748B) // 상단 통계, 시간, 메타데이터의 보조 텍스트
    val lightEmphasisTextColor = Color(0xFF1565C0) // 라이트 모드 `**강조 텍스트**` 색상
    val darkEmphasisTextColor = Color(0xFF90CAF9) // 다크 모드 `**강조 텍스트**` 색상
    val lightPrimaryTextColor = Color(0xFF1565C0) // 라이트 모드 제목, 링크, 인라인 코드 텍스트
    val darkPrimaryTextColor = Color(0xFF90CAF9) // 다크 모드 제목, 링크, 인라인 코드 텍스트
    val lightSurfaceColor = Color(0xFFF8FAFC) // 라이트 모드 입력창/보조 표면
    val darkSurfaceColor = Color(0xFF1E293B) // 다크 모드 입력창/보조 표면
    val lightVariantTextColor = Color(0xFF475569) // 라이트 모드 보조 텍스트
    val darkVariantTextColor = Color(0xFFCBD5E1) // 다크 모드 보조 텍스트
    val lightErrorContainerColor = Color(0xFFFFE4E6) // 라이트 모드 오류 입력창
    val darkErrorContainerColor = Color(0xFF7F1D1D) // 다크 모드 오류 입력창
    val codeBackgroundColor = Color(0xFFC7CDD4) // 코드/Mermaid WebView의 라이트 모드 배경
    val codeDarkBackgroundColor = Color(0xFF1E1E1E) // 코드/Mermaid WebView의 다크 모드 배경
    const val codeBackgroundHex = "#C7CDD4" // 코드/Mermaid HTML의 라이트 배경
    const val codeDarkBackgroundHex = "#1E1E1E" // 코드/Mermaid HTML의 다크 배경
    const val mermaidErrorHex = "#FF5252" // Mermaid 렌더링 오류 문구
    val attachmentRemoveIconColor = Color.White // 첨부 이미지 제거 버튼 아이콘
}

object AgentPaletteLegacy {
    const val darkTextHex = "#E0E0E0" // 레거시 렌더러 다크 모드 일반 텍스트
    const val lightTextHex = "#212121" // 레거시 렌더러 라이트 모드 일반 텍스트
    const val darkCodeBackgroundHex = "#2D2D2D" // 레거시 렌더러 다크 모드 코드 배경
    const val lightCodeBackgroundHex = "#F5F5F5" // 레거시 렌더러 라이트 모드 코드 배경
    const val darkBorderHex = "#444444" // 레거시 렌더러 다크 모드 테두리
    const val lightBorderHex = "#E0E0E0" // 레거시 렌더러 라이트 모드 테두리
    const val inlineCodeTextHex = "#D81B60" // 레거시 렌더러 인라인 코드 텍스트
}
