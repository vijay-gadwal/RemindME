package com.remindme.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Reschedule all periodic workers after device reboot
            NotificationChannels.createAllChannels(context)
            TaskReminderWorker.schedule(context)
            DailyDigestWorker.schedule(context)
            GoalCheckInWorker.schedule(context)
        }
    }
}
