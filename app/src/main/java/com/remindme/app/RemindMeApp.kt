package com.remindme.app

import android.app.Application
import com.remindme.app.data.database.AppDatabase
import com.remindme.app.notification.DailyDigestWorker
import com.remindme.app.notification.GoalCheckInWorker
import com.remindme.app.notification.NotificationChannels
import com.remindme.app.notification.TaskReminderWorker

class RemindMeApp : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()

        // Create notification channels
        NotificationChannels.createAllChannels(this)

        // Schedule background workers
        TaskReminderWorker.schedule(this)
        DailyDigestWorker.schedule(this, hourOfDay = 8, minute = 0)
        GoalCheckInWorker.schedule(this, hourOfDay = 20, minute = 0)
    }
}
