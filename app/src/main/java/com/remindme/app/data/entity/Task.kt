package com.remindme.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TriggerType {
    TIME,       // Triggered at a specific date/time
    LOCATION,   // Triggered when near a location
    EVENT,      // Triggered when a specific event occurs
    CONTEXT     // Triggered based on user-provided context
}

enum class TaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    SNOOZED,
    CANCELLED
}

enum class Priority {
    LOW,
    MEDIUM,
    HIGH,
    URGENT
}

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = Goal::class,
            parentColumns = ["id"],
            childColumns = ["linkedGoalId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("linkedGoalId")]
)
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val description: String,
    val triggerType: TriggerType = TriggerType.CONTEXT,
    val triggerValue: String? = null,
    val category: String? = null,
    val status: TaskStatus = TaskStatus.PENDING,
    val priority: Priority = Priority.MEDIUM,
    val isRecurring: Boolean = false,
    val recurrencePattern: String? = null,
    val locationName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationRadius: Float? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val dueDate: Long? = null,
    val snoozedUntil: Long? = null,
    val linkedGoalId: Long? = null,
    val notes: String? = null,
    val reminderCount: Int = 0,
    val lastRemindedAt: Long? = null
)
