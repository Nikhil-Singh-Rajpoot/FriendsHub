package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.data.model.Meetup
import java.util.Locale

object SafetyNotificationHelper {
  const val CHANNEL_ID_SAFETY = "friendshub_meeting_safety"

  fun ensureChannels(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
      if (manager.getNotificationChannel(CHANNEL_ID_SAFETY) == null) {
        val channel =
          NotificationChannel(
            CHANNEL_ID_SAFETY,
            "Meeting Safety Check-Ins",
            NotificationManager.IMPORTANCE_HIGH,
          ).apply {
            description = "Check-in reminders and missed check-in safety alerts for meetups."
          }
        manager.createNotificationChannel(channel)
      }
    }
  }

  fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }

  fun showCheckInReminderNotification(context: Context, meetup: Meetup) {
    if (!hasNotificationPermission(context)) return
    ensureChannels(context)
    val openIntent =
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
    val pendingIntent =
      PendingIntent.getActivity(
        context,
        meetup.id.hashCode(),
        openIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    val builder =
      NotificationCompat.Builder(context, CHANNEL_ID_SAFETY)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("Meetup Check-In Reminder")
        .setContentText("Have you arrived at ${meetup.publicLocationName}? Open FriendsHub to check in or tap I'm Safe.")
        .setStyle(
          NotificationCompat.BigTextStyle()
            .bigText("Your ${meetup.activity} meetup at ${meetup.publicLocationName} (${meetup.scheduledTimeText}) has started. Tap to check in or mark I'm Safe.")
        )
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)

    runCatching {
      NotificationManagerCompat.from(context).notify(meetup.id.hashCode(), builder.build())
    }
  }

  fun showTrustedContactAlertNotification(
    context: Context,
    meetup: Meetup,
    myName: String,
    trustedContactName: String,
    trustedContactPhone: String,
    liveCityArea: String,
  ) {
    if (!hasNotificationPermission(context)) return
    ensureChannels(context)
    val smsIntent =
      buildTrustedContactSmsIntent(
        phone = trustedContactPhone,
        body =
          buildMissedCheckInAlertText(
            myName = myName,
            trustedContactName = trustedContactName,
            meetup = meetup,
            liveCityArea = liveCityArea,
          ),
      )
    val pendingIntent =
      PendingIntent.getActivity(
        context,
        (meetup.id + "_alert").hashCode(),
        smsIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    val targetLabel = trustedContactName.ifBlank { "Trusted Contact" }
    val builder =
      NotificationCompat.Builder(context, CHANNEL_ID_SAFETY)
        .setSmallIcon(android.R.drawable.ic_dialog_alert)
        .setContentTitle("Missed Check-In: Alert $targetLabel")
        .setContentText("You missed your safety check-in at ${meetup.publicLocationName}. Tap to alert $targetLabel or open FriendsHub to mark I'm Safe.")
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)

    runCatching {
      NotificationManagerCompat.from(context).notify((meetup.id + "_alert").hashCode(), builder.build())
    }
  }

  fun buildMeetupShareMessage(
    myName: String,
    peerName: String,
    activity: String,
    dateText: String,
    timeText: String,
    publicLocationName: String,
    publicArea: String,
  ): String {
    return "FriendsHub Meetup Plan from ${myName.ifBlank { "Me" }}:\n" +
      "Meeting: $peerName ($activity)\n" +
      "Place: $publicLocationName, $publicArea\n" +
      "When: $dateText at $timeText\n" +
      "I will check in when arriving and tap \"I'm Safe\" after the meetup."
  }

  fun buildMissedCheckInAlertText(
    myName: String,
    trustedContactName: String,
    meetup: Meetup?,
    liveCityArea: String,
  ): String {
    val greeting = if (trustedContactName.isNotBlank()) "Hi $trustedContactName, " else ""
    return if (meetup != null) {
      "${greeting}this is an automatic safety alert from ${myName.ifBlank { "your contact" }} via FriendsHub. " +
        "I missed my check-in for my ${meetup.activity} meetup at ${meetup.publicLocationName} (${meetup.publicArea}) " +
        "scheduled for ${meetup.scheduledDateText} at ${meetup.scheduledTimeText} with ${meetup.proposerName}/${meetup.otherName}. " +
        "Last known area: ${liveCityArea.ifBlank { meetup.publicArea }}. Please check on me."
    } else {
      "${greeting}this is an urgent help alert from ${myName.ifBlank { "your contact" }} via FriendsHub. " +
        "My last known area is ${liveCityArea.ifBlank { "unavailable" }}. Please call me."
    }
  }

  fun buildEmergencyHelpSmsText(
    myName: String,
    liveCityArea: String,
    latLng: Pair<Double, Double>?,
    activeMeetup: Meetup?,
  ): String {
    val coordsPart =
      if (latLng != null && (latLng.first != 0.0 || latLng.second != 0.0)) {
        String.format(Locale.US, " (Map: https://maps.google.com/?q=%.5f,%.5f)", latLng.first, latLng.second)
      } else {
        ""
      }
    val meetupPart =
      if (activeMeetup != null) {
        " Active meetup: ${activeMeetup.activity} at ${activeMeetup.publicLocationName}, ${activeMeetup.publicArea}."
      } else {
        ""
      }
    return "EMERGENCY ALERT from ${myName.ifBlank { "FriendsHub user" }}: I need help right now. " +
      "Current area: ${liveCityArea.ifBlank { "Unknown" }}$coordsPart.$meetupPart"
  }

  /** Zero-permission system dialer launch (`ACTION_DIAL`, compliant with Google Play policy). */
  fun launchDialer(context: Context, phoneNumber: String) {
    val clean = phoneNumber.trim()
    val intent =
      Intent(Intent.ACTION_DIAL).apply {
        data = Uri.parse("tel:$clean")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    runCatching { context.startActivity(intent) }
  }

  /** Zero-permission SMS compose launch (`ACTION_SENDTO` `smsto:`, compliant with Google Play policy). */
  fun buildTrustedContactSmsIntent(phone: String, body: String): Intent {
    val cleanPhone = phone.trim()
    return if (cleanPhone.isNotBlank()) {
      Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("smsto:${Uri.encode(cleanPhone)}")
        putExtra("sms_body", body)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    } else {
      Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, body)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    }
  }

  fun launchShareSheet(context: Context, title: String, body: String) {
    val sendIntent =
      Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, body)
      }
    val chooser =
      Intent.createChooser(sendIntent, title).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    runCatching { context.startActivity(chooser) }
  }

  fun launchTrustedContactSmsOrShare(context: Context, phone: String, title: String, body: String) {
    val cleanPhone = phone.trim()
    if (cleanPhone.isNotBlank()) {
      val smsIntent = buildTrustedContactSmsIntent(cleanPhone, body)
      val started = runCatching { context.startActivity(smsIntent) }.isSuccess
      if (!started) {
        launchShareSheet(context, title, body)
      }
    } else {
      launchShareSheet(context, title, body)
    }
  }
}
