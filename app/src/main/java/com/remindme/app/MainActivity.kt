package com.remindme.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.remindme.app.ui.navigation.MainNavigation
import com.remindme.app.ui.screens.ModeSelectionScreen
import com.remindme.app.ui.theme.RemindMETheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "remindme_preferences")

class MainActivity : ComponentActivity() {

    companion object {
        val KEY_VOICE_MODE = booleanPreferencesKey("voice_mode")
        val KEY_REMEMBER_PREFERENCE = booleanPreferencesKey("remember_preference")
        val KEY_MODE_SELECTED = booleanPreferencesKey("mode_selected")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RemindMETheme {
                var modeSelected by remember { mutableStateOf(false) }
                var isVoiceMode by remember { mutableStateOf(false) }
                var rememberPreference by remember { mutableStateOf(false) }
                val coroutineScope = rememberCoroutineScope()

                // Permission state
                var hasAudioPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            this@MainActivity, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }
                var hasNotificationPermission by remember {
                    mutableStateOf(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            ContextCompat.checkSelfPermission(
                                this@MainActivity, Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                        } else true
                    )
                }
                var hasLocationPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }

                // Track pending voice mode activation
                var pendingVoiceMode by remember { mutableStateOf(false) }

                // Permission launchers
                val audioPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    hasAudioPermission = granted
                    // Activate voice mode after permission result
                    if (pendingVoiceMode) {
                        isVoiceMode = true
                        modeSelected = true
                        pendingVoiceMode = false
                    }
                }

                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    hasNotificationPermission = granted
                }

                val locationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
                }

                // Request permissions on first launch
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (!hasLocationPermission) {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                }

                // Load saved preferences
                LaunchedEffect(Unit) {
                    val prefs = dataStore.data.first()
                    val savedRemember = prefs[KEY_REMEMBER_PREFERENCE] ?: false
                    rememberPreference = savedRemember
                    if (savedRemember) {
                        val savedModeSelected = prefs[KEY_MODE_SELECTED] ?: false
                        if (savedModeSelected) {
                            isVoiceMode = prefs[KEY_VOICE_MODE] ?: false
                            modeSelected = true
                        }
                    }
                }

                if (!modeSelected) {
                    ModeSelectionScreen(
                        onSelectVoiceMode = {
                            if (!hasAudioPermission) {
                                // Request permission first, activate voice mode in callback
                                pendingVoiceMode = true
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                isVoiceMode = true
                                modeSelected = true
                            }
                            coroutineScope.launch {
                                savePreferences(true, rememberPreference)
                            }
                        },
                        onSelectTextMode = {
                            isVoiceMode = false
                            modeSelected = true
                            coroutineScope.launch {
                                savePreferences(false, rememberPreference)
                            }
                        },
                        rememberPreference = rememberPreference,
                        onRememberPreferenceChanged = { remember ->
                            rememberPreference = remember
                            coroutineScope.launch {
                                dataStore.edit { prefs ->
                                    prefs[KEY_REMEMBER_PREFERENCE] = remember
                                    if (!remember) {
                                        prefs[KEY_MODE_SELECTED] = false
                                    }
                                }
                            }
                        }
                    )
                } else {
                    MainNavigation(
                        isVoiceMode = isVoiceMode,
                        onToggleMode = {
                            val newMode = !isVoiceMode
                            if (newMode && !hasAudioPermission) {
                                // Request permission, then toggle in callback
                                pendingVoiceMode = false // not from mode selection
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                            isVoiceMode = newMode
                            coroutineScope.launch {
                                savePreferences(newMode, rememberPreference)
                            }
                        }
                    )
                }
            }
        }
    }

    private suspend fun savePreferences(voiceMode: Boolean, remember: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_VOICE_MODE] = voiceMode
            prefs[KEY_REMEMBER_PREFERENCE] = remember
            if (remember) {
                prefs[KEY_MODE_SELECTED] = true
            }
        }
    }
}
