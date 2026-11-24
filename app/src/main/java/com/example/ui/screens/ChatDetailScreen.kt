package com.example.ui.screens

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.crypto.TammieCipher
import com.example.data.model.AttachmentUploadState
import com.example.data.model.CallType
import com.example.data.model.ConversationType
import com.example.data.model.ConversationUi
import com.example.data.model.MessageType
import com.example.data.model.MessageUi
import com.example.data.model.StagedAttachment
import com.example.data.model.UiState
import com.example.i18n.TammieStrings
import com.example.ui.components.Base64ImagePreview
import com.example.ui.components.UserAvatar
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    strings: TammieStrings,
    conversationUi: ConversationUi,
    messagesState: UiState<List<MessageUi>>,
    stagedAttachment: StagedAttachment?,
    uploadState: AttachmentUploadState,
    downloadingMessageId: String?,
    downloadProgress: Float,
    currentUserId: String,
    showBackButton: Boolean,
    onBack: () -> Unit,
    onStagePickedUri: (Uri, Boolean) -> Unit,
    onClearStaged: () -> Unit,
    onSendMessage: (String) -> Unit,
    onRetryUpload: () -> Unit,
    onCancelUpload: () -> Unit,
    onDownloadAndOpen: (MessageUi) -> Unit,
    onStartCall: (CallType) -> Unit,
    onRenameGroup: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (showBackButton) {
        BackHandler(onBack = onBack)
    }

    val context = LocalContext.current
    var draftText by remember(conversationUi.conversation.id) { mutableStateOf("") }
    var showAttachSheet by remember { mutableStateOf(false) }
    var showEncryptionDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var lightboxMessage by remember { mutableStateOf<MessageUi?>(null) }

    val listState = rememberLazyListState()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onStagePickedUri(uri, true)
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onStagePickedUri(uri, false)
        }
    }

    val messageCount = (messagesState as? UiState.Success)?.data?.size ?: 0
    LaunchedEffect(messageCount) {
        if (messageCount > 0) {
            listState.animateScrollToItem(messageCount - 1)
        }
    }

    val subtitle = remember(conversationUi, strings) {
        if (conversationUi.conversationType == ConversationType.GROUP) {
            "${conversationUi.conversation.memberUids.size} ${strings.membersCount}"
        } else {
            val peer = conversationUi.peerProfile
            when {
                peer == null -> strings.encryptedBadge
                peer.online -> strings.online
                !peer.showLastSeen -> strings.lastSeenHidden
                peer.lastSeenAt != null -> {
                    val fmt = SimpleDateFormat("h:mm a", Locale.getDefault()).format(peer.lastSeenAt.toDate())
                    "${strings.lastSeenAt} $fmt"
                }
                else -> strings.lastSeenHidden
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("chat_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = strings.close
                            )
                        }
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showEncryptionDialog = true }
                    ) {
                        UserAvatar(
                            name = conversationUi.displayTitle,
                            photoUrl = conversationUi.peerProfile?.photoUrl,
                            avatarColorHex = if (conversationUi.conversationType == ConversationType.GROUP) {
                                conversationUi.conversation.accentColorHex
                            } else {
                                conversationUi.peerProfile?.avatarColorHex ?: "#0D7C7B"
                            },
                            size = 40.dp,
                            isOnline = conversationUi.peerProfile?.online == true,
                            isGroup = conversationUi.conversationType == ConversationType.GROUP
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = conversationUi.displayTitle,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = strings.encryptedBadge,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (conversationUi.peerProfile?.online == true) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (conversationUi.conversationType == ConversationType.GROUP &&
                        conversationUi.conversation.adminUid == currentUserId
                    ) {
                        IconButton(onClick = { showRenameDialog = true }) {
                            Icon(Icons.Rounded.Edit, contentDescription = strings.editGroupTitle)
                        }
                    }
                    IconButton(
                        onClick = { onStartCall(CallType.AUDIO) },
                        modifier = Modifier.testTag("audio_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Call,
                            contentDescription = strings.audioCall,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { onStartCall(CallType.VIDEO) },
                        modifier = Modifier.testTag("video_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Videocam,
                            contentDescription = strings.videoCall,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .navigationBarsPadding()
        ) {
            // Message list
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (messagesState) {
                    is UiState.Loading -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    is UiState.Error -> {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = messagesState.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }

                    is UiState.Success -> {
                        val messages = messagesState.data
                        if (messages.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(18.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.VerifiedUser,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = strings.noMessagesYet,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(messages, key = { it.raw.id }) { msgUi ->
                                    MessageBubble(
                                        msgUi = msgUi,
                                        isGroup = conversationUi.conversationType == ConversationType.GROUP,
                                        strings = strings,
                                        isDownloading = downloadingMessageId == msgUi.raw.id,
                                        downloadProgress = downloadProgress,
                                        onImageClick = { lightboxMessage = msgUi },
                                        onDownloadDoc = { onDownloadAndOpen(msgUi) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Live Upload Progress / Failure Card
            AnimatedVisibility(visible = uploadState !is AttachmentUploadState.Idle) {
                when (uploadState) {
                    is AttachmentUploadState.Uploading -> {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${strings.uploadingProgress}: ${uploadState.attachment.fileName} (${(uploadState.progress * 100).toInt()}%)",
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = onCancelUpload) {
                                        Text(strings.cancel)
                                    }
                                }
                                LinearProgressIndicator(
                                    progress = { uploadState.progress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    is AttachmentUploadState.Failed -> {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${strings.uploadFailed}: ${uploadState.attachment.fileName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = onRetryUpload) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.retry)
                                }
                                IconButton(onClick = onCancelUpload) {
                                    Icon(Icons.Rounded.Close, contentDescription = strings.cancel)
                                }
                            }
                        }
                    }

                    AttachmentUploadState.Idle -> {}
                }
            }

            // Staged Attachment Preview Strip
            AnimatedVisibility(visible = stagedAttachment != null) {
                stagedAttachment?.let { staged ->
                    val sizeLabel = remember(staged.sizeBytes) {
                        Formatter.formatShortFileSize(context, staged.sizeBytes)
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (staged.isImage) {
                                AsyncImage(
                                    model = staged.uri,
                                    contentDescription = staged.fileName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.Description,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = staged.fileName,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "$sizeLabel • ${staged.mimeType}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = onClearStaged) {
                                Icon(Icons.Rounded.Close, contentDescription = strings.cancel)
                            }
                        }
                    }
                }
            }

            // Composer Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { showAttachSheet = true },
                        modifier = Modifier.testTag("attach_file_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = strings.attach,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    OutlinedTextField(
                        value = draftText,
                        onValueChange = { draftText = it },
                        placeholder = {
                            Text(
                                strings.messagePlaceholder,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        maxLines = 4,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("message_input")
                    )

                    FilledIconButton(
                        onClick = {
                            val toSend = draftText
                            draftText = ""
                            onSendMessage(toSend)
                        },
                        enabled = draftText.isNotBlank() || stagedAttachment != null,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("send_message_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = strings.send
                        )
                    }
                }
            }
        }
    }

    // Attachment Picker Bottom Sheet
    if (showAttachSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAttachSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = strings.attach,
                    style = MaterialTheme.typography.titleLarge
                )
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            showAttachSheet = false
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                        .testTag("pick_photo_option")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            Icons.Rounded.PhotoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(strings.sendPhoto, style = MaterialTheme.typography.titleMedium)
                    }
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            showAttachSheet = false
                            documentPickerLauncher.launch(
                                arrayOf(
                                    "application/pdf",
                                    "application/msword",
                                    "application/vnd.openxmlformats-officedocument.*",
                                    "text/*",
                                    "application/zip",
                                    "application/octet-stream"
                                )
                            )
                        }
                        .testTag("pick_document_option")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Text(strings.sendDocument, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }

    // Encryption Key Fingerprint Dialog
    if (showEncryptionDialog) {
        val fingerprint = remember(conversationUi.conversation.id, conversationUi.conversation.encryptionSalt) {
            TammieCipher.fingerprint(
                conversationUi.conversation.id,
                conversationUi.conversation.encryptionSalt
            )
        }
        AlertDialog(
            onDismissRequest = { showEncryptionDialog = false },
            icon = {
                Icon(
                    Icons.Rounded.VerifiedUser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text(strings.encryptionDialogTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.encryptionDialogBody, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = strings.keyFingerprintLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = fingerprint,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showEncryptionDialog = false }) {
                    Text(strings.close)
                }
            }
        )
    }

    // Rename Group Dialog
    if (showRenameDialog) {
        var newTitle by remember { mutableStateOf(conversationUi.displayTitle) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(strings.editGroupTitle) },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it.take(80) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            onRenameGroup(newTitle.trim())
                            showRenameDialog = false
                        }
                    }
                ) {
                    Text(strings.saveProfile)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    // Full-screen Image Lightbox
    lightboxMessage?.let { msg ->
        ImageLightboxDialog(
            message = msg,
            strings = strings,
            onDismiss = { lightboxMessage = null },
            onDownload = { onDownloadAndOpen(msg) }
        )
    }
}

@Composable
private fun MessageBubble(
    msgUi: MessageUi,
    isGroup: Boolean,
    strings: TammieStrings,
    isDownloading: Boolean,
    downloadProgress: Float,
    onImageClick: () -> Unit,
    onDownloadDoc: () -> Unit
) {
    val context = LocalContext.current
    val isMine = msgUi.isMine
    val bubbleColor = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val textColor = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val metaColor = if (isMine) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurfaceVariant

    val timeLabel = remember(msgUi.raw.createdAt) {
        val d = msgUi.raw.createdAt?.toDate() ?: return@remember ""
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(d)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isMine) 18.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 18.dp
            ),
            tonalElevation = if (isMine) 0.dp else 1.dp,
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                if (isGroup && !isMine) {
                    Text(
                        text = msgUi.raw.senderName,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                }

                // Image Attachment Card
                if (msgUi.messageType == MessageType.IMAGE) {
                    val hasUrl = !msgUi.raw.attachmentUrl.isNullOrBlank()
                    val hasInline = !msgUi.raw.inlinePreviewBase64.isNullOrBlank()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onImageClick)
                    ) {
                        if (hasUrl) {
                            AsyncImage(
                                model = msgUi.raw.attachmentUrl,
                                contentDescription = msgUi.raw.attachmentName ?: strings.photoAttachment,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (hasInline) {
                            Base64ImagePreview(
                                base64 = msgUi.raw.inlinePreviewBase64.orEmpty(),
                                contentDescription = msgUi.raw.attachmentName ?: strings.photoAttachment,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    if (msgUi.decryptedText.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                    }
                }

                // Document Attachment Card
                if (msgUi.messageType == MessageType.DOCUMENT) {
                    val sizeText = remember(msgUi.raw.attachmentSizeBytes) {
                        Formatter.formatShortFileSize(context, msgUi.raw.attachmentSizeBytes ?: 0L)
                    }
                    Surface(
                        color = if (isMine) Color.White.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onDownloadDoc)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.Description,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = msgUi.raw.attachmentName ?: strings.documentAttachment,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = textColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "$sizeText • ${strings.downloadAndOpen}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = metaColor
                                    )
                                }
                                Icon(
                                    Icons.Rounded.Download,
                                    contentDescription = strings.downloadAndOpen,
                                    tint = textColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            if (isDownloading) {
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { downloadProgress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    if (msgUi.decryptedText.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                    }
                }

                if (msgUi.decryptedText.isNotBlank()) {
                    Text(
                        text = msgUi.decryptedText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor
                    )
                }

                if (timeLabel.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = metaColor,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageLightboxDialog(
    message: MessageUi,
    strings: TammieStrings,
    onDismiss: () -> Unit,
    onDownload: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    var scale by remember { mutableFloatStateOf(1f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 4f)
                    }
                }
        ) {
            if (!message.raw.attachmentUrl.isNullOrBlank()) {
                AsyncImage(
                    model = message.raw.attachmentUrl,
                    contentDescription = message.raw.attachmentName ?: strings.photoAttachment,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(scaleX = scale, scaleY = scale)
                )
            } else if (!message.raw.inlinePreviewBase64.isNullOrBlank()) {
                Base64ImagePreview(
                    base64 = message.raw.inlinePreviewBase64.orEmpty(),
                    contentDescription = message.raw.attachmentName ?: strings.photoAttachment,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(scaleX = scale, scaleY = scale)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = strings.close, tint = Color.White)
                }
                IconButton(
                    onClick = onDownload,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = strings.downloadAndOpen, tint = Color.White)
                }
            }
        }
    }
}
