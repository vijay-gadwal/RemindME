package com.remindme.app.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.remindme.app.engine.BackupExportManager
import com.remindme.app.llm.GemmaModelManager
import com.remindme.app.llm.ModelState
import com.remindme.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val backupManager = remember { BackupExportManager(context) }
    val modelManager = remember { GemmaModelManager(context) }
    val modelInfo by modelManager.modelInfo.collectAsState()

    var showAboutDialog by remember { mutableStateOf(false) }
    var showSnackbar by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf("") }

    // File picker launchers
    val backupExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                val success = backupManager.exportBackupToUri(it)
                snackbarMessage = if (success) "Backup exported successfully!" else "Backup export failed"
                showSnackbar = true
            }
        }
    }

    val backupImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                val success = backupManager.importBackupFromUri(it)
                snackbarMessage = if (success) "Backup restored successfully!" else "Restore failed - invalid file"
                showSnackbar = true
            }
        }
    }

    val csvExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                val success = backupManager.exportTasksCsvToUri(it)
                snackbarMessage = if (success) "Tasks exported to CSV!" else "CSV export failed"
                showSnackbar = true
            }
        }
    }

    val goalsCsvExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                val success = backupManager.exportGoalsCsvToUri(it)
                snackbarMessage = if (success) "Goals exported to CSV!" else "CSV export failed"
                showSnackbar = true
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(showSnackbar) {
        if (showSnackbar) {
            snackbarHostState.showSnackbar(snackbarMessage)
            showSnackbar = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DarkBackground)
        ) {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Interface section
                SectionHeader("Interface")
                SettingsItem(
                    icon = Icons.Default.Mic,
                    title = "Default Mode",
                    subtitle = "Choose between voice and text mode",
                    onClick = { }
                )
                SettingsItem(
                    icon = Icons.Default.Notifications,
                    title = "Notifications",
                    subtitle = "Configure reminder notifications",
                    onClick = { }
                )
                SettingsItem(
                    icon = Icons.Default.Schedule,
                    title = "Daily Digest",
                    subtitle = "Set time for daily summary",
                    onClick = { }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Goals section
                SectionHeader("Goals & Reminders")
                SettingsItem(
                    icon = Icons.Default.Flag,
                    title = "Goal Check-in Reminders",
                    subtitle = "Configure check-in notification times",
                    onClick = { }
                )
                SettingsItem(
                    icon = Icons.Default.TrendingUp,
                    title = "Motivational Nudges",
                    subtitle = "Enable streak and progress notifications",
                    onClick = { }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Data section
                SectionHeader("Data")
                SettingsItem(
                    icon = Icons.Default.Backup,
                    title = "Backup Data",
                    subtitle = "Export all tasks and goals to JSON",
                    onClick = {
                        backupExportLauncher.launch(backupManager.getBackupFileName())
                    }
                )
                SettingsItem(
                    icon = Icons.Default.Restore,
                    title = "Restore Data",
                    subtitle = "Import from a backup JSON file",
                    onClick = {
                        backupImportLauncher.launch(arrayOf("application/json"))
                    }
                )
                SettingsItem(
                    icon = Icons.Default.FileDownload,
                    title = "Export Tasks CSV",
                    subtitle = "Export tasks as spreadsheet",
                    onClick = {
                        csvExportLauncher.launch(backupManager.getTasksCsvFileName())
                    }
                )
                SettingsItem(
                    icon = Icons.Default.FileDownload,
                    title = "Export Goals CSV",
                    subtitle = "Export goals as spreadsheet",
                    onClick = {
                        goalsCsvExportLauncher.launch(backupManager.getGoalsCsvFileName())
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // AI section
                SectionHeader("AI Engine")
                val llmSubtitle = when (modelInfo.state) {
                    ModelState.NOT_DOWNLOADED -> "Gemma 2B - Model not found"
                    ModelState.READY -> "Gemma 2B - Ready (${modelInfo.modelSizeMb} MB)"
                    ModelState.LOADING -> "Gemma 2B - Loading..."
                    ModelState.LOADED -> "Gemma 2B - Active in memory"
                    ModelState.GENERATING -> "Gemma 2B - Generating..."
                    ModelState.ERROR -> "Error: ${modelInfo.errorMessage ?: "Unknown"}"
                    ModelState.UNLOADING -> "Gemma 2B - Unloading..."
                    ModelState.DOWNLOADING -> "Gemma 2B - Downloading..."
                }
                SettingsItem(
                    icon = Icons.Default.SmartToy,
                    title = "On-Device LLM",
                    subtitle = llmSubtitle,
                    onClick = { }
                )
                SettingsItem(
                    icon = Icons.Default.Memory,
                    title = "LLM Memory Usage",
                    subtitle = "Auto-unload after 2 minutes idle",
                    onClick = {
                        if (modelInfo.state == ModelState.LOADED || modelInfo.state == ModelState.GENERATING) {
                            modelManager.unloadModel()
                            snackbarMessage = "LLM model unloaded from memory"
                            showSnackbar = true
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Location section
                SectionHeader("Location")
                SettingsItem(
                    icon = Icons.Default.LocationOn,
                    title = "Saved Locations",
                    subtitle = "Manage geofenced locations",
                    onClick = { }
                )
                SettingsItem(
                    icon = Icons.Default.Map,
                    title = "Location Tracking",
                    subtitle = "Configure background location updates",
                    onClick = { }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // About section
                SectionHeader("About")
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "About RemindME",
                    subtitle = "Version 1.0.0",
                    onClick = { showAboutDialog = true }
                )
            }
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Text("RemindME", color = PrimaryCyan, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Version 1.0.0", color = TextPrimary)
                    Text("Author: Vijay Gadwal", color = TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Your personal context-aware reminder assistant. " +
                        "Track tasks, goals, and get smart reminders based on your location and activities.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Features:", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text("• Context-aware reminders", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Text("• Long-term goal tracking", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Text("• Voice & text interface", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Text("• On-device AI (Gemma 2B)", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Text("• Location awareness", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Text("• Activity recognition", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Text("• Backup & export", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Text("• 100% local data storage", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("OK", color = PrimaryCyan)
                }
            },
            containerColor = DarkSurfaceVariant
        )
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = PrimaryCyan,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = PrimaryCyan,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
