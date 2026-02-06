package com.remindme.app.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.remindme.app.MainActivity
import com.remindme.app.R
import com.remindme.app.data.entity.Goal
import com.remindme.app.data.entity.Task

object NotificationHelper {

    private var notificationIdCounter = 1000

    private fun getNextNotificationId(): Int = notificationIdCounter++

    private fun hasNotificationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun createMainIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // Action intents for notification buttons
    private fun createCompleteTaskIntent(context: Context, taskId: Long): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_COMPLETE_TASK
            putExtra(NotificationActionReceiver.EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(
            context, taskId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createSnoozeTaskIntent(context: Context, taskId: Long): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SNOOZE_TASK
            putExtra(NotificationActionReceiver.EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(
            context, (taskId + 10000).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createCheckInGoalIntent(context: Context, goalId: Long): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_CHECK_IN_GOAL
            putExtra(NotificationActionReceiver.EXTRA_GOAL_ID, goalId)
        }
        return PendingIntent.getBroadcast(
            context, (goalId + 20000).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun showTaskReminder(context: Context, task: Task) {
        if (!hasNotificationPermission(context)) return

        val notification = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_TASK_REMINDERS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Reminder")
            .setContentText(task.description)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(createMainIntent(context))
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Done",
                createCompleteTaskIntent(context, task.id)
            )
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Snooze 1h",
                createSnoozeTaskIntent(context, task.id)
            )
            .apply {
                if (task.locationName != null) {
                    setSubText("Near ${task.locationName}")
                }
                if (task.notes != null) {
                    setStyle(NotificationCompat.BigTextStyle()
                        .bigText("${task.description}\n\n${task.notes}"))
                }
            }
            .build()

        NotificationManagerCompat.from(context).notify(getNextNotificationId(), notification)
    }

    fun showGoalCheckInReminder(context: Context, goal: Goal) {
        if (!hasNotificationPermission(context)) return

        val streakText = if (goal.currentStreak > 0) {
            " You're on a ${goal.currentStreak}-day streak!"
        } else ""

        val notification = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_GOAL_CHECKINS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Goal Check-in: ${goal.title}")
            .setContentText("Time to check in on your progress!$streakText")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(createMainIntent(context))
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Check In",
                createCheckInGoalIntent(context, goal.id)
            )
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Time to check in on \"${goal.title}\"!$streakText\n" +
                        "Progress: ${goal.progress.toInt()}%"))
            .build()

        NotificationManagerCompat.from(context).notify(getNextNotificationId(), notification)
    }

    fun showDailyDigest(
        context: Context,
        taskCount: Int,
        goalCount: Int,
        urgentTasks: List<Task>,
        goalsNeedingCheckIn: List<Goal>
    ) {
        if (!hasNotificationPermission(context)) return

        val summary = buildString {
            append("You have $taskCount active task(s)")
            if (goalCount > 0) append(" and $goalCount goal(s)")
            append(".")
            if (urgentTasks.isNotEmpty()) {
                append("\n\nUrgent: ${urgentTasks.joinToString(", ") { it.description }}")
            }
            if (goalsNeedingCheckIn.isNotEmpty()) {
                append("\n\nGoals to check in: ${goalsNeedingCheckIn.joinToString(", ") { it.title }}")
            }
        }

        val notification = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_DAILY_DIGEST)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Your Daily Summary")
            .setContentText("$taskCount tasks, $goalCount goals active")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(createMainIntent(context))
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .build()

        NotificationManagerCompat.from(context).notify(getNextNotificationId(), notification)
    }

    fun showMotivationalNudge(context: Context, title: String, message: String) {
        if (!hasNotificationPermission(context)) return

        val notification = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_MOTIVATIONAL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(createMainIntent(context))
            .build()

        NotificationManagerCompat.from(context).notify(getNextNotificationId(), notification)
    }

    fun showLocationReminder(context: Context, task: Task, locationName: String) {
        if (!hasNotificationPermission(context)) return

        val notification = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_LOCATION)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("You're near $locationName")
            .setContentText(task.description)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(createMainIntent(context))
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Done",
                createCompleteTaskIntent(context, task.id)
            )
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Snooze",
                createSnoozeTaskIntent(context, task.id)
            )
            .build()

        NotificationManagerCompat.from(context).notify(getNextNotificationId(), notification)
    }
}
