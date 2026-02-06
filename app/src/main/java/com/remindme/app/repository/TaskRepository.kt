package com.remindme.app.repository

import com.remindme.app.data.dao.TagDao
import com.remindme.app.data.dao.TaskDao
import com.remindme.app.data.entity.*
import kotlinx.coroutines.flow.Flow

class TaskRepository(
    private val taskDao: TaskDao,
    private val tagDao: TagDao
) {
    fun getAllTasks(): Flow<List<Task>> = taskDao.getAllTasks()
    fun getActiveTasks(): Flow<List<Task>> = taskDao.getActiveTasks()
    suspend fun getActiveTasksSync(): List<Task> = taskDao.getActiveTasksSync()
    suspend fun getTaskById(id: Long): Task? = taskDao.getTaskById(id)
    fun getTasksByStatus(status: TaskStatus): Flow<List<Task>> = taskDao.getTasksByStatus(status)
    fun getTasksByTriggerType(type: TriggerType): Flow<List<Task>> = taskDao.getTasksByTriggerType(type)
    fun getTasksForGoal(goalId: Long): Flow<List<Task>> = taskDao.getTasksForGoal(goalId)
    fun getTasksByCategory(category: String): Flow<List<Task>> = taskDao.getTasksByCategory(category)
    suspend fun searchTasks(keyword: String): List<Task> = taskDao.searchTasks(keyword)
    fun getTasksDueBetween(start: Long, end: Long): Flow<List<Task>> = taskDao.getTasksDueBetween(start, end)
    fun getActiveTaskCount(): Flow<Int> = taskDao.getActiveTaskCount()
    fun getCompletedTaskCount(): Flow<Int> = taskDao.getCompletedTaskCount()

    suspend fun insertTask(task: Task): Long {
        return taskDao.insertTask(task)
    }

    suspend fun updateTask(task: Task) {
        taskDao.updateTask(task.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteTask(task: Task) {
        tagDao.deleteTagsForTask(task.id)
        taskDao.deleteTask(task)
    }

    suspend fun completeTask(taskId: Long) {
        taskDao.completeTask(taskId)
    }

    suspend fun snoozeTask(taskId: Long, until: Long) {
        taskDao.snoozeTask(taskId, until)
    }

    suspend fun reactivateTask(taskId: Long) {
        taskDao.updateTaskStatus(taskId, TaskStatus.PENDING)
    }

    // Tag operations for tasks
    suspend fun addTagToTask(taskId: Long, tagName: String, tagType: TagType = TagType.CUSTOM): Long {
        var tag = tagDao.getTagByNameIgnoreCase(tagName)
        if (tag == null) {
            val tagId = tagDao.insertTag(Tag(name = tagName.lowercase(), type = tagType))
            tag = Tag(id = tagId, name = tagName.lowercase(), type = tagType)
        }
        tagDao.insertTaskTag(TaskTag(taskId = taskId, tagId = tag.id))
        return tag.id
    }

    suspend fun getTagsForTask(taskId: Long): List<Tag> = tagDao.getTagsForTaskSync(taskId)

    fun getTagsForTaskFlow(taskId: Long): Flow<List<Tag>> = tagDao.getTagsForTask(taskId)

    suspend fun getActiveTasksForTag(tagId: Long): List<Task> = tagDao.getActiveTasksForTag(tagId)

    // Find tasks matching keywords (for context matching)
    suspend fun findMatchingTasks(keywords: List<String>): List<Task> {
        val matchingTasks = mutableSetOf<Task>()
        for (keyword in keywords) {
            matchingTasks.addAll(taskDao.searchTasks(keyword))
            // Also search by tags
            val tag = tagDao.getTagByNameIgnoreCase(keyword)
            if (tag != null) {
                matchingTasks.addAll(tagDao.getActiveTasksForTag(tag.id))
            }
        }
        return matchingTasks.toList()
    }
}
