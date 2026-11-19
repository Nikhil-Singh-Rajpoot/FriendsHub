package com.example.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.example.R
import com.example.data.model.AppNotification
import com.example.data.model.Meetup
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * BroadcastReceiver that displays scheduled meetup countdown, start-time, and safety
 * notifications in the Android mobile notification bar even when the app is closed.
 */
class MeetupAlarmReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
    val body = intent.getStringExtra(EXTRA_BODY).orEmpty()
    val key = intent.getStringExtra(EXTRA_KEY).orEmpty()
    val meetupId = intent.getStringExtra(EXTRA_MEETUP_ID).orEmpty()
    if (title.isBlank() || body.isBlank() || key.isBlank()) return

    SafetyNotificationHelper.showMeetupSystemNotification(
      context = context,
      title = title,
      body = body,
      notificationKey = key,
      meetupId = meetupId,
    )
  }

  companion object {
    const val ACTION_MEETUP_ALARM = "com.example.action.MEETUP_ALARM"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_BODY = "extra_body"
    const val EXTRA_KEY = "extra_key"
    const val EXTRA_MEETUP_ID = "extra_meetup_id"
  }
}

/**
 * Periodic JobService that polls unread meetup/safety notifications from Firestore while the app
 * process is closed, posting them to the device's notification shade.
 */
class MeetupSyncJobService : JobService() {
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onStartJob(params: JobParameters): Boolean {
    val uid = Firebase.auth.currentUser?.uid
    if (uid.isNullOrBlank()) {
      return false
    }

    serviceScope.launch {
      try {
        val context = applicationContext
        val resId = context.resources.getIdentifier("firestore_database_id", "string", context.packageName)
        val dbId =
          if (resId != 0) context.getString(resId)
          else "ai-studio-android-friendsh-4f459fd9-67d1-4907-a908-f0926f62adda"
        val db = FirebaseFirestore.getInstance(dbId)

        val unreadSnap =
          db.collection("notifications")
            .whereEqualTo("recipientUid", uid)
            .whereEqualTo("isRead", false)
            .get()
            .await()

        unreadSnap.toObjects(AppNotification::class.java).forEach { notif ->
          if (
            notif.type.startsWith("MEETUP_") ||
            notif.type == "TRUSTED_CONTACT_ALERT"
          ) {
            SafetyNotificationHelper.showMeetupSystemNotification(
              context = context,
              title = notif.title.ifBlank { "Between-Us Meetup Update" },
              body = notif.body,
              notificationKey = "notif_${notif.id}",
              meetupId = notif.referenceId,
            )
          }
        }

        val now = System.currentTimeMillis()
        val meetupSnap =
          db.collection("meetups")
            .whereArrayContains("participantUids", uid)
            .get()
            .await()
        meetupSnap.toObjects(Meetup::class.java).forEach { meetup ->
          if (
            (meetup.status == "PROPOSED" || meetup.status == "CONFIRMED") &&
            meetup.scheduledAtMillis > 0L
          ) {
            val delta = meetup.scheduledAtMillis - now
            if (delta in -60_000L..10 * 60_000L) {
              val key = "sync_due_${meetup.id}"
              SafetyNotificationHelper.showMeetupSystemNotification(
                context = context,
                title = "Upcoming Meetup: ${meetup.activity}",
                body = "${meetup.publicLocationName} (${meetup.scheduledTimeText}) with ${meetup.peerNameFor(uid)}.",
                notificationKey = key,
                meetupId = meetup.id,
              )
            }
          }
        }
      } catch (_: Exception) {
        // Best-effort background notification sync
      } finally {
        jobFinished(params, false)
      }
    }
    return true
  }

  override fun onStopJob(params: JobParameters): Boolean {
    serviceScope.cancel()
    return true
  }
}

object MeetupBackgroundScheduler {
  private const val SYNC_JOB_ID = 44201

  fun syncScheduledMeetupNotifications(
    context: Context,
    meetups: List<Meetup>,
    currentUid: String,
  ) {
    val appContext = context.applicationContext
    ensurePeriodicNotificationSync(appContext)
    val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    val now = System.currentTimeMillis()

    meetups.forEach { meetup ->
      if (meetup.status !in listOf("PROPOSED", "CONFIRMED") || meetup.scheduledAtMillis <= 0L) {
        cancelMeetupAlarms(appContext, alarmManager, meetup.id)
        return@forEach
      }

      val peerName = meetup.peerNameFor(currentUid)

      // 1) 5 minutes before meetup reminder
      val preAlertAt = meetup.scheduledAtMillis - 5 * 60_000L
      if (preAlertAt > now + 5_000L) {
        scheduleInexactWakeupAlarm(
          context = appContext,
          alarmManager = alarmManager,
          triggerAtMillis = preAlertAt,
          requestCode = ("pre_${meetup.id}").hashCode(),
          key = "alarm_pre_${meetup.id}_${meetup.scheduledAtMillis}",
          title = "Meetup in 5 minutes",
          body = "${meetup.activity} with $peerName at ${meetup.publicLocationName} (${meetup.publicArea}).",
          meetupId = meetup.id,
        )
      }

      // 2) Meetup countdown reaches zero (start time)
      if (meetup.scheduledAtMillis > now + 2_000L) {
        scheduleInexactWakeupAlarm(
          context = appContext,
          alarmManager = alarmManager,
          triggerAtMillis = meetup.scheduledAtMillis,
          requestCode = ("start_${meetup.id}").hashCode(),
          key = "alarm_start_${meetup.id}_${meetup.scheduledAtMillis}",
          title = "Meetup Time: ${meetup.publicLocationName}",
          body = "Countdown finished for your ${meetup.activity} meetup with $peerName. Open Between-Us when you arrive within 5m to start.",
          meetupId = meetup.id,
        )
      }

      // 3) Safety check-in reminder 5 minutes after scheduled start
      val safetyReminderAt = meetup.scheduledAtMillis + 5 * 60_000L
      if (safetyReminderAt > now && !meetup.isCheckedInBy(currentUid) && !meetup.isMarkedSafeBy(currentUid)) {
        scheduleInexactWakeupAlarm(
          context = appContext,
          alarmManager = alarmManager,
          triggerAtMillis = safetyReminderAt,
          requestCode = ("safe_${meetup.id}").hashCode(),
          key = "alarm_safe_${meetup.id}_${meetup.scheduledAtMillis}",
          title = "Meetup Safety Check-In",
          body = "Have you arrived at ${meetup.publicLocationName}? Tap Check In or I'm Safe in Between-Us.",
          meetupId = meetup.id,
        )
      }
    }
  }

  private fun scheduleInexactWakeupAlarm(
    context: Context,
    alarmManager: AlarmManager,
    triggerAtMillis: Long,
    requestCode: Int,
    key: String,
    title: String,
    body: String,
    meetupId: String,
  ) {
    val intent =
      Intent(context, MeetupAlarmReceiver::class.java).apply {
        action = MeetupAlarmReceiver.ACTION_MEETUP_ALARM
        putExtra(MeetupAlarmReceiver.EXTRA_TITLE, title)
        putExtra(MeetupAlarmReceiver.EXTRA_BODY, body)
        putExtra(MeetupAlarmReceiver.EXTRA_KEY, key)
        putExtra(MeetupAlarmReceiver.EXTRA_MEETUP_ID, meetupId)
      }
    val pendingIntent =
      PendingIntent.getBroadcast(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    runCatching {
      alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }
  }

  private fun cancelMeetupAlarms(context: Context, alarmManager: AlarmManager, meetupId: String) {
    listOf("pre_$meetupId", "start_$meetupId", "safe_$meetupId").forEach { tag ->
      val intent = Intent(context, MeetupAlarmReceiver::class.java).apply {
        action = MeetupAlarmReceiver.ACTION_MEETUP_ALARM
      }
      val pendingIntent =
        PendingIntent.getBroadcast(
          context,
          tag.hashCode(),
          intent,
          PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
      if (pendingIntent != null) {
        alarmManager.cancel(pendingIntent)
      }
    }
  }

  fun ensurePeriodicNotificationSync(context: Context) {
    val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
    if (scheduler.getPendingJob(SYNC_JOB_ID) != null) return
    val component = ComponentName(context, MeetupSyncJobService::class.java)
    val jobInfo =
      JobInfo.Builder(SYNC_JOB_ID, component)
        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
        .setPeriodic(15 * 60_000L)
        .build()
    runCatching { scheduler.schedule(jobInfo) }
  }
}
