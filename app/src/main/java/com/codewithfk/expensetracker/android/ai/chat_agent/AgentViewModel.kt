package com.codewithfk.expensetracker.android.ai.chat_agent

import android.content.Context
import android.net.Uri
import android.util.Log
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codewithfk.expensetracker.android.ai.gateway.AiGateway
import com.codewithfk.expensetracker.android.ai.gateway.AiImageAttachment
import com.codewithfk.expensetracker.android.ai.gateway.AiFileAttachment
import com.codewithfk.expensetracker.android.ai.gateway.ChatResponse
import com.codewithfk.expensetracker.android.ai.gateway.AiModelCatalog
import com.codewithfk.expensetracker.android.data.dao.ExpenseDao
import com.codewithfk.expensetracker.android.data.model.ChatMessageEntity
import com.codewithfk.expensetracker.android.data.model.ChatSessionEntity
import com.codewithfk.expensetracker.android.data.repository.ChatRepository
import com.codewithfk.expensetracker.android.utils.Utils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.delay
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.auth.FirebaseAuth
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject

/**
 * [성능 최적화 2번: 청크 배치 처리]
 * 스트리밍 중 AI 응답은 아주 잘게 나뉜 청크로 수십~수백 회 도착한다.
 * 청크마다 Room DB에 쓰면 → Flow가 다시 방출 → LazyColumn 전체 리컴포지션이
 * 초당 수회 반복되어 UI가 버벅거린다.
 * 이 간격(250ms) 동안 도착한 청크는 메모리(fullAssistantContent)에만 누적하고,
 * 일정 시간이 지난 뒤 한 번만 DB에 반영해서 리컴포지션 빈도를 수십 배 줄인다.
 * 부작용: 화면의 스트리밍 텍스트가 최대 250ms 늦게 갱신된다(체감 거의 없음).
 */
private const val CONTENT_SYNC_INTERVAL_MS = 250L

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AgentViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatRepository: ChatRepository,
    private val expenseDao: ExpenseDao,
    private val aiGateway: AiGateway
) : ViewModel() {

    private data class UploadedAttachment(val name: String, val url: String, val mimeType: String)

    val sessions: StateFlow<List<ChatSessionEntity>> = chatRepository.getSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentSessionId = MutableStateFlow<Int?>(null)
    val currentSessionId: StateFlow<Int?> = _currentSessionId

    private val _selectedModel = MutableStateFlow(AiModelCatalog.liteModelName)
    val selectedModel: StateFlow<String> = _selectedModel

    private val _selectedHistoryLimit = MutableStateFlow(20) // 기본값 20
    val selectedHistoryLimit: StateFlow<Int> = _selectedHistoryLimit

    val messages: StateFlow<List<ChatMessageEntity>> = _currentSessionId.flatMapLatest { sessionId ->
        if (sessionId == null) flowOf(emptyList())
        else chatRepository.getMessages(sessionId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sessionTotalTokens: StateFlow<Int> = messages.flatMapLatest { messageList ->
        flowOf(messageList.sumOf { it.totalTokens ?: 0 })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val sessionTotalCost: StateFlow<Double> = messages.flatMapLatest { messageList ->
        flowOf(messageList.sumOf { it.estimatedCostKrw ?: 0.0 })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming

    private val _chatInput = MutableStateFlow("")
    val chatInput: StateFlow<String> = _chatInput

    private val _pendingAction = MutableStateFlow<ChatResponse.ActionRequest?>(null)
    val pendingAction: StateFlow<ChatResponse.ActionRequest?> = _pendingAction

    private val _detailItems = MutableStateFlow<ChatResponse.ShowDetails?>(null)
    val detailItems: StateFlow<ChatResponse.ShowDetails?> = _detailItems

    private val _isDebugMode = MutableStateFlow(false)
    val isDebugMode: StateFlow<Boolean> = _isDebugMode

    private var chatJob: kotlinx.coroutines.Job? = null

    init {
        // 앱 실행 시 Firestore 동기화만 수행하고 최근 세션을 자동 선택하지 않는다.
        // → 프로세스 시작 후 첫 진입 시에는 빈 새 대화 상태로 시작하며,
        //   사용자가 첫 메시지를 보내는 시점에만 세션이 DB/Firestore에 저장된다.
        // ViewModel이 하단바 saveState/restoreState 덕분에 탭 이동 간 유지되므로,
        // 다른 화면에 갔다 돌아오면 마지막 대화 화면이 그대로 보존된다.
        viewModelScope.launch {
            chatRepository.syncFromFirestore()
        }
    }

    fun selectSession(sessionId: Int) {
        _currentSessionId.value = sessionId
        viewModelScope.launch {
            val lastUsedModel = chatRepository.getMessages(sessionId).first()
                .lastOrNull {
                    it.role == ChatMessageEntity.ROLE_ASSISTANT && !it.modelName.isNullOrBlank()
                }
                ?.modelName
            if (_currentSessionId.value == sessionId) {
                _selectedModel.value = lastUsedModel ?: AiModelCatalog.liteModelName
            }
        }
    }

    fun setModel(model: String) {
        _selectedModel.value = model
    }

    fun setHistoryLimit(limit: Int) {
        _selectedHistoryLimit.value = limit
    }

    fun onChatInputChanged(input: String) {
        _chatInput.value = input
    }

    fun createNewChat() {
        _currentSessionId.value = null // 새로운 채팅 준비 상태
        _selectedModel.value = AiModelCatalog.liteModelName
    }

    data class SessionStats(
        val assistantMessageCount: Int,
        val avgResponseTimeMs: Long?,
        val totalTokens: Int,
        val totalCostKrw: Double
    )

    private val _sessionStats = MutableStateFlow<SessionStats?>(null)
    val sessionStats: StateFlow<SessionStats?> = _sessionStats

    fun loadSessionStats(sessionId: Int) {
        _sessionStats.value = null
        viewModelScope.launch {
            val msgs = chatRepository.getMessages(sessionId).first()
            val assistantMsgs = msgs.filter { it.role == ChatMessageEntity.ROLE_ASSISTANT }
            val responseTimes = assistantMsgs.mapNotNull { it.responseTimeMs }
            _sessionStats.value = SessionStats(
                assistantMessageCount = assistantMsgs.size,
                avgResponseTimeMs = if (responseTimes.isEmpty()) null else responseTimes.average().toLong(),
                totalTokens = msgs.sumOf { it.totalTokens ?: 0 },
                totalCostKrw = msgs.sumOf { it.estimatedCostKrw ?: 0.0 }
            )
        }
    }

    fun renameSession(sessionId: Int, newTitle: String) {
        viewModelScope.launch {
            chatRepository.renameSession(sessionId, newTitle.trim())
        }
    }

    fun showDetailsAtIndex(message: ChatMessageEntity, index: Int) {
        val jsonStr = message.detailsJson
        Log.d("AgentViewModel", "showDetailsAtIndex: index=$index, jsonStr=${jsonStr?.take(50)}...")
        if (jsonStr.isNullOrBlank()) {
            Log.w("AgentViewModel", "showDetailsAtIndex: detailsJson is null or empty")
            return
        }
        try {
            val allDetails = Json.decodeFromString<List<ChatResponse.ShowDetails>>(jsonStr)
            Log.d("AgentViewModel", "showDetailsAtIndex: allDetails size=${allDetails.size}")
            if (index in allDetails.indices) {
                _detailItems.value = allDetails[index]
            } else {
                Log.w("AgentViewModel", "showDetailsAtIndex: index out of bounds")
            }
        } catch (e: Exception) {
            Log.e("AgentViewModel", "Failed to parse detailsJson: ${e.message}", e)
        }
    }

    fun dismissDetailItems() {
        _detailItems.value = null
    }

    fun sendMessage(
        content: String,
        imageUris: List<Uri> = emptyList(),
        fileUris: List<Uri> = emptyList()
    ) {
        if (content.isBlank() && imageUris.isEmpty() && fileUris.isEmpty()) return
        require(imageUris.size + fileUris.size <= 10) { "이미지와 파일은 최대 10개까지 첨부할 수 있습니다." }

        val messageText = content.ifBlank { "이 이미지를 분석해 주세요." }
        val cleanInput = messageText.trim().lowercase()

        // --- 명령어 인터셉트 (모드 전환) ---
        if (cleanInput == "/debug") {
            _isDebugMode.value = true
            _chatInput.value = ""
            viewModelScope.launch {
                val sId = _currentSessionId.value ?: chatRepository.insertSession(ChatSessionEntity(title = "Debug Mode")).toInt().also { _currentSessionId.value = it }
                addAssistantMessage("🛠️ **개발자 모드 활성화**\n지금부터 모든 인텐트 분석을 건너뛰고 Gemini와 직접 자유롭게 대화합니다.\n종료하려면 `/exit`를 입력하세요.")
            }
            return
        }

        if (cleanInput == "/exit" && _isDebugMode.value) {
            _isDebugMode.value = false
            _chatInput.value = ""
            viewModelScope.launch {
                val sId = _currentSessionId.value
                if (sId != null) addAssistantMessage("👋 **개발자 모드 종료**\n일반 가계부 비서 모드로 돌아갑니다.")
            }
            return
        }
        // ------------------------------

        _chatInput.value = "" // 메시지 전송 시작 시 입력창 비우기
        chatJob?.cancel()
        chatJob = viewModelScope.launch {
            var sessionId = _currentSessionId.value
            val isNewSession = sessionId == null

            if (isNewSession) {
                // 새 세션 생성 (제목은 첫 질문의 일부로)
                val title = if (messageText.length > 20) messageText.take(20) + "..." else messageText
                val newSessionId = chatRepository.insertSession(ChatSessionEntity(title = title)).toInt()
                _currentSessionId.value = newSessionId
                sessionId = newSessionId
            }

            val attachmentUris = imageUris + fileUris
            val localAttachments = attachmentUris.map { uri -> attachmentName(uri) to uri.toString() }
            val attachmentMarkdown = localAttachments.joinToString("\n") { (name, uri) ->
                "[${name.replace("[", "(").replace("]", ")")}]($uri)"
            }
            val storageUpload = if (attachmentUris.isNotEmpty()) {
                viewModelScope.async {
                    runCatching { attachmentUris.map { uploadAttachment(it) } }
                }
            } else null

            val userMessage = ChatMessageEntity(
                sessionId = sessionId!!, 
                content = buildString {
                    if (attachmentMarkdown.isNotBlank()) append(attachmentMarkdown).append("\n\n")
                    append(messageText)
                },
                role = ChatMessageEntity.ROLE_USER
            )
            val userMessageId = chatRepository.insertMessage(userMessage).toInt()
            storageUpload?.let { upload ->
                viewModelScope.launch {
                    upload.await().onSuccess { uploaded ->
                        val remoteMarkdown = uploaded.joinToString("\n") {
                            "[${it.name.replace("[", "(").replace("]", ")")}](${it.url})"
                        }
                        chatRepository.updateMessageContentAndSync(
                            userMessageId,
                            buildString {
                                if (remoteMarkdown.isNotBlank()) append(remoteMarkdown).append("\n\n")
                                append(messageText)
                            }
                        )
                    }
                }
            }
            chatRepository.updateSessionLastTime(sessionId, System.currentTimeMillis())

            _isLoading.value = true
            _isStreaming.value = true
            
            // Get all expenses for context
            val history = expenseDao.getAllExpense().first()
            
            // Get all messages for context (including the one just added)
            val currentMessages = chatRepository.getMessages(sessionId).first()

            // AI 답변용 빈 메시지 먼저 삽입
            val assistantMessageId = chatRepository.insertMessage(
                ChatMessageEntity(
                    sessionId = sessionId, 
                    content = "", 
                    role = ChatMessageEntity.ROLE_ASSISTANT
                )
            ).toInt()

            var fullAssistantContent = ""      // 지금까지 수신한 답변 전체 (누적 버퍼)
            var lastContentSyncAt = 0L         // 마지막으로 DB에 반영한 시각 (스로틀용)
            var streamCompleted = false        // collect가 예외 없이 끝났는지 (최종 저장 판단용)
            val tempDetailsList = mutableListOf<ChatResponse.ShowDetails>()
            
            try {
                fun readAttachment(uri: Uri): AiImageAttachment {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("첨부 파일을 읽을 수 없습니다.")
                    return AiImageAttachment(
                        bytes = bytes,
                        mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                    )
                }
                val images = imageUris.map(::readAttachment)
                val files = fileUris.map { uri ->
                    readAttachment(uri).let { AiFileAttachment(it.bytes, it.mimeType) }
                }
                require((images + files).sumOf { it.bytes.size } <= 20 * 1024 * 1024) {
                    "첨부 이미지의 전체 크기는 20MB 이하여야 합니다."
                }
                val flow = if (_isDebugMode.value) {
                    aiGateway.rawChatStream(
                        currentMessages, 
                        _selectedModel.value, 
                        _selectedHistoryLimit.value,
                        images
                    )
                } else {
                    aiGateway.chatStream(
                        currentMessages, 
                        history, 
                        _selectedModel.value, 
                        _selectedHistoryLimit.value,
                        images,
                        files
                    )
                }

                flow.collect { response ->
                    when (response) {
                        is ChatResponse.Chunk -> {
                            fullAssistantContent += response.text
                            // [성능 최적화 2번] 청크마다 DB에 쓰지 않고, 마지막 반영에서
                            // CONTENT_SYNC_INTERVAL_MS 이상 지났을 때만 쓴다.
                            // (DB 쓰기 → Flow 재방출 → 리컴포지션 연쇄를 줄이는 핵심)
                            val now = System.currentTimeMillis()
                            if (now - lastContentSyncAt >= CONTENT_SYNC_INTERVAL_MS) {
                                lastContentSyncAt = now
                                chatRepository.updateMessageContent(assistantMessageId, fullAssistantContent)
                            }
                        }
                        is ChatResponse.Metadata -> {
                            val promptTokens = response.promptTokens ?: 0
                            val candidatesTokens = response.candidatesTokens ?: 0
                            val totalTokens = response.totalTokens ?: (promptTokens + candidatesTokens)
                            val thoughtsTokens = response.thoughtsTokens ?: 0
                            
                            val costUsd = Utils.calculateCostUsd(
                                modelName = response.modelName ?: _selectedModel.value,
                                promptTokens = promptTokens,
                                candidatesTokens = candidatesTokens,
                                thoughtsTokens = thoughtsTokens
                            )
                            val costKrw = Utils.calculateCostKrw(costUsd)
                            
                            chatRepository.updateMessageMetadata(
                                messageId = assistantMessageId,
                                promptTokens = promptTokens,
                                candidatesTokens = candidatesTokens,
                                totalTokens = totalTokens,
                                responseTimeMs = response.responseTimeMs,
                                estimatedCostUsd = costUsd,
                                estimatedCostKrw = costKrw,
                                modelName = response.modelName,
                                agentVersion = response.agentVersion,
                                provider = response.provider,
                                appCheckStatus = response.inputHistoryCount?.toString() ?: "0",
                                deviceModel = Utils.getDeviceModel(),
                                osVersion = Utils.getOsVersion(),
                                firstPassPrompt = response.firstPassPrompt,
                                firstPassResponse = response.firstPassResponse,
                                secondPassPrompt = response.secondPassPrompt,
                                secondPassResponse = response.secondPassResponse,
                                thoughtsTokens = thoughtsTokens
                            )
                        }
                        is ChatResponse.ActionRequest -> {
                            _pendingAction.value = response
                        }
                        is ChatResponse.ShowDetails -> {
                            tempDetailsList.add(response)
                        }
                    }
                }
                // collect가 끝까지 정상 완료했음을 표시.
                // true여야만 finally에서 최종 내용 저장을 수행한다.
                streamCompleted = true
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 중지된 경우 처리 (선택 사항: '중지됨' 표시 등)
                if (fullAssistantContent.isEmpty()) {
                    chatRepository.updateMessageContent(assistantMessageId, "답변이 중지되었습니다.")
                }
            } catch (e: Exception) {
                chatRepository.updateMessageContent(
                    assistantMessageId,
                    "이미지 전송에 실패했습니다: ${e.message ?: "알 수 없는 오류"}"
                )
            } finally {
                // [성능 최적화 2번의 마무리]
                // 스로틀 때문에 마지막 청크 몇 개는 아직 DB에 미반영 상태일 수 있다.
                // 정상 종료(streamCompleted == true)일 때만 최종 전체 내용을 한 번 더 저장해
                // 잘린 답변을 방지한다. 오류/중지 경로(catch에서 안내 문구를 쓴 경우)는
                // 그 문구를 덮어쓰지 않도록 건드리지 않는다.
                if (streamCompleted && fullAssistantContent.isNotEmpty()) {
                    chatRepository.updateMessageContent(assistantMessageId, fullAssistantContent)
                }

                // 스트리밍 완료 후 수집된 상세 내역들을 JSON으로 변환하여 저장
                if (tempDetailsList.isNotEmpty()) {
                    try {
                        val jsonString = Json.encodeToString(tempDetailsList)
                        Log.d("AgentViewModel", "Saving detailsJson: count=${tempDetailsList.size}, length=${jsonString.length}")
                        chatRepository.updateMessageDetails(assistantMessageId, jsonString)
                    } catch (e: Exception) {
                        Log.e("AgentViewModel", "CRITICAL: Failed to encode details list: ${e.message}", e)
                    }
                } else {
                    Log.d("AgentViewModel", "No details to save for message $assistantMessageId")
                }

                chatRepository.updateSessionLastTime(sessionId, System.currentTimeMillis())
                _isLoading.value = false
                _isStreaming.value = false
                chatJob = null
            }
        }
    }

    fun confirmAction(selectedItems: List<com.codewithfk.expensetracker.android.data.model.ExpenseEntity>) {
        val actionRequest = _pendingAction.value ?: return
        viewModelScope.launch {
            try {
                when (actionRequest.action) {
                    "DELETE" -> {
                        selectedItems.forEach { expenseDao.deleteExpense(it) }
                        addAssistantMessage("선택한 **${selectedItems.size}건**의 내역을 삭제했습니다.")
                    }
                    "UPDATE" -> {
                        selectedItems.forEach { expenseDao.updateExpense(it) }
                        addAssistantMessage("선택한 **${selectedItems.size}건**의 내역을 수정했습니다.")
                    }
                    "INSERT" -> {
                        selectedItems.forEach { expenseDao.insertExpense(it) }
                        addAssistantMessage("항목을 추가했습니다.")
                    }
                }
            } catch (e: Exception) {
                addAssistantMessage("작업 처리 중 오류가 발생했습니다: ${e.message}")
            } finally {
                _pendingAction.value = null
            }
        }
    }

    private suspend fun addAssistantMessage(content: String) {
        val sessionId = _currentSessionId.value ?: return
        chatRepository.insertMessage(
            ChatMessageEntity(
                sessionId = sessionId,
                content = content,
                role = ChatMessageEntity.ROLE_ASSISTANT
            )
        )
        chatRepository.updateSessionLastTime(sessionId, System.currentTimeMillis())
    }

    fun dismissAction() {
        _pendingAction.value = null
    }

    private suspend fun uploadAttachment(uri: Uri): UploadedAttachment {
        val name = attachmentName(uri)
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("첨부 파일을 읽을 수 없습니다.")
        require(bytes.size <= 20 * 1024 * 1024) { "첨부 파일은 20MB 이하만 업로드할 수 있습니다." }
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
        val reference = FirebaseStorage.getInstance(
            "gs://smartspend-ai-cbf59.firebasestorage.app"
        ).reference
            .child("users/$uid/chat_attachments/${UUID.randomUUID()}_$name")
        val metadata = StorageMetadata.Builder().setContentType(mimeType).build()
        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                reference.putBytes(bytes, metadata).await()
                val downloadUrl = reference.downloadUrl.await().toString()
                return UploadedAttachment(name, downloadUrl, mimeType)
            } catch (error: Exception) {
                lastError = error
                if (attempt < 2) delay(500L * (attempt + 1))
            }
        }
        throw lastError ?: IllegalStateException("Storage 업로드에 실패했습니다.")
    }

    private fun attachmentName(uri: Uri): String = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "첨부 파일"

    fun stopGeneration() {
        chatJob?.cancel()
        chatJob = null
        _isLoading.value = false
        _isStreaming.value = false
    }

    fun deleteSession(sessionId: Int) {
        viewModelScope.launch {
            chatRepository.deleteSession(sessionId)
            if (_currentSessionId.value == sessionId) {
                _currentSessionId.value = null
                _selectedModel.value = AiModelCatalog.liteModelName
            }
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            chatRepository.clearChat()
            _currentSessionId.value = null
            _selectedModel.value = AiModelCatalog.liteModelName
        }
    }
}
