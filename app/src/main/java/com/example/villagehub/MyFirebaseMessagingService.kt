package com.example.villagehub

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.*

class MyFirebaseMessagingService : FirebaseMessagingService() {

    // 1. Срабатывает, когда приходит ЛЮБОЕ уведомление (и чат, и новости)
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        remoteMessage.notification?.let {
            // Показываем уведомление с тем заголовком, который пришел
            sendNotification(it.title ?: "VillageHub", it.body ?: "Новое событие")
        }
    }

    // 2. Сохраняем токен (нужно для личных сообщений)
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "Новый токен: $token")
        saveTokenToDatabase(token)
    }

    private fun saveTokenToDatabase(token: String) {
        val sharedPref = getSharedPreferences("VillagePrefs", Context.MODE_PRIVATE)
        val myPhone = sharedPref.getString("USER_PHONE", "") ?: ""

        if (myPhone.isNotEmpty()) {
            FirebaseDatabase.getInstance().getReference("Users")
                .child(myPhone).child("token").setValue(token)
        }
    }

    private fun sendNotification(title: String, messageBody: String) {
        // ИСПРАВИЛ: Теперь всегда открываем ГЛАВНОЕ МЕНЮ
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "village_chat_channel"
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_email) // Иконка конвертика
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Уведомления VillageHub",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(Random().nextInt(), notificationBuilder.build())
    }
}