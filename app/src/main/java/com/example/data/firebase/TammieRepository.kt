package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.crypto.TammieCipher
import com.example.data.model.CallSession
import com.example.data.model.CallStatus
import com.example.data.model.CallType
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.ConversationType
import com.example.data.model.MessageType
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class TammieRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    suspend fun ensureUserProfile(user: FirebaseUser): Result<Unit> {
        val uid = user.uid
        val path = "users/$uid"
        return try {
            val activeUid = requireUserId()
            check(activeUid == uid) { "Signed-in user mismatch." }
            val docRef = db.collection("users").document(uid)
            val existing = docRef.get().await()
            val safeName = (user.displayName?.trim()?.takeIf { it.isNotEmpty() }
                ?: user.email?.substringBefore("@")?.takeIf { it.isNotEmpty() }
                ?: "Tammie User").take(60)
            val safePhoto = user.photoUrl?.toString()?.take(1000)

            if (!existing.exists()) {
                val payload = mapOf(
                    "uid" to uid,
                    "displayName" to safeName,
                    "photoUrl" to safePhoto,
                    "bio" to "Hey there! I am using Tammie.",
                    "avatarColorHex" to pickAvatarColor(uid),
                    "showLastSeen" to true,
                    "online" to true,
                    "discoverable" to true,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            } else {
                val updates = mutableMapOf<String, Any>(
                    "online" to true,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (existing.getString("photoUrl").isNullOrBlank() && safePhoto != null) {
                    updates["photoUrl"] = safePhoto
                }
                docRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePresence(uid: String, online: Boolean): Result<Unit> {
        val path = "users/$uid"
        return try {
            val activeUid = auth.currentUser?.uid ?: return Result.success(Unit)
            if (activeUid != uid) return Result.success(Unit)
            db.collection("users").document(uid).update(
                mapOf(
                    "online" to online,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateProfile(
        uid: String,
        displayName: String,
        bio: String,
        avatarColorHex: String,
        showLastSeen: Boolean,
        photoUrl: String?
    ): Result<Unit> {
        val path = "users/$uid"
        return try {
            val activeUid = requireUserId()
            check(activeUid == uid) { "Cannot edit another user's profile." }
            val cleanName = displayName.trim().take(60)
            require(cleanName.isNotEmpty()) { "Display name is required." }
            val cleanBio = bio.trim().take(160)
            val updates = mutableMapOf<String, Any>(
                "displayName" to cleanName,
                "bio" to cleanBio,
                "avatarColorHex" to avatarColorHex,
                "showLastSeen" to showLastSeen,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!photoUrl.isNullOrBlank()) {
                updates["photoUrl"] = photoUrl.take(1000)
            }
            db.collection("users").document(uid).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeDirectoryUsers(): Flow<List<UserProfile>> = flow {
        val uid = requireUserId()
        val path = "users"
        emitAll(
            db.collection("users")
                .whereEqualTo("discoverable", true)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    logScopedFlowError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeConversations(uid: String): Flow<List<Conversation>> = flow {
        val activeUid = requireUserId()
        check(activeUid == uid)
        val path = "conversations"
        emitAll(
            db.collection("conversations")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    logScopedFlowError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeMessages(conversationId: String, uid: String): Flow<List<ChatMessage>> = flow {
        val activeUid = requireUserId()
        check(activeUid == uid)
        val path = "conversations/$conversationId/messages"
        emitAll(
            db.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
                }
                .catch { error ->
                    logScopedFlowError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeCalls(uid: String): Flow<List<CallSession>> = flow {
        val activeUid = requireUserId()
        check(activeUid == uid)
        val path = "calls"
        emitAll(
            db.collection("calls")
                .whereArrayContains("participantUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(CallSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.startedAt?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    logScopedFlowError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun createOrOpenDirectConversation(
        currentUser: UserProfile,
        peer: UserProfile
    ): Result<String> {
        val ordered = listOf(currentUser.uid, peer.uid).sorted()
        val convId = "d_${ordered[0]}_${ordered[1]}".replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)
        val path = "conversations/$convId"
        return try {
            val uid = requireUserId()
            check(uid == currentUser.uid)
            val docRef = db.collection("conversations").document(convId)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val salt = TammieCipher.generateSalt()
                val payload = mapOf(
                    "type" to ConversationType.DIRECT.name,
                    "title" to "",
                    "accentColorHex" to "#0D7C7B",
                    "memberUids" to ordered,
                    "memberNames" to mapOf(
                        currentUser.uid to currentUser.displayName.ifBlank { "User" },
                        peer.uid to peer.displayName.ifBlank { "Contact" }
                    ),
                    "adminUid" to uid,
                    "encryptionSalt" to salt,
                    "lastMessageCiphertext" to "",
                    "lastMessageIv" to "",
                    "lastMessageType" to MessageType.NONE.name,
                    "lastMessageSenderId" to "",
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(convId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun createGroupConversation(
        currentUser: UserProfile,
        title: String,
        accentColorHex: String,
        peers: List<UserProfile>
    ): Result<String> {
        val convId = "grp_${UUID.randomUUID().toString().replace("-", "")}"
        val path = "conversations/$convId"
        return try {
            val uid = requireUserId()
            check(uid == currentUser.uid)
            val cleanTitle = title.trim().take(80)
            require(cleanTitle.isNotEmpty()) { "Group title is required." }
            val allUsers = (listOf(currentUser) + peers).distinctBy { it.uid }.take(50)
            require(allUsers.size >= 2) { "Group needs at least 2 members." }

            val memberUids = allUsers.map { it.uid }
            val memberNames = allUsers.associate { it.uid to it.displayName.ifBlank { "Member" } }
            val salt = TammieCipher.generateSalt()

            val payload = mapOf(
                "type" to ConversationType.GROUP.name,
                "title" to cleanTitle,
                "accentColorHex" to accentColorHex,
                "memberUids" to memberUids,
                "memberNames" to memberNames,
                "adminUid" to uid,
                "encryptionSalt" to salt,
                "lastMessageCiphertext" to "",
                "lastMessageIv" to "",
                "lastMessageType" to MessageType.NONE.name,
                "lastMessageSenderId" to "",
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("conversations").document(convId).set(payload).await()
            Result.success(convId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateGroupDetails(
        conversationId: String,
        title: String,
        accentColorHex: String
    ): Result<Unit> {
        val path = "conversations/$conversationId"
        return try {
            requireUserId()
            val cleanTitle = title.trim().take(80)
            require(cleanTitle.isNotEmpty()) { "Group title is required." }
            db.collection("conversations").document(conversationId).update(
                mapOf(
                    "title" to cleanTitle,
                    "accentColorHex" to accentColorHex,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun sendEncryptedMessage(
        conversation: Conversation,
        senderName: String,
        messageType: MessageType,
        plaintextBody: String,
        attachmentUrl: String? = null,
        attachmentPath: String? = null,
        attachmentName: String? = null,
        attachmentMimeType: String? = null,
        attachmentSizeBytes: Long? = null,
        inlinePreviewBase64: String? = null
    ): Result<Unit> {
        val convId = conversation.id
        val msgId = "m_${UUID.randomUUID().toString().replace("-", "")}"
        val msgPath = "conversations/$convId/messages/$msgId"
        return try {
            val uid = requireUserId()
            require(convId.isNotBlank())
            val encrypted = TammieCipher.encrypt(
                plaintext = plaintextBody,
                conversationId = convId,
                encryptionSalt = conversation.encryptionSalt
            )

            val previewSource = when (messageType) {
                MessageType.IMAGE -> if (plaintextBody.isNotBlank()) plaintextBody else "Photo: ${attachmentName ?: "image"}"
                MessageType.DOCUMENT -> if (plaintextBody.isNotBlank()) plaintextBody else "Document: ${attachmentName ?: "file"}"
                else -> plaintextBody
            }
            val encryptedPreview = TammieCipher.encrypt(
                plaintext = previewSource.take(200),
                conversationId = convId,
                encryptionSalt = conversation.encryptionSalt
            )

            val msgPayload = mapOf(
                "senderId" to uid,
                "senderName" to senderName.ifBlank { "User" }.take(80),
                "memberUids" to conversation.memberUids,
                "messageType" to messageType.name,
                "ciphertext" to encrypted.ciphertextBase64,
                "iv" to encrypted.ivBase64,
                "encryptionVersion" to 1L,
                "attachmentUrl" to attachmentUrl?.take(2048),
                "attachmentPath" to attachmentPath?.take(512),
                "attachmentName" to attachmentName?.take(256),
                "attachmentMimeType" to attachmentMimeType?.take(128),
                "attachmentSizeBytes" to attachmentSizeBytes,
                "inlinePreviewBase64" to inlinePreviewBase64?.take(450000),
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            val convRef = db.collection("conversations").document(convId)
            val msgRef = convRef.collection("messages").document(msgId)

            val batch = db.batch()
            batch.set(msgRef, msgPayload)
            batch.update(
                convRef,
                mapOf(
                    "lastMessageCiphertext" to encryptedPreview.ciphertextBase64.take(4000),
                    "lastMessageIv" to encryptedPreview.ivBase64,
                    "lastMessageType" to messageType.name,
                    "lastMessageSenderId" to uid,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, msgPath)
            Result.failure(e)
        }
    }

    suspend fun initiateCall(
        conversation: Conversation,
        callerName: String,
        calleeNames: String,
        callType: CallType
    ): Result<String> {
        val callId = "c_${UUID.randomUUID().toString().replace("-", "")}"
        val path = "calls/$callId"
        return try {
            val uid = requireUserId()
            val calleeUids = conversation.memberUids.filter { it != uid }
            require(calleeUids.isNotEmpty()) { "No recipients available for call." }
            val payload = mapOf(
                "conversationId" to conversation.id,
                "callerUid" to uid,
                "callerName" to callerName.ifBlank { "Caller" }.take(80),
                "calleeNames" to calleeNames.take(120),
                "calleeUids" to calleeUids,
                "participantUids" to conversation.memberUids,
                "callType" to callType.name,
                "status" to CallStatus.RINGING.name,
                "startedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("calls").document(callId).set(payload).await()
            Result.success(callId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateCallStatus(callId: String, newStatus: CallStatus): Result<Unit> {
        val path = "calls/$callId"
        return try {
            requireUserId()
            val updates = mutableMapOf<String, Any>(
                "status" to newStatus.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (newStatus in listOf(CallStatus.DECLINED, CallStatus.ENDED, CallStatus.MISSED)) {
                updates["endedAt"] = FieldValue.serverTimestamp()
            }
            db.collection("calls").document(callId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    private fun logScopedFlowError(error: Throwable, op: OperationType, path: String, uid: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val toLog = firestoreError ?: (error as? Exception)
        if (toLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(toLog, op, path)
        }
    }

    companion object {
        private val AVATAR_COLORS = listOf(
            "#0D7C7B", "#1565C0", "#7B4F9E", "#C87A19", "#2E7D32", "#00838F"
        )

        fun pickAvatarColor(seed: String): String {
            val idx = (seed.hashCode() and 0x7fffffff) % AVATAR_COLORS.size
            return AVATAR_COLORS[idx]
        }
    }
}
