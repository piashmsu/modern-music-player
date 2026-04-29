package com.gsmtrick.musicplayer.work

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Calendar
import com.gsmtrick.musicplayer.R

object DailyStatsScheduler {
    private const val REQUEST_CODE = 9911
    private const val CHANNEL_ID = "daily_stats"

    fun enable(context: Context) {
        ensureChannel(context)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, create = true) ?: return
        // Trigger every day at 21:00 local
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 21)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (timeInMillis < System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        am.setInexactRepeating(
            AlarmManager.RTC,
            cal.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pi,
        )
    }

    fun disable(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, create = false) ?: return
        am.cancel(pi)
    }

    private fun pendingIntent(context: Context, create: Boolean): PendingIntent? {
        val intent = Intent(context, DailyStatsReceiver::class.java)
        val flags = (PendingIntent.FLAG_IMMUTABLE) or
            (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE)
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Daily stats",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Daily listening summary"
            }
            nm?.createNotificationChannel(channel)
        }
    }

    fun showNotification(context: Context, body: String) {
        ensureChannel(context)
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Today's listening")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        val nm = context.getSystemService(NotificationManager::class.java)
        nm?.notify(2611, notif)
    }
}

class DailyStatsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val msg = "Tap to open Modern Music Player and see today's listening stats."
        DailyStatsScheduler.showNotification(context, msg)
    }
}
