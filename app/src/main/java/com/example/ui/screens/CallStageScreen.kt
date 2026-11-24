package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.CallSession
import com.example.data.model.CallStatus
import com.example.data.model.CallType
import com.example.data.model.safeEnumValueOf
import com.example.i18n.TammieStrings
import com.example.ui.components.AcousticCallRings
import com.example.ui.components.UserAvatar
import com.example.ui.theme.CallAcceptGreen
import kotlinx.coroutines.delay

@Composable
fun IncomingCallBanner(
    call: CallSession,
    strings: TammieStrings,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isVideo = safeEnumValueOf(call.callType, CallType.AUDIO) == CallType.VIDEO
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(name = call.callerName, size = 46.dp, isOnline = true)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = call.callerName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${strings.incomingCall} ${if (isVideo) strings.videoCall else strings.audioCall}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(
                onClick = onDecline,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(48.dp)
                    .testTag("decline_call_button")
            ) {
                Icon(Icons.Rounded.CallEnd, contentDescription = strings.decline)
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onAccept,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = CallAcceptGreen,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(48.dp)
                    .testTag("accept_call_button")
            ) {
                Icon(
                    if (isVideo) Icons.Rounded.Videocam else Icons.Rounded.Call,
                    contentDescription = strings.accept
                )
            }
        }
    }
}

@Composable
fun CallStageScreen(
    call: CallSession,
    currentUserId: String,
    strings: TammieStrings,
    onEndCall: () -> Unit,
    onDismissFinished: () -> Unit
) {
    BackHandler(onBack = onEndCall)

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isVideoCall = safeEnumValueOf(call.callType, CallType.AUDIO) == CallType.VIDEO
    val callStatus = safeEnumValueOf(call.status, CallStatus.RINGING)
    val peerName = if (call.callerUid == currentUserId) call.calleeNames else call.callerName

    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(isVideoCall) }
    var isVideoEnabled by remember { mutableStateOf(isVideoCall) }
    var useFrontCamera by remember { mutableStateOf(true) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }

    var hasCameraPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasCameraPerm = result[Manifest.permission.CAMERA] ?: hasCameraPerm
        hasAudioPerm = result[Manifest.permission.RECORD_AUDIO] ?: hasAudioPerm
    }

    LaunchedEffect(isVideoCall) {
        val needed = buildList {
            if (!hasAudioPerm) add(Manifest.permission.RECORD_AUDIO)
            if (isVideoCall && !hasCameraPerm) add(Manifest.permission.CAMERA)
        }
        if (needed.isNotEmpty()) {
            permLauncher.launch(needed.toTypedArray())
        }
    }

    // Audio routing
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    DisposableEffect(isMuted, isSpeakerOn) {
        audioManager?.apply {
            mode = AudioManager.MODE_IN_COMMUNICATION
            isMicrophoneMute = isMuted
            @Suppress("DEPRECATION")
            isSpeakerphoneOn = isSpeakerOn
        }
        onDispose {
            audioManager?.apply {
                isMicrophoneMute = false
                @Suppress("DEPRECATION")
                isSpeakerphoneOn = false
                mode = AudioManager.MODE_NORMAL
            }
        }
    }

    // Call elapsed timer + auto-dismiss after terminal state
    LaunchedEffect(callStatus) {
        if (callStatus == CallStatus.CONNECTED) {
            while (true) {
                delay(1000L)
                elapsedSeconds += 1
            }
        } else if (callStatus in listOf(CallStatus.ENDED, CallStatus.DECLINED, CallStatus.MISSED)) {
            delay(1400L)
            onDismissFinished()
        }
    }

    val statusText = when (callStatus) {
        CallStatus.RINGING -> if (call.callerUid == currentUserId) strings.calling else strings.ringing
        CallStatus.CONNECTED -> "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60)
        CallStatus.DECLINED -> strings.declined
        CallStatus.ENDED -> strings.ended
        CallStatus.MISSED -> strings.missed
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF091A1D), Color(0xFF0E292E), Color(0xFF071316))
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Live CameraX preview when Video Call & permission granted
        if (isVideoEnabled && hasCameraPerm) {
            val previewView = remember { PreviewView(context) }
            LaunchedEffect(useFrontCamera, isVideoEnabled) {
                val providerFuture = ProcessCameraProvider.getInstance(context)
                providerFuture.addListener({
                    try {
                        val cameraProvider = providerFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val selector = if (useFrontCamera) {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        } else {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        }
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview)
                    } catch (_: Exception) {
                    }
                }, ContextCompat.getMainExecutor(context))
            }

            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
            )
        } else {
            // Audio Call acoustic stage
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                AcousticCallRings(
                    isRinging = callStatus == CallStatus.RINGING,
                    modifier = Modifier.size(280.dp)
                )
            }
        }

        // Top Peer Header
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!isVideoEnabled || !hasCameraPerm) {
                UserAvatar(name = peerName, size = 96.dp, isOnline = callStatus == CallStatus.CONNECTED)
                Spacer(Modifier.height(18.dp))
            }
            Text(
                text = peerName,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFA7F3EB)
            )

            if ((isVideoCall && !hasCameraPerm) || !hasAudioPerm) {
                Spacer(Modifier.height(16.dp))
                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = strings.callPermissionsNeeded,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                permLauncher.launch(
                                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                                )
                            }
                        ) {
                            Text(strings.grantPermissions)
                        }
                    }
                }
            }
        }

        // Bottom Controls Bar
        Surface(
            color = Color.Black.copy(alpha = 0.52f),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 28.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CallControlIcon(
                    icon = if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                    label = if (isMuted) strings.unmute else strings.mute,
                    active = isMuted,
                    onClick = { isMuted = !isMuted }
                )
                CallControlIcon(
                    icon = Icons.Rounded.VolumeUp,
                    label = strings.speaker,
                    active = isSpeakerOn,
                    onClick = { isSpeakerOn = !isSpeakerOn }
                )
                if (isVideoCall) {
                    CallControlIcon(
                        icon = if (isVideoEnabled) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                        label = if (isVideoEnabled) strings.cameraOn else strings.cameraOff,
                        active = !isVideoEnabled,
                        onClick = { isVideoEnabled = !isVideoEnabled }
                    )
                    CallControlIcon(
                        icon = Icons.Rounded.Cameraswitch,
                        label = strings.flipCamera,
                        active = false,
                        onClick = { useFrontCamera = !useFrontCamera }
                    )
                }
                Button(
                    onClick = onEndCall,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD63B3B),
                        contentColor = Color.White
                    ),
                    shape = CircleShape,
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("end_call_button")
                ) {
                    Icon(Icons.Rounded.CallEnd, contentDescription = strings.endCall)
                }
            }
        }
    }
}

@Composable
private fun CallControlIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (active) Color.White else Color.White.copy(alpha = 0.18f),
                contentColor = if (active) Color(0xFF0E292E) else Color.White
            ),
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
        ) {
            Icon(icon, contentDescription = label)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f)
        )
    }
}
