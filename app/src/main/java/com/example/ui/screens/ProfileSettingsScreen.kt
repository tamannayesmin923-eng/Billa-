package com.example.ui.screens

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.UserProfile
import com.example.data.prefs.UserPreferences
import com.example.i18n.AppLanguage
import com.example.i18n.TammieStrings
import com.example.ui.components.UserAvatar
import com.example.ui.components.parseColorSafe
import com.example.ui.theme.ThemeMode
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

private val AVATAR_SWATCHES = listOf(
    "#0D7C7B", "#1565C0", "#7B4F9E", "#C87A19", "#2E7D32", "#00838F"
)

@Composable
fun ProfileSettingsScreen(
    strings: TammieStrings,
    profile: UserProfile?,
    preferences: UserPreferences,
    onSaveProfile: (displayName: String, bio: String, colorHex: String, showLastSeen: Boolean, avatarUri: Uri?) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val authEmail = remember { Firebase.auth.currentUser?.email.orEmpty() }

    var displayName by remember(profile?.uid) { mutableStateOf(profile?.displayName.orEmpty()) }
    var bio by remember(profile?.uid) { mutableStateOf(profile?.bio.orEmpty()) }
    var selectedColor by remember(profile?.uid) { mutableStateOf(profile?.avatarColorHex ?: AVATAR_SWATCHES.first()) }
    var showLastSeen by remember(profile?.uid) { mutableStateOf(profile?.showLastSeen ?: true) }
    var pickedAvatarUri by remember { mutableStateOf<Uri?>(null) }
    var showSignOutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(profile) {
        if (profile != null) {
            if (displayName.isBlank()) displayName = profile.displayName
            if (bio.isBlank()) bio = profile.bio
            selectedColor = profile.avatarColorHex
            showLastSeen = profile.showLastSeen
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pickedAvatarUri = uri
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onNotificationsChange(granted)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = strings.profileTitle,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Profile Card
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier.clickable {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        ) {
                            UserAvatar(
                                name = displayName.ifBlank { "Tammie" },
                                photoUrl = pickedAvatarUri?.toString() ?: profile?.photoUrl,
                                avatarColorHex = selectedColor,
                                size = 68.dp,
                                isOnline = true
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.PhotoCamera,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayName.ifBlank { "Tammie User" },
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (authEmail.isNotBlank()) {
                                Text(
                                    text = authEmail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it.take(60) },
                        label = { Text(strings.displayNameLabel) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_name_input")
                    )

                    OutlinedTextField(
                        value = bio,
                        onValueChange = { bio = it.take(160) },
                        label = { Text(strings.bioLabel) },
                        maxLines = 2,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_bio_input")
                    )

                    Text(
                        text = strings.avatarColorLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AVATAR_SWATCHES.forEach { hex ->
                            val isSelected = selectedColor == hex
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(parseColorSafe(hex))
                                    .border(
                                        width = if (isSelected) 2.5.dp else 0.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColor = hex },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (displayName.isNotBlank()) {
                                onSaveProfile(displayName, bio, selectedColor, showLastSeen, pickedAvatarUri)
                            }
                        },
                        enabled = displayName.isNotBlank(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("save_profile_button")
                    ) {
                        Text(strings.saveProfile)
                    }
                }
            }

            // Privacy Card (Last Seen)
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = strings.privacySection,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(strings.showLastSeenLabel, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                strings.showLastSeenDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = showLastSeen,
                            onCheckedChange = { checked ->
                                showLastSeen = checked
                                onSaveProfile(displayName.ifBlank { "Tammie User" }, bio, selectedColor, checked, null)
                            },
                            modifier = Modifier.testTag("last_seen_switch")
                        )
                    }
                }
            }

            // Appearance & Language Card
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = strings.appearanceSection,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = strings.themeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val modes = listOf(
                            ThemeMode.SYSTEM to strings.themeSystem,
                            ThemeMode.LIGHT to strings.themeLight,
                            ThemeMode.DARK to strings.themeDark
                        )
                        modes.forEachIndexed { index, (mode, label) ->
                            SegmentedButton(
                                selected = preferences.themeMode == mode,
                                onClick = { onThemeChange(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                                modifier = Modifier.testTag("theme_${mode.name.lowercase()}_btn")
                            ) {
                                Text(label)
                            }
                        }
                    }

                    Text(
                        text = strings.languageLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val langs = AppLanguage.entries
                        langs.forEachIndexed { index, lang ->
                            SegmentedButton(
                                selected = preferences.language == lang,
                                onClick = { onLanguageChange(lang) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = langs.size),
                                modifier = Modifier.testTag("lang_${lang.code}_btn")
                            ) {
                                Text(lang.label)
                            }
                        }
                    }
                }
            }

            // Notifications & Cloud Readiness Card
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = strings.notificationsSection,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(strings.enableNotificationsLabel, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                strings.enableNotificationsDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = preferences.notificationsEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    onNotificationsChange(enabled)
                                }
                            },
                            modifier = Modifier.testTag("notifications_switch")
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Rounded.CloudDone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = strings.cloudStatusTitle,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = strings.cloudStatusDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { showSignOutDialog = true },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(strings.signOut)
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text(strings.signOutConfirmTitle) },
            text = { Text(strings.signOutConfirmBody) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutDialog = false
                        onSignOut()
                    }
                ) {
                    Text(strings.confirm, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}
