package com.example.data.model

import android.net.Uri
import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class ConversationType {
    DIRECT, GROUP
}

enum class MessageType {
    NONE, TEXT, IMAGE, DOCUMENT
}

enum class CallType {
    AUDIO, VIDEO
}

enum class CallStatus {
    RINGING, CONNECTED, DECLINED, ENDED, MISSED
}

inline fun <reified T : Enum<T>> safeEnumValueOf(raw: String?, default: T): T {
    return enumValues<T>().firstOrNull { it.name == raw } ?: default
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val uid: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val bio: String = "",
    val avatarColorHex: String = "#0D7C7B",
    val showLastSeen: Boolean = true,
    val online: Boolean = false,
    val discoverable: Boolean = true,
    @ServerTimestamp val lastSeenAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val type: String = ConversationType.DIRECT.name,
    val title: String = "",
    val accentColorHex: String = "#0D7C7B",
    val memberUids: List<String> = emptyList(),
    val memberNames: Map<String, String> = emptyMap(),
    val adminUid: String = "",
    val encryptionSalt: String = "",
    val lastMessageCiphertext: String = "",
    val lastMessageIv: String = "",
    val lastMessageType: String = MessageType.NONE.name,
    val lastMessageSenderId: String = "",
    @ServerTimestamp val lastMessageAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val memberUids: List<String> = emptyList(),
    val messageType: String = MessageType.TEXT.name,
    val ciphertext: String = "",
    val iv: String = "",
    val encryptionVersion: Long = 1L,
    val attachmentUrl: String? = null,
    val attachmentPath: String? = null,
    val attachmentName: String? = null,
    val attachmentMimeType: String? = null,
    val attachmentSizeBytes: Long? = null,
    val inlinePreviewBase64: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CallSession(
    @DocumentId val id: String = "",
    val conversationId: String = "",
    val callerUid: String = "",
    val callerName: String = "",
    val calleeNames: String = "",
    val calleeUids: List<String> = emptyList(),
    val participantUids: List<String> = emptyList(),
    val callType: String = CallType.AUDIO.name,
    val status: String = CallStatus.RINGING.name,
    @ServerTimestamp val startedAt: Timestamp? = null,
    @ServerTimestamp val endedAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

// Domain / UI models
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class ConversationUi(
    val conversation: Conversation,
    val conversationType: ConversationType,
    val displayTitle: String,
    val peerProfile: UserProfile?,
    val decryptedLastMessage: String,
    val lastMessageKind: MessageType,
    val isLastMessageMine: Boolean
)

data class MessageUi(
    val raw: ChatMessage,
    val messageType: MessageType,
    val decryptedText: String,
    val isMine: Boolean
)

data class StagedAttachment(
    val uri: Uri,
    val isImage: Boolean,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long
)

sealed interface AttachmentUploadState {
    data object Idle : AttachmentUploadState
    data class Uploading(
        val attachment: StagedAttachment,
        val progress: Float,
        val bytesTransferred: Long,
        val totalBytes: Long
    ) : AttachmentUploadState
    data class Failed(
        val attachment: StagedAttachment,
        val caption: String,
        val errorMessage: String
    ) : AttachmentUploadState
}
