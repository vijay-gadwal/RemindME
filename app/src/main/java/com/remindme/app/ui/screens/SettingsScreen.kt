package com.remindme.app.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
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
    val modelManager = remember { GemmaModelManager.getInstance(context) }
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
                    ModelState.NOT_DOWNLOADED -> "Gemma 2B - Not imported. Tap to import."
                    ModelState.READY -> "Gemma 2B - Ready (${modelInfo.modelSizeMb} MB)"
                    ModelState.LOADING -> "Gemma 2B - Loading..."
                    ModelState.LOADED -> "Gemma 2B - Active in memory"
                    ModelState.GENERATING -> "Gemma 2B - Generating..."
                    ModelState.ERROR -> "Error: ${modelInfo.errorMessage ?: "Unknown"}"
                    ModelState.UNLOADING -> "Gemma 2B - Unloading..."
                    ModelState.DOWNLOADING -> "Importing model... ${(modelInfo.downloadProgress * 100).toInt()}%"
                }

                var showModelDialog by remember { mutableStateOf(false) }
                var showModelInstructions by remember { mutableStateOf(false) }
                var testResult by remember { mutableStateOf<String?>(null) }
                var isTesting by remember { mutableStateOf(false) }

                val modelFilePicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri?.let {
                        coroutineScope.launch {
                            val success = modelManager.copyModelFromUri(it)
                            snackbarMessage = if (success) "Model imported successfully!" else "Model import failed"
                            showSnackbar = true
                        }
                    }
                }

                SettingsItem(
                    icon = Icons.Default.SmartToy,
                    title = "On-Device LLM",
                    subtitle = llmSubtitle,
                    onClick = { showModelDialog = true }
                )

                if (modelInfo.state == ModelState.DOWNLOADING) {
                    LinearProgressIndicator(
                        progress = modelInfo.downloadProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(4.dp),
                        color = PrimaryCyan,
                        trackColor = DarkSurface
                    )
                }

                if (modelInfo.state == ModelState.READY || modelInfo.state == ModelState.LOADED) {
                    SettingsItem(
                        icon = Icons.Default.PlayArrow,
                        title = "Test Model",
                        subtitle = if (isTesting) "Testing..." else (testResult ?: "Tap to verify model is working"),
                        onClick = {
                            if (!isTesting) {
                                isTesting = true
                                testResult = null
                                coroutineScope.launch {
                                    testResult = modelManager.testModel()
                                    isTesting = false
                                }
                            }
                        }
                    )
                }

                SettingsItem(
                    icon = Icons.Default.Memory,
                    title = "LLM Memory Usage",
                    subtitle = when (modelInfo.state) {
                        ModelState.LOADED, ModelState.GENERATING -> "Model in memory. Tap to unload."
                        else -> "Auto-unload after 2 minutes idle"
                    },
                    onClick = {
                        if (modelInfo.state == ModelState.LOADED || modelInfo.state == ModelState.GENERATING) {
                            modelManager.unloadModel()
                            snackbarMessage = "LLM model unloaded from memory"
                            showSnackbar = true
                        }
                    }
                )

                // Model management dialog
                if (showModelDialog) {
                    AlertDialog(
                        onDismissRequest = { showModelDialog = false },
                        title = {
                            Text("On-Device LLM (Gemma 2B)", color = PrimaryCyan, fontWeight = FontWeight.Bold)
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    "Status: ${llmSubtitle}",
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                if (modelInfo.state == ModelState.NOT_DOWNLOADED || modelInfo.state == ModelState.ERROR) {
                                    Text(
                                        "The Gemma 2B model file is required for AI features. " +
                                        "Download it and then import it here.",
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    OutlinedButton(
                                        onClick = { showModelInstructions = true; showModelDialog = false },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Help, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Download Instructions", color = PrimaryCyan)
                                    }
                                    Button(
                                        onClick = {
                                            showModelDialog = false
                                            modelFilePicker.launch(arrayOf("application/octet-stream", "*/*"))
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                                    ) {
                                        Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Import Model File")
                                    }
                                }

                                if (modelInfo.state == ModelState.READY || modelInfo.state == ModelState.LOADED) {
                                    Text(
                                        "Model file: ${modelInfo.modelSizeMb} MB",
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (modelInfo.state == ModelState.READY) {
                                        Button(
                                            onClick = {
                                                coroutineScope.launch { modelManager.loadModel() }
                                                showModelDialog = false
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                                        ) {
                                            Text("Load Model into Memory")
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            modelManager.deleteModel()
                                            showModelDialog = false
                                            snackbarMessage = "Model deleted"
                                            showSnackbar = true
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Delete Model", color = StatusRed)
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showModelDialog = false }) {
                                Text("Close", color = PrimaryCyan)
                            }
                        },
                        containerColor = DarkSurfaceVariant
                    )
                }

                // Download instructions dialog
                if (showModelInstructions) {
                    val uriHandler = LocalUriHandler.current
                    AlertDialog(
                        onDismissRequest = { showModelInstructions = false },
                        title = {
                            Text("How to Get the Model", color = PrimaryCyan, fontWeight = FontWeight.Bold)
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Option A: Gemma 2B (Kaggle)", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                val kaggleLink = buildAnnotatedString {
                                    append("Download from ")
                                    pushStringAnnotation(tag = "URL", annotation = "https://www.kaggle.com/models/google/gemma/tfLite/gemma-2b-it-cpu-int4")
                                    withStyle(SpanStyle(color = PrimaryCyan, textDecoration = TextDecoration.Underline)) {
                                        append("Kaggle - Gemma 2B CPU")
                                    }
                                    pop()
                                }
                                ClickableText(
                                    text = kaggleLink,
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                                    onClick = { offset ->
                                        kaggleLink.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
                                            uriHandler.openUri(it.item)
                                        }
                                    }
                                )

                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Option B: Gemma 3 1B (Hugging Face)", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                val hfLink = buildAnnotatedString {
                                    append("Download from ")
                                    pushStringAnnotation(tag = "URL", annotation = "https://huggingface.co/litert-community/Gemma3-1B-IT")
                                    withStyle(SpanStyle(color = PrimaryCyan, textDecoration = TextDecoration.Underline)) {
                                        append("HuggingFace - Gemma 3 1B")
                                    }
                                    pop()
                                    append(" (smaller, newer)")
                                }
                                ClickableText(
                                    text = hfLink,
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                                    onClick = { offset ->
                                        hfLink.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
                                            uriHandler.openUri(it.item)
                                        }
                                    }
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Steps:", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text("1. Sign in and download the .bin or .task file", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                                Text("2. Transfer the file to your phone", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                                Text("3. Come back here and tap 'Import Model File'", color = TextSecondary, style = MaterialTheme.typography.bodySmall)

                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Your device:", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Snapdragon 7s Gen 2 with Adreno 710 GPU supports " +
                                    "both CPU and GPU model variants.",
                                    color = StatusGreen,
                                    style = MaterialTheme.typography.bodySmall
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Alternative (ADB):", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "adb push model.bin /sdcard/Download/",
                                    color = TextTertiary,
                                    style = MaterialTheme.typography.bodySmall
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Note: Models are ~1-1.5 GB. AI features work without it — " +
                                    "the app falls back to local keyword-based processing.",
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showModelInstructions = false }) {
                                Text("Got it", color = PrimaryCyan)
                            }
                        },
                        containerColor = DarkSurfaceVariant
                    )
                }

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
