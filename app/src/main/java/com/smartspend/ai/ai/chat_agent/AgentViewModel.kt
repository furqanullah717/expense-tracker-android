package com.smartspend.ai.ai.chat_agent

import android.content.Context
import android.net.Uri
import android.util.Log
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartspend.ai.ai.gateway.AiGateway
import com.smartspend.ai.ai.gateway.AiImageAttachment
import com.smartspend.ai.ai.gateway.AiFileAttachment
import com.smartspend.ai.ai.gateway.ChatResponse
import com.smartspend.ai.ai.gateway.AiModelCatalog
import com.smartspend.ai.data.dao.ExpenseDao
import com.smartspend.ai.data.model.ChatMessageEntity
import com.smartspend.ai.data.model.ChatSessionEntity
import com.smartspend.ai.data.repository.ChatRepository
import com.smartspend.ai.utils.Utils
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
 * [?±ëŠ¥ ìµœì ??2ë²? ì²?¬ ë°°ì¹˜ ì²˜ë¦¬]
 * ?¤íŠ¸ë¦¬ë° ì¤?AI ?‘ë‹µ?€ ?„ì£¼ ?˜ê²Œ ?˜ë‰œ ì²?¬ë¡??˜ì‹­~?˜ë°± ???„ì°©?œë‹¤.
 * ì²?¬ë§ˆë‹¤ Room DB???°ë©´ ??Flowê°€ ?¤ì‹œ ë°©ì¶œ ??LazyColumn ?„ì²´ ë¦¬ì»´?¬ì??˜ì´
 * ì´ˆë‹¹ ?˜íšŒ ë°˜ë³µ?˜ì–´ UIê°€ ë²„ë²…ê±°ë¦°??
 * ??ê°„ê²©(250ms) ?™ì•ˆ ?„ì°©??ì²?¬??ë©”ëª¨ë¦?fullAssistantContent)?ë§Œ ?„ì ?˜ê³ ,
 * ?¼ì • ?œê°„??ì§€??????ë²ˆë§Œ DB??ë°˜ì˜?´ì„œ ë¦¬ì»´?¬ì???ë¹ˆë„ë¥??˜ì‹­ ë°?ì¤„ì¸??
 * ë¶€?‘ìš©: ?”ë©´???¤íŠ¸ë¦¬ë° ?ìŠ¤?¸ê? ìµœë? 250ms ??²Œ ê°±ì‹ ?œë‹¤(ì²´ê° ê±°ì˜ ?†ìŒ).
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

    private val _selectedHistoryLimit = MutableStateFlow(20) // ê¸°ë³¸ê°?20
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
        // ???¤í–‰ ??Firestore ?™ê¸°?”ë§Œ ?˜í–‰?˜ê³  ìµœê·¼ ?¸ì…˜???ë™ ? íƒ?˜ì? ?ŠëŠ”??
        // ???„ë¡œ?¸ìŠ¤ ?œì‘ ??ì²?ì§„ì… ?œì—??ë¹????€???íƒœë¡??œì‘?˜ë©°,
        //   ?¬ìš©?ê? ì²?ë©”ì‹œì§€ë¥?ë³´ë‚´???œì ?ë§Œ ?¸ì…˜??DB/Firestore???€?¥ëœ??
        // ViewModel???˜ë‹¨ë°?saveState/restoreState ?•ë¶„?????´ë™ ê°?? ì??˜ë?ë¡?
        // ?¤ë¥¸ ?”ë©´??ê°”ë‹¤ ?Œì•„?¤ë©´ ë§ˆì?ë§??€???”ë©´??ê·¸ë?ë¡?ë³´ì¡´?œë‹¤.
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
        _currentSessionId.value = null // ?ˆë¡œ??ì±„íŒ… ì¤€ë¹??íƒœ
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
        require(imageUris.size + fileUris.size <= 10) { "?´ë?ì§€?€ ?Œì¼?€ ìµœë? 10ê°œê¹Œì§€ ì²¨ë??????ˆìŠµ?ˆë‹¤." }

        val messageText = content.ifBlank { "???´ë?ì§€ë¥?ë¶„ì„??ì£¼ì„¸??" }
        val cleanInput = messageText.trim().lowercase()

        // --- ëª…ë ¹???¸í„°?‰íŠ¸ (ëª¨ë“œ ?„í™˜) ---
        if (cleanInput == "/debug") {
            _isDebugMode.value = true
            _chatInput.value = ""
            viewModelScope.launch {
                val sId = _currentSessionId.value ?: chatRepository.insertSession(ChatSessionEntity(title = "Debug Mode")).toInt().also { _currentSessionId.value = it }
                addAssistantMessage("?› ï¸?**ê°œë°œ??ëª¨ë“œ ?œì„±??*\nì§€ê¸ˆë???ëª¨ë“  ?¸í…??ë¶„ì„??ê±´ë„ˆ?°ê³  Gemini?€ ì§ì ‘ ?ìœ ë¡?²Œ ?€?”í•©?ˆë‹¤.\nì¢…ë£Œ?˜ë ¤ë©?`/exit`ë¥??…ë ¥?˜ì„¸??")
            }
            return
        }

        if (cleanInput == "/exit" && _isDebugMode.value) {
            _isDebugMode.value = false
            _chatInput.value = ""
            viewModelScope.launch {
                val sId = _currentSessionId.value
                if (sId != null) addAssistantMessage("?‘‹ **ê°œë°œ??ëª¨ë“œ ì¢…ë£Œ**\n?¼ë°˜ ê°€ê³„ë? ë¹„ì„œ ëª¨ë“œë¡??Œì•„ê°‘ë‹ˆ??")
            }
            return
        }
        // ------------------------------

        _chatInput.value = "" // ë©”ì‹œì§€ ?„ì†¡ ?œì‘ ???…ë ¥ì°?ë¹„ìš°ê¸?        chatJob?.cancel()
        chatJob = viewModelScope.launch {
            var sessionId = _currentSessionId.value
            val isNewSession = sessionId == null

            if (isNewSession) {
                // ???¸ì…˜ ?ì„± (?œëª©?€ ì²?ì§ˆë¬¸???¼ë?ë¡?
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

            // AI ?µë???ë¹?ë©”ì‹œì§€ ë¨¼ì? ?½ì…
            val assistantMessageId = chatRepository.insertMessage(
                ChatMessageEntity(
                    sessionId = sessionId, 
                    content = "", 
                    role = ChatMessageEntity.ROLE_ASSISTANT
                )
            ).toInt()

            var fullAssistantContent = ""      // ì§€ê¸ˆê¹Œì§€ ?˜ì‹ ???µë? ?„ì²´ (?„ì  ë²„í¼)
            var lastContentSyncAt = 0L         // ë§ˆì?ë§‰ìœ¼ë¡?DB??ë°˜ì˜???œê° (?¤ë¡œ?€??
            var streamCompleted = false        // collectê°€ ?ˆì™¸ ?†ì´ ?ë‚¬?”ì? (ìµœì¢… ?€???ë‹¨??
            val tempDetailsList = mutableListOf<ChatResponse.ShowDetails>()
            
            try {
                fun readAttachment(uri: Uri): AiImageAttachment {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("ì²¨ë? ?Œì¼???½ì„ ???†ìŠµ?ˆë‹¤.")
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
                    "ì²¨ë? ?´ë?ì§€???„ì²´ ?¬ê¸°??20MB ?´í•˜?¬ì•¼ ?©ë‹ˆ??"
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
                            // [?±ëŠ¥ ìµœì ??2ë²? ì²?¬ë§ˆë‹¤ DB???°ì? ?Šê³ , ë§ˆì?ë§?ë°˜ì˜?ì„œ
                            // CONTENT_SYNC_INTERVAL_MS ?´ìƒ ì§€?¬ì„ ?Œë§Œ ?´ë‹¤.
                            // (DB ?°ê¸° ??Flow ?¬ë°©ì¶???ë¦¬ì»´?¬ì????°ì‡„ë¥?ì¤„ì´???µì‹¬)
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
                // collectê°€ ?ê¹Œì§€ ?•ìƒ ?„ë£Œ?ˆìŒ???œì‹œ.
                // true?¬ì•¼ë§?finally?ì„œ ìµœì¢… ?´ìš© ?€?¥ì„ ?˜í–‰?œë‹¤.
                streamCompleted = true
            } catch (e: kotlinx.coroutines.CancellationException) {
                // ì¤‘ì???ê²½ìš° ì²˜ë¦¬ (? íƒ ?¬í•­: 'ì¤‘ì??? ?œì‹œ ??
                if (fullAssistantContent.isEmpty()) {
                    chatRepository.updateMessageContent(assistantMessageId, "?µë???ì¤‘ì??˜ì—ˆ?µë‹ˆ??")
                }
            } catch (e: Exception) {
                chatRepository.updateMessageContent(
                    assistantMessageId,
                    "?´ë?ì§€ ?„ì†¡???¤íŒ¨?ˆìŠµ?ˆë‹¤: ${e.message ?: "?????†ëŠ” ?¤ë¥˜"}"
                )
            } finally {
                // [?±ëŠ¥ ìµœì ??2ë²ˆì˜ ë§ˆë¬´ë¦?
                // ?¤ë¡œ?€ ?Œë¬¸??ë§ˆì?ë§?ì²?¬ ëª?ê°œëŠ” ?„ì§ DB??ë¯¸ë°˜???íƒœ?????ˆë‹¤.
                // ?•ìƒ ì¢…ë£Œ(streamCompleted == true)???Œë§Œ ìµœì¢… ?„ì²´ ?´ìš©????ë²????€?¥í•´
                // ?˜ë¦° ?µë???ë°©ì??œë‹¤. ?¤ë¥˜/ì¤‘ì? ê²½ë¡œ(catch?ì„œ ?ˆë‚´ ë¬¸êµ¬ë¥???ê²½ìš°)??                // ê·?ë¬¸êµ¬ë¥???–´?°ì? ?Šë„ë¡?ê±´ë“œë¦¬ì? ?ŠëŠ”??
                if (streamCompleted && fullAssistantContent.isNotEmpty()) {
                    chatRepository.updateMessageContent(assistantMessageId, fullAssistantContent)
                }

                // ?¤íŠ¸ë¦¬ë° ?„ë£Œ ???˜ì§‘???ì„¸ ?´ì—­?¤ì„ JSON?¼ë¡œ ë³€?˜í•˜???€??                if (tempDetailsList.isNotEmpty()) {
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

    fun confirmAction(selectedItems: List<com.smartspend.ai.data.model.ExpenseEntity>) {
        val actionRequest = _pendingAction.value ?: return
        viewModelScope.launch {
            try {
                when (actionRequest.action) {
                    "DELETE" -> {
                        selectedItems.forEach { expenseDao.deleteExpense(it) }
                        addAssistantMessage("? íƒ??**${selectedItems.size}ê±?*???´ì—­???? œ?ˆìŠµ?ˆë‹¤.")
                    }
                    "UPDATE" -> {
                        selectedItems.forEach { expenseDao.updateExpense(it) }
                        addAssistantMessage("? íƒ??**${selectedItems.size}ê±?*???´ì—­???˜ì •?ˆìŠµ?ˆë‹¤.")
                    }
                    "INSERT" -> {
                        selectedItems.forEach { expenseDao.insertExpense(it) }
                        addAssistantMessage("??ª©??ì¶”ê??ˆìŠµ?ˆë‹¤.")
                    }
                }
            } catch (e: Exception) {
                addAssistantMessage("?‘ì—… ì²˜ë¦¬ ì¤??¤ë¥˜ê°€ ë°œìƒ?ˆìŠµ?ˆë‹¤: ${e.message}")
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
            ?: error("ì²¨ë? ?Œì¼???½ì„ ???†ìŠµ?ˆë‹¤.")
        require(bytes.size <= 20 * 1024 * 1024) { "ì²¨ë? ?Œì¼?€ 20MB ?´í•˜ë§??…ë¡œ?œí•  ???ˆìŠµ?ˆë‹¤." }
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
        throw lastError ?: IllegalStateException("Storage ?…ë¡œ?œì— ?¤íŒ¨?ˆìŠµ?ˆë‹¤.")
    }

    private fun attachmentName(uri: Uri): String = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "ì²¨ë? ?Œì¼"

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
