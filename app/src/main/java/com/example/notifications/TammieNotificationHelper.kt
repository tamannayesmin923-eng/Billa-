package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

class TammieNotificationHelper(private val context: Context) {

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val messagesChannel = NotificationChannel(
                CHANNEL_MESSAGES,
                "Tammie Messages",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Encrypted direct and group chat notifications"
            }
            val callsChannel = NotificationChannel(
                CHANNEL_CALLS,
                "Tammie Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming audio and video call alerts"
            }
            manager.createNotificationChannel(messagesChannel)
            manager.createNotificationChannel(callsChannel)
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun notifyNewMessage(conversationTitle: String, preview: String, notificationId: Int) {
        if (!hasNotificationPermission()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_tammie)
            .setContentTitle(conversationTitle)
            .setContentText(preview)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
        }
    }

    fun notifyIncomingCall(callerName: String, isVideo: Boolean, callId: String) {
        if (!hasNotificationPermission()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            callId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val label = if (isVideo) "Incoming video call" else "Incoming audio call"
        val notification = NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(R.drawable.ic_stat_tammie)
            .setContentTitle(callerName)
            .setContentText(label)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(callId.hashCode(), notification)
        } catch (_: SecurityException) {
        }
    }

    companion object {
        const val CHANNEL_MESSAGES = "tammie_messages"
        const val CHANNEL_CALLS = "tammie_calls"
    }
}
