package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ConversationType
import com.example.data.model.ConversationUi
import com.example.data.model.MessageType
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.i18n.AppLanguage
import com.example.i18n.TammieStrings
import com.example.ui.components.LanguagePillToggle
import com.example.ui.components.UserAvatar
import com.example.ui.components.parseColorSafe
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ChatFilter {
    ALL, DIRECT, GROUPS
}

private val GROUP_ACCENTS = listOf("#0D7C7B", "#1565C0", "#7B4F9E", "#C87A19", "#2E7D32")

@Composable
fun ConversationsPane(
    strings: TammieStrings,
    language: AppLanguage,
    onToggleLanguage: () -> Unit,
    currentUserId: String,
    conversationsState: UiState<List<ConversationUi>>,
    directoryUsersState: UiState<List<UserProfile>>,
    selectedConversationId: String?,
    onSelectConversation: (String) -> Unit,
    onStartDirectChat: (UserProfile) -> Unit,
    onCreateGroupChat: (String, String, List<UserProfile>) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(ChatFilter.ALL) }
    var showNewChatSheet by remember { mutableStateOf(false) }

    val directoryPeers = remember(directoryUsersState, currentUserId) {
        ((directoryUsersState as? UiState.Success)?.data ?: emptyList())
            .filter { it.uid != currentUserId }
    }
    val onlinePeers = remember(directoryPeers) { directoryPeers.filter { it.online } }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNewChatSheet = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(strings.newChat) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("new_chat_fab")
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = strings.appName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = strings.tagline,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LanguagePillToggle(
                    isBengali = language == AppLanguage.BN,
                    onToggle = onToggleLanguage,
                    modifier = Modifier.testTag("chats_language_toggle")
                )
            }

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(strings.searchChatsPlaceholder) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = strings.close)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("chats_search_input")
            )

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = activeFilter == ChatFilter.ALL,
                    onClick = { activeFilter = ChatFilter.ALL },
                    label = { Text(strings.filterAll) },
                    modifier = Modifier.testTag("filter_all_chip")
                )
                FilterChip(
                    selected = activeFilter == ChatFilter.DIRECT,
                    onClick = { activeFilter = ChatFilter.DIRECT },
                    label = { Text(strings.filterDirect) },
                    modifier = Modifier.testTag("filter_direct_chip")
                )
                FilterChip(
                    selected = activeFilter == ChatFilter.GROUPS,
                    onClick = { activeFilter = ChatFilter.GROUPS },
                    label = { Text(strings.filterGroups) },
                    modifier = Modifier.testTag("filter_groups_chip")
                )
            }

            // Active Now strip
            if (onlinePeers.isNotEmpty()) {
                Text(
                    text = strings.activeNow,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(onlinePeers, key = { it.uid }) { peer ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onStartDirectChat(peer) }
                                .padding(4.dp)
                        ) {
                            UserAvatar(
                                name = peer.displayName,
                                photoUrl = peer.photoUrl,
                                avatarColorHex = peer.avatarColorHex,
                                size = 50.dp,
                                isOnline = true
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = peer.displayName.substringBefore(" "),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Conversation list
            when (conversationsState) {
                is UiState.Loading -> {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                is UiState.Error -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = conversationsState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                is UiState.Success -> {
                    val filtered = remember(conversationsState.data, searchQuery, activeFilter) {
                        conversationsState.data.filter { item ->
                            val matchesFilter = when (activeFilter) {
                                ChatFilter.ALL -> true
                                ChatFilter.DIRECT -> item.conversationType == ConversationType.DIRECT
                                ChatFilter.GROUPS -> item.conversationType == ConversationType.GROUP
                            }
                            val matchesSearch = searchQuery.isBlank() ||
                                item.displayTitle.contains(searchQuery, ignoreCase = true) ||
                                item.decryptedLastMessage.contains(searchQuery, ignoreCase = true)
                            matchesFilter && matchesSearch
                        }
                    }

                    if (filtered.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Forum,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    text = strings.noChatsTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = strings.noChatsSubtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 4.dp,
                                bottom = 88.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filtered, key = { it.conversation.id }) { item ->
                                ConversationCard(
                                    item = item,
                                    strings = strings,
                                    isSelected = item.conversation.id == selectedConversationId,
                                    onClick = { onSelectConversation(item.conversation.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewChatSheet) {
        NewChatGroupSheet(
            strings = strings,
            peers = directoryPeers,
            onDismiss = { showNewChatSheet = false },
            onSelectDirectPeer = { peer ->
                showNewChatSheet = false
                onStartDirectChat(peer)
            },
            onCreateGroup = { title, accent, members ->
                showNewChatSheet = false
                onCreateGroupChat(title, accent, members)
            }
        )
    }
}

@Composable
private fun ConversationCard(
    item: ConversationUi,
    strings: TammieStrings,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val timeText = remember(item.conversation.lastMessageAt) {
        val date = item.conversation.lastMessageAt?.toDate() ?: return@remember ""
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 0.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("conversation_card_${item.conversation.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(
                name = item.displayTitle,
                photoUrl = item.peerProfile?.photoUrl,
                avatarColorHex = if (item.conversationType == ConversationType.GROUP) {
                    item.conversation.accentColorHex
                } else {
                    item.peerProfile?.avatarColorHex ?: "#0D7C7B"
                },
                size = 52.dp,
                isOnline = item.peerProfile?.online == true,
                isGroup = item.conversationType == ConversationType.GROUP
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = item.displayTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = strings.encryptedBadge,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    if (timeText.isNotEmpty()) {
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.lastMessageKind == MessageType.IMAGE) {
                        Icon(
                            Icons.Rounded.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    } else if (item.lastMessageKind == MessageType.DOCUMENT) {
                        Icon(
                            Icons.Rounded.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    val previewText = when {
                        item.decryptedLastMessage.isNotBlank() -> {
                            if (item.isLastMessageMine) "${strings.youPrefix}: ${item.decryptedLastMessage}"
                            else item.decryptedLastMessage
                        }
                        item.lastMessageKind == MessageType.IMAGE -> strings.photoAttachment
                        item.lastMessageKind == MessageType.DOCUMENT -> strings.documentAttachment
                        else -> strings.noMessagesYet
                    }
                    Text(
                        text = previewText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewChatGroupSheet(
    strings: TammieStrings,
    peers: List<UserProfile>,
    onDismiss: () -> Unit,
    onSelectDirectPeer: (UserProfile) -> Unit,
    onCreateGroup: (String, String, List<UserProfile>) -> Unit
) {
    BackHandler(onBack = onDismiss)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isGroupMode by remember { mutableStateOf(false) }
    var groupTitle by remember { mutableStateOf("") }
    var selectedAccent by remember { mutableStateOf(GROUP_ACCENTS.first()) }
    var selectedMemberIds by remember { mutableStateOf(setOf<String>()) }
    var peopleQuery by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val filteredPeers = remember(peers, peopleQuery) {
        peers.filter {
            peopleQuery.isBlank() || it.displayName.contains(peopleQuery, ignoreCase = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = strings.newChatSheetTitle,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !isGroupMode,
                    onClick = {
                        isGroupMode = false
                        validationError = null
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text(strings.modeDirect)
                }
                SegmentedButton(
                    selected = isGroupMode,
                    onClick = {
                        isGroupMode = true
                        validationError = null
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text(strings.modeGroup)
                }
            }

            if (isGroupMode) {
                OutlinedTextField(
                    value = groupTitle,
                    onValueChange = {
                        groupTitle = it
                        validationError = null
                    },
                    placeholder = { Text(strings.groupTitlePlaceholder) },
                    leadingIcon = { Icon(Icons.Rounded.Group, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("group_title_input")
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = strings.groupAccentLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    GROUP_ACCENTS.forEach { hex ->
                        val selected = selectedAccent == hex
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(parseColorSafe(hex))
                                .border(
                                    width = if (selected) 2.5.dp else 0.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface,
                                    shape = CircleShape
                                )
                                .clickable { selectedAccent = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = peopleQuery,
                onValueChange = { peopleQuery = it },
                placeholder = { Text(strings.searchPeoplePlaceholder) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            if (validationError != null) {
                Text(
                    text = validationError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (filteredPeers.isEmpty()) {
                Text(
                    text = strings.noContactsFound,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredPeers, key = { it.uid }) { peer ->
                        val checked = peer.uid in selectedMemberIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    if (!isGroupMode) {
                                        onSelectDirectPeer(peer)
                                    } else {
                                        selectedMemberIds = if (checked) {
                                            selectedMemberIds - peer.uid
                                        } else {
                                            selectedMemberIds + peer.uid
                                        }
                                        validationError = null
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(
                                name = peer.displayName,
                                photoUrl = peer.photoUrl,
                                avatarColorHex = peer.avatarColorHex,
                                size = 44.dp,
                                isOnline = peer.online
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = peer.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (peer.bio.isNotBlank()) {
                                    Text(
                                        text = peer.bio,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (isGroupMode) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { isChecked ->
                                        selectedMemberIds = if (isChecked) {
                                            selectedMemberIds + peer.uid
                                        } else {
                                            selectedMemberIds - peer.uid
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (isGroupMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(strings.cancel)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            when {
                                groupTitle.isBlank() -> validationError = strings.enterGroupName
                                selectedMemberIds.isEmpty() -> validationError = strings.selectAtLeastOne
                                else -> {
                                    val chosen = peers.filter { it.uid in selectedMemberIds }
                                    onCreateGroup(groupTitle.trim(), selectedAccent, chosen)
                                }
                            }
                        },
                        modifier = Modifier.testTag("create_group_button")
                    ) {
                        Text(strings.createGroup)
                    }
                }
            }
        }
    }
}
