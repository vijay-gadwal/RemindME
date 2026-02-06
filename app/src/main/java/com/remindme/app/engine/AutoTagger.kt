package com.remindme.app.engine

import com.remindme.app.data.entity.TagType
import com.remindme.app.data.entity.TriggerType
import com.remindme.app.data.entity.Priority
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

data class ParsedTask(
    val description: String,
    val triggerType: TriggerType,
    val triggerValue: String? = null,
    val category: String? = null,
    val locationName: String? = null,
    val dueDate: Long? = null,
    val priority: Priority = Priority.MEDIUM,
    val tags: List<Pair<String, TagType>> = emptyList(),
    val isGoalRelated: Boolean = false,
    val goalCategory: String? = null
)

object AutoTagger {
    // Known location keywords
    private val locationKeywords = mapOf(
        "supermarket" to TagType.LOCATION,
        "grocery" to TagType.LOCATION,
        "store" to TagType.LOCATION,
        "shop" to TagType.LOCATION,
        "mall" to TagType.LOCATION,
        "market" to TagType.LOCATION,
        "nursery" to TagType.LOCATION,
        "garden center" to TagType.LOCATION,
        "hospital" to TagType.LOCATION,
        "clinic" to TagType.LOCATION,
        "pharmacy" to TagType.LOCATION,
        "bank" to TagType.LOCATION,
        "atm" to TagType.LOCATION,
        "restaurant" to TagType.LOCATION,
        "cafe" to TagType.LOCATION,
        "gym" to TagType.LOCATION,
        "metro" to TagType.LOCATION,
        "station" to TagType.LOCATION,
        "airport" to TagType.LOCATION,
        "office" to TagType.LOCATION,
        "school" to TagType.LOCATION,
        "college" to TagType.LOCATION,
        "university" to TagType.LOCATION,
        "temple" to TagType.LOCATION,
        "church" to TagType.LOCATION,
        "mosque" to TagType.LOCATION,
        "park" to TagType.LOCATION,
        "hotel" to TagType.LOCATION,
        "petrol" to TagType.LOCATION,
        "gas station" to TagType.LOCATION
    )

    // Known city names (can be expanded)
    private val cityKeywords = setOf(
        "bangalore", "bengaluru", "mumbai", "delhi", "chennai", "hyderabad",
        "kolkata", "pune", "ahmedabad", "jaipur", "lucknow", "kochi",
        "goa", "mysore", "mangalore", "coimbatore", "chandigarh",
        "new york", "london", "tokyo", "dubai", "singapore",
        "jayanagar", "koramangala", "indiranagar", "whitefield", "hsr layout"
    )

    // Category keywords
    private val categoryKeywords = mapOf(
        "car" to "vehicle",
        "vehicle" to "vehicle",
        "bike" to "vehicle",
        "service" to "vehicle",
        "brake" to "vehicle",
        "engine" to "vehicle",
        "tire" to "vehicle",
        "tyre" to "vehicle",
        "fuel" to "vehicle",
        "petrol" to "vehicle",
        "diesel" to "vehicle",
        "buy" to "shopping",
        "purchase" to "shopping",
        "get" to "shopping",
        "order" to "shopping",
        "food" to "food",
        "restaurant" to "food",
        "eat" to "food",
        "cook" to "food",
        "recipe" to "food",
        "doctor" to "health",
        "medicine" to "health",
        "hospital" to "health",
        "health" to "health",
        "fitness" to "health",
        "exercise" to "health",
        "workout" to "health",
        "gym" to "health",
        "run" to "health",
        "yoga" to "health",
        "pay" to "finance",
        "bill" to "finance",
        "rent" to "finance",
        "insurance" to "finance",
        "tax" to "finance",
        "investment" to "finance",
        "call" to "communication",
        "email" to "communication",
        "meet" to "communication",
        "meeting" to "work",
        "deadline" to "work",
        "project" to "work",
        "travel" to "travel",
        "trip" to "travel",
        "vacation" to "travel",
        "holiday" to "travel",
        "flight" to "travel",
        "hotel" to "travel",
        "booking" to "travel",
        "passport" to "travel",
        "visa" to "travel",
        "learn" to "learning",
        "study" to "learning",
        "course" to "learning",
        "read" to "learning",
        "book" to "learning",
        "plant" to "home",
        "garden" to "home",
        "clean" to "home",
        "repair" to "home",
        "fix" to "home",
        "compost" to "home"
    )

    // Goal-related keywords
    private val goalKeywords = setOf(
        "start", "begin", "plan", "goal", "routine", "habit",
        "from next", "onwards", "long term", "this year", "this month",
        "resolution", "target", "achieve", "improve", "build"
    )

    // Time-related patterns
    private val timePatterns = listOf(
        Pattern.compile("(?i)(january|february|march|april|may|june|july|august|september|october|november|december)\\s+(\\d{1,2})(?:,?\\s+(\\d{4}))?"),
        Pattern.compile("(?i)(\\d{1,2})\\s+(january|february|march|april|may|june|july|august|september|october|november|december)(?:,?\\s+(\\d{4}))?"),
        Pattern.compile("(?i)(tomorrow|today|next week|next month|next year)"),
        Pattern.compile("(?i)in\\s+(\\d+)\\s+(day|days|week|weeks|month|months)")
    )

    // Location trigger patterns
    private val locationTriggerPatterns = listOf(
        Pattern.compile("(?i)when\\s+(?:i\\s+)?(?:go|going|visit|travel|am)\\s+(?:to|near|at)\\s+(.+?)(?:\\s+next|\\s+time|\$)"),
        Pattern.compile("(?i)(?:at|in|near)\\s+(?:a\\s+|the\\s+)?(.+?)(?:\\s+next|\\s+time|\$)"),
        Pattern.compile("(?i)(?:go|going)\\s+(?:to|near)\\s+(.+?)(?:\\s+next|\$)")
    )

    // Priority keywords
    private val urgentKeywords = setOf("urgent", "asap", "immediately", "critical", "emergency")
    private val highKeywords = setOf("important", "high priority", "soon", "quickly")
    private val lowKeywords = setOf("someday", "whenever", "no rush", "low priority", "eventually")

    fun parseInput(input: String): ParsedTask {
        val lowerInput = input.lowercase().trim()
        val tags = mutableListOf<Pair<String, TagType>>()
        var triggerType = TriggerType.CONTEXT
        var triggerValue: String? = null
        var category: String? = null
        var locationName: String? = null
        var dueDate: Long? = null
        var isGoalRelated = false
        var goalCategory: String? = null

        // Detect priority
        var priority = when {
            urgentKeywords.any { lowerInput.contains(it) } -> Priority.URGENT
            highKeywords.any { lowerInput.contains(it) } -> Priority.HIGH
            lowKeywords.any { lowerInput.contains(it) } -> Priority.LOW
            else -> Priority.MEDIUM
        }

        // Detect dates
        for (pattern in timePatterns) {
            val matcher = pattern.matcher(lowerInput)
            if (matcher.find()) {
                triggerType = TriggerType.TIME
                triggerValue = matcher.group()
                dueDate = parseDateFromMatch(matcher.group())
                break
            }
        }

        // Detect location triggers
        for (pattern in locationTriggerPatterns) {
            val matcher = pattern.matcher(lowerInput)
            if (matcher.find()) {
                val location = matcher.group(1)?.trim()
                if (location != null) {
                    triggerType = TriggerType.LOCATION
                    locationName = location
                    tags.add(location to TagType.LOCATION)
                    break
                }
            }
        }

        // Detect city names
        for (city in cityKeywords) {
            if (lowerInput.contains(city)) {
                tags.add(city to TagType.LOCATION)
                if (triggerType == TriggerType.CONTEXT) {
                    triggerType = TriggerType.LOCATION
                    locationName = city
                }
            }
        }

        // Detect location types
        for ((keyword, tagType) in locationKeywords) {
            if (lowerInput.contains(keyword)) {
                tags.add(keyword to tagType)
                if (locationName == null) {
                    locationName = keyword
                }
            }
        }

        // Detect categories
        val words = lowerInput.split("\\s+".toRegex())
        for (word in words) {
            val cat = categoryKeywords[word]
            if (cat != null && category == null) {
                category = cat
                tags.add(cat to TagType.CATEGORY)
            }
        }

        // Detect goal-related content
        for (keyword in goalKeywords) {
            if (lowerInput.contains(keyword)) {
                isGoalRelated = true
                goalCategory = category
                break
            }
        }

        // Clean up description
        val description = input.trim()
            .replace(Regex("(?i)^remind\\s+me\\s+(?:to\\s+)?"), "")
            .replaceFirstChar { it.uppercase() }

        return ParsedTask(
            description = description,
            triggerType = triggerType,
            triggerValue = triggerValue,
            category = category,
            locationName = locationName,
            dueDate = dueDate,
            priority = priority,
            tags = tags.distinctBy { it.first },
            isGoalRelated = isGoalRelated,
            goalCategory = goalCategory
        )
    }

    @Suppress("UNUSED_PARAMETER")
    private fun parseDateFromMatch(dateStr: String): Long? {
        val cal = Calendar.getInstance()
        val lower = dateStr.lowercase()

        return try {
            when {
                lower == "today" -> cal.timeInMillis
                lower == "tomorrow" -> {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                    cal.timeInMillis
                }
                lower == "next week" -> {
                    cal.add(Calendar.WEEK_OF_YEAR, 1)
                    cal.timeInMillis
                }
                lower == "next month" -> {
                    cal.add(Calendar.MONTH, 1)
                    cal.timeInMillis
                }
                lower == "next year" -> {
                    cal.add(Calendar.YEAR, 1)
                    cal.timeInMillis
                }
                lower.startsWith("in ") -> {
                    val parts = lower.removePrefix("in ").split("\\s+".toRegex())
                    if (parts.size >= 2) {
                        val amount = parts[0].toIntOrNull() ?: 1
                        when {
                            parts[1].startsWith("day") -> cal.add(Calendar.DAY_OF_YEAR, amount)
                            parts[1].startsWith("week") -> cal.add(Calendar.WEEK_OF_YEAR, amount)
                            parts[1].startsWith("month") -> cal.add(Calendar.MONTH, amount)
                        }
                        cal.timeInMillis
                    } else null
                }
                else -> {
                    // Try parsing "Month Day Year" format
                    val formats = listOf(
                        SimpleDateFormat("MMMM dd yyyy", Locale.ENGLISH),
                        SimpleDateFormat("MMMM dd", Locale.ENGLISH),
                        SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH),
                        SimpleDateFormat("dd MMMM", Locale.ENGLISH)
                    )
                    var parsed: Long? = null
                    for (fmt in formats) {
                        try {
                            val date = fmt.parse(dateStr)
                            if (date != null) {
                                val parsedCal = Calendar.getInstance().apply { time = date }
                                if (parsedCal.get(Calendar.YEAR) < 2000) {
                                    parsedCal.set(Calendar.YEAR, cal.get(Calendar.YEAR))
                                }
                                parsed = parsedCal.timeInMillis
                                break
                            }
                        } catch (_: Exception) { }
                    }
                    parsed
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
