package com.codewithfk.expensetracker.android.ai.chat_agent

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AiChatMarkdownView(
    markdown: String,
    modifier: Modifier = Modifier,
    isUser: Boolean = false,
    isStreaming: Boolean = false,
    onImageClick: (String) -> Unit = {},
    onAskGemini: (String) -> Unit = {},
    onShowDetailsAtIndex: (Int) -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    
    val userTextColor = if (isDark) AgentPalette.darkUserMessageTextColor else AgentPalette.lightUserMessageTextColor
    val textColor = if (isUser) userTextColor else onSurfaceColor
    
    val emphasisColor = if (isDark) AgentPalette.darkEmphasisTextColor else AgentPalette.lightEmphasisTextColor
    val variantTextColor = if (isDark) AgentPalette.darkVariantTextColor else AgentPalette.lightVariantTextColor
    val primaryTextColor = if (isDark) AgentPalette.darkPrimaryTextColor else AgentPalette.lightPrimaryTextColor

    SelectionContainer {
        Column(modifier = modifier) {
            // [성능 최적화 3번: 파싱 결과 캐싱]
            // parseMarkdownBlocks()는 메시지 문자열을 한 줄씩 검사하는 비용이 큰 작업인데,
            // remember가 없으면 키보드 표시, 드로어 열기 등 '아무 상태 변화'에도
            // 이 컴포저블이 재실행되면서 매번 전체를 다시 파싱했다.
            // key를 markdown으로 지정해 "문자열이 실제로 바뀔 때만" 재파싱한다.
            val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
            blocks.forEach { block ->
                when (block) {
                    is MarkdownBlock.Text -> {
                        // [성능 최적화 3번의 연장]
                        // markdownText()는 정규식으로 인라인 마크다운(굵게/기울임/링크 등)을
                        // 해석해 AnnotatedString을 만드는 비용 큰 작업이다.
                        // 텍스트 내용과 색상(다크모드 전환 시 바뀜)이 같으면 기존 결과를 재사용한다.
                        val text = remember(
                            block.value, primaryColor, primaryTextColor,
                            emphasisColor, variantTextColor, surfaceVariantColor
                        ) {
                            markdownText(
                                value = block.value,
                                primaryColor = primaryColor,
                                primaryTextColor = primaryTextColor,
                                emphasisColor = emphasisColor,
                                variantTextColor = variantTextColor,
                                surfaceVariantColor = surfaceVariantColor
                            )
                        }
                        ClickableText(
                            text = text,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(color = textColor),
                            onClick = { offset ->
                                text.getStringAnnotations("URL", offset, offset)
                                    .firstOrNull()?.item
                                    ?.takeIf { isImageUrl(it) }
                                    ?.let(onImageClick)
                                
                                text.getStringAnnotations("ACTION", offset, offset)
                                    .firstOrNull()?.let { 
                                        if (it.item.startsWith("SHOW_DETAILS:")) {
                                            val index = it.item.substringAfter("SHOW_DETAILS:").toIntOrNull() ?: 0
                                            onShowDetailsAtIndex(index)
                                        }
                                    }
                            }
                        )
                    }
                    is MarkdownBlock.Code -> {
                        val isMermaid = block.language == "mermaid"
                        CodeWebView(
                            code = block.value,
                            mermaid = isMermaid && !isStreaming,
                            renderKey = if (isStreaming) 0 else markdown.hashCode(),
                            onAskGemini = onAskGemini
                        )
                    }
                }
            }
        }
    }
}

sealed interface MarkdownBlock {
    data class Text(val value: String) : MarkdownBlock
    data class Code(val value: String, val language: String) : MarkdownBlock
}

fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val result = mutableListOf<MarkdownBlock>()
    val text = StringBuilder()
    var code: StringBuilder? = null
    var language = ""

    markdown.lines().forEach { line ->
        if (line.trimStart().startsWith("```")) {
            if (code == null) {
                if (text.isNotBlank()) {
                    result += MarkdownBlock.Text(text.toString().trim())
                }
                text.clear()
                code = StringBuilder()
                language = line.trim().removePrefix("```").trim().lowercase()
            } else {
                result += MarkdownBlock.Code(code.toString().trimEnd(), language)
                code = null
                language = ""
            }
        } else {
            if (code != null) {
                code.appendLine(line)
            } else {
                text.appendLine(line)
            }
        }
    }

    if (code != null) {
        result += MarkdownBlock.Code(code.toString().trimEnd(), language)
    }
    if (text.isNotBlank()) {
        result += MarkdownBlock.Text(text.toString().trim())
    }

    return result
}

fun markdownText(
    value: String,
    primaryColor: Color,
    primaryTextColor: Color,
    emphasisColor: Color,
    variantTextColor: Color,
    surfaceVariantColor: Color
): AnnotatedString = buildAnnotatedString {
    var detailIndex = 0
    value.lines().forEachIndexed { index, line ->
        if (index > 0) append("\n")
        val clean = line.trimStart()
        val heading = clean.takeWhile { it == '#' }.length
        val content = clean.removePrefix("#".repeat(heading)).trim()

        val isThinkingStep = clean.startsWith("🔍") || clean.startsWith("📊") || 
                             clean.startsWith("⚙️") || clean.startsWith("📝") || 
                             clean.startsWith("🔄") || clean.startsWith("✅") ||
                             clean.startsWith("📅") || clean.startsWith("🏷️")

        when {
            isThinkingStep -> {
                withStyle(SpanStyle(
                    color = AgentPalette.metadataTextColor.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    fontStyle = FontStyle.Italic
                )) {
                    append(line)
                }
            }
            clean.startsWith("**AI 답변:**") -> {
                withStyle(SpanStyle(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = primaryColor
                )) {
                    append(line)
                }
            }
            clean.contains("[자세히 보기]") -> {
                val linkText = " [자세히 보기]"
                append(line.replace("[자세히 보기]", ""))
                val start = length
                withStyle(SpanStyle(
                    color = primaryColor,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline
                )) {
                    append(linkText)
                }
                addStringAnnotation("ACTION", "SHOW_DETAILS:$detailIndex", start = start, end = length)
                detailIndex++
            }
            heading > 0 -> {
                val fontSize = (20 - heading * 2).let { if (it < 14) 14 else it }.sp
                withStyle(SpanStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = fontSize,
                    color = primaryTextColor
                )) {
                    append(content)
                }
            }
            clean.startsWith("> ") -> {
                withStyle(SpanStyle(
                    fontStyle = FontStyle.Italic,
                    color = variantTextColor
                )) {
                    append("▎ ")
                    appendInlineMarkdown(
                        value = clean.drop(2),
                        primaryTextColor = primaryTextColor,
                        emphasisColor = emphasisColor,
                        surfaceVariantColor = surfaceVariantColor,
                        primaryColor = primaryColor
                    )
                }
            }
            clean.startsWith("- ") || clean.startsWith("* ") -> {
                withStyle(SpanStyle(color = primaryTextColor)) {
                    append("• ")
                }
                appendInlineMarkdown(
                    value = clean.drop(2),
                    primaryTextColor = primaryTextColor,
                    emphasisColor = emphasisColor,
                    surfaceVariantColor = surfaceVariantColor,
                    primaryColor = primaryColor
                )
            }
            clean.matches(Regex("^\\d+\\.\\s.*")) -> {
                val number = clean.substringBefore(".")
                withStyle(SpanStyle(color = primaryTextColor)) {
                    append("$number. ")
                }
                appendInlineMarkdown(
                    value = clean.substringAfter(". ").trim(),
                    primaryTextColor = primaryTextColor,
                    emphasisColor = emphasisColor,
                    surfaceVariantColor = surfaceVariantColor,
                    primaryColor = primaryColor
                )
            }
            else -> appendInlineMarkdown(
                value = line,
                primaryTextColor = primaryTextColor,
                emphasisColor = emphasisColor,
                surfaceVariantColor = surfaceVariantColor,
                primaryColor = primaryColor
            )
        }
    }
}

fun AnnotatedString.Builder.appendInlineMarkdown(
    value: String,
    primaryTextColor: Color,
    emphasisColor: Color,
    surfaceVariantColor: Color,
    primaryColor: Color
) {
    val regex = Regex("""(\*\*.+?\*\*)|(\*.+?\*)|(`.+?`)|(\[.+?\]\(.+?\))""")
    var cursor = 0

    regex.findAll(value).forEach { match ->
        append(value.substring(cursor, match.range.first))
        val token = match.value

        when {
            token.startsWith("**") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = emphasisColor)) {
                    append(token.drop(2).dropLast(2))
                }
            }
            token.startsWith("*") -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(token.drop(1).dropLast(1))
                }
            }
            token.startsWith("`") -> {
                withStyle(SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = surfaceVariantColor,
                    color = primaryColor
                )) {
                    append(token.drop(1).dropLast(1))
                }
            }
            token.startsWith("[") -> {
                val linkText = token.substringAfter("[").substringBefore("]")
                val linkUrl = token.substringAfter("](").removeSuffix(")")
                withStyle(SpanStyle(
                    color = primaryTextColor,
                    textDecoration = TextDecoration.Underline
                )) {
                    addStringAnnotation("URL", linkUrl, start = length, end = length + linkText.length)
                    append(linkText)
                }
            }
        }

        cursor = match.range.last + 1
    }

    append(value.substring(cursor))
}

fun isImageUrl(url: String): Boolean =
    url.startsWith("content://") ||
            Regex("\\.(png|jpe?g|gif|webp|heic)(\\?|%|$)", RegexOption.IGNORE_CASE).containsMatchIn(url)
