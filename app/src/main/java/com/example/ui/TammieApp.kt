package com.example.ui

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.firebase.TammieRepository
import com.example.data.firebase.authStateFlow
import com.example.data.firebase.signOutUser
import com.example.data.model.UiState
import com.example.data.prefs.PreferencesRepository
import com.example.data.prefs.UserPreferences
import com.example.data.storage.AttachmentTransferManager
import com.example.i18n.AppLanguage
import com.example.i18n.TammieStrings
import com.example.i18n.stringsFor
import com.example.notifications.TammieNotificationHelper
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CallStageScreen
import com.example.ui.screens.CallsHistoryScreen
import com.example.ui.screens.ChatDetailScreen
import com.example.ui.screens.ConversationsPane
import com.example.ui.screens.IncomingCallBanner
import com.example.ui.screens.ProfileSettingsScreen
import com.example.ui.theme.TammieTheme
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.MainSessionViewModel
import com.example.ui.viewmodel.SettingsViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private enum class TammieTab {
    CHATS, CALLS, PROFILE
}

@Composable
fun TammieApp() {
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                SettingsViewModel(PreferencesRepository(app))
            }
        }
    )
    val prefs by settingsViewModel.preferences.collectAsStateWithLifecycle()
    val strings = remember(prefs.language) { stringsFor(prefs.language) }

    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    TammieTheme(themeMode = prefs.themeMode) {
        val user = currentUser
        if (user == null) {
            AuthScreen(
                strings = strings,
                language = prefs.language,
                onToggleLanguage = {
                    val next = if (prefs.language == AppLanguage.EN) AppLanguage.BN else AppLanguage.EN
                    settingsViewModel.setLanguage(next)
                },
                onAuthSuccess = {}
            )
        } else {
            key(user.uid) {
                TammieWorkspace(
                    firebaseUser = user,
                    preferences = prefs,
                    strings = strings,
                    settingsViewModel = settingsViewModel
                )
            }
        }
    }
}

@Composable
private fun TammieWorkspace(
    firebaseUser: FirebaseUser,
    preferences: UserPreferences,
    strings: TammieStrings,
    settingsViewModel: SettingsViewModel
) {
    val uid = firebaseUser.uid
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val sessionViewModel: MainSessionViewModel = viewModel(
        key = uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                MainSessionViewModel(
                    repository = TammieRepository(db),
                    notificationHelper = TammieNotificationHelper(app),
                    attachmentManager = AttachmentTransferManager(app),
                    currentUserId = uid
                )
            }
        }
    )

    val chatViewModel: ChatViewModel = viewModel(
        key = "chat_$uid",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                ChatViewModel(
                    repository = TammieRepository(db),
                    attachmentManager = AttachmentTransferManager(app),
                    currentUserId = uid
                )
            }
        }
    )

    LaunchedEffect(uid) {
        sessionViewModel.bootstrapUser(firebaseUser)
    }

    DisposableEffect(lifecycleOwner, uid) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> sessionViewModel.setOnlinePresence(true)
                Lifecycle.Event.ON_STOP -> sessionViewModel.setOnlinePresence(false)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val conversationsState by sessionViewModel.conversationsState.collectAsStateWithLifecycle()
    val directoryState by sessionViewModel.directoryUsers.collectAsStateWithLifecycle()
    val myProfile by sessionViewModel.myProfile.collectAsStateWithLifecycle()
    val callsState by sessionViewModel.callsState.collectAsStateWithLifecycle()
    val selectedConvId by sessionViewModel.selectedConversationId.collectAsStateWithLifecycle()
    val incomingCall by sessionViewModel.incomingRingingCall.collectAsStateWithLifecycle()
    val activeCall by sessionViewModel.activeCall.collectAsStateWithLifecycle()

    val messagesState by chatViewModel.messagesState.collectAsStateWithLifecycle()
    val stagedAttachment by chatViewModel.stagedAttachment.collectAsStateWithLifecycle()
    val uploadState by chatViewModel.uploadState.collectAsStateWithLifecycle()
    val downloadingMsgId by chatViewModel.downloadingMessageId.collectAsStateWithLifecycle()
    val downloadProgress by chatViewModel.downloadProgress.collectAsStateWithLifecycle()

    val selectedConversationUi = remember(conversationsState, selectedConvId) {
        val list = (conversationsState as? UiState.Success)?.data ?: return@remember null
        list.firstOrNull { it.conversation.id == selectedConvId }
    }

    LaunchedEffect(selectedConversationUi?.conversation?.id) {
        chatViewModel.setConversation(selectedConversationUi?.conversation)
    }

    LaunchedEffect(conversationsState, incomingCall, preferences.notificationsEnabled) {
        sessionViewModel.observeNotificationsIfEnabled(preferences.notificationsEnabled)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        sessionViewModel.statusEvent.collect { msg -> snackbarHostState.showSnackbar(msg) }
    }
    LaunchedEffect(Unit) {
        chatViewModel.chatEvents.collect { msg -> snackbarHostState.showSnackbar(msg) }
    }

    var currentTab by rememberSaveable { mutableStateOf(TammieTab.CHATS) }
    val senderName = myProfile?.displayName ?: firebaseUser.displayName ?: "Tammie User"

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 840.dp

        Box(modifier = Modifier.fillMaxSize()) {
            if (isExpanded) {
                // Expanded Tablet / Foldable Layout: NavigationRail + List-Detail
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        NavigationRail {
                            Spacer(Modifier.height(12.dp))
                            NavigationRailItem(
                                selected = currentTab == TammieTab.CHATS,
                                onClick = { currentTab = TammieTab.CHATS },
                                icon = {
                                    Icon(
                                        if (currentTab == TammieTab.CHATS) Icons.Rounded.Forum else Icons.Outlined.Forum,
                                        contentDescription = strings.tabChats
                                    )
                                },
                                label = { Text(strings.tabChats) },
                                modifier = Modifier.testTag("nav_chats")
                            )
                            NavigationRailItem(
                                selected = currentTab == TammieTab.CALLS,
                                onClick = { currentTab = TammieTab.CALLS },
                                icon = {
                                    Icon(
                                        if (currentTab == TammieTab.CALLS) Icons.Rounded.Call else Icons.Outlined.Call,
                                        contentDescription = strings.tabCalls
                                    )
                                },
                                label = { Text(strings.tabCalls) },
                                modifier = Modifier.testTag("nav_calls")
                            )
                            NavigationRailItem(
                                selected = currentTab == TammieTab.PROFILE,
                                onClick = { currentTab = TammieTab.PROFILE },
                                icon = {
                                    Icon(
                                        if (currentTab == TammieTab.PROFILE) Icons.Rounded.Person else Icons.Outlined.Person,
                                        contentDescription = strings.tabProfile
                                    )
                                },
                                label = { Text(strings.tabProfile) },
                                modifier = Modifier.testTag("nav_profile")
                            )
                        }

                        when (currentTab) {
                            TammieTab.CHATS -> {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    ConversationsPane(
                                        strings = strings,
                                        language = preferences.language,
                                        onToggleLanguage = {
                                            val next = if (preferences.language == AppLanguage.EN) AppLanguage.BN else AppLanguage.EN
                                            settingsViewModel.setLanguage(next)
                                        },
                                        currentUserId = uid,
                                        conversationsState = conversationsState,
                                        directoryUsersState = directoryState,
                                        selectedConversationId = selectedConvId,
                                        onSelectConversation = { sessionViewModel.selectConversation(it) },
                                        onStartDirectChat = { peer ->
                                            sessionViewModel.openOrCreateDirectChat(peer) {}
                                        },
                                        onCreateGroupChat = { title, accent, peers ->
                                            sessionViewModel.createGroupChat(title, accent, peers) {}
                                        },
                                        modifier = Modifier
                                            .width(360.dp)
                                            .fillMaxHeight()
                                    )

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .background(MaterialTheme.colorScheme.background)
                                    ) {
                                        if (selectedConversationUi != null) {
                                            ChatDetailScreen(
                                                strings = strings,
                                                conversationUi = selectedConversationUi,
                                                messagesState = messagesState,
                                                stagedAttachment = stagedAttachment,
                                                uploadState = uploadState,
                                                downloadingMessageId = downloadingMsgId,
                                                downloadProgress = downloadProgress,
                                                currentUserId = uid,
                                                showBackButton = false,
                                                onBack = { sessionViewModel.selectConversation(null) },
                                                onStagePickedUri = { uri, isImg ->
                                                    chatViewModel.stagePickedUri(uri, isImg, strings.fileTooLarge)
                                                },
                                                onClearStaged = { chatViewModel.clearStagedAttachment() },
                                                onSendMessage = { text ->
                                                    chatViewModel.sendMessage(
                                                        selectedConversationUi.conversation,
                                                        senderName,
                                                        text,
                                                        strings.storageFallbackNotice
                                                    )
                                                },
                                                onRetryUpload = {
                                                    chatViewModel.retryFailedUpload(
                                                        selectedConversationUi.conversation,
                                                        senderName,
                                                        strings.storageFallbackNotice
                                                    )
                                                },
                                                onCancelUpload = { chatViewModel.cancelUpload() },
                                                onDownloadAndOpen = { chatViewModel.downloadAndOpenMessage(it) },
                                                onStartCall = { type -> sessionViewModel.startCall(selectedConversationUi, type) },
                                                onRenameGroup = { newTitle ->
                                                    sessionViewModel.renameGroup(
                                                        selectedConversationUi.conversation.id,
                                                        newTitle,
                                                        selectedConversationUi.conversation.accentColorHex
                                                    )
                                                }
                                            )
                                        } else {
                                            EmptySplitPanePlaceholder(strings = strings)
                                        }
                                    }
                                }
                            }

                            TammieTab.CALLS -> {
                                CallsHistoryScreen(
                                    strings = strings,
                                    currentUserId = uid,
                                    callsState = callsState,
                                    onCallbackConversation = { convId, callType ->
                                        val convUi = (conversationsState as? UiState.Success)?.data
                                            ?.firstOrNull { it.conversation.id == convId }
                                        if (convUi != null) {
                                            sessionViewModel.startCall(convUi, callType)
                                        }
                                    }
                                )
                            }

                            TammieTab.PROFILE -> {
                                ProfileSettingsScreen(
                                    strings = strings,
                                    profile = myProfile,
                                    preferences = preferences,
                                    onSaveProfile = { name, bio, color, showLastSeen, uri ->
                                        sessionViewModel.saveProfile(
                                            name,
                                            bio,
                                            color,
                                            showLastSeen,
                                            uri,
                                            strings.profileSaved
                                        )
                                    },
                                    onThemeChange = { settingsViewModel.setThemeMode(it) },
                                    onLanguageChange = { settingsViewModel.setLanguage(it) },
                                    onNotificationsChange = { settingsViewModel.setNotificationsEnabled(it) },
                                    onSignOut = {
                                        signOutUser(
                                            credentialManager = credentialManager,
                                            scope = scope,
                                            onBeforeSignOut = { sessionViewModel.setOnlinePresence(false) }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                // Compact Handheld Layout
                if (currentTab == TammieTab.CHATS && selectedConversationUi != null) {
                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        contentWindowInsets = WindowInsets.safeDrawing
                    ) { innerPadding ->
                        ChatDetailScreen(
                            strings = strings,
                            conversationUi = selectedConversationUi,
                            messagesState = messagesState,
                            stagedAttachment = stagedAttachment,
                            uploadState = uploadState,
                            downloadingMessageId = downloadingMsgId,
                            downloadProgress = downloadProgress,
                            currentUserId = uid,
                            showBackButton = true,
                            onBack = { sessionViewModel.selectConversation(null) },
                            onStagePickedUri = { uri, isImg ->
                                chatViewModel.stagePickedUri(uri, isImg, strings.fileTooLarge)
                            },
                            onClearStaged = { chatViewModel.clearStagedAttachment() },
                            onSendMessage = { text ->
                                chatViewModel.sendMessage(
                                    selectedConversationUi.conversation,
                                    senderName,
                                    text,
                                    strings.storageFallbackNotice
                                )
                            },
                            onRetryUpload = {
                                chatViewModel.retryFailedUpload(
                                    selectedConversationUi.conversation,
                                    senderName,
                                    strings.storageFallbackNotice
                                )
                            },
                            onCancelUpload = { chatViewModel.cancelUpload() },
                            onDownloadAndOpen = { chatViewModel.downloadAndOpenMessage(it) },
                            onStartCall = { type -> sessionViewModel.startCall(selectedConversationUi, type) },
                            onRenameGroup = { newTitle ->
                                sessionViewModel.renameGroup(
                                    selectedConversationUi.conversation.id,
                                    newTitle,
                                    selectedConversationUi.conversation.accentColorHex
                                )
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                } else {
                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        contentWindowInsets = WindowInsets.safeDrawing,
                        bottomBar = {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = currentTab == TammieTab.CHATS,
                                    onClick = { currentTab = TammieTab.CHATS },
                                    icon = {
                                        Icon(
                                            if (currentTab == TammieTab.CHATS) Icons.Rounded.Forum else Icons.Outlined.Forum,
                                            contentDescription = strings.tabChats
                                        )
                                    },
                                    label = { Text(strings.tabChats) },
                                    modifier = Modifier.testTag("nav_chats")
                                )
                                NavigationBarItem(
                                    selected = currentTab == TammieTab.CALLS,
                                    onClick = { currentTab = TammieTab.CALLS },
                                    icon = {
                                        Icon(
                                            if (currentTab == TammieTab.CALLS) Icons.Rounded.Call else Icons.Outlined.Call,
                                            contentDescription = strings.tabCalls
                                        )
                                    },
                                    label = { Text(strings.tabCalls) },
                                    modifier = Modifier.testTag("nav_calls")
                                )
                                NavigationBarItem(
                                    selected = currentTab == TammieTab.PROFILE,
                                    onClick = { currentTab = TammieTab.PROFILE },
                                    icon = {
                                        Icon(
                                            if (currentTab == TammieTab.PROFILE) Icons.Rounded.Person else Icons.Outlined.Person,
                                            contentDescription = strings.tabProfile
                                        )
                                    },
                                    label = { Text(strings.tabProfile) },
                                    modifier = Modifier.testTag("nav_profile")
                                )
                            }
                        }
                    ) { innerPadding ->
                        when (currentTab) {
                            TammieTab.CHATS -> {
                                ConversationsPane(
                                    strings = strings,
                                    language = preferences.language,
                                    onToggleLanguage = {
                                        val next = if (preferences.language == AppLanguage.EN) AppLanguage.BN else AppLanguage.EN
                                        settingsViewModel.setLanguage(next)
                                    },
                                    currentUserId = uid,
                                    conversationsState = conversationsState,
                                    directoryUsersState = directoryState,
                                    selectedConversationId = selectedConvId,
                                    onSelectConversation = { sessionViewModel.selectConversation(it) },
                                    onStartDirectChat = { peer ->
                                        sessionViewModel.openOrCreateDirectChat(peer) {}
                                    },
                                    onCreateGroupChat = { title, accent, peers ->
                                        sessionViewModel.createGroupChat(title, accent, peers) {}
                                    },
                                    modifier = Modifier.padding(innerPadding)
                                )
                            }

                            TammieTab.CALLS -> {
                                CallsHistoryScreen(
                                    strings = strings,
                                    currentUserId = uid,
                                    callsState = callsState,
                                    onCallbackConversation = { convId, callType ->
                                        val convUi = (conversationsState as? UiState.Success)?.data
                                            ?.firstOrNull { it.conversation.id == convId }
                                        if (convUi != null) {
                                            sessionViewModel.startCall(convUi, callType)
                                        }
                                    },
                                    modifier = Modifier.padding(innerPadding)
                                )
                            }

                            TammieTab.PROFILE -> {
                                ProfileSettingsScreen(
                                    strings = strings,
                                    profile = myProfile,
                                    preferences = preferences,
                                    onSaveProfile = { name, bio, color, showLastSeen, uri ->
                                        sessionViewModel.saveProfile(
                                            name,
                                            bio,
                                            color,
                                            showLastSeen,
                                            uri,
                                            strings.profileSaved
                                        )
                                    },
                                    onThemeChange = { settingsViewModel.setThemeMode(it) },
                                    onLanguageChange = { settingsViewModel.setLanguage(it) },
                                    onNotificationsChange = { settingsViewModel.setNotificationsEnabled(it) },
                                    onSignOut = {
                                        signOutUser(
                                            credentialManager = credentialManager,
                                            scope = scope,
                                            onBeforeSignOut = { sessionViewModel.setOnlinePresence(false) }
                                        )
                                    },
                                    modifier = Modifier.padding(innerPadding)
                                )
                            }
                        }
                    }
                }
            }

            // Floating Incoming Call Banner Overlay
            AnimatedVisibility(
                visible = incomingCall != null && activeCall == null,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                incomingCall?.let { call ->
                    IncomingCallBanner(
                        call = call,
                        strings = strings,
                        onAccept = { sessionViewModel.acceptIncomingCall(call) },
                        onDecline = { sessionViewModel.declineIncomingCall(call) }
                    )
                }
            }

            // Full-Screen Live Call Stage Overlay
            activeCall?.let { call ->
                CallStageScreen(
                    call = call,
                    currentUserId = uid,
                    strings = strings,
                    onEndCall = { sessionViewModel.endActiveCall() },
                    onDismissFinished = { sessionViewModel.dismissActiveCallOverlay() }
                )
            }
        }
    }
}

@Composable
private fun EmptySplitPanePlaceholder(strings: TammieStrings) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = strings.selectConversationHint,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
