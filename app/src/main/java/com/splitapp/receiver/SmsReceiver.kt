package com.splitapp.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import com.splitapp.R
import com.splitapp.SplitActivity
import com.splitapp.SplitApplication


import com.splitapp.parser.TransactionParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "splitapp_transactions"
        const val EXTRA_TRANSACTION_ID = "transaction_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        // Group multi-part SMS by sender
        val grouped = messages.groupBy { it.originatingAddress ?: "" }

        val db = SplitApplication.database(context)

        CoroutineScope(Dispatchers.IO).launch {
            for ((sender, parts) in grouped) {
                val body = parts.joinToString("") { it.messageBody }
                val transaction = TransactionParser.parse(sender, body) ?: continue

                val id = db.transactionDao().insert(transaction)

                showNotification(context, id, transaction.amount, transaction.description)
            }
        }
    }

    private fun showNotification(context: Context, transactionId: Long, amount: Double, description: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel (safe to call repeatedly — no-op if already exists)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Transaction Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            this.description = "Bank transaction detected — tap to split"
        }
        notificationManager.createNotificationChannel(channel)

        val openIntent = Intent(context, SplitActivity::class.java).apply {
            putExtra(EXTRA_TRANSACTION_ID, transactionId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            transactionId.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val amountStr = "₹%.2f".format(amount)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$amountStr debited — Split this?")
            .setContentText(description)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(transactionId.toInt(), notification)
    }
}
