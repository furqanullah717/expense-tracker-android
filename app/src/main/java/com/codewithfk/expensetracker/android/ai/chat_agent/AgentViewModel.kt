package com.codewithfk.expensetracker.android.ai.chat_agent

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codewithfk.expensetracker.android.ai.chat_agent.data.ChatSessionEntity
import com.codewithfk.expensetracker.android.ai.core.AiGateway
import com.codewithfk.expensetracker.android.ai.core.AiImageAttachment
import com.codewithfk.expensetracker.android.ai.core.AiFileAttachment
import com.codewithfk.expensetracker.android.ai.core.ChatResponse
import com.codewithfk.expensetracker.android.data.dao.ExpenseDao
import com.codewithfk.expensetracker.android.data.model.ChatMessageEntity
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
import java.util.UUID
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AgentViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatRepository: ChatRepository,
    private val expenseDao: ExpenseDao,
    private val aiGateway: AiGateway
) : ViewModel() {

    private data class UploadedAttachment(val name: String, val url: String, val mimeType: String)

    private companion object {
        const val DEFAULT_MODEL = "gemini-2.5-flash-lite"
    }

    val sessions: StateFlow<List<ChatSessionEntity>> = chatRepository.getSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentSessionId = MutableStateFlow<Int?>(null)
    val currentSessionId: StateFlow<Int?> = _currentSessionId

    private val _selectedModel = MutableStateFlow(DEFAULT_MODEL)
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

    // 사용자 확인이 필요한 펜딩 액션 (데이터 삭제/수정 등)
    private val _pendingAction = MutableStateFlow<ChatResponse.ActionRequest?>(null)
    val pendingAction: StateFlow<ChatResponse.ActionRequest?> = _pendingAction

    private var chatJob: kotlinx.coroutines.Job? = null

    init {
        // 앱 실행 시 Firestore 동기화 및 가장 최근 세션 선택
        viewModelScope.launch {
            chatRepository.syncFromFirestore()
            var initialSessionRestored = false
            chatRepository.getSessions().collect { sessionList ->
                if (!initialSessionRestored && sessionList.isNotEmpty()) {
                    initialSessionRestored = true
                    sessionList.first().id?.let(::selectSession)
                }
            }
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
                _selectedModel.value = lastUsedModel ?: DEFAULT_MODEL
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
        _selectedModel.value = DEFAULT_MODEL
    }

    fun sendMessage(
        content: String,
        imageUris: List<Uri> = emptyList(),
        fileUris: List<Uri> = emptyList()
    ) {
        if (content.isBlank() && imageUris.isEmpty() && fileUris.isEmpty()) return
        require(imageUris.size + fileUris.size <= 10) { "이미지와 파일은 최대 10개까지 첨부할 수 있습니다." }

        val messageText = content.ifBlank { "이 이미지를 분석해 주세요." }

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

            var fullAssistantContent = ""
            
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
                aiGateway.chatStream(
                    currentMessages, 
                    history, 
                    _selectedModel.value, 
                    _selectedHistoryLimit.value,
                    images,
                    files
                ).collect { response ->
                    when (response) {
                        is ChatResponse.Chunk -> {
                            fullAssistantContent += response.text
                            chatRepository.updateMessageContent(assistantMessageId, fullAssistantContent)
                        }
                        is ChatResponse.Metadata -> {
                            val promptTokens = response.promptTokens ?: 0
                            val candidatesTokens = response.candidatesTokens ?: 0
                            val totalTokens = response.totalTokens ?: (promptTokens + candidatesTokens)
                            
                            val costUsd = Utils.calculateCostUsd(promptTokens, candidatesTokens)
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
                                appCheckStatus = "Verified",
                                deviceModel = Utils.getDeviceModel(),
                                osVersion = Utils.getOsVersion()
                            )
                        }
                        is ChatResponse.ActionRequest -> {
                            _pendingAction.value = response
                        }
                    }
                }
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
                        addAssistantMessage("수정 기능은 현재 구현 중입니다.")
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
                _selectedModel.value = DEFAULT_MODEL
            }
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            chatRepository.clearChat()
            _currentSessionId.value = null
            _selectedModel.value = DEFAULT_MODEL
        }
    }
}
