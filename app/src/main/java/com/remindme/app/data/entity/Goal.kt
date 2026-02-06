package com.remindme.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class GoalCategory {
    FITNESS,
    TRAVEL,
    FINANCIAL,
    LEARNING,
    CAREER,
    PERSONAL,
    HEALTH
}

enum class GoalStatus {
    NOT_STARTED,
    IN_PROGRESS,
    ON_HOLD,
    COMPLETED,
    ABANDONED
}

enum class CheckInFrequency {
    DAILY,
    WEEKLY,
    BIWEEKLY,
    MONTHLY
}

@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String? = null,
    val category: GoalCategory = GoalCategory.PERSONAL,
    val status: GoalStatus = GoalStatus.NOT_STARTED,
    val targetDate: Long? = null,
    val startDate: Long? = null,
    val progress: Float = 0f,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val lastCheckedIn: Long? = null,
    val checkInFrequency: CheckInFrequency = CheckInFrequency.WEEKLY,
    val reminderEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val notes: String? = null
)
