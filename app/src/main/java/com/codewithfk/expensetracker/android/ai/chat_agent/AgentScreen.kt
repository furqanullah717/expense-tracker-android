package com.codewithfk.expensetracker.android.ai.chat_agent

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.codewithfk.expensetracker.android.ai.AiLoadingIndicator
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.ClickableText
import com.codewithfk.expensetracker.android.data.model.ChatMessageEntity
import com.codewithfk.expensetracker.android.utils.Utils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.io.File
import java.util.Date
import java.util.Locale

private object AgentPalette {
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
    val uncoloredIconTint = Color.Unspecified // 원본 아이콘 색상을 유지하는 아이콘 틴트
    val attachmentRemoveIconColor = Color.White // 첨부 이미지 제거 버튼 아이콘
}

private object AgentPaletteLegacy {
    const val darkTextHex = "#E0E0E0" // 레거시 렌더러 다크 모드 일반 텍스트
    const val lightTextHex = "#212121" // 레거시 렌더러 라이트 모드 일반 텍스트
    const val darkCodeBackgroundHex = "#2D2D2D" // 레거시 렌더러 다크 모드 코드 배경
    const val lightCodeBackgroundHex = "#F5F5F5" // 레거시 렌더러 라이트 모드 코드 배경
    const val darkBorderHex = "#444444" // 레거시 렌더러 다크 모드 테두리
    const val lightBorderHex = "#E0E0E0" // 레거시 렌더러 라이트 모드 테두리
    const val inlineCodeTextHex = "#D81B60" // 레거시 렌더러 인라인 코드 텍스트
}

private fun createChatImageUri(context: Context): Uri {
    val directory = File(context.cacheDir, "chat_images").apply { mkdirs() }
    val image = File.createTempFile("chat_", ".jpg", directory)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", image)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentScreen(
    viewModel: AgentViewModel = hiltViewModel()
) {
    val darkTheme = isSystemInDarkTheme()
    val messages by viewModel.messages.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val currentSessionId by viewModel.currentSessionId.collectAsState()
    val totalTokens by viewModel.sessionTotalTokens.collectAsState()
    val totalCost by viewModel.sessionTotalCost.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val selectedHistoryLimit by viewModel.selectedHistoryLimit.collectAsState()
    val chatInput by viewModel.chatInput.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val streamingMessageId = messages.lastOrNull { it.role == ChatMessageEntity.ROLE_ASSISTANT }?.id
    
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var showDetailDialog by remember { mutableStateOf<ChatMessageEntity?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<Int?>(null) } // null: none, -1: all, >0: sessionId
    var showModelSelector by remember { mutableStateOf(false) }
    var showHistoryLimitSelector by remember { mutableStateOf(false) }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var selectedFileUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        selectedImageUris = (selectedImageUris + uris).distinct().take(10 - selectedFileUris.size)
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        selectedFileUris = (selectedFileUris + uris).distinct().take(10 - selectedImageUris.size)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) pendingCameraUri?.let { selectedImageUris = (selectedImageUris + it).take(10 - selectedFileUris.size) }
        pendingCameraUri = null
    }

    // 메시지 목록이나 마지막 메시지의 내용이 변경될 때마다 하단으로 스크롤
    LaunchedEffect(messages.size, messages.lastOrNull()?.content, isLoading) {
        if (messages.isNotEmpty()) {
            delay(120)
            listState.scrollToItem(
                index = messages.lastIndex,
                scrollOffset = Int.MAX_VALUE
            )
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        viewModel.createNewChat()
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("새 채팅 시작")
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                Text(
                    "최근 대화 목록",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isSystemInDarkTheme()) AgentPalette.darkPrimaryTextColor else AgentPalette.lightPrimaryTextColor
                )

                LazyColumn {
                    items(sessions) { session ->
                        NavigationDrawerItem(
                            label = { 
                                Text(
                                    session.title, 
                                    maxLines = 1, 
                                    overflow = TextOverflow.Ellipsis 
                                ) 
                            },
                            selected = session.id == currentSessionId,
                            onClick = {
                                viewModel.selectSession(session.id!!)
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                            badge = {
                                IconButton(onClick = { showDeleteConfirmDialog = session.id }) {
                                    Icon(Icons.Default.Delete, contentDescription = "삭제", modifier = Modifier.size(20.dp))
                                }
                            }
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            containerColor = if (darkTheme) AgentPalette.darkChatBackgroundColor else AgentPalette.lightChatBackgroundColor,
            topBar = {
                TopAppBar(
                    title = { 
                        val sessionTitle = sessions.find { it.id == currentSessionId }?.title ?: "AI Agent Chat"
                        Column {
                            Text(
                                sessionTitle, 
                                fontWeight = FontWeight.Bold, 
                                maxLines = 1, 
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (totalTokens > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "🪙 ${String.format(Locale.getDefault(), "%,d", totalTokens)}",
                                        fontSize = 11.sp,
                                        color = AgentPalette.metadataTextColor
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "💸 ${Utils.formatCost(totalCost)}",
                                        fontSize = 11.sp,
                                        color = AgentPalette.metadataTextColor
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "💬 ${messages.count { it.role == ChatMessageEntity.ROLE_ASSISTANT }}",
                                        fontSize = 11.sp,
                                        color = AgentPalette.metadataTextColor
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        // 모델 선택 버튼
                        Box {
                            IconButton(onClick = { showModelSelector = true }) {
                                Icon(
                                    painter = painterResource(
                                        id = if (selectedModel.contains("pro")) 
                                            com.codewithfk.expensetracker.android.R.drawable.ic_gemini_color 
                                        else 
                                            com.codewithfk.expensetracker.android.R.drawable.ic_gemini_color
                                    ),
                                    contentDescription = "Select Model",
                                    modifier = Modifier.size(24.dp),
                                    tint = if (selectedModel.contains("pro")) AgentPalette.uncoloredIconTint else AgentPalette.metadataTextColor
                                )
                            }
                            DropdownMenu(
                                expanded = showModelSelector,
                                onDismissRequest = { showModelSelector = false }
                            ) {
                                listOf(
                                    "gemini-2.5-flash-lite" to "Flash Lite (Fast)",
                                    "gemini-2.5-flash" to "Flash (Balanced)",
                                    "gemini-2.5-pro" to "Pro (Smart)"
                                ).forEach { (modelId, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            viewModel.setModel(modelId)
                                            showModelSelector = false
                                        },
                                        leadingIcon = {
                                            RadioButton(
                                                selected = selectedModel == modelId,
                                                onClick = null
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        // 히스토리 리밋 선택 버튼 (한번에 보낼 메시지 개수)
                        Box {
                            IconButton(onClick = { showHistoryLimitSelector = true }) {
                                BadgedBox(
                                    badge = {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.offset(x = (-2).dp, y = 2.dp)
                                        ) {
                                            Text(
                                                text = if (selectedHistoryLimit <= 0) "All" else selectedHistoryLimit.toString(),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 2.dp)
                                            )
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = "History Limit",
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = showHistoryLimitSelector,
                                onDismissRequest = { showHistoryLimitSelector = false }
                            ) {
                                listOf(
                                    20 to "20개 (Economy)",
                                    50 to "50개 (Balanced)",
                                    -1 to "전체 (Unlimited)"
                                ).forEach { (limit, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            viewModel.setHistoryLimit(limit)
                                            showHistoryLimitSelector = false
                                        },
                                        leadingIcon = {
                                            RadioButton(
                                                selected = selectedHistoryLimit == limit,
                                                onClick = null
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        IconButton(onClick = { 
                            currentSessionId?.let { showDeleteConfirmDialog = it }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Current Chat")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (messages.isEmpty() && currentSessionId == null) {
                    // 빈 상태 UI
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(id = com.codewithfk.expensetracker.android.R.drawable.ic_gemini_color),
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = AgentPalette.uncoloredIconTint
                            )
                            Spacer(Modifier.height(16.dp))
                            Text("무엇을 도와드릴까요?", style = MaterialTheme.typography.headlineSmall)
                            Text("지출 내역 분석이나 금융 조언을 받아보세요.", style = MaterialTheme.typography.bodyMedium, color = AgentPalette.metadataTextColor)
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(messages) { message ->
                            if (message.content.isNotEmpty()) {
                                ChatBubble(
                                    message,
                                    isStreaming = isStreaming && message.id == streamingMessageId,
                                    onInfoClick = { showDetailDialog = it },
                                    onImageClick = { previewImageUrl = it },
                                    onAskGemini = { errorMessage ->
                                        viewModel.sendMessage(
                                            "다음 Mermaid 오류의 원인을 분석하고 수정된 Mermaid 코드를 알려줘:\n\n$errorMessage"
                                        )
                                    }
                                )
                            }
                        }
                        if (isLoading) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    AiLoadingIndicator(modifier = Modifier.size(32.dp))
                                }
                            }
                        }
                    }
                }

                // Modern Floating Input Bar
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = if (isSystemInDarkTheme()) AgentPalette.darkSurfaceColor else AgentPalette.lightSurfaceColor,
                    tonalElevation = 3.dp,
                    shadowElevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        if (selectedImageUris.isNotEmpty() || selectedFileUris.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .padding(start = 44.dp, top = 8.dp, bottom = 4.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                selectedImageUris.forEachIndexed { index, uri ->
                                    Box {
                                        AsyncImage(
                                            model = uri,
                                            contentDescription = "첨부 이미지 ${index + 1}",
                                            modifier = Modifier
                                                .size(88.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                        )
                                        IconButton(
                                            onClick = {
                                                selectedImageUris = selectedImageUris.filterIndexed { itemIndex, _ -> itemIndex != index }
                                            },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(28.dp)
                                                .background(AgentPalette.darkChatBackgroundColor.copy(alpha = 0.75f), CircleShape)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "첨부 이미지 제거", tint = AgentPalette.attachmentRemoveIconColor)
                                        }
                                    }
                                }
                                selectedFileUris.forEachIndexed { index, uri ->
                                    AssistChip(
                                        onClick = { selectedFileUris = selectedFileUris.filterIndexed { itemIndex, _ -> itemIndex != index } },
                                        label = { Text(uri.lastPathSegment?.substringAfterLast('/') ?: "파일") },
                                        leadingIcon = { Icon(Icons.Default.Create, contentDescription = null) },
                                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "파일 제거") }
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                IconButton(
                                    onClick = { showAttachmentMenu = true },
                                    enabled = selectedImageUris.size + selectedFileUris.size < 10
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "첨부")
                                }
                                DropdownMenu(
                                    expanded = showAttachmentMenu,
                                    onDismissRequest = { showAttachmentMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("사진 선택") },
                                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                        onClick = {
                                            showAttachmentMenu = false
                                            galleryLauncher.launch("image/*")
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("파일 선택") },
                                        leadingIcon = { Icon(Icons.Default.MailOutline, contentDescription = null) },
                                        onClick = {
                                            showAttachmentMenu = false
                                            fileLauncher.launch("*/*")
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("카메라 촬영") },
                                        leadingIcon = { Icon(Icons.Default.Face, contentDescription = null) },
                                        onClick = {
                                            showAttachmentMenu = false
                                            createChatImageUri(context).let { uri ->
                                                pendingCameraUri = uri
                                                cameraLauncher.launch(uri)
                                            }
                                        }
                                    )
                                }
                            }

                        OutlinedTextField(
                            value = chatInput,
                            onValueChange = { viewModel.onChatInputChanged(it) },
                            modifier = Modifier
                                .weight(1f),
                            placeholder = { 
                                Text(
                                    "AI에게 물어보세요...", 
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                ) 
                            },
                            maxLines = 5,
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                cursorColor = MaterialTheme.colorScheme.primary
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium
                        )

                        IconButton(
                            onClick = {
                                if (isLoading) {
                                    viewModel.stopGeneration()
                                } else if (chatInput.isNotBlank() || selectedImageUris.isNotEmpty() || selectedFileUris.isNotEmpty()) {
                                    viewModel.sendMessage(chatInput, selectedImageUris, selectedFileUris)
                                    selectedImageUris = emptyList()
                                    selectedFileUris = emptyList()
                                    keyboardController?.hide()
                                }
                            },
                            enabled = isLoading || chatInput.isNotBlank() || selectedImageUris.isNotEmpty() || selectedFileUris.isNotEmpty(),
                            modifier = Modifier
                                .padding(4.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isLoading) MaterialTheme.colorScheme.errorContainer 
                                    else if (chatInput.isNotBlank() || selectedImageUris.isNotEmpty() || selectedFileUris.isNotEmpty()) MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                        ) {
                            Icon(
                                imageVector = if (isLoading) Icons.Default.Close else Icons.AutoMirrored.Filled.Send,
                                contentDescription = if (isLoading) "Stop" else "Send",
                                tint = if (isLoading) MaterialTheme.colorScheme.onErrorContainer 
                                       else if (chatInput.isNotBlank() || selectedImageUris.isNotEmpty() || selectedFileUris.isNotEmpty()) MaterialTheme.colorScheme.onPrimary 
                                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    }
                }
            }
        }
    }

    // 상세 정보 다이얼로그
    if (showDetailDialog != null) {
        MessageDetailDialog(
            message = showDetailDialog!!,
            onDismiss = { showDetailDialog = null }
        )
    }
    previewImageUrl?.let { imageUrl ->
        AlertDialog(
            onDismissRequest = { previewImageUrl = null },
            confirmButton = {},
            text = {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "이미지 미리보기",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }

    // 삭제 확인 다이얼로그
    if (showDeleteConfirmDialog != null) {
        val isAll = showDeleteConfirmDialog == -1
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text(if (isAll) "전체 삭제" else "대화 삭제") },
            text = { Text(if (isAll) "모든 대화 기록을 삭제하시겠습니까?" else "이 대화방을 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isAll) viewModel.clearChat()
                        else viewModel.deleteSession(showDeleteConfirmDialog!!)
                        showDeleteConfirmDialog = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("취소")
                }
            }
        )
    }
}
@Composable
fun AiChatMarkdownView(
    markdown: String,
    modifier: Modifier = Modifier,
    isUser: Boolean = false,
    isStreaming: Boolean = false,
    onImageClick: (String) -> Unit = {},
    onAskGemini: (String) -> Unit = {}
) {
    SelectionContainer {
        Column(modifier = modifier) {
            parseMarkdownBlocks(markdown).forEach { block ->
                when (block) {
                    is MarkdownBlock.Text -> {
                        val text = markdownText(block.value)
                        ClickableText(
                            text = text,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = if (isUser) {
                                    if (isSystemInDarkTheme()) AgentPalette.darkUserMessageTextColor else AgentPalette.lightUserMessageTextColor
                                } else MaterialTheme.colorScheme.onSurface
                            ),
                            onClick = { offset ->
                                text.getStringAnnotations("URL", offset, offset)
                                    .firstOrNull()?.item
                                    ?.takeIf { isImageUrl(it) }
                                    ?.let(onImageClick)
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

private sealed interface MarkdownBlock {
    data class Text(val value: String) : MarkdownBlock
    data class Code(val value: String, val language: String) : MarkdownBlock
}

private fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val result = mutableListOf<MarkdownBlock>()
    val text = StringBuilder()
    var code: StringBuilder? = null
    var language = ""

    markdown.lines().forEach { line ->
        if (line.trimStart().startsWith("```")) {
            if (code == null) {
                // 코드 블록 시작
                if (text.isNotBlank()) {
                    result += MarkdownBlock.Text(text.toString().trim())
                }
                text.clear()
                code = StringBuilder()
                language = line.trim().removePrefix("```").trim().lowercase()
            } else {
                // 코드 블록 종료
                result += MarkdownBlock.Code(code.toString().trimEnd(), language)
                code = null
                language = ""
            }
        } else {
            if (code != null) {
                // 코드 블록 내부
                code.appendLine(line)
            } else {
                // 일반 텍스트
                text.appendLine(line)
            }
        }
    }

    // 마지막 블록 처리
    if (code != null) {
        result += MarkdownBlock.Code(code.toString().trimEnd(), language)
    }
    if (text.isNotBlank()) {
        result += MarkdownBlock.Text(text.toString().trim())
    }

    return result
}

@Composable
private fun markdownText(value: String): AnnotatedString = buildAnnotatedString {
    value.lines().forEachIndexed { index, line ->
        if (index > 0) append("\n")
        val clean = line.trimStart()
        val heading = clean.takeWhile { it == '#' }.length
        val content = clean.removePrefix("#".repeat(heading)).trim()

        when {
            heading > 0 -> {
                // 헤딩
                withStyle(SpanStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = (20 - heading * 2).sp,
                    color = if (isSystemInDarkTheme()) AgentPalette.darkPrimaryTextColor else AgentPalette.lightPrimaryTextColor
                )) {
                    append(content)
                }
            }
            clean.startsWith("> ") -> {
                // 인용구
                withStyle(SpanStyle(
                    fontStyle = FontStyle.Italic,
                    color = if (isSystemInDarkTheme()) AgentPalette.darkVariantTextColor else AgentPalette.lightVariantTextColor
                )) {
                    append("▎ ")
                    appendInlineMarkdown(clean.drop(2))
                }
            }
            clean.startsWith("- ") || clean.startsWith("* ") -> {
                // 리스트
                withStyle(SpanStyle(color = if (isSystemInDarkTheme()) AgentPalette.darkPrimaryTextColor else AgentPalette.lightPrimaryTextColor)) {
                    append("• ")
                }
                appendInlineMarkdown(clean.drop(2))
            }
            clean.matches(Regex("^\\d+\\.\\s.*")) -> {
                // 숫자 리스트
                val number = clean.substringBefore(".")
                withStyle(SpanStyle(color = if (isSystemInDarkTheme()) AgentPalette.darkPrimaryTextColor else AgentPalette.lightPrimaryTextColor)) {
                    append("$number. ")
                }
                appendInlineMarkdown(clean.substringAfter(". ").trim())
            }
            else -> appendInlineMarkdown(line)
        }
    }
}

@Composable
private fun AnnotatedString.Builder.appendInlineMarkdown(value: String) {
    val regex = Regex("""(\*\*.+?\*\*)|(\*.+?\*)|(`.+?`)|(\[.+?\]\(.+?\))""")
    var cursor = 0

    regex.findAll(value).forEach { match ->
        append(value.substring(cursor, match.range.first))
        val token = match.value

        when {
            token.startsWith("**") -> {
                // 굵은 텍스트
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = if (isSystemInDarkTheme()) AgentPalette.darkEmphasisTextColor else AgentPalette.lightEmphasisTextColor)) {
                    append(token.drop(2).dropLast(2))
                }
            }
            token.startsWith("*") -> {
                // 기울임 텍스트
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(token.drop(1).dropLast(1))
                }
            }
            token.startsWith("`") -> {
                // 인라인 코드
                withStyle(SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = MaterialTheme.colorScheme.surfaceVariant,
                    color = MaterialTheme.colorScheme.primary
                )) {
                    append(token.drop(1).dropLast(1))
                }
            }
            token.startsWith("[") -> {
                // 링크
                val linkText = token.substringAfter("[").substringBefore("]")
                val linkUrl = token.substringAfter("](").removeSuffix(")")
                withStyle(SpanStyle(
                    color = if (isSystemInDarkTheme()) AgentPalette.darkPrimaryTextColor else AgentPalette.lightPrimaryTextColor,
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

private fun isImageUrl(url: String): Boolean =
    url.startsWith("content://") ||
        Regex("\\.(png|jpe?g|gif|webp|heic)(\\?|%|$)", RegexOption.IGNORE_CASE).containsMatchIn(url)

@Composable
private fun CodeWebView(
    code: String,
    mermaid: Boolean,
    renderKey: Int = 0,
    onAskGemini: (String) -> Unit = {}
) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val backgroundColor = if (dark) AgentPalette.codeDarkBackgroundColor else AgentPalette.codeBackgroundColor
    val backgroundHex = if (dark) AgentPalette.codeDarkBackgroundHex else AgentPalette.codeBackgroundHex
    val highlightStyle = if (dark) "github-dark" else "github"
    val mermaidTheme = if (dark) "dark" else "default"
    val errorColorHex = AgentPalette.mermaidErrorHex
    val renderToken = listOf(code, mermaid, dark, renderKey).hashCode()
    val activeRenderToken = rememberUpdatedState(renderToken)
    var mermaidError by remember(code, mermaid) { mutableStateOf<String?>(null) }
    var showMermaidErrorDialog by remember { mutableStateOf(false) }
    val height = remember {
        mutableStateOf(
            if (mermaid) 300.dp
            else (code.lines().size * 20 + 40).coerceIn(60, 400).dp
        )
    }

    val html = remember(code, mermaid, dark, renderKey) {
        if (mermaid) {
            // Mermaid용 HTML
            val escapedCode = code
                .replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("${'$'}", "\\${'$'}")

            """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <script src="https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js"></script>
                <style>
                    body { 
                        margin: 0; 
                        padding: 16px;
                        background: $backgroundHex;
                        overflow: visible;
                        box-sizing: border-box;
                    }
                    #diagram { 
                        width: 100%;
                        text-align: center;
                    }
                    #diagram svg {
                        display: block;
                        width: 100%;
                        height: auto;
                        overflow: visible;
                    }
                    .error {
                        color: $errorColorHex;
                        padding: 10px;
                        font-family: monospace;
                    }
                </style>
            </head>
            <body>
                <div id="diagram"></div>
                <script>
                    const mermaidCode = `${escapedCode}`;
                    
                    mermaid.initialize({
                        startOnLoad: true,
                        theme: '$mermaidTheme',
                        securityLevel: 'loose',
                        themeVariables: {
                            'fontSize': '14px',
                            'fontFamily': 'sans-serif'
                        }
                    });
                    
                    async function renderMermaid() {
                        try {
                            const { svg } = await mermaid.render('mermaid-diagram', mermaidCode);
                            document.getElementById('diagram').innerHTML = svg;
                            if (window.Android) window.Android.onMermaidError('');
                            
                            if (window.Android) {
                                const reportHeight = () => {
                                    const diagram = document.getElementById('diagram');
                                    const bodyStyle = window.getComputedStyle(document.body);
                                    const verticalPadding = parseFloat(bodyStyle.paddingTop) + parseFloat(bodyStyle.paddingBottom);
                                    const height = Math.ceil(diagram.getBoundingClientRect().height + verticalPadding);
                                    window.Android.onDiagramRendered($renderToken, height);
                                };
                                new ResizeObserver(reportHeight).observe(document.getElementById('diagram'));
                                requestAnimationFrame(reportHeight);
                                setTimeout(reportHeight, 100);
                                setTimeout(reportHeight, 300);
                                setTimeout(reportHeight, 700);
                                if (document.fonts?.ready) document.fonts.ready.then(reportHeight);
                            }
                        } catch (error) {
                            console.error('Mermaid error:', error);
                            if (window.Android) window.Android.onMermaidError(String(error?.message || error));
                            document.getElementById('diagram').innerHTML = 
                                '<div class="error">Mermaid Error: ' + error.message + '</div>';
                        }
                    }
                    
                    renderMermaid();
                </script>
            </body>
            </html>
            """.trimIndent()
        } else {
            // 코드 하이라이팅
            val language = detectLanguage(code)

            """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/$highlightStyle.min.css">
                <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js"></script>
                <style>
                    body { 
                        margin: 0; 
                        padding: 0px;
                        background: $backgroundHex;
                        overflow-x: auto;
                    }
                    pre {
                        margin: 0;
                        padding: 0px;
                        background: $backgroundHex;
                        border-radius: 6px;
                    }
                    code {
                        font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
                        font-size: 13px;
                        line-height: 1.5;
                    }
                    .hljs {
                        background: transparent !important;
                    }
                </style>
            </head>
            <body>
                <pre><code class="language-${language}">${escapeHtml(code)}</code></pre>
                <script>
                    hljs.highlightAll();
                    
                    if (window.Android) {
                        window.Android.onCodeRendered($renderToken, document.body.scrollHeight);
                    }
                </script>
            </body>
            </html>
            """.trimIndent()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height.value)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                }

                // JavaScript 인터페이스 추가
                addJavascriptInterface(object {
                    @android.webkit.JavascriptInterface
                    fun onDiagramRendered(token: Int, newHeight: Int) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            if (token == activeRenderToken.value) {
                                val newDpHeight = newHeight.dp + 12.dp
                                height.value = maxOf(height.value, newDpHeight.coerceAtLeast(50.dp))
                            }
                        }
                    }

                    @android.webkit.JavascriptInterface
                    fun onCodeRendered(token: Int, newHeight: Int) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            if (token == activeRenderToken.value) {
                                val newDpHeight = newHeight.dp * 1.035f
                                height.value = maxOf(height.value, newDpHeight.coerceAtLeast(50.dp))
                            }
                        }
                    }

                    @android.webkit.JavascriptInterface
                    fun onMermaidError(message: String) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            mermaidError = message.takeIf { it.isNotBlank() }
                        }
                    }
                }, "Android")

                webViewClient = object : WebViewClient() {
                    @SuppressLint("ClickableViewAccessibility")
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.setOnTouchListener { _, _ -> true }
                    }
                }
            }
        },
            update = { webView ->
                if (webView.tag != html) {
                    webView.loadDataWithBaseURL(
                        "https://cdn.jsdelivr.net",
                        html,
                        "text/html",
                        "UTF-8",
                        null
                    )
                    webView.tag = html
                }
            }
        )
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp),
            shape = RoundedCornerShape(6.dp),
            color = Color.Transparent
        ) {
            Row {
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("code", code))
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                }
                if (mermaid && mermaidError != null) {
                    IconButton(
                        onClick = { showMermaidErrorDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = "Mermaid 오류 메시지 복사", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
    if (showMermaidErrorDialog && mermaidError != null) {
        val errorMessage = mermaidError.orEmpty()
        AlertDialog(
            onDismissRequest = { showMermaidErrorDialog = false },
            title = { Text("Mermaid 오류") },
            text = {
                SelectionContainer {
                    Text(errorMessage)
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            showMermaidErrorDialog = false
                            onAskGemini(errorMessage)
                        }
                    ) {
                        Text("Gemini에게 물어보기")
                    }
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText("mermaid-error", errorMessage)
                            )
                        }
                    ) {
                        Text("복사")
                    }
                    TextButton(onClick = { showMermaidErrorDialog = false }) {
                        Text("확인")
                    }
                }
            }
        )
    }
}

// 언어 감지 함수
private fun detectLanguage(code: String): String {
    return when {
        code.contains("fun ") || code.contains("val ") -> "kotlin"
        code.contains("def ") || code.contains("import ") -> "python"
        code.contains("function") || code.contains("const ") -> "javascript"
        code.contains("class ") || code.contains("public ") -> "java"
        code.contains("SELECT") || code.contains("INSERT") -> "sql"
        code.contains("<!DOCTYPE") || code.contains("<html") -> "html"
        code.contains("{") && code.contains("}") -> "json"
        else -> "plaintext"
    }
}

// HTML 이스케이프 함수
private fun escapeHtml(text: String): String {
    return text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#x27;")
        .replace("/", "&#x2F;")
}

@Composable
fun LegacyAiChatMarkdownView(
    markdown: String,
    modifier: Modifier = Modifier,
    isUser: Boolean = false
) {
    val isDarkTheme = isSystemInDarkTheme()
    var webViewHeight by remember { mutableStateOf(1.dp) }

    val encodedMarkdown = remember(markdown) {
        android.util.Base64.encodeToString(markdown.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
    }

    val html = remember(isDarkTheme, encodedMarkdown) {
        val textColor = if (isDarkTheme) AgentPaletteLegacy.darkTextHex else AgentPaletteLegacy.lightTextHex
        val codeBg = if (isDarkTheme) AgentPaletteLegacy.darkCodeBackgroundHex else AgentPaletteLegacy.lightCodeBackgroundHex
        val borderColor = if (isDarkTheme) AgentPaletteLegacy.darkBorderHex else AgentPaletteLegacy.lightBorderHex

        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/${if (isDarkTheme) "github-dark" else "github"}.min.css">
            <script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
            <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js"></script>
            <script src="https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js"></script>
            <style>
                body {
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, "Apple SD Gothic Neo", "Malgun Gothic", sans-serif;
                    font-size: 15px;
                    line-height: 1.5;
                    color: $textColor;
                    background-color: transparent;
                    margin: 0;
                    padding: 0;
                    word-wrap: break-word;
                    overflow: hidden;
                }
                #content > :first-child {
                    margin-top: 0;
                }

                #content > :last-child {
                    margin-bottom: 0;
                }
                pre {
                    background-color: $codeBg;
                    padding: 16px;
                    border-radius: 12px;
                    overflow-x: auto;
                    margin: 12px 0;
                    border: 1px solid $borderColor;
                }
                code {
                    font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
                    font-size: 13px;
                }
                :not(pre) > code {
                    background-color: $codeBg;
                    padding: 2px 6px;
                    border-radius: 6px;
                    color: ${AgentPaletteLegacy.inlineCodeTextHex};
                }
                table {
                    border-collapse: collapse;
                    width: 100%;
                    margin: 12px 0;
                }
                th, td {
                    border: 1px solid $borderColor;
                    padding: 10px;
                    text-align: left;
                }
                th {
                    background-color: $codeBg;
                    font-weight: bold;
                }
                .mermaid {
                    background-color: white;
                    padding: 12px;
                    border-radius: 12px;
                    margin: 16px 0;
                    display: flex;
                    justify-content: center;
                    border: 1px solid $borderColor;
                }
                img { max-width: 100%; }
            </style>
        </head>
        <body>
            <div id="content"></div>
            <script>
                mermaid.initialize({ 
                    startOnLoad: false, 
                    theme: '${if (isDarkTheme) "dark" else "default"}',
                    securityLevel: 'loose',
                    fontFamily: 'inherit'
                });
                
                function sendHeight() {
                    const height = Math.ceil(document.getElementById('content').getBoundingClientRect().height);
                    if (window.Android) {
                        window.Android.updateHeight(height);
                    }
                }

                function decodeUTF8(s) {
                    return decodeURIComponent(atob(s).split('').map(function(c) {
                        return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
                    }).join(''));
                }

                function render(base64Markdown) {
                    try {
                        const markdown = decodeUTF8(base64Markdown);
                        const contentDiv = document.getElementById('content');
                        contentDiv.innerHTML = marked.parse(markdown);
                        new ResizeObserver(sendHeight).observe(contentDiv);
                        
                        contentDiv.querySelectorAll('pre code').forEach((block) => {
                            hljs.highlightElement(block);
                        });
                        
                        const mermaidBlocks = contentDiv.querySelectorAll('pre code.language-mermaid');
                        let mermaidCount = mermaidBlocks.length;
                        
                        if (mermaidCount === 0) {
                            setTimeout(sendHeight, 100);
                            return;
                        }

                        mermaidBlocks.forEach((block, index) => {
                            const pre = block.parentElement;
                            const code = block.textContent;
                            const id = 'mermaid-' + index;
                            const container = document.createElement('div');
                            container.className = 'mermaid';
                            container.id = id;
                            pre.parentNode.replaceChild(container, pre);
                            
                            mermaid.render(id + '-svg', code).then(({svg}) => {
                                container.innerHTML = svg;
                                mermaidCount--;
                                if (mermaidCount === 0) setTimeout(sendHeight, 200);
                            }).catch(err => {
                                container.innerHTML = '<p style="color:red">Mermaid Error</p>';
                                mermaidCount--;
                                if (mermaidCount === 0) sendHeight();
                            });
                        });
                    } catch (e) {
                        document.getElementById('content').innerText = "Rendering Error: " + e.message;
                    }
                }
                
                window.onload = () => render('$encodedMarkdown');
                window.onresize = sendHeight;
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(webViewHeight),
        factory = { ctx ->
            WebView(ctx).apply {
                webViewClient = WebViewClient()
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    cacheMode = android.webkit.WebSettings.LOAD_CACHE_ELSE_NETWORK
                    databaseEnabled = true
                    useWideViewPort = false
                    loadWithOverviewMode = true
                }
                
                setBackgroundColor(0)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                
                addJavascriptInterface(object {
                    @android.webkit.JavascriptInterface
                    fun updateHeight(height: Float) {
                        val heightInDp = height.dp + 8.dp
                        if (kotlin.math.abs(webViewHeight.value - heightInDp.value) > 3) {
                            webViewHeight = heightInDp
                        }
                    }
                }, "Android")
            }
        },
        update = { webView ->
            val currentTag = webView.tag as? String
            if (currentTag != encodedMarkdown) {
                webView.loadDataWithBaseURL("https://local", html, "text/html", "UTF-8", null)
                webView.tag = encodedMarkdown
            }
        }
    )
}

@Composable
fun ChatBubble(
    message: ChatMessageEntity,
    isStreaming: Boolean = false,
    onInfoClick: (ChatMessageEntity) -> Unit,
    onImageClick: (String) -> Unit = {},
    onAskGemini: (String) -> Unit = {}
) {
    val isUser = message.role == ChatMessageEntity.ROLE_USER
    
    // ChatGPT-like color palette
    val darkTheme = isSystemInDarkTheme()
    val bubbleColor = when {
        isUser && darkTheme -> AgentPalette.darkUserMessageBubbleColor
        isUser -> AgentPalette.lightUserMessageBubbleColor
        darkTheme -> AgentPalette.darkAssistantMessageBubbleColor
        else -> AgentPalette.lightAssistantMessageBubbleColor
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.92f), // 화면 너비를 넓게 사용 (아이콘 공간 제거)
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Surface(
                color = bubbleColor,
                shape = RoundedCornerShape(20.dp),
                tonalElevation = if (isUser) 0.dp else 1.dp
            ) {
                AiChatMarkdownView(
                    markdown = message.content,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    isUser = isUser,
                    isStreaming = isStreaming && !isUser,
                    onImageClick = onImageClick,
                    onAskGemini = onAskGemini
                )
            }

            // 하단 정보 (시간 및 AI 메타데이터)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp)
            ) {
                if (isUser) {
                    Text(
                        text = formatTime(message.timestamp),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                } else {
                    if (message.role == ChatMessageEntity.ROLE_ASSISTANT) {
                        AiMetadataView(message, onInfoClick = { onInfoClick(message) })
                    } else {
                        Text(
                            text = formatTime(message.timestamp),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AiMetadataView(
    message: ChatMessageEntity,
    onInfoClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(start = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onInfoClick,
            modifier = Modifier.size(16.dp)
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = "Details",
                tint = AgentPalette.metadataTextColor.copy(alpha = 0.6f),
                modifier = Modifier.size(12.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        val infoText = buildString {
            append("🤖 ${message.modelName ?: "UNKNOWN"} | ")
            append("🪙 ${String.format(Locale.getDefault(), "%,d", message.totalTokens ?: 0)} | ")
            append("💸 ${Utils.formatCost(message.estimatedCostKrw ?: 0.0)} | ")
            append("⏱️ ${Utils.formatDurationMs(message.responseTimeMs ?: 0)}")
        }

        Text(
            text = infoText,
            fontSize = 10.sp,
            color = AgentPalette.metadataTextColor.copy(alpha = 0.8f)
        )
    }
}

@Composable
fun MessageDetailDialog(
    message: ChatMessageEntity,
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
                    .fillMaxWidth(),
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

fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("a h:mm", Locale.KOREA)
    return sdf.format(Date(timestamp))
}
