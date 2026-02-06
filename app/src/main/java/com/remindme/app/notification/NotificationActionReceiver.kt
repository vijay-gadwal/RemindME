package com.remindme.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.remindme.app.data.database.AppDatabase
import com.remindme.app.data.entity.TaskStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_COMPLETE_TASK = "com.remindme.app.ACTION_COMPLETE_TASK"
        const val ACTION_SNOOZE_TASK = "com.remindme.app.ACTION_SNOOZE_TASK"
        const val ACTION_CHECK_IN_GOAL = "com.remindme.app.ACTION_CHECK_IN_GOAL"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_GOAL_ID = "extra_goal_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val database = AppDatabase.getDatabase(context)
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_COMPLETE_TASK -> {
                        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1)
                        if (taskId != -1L) {
                            database.taskDao().completeTask(taskId)
                        }
                    }

                    ACTION_SNOOZE_TASK -> {
                        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1)
                        if (taskId != -1L) {
                            val snoozeUntil = System.currentTimeMillis() + (60 * 60 * 1000) // 1 hour
                            database.taskDao().snoozeTask(taskId, snoozeUntil)
                        }
                    }

                    ACTION_CHECK_IN_GOAL -> {
                        val goalId = intent.getLongExtra(EXTRA_GOAL_ID, -1)
                        if (goalId != -1L) {
                            val goal = database.goalDao().getGoalById(goalId)
                            if (goal != null) {
                                val newStreak = goal.currentStreak + 1
                                database.goalDao().checkInGoal(goalId, newStreak)

                                // Show motivational feedback
                                val message = when {
                                    newStreak >= 30 -> "Incredible! ${newStreak}-day streak! You're unstoppable!"
                                    newStreak >= 14 -> "Amazing! ${newStreak} days in a row! Keep crushing it!"
                                    newStreak >= 7 -> "One week streak! You're building a great habit!"
                                    newStreak >= 3 -> "${newStreak}-day streak! You're on a roll!"
                                    else -> "Checked in! Day $newStreak. Consistency is key!"
                                }
                                NotificationHelper.showMotivationalNudge(
                                    context,
                                    "Goal: ${goal.title}",
                                    message
                                )
                            }
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
