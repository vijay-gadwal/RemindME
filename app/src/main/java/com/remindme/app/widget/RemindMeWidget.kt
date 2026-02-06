package com.remindme.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.remindme.app.MainActivity
import com.remindme.app.data.database.AppDatabase
import com.remindme.app.data.entity.Priority
import com.remindme.app.data.entity.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RemindMeWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val database = AppDatabase.getDatabase(context)
        val tasks = withContext(Dispatchers.IO) {
            database.taskDao().getActiveTasksSync().take(5)
        }
        val taskCount = withContext(Dispatchers.IO) {
            database.taskDao().getActiveTasksSync().size
        }

        provideContent {
            WidgetContent(tasks = tasks, totalCount = taskCount)
        }
    }
}

@Composable
private fun WidgetContent(tasks: List<Task>, totalCount: Int) {
    val bgColor = ColorProvider(Color(0xFF1A1F2E))
    val cyanColor = ColorProvider(Color(0xFF4FC3F7))
    val textColor = ColorProvider(Color(0xFFE8EAED))
    val subtextColor = ColorProvider(Color(0xFFA0A4AB))
    val surfaceColor = ColorProvider(Color(0xFF242938))

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bgColor)
            .cornerRadius(16.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        // Header
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Horizontal.Start,
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = "RemindME",
                style = TextStyle(
                    color = cyanColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = "$totalCount tasks",
                style = TextStyle(
                    color = subtextColor,
                    fontSize = 12.sp
                )
            )
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        // Task list
        if (tasks.isEmpty()) {
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No active tasks",
                    style = TextStyle(
                        color = subtextColor,
                        fontSize = 14.sp
                    )
                )
            }
        } else {
            Column(
                modifier = GlanceModifier.defaultWeight(),
                verticalAlignment = Alignment.Vertical.Top
            ) {
                tasks.forEach { task ->
                    WidgetTaskItem(task, textColor, surfaceColor)
                    Spacer(modifier = GlanceModifier.height(4.dp))
                }
                if (totalCount > 5) {
                    Text(
                        text = "+${totalCount - 5} more...",
                        style = TextStyle(
                            color = subtextColor,
                            fontSize = 11.sp
                        ),
                        modifier = GlanceModifier.padding(start = 4.dp, top = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        // Quick actions row
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally
        ) {
            // Quick add button
            Box(
                modifier = GlanceModifier
                    .background(ColorProvider(Color(0xFF4FC3F7)))
                    .cornerRadius(20.dp)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+ Quick Add",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF0F1419)),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            // Voice button
            Box(
                modifier = GlanceModifier
                    .background(surfaceColor)
                    .cornerRadius(20.dp)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Voice",
                    style = TextStyle(
                        color = cyanColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}

@Composable
private fun WidgetTaskItem(
    task: Task,
    textColor: ColorProvider,
    surfaceColor: ColorProvider
) {
    val priorityColor = when (task.priority) {
        Priority.URGENT -> ColorProvider(Color(0xFFEF4444))
        Priority.HIGH -> ColorProvider(Color(0xFFF59E0B))
        Priority.MEDIUM -> ColorProvider(Color(0xFF3B82F6))
        Priority.LOW -> ColorProvider(Color(0xFF6B7280))
    }

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(surfaceColor)
            .cornerRadius(8.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        // Priority dot
        Box(
            modifier = GlanceModifier
                .size(6.dp)
                .background(priorityColor)
                .cornerRadius(3.dp)
        ) {}

        Spacer(modifier = GlanceModifier.width(8.dp))

        // Task description
        Text(
            text = task.description,
            style = TextStyle(
                color = textColor,
                fontSize = 12.sp
            ),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight()
        )
    }
}

class RemindMeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RemindMeWidget()
}
