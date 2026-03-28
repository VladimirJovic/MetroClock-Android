package com.echoproduction.metroclock.notifications

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Handles incoming FCM messages and token refreshes.
 * Registered in AndroidManifest.xml with the MESSAGING_EVENT intent filter.
 */
class MetroClockMessagingService : FirebaseMessagingService() {

    /**
     * Called when FCM issues a new token (first run or token expiry).
     * Saves the fresh token to Firestore so push delivery stays valid.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .update("fcmToken", token)
    }

    /**
     * Called when the app is in the foreground and a data/notification message arrives.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val title = remoteMessage.notification?.title ?: return
        val body  = remoteMessage.notification?.body  ?: return
        NotificationHelper.showNotification(this, title, body)
    }
}
