package com.gsbtechnologies.lotto642modeltracker.notifications

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.gsbtechnologies.lotto642modeltracker.MainActivity
import com.gsbtechnologies.lotto642modeltracker.R

object MajorWinNotifier {
    const val CHANNEL_ID="major_win_alert"

    fun createChannel(context: Context) {
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) {
            val sound=Uri.parse("android.resource://${context.packageName}/${R.raw.major_win_alert}")
            val attrs=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            val channel=NotificationChannel(CHANNEL_ID,"Major Win Alert",NotificationManager.IMPORTANCE_HIGH).apply {
                description="Loud alert when a Bought & Locked Lotto 6/42 ticket matches 5 or 6 numbers"
                enableVibration(true)
                vibrationPattern=longArrayOf(0,500,200,500,200,900)
                setSound(sound,attrs)
                lockscreenVisibility=Notification.VISIBILITY_PUBLIC
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun notifyMatch(context: Context, matchCount:Int, lineNumber:Int, matched:List<Int>) {
        if(matchCount<5) return
        val prefs=context.getSharedPreferences("settings",Context.MODE_PRIVATE)
        if(!prefs.getBoolean("major_win_enabled",true)) return
        val intent=PendingIntent.getActivity(context,10,Intent(context,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val title=if(matchCount==6) "JACKPOT MATCH — 6 OF 6" else "MAJOR WIN — 5 OF 6 MATCHED"
        val text="Line #$lineNumber matched: ${matched.sorted().joinToString(" • ")}. Verify the official PCSO result."
        val n=NotificationCompat.Builder(context,CHANNEL_ID)
            .setSmallIcon(android.R.drawable.star_big_on)
            .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true).setContentIntent(intent).build()
        if(ActivityCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT<33) {
            NotificationManagerCompat.from(context).notify((System.currentTimeMillis()%Int.MAX_VALUE).toInt(),n)
        }
    }

    fun test(context:Context)=notifyMatch(context,5,1,listOf(7,12,23,27,29))
}
