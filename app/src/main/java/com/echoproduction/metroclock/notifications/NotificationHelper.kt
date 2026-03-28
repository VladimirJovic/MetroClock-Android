package com.echoproduction.metroclock.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

object NotificationHelper {

    private const val CHANNEL_ID   = "metroclock_channel"
    private const val CHANNEL_NAME = "MetroClock Notifications"

    private val db = FirebaseFirestore.getInstance()

    // ── Channel ──────────────────────────────────────────────────────────────

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    // ── Show local notification (called when app is in foreground) ────────────

    fun showNotification(context: Context, title: String, body: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    // ── Save FCM token to Firestore ───────────────────────────────────────────

    fun saveToken(userId: String) {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            db.collection("users").document(userId)
                .update("fcmToken", token)
        }
    }
}
