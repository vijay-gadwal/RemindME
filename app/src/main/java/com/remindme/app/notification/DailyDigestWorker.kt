package com.remindme.app.notification

import android.content.Context
import androidx.work.*
import com.remindme.app.data.database.AppDatabase
import com.remindme.app.data.entity.Priority
import java.util.Calendar
import java.util.concurrent.TimeUnit

class DailyDigestWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)

        val activeTasks = database.taskDao().getActiveTasksSync()
        val activeGoals = database.goalDao().getActiveGoalsSync()
        val urgentTasks = activeTasks.filter { it.priority == Priority.URGENT || it.priority == Priority.HIGH }

        // Find goals needing check-in (not checked in today)
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val goalsNeedingCheckIn = database.goalDao().getGoalsNeedingCheckIn(todayStart)

        if (activeTasks.isNotEmpty() || activeGoals.isNotEmpty()) {
            NotificationHelper.showDailyDigest(
                applicationContext,
                taskCount = activeTasks.size,
                goalCount = activeGoals.size,
                urgentTasks = urgentTasks.take(3),
                goalsNeedingCheckIn = goalsNeedingCheckIn.take(3)
            )
        }

        return Result.success()
    }

    companion object {
        const val WORK_NAME = "daily_digest"

        fun schedule(context: Context, hourOfDay: Int = 8, minute: Int = 0) {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hourOfDay)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(now)) {
                    add(Calendar.DAY_OF_MONTH, 1)
                }
            }

            val delay = target.timeInMillis - now.timeInMillis

            val request = PeriodicWorkRequestBuilder<DailyDigestWorker>(
                1, TimeUnit.DAYS
            )
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
