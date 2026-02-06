package com.remindme.app.llm

import android.content.Context
import com.remindme.app.data.database.AppDatabase
import com.remindme.app.data.entity.*
import com.remindme.app.engine.AutoTagger
import com.remindme.app.engine.ContextMatcher
import com.remindme.app.engine.UserIntent
import com.remindme.app.location.LocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class IntelligentResponse(
    val text: String,
    val intent: UserIntent,
    val confidence: Float,
    val suggestedActions: List<SuggestedAction> = emptyList(),
    val usedLlm: Boolean = false
)

data class SuggestedAction(
    val label: String,
    val actionType: ActionType,
    val payload: String? = null
)

enum class ActionType {
    CREATE_TASK,
    CREATE_GOAL,
    COMPLETE_TASK,
    CHECK_IN_GOAL,
    SNOOZE_TASK,
    VIEW_SUMMARY,
    ADD_MILESTONE,
    NAVIGATE
}

class ConversationIntelligence(private val context: Context) {

    private val modelManager = GemmaModelManager.getInstance(context)
    private val database = AppDatabase.getDatabase(context)
    private val locationManager = LocationManager(context)

    val isLlmAvailable: Boolean
        get() = modelManager.isModelDownloaded()

    val isLlmLoaded: Boolean
        get() = modelManager.isLoaded.value

    val modelInfo get() = modelManager.modelInfo

    suspend fun processWithIntelligence(
        userInput: String,
        activeTasks: List<Task>,
        activeGoals: List<Goal>,
        recentHistory: List<Pair<String, Boolean>> = emptyList()
    ): IntelligentResponse = withContext(Dispatchers.IO) {
        // First: fast local intent detection (always available)
        val localIntent = ContextMatcher.detectIntent(userInput)

        // If LLM is available, try enhanced processing
        if (isLlmAvailable) {
            try {
                val llmResponse = processWithLlm(userInput, localIntent, activeTasks, activeGoals, recentHistory)
                if (llmResponse != null) return@withContext llmResponse
            } catch (_: Exception) {
                // Fall back to local processing
            }
        }

        // Fallback: use local processing
        processLocally(userInput, localIntent, activeTasks, activeGoals)
    }

    private suspend fun processWithLlm(
        userInput: String,
        localIntent: UserIntent,
        activeTasks: List<Task>,
        activeGoals: List<Goal>,
        recentHistory: List<Pair<String, Boolean>>
    ): IntelligentResponse? {
        // Use LLM for contextual response when local intent is ambiguous
        val currentLocation = locationManager.currentLocation.value?.city

        val prompt = SmartPromptEngine.buildContextualResponsePrompt(
            userInput = userInput,
            activeTasks = activeTasks,
            activeGoals = activeGoals,
            currentLocation = currentLocation,
            recentHistory = recentHistory
        )

        val llmText = modelManager.generateResponse(prompt) ?: return null

        val suggestedActions = buildSuggestedActions(localIntent, userInput, activeTasks, activeGoals)

        return IntelligentResponse(
            text = llmText,
            intent = localIntent,
            confidence = 0.85f,
            suggestedActions = suggestedActions,
            usedLlm = true
        )
    }

    private fun processLocally(
        userInput: String,
        intent: UserIntent,
        activeTasks: List<Task>,
        activeGoals: List<Goal>
    ): IntelligentResponse {
        val suggestedActions = buildSuggestedActions(intent, userInput, activeTasks, activeGoals)

        return IntelligentResponse(
            text = "", // Will be filled by ChatViewModel's existing logic
            intent = intent,
            confidence = 0.6f,
            suggestedActions = suggestedActions,
            usedLlm = false
        )
    }

    private fun buildSuggestedActions(
        intent: UserIntent,
        userInput: String,
        activeTasks: List<Task>,
        activeGoals: List<Goal>
    ): List<SuggestedAction> {
        val actions = mutableListOf<SuggestedAction>()
        val parsed = AutoTagger.parseInput(userInput)

        when (intent) {
            UserIntent.ADD_TASK -> {
                actions.add(SuggestedAction("View Tasks", ActionType.NAVIGATE, "tasks"))
            }
            UserIntent.ADD_GOAL -> {
                actions.add(SuggestedAction("View Goals", ActionType.NAVIGATE, "goals"))
                actions.add(SuggestedAction("Add Milestones", ActionType.ADD_MILESTONE))
            }
            UserIntent.GET_SUMMARY -> {
                if (activeTasks.any { it.priority == Priority.URGENT }) {
                    val urgentTask = activeTasks.first { it.priority == Priority.URGENT }
                    actions.add(SuggestedAction(
                        "Complete: ${urgentTask.description.take(30)}",
                        ActionType.COMPLETE_TASK,
                        urgentTask.id.toString()
                    ))
                }
                if (activeGoals.any { it.status == GoalStatus.IN_PROGRESS }) {
                    val goal = activeGoals.first { it.status == GoalStatus.IN_PROGRESS }
                    actions.add(SuggestedAction(
                        "Check in: ${goal.title.take(30)}",
                        ActionType.CHECK_IN_GOAL,
                        goal.id.toString()
                    ))
                }
            }
            UserIntent.GOING_SOMEWHERE -> {
                if (parsed.locationName != null) {
                    actions.add(SuggestedAction("View Tasks", ActionType.NAVIGATE, "tasks"))
                }
            }
            else -> {
                // Generic suggestions
                if (activeTasks.size > 3) {
                    actions.add(SuggestedAction("View Summary", ActionType.VIEW_SUMMARY))
                }
            }
        }

        return actions.take(3)
    }

    suspend fun generateTaskSummary(): String? {
        if (!isLlmAvailable) return null
        val tasks = database.taskDao().getActiveTasksSync()
        if (tasks.isEmpty()) return null

        val prompt = SmartPromptEngine.buildTaskSummaryPrompt(tasks)
        return modelManager.generateResponse(prompt)
    }

    suspend fun generateGoalProgress(goalId: Long): String? {
        if (!isLlmAvailable) return null
        val goal = database.goalDao().getGoalById(goalId) ?: return null
        val milestones = database.milestoneDao().getMilestonesForGoalSync(goalId)

        val prompt = SmartPromptEngine.buildGoalProgressPrompt(goal, milestones)
        return modelManager.generateResponse(prompt)
    }

    suspend fun suggestGoalMilestones(goalId: Long): List<String> {
        if (!isLlmAvailable) return emptyList()
        val goal = database.goalDao().getGoalById(goalId) ?: return emptyList()

        val prompt = SmartPromptEngine.buildGoalSubTaskPrompt(goal)
        val response = modelManager.generateResponse(prompt) ?: return emptyList()

        return response.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { line ->
                // Remove numbering prefixes like "1.", "1)", "- "
                line.replace(Regex("^\\d+[.)\\-]\\s*"), "")
                    .replace(Regex("^[-•]\\s*"), "")
                    .trim()
            }
            .filter { it.isNotBlank() }
    }

    suspend fun suggestSmartSnooze(taskId: Long): String? {
        if (!isLlmAvailable) return null
        val task = database.taskDao().getTaskById(taskId) ?: return null

        val prompt = SmartPromptEngine.buildSmartSnoozePrompt(task, System.currentTimeMillis())
        return modelManager.generateResponse(prompt)
    }

    suspend fun generateReflectionPrompt(goalId: Long): String? {
        if (!isLlmAvailable) return null
        val goal = database.goalDao().getGoalById(goalId) ?: return null

        val daysSinceStart = (System.currentTimeMillis() - goal.createdAt) / (24 * 60 * 60 * 1000)
        val prompt = SmartPromptEngine.buildReflectionPrompt(goal, goal.currentStreak, daysSinceStart)
        return modelManager.generateResponse(prompt)
    }

    suspend fun generateDailyMotivation(): String? {
        if (!isLlmAvailable) return null

        val tasks = database.taskDao().getActiveTasksSync()
        val goals = database.goalDao().getActiveGoalsSync()
        val topGoal = goals.maxByOrNull { it.currentStreak }
        val longestStreak = goals.maxOfOrNull { it.currentStreak } ?: 0

        val prompt = SmartPromptEngine.buildDailyMotivationPrompt(
            activeGoalCount = goals.size,
            activeTaskCount = tasks.size,
            longestStreak = longestStreak,
            topGoal = topGoal
        )
        return modelManager.generateResponse(prompt)
    }

    fun unloadModel() {
        modelManager.unloadModel()
    }

    fun destroy() {
        modelManager.destroy()
    }
}
