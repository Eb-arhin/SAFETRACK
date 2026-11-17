package com.example.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.remote.SafeTrackRepository
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return
        if (geofencingEvent.hasError()) {
            val errorMessage = GeofenceStatusCodes.getStatusCodeString(geofencingEvent.errorCode)
            Log.w(TAG, "GeofencingEvent error: $errorMessage")
            return
        }

        val transition = geofencingEvent.geofenceTransition
        if (transition != Geofence.GEOFENCE_TRANSITION_ENTER &&
            transition != Geofence.GEOFENCE_TRANSITION_EXIT
        ) {
            return
        }

        val isEntering = transition == Geofence.GEOFENCE_TRANSITION_ENTER
        val config = GeofenceManager.loadConfig(context)
        if (!config.isActive || config.schoolId.isBlank() || config.trackedStudents.isEmpty()) {
            Log.d(TAG, "Ignoring geofence transition because no active school students are configured.")
            return
        }

        val timeLabel = SimpleDateFormat("HH:mm", Locale.US).format(Date())
        val triggerLat = geofencingEvent.triggeringLocation?.latitude ?: config.latitude
        val triggerLng = geofencingEvent.triggeringLocation?.longitude ?: config.longitude
        val eventActionText = if (isEntering) "Entered" else "Exited"
        val summaryText = "$eventActionText ${config.schoolName} at $timeLabel"
        GeofenceManager.recordLastEventLocally(context, summaryText)

        // Show immediate local alert on the carrying device if permitted
        val firstStudent = config.trackedStudents.first()
        val (alertTitle, alertBody) = GeofenceManager.formatGeofenceAlert(
            studentName = if (config.trackedStudents.size == 1) {
                firstStudent.fullName
            } else {
                "${config.trackedStudents.size} pupils (${firstStudent.fullName}, ...)"
            },
            schoolName = config.schoolName,
            isEntering = isEntering,
            timeLabel = timeLabel
        )
        NotificationHelper.showAlert(context, alertTitle, alertBody)

        // Persist Firestore notifications for parents & audit trail asynchronously
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (Firebase.auth.currentUser != null) {
                    val repo = SafeTrackRepository(context.applicationContext)
                    repo.recordGeofenceTransition(
                        schoolId = config.schoolId,
                        schoolName = config.schoolName,
                        trackedStudents = config.trackedStudents,
                        isEntering = isEntering,
                        latitude = triggerLat,
                        longitude = triggerLng,
                        timeLabel = timeLabel
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync geofence transition to Firestore", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "GeofenceReceiver"
    }
}
