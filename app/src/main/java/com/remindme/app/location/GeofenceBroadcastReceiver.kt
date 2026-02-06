package com.remindme.app.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.remindme.app.data.database.AppDatabase
import com.remindme.app.data.entity.TriggerType
import com.remindme.app.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return
        if (geofencingEvent.hasError()) return

        val transitionType = geofencingEvent.geofenceTransition
        val triggeringGeofences = geofencingEvent.triggeringGeofences ?: return

        if (transitionType == Geofence.GEOFENCE_TRANSITION_ENTER ||
            transitionType == Geofence.GEOFENCE_TRANSITION_DWELL
        ) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val database = AppDatabase.getDatabase(context)

                    for (geofence in triggeringGeofences) {
                        val locationId = geofence.requestId.toLongOrNull() ?: continue
                        val savedLocation = database.savedLocationDao().getLocationById(locationId) ?: continue

                        // Find tasks linked to this location
                        val locationTasks = database.taskDao().getActiveTasksSync().filter { task ->
                            task.triggerType == TriggerType.LOCATION &&
                            task.locationName != null &&
                            (task.locationName.equals(savedLocation.name, ignoreCase = true) ||
                             task.locationName.equals(savedLocation.address, ignoreCase = true))
                        }

                        // Also search tasks by location keywords
                        val keywordTasks = database.taskDao().searchTasks(savedLocation.name)
                            .filter { it.triggerType == TriggerType.LOCATION }

                        val allTasks = (locationTasks + keywordTasks).distinctBy { it.id }

                        for (task in allTasks) {
                            NotificationHelper.showLocationReminder(
                                context,
                                task,
                                savedLocation.name
                            )
                        }
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
