package com.remindme.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.remindme.app.data.database.AppDatabase
import com.remindme.app.data.entity.*
import com.remindme.app.engine.AutoTagger
import com.remindme.app.engine.ContextMatcher
import com.remindme.app.engine.UserIntent
import com.remindme.app.llm.ConversationIntelligence
import com.remindme.app.repository.ConversationRepository
import com.remindme.app.repository.GoalRepository
import com.remindme.app.repository.TaskRepository
import com.remindme.app.voice.VoiceInteractionManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ChatMessage(
    val id: Long = 0,
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val relatedTaskIds: List<Long> = emptyList(),
    val relatedGoalIds: List<Long> = emptyList()
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val taskRepository = TaskRepository(database.taskDao(), database.tagDao())
    private val goalRepository = GoalRepository(database.goalDao(), database.milestoneDao())
    private val conversationRepository = ConversationRepository(database.conversationDao())

    val voiceManager = VoiceInteractionManager(application)
    val conversationIntelligence = ConversationIntelligence(application)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _lastResponse = MutableStateFlow<String?>(null)
    val lastResponse: StateFlow<String?> = _lastResponse.asStateFlow()

    init {
        // Load conversation history
        viewModelScope.launch {
            conversationRepository.getAllConversations().collect { conversations ->
                _messages.value = conversations.map { conv ->
                    ChatMessage(
                        id = conv.id,
                        text = conv.message,
                        isFromUser = conv.isFromUser,
                        timestamp = conv.timestamp,
                        relatedTaskIds = conv.relatedTaskIds?.split(",")?.mapNotNull { it.toLongOrNull() } ?: emptyList(),
                        relatedGoalIds = conv.relatedGoalIds?.split(",")?.mapNotNull { it.toLongOrNull() } ?: emptyList()
                    )
                }
            }
        }

        // Add welcome message if no history
        viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            if (_messages.value.isEmpty()) {
                conversationRepository.addAssistantResponse(
                    "👋 Hi! I'm RemindME, your personal reminder assistant.\n\n" +
                    "You can tell me things like:\n" +
                    "• \"Remind me to buy basil at the supermarket\"\n" +
                    "• \"I'm going to Bangalore tomorrow\"\n" +
                    "• \"Start exercise routine from next month\"\n" +
                    "• \"My car service is on August 10\"\n\n" +
                    "How can I help you today?"
                )
            }
        }
    }

    fun processUserInput(input: String, isVoice: Boolean = false) {
        viewModelScope.launch {
            _isProcessing.value = true

            // Save user message
            val msgType = if (isVoice) MessageType.USER_VOICE else MessageType.USER_TEXT
            conversationRepository.addUserMessage(input, msgType)

            // Detect intent
            val intent = ContextMatcher.detectIntent(input)

            // Get active tasks and goals for matching
            val activeTasks = taskRepository.getActiveTasksSync()
            val activeGoals = goalRepository.getActiveGoalsSync()

            // Build tag map for context matching
            val taskTags = mutableMapOf<Long, List<Tag>>()
            for (task in activeTasks) {
                taskTags[task.id] = taskRepository.getTagsForTask(task.id)
            }

            // Match context
            val taskMatches = ContextMatcher.matchTasksToContext(input, activeTasks, taskTags)
            val goalMatches = ContextMatcher.matchGoalsToContext(input, activeGoals)

            // Process based on intent
            var response: String
            val relatedTaskIds = mutableListOf<Long>()
            val relatedGoalIds = mutableListOf<Long>()

            when (intent) {
                UserIntent.ADD_TASK -> {
                    val parsed = AutoTagger.parseInput(input)
                    val taskId = taskRepository.insertTask(
                        Task(
                            description = parsed.description,
                            triggerType = parsed.triggerType,
                            triggerValue = parsed.triggerValue,
                            category = parsed.category,
                            locationName = parsed.locationName,
                            dueDate = parsed.dueDate,
                            priority = parsed.priority
                        )
                    )
                    // Add auto-detected tags
                    for ((tagName, tagType) in parsed.tags) {
                        taskRepository.addTagToTask(taskId, tagName, tagType)
                    }
                    relatedTaskIds.add(taskId)

                    response = buildString {
                        append("✅ Task added: \"${parsed.description}\"\n")
                        if (parsed.tags.isNotEmpty()) {
                            append("🏷️ Tags: ${parsed.tags.joinToString(", ") { it.first }}\n")
                        }
                        if (parsed.locationName != null) {
                            append("📍 Location trigger: ${parsed.locationName}\n")
                        }
                        if (parsed.dueDate != null) {
                            append("📅 Due date set\n")
                        }
                        append("\nI'll remind you at the right time!")
                    }
                }

                UserIntent.ADD_GOAL -> {
                    val parsed = AutoTagger.parseInput(input)
                    val goalCategory = when (parsed.goalCategory?.lowercase()) {
                        "health" -> GoalCategory.FITNESS
                        "fitness" -> GoalCategory.FITNESS
                        "travel" -> GoalCategory.TRAVEL
                        "finance" -> GoalCategory.FINANCIAL
                        "learning" -> GoalCategory.LEARNING
                        "work" -> GoalCategory.CAREER
                        else -> GoalCategory.PERSONAL
                    }
                    val goalId = goalRepository.insertGoal(
                        Goal(
                            title = parsed.description,
                            category = goalCategory,
                            targetDate = parsed.dueDate,
                            status = GoalStatus.NOT_STARTED,
                            reminderEnabled = true
                        )
                    )
                    relatedGoalIds.add(goalId)
                    response = "🎯 Goal created: \"${parsed.description}\"\n" +
                            "Category: ${goalCategory.name.lowercase().replaceFirstChar { it.uppercase() }}\n\n" +
                            "You can add milestones to break this down into steps. " +
                            "I'll check in with you regularly on your progress!"
                }

                UserIntent.GOING_SOMEWHERE -> {
                    // Also check keyword-based matches
                    val parsed = AutoTagger.parseInput(input)
                    val keywordTasks = if (parsed.locationName != null) {
                        taskRepository.findMatchingTasks(
                            listOf(parsed.locationName) + parsed.tags.map { it.first }
                        )
                    } else emptyList()

                    val allMatches = (taskMatches.map { it.task } + keywordTasks).distinctBy { it.id }
                    val enrichedMatches = allMatches.map { task ->
                        val existing = taskMatches.find { it.task.id == task.id }
                        existing ?: com.remindme.app.engine.TaskMatch(task, 0.3f, "keyword match")
                    }

                    response = ContextMatcher.generateResponse(input, intent, enrichedMatches, goalMatches)
                    relatedTaskIds.addAll(enrichedMatches.map { it.task.id })
                    relatedGoalIds.addAll(goalMatches.map { it.goal.id })
                }

                UserIntent.STATUS_UPDATE -> {
                    // Save as a note on matching tasks, or create new task
                    if (taskMatches.isNotEmpty()) {
                        val task = taskMatches.first().task
                        val updatedNotes = buildString {
                            if (task.notes != null) append(task.notes + "\n")
                            append("[${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())}] $input")
                        }
                        taskRepository.updateTask(task.copy(notes = updatedNotes))
                        relatedTaskIds.add(task.id)
                        response = "📝 Noted! Added this update to \"${task.description}\".\n" +
                                "I'll include this in the summary when relevant."
                    } else {
                        // Create a new task as an informational note
                        val parsed = AutoTagger.parseInput(input)
                        val taskId = taskRepository.insertTask(
                            Task(
                                description = parsed.description,
                                triggerType = TriggerType.CONTEXT,
                                category = parsed.category,
                                priority = Priority.LOW,
                                notes = input
                            )
                        )
                        for ((tagName, tagType) in parsed.tags) {
                            taskRepository.addTagToTask(taskId, tagName, tagType)
                        }
                        relatedTaskIds.add(taskId)
                        response = "📝 Got it! I've saved this note and will bring it up when relevant."
                    }
                }

                UserIntent.COMPLETE_TASK -> {
                    if (taskMatches.isNotEmpty()) {
                        val task = taskMatches.first().task
                        taskRepository.completeTask(task.id)
                        relatedTaskIds.add(task.id)
                        response = "🎉 Great job! \"${task.description}\" marked as completed!"
                    } else {
                        response = "Which task did you complete? I couldn't find a matching task."
                    }
                }

                UserIntent.CHECK_IN_GOAL -> {
                    if (goalMatches.isNotEmpty()) {
                        val goal = goalMatches.first().goal
                        goalRepository.checkInGoal(goal.id)
                        relatedGoalIds.add(goal.id)
                        response = "🔥 Checked in for \"${goal.title}\"! Streak: ${goal.currentStreak + 1} days!\n" +
                                "Keep it up! Progress: ${goal.progress.toInt()}%"
                    } else {
                        response = "Which goal are you checking in for?"
                    }
                }

                UserIntent.GET_SUMMARY -> {
                    val allActiveTasks = taskRepository.getActiveTasksSync()
                    val allActiveGoals = goalRepository.getActiveGoalsSync()

                    // Try LLM-powered summary first
                    val llmSummary = try {
                        conversationIntelligence.generateTaskSummary()
                    } catch (_: Exception) { null }

                    response = if (llmSummary != null) {
                        "📊 **AI Summary**\n\n$llmSummary"
                    } else {
                        buildString {
                            append("📊 Your RemindME Summary\n\n")
                            if (allActiveTasks.isNotEmpty()) {
                                append("📋 ${allActiveTasks.size} pending task(s):\n")
                                allActiveTasks.take(10).forEachIndexed { i, task ->
                                    val icon = when (task.priority) {
                                        Priority.URGENT -> "🔴"
                                        Priority.HIGH -> "🟠"
                                        Priority.MEDIUM -> "🟡"
                                        Priority.LOW -> "⚪"
                                    }
                                    append("$icon ${i + 1}. ${task.description}\n")
                                }
                                if (allActiveTasks.size > 10) {
                                    append("...and ${allActiveTasks.size - 10} more\n")
                                }
                            } else {
                                append("📋 No pending tasks!\n")
                            }
                            append("\n")
                            if (allActiveGoals.isNotEmpty()) {
                                append("🎯 ${allActiveGoals.size} active goal(s):\n")
                                allActiveGoals.forEachIndexed { i, goal ->
                                    val statusIcon = when (goal.status) {
                                        GoalStatus.IN_PROGRESS -> "🟢"
                                        GoalStatus.NOT_STARTED -> "⬜"
                                        GoalStatus.ON_HOLD -> "⏸️"
                                        else -> "✅"
                                    }
                                    append("$statusIcon ${i + 1}. ${goal.title} - ${goal.progress.toInt()}%")
                                    if (goal.currentStreak > 0) append(" 🔥${goal.currentStreak}")
                                    append("\n")
                                }
                            } else {
                                append("🎯 No active goals.\n")
                            }
                        }
                    }
                }

                else -> {
                    // Try to determine if it's more like a task or just conversation
                    val parsed = AutoTagger.parseInput(input)
                    if (parsed.tags.isNotEmpty() || parsed.locationName != null || parsed.dueDate != null) {
                        // Looks like a task - add it
                        val taskId = taskRepository.insertTask(
                            Task(
                                description = parsed.description,
                                triggerType = parsed.triggerType,
                                triggerValue = parsed.triggerValue,
                                category = parsed.category,
                                locationName = parsed.locationName,
                                dueDate = parsed.dueDate,
                                priority = parsed.priority
                            )
                        )
                        for ((tagName, tagType) in parsed.tags) {
                            taskRepository.addTagToTask(taskId, tagName, tagType)
                        }
                        relatedTaskIds.add(taskId)
                        response = "✅ Saved: \"${parsed.description}\"\n"
                        if (parsed.tags.isNotEmpty()) {
                            response += "🏷️ Tags: ${parsed.tags.joinToString(", ") { it.first }}\n"
                        }
                        response += "I'll remind you when the time is right!"
                    } else if (taskMatches.isNotEmpty() || goalMatches.isNotEmpty()) {
                        response = ContextMatcher.generateResponse(input, UserIntent.GET_SUMMARY, taskMatches, goalMatches)
                    } else {
                        // Try LLM for conversational response
                        val llmResponse = try {
                            conversationIntelligence.processWithIntelligence(input, activeTasks, activeGoals)
                        } catch (_: Exception) { null }

                        response = if (llmResponse != null && llmResponse.usedLlm && llmResponse.text.isNotBlank()) {
                            llmResponse.text
                        } else {
                            "Got it! I've noted that. You can ask me for a summary anytime, " +
                                    "or tell me about tasks you need reminders for."
                        }
                    }
                }
            }

            // Save assistant response
            conversationRepository.addAssistantResponse(
                message = response,
                relatedTaskIds = relatedTaskIds.joinToString(","),
                relatedGoalIds = relatedGoalIds.joinToString(",")
            )

            _lastResponse.value = response
            _isProcessing.value = false
        }
    }

    fun initializeVoice() {
        voiceManager.initialize()
    }

    fun startVoiceInput() {
        voiceManager.startListening { recognizedText ->
            processUserInput(recognizedText, isVoice = true)
        }
    }

    fun stopVoiceInput() {
        voiceManager.stopListening()
    }

    fun cancelVoiceInput() {
        voiceManager.cancelListening()
    }

    fun speakLastResponse() {
        _lastResponse.value?.let { response ->
            voiceManager.speakResponse(response)
        }
    }

    fun stopSpeaking() {
        voiceManager.stopSpeaking()
    }

    fun clearHistory() {
        viewModelScope.launch {
            conversationRepository.clearHistory()
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.destroy()
        conversationIntelligence.destroy()
    }
}
