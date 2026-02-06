package com.remindme.app.notification

import android.content.Context
import androidx.work.*
import com.remindme.app.data.database.AppDatabase
import com.remindme.app.data.entity.TaskStatus
import com.remindme.app.data.entity.TriggerType
import java.util.concurrent.TimeUnit

class TaskReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)

        val now = System.currentTimeMillis()
        val windowEnd = now + (15 * 60 * 1000) // 15-minute window

        // Find time-based tasks due within the next 15 minutes
        val activeTasks = database.taskDao().getActiveTasksSync()

        for (task in activeTasks) {
            if (task.triggerType == TriggerType.TIME && task.dueDate != null) {
                if (task.dueDate in now..windowEnd) {
                    NotificationHelper.showTaskReminder(applicationContext, task)
                }
            }

            // Check snoozed tasks that should wake up
            if (task.status == TaskStatus.SNOOZED && task.snoozedUntil != null) {
                if (task.snoozedUntil <= now) {
                    database.taskDao().updateTaskStatus(task.id, TaskStatus.PENDING)
                    NotificationHelper.showTaskReminder(applicationContext, task)
                }
            }
        }

        return Result.success()
    }

    companion object {
        const val WORK_NAME = "task_reminder_check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<TaskReminderWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
