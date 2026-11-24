package com.example.ui.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.TammieCipher
import com.example.data.firebase.TammieRepository
import com.example.data.model.AttachmentUploadState
import com.example.data.model.CallSession
import com.example.data.model.CallStatus
import com.example.data.model.CallType
import com.example.data.model.Conversation
import com.example.data.model.ConversationType
import com.example.data.model.ConversationUi
import com.example.data.model.MessageType
import com.example.data.model.MessageUi
import com.example.data.model.StagedAttachment
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.safeEnumValueOf
import com.example.data.prefs.PreferencesRepository
import com.example.data.prefs.UserPreferences
import com.example.data.storage.AttachmentTransferManager
import com.example.data.storage.UploadOutcome
import com.example.i18n.AppLanguage
import com.example.notifications.TammieNotificationHelper
import com.example.ui.theme.ThemeMode
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {
    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UserPreferences()
        )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { preferencesRepository.setLanguage(language) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setNotificationsEnabled(enabled) }
    }
}

class MainSessionViewModel(
    private val repository: TammieRepository,
    private val notificationHelper: TammieNotificationHelper,
    private val attachmentManager: AttachmentTransferManager,
    private val currentUserId: String
) : ViewModel() {

    private var lastKnownMessageKeys = mutableSetOf<String>()
    private var initialConvLoaded = false
    private var lastNotifiedCallId: String? = null

    private val _selectedConversationId = MutableStateFlow<String?>(null)
    val selectedConversationId: StateFlow<String?> = _selectedConversationId.asStateFlow()

    private val _activeCallId = MutableStateFlow<String?>(null)
    val activeCallId: StateFlow<String?> = _activeCallId.asStateFlow()

    private val _statusEvent = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val statusEvent = _statusEvent.asSharedFlow()

    val directoryUsers: StateFlow<UiState<List<UserProfile>>> = repository.observeDirectoryUsers()
        .map<List<UserProfile>, UiState<List<UserProfile>>> { users ->
            UiState.Success(users.sortedBy { it.displayName.lowercase() })
        }
        .catch { error ->
            emit(UiState.Error(unwrapFirestoreError(error, "Failed to load directory")))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val myProfile: StateFlow<UserProfile?> = directoryUsers
        .map { state ->
            (state as? UiState.Success)?.data?.firstOrNull { it.uid == currentUserId }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    val conversationsState: StateFlow<UiState<List<ConversationUi>>> = combine(
        repository.observeConversations(currentUserId),
        directoryUsers
    ) { conversations, usersState ->
        val usersMap = (usersState as? UiState.Success)?.data?.associateBy { it.uid }.orEmpty()
        conversations
            .sortedByDescending { it.lastMessageAt?.toDate()?.time ?: it.createdAt?.toDate()?.time ?: 0L }
            .map { conv ->
                val convType = safeEnumValueOf(conv.type, ConversationType.DIRECT)
                val peerUid = conv.memberUids.firstOrNull { it != currentUserId }
                val peerProfile = peerUid?.let { usersMap[it] }
                val displayTitle = when (convType) {
                    ConversationType.GROUP -> conv.title.ifBlank { "Group" }
                    ConversationType.DIRECT -> {
                        peerProfile?.displayName
                            ?: peerUid?.let { conv.memberNames[it] }
                            ?: "Chat"
                    }
                }
                val decryptedPreview = if (conv.lastMessageCiphertext.isNotBlank() && conv.lastMessageIv.isNotBlank()) {
                    TammieCipher.decrypt(
                        conv.lastMessageCiphertext,
                        conv.lastMessageIv,
                        conv.id,
                        conv.encryptionSalt
                    )
                } else {
                    ""
                }
                ConversationUi(
                    conversation = conv,
                    conversationType = convType,
                    displayTitle = displayTitle,
                    peerProfile = peerProfile,
                    decryptedLastMessage = decryptedPreview,
                    lastMessageKind = safeEnumValueOf(conv.lastMessageType, MessageType.NONE),
                    isLastMessageMine = conv.lastMessageSenderId == currentUserId
                )
            }
    }
        .map<List<ConversationUi>, UiState<List<ConversationUi>>> { UiState.Success(it) }
        .catch { error ->
            emit(UiState.Error(unwrapFirestoreError(error, "Failed to load conversations")))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val callsState: StateFlow<UiState<List<CallSession>>> = repository.observeCalls(currentUserId)
        .map<List<CallSession>, UiState<List<CallSession>>> { UiState.Success(it) }
        .catch { error ->
            emit(UiState.Error(unwrapFirestoreError(error, "Failed to load calls")))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val incomingRingingCall: StateFlow<CallSession?> = callsState
        .map { state ->
            val list = (state as? UiState.Success)?.data.orEmpty()
            list.firstOrNull { call ->
                safeEnumValueOf(call.status, CallStatus.ENDED) == CallStatus.RINGING &&
                    call.callerUid != currentUserId &&
                    currentUserId in call.calleeUids
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    val activeCall: StateFlow<CallSession?> = combine(callsState, _activeCallId) { state, activeId ->
        if (activeId == null) return@combine null
        val list = (state as? UiState.Success)?.data.orEmpty()
        list.firstOrNull { it.id == activeId }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = null
    )

    fun bootstrapUser(firebaseUser: FirebaseUser) {
        viewModelScope.launch {
            repository.ensureUserProfile(firebaseUser)
        }
    }

    fun setOnlinePresence(online: Boolean) {
        viewModelScope.launch {
            repository.updatePresence(currentUserId, online)
        }
    }

    fun selectConversation(conversationId: String?) {
        _selectedConversationId.value = conversationId
    }

    fun observeNotificationsIfEnabled(notificationsEnabled: Boolean) {
        if (!notificationsEnabled) return
        val convList = (conversationsState.value as? UiState.Success)?.data ?: return
        val currentKeys = convList.mapNotNull { c ->
            val ts = c.conversation.lastMessageAt?.toDate()?.time ?: return@mapNotNull null
            "${c.conversation.id}:$ts:${c.conversation.lastMessageSenderId}"
        }.toSet()

        if (!initialConvLoaded) {
            lastKnownMessageKeys.addAll(currentKeys)
            initialConvLoaded = true
        } else {
            convList.forEach { convUi ->
                val ts = convUi.conversation.lastMessageAt?.toDate()?.time ?: return@forEach
                val key = "${convUi.conversation.id}:$ts:${convUi.conversation.lastMessageSenderId}"
                if (key !in lastKnownMessageKeys &&
                    !convUi.isLastMessageMine &&
                    convUi.conversation.id != _selectedConversationId.value &&
                    convUi.decryptedLastMessage.isNotBlank()
                ) {
                    notificationHelper.notifyNewMessage(
                        conversationTitle = convUi.displayTitle,
                        preview = convUi.decryptedLastMessage,
                        notificationId = convUi.conversation.id.hashCode()
                    )
                }
            }
            lastKnownMessageKeys.clear()
            lastKnownMessageKeys.addAll(currentKeys)
        }

        val ringing = incomingRingingCall.value
        if (ringing != null && ringing.id != lastNotifiedCallId && ringing.id != _activeCallId.value) {
            lastNotifiedCallId = ringing.id
            notificationHelper.notifyIncomingCall(
                callerName = ringing.callerName,
                isVideo = safeEnumValueOf(ringing.callType, CallType.AUDIO) == CallType.VIDEO,
                callId = ringing.id
            )
        }
    }

    fun openOrCreateDirectChat(peer: UserProfile, onOpened: (String) -> Unit) {
        val current = myProfile.value ?: UserProfile(uid = currentUserId, displayName = "Me")
        val existing = (conversationsState.value as? UiState.Success)?.data?.firstOrNull {
            it.conversationType == ConversationType.DIRECT && peer.uid in it.conversation.memberUids
        }
        if (existing != null) {
            _selectedConversationId.value = existing.conversation.id
            onOpened(existing.conversation.id)
            return
        }
        viewModelScope.launch {
            repository.createOrOpenDirectConversation(current, peer)
                .onSuccess { convId ->
                    _selectedConversationId.value = convId
                    onOpened(convId)
                }
                .onFailure { e ->
                    _statusEvent.tryEmit(e.localizedMessage ?: "Could not start chat")
                }
        }
    }

    fun createGroupChat(
        title: String,
        accentHex: String,
        peers: List<UserProfile>,
        onCreated: (String) -> Unit
    ) {
        val current = myProfile.value ?: UserProfile(uid = currentUserId, displayName = "Me")
        viewModelScope.launch {
            repository.createGroupConversation(current, title, accentHex, peers)
                .onSuccess { convId ->
                    _selectedConversationId.value = convId
                    onCreated(convId)
                }
                .onFailure { e ->
                    _statusEvent.tryEmit(e.localizedMessage ?: "Could not create group")
                }
        }
    }

    fun renameGroup(conversationId: String, newTitle: String, accentHex: String) {
        viewModelScope.launch {
            repository.updateGroupDetails(conversationId, newTitle, accentHex)
                .onFailure { e -> _statusEvent.tryEmit(e.localizedMessage ?: "Update failed") }
        }
    }

    fun startCall(conversationUi: ConversationUi, callType: CallType) {
        val myName = myProfile.value?.displayName?.ifBlank { "Tammie User" } ?: "Tammie User"
        viewModelScope.launch {
            repository.initiateCall(
                conversation = conversationUi.conversation,
                callerName = myName,
                calleeNames = conversationUi.displayTitle,
                callType = callType
            ).onSuccess { callId ->
                _activeCallId.value = callId
            }.onFailure { e ->
                _statusEvent.tryEmit(e.localizedMessage ?: "Could not start call")
            }
        }
    }

    fun acceptIncomingCall(call: CallSession) {
        _activeCallId.value = call.id
        viewModelScope.launch {
            repository.updateCallStatus(call.id, CallStatus.CONNECTED)
                .onFailure { e -> _statusEvent.tryEmit(e.localizedMessage ?: "Could not accept call") }
        }
    }

    fun declineIncomingCall(call: CallSession) {
        viewModelScope.launch {
            repository.updateCallStatus(call.id, CallStatus.DECLINED)
        }
    }

    fun endActiveCall() {
        val call = activeCall.value ?: run {
            _activeCallId.value = null
            return
        }
        val status = safeEnumValueOf(call.status, CallStatus.ENDED)
        val targetStatus = if (status == CallStatus.RINGING && call.callerUid == currentUserId) {
            CallStatus.MISSED
        } else {
            CallStatus.ENDED
        }
        viewModelScope.launch {
            if (status !in listOf(CallStatus.DECLINED, CallStatus.ENDED, CallStatus.MISSED)) {
                repository.updateCallStatus(call.id, targetStatus)
            }
            _activeCallId.value = null
        }
    }

    fun dismissActiveCallOverlay() {
        _activeCallId.value = null
    }

    fun saveProfile(
        displayName: String,
        bio: String,
        avatarColorHex: String,
        showLastSeen: Boolean,
        newAvatarUri: Uri?,
        successMessage: String
    ) {
        viewModelScope.launch {
            var uploadedPhotoUrl: String? = null
            if (newAvatarUri != null) {
                attachmentManager.uploadAvatar(currentUserId, newAvatarUri)
                    .onSuccess { uploadedPhotoUrl = it }
            }
            repository.updateProfile(
                uid = currentUserId,
                displayName = displayName,
                bio = bio,
                avatarColorHex = avatarColorHex,
                showLastSeen = showLastSeen,
                photoUrl = uploadedPhotoUrl
            ).onSuccess {
                _statusEvent.tryEmit(successMessage)
            }.onFailure { e ->
                _statusEvent.tryEmit(e.localizedMessage ?: "Failed to save profile")
            }
        }
    }

    private fun unwrapFirestoreError(error: Throwable, fallback: String): String {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w("MainSessionVM", fallback, rootError)
        return rootError.localizedMessage ?: fallback
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val repository: TammieRepository,
    private val attachmentManager: AttachmentTransferManager,
    private val currentUserId: String
) : ViewModel() {

    private val _activeConversation = MutableStateFlow<Conversation?>(null)
    private val _stagedAttachment = MutableStateFlow<StagedAttachment?>(null)
    val stagedAttachment: StateFlow<StagedAttachment?> = _stagedAttachment.asStateFlow()

    private val _uploadState = MutableStateFlow<AttachmentUploadState>(AttachmentUploadState.Idle)
    val uploadState: StateFlow<AttachmentUploadState> = _uploadState.asStateFlow()

    private val _downloadingMessageId = MutableStateFlow<String?>(null)
    val downloadingMessageId: StateFlow<String?> = _downloadingMessageId.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _chatEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val chatEvents = _chatEvents.asSharedFlow()

    private var uploadJob: Job? = null

    val messagesState: StateFlow<UiState<List<MessageUi>>> = _activeConversation
        .flatMapLatest { conv ->
            if (conv == null) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeMessages(conv.id, currentUserId)
                    .map<List<com.example.data.model.ChatMessage>, UiState<List<MessageUi>>> { rawMessages ->
                        val uiList = rawMessages.map { msg ->
                            val decrypted = TammieCipher.decrypt(
                                msg.ciphertext,
                                msg.iv,
                                conv.id,
                                conv.encryptionSalt
                            )
                            MessageUi(
                                raw = msg,
                                messageType = safeEnumValueOf(msg.messageType, MessageType.TEXT),
                                decryptedText = decrypted,
                                isMine = msg.senderId == currentUserId
                            )
                        }
                        UiState.Success(uiList)
                    }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        Log.w("ChatVM", "Error observing messages", rootError)
                        emit(UiState.Error(rootError.localizedMessage ?: "Failed to load messages"))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    fun setConversation(conversation: Conversation?) {
        if (_activeConversation.value?.id != conversation?.id) {
            _stagedAttachment.value = null
            _uploadState.value = AttachmentUploadState.Idle
        }
        _activeConversation.value = conversation
    }

    fun stagePickedUri(uri: Uri, isImage: Boolean, tooLargeMessage: String) {
        attachmentManager.resolveAttachment(uri, isImage)
            .onSuccess { staged ->
                if (staged.sizeBytes > AttachmentTransferManager.MAX_UPLOAD_BYTES) {
                    _chatEvents.tryEmit(tooLargeMessage)
                } else {
                    _stagedAttachment.value = staged
                    _uploadState.value = AttachmentUploadState.Idle
                }
            }
            .onFailure { e ->
                _chatEvents.tryEmit(e.localizedMessage ?: "Could not read file")
            }
    }

    fun clearStagedAttachment() {
        _stagedAttachment.value = null
        _uploadState.value = AttachmentUploadState.Idle
    }

    fun sendMessage(
        conversation: Conversation,
        senderName: String,
        captionOrText: String,
        fallbackNotice: String
    ) {
        val staged = _stagedAttachment.value
        val trimmed = captionOrText.trim()
        if (staged == null) {
            if (trimmed.isEmpty()) return
            viewModelScope.launch {
                repository.sendEncryptedMessage(
                    conversation = conversation,
                    senderName = senderName,
                    messageType = MessageType.TEXT,
                    plaintextBody = trimmed
                ).onFailure { e ->
                    _chatEvents.tryEmit(e.localizedMessage ?: "Failed to send message")
                }
            }
        } else {
            uploadAndSend(conversation, senderName, staged, trimmed, fallbackNotice)
        }
    }

    fun retryFailedUpload(conversation: Conversation, senderName: String, fallbackNotice: String) {
        val failed = _uploadState.value as? AttachmentUploadState.Failed ?: return
        uploadAndSend(conversation, senderName, failed.attachment, failed.caption, fallbackNotice)
    }

    fun cancelUpload() {
        uploadJob?.cancel()
        attachmentManager.cancelActiveUpload()
        _uploadState.value = AttachmentUploadState.Idle
    }

    private fun uploadAndSend(
        conversation: Conversation,
        senderName: String,
        staged: StagedAttachment,
        caption: String,
        fallbackNotice: String
    ) {
        uploadJob?.cancel()
        uploadJob = viewModelScope.launch {
            _stagedAttachment.value = null
            _uploadState.value = AttachmentUploadState.Uploading(
                attachment = staged,
                progress = 0f,
                bytesTransferred = 0L,
                totalBytes = staged.sizeBytes.coerceAtLeast(1L)
            )

            val result = attachmentManager.uploadAttachment(
                conversationId = conversation.id,
                senderUid = currentUserId,
                attachment = staged,
                onProgress = { progress, bytes, total ->
                    _uploadState.value = AttachmentUploadState.Uploading(
                        attachment = staged,
                        progress = progress,
                        bytesTransferred = bytes,
                        totalBytes = total
                    )
                }
            )

            result.onSuccess { outcome ->
                val msgType = if (staged.isImage) MessageType.IMAGE else MessageType.DOCUMENT
                when (outcome) {
                    is UploadOutcome.CloudUploaded -> {
                        repository.sendEncryptedMessage(
                            conversation = conversation,
                            senderName = senderName,
                            messageType = msgType,
                            plaintextBody = caption,
                            attachmentUrl = outcome.downloadUrl,
                            attachmentPath = outcome.storagePath,
                            attachmentName = staged.fileName,
                            attachmentMimeType = staged.mimeType,
                            attachmentSizeBytes = staged.sizeBytes
                        )
                    }
                    is UploadOutcome.InlineFallback -> {
                        repository.sendEncryptedMessage(
                            conversation = conversation,
                            senderName = senderName,
                            messageType = MessageType.IMAGE,
                            plaintextBody = caption,
                            attachmentName = staged.fileName,
                            attachmentMimeType = "image/jpeg",
                            attachmentSizeBytes = staged.sizeBytes,
                            inlinePreviewBase64 = outcome.base64Jpeg
                        )
                        _chatEvents.tryEmit(fallbackNotice)
                    }
                }
                _uploadState.value = AttachmentUploadState.Idle
            }.onFailure { e ->
                if (e !is CancellationException) {
                    _uploadState.value = AttachmentUploadState.Failed(
                        attachment = staged,
                        caption = caption,
                        errorMessage = e.localizedMessage ?: "Upload failed"
                    )
                } else {
                    _uploadState.value = AttachmentUploadState.Idle
                }
            }
        }
    }

    fun downloadAndOpenMessage(message: MessageUi) {
        val raw = message.raw
        viewModelScope.launch {
            _downloadingMessageId.value = raw.id
            _downloadProgress.value = 0.05f
            attachmentManager.downloadAndOpen(
                fileName = raw.attachmentName ?: "tammie_file",
                mimeType = raw.attachmentMimeType ?: "*/*",
                downloadUrl = raw.attachmentUrl,
                inlinePreviewBase64 = raw.inlinePreviewBase64,
                onProgress = { _downloadProgress.value = it }
            ).onFailure { e ->
                _chatEvents.tryEmit(e.localizedMessage ?: "Could not open file")
            }
            _downloadingMessageId.value = null
            _downloadProgress.value = 0f
        }
    }
}
