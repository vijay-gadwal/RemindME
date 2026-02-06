package com.remindme.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "milestones",
    foreignKeys = [
        ForeignKey(
            entity = Goal::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("goalId")]
)
data class Milestone(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val title: String,
    val description: String? = null,
    val targetDate: Long? = null,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val orderIndex: Int = 0,
    val dependsOnMilestoneId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
