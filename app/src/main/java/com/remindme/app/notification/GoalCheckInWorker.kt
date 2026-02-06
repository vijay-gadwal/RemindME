package com.remindme.app.notification

import android.content.Context
import androidx.work.*
import com.remindme.app.data.database.AppDatabase
import com.remindme.app.data.entity.GoalStatus
import java.util.Calendar
import java.util.concurrent.TimeUnit

class GoalCheckInWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)

        // Find goals that need check-in reminders
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val goalsNeedingCheckIn = database.goalDao().getGoalsNeedingCheckIn(todayStart)

        for (goal in goalsNeedingCheckIn) {
            if (goal.reminderEnabled && goal.status == GoalStatus.IN_PROGRESS) {
                NotificationHelper.showGoalCheckInReminder(applicationContext, goal)
            }
        }

        // Check for streak milestones and send motivational nudges
        val activeGoals = database.goalDao().getActiveGoalsSync()
        for (goal in activeGoals) {
            val streak = goal.currentStreak
            when {
                streak == 7 -> NotificationHelper.showMotivationalNudge(
                    applicationContext,
                    "1 Week Milestone!",
                    "You've been consistent with \"${goal.title}\" for a whole week! Keep going!"
                )
                streak == 14 -> NotificationHelper.showMotivationalNudge(
                    applicationContext,
                    "2 Week Milestone!",
                    "Two weeks of dedication to \"${goal.title}\"! You're building a real habit!"
                )
                streak == 30 -> NotificationHelper.showMotivationalNudge(
                    applicationContext,
                    "30 Day Milestone!",
                    "A full month of \"${goal.title}\"! This is now part of who you are!"
                )
                streak == 100 -> NotificationHelper.showMotivationalNudge(
                    applicationContext,
                    "100 Day Milestone!",
                    "100 days of \"${goal.title}\"! You are truly extraordinary!"
                )
            }

            // Check if goal is approaching target date
            if (goal.targetDate != null) {
                val daysLeft = ((goal.targetDate - System.currentTimeMillis()) / (24 * 60 * 60 * 1000)).toInt()
                when (daysLeft) {
                    7 -> NotificationHelper.showMotivationalNudge(
                        applicationContext,
                        "1 Week Left",
                        "\"${goal.title}\" target date is in 1 week. Progress: ${goal.progress.toInt()}%"
                    )
                    1 -> NotificationHelper.showMotivationalNudge(
                        applicationContext,
                        "Tomorrow is the day!",
                        "\"${goal.title}\" target is tomorrow. You're at ${goal.progress.toInt()}%. Final push!"
                    )
                    0 -> NotificationHelper.showMotivationalNudge(
                        applicationContext,
                        "Goal Target Today",
                        "Today's the target date for \"${goal.title}\"! Progress: ${goal.progress.toInt()}%"
                    )
                }
            }
        }

        return Result.success()
    }

    companion object {
        const val WORK_NAME = "goal_checkin"

        fun schedule(context: Context, hourOfDay: Int = 20, minute: Int = 0) {
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

            val request = PeriodicWorkRequestBuilder<GoalCheckInWorker>(
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
