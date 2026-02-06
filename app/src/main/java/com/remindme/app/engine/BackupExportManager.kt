package com.remindme.app.engine

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.remindme.app.data.database.AppDatabase
import com.remindme.app.data.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

data class BackupData(
    val version: Int = 1,
    val appName: String = "RemindME",
    val exportDate: Long = System.currentTimeMillis(),
    val tasks: List<Task> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val taskTags: List<TaskTag> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val milestones: List<Milestone> = emptyList(),
    val savedLocations: List<SavedLocation> = emptyList(),
    val conversations: List<Conversation> = emptyList()
)

class BackupExportManager(private val context: Context) {

    private val database = AppDatabase.getDatabase(context)
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    // --- JSON Backup/Restore ---

    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        val backup = BackupData(
            tasks = database.taskDao().getAllTasksSync(),
            tags = database.tagDao().getAllTagsSync(),
            taskTags = database.tagDao().getAllTaskTagsSync(),
            goals = database.goalDao().getAllGoalsSync(),
            milestones = database.milestoneDao().getAllMilestonesSync(),
            savedLocations = database.savedLocationDao().getActiveLocationsSync(),
            conversations = database.conversationDao().getAllConversationsSync()
        )
        gson.toJson(backup)
    }

    suspend fun exportBackupToUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = exportBackupJson()
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(json.toByteArray(Charsets.UTF_8))
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun importBackupFromJson(json: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val backup = gson.fromJson(json, BackupData::class.java)
            if (backup.appName != "RemindME") return@withContext false

            // Clear existing data
            database.clearAllTables()

            // Restore in order (respect foreign keys)
            for (tag in backup.tags) database.tagDao().insertTag(tag)
            for (task in backup.tasks) database.taskDao().insertTask(task)
            for (taskTag in backup.taskTags) database.tagDao().insertTaskTag(taskTag)
            for (goal in backup.goals) database.goalDao().insertGoal(goal)
            for (milestone in backup.milestones) database.milestoneDao().insertMilestone(milestone)
            for (location in backup.savedLocations) database.savedLocationDao().insertLocation(location)
            for (conversation in backup.conversations) database.conversationDao().insertConversation(conversation)

            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun importBackupFromUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().readText()
            } ?: return@withContext false
            importBackupFromJson(json)
        } catch (e: Exception) {
            false
        }
    }

    // --- CSV Export ---

    suspend fun exportTasksCsv(): String = withContext(Dispatchers.IO) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val tasks = database.taskDao().getAllTasksSync()

        buildString {
            appendLine("ID,Description,Status,Priority,Trigger Type,Location,Category,Due Date,Created,Completed,Notes")
            for (task in tasks) {
                val dueDate = task.dueDate?.let { dateFormat.format(Date(it)) } ?: ""
                val created = dateFormat.format(Date(task.createdAt))
                val completed = task.completedAt?.let { dateFormat.format(Date(it)) } ?: ""
                appendLine(
                    "${task.id}," +
                    "\"${task.description.replace("\"", "\"\"")}\"," +
                    "${task.status}," +
                    "${task.priority}," +
                    "${task.triggerType}," +
                    "\"${task.locationName?.replace("\"", "\"\"") ?: ""}\"," +
                    "\"${task.category?.replace("\"", "\"\"") ?: ""}\"," +
                    "$dueDate," +
                    "$created," +
                    "$completed," +
                    "\"${task.notes?.replace("\"", "\"\"")?.replace("\n", " ") ?: ""}\""
                )
            }
        }
    }

    suspend fun exportGoalsCsv(): String = withContext(Dispatchers.IO) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val goals = database.goalDao().getAllGoalsSync()

        buildString {
            appendLine("ID,Title,Description,Category,Status,Progress,Streak,Best Streak,Check-in Frequency,Target Date,Created,Notes")
            for (goal in goals) {
                val targetDate = goal.targetDate?.let { dateFormat.format(Date(it)) } ?: ""
                val created = dateFormat.format(Date(goal.createdAt))
                appendLine(
                    "${goal.id}," +
                    "\"${goal.title.replace("\"", "\"\"")}\"," +
                    "\"${goal.description?.replace("\"", "\"\"") ?: ""}\"," +
                    "${goal.category}," +
                    "${goal.status}," +
                    "${goal.progress}," +
                    "${goal.currentStreak}," +
                    "${goal.bestStreak}," +
                    "${goal.checkInFrequency}," +
                    "$targetDate," +
                    "$created," +
                    "\"${goal.notes?.replace("\"", "\"\"")?.replace("\n", " ") ?: ""}\""
                )
            }
        }
    }

    suspend fun exportTasksCsvToUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val csv = exportTasksCsv()
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(csv.toByteArray(Charsets.UTF_8))
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun exportGoalsCsvToUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val csv = exportGoalsCsv()
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(csv.toByteArray(Charsets.UTF_8))
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getBackupFileName(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return "RemindME_backup_${dateFormat.format(Date())}.json"
    }

    fun getTasksCsvFileName(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        return "RemindME_tasks_${dateFormat.format(Date())}.csv"
    }

    fun getGoalsCsvFileName(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        return "RemindME_goals_${dateFormat.format(Date())}.csv"
    }
}
