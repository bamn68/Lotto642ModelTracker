package com.gsbtechnologies.lotto642modeltracker.notifications

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.gsbtechnologies.lotto642modeltracker.MainActivity
import com.gsbtechnologies.lotto642modeltracker.R

object MajorWinNotifier {
    private const val CHANNEL_PREFIX = "major_win_alert_v2_"
    private const val PREF_SOUND_URI = "major_win_sound_uri"
    private const val PREF_CHANNEL_ID = "major_win_channel_id"

    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun bundledSound(context: Context): Uri =
        Uri.parse("android.resource://${context.packageName}/${R.raw.major_win_alert}")

    fun selectedSoundUri(context: Context): Uri =
        prefs(context).getString(PREF_SOUND_URI, null)?.let(Uri::parse) ?: bundledSound(context)

    fun selectedExternalSoundUri(context: Context): Uri? =
        prefs(context).getString(PREF_SOUND_URI, null)?.let(Uri::parse)

    fun selectedSoundName(context: Context): String {
        val saved = prefs(context).getString(PREF_SOUND_URI, null) ?: return "Major Win (built-in)"
        return runCatching {
            RingtoneManager.getRingtone(context, Uri.parse(saved))?.getTitle(context)
        }.getOrNull().takeUnless { it.isNullOrBlank() } ?: "Selected notification sound"
    }

    fun setSound(context: Context, uri: Uri?) {
        val editor = prefs(context).edit()
        if (uri == null) editor.remove(PREF_SOUND_URI) else editor.putString(PREF_SOUND_URI, uri.toString())
        editor.apply()
        createChannel(context, replacePrevious = true)
    }

    private fun channelIdFor(uri: Uri): String =
        CHANNEL_PREFIX + uri.toString().hashCode().toUInt().toString(16)

    fun createChannel(context: Context, replacePrevious: Boolean = false): String {
        val sound = selectedSoundUri(context)
        val channelId = channelIdFor(sound)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val channel = NotificationChannel(channelId, "Major Win Alert", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Loud alert when a Bought & Locked Lotto 6/42 ticket matches 5 or 6 numbers"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 900)
                setSound(sound, attrs)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)

            val settings = prefs(context)
            val previous = settings.getString(PREF_CHANNEL_ID, null)
            if (replacePrevious && previous != null && previous != channelId && previous.startsWith(CHANNEL_PREFIX)) {
                manager.deleteNotificationChannel(previous)
            }
            settings.edit().putString(PREF_CHANNEL_ID, channelId).apply()
        }
        return channelId
    }

    fun notifyMatch(context: Context, matchCount: Int, lineNumber: Int, matched: List<Int>) {
        if (matchCount < 5) return
        val settings = prefs(context)
        if (!settings.getBoolean("major_win_enabled", true)) return

        val channelId = createChannel(context)
        val intent = PendingIntent.getActivity(
            context,
            10,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val title = if (matchCount == 6) "JACKPOT MATCH — 6 OF 6" else "MAJOR WIN — 5 OF 6 MATCHED"
        val text = "Line #$lineNumber matched: ${matched.sorted().joinToString(" • ")}. Verify the official PCSO result."
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.star_big_on)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(intent)
            .build()

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < 33) {
            NotificationManagerCompat.from(context).notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
        }
    }

    fun test(context: Context) = notifyMatch(context, 5, 1, listOf(7, 12, 23, 27, 29))
}
