package com.codewithfk.expensetracker.android.ai.chat_agent

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.codewithfk.expensetracker.android.ai.AiLoadingIndicator
import com.codewithfk.expensetracker.android.data.model.ChatMessageEntity
import com.codewithfk.expensetracker.android.ai.gateway.AiModelCatalog
import com.codewithfk.expensetracker.android.utils.Utils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun createChatImageUri(context: Context): Uri {
    val directory = File(context.cacheDir, "chat_images").apply { mkdirs() }
    val image = File.createTempFile("chat_", ".jpg", directory)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", image)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentScreen(
    navController: NavController,
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
    val pendingAction by viewModel.pendingAction.collectAsState()
    val detailItems by viewModel.detailItems.collectAsState()
    val sessionStats by viewModel.sessionStats.collectAsState()
    val streamingMessageId = messages.lastOrNull { it.role == ChatMessageEntity.ROLE_ASSISTANT }?.id

    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var showDetailDialog by remember { mutableStateOf<ChatMessageEntity?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<Int?>(null) }
    var showSessionInfoDialog by remember { mutableStateOf<Int?>(null) }
    var showRenameDialogFor by remember { mutableStateOf<Int?>(null) }
    var showModelSelector by remember { mutableStateOf(false) }
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
        gesturesEnabled = true,
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
                                IconButton(
                                    onClick = {
                                        viewModel.loadSessionStats(session.id!!)
                                        showSessionInfoDialog = session.id
                                    }
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = "정보", modifier = Modifier.size(20.dp))
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
                        Box {
                            IconButton(onClick = { showModelSelector = true }) {
                                Icon(
                                    painter = painterResource(id = com.codewithfk.expensetracker.android.R.drawable.ic_gemini_color),
                                    contentDescription = "Select Model",
                                    modifier = Modifier
                                        .size(24.dp)
                                        .alpha(if (selectedModel == AiModelCatalog.smartModelName || selectedModel == AiModelCatalog.liteModelName) 1f else 0.75f),
                                    tint = if (selectedModel == AiModelCatalog.liteModelName)
                                        AgentPalette.metadataTextColor
                                    else AgentPalette.geminiOriginalIconTint
                                )
                            }
                            DropdownMenu(
                                expanded = showModelSelector,
                                onDismissRequest = { showModelSelector = false }
                            ) {
                                Text(
                                    text = "🤖 모델 선택",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )

                                listOf(
                                    AiModelCatalog.liteModelName to "Flash Lite (Fast)",
                                    AiModelCatalog.modelName to "Flash (Balanced)",
                                    AiModelCatalog.smartModelName to "Pro (Smart)"
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
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                Text(
                                    text = "📏 전송할 대화 길이",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )

                                listOf(
                                    20 to "20개 (Economy)",
                                    50 to "50개 (Balanced)",
                                    -1 to "전체 (Unlimited)"
                                ).forEach { (limit, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            viewModel.setHistoryLimit(limit)
                                            showModelSelector = false
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
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(id = com.codewithfk.expensetracker.android.R.drawable.ic_gemini_color),
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = AgentPalette.geminiOriginalIconTint
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
                            .fillMaxWidth()
                            .imePadding(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // [성능 최적화 4번: key + contentType]
                        // key: 각 메시지를 고유 id로 식별해, 리스트가 갱신돼도 Compose가
                        //   "어떤 아이템이 추가/이동/변경됐는지" 정확히 추적한다.
                        //   (id가 null인 임시 항목 방지용 폴백: timestamp 기반 음수 값)
                        // contentType: 사용자/어시스턴트 말풍선은 구조가 다른 컴포저블이므로
                        //   타입을 나눠 표시하면 스크롤 시 구성 슬롯을 더 효율적으로 재사용한다.
                        items(
                            messages,
                            key = { it.id ?: -it.timestamp.toInt() },
                            contentType = { if (it.role == ChatMessageEntity.ROLE_USER) "user" else "assistant" }
                        ) { message ->
                            if (message.content.isNotEmpty()) {
                                ChatBubble(
                                    message,
                                    isStreaming = isStreaming && message.id == streamingMessageId,
                                    onInfoClick = { showDetailDialog = it },
                                    onImageClick = { previewImageUrl = it },
                                    onAskGemini = { errorMessage ->
                                        viewModel.sendMessage(
                                            "오류가 발생한 Mermaid 코드를 오류가 안나게 수정해서 다시 보내줘:\n\n$errorMessage"
                                        )
                                    },
                                    onShowDetailsAtIndex = { index ->
                                        viewModel.showDetailsAtIndex(message, index)
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
                                    AiLoadingIndicator(isClockwiseRotation = true, modifier = Modifier.size(32.dp))
                                }
                            }
                        }
                    }
                }

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
                                modifier = Modifier.weight(1f),
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

    if (showSessionInfoDialog != null) {
        sessions.find { it.id == showSessionInfoDialog }?.let { session ->
            SessionInfoDialog(
                session = session,
                stats = sessionStats,
                onDelete = {
                    showSessionInfoDialog = null
                    showDeleteConfirmDialog = session.id
                },
                onRename = { showRenameDialogFor = session.id },
                onDismiss = { showSessionInfoDialog = null }
            )
        }
    }

    if (showRenameDialogFor != null) {
        sessions.find { it.id == showRenameDialogFor }?.let { session ->
            RenameSessionDialog(
                currentTitle = session.title,
                onConfirm = { newTitle ->
                    viewModel.renameSession(session.id!!, newTitle)
                    showRenameDialogFor = null
                },
                onDismiss = { showRenameDialogFor = null }
            )
        }
    }

    if (detailItems != null) {
        SimpleDetailListDialog(
            data = detailItems!!,
            onDismiss = { viewModel.dismissDetailItems() }
        )
    }

    if (pendingAction != null) {
        if (pendingAction!!.intent == "APP_ACTION") {
            LaunchedEffect(pendingAction) {
                val action = pendingAction!!.action
                when (action) {
                    "NAVIGATE_HOME" -> navController.navigate("/home") {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                    "NAVIGATE_CHARTS" -> navController.navigate("/analytics") {
                        launchSingleTop = true
                        restoreState = true
                    }
                    "NAVIGATE_TRANSACTIONS" -> navController.navigate("/all_transactions") {
                        launchSingleTop = true
                        restoreState = true
                    }
                    "EXPORT_EXCEL" -> {
                        android.widget.Toast.makeText(context, "엑셀 내보내기 기능을 준비 중입니다.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                viewModel.dismissAction()
            }
        } else {
            ActionConfirmDialog(
                request = pendingAction!!,
                onConfirm = { selectedItems -> viewModel.confirmAction(selectedItems) },
                onDismiss = { viewModel.dismissAction() }
            )
        }
    }
}

@Composable
fun ChatBubble(
    message: ChatMessageEntity,
    isStreaming: Boolean = false,
    onInfoClick: (ChatMessageEntity) -> Unit = {},
    onImageClick: (String) -> Unit = {},
    onAskGemini: (String) -> Unit = {},
    onShowDetailsAtIndex: (Int) -> Unit = {}
) {
    val isUser = message.role == ChatMessageEntity.ROLE_USER
    val darkTheme = isSystemInDarkTheme()
    val bubbleColor = when {
        isUser && darkTheme -> AgentPalette.darkUserMessageBubbleColor
        isUser -> AgentPalette.lightUserMessageBubbleColor
        darkTheme -> AgentPalette.darkAssistantMessageBubbleColor
        else -> AgentPalette.lightAssistantMessageBubbleColor
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.92f),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Surface(
                color = bubbleColor,
                shape = RoundedCornerShape(20.dp),
                tonalElevation = if (isUser) 0.dp else 1.dp
            ) {
                AiChatMarkdownView(
                    markdown = message.content,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    isUser = isUser,
                    isStreaming = isStreaming && !isUser,
                    onImageClick = onImageClick,
                    onAskGemini = onAskGemini,
                    onShowDetailsAtIndex = onShowDetailsAtIndex
                )
            }

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
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onInfoClick, modifier = Modifier.size(16.dp)) {
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
        Text(text = infoText, fontSize = 10.sp, color = AgentPalette.metadataTextColor.copy(alpha = 0.8f))
    }
}

fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("a h:mm", Locale.KOREA)
    return sdf.format(Date(timestamp))
}
