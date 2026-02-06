package com.remindme.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri

object NotificationChannels {

    const val CHANNEL_TASK_REMINDERS = "task_reminders"
    const val CHANNEL_GOAL_CHECKINS = "goal_checkins"
    const val CHANNEL_DAILY_DIGEST = "daily_digest"
    const val CHANNEL_MOTIVATIONAL = "motivational_nudges"
    const val CHANNEL_LOCATION = "location_reminders"

    fun createAllChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channels = listOf(
            NotificationChannel(
                CHANNEL_TASK_REMINDERS,
                "Task Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for task reminders based on time, location, or context"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 100, 250)
            },

            NotificationChannel(
                CHANNEL_GOAL_CHECKINS,
                "Goal Check-ins",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders to check in on your goals and track progress"
                enableVibration(true)
            },

            NotificationChannel(
                CHANNEL_DAILY_DIGEST,
                "Daily Digest",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily summary of your tasks, goals, and upcoming reminders"
            },

            NotificationChannel(
                CHANNEL_MOTIVATIONAL,
                "Motivational Nudges",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Streak updates, progress milestones, and encouragement"
            },

            NotificationChannel(
                CHANNEL_LOCATION,
                "Location Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders triggered when you arrive at or near a location"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 150, 300)
            }
        )

        channels.forEach { manager.createNotificationChannel(it) }
    }
}
