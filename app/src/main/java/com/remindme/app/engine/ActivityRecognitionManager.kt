package com.remindme.app.engine

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class UserActivity {
    STILL,
    WALKING,
    RUNNING,
    IN_VEHICLE,
    ON_BICYCLE,
    UNKNOWN
}

data class ActivityState(
    val activity: UserActivity = UserActivity.UNKNOWN,
    val confidence: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

class ActivityRecognitionManager(private val context: Context) {

    companion object {
        private const val DETECTION_INTERVAL_MS = 30_000L // 30 seconds
        private var _currentActivity = MutableStateFlow(ActivityState())
        val currentActivity: StateFlow<ActivityState> = _currentActivity.asStateFlow()

        fun updateActivity(activity: UserActivity, confidence: Int) {
            _currentActivity.value = ActivityState(activity, confidence)
        }
    }

    private val pendingIntent: PendingIntent by lazy {
        val intent = Intent(context, ActivityRecognitionReceiver::class.java)
        PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    fun startTracking() {
        if (!hasPermission()) return

        try {
            ActivityRecognition.getClient(context)
                .requestActivityUpdates(DETECTION_INTERVAL_MS, pendingIntent)
        } catch (_: SecurityException) { }
    }

    fun stopTracking() {
        try {
            ActivityRecognition.getClient(context)
                .removeActivityUpdates(pendingIntent)
        } catch (_: Exception) { }
    }

    private fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isUserMoving(): Boolean {
        val state = _currentActivity.value
        return state.activity == UserActivity.WALKING ||
                state.activity == UserActivity.RUNNING ||
                state.activity == UserActivity.IN_VEHICLE ||
                state.activity == UserActivity.ON_BICYCLE
    }

    fun isUserDriving(): Boolean {
        return _currentActivity.value.activity == UserActivity.IN_VEHICLE
    }

    fun isUserStationary(): Boolean {
        return _currentActivity.value.activity == UserActivity.STILL
    }
}

class ActivityRecognitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityRecognitionResult.hasResult(intent)) return

        val result = ActivityRecognitionResult.extractResult(intent) ?: return
        val mostProbable = result.mostProbableActivity

        val activity = when (mostProbable.type) {
            DetectedActivity.STILL -> UserActivity.STILL
            DetectedActivity.WALKING -> UserActivity.WALKING
            DetectedActivity.RUNNING -> UserActivity.RUNNING
            DetectedActivity.IN_VEHICLE -> UserActivity.IN_VEHICLE
            DetectedActivity.ON_BICYCLE -> UserActivity.ON_BICYCLE
            DetectedActivity.ON_FOOT -> UserActivity.WALKING
            else -> UserActivity.UNKNOWN
        }

        ActivityRecognitionManager.updateActivity(activity, mostProbable.confidence)
    }
}
