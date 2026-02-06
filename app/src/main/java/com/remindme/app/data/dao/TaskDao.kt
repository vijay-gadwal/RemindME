package com.remindme.app.data.dao

import androidx.room.*
import com.remindme.app.data.entity.Task
import com.remindme.app.data.entity.TaskStatus
import com.remindme.app.data.entity.TriggerType
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY priority DESC, createdAt DESC")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks ORDER BY priority DESC, createdAt DESC")
    suspend fun getAllTasksSync(): List<Task>

    @Query("SELECT * FROM tasks WHERE status = :status ORDER BY priority DESC, createdAt DESC")
    fun getTasksByStatus(status: TaskStatus): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE status IN ('PENDING', 'IN_PROGRESS', 'SNOOZED') ORDER BY priority DESC, createdAt DESC")
    fun getActiveTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE status IN ('PENDING', 'IN_PROGRESS', 'SNOOZED') ORDER BY priority DESC, createdAt DESC")
    suspend fun getActiveTasksSync(): List<Task>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): Task?

    @Query("SELECT * FROM tasks WHERE triggerType = :triggerType AND status IN ('PENDING', 'IN_PROGRESS')")
    fun getTasksByTriggerType(triggerType: TriggerType): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE linkedGoalId = :goalId ORDER BY createdAt DESC")
    fun getTasksForGoal(goalId: Long): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE category = :category AND status IN ('PENDING', 'IN_PROGRESS')")
    fun getTasksByCategory(category: String): Flow<List<Task>>

    @Query("""
        SELECT * FROM tasks 
        WHERE status IN ('PENDING', 'IN_PROGRESS') 
        AND (description LIKE '%' || :keyword || '%' 
             OR category LIKE '%' || :keyword || '%'
             OR locationName LIKE '%' || :keyword || '%'
             OR notes LIKE '%' || :keyword || '%')
        ORDER BY priority DESC
    """)
    suspend fun searchTasks(keyword: String): List<Task>

    @Query("""
        SELECT * FROM tasks 
        WHERE status IN ('PENDING', 'IN_PROGRESS') 
        AND dueDate IS NOT NULL 
        AND dueDate BETWEEN :startTime AND :endTime
        ORDER BY dueDate ASC
    """)
    fun getTasksDueBetween(startTime: Long, endTime: Long): Flow<List<Task>>

    @Query("""
        SELECT * FROM tasks 
        WHERE status = 'SNOOZED' 
        AND snoozedUntil IS NOT NULL 
        AND snoozedUntil <= :currentTime
    """)
    suspend fun getExpiredSnoozedTasks(currentTime: Long): List<Task>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long

    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("UPDATE tasks SET status = :status, updatedAt = :now WHERE id = :taskId")
    suspend fun updateTaskStatus(taskId: Long, status: TaskStatus, now: Long = System.currentTimeMillis())

    @Query("UPDATE tasks SET status = 'COMPLETED', completedAt = :now, updatedAt = :now WHERE id = :taskId")
    suspend fun completeTask(taskId: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE tasks SET status = 'SNOOZED', snoozedUntil = :until, updatedAt = :now WHERE id = :taskId")
    suspend fun snoozeTask(taskId: Long, until: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE tasks SET reminderCount = reminderCount + 1, lastRemindedAt = :now WHERE id = :taskId")
    suspend fun incrementReminderCount(taskId: Long, now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM tasks WHERE status IN ('PENDING', 'IN_PROGRESS')")
    fun getActiveTaskCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tasks WHERE status = 'COMPLETED'")
    fun getCompletedTaskCount(): Flow<Int>
}
