package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AppNotification
import com.example.data.model.ClassDepartureEntry
import com.example.data.model.Journey
import com.example.data.model.MovementStatus
import com.example.data.model.School
import com.example.data.model.SchoolClass
import com.example.data.model.Student
import com.example.data.model.UserRole
import com.example.ui.components.ClassDepartureTableCard
import com.example.ui.components.DeviceGeofenceMonitorCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.JourneyLocationCard
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.MovementStatusBadge
import com.example.ui.components.SchoolGeofenceConfigCard
import com.example.ui.theme.DelayAmber700
import com.example.ui.theme.SafeBlue100
import com.example.ui.theme.SafeBlue700
import com.example.ui.theme.SafeGreen100
import com.example.ui.theme.SafeGreen600
import com.example.util.GeofenceMonitoringConfig

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MovementTabScreen(
    role: UserRole,
    currentUserId: String = "",
    school: School?,
    classes: List<SchoolClass>,
    students: List<Student>,
    journeys: List<Journey>,
    notifications: List<AppNotification>,
    geofenceStatus: GeofenceMonitoringConfig,
    perimeterCheckStatus: String?,
    isBusy: Boolean,
    onStartMorningJourney: (Student, destinationName: String, note: String) -> Unit,
    onConfirmArrivalAtSchool: (Journey, Student, markLate: Boolean) -> Unit,
    onInitiateAfternoonDismissal: (Student, destinationName: String, isAlternative: Boolean, note: String) -> Unit,
    onDispatchClassDeparture: (entries: List<ClassDepartureEntry>, notifyLateExceptions: Boolean) -> Unit,
    onConfirmSafeArrivalHome: (Journey) -> Unit,
    onUpdateJourneyStatus: (Journey, MovementStatus, note: String) -> Unit,
    onMarkNotificationRead: (String) -> Unit,
    onEnableGeofenceMonitoring: (List<Student>) -> Unit,
    onDisableGeofenceMonitoring: () -> Unit,
    onCheckPerimeterNow: (School) -> Unit,
    onCaptureSchoolGps: ((Double, Double) -> Unit) -> Unit,
    onSaveSchoolGeofence: (latitude: Double, longitude: Double, radiusMeters: Double, enabled: Boolean) -> Unit,
    lastGpsAccuracyMeters: Float? = null,
    modifier: Modifier = Modifier
) {
    val activeStudents = students.filter { it.status == "active" }
    var selectedStudentId by remember(activeStudents) {
        mutableStateOf(activeStudents.firstOrNull()?.id.orEmpty())
    }
    val selectedStudent = activeStudents.firstOrNull { it.id == selectedStudentId } ?: activeStudents.firstOrNull()
    var departureNote by remember { mutableStateOf("") }
    var selectedHomeDest by remember(selectedStudent?.id) {
        mutableStateOf(
            selectedStudent?.approvedDestinations?.firstOrNull { !it.equals(school?.name, ignoreCase = true) }
                ?: "Home"
        )
    }

    val leftHomeJourneys = journeys.filter { it.status == MovementStatus.ON_THE_WAY_TO_SCHOOL.key }
    val leftSchoolJourneys = journeys.filter { it.status == MovementStatus.ON_THE_WAY_HOME.key }
    val movementNotifications = notifications.filter {
        it.type in listOf("journey", "journey_started", "dismissed", "late_departure", "geofence_enter", "geofence_exit") ||
            it.title.contains("Way", ignoreCase = true) ||
            it.title.contains("Left", ignoreCase = true) ||
            it.title.contains("Late", ignoreCase = true) ||
            it.title.contains("Premises", ignoreCase = true) ||
            it.title.contains("Dismiss", ignoreCase = true) ||
            it.title.contains("Arriv", ignoreCase = true)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        item {
            Column(modifier = Modifier.widthIn(max = 860.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Child Movement & Departure Alerts", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Instant updates when a pupil leaves home for school or leaves school for home, with automatic alerts sent to parents and teachers.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    maxItemsInEachRow = 3
                ) {
                    KpiMetricCard(
                        label = "Left Home",
                        value = leftHomeJourneys.size.toString(),
                        icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                        accentColor = SafeBlue700,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMetricCard(
                        label = "Left School",
                        value = leftSchoolJourneys.size.toString(),
                        icon = Icons.Filled.DirectionsBus,
                        accentColor = DelayAmber700,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMetricCard(
                        label = "Unread Alerts",
                        value = movementNotifications.count { !it.isRead }.toString(),
                        icon = Icons.Filled.NotificationsActive,
                        accentColor = SafeGreen600,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            Column(modifier = Modifier.widthIn(max = 860.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DeviceGeofenceMonitorCard(
                    school = school,
                    students = activeStudents,
                    geofenceStatus = geofenceStatus,
                    perimeterCheckStatus = perimeterCheckStatus,
                    isBusy = isBusy,
                    lastGpsAccuracyMeters = lastGpsAccuracyMeters,
                    onEnableMonitoring = onEnableGeofenceMonitoring,
                    onDisableMonitoring = onDisableGeofenceMonitoring,
                    onCheckPerimeterNow = onCheckPerimeterNow
                )
                if ((role == UserRole.SCHOOL_ADMIN || role == UserRole.SUPER_ADMIN) && school != null) {
                    SchoolGeofenceConfigCard(
                        school = school,
                        isBusy = isBusy,
                        lastGpsAccuracyMeters = lastGpsAccuracyMeters,
                        onCaptureCurrentGps = onCaptureSchoolGps,
                        onSaveGeofence = onSaveSchoolGeofence
                    )
                }
            }
        }

        if (activeStudents.isNotEmpty() && (role == UserRole.TEACHER || role == UserRole.SCHOOL_ADMIN || role == UserRole.SUPER_ADMIN)) {
            item {
                Column(modifier = Modifier.widthIn(max = 860.dp)) {
                    ClassDepartureTableCard(
                        school = school,
                        classes = classes,
                        students = activeStudents,
                        isBusy = isBusy,
                        onDispatchClassDeparture = onDispatchClassDeparture,
                        currentUserId = currentUserId,
                        currentUserRole = role
                    )
                }
            }
        }

        // Quick Departure Dispatch Card (Parent -> Left Home; Teacher/Admin -> Left School)
        if (activeStudents.isNotEmpty() && selectedStudent != null) {
            item {
                Card(
                    modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SafeBlue100.copy(alpha = 0.45f)),
                    border = BorderStroke(1.dp, SafeBlue700.copy(alpha = 0.25f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Send Instant Departure Notification", style = MaterialTheme.typography.titleMedium)

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            activeStudents.forEach { stu ->
                                FilterChip(
                                    selected = stu.id == selectedStudent.id,
                                    onClick = { selectedStudentId = stu.id },
                                    label = { Text("${stu.fullName} (${stu.className})") }
                                )
                            }
                        }

                        if (role == UserRole.TEACHER || role == UserRole.SCHOOL_ADMIN || role == UserRole.SUPER_ADMIN) {
                            val homeOptions = (selectedStudent.approvedDestinations.filter {
                                !it.equals(school?.name, ignoreCase = true)
                            } + "Home").distinct()
                            Text("Approved Destination", style = MaterialTheme.typography.labelLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                homeOptions.forEach { dest ->
                                    FilterChip(
                                        selected = selectedHomeDest == dest,
                                        onClick = { selectedHomeDest = dest },
                                        label = { Text(dest) }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = departureNote,
                            onValueChange = { departureNote = it },
                            label = { Text("Movement Note (optional)") },
                            placeholder = {
                                Text(
                                    if (role == UserRole.PARENT) "e.g., Left home with school bus at 07:10"
                                    else "e.g., Dismissed at gate to authorised guardian"
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (role == UserRole.PARENT) {
                            Button(
                                onClick = {
                                    onStartMorningJourney(
                                        selectedStudent,
                                        school?.name ?: "School",
                                        departureNote.ifBlank { "Child has left home for school" }
                                    )
                                    departureNote = ""
                                },
                                enabled = !isBusy,
                                modifier = Modifier.fillMaxWidth().testTag("movement_left_home_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text("Child Has Left Home — Notify Teachers")
                            }
                        } else {
                            val canDismissSelected = role == UserRole.TEACHER &&
                                (currentUserId.isBlank() || currentUserId in selectedStudent.teacherUids)
                            if (!canDismissSelected) {
                                Text(
                                    text = "Only ${selectedStudent.fullName}'s assigned class teacher may authorize afternoon dismissal.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = {
                                    onInitiateAfternoonDismissal(
                                        selectedStudent,
                                        selectedHomeDest,
                                        !selectedHomeDest.contains("Home", ignoreCase = true),
                                        departureNote.ifBlank { "Child has left school for $selectedHomeDest" }
                                    )
                                    departureNote = ""
                                },
                                enabled = !isBusy && canDismissSelected,
                                modifier = Modifier.fillMaxWidth().testTag("movement_left_school_button")
                            ) {
                                Icon(Icons.Filled.DirectionsBus, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text(if (canDismissSelected) "Child Has Left School — Notify Parents" else "Dismissal Locked (Assigned Teacher Only)")
                            }
                        }
                    }
                }
            }
        }

        // Active Left Home Journeys
        item {
            Column(modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth()) {
                Text("Left Home (On the Way to School — ${leftHomeJourneys.size})", style = MaterialTheme.typography.titleMedium)
            }
        }
        if (leftHomeJourneys.isEmpty()) {
            item {
                Column(modifier = Modifier.widthIn(max = 860.dp)) {
                    EmptyStateCard("No Pupils En Route From Home", "When a parent reports that a child left home, the live status appears here.")
                }
            }
        } else {
            items(leftHomeJourneys, key = { "lh_${it.id}" }) { journey ->
                val student = students.firstOrNull { it.id == journey.studentId }
                Card(modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(journey.studentName, style = MaterialTheme.typography.titleMedium)
                                Text("Destination: ${journey.destinationName} • Started by ${journey.initiatedByName}", style = MaterialTheme.typography.bodySmall)
                            }
                            MovementStatusBadge(journey.status)
                        }
                        JourneyLocationCard(journey)
                        if (student != null && (role == UserRole.TEACHER || role == UserRole.SCHOOL_ADMIN || role == UserRole.SUPER_ADMIN)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onConfirmArrivalAtSchool(journey, student, false) },
                                    enabled = !isBusy,
                                    colors = ButtonDefaults.buttonColors(containerColor = SafeGreen600),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.School, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text("Confirm Arrived at School")
                                }
                                OutlinedButton(
                                    onClick = { onUpdateJourneyStatus(journey, MovementStatus.JOURNEY_DELAYED, "Reported delayed en route") },
                                    enabled = !isBusy
                                ) { Text("Delayed") }
                            }
                        }
                    }
                }
            }
        }

        // Active Left School Journeys
        item {
            Column(modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth()) {
                Text("Left School (On the Way Home — ${leftSchoolJourneys.size})", style = MaterialTheme.typography.titleMedium)
            }
        }
        if (leftSchoolJourneys.isEmpty()) {
            item {
                Column(modifier = Modifier.widthIn(max = 860.dp)) {
                    EmptyStateCard("No Pupils En Route From School", "When a teacher dismisses a child from school, parents are notified and can confirm safe arrival home.")
                }
            }
        } else {
            items(leftSchoolJourneys, key = { "ls_${it.id}" }) { journey ->
                Card(
                    modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SafeGreen100.copy(alpha = 0.25f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(journey.studentName, style = MaterialTheme.typography.titleMedium)
                                Text("Heading to: ${journey.destinationName} • Dismissed by ${journey.initiatedByName}", style = MaterialTheme.typography.bodySmall)
                            }
                            MovementStatusBadge(journey.status)
                        }
                        JourneyLocationCard(journey)
                        if (role == UserRole.PARENT) {
                            Button(
                                onClick = { onConfirmSafeArrivalHome(journey) },
                                enabled = !isBusy,
                                colors = ButtonDefaults.buttonColors(containerColor = SafeGreen600),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Home, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("Confirm Child Arrived Safely Home")
                            }
                        }
                    }
                }
            }
        }

        // Movement Notifications Feed
        item {
            Column(modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth()) {
                Text("Movement Notification Log (${movementNotifications.size})", style = MaterialTheme.typography.titleMedium)
            }
        }
        if (movementNotifications.isEmpty()) {
            item {
                Text(
                    "No movement notifications received yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth()
                )
            }
        } else {
            items(movementNotifications.take(20), key = { "ntf_${it.id}" }) { ntf ->
                Card(
                    modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (ntf.isRead) MaterialTheme.colorScheme.surface else SafeBlue100.copy(alpha = 0.45f)
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(ntf.title, style = MaterialTheme.typography.titleSmall)
                            Text(ntf.body, style = MaterialTheme.typography.bodySmall)
                        }
                        if (!ntf.isRead) {
                            TextButton(onClick = { onMarkNotificationRead(ntf.id) }) {
                                Icon(Icons.Filled.MarkEmailRead, contentDescription = "Mark read", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("Mark Read")
                            }
                        } else {
                            Icon(Icons.Filled.CheckCircle, contentDescription = "Read", tint = SafeGreen600, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
