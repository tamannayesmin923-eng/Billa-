package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.CallSession
import com.example.data.model.CallStatus
import com.example.data.model.CallType
import com.example.data.model.UiState
import com.example.data.model.safeEnumValueOf
import com.example.i18n.TammieStrings
import com.example.ui.components.UserAvatar
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun CallsHistoryScreen(
    strings: TammieStrings,
    currentUserId: String,
    callsState: UiState<List<CallSession>>,
    onCallbackConversation: (conversationId: String, callType: CallType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Text(
            text = strings.tabCalls,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
        )

        when (callsState) {
            is UiState.Loading -> {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
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
                        text = callsState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            is UiState.Success -> {
                val calls = callsState.data
                if (calls.isEmpty()) {
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
                                    imageVector = Icons.Rounded.Call,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = strings.noCallsTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = strings.noCallsSubtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(calls, key = { it.id }) { call ->
                            val isOutgoing = call.callerUid == currentUserId
                            val peerTitle = if (isOutgoing) call.calleeNames else call.callerName
                            val callType = safeEnumValueOf(call.callType, CallType.AUDIO)
                            val status = safeEnumValueOf(call.status, CallStatus.ENDED)
                            val statusText = when (status) {
                                CallStatus.RINGING -> strings.ringing
                                CallStatus.CONNECTED -> strings.connected
                                CallStatus.DECLINED -> strings.declined
                                CallStatus.MISSED -> strings.missed
                                CallStatus.ENDED -> strings.ended
                            }
                            val timeText = remember(call.startedAt) {
                                val date = call.startedAt?.toDate() ?: return@remember ""
                                SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(date)
                            }
                            val isAlertStatus = status == CallStatus.DECLINED || status == CallStatus.MISSED

                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    UserAvatar(name = peerTitle, size = 46.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = peerTitle,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (isOutgoing) Icons.AutoMirrored.Rounded.CallMade else Icons.AutoMirrored.Rounded.CallReceived,
                                                contentDescription = null,
                                                tint = if (isAlertStatus) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = "$statusText • $timeText",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { onCallbackConversation(call.conversationId, callType) },
                                        modifier = Modifier.testTag("callback_button_${call.id}")
                                    ) {
                                        Icon(
                                            imageVector = if (callType == CallType.VIDEO) Icons.Rounded.Videocam else Icons.Rounded.Call,
                                            contentDescription = if (callType == CallType.VIDEO) strings.videoCall else strings.audioCall,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
