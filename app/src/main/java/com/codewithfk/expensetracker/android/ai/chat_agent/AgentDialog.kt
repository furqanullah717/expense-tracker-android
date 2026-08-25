package com.codewithfk.expensetracker.android.ai.chat_agent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.codewithfk.expensetracker.android.ai.gateway.ChatResponse
import com.codewithfk.expensetracker.android.data.model.ChatMessageEntity
import com.codewithfk.expensetracker.android.data.model.ChatSessionEntity
import com.codewithfk.expensetracker.android.data.model.ExpenseEntity
import com.codewithfk.expensetracker.android.utils.Utils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SimpleDetailListDialog(
    data: ChatResponse.ShowDetails,
    onDismiss: () -> Unit
) {
    val sortedItems = remember(data.items) { data.items.reversed() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(data.title, fontWeight = FontWeight.Bold) },
        text = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                LazyColumn(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sortedItems) { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(item.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(item.date, style = MaterialTheme.typography.labelSmall)
                                Text(
                                    Utils.formatCurrency(item.amount),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(top = 8.dp), thickness = 0.5.dp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        }
    )
}

@Composable
fun ActionConfirmDialog(
    request: ChatResponse.ActionRequest,
    onConfirm: (List<ExpenseEntity>) -> Unit,
    onDismiss: () -> Unit
) {
    val sortedItems = remember(request.targetItems) { request.targetItems.reversed() }
    val selectedItems = remember { mutableStateListOf<ExpenseEntity>().apply { addAll(request.targetItems) } }

    var updatedValue by remember { mutableStateOf(request.newValue ?: "") }
    var showCategorySelector by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            val title = when (request.action) {
                "DELETE" -> "데이터 삭제 확인"
                "UPDATE" -> "데이터 수정 확인"
                "INSERT" -> "데이터 추가 확인"
                else -> "작업 확인"
            }
            Text(title, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "AI가 분석한 결과입니다. 아래 항목들에 대해 작업을 진행할까요?",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (request.action == "UPDATE" && request.updateField != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "변경 내용: ${request.updateField}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            if (request.updateField == "category") {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                        .clickable { showCategorySelector = true }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = updatedValue.ifBlank { "카테고리 선택" },
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }

                                if (showCategorySelector) {
                                    Dialog(onDismissRequest = { showCategorySelector = false }) {
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            modifier = Modifier.padding(16.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Text("카테고리 선택", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                                                    items(com.codewithfk.expensetracker.android.utils.FakeDataGenerator.ALL_CATEGORIES.toList()) { category ->
                                                        Text(
                                                            text = category,
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clickable {
                                                                    updatedValue = category
                                                                    showCategorySelector = false
                                                                }
                                                                .padding(
                                                                    vertical = 12.dp,
                                                                    horizontal = 8.dp
                                                                ),
                                                            style = MaterialTheme.typography.bodyMedium
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                OutlinedTextField(
                                    value = updatedValue,
                                    onValueChange = { updatedValue = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("새로운 ${request.updateField}") },
                                    singleLine = true
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val allSelected = selectedItems.size == request.targetItems.size
                    TextButton(
                        onClick = {
                            if (allSelected) {
                                selectedItems.clear()
                            } else {
                                selectedItems.clear()
                                selectedItems.addAll(request.targetItems)
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            if (allSelected) "전체 해제" else "전체 선택",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 250.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    LazyColumn(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(sortedItems) { item ->
                            val isSelected = selectedItems.contains(item)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isSelected) selectedItems.remove(item) else selectedItems.add(
                                            item
                                        )
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        if (checked) selectedItems.add(item) else selectedItems.remove(item)
                                    }
                                )
                                Column {
                                    Text(item.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                    Text(
                                        "${item.date} | ${Utils.formatCurrency(item.amount)} | ${item.category}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "💡 이유: ${request.reasoning}",
                    style = MaterialTheme.typography.labelSmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(selectedItems.toList().map {
                        if (request.action == "UPDATE") {
                            when (request.updateField) {
                                "title" -> it.copy(title = updatedValue)
                                "amount" -> it.copy(amount = updatedValue.toDoubleOrNull() ?: it.amount)
                                "category" -> it.copy(category = updatedValue)
                                else -> it
                            }
                        } else it
                    })
                },
                enabled = (selectedItems.isNotEmpty() || request.action == "INSERT") && (request.action != "UPDATE" || updatedValue.isNotBlank()),
                colors = if (request.action == "DELETE") ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                else ButtonDefaults.buttonColors()
            ) {
                Text("확인")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

@Composable
fun MessageDetailDialog(
    message: ChatMessageEntity,
    onDismiss: () -> Unit
) {
    var isFirstPromptExpanded by remember { mutableStateOf(false) }
    var isFirstResponseExpanded by remember { mutableStateOf(false) }
    var isSecondPromptExpanded by remember { mutableStateOf(false) }
    var isSecondResponseExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "상세정보",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                DetailRow("Provider", message.provider ?: "Firebase")
                DetailRow("Model", message.modelName ?: "Unknown")
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                DetailRow("Response Time", Utils.formatDurationMs(message.responseTimeMs ?: 0))
                DetailRow("Prompt Tokens", String.format(Locale.getDefault(), "%,d", message.promptTokens ?: 0))
                DetailRow("Candidates Tokens", String.format(Locale.getDefault(), "%,d", message.candidatesTokens ?: 0))
                if ((message.thoughtsTokens ?: 0) > 0) {
                    DetailRow("Thoughts Tokens", String.format(Locale.getDefault(), "%,d", message.thoughtsTokens ?: 0))
                }
                DetailRow("Total Tokens", String.format(Locale.getDefault(), "%,d", message.totalTokens ?: 0))
                DetailRow("Estimated Cost", Utils.formatCost(message.estimatedCostKrw ?: 0.0))

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                DetailRow("Device", message.deviceModel ?: Utils.getDeviceModel())
                DetailRow("OS", message.osVersion ?: Utils.getOsVersion())
                DetailRow("Auth", message.appCheckStatus ?: "Verified")

                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date(message.timestamp))
                val timeStr = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(message.timestamp))
                DetailRow("Date", dateStr)
                DetailRow("Time", timeStr)

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                DetailRow("Input History Count", message.appCheckStatus ?: "0")

                if (message.firstPassPrompt != null || message.firstPassResponse != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    
                    message.firstPassPrompt?.let {
                        ExpandablePassSection(
                            title = "1st Pass: Gemini 전송 프롬프트",
                            content = it,
                            isExpanded = isFirstPromptExpanded,
                            onToggle = { isFirstPromptExpanded = !isFirstPromptExpanded },
                            charCount = it.length
                        )
                    }
                    
                    message.firstPassResponse?.let {
                        ExpandablePassSection(
                            title = "1st Pass: AI 응답 원본 JSON",
                            content = it,
                            isExpanded = isFirstResponseExpanded,
                            onToggle = { isFirstResponseExpanded = !isFirstResponseExpanded },
                            charCount = it.length
                        )
                    }
                }

                if (message.secondPassPrompt != null || message.secondPassResponse != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    message.secondPassPrompt?.let {
                        ExpandablePassSection(
                            title = "2nd Pass: Gemini 전송 프롬프트",
                            content = it,
                            isExpanded = isSecondPromptExpanded,
                            onToggle = { isSecondPromptExpanded = !isSecondPromptExpanded },
                            charCount = it.length
                        )
                    }

                    message.secondPassResponse?.let {
                        ExpandablePassSection(
                            title = "2nd Pass: AI 응답 원본",
                            content = it,
                            isExpanded = isSecondResponseExpanded,
                            onToggle = { isSecondResponseExpanded = !isSecondResponseExpanded },
                            charCount = it.length
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun ExpandablePassSection(
    title: String,
    content: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    charCount: Int
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onToggle() }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$title (${charCount}자)",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
            exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(8.dp),
                color = if (isSystemInDarkTheme()) Color(0xFF1E293B) else Color(0xFFF1F5F9)
            ) {
                SelectionContainer {
                    Text(
                        text = content,
                        color = if (isSystemInDarkTheme()) Color(0xFFCBD5E1) else Color(0xFF475569),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SessionInfoDialog(
    session: ChatSessionEntity,
    stats: AgentViewModel.SessionStats?,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "대화창 정보",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                DetailRow("제목", session.title)

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                DetailRow(
                    "Firestore Synchronization",
                    if (session.firestoreId.isBlank()) "Local only" else "Synced"
                )
                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date(session.lastMessageTime))
                val timeStr = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(session.lastMessageTime))
                DetailRow("Last Updated", "$dateStr $timeStr")
                DetailRow("Messages", "${stats?.assistantMessageCount ?: 0}")
                DetailRow(
                    "Avg. Response Time",
                    stats?.avgResponseTimeMs?.let { Utils.formatDurationMs(it) } ?: "-"
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                DetailRow("Total Tokens", String.format(Locale.getDefault(), "%,d", stats?.totalTokens ?: 0))
                DetailRow("Total Est. Cost", Utils.formatCost(stats?.totalCostKrw ?: 0.0))

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("Delete")
                    }
                    Button(
                        onClick = onRename,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Rename")
                    }
                }
            }
        }
    }
}

@Composable
fun RenameSessionDialog(
    currentTitle: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(currentTitle) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        focusRequester.requestFocus()
        keyboard?.show()
    }

    AlertDialog(
        onDismissRequest = {
            keyboard?.hide()
            onDismiss()
        },
        title = { Text("대화창 이름 변경", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                singleLine = true,
                label = { Text("새 이름") }
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    keyboard?.hide()
                    onConfirm(text)
                },
                enabled = text.trim().isNotEmpty()
            ) {
                Text("확인")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                keyboard?.hide()
                onDismiss()
            }) {
                Text("취소")
            }
        }
    )
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = AgentPalette.metadataTextColor
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End
        )
    }
}
