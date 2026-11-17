package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceStatus
import com.example.data.model.Journey
import com.example.data.model.MovementStatus
import com.example.data.model.School
import com.example.data.model.Student
import com.example.ui.components.AttendanceStatusBadge
import com.example.ui.components.DeviceGeofenceMonitorCard
import com.example.ui.components.EmergencyReportDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.JourneyLocationCard
import com.example.ui.components.LocationConsentBanner
import com.example.ui.components.MovementStatusBadge
import com.example.ui.theme.DelayAmber700
import com.example.ui.theme.EmergencyRed600
import com.example.ui.theme.SafeBlue100
import com.example.ui.theme.SafeBlue700
import com.example.ui.theme.SafeGreen100
import com.example.ui.theme.SafeGreen600
import com.example.ui.theme.SafeGreen700
import com.example.util.GeofenceMonitoringConfig
import com.example.util.LocationTracker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParentDashboardScreen(
    school: School?,
    students: List<Student>,
    journeys: List<Journey>,
    attendance: List<AttendanceRecord>,
    locationConsentGranted: Boolean,
    gpsStatusLabel: String,
    geofenceStatus: GeofenceMonitoringConfig,
    perimeterCheckStatus: String?,
    isBusy: Boolean,
    onToggleLocationConsent: (Boolean) -> Unit,
    onEnableGeofenceMonitoring: (List<Student>) -> Unit,
    onDisableGeofenceMonitoring: () -> Unit,
    onCheckPerimeterNow: (School) -> Unit,
    onStartMorningJourney: (Student, destination: String, manualNote: String) -> Unit,
    onConfirmSafeArrivalHome: (Journey) -> Unit,
    onUpdateJourneyStatus: (Journey, MovementStatus, String) -> Unit,
    onAddDestination: (Student, String) -> Unit,
    onReportIncident: (Student, journeyId: String?, severity: String, title: String, desc: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val todayKey = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    var selectedStudentId by remember(students) { mutableStateOf(students.firstOrNull()?.id.orEmpty()) }
    val selectedStudent = students.firstOrNull { it.id == selectedStudentId } ?: students.firstOrNull()

    var selectedDestination by remember(selectedStudent?.id, school?.name) {
        mutableStateOf(selectedStudent?.approvedDestinations?.firstOrNull() ?: school?.name ?: "School")
    }
    var manualNote by remember { mutableStateOf("") }
    var showAddDestDialog by remember { mutableStateOf(false) }
    var newDestName by remember { mutableStateOf("") }
    var incidentTargetJourney by remember { mutableStateOf<Journey?>(null) }
    var showIncidentDialog by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        onToggleLocationConsent(granted)
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
            Column(modifier = Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LocationConsentBanner(
                    consentGranted = locationConsentGranted,
                    onToggleConsent = { desired ->
                        if (desired && !LocationTracker.hasLocationPermission(context)) {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        } else {
                            onToggleLocationConsent(desired)
                        }
                    }
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SafeBlue100.copy(alpha = 0.45f)
                ) {
                    Text(
                        text = "GPS Status: $gpsStatusLabel",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                DeviceGeofenceMonitorCard(
                    school = school,
                    students = students.filter { it.status == "active" },
                    geofenceStatus = geofenceStatus,
                    perimeterCheckStatus = perimeterCheckStatus,
                    isBusy = isBusy,
                    onEnableMonitoring = onEnableGeofenceMonitoring,
                    onDisableMonitoring = onDisableGeofenceMonitoring,
                    onCheckPerimeterNow = onCheckPerimeterNow
                )
            }
        }

        if (students.isEmpty()) {
            item {
                Column(modifier = Modifier.widthIn(max = 760.dp)) {
                    EmptyStateCard(
                        title = "No Linked Children Yet",
                        subtitle = "Ask your School Administrator to link your Google account to your child's profile."
                    )
                }
            }
        } else {
            item {
                Column(modifier = Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("My Linked Children", style = MaterialTheme.typography.titleLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        students.forEach { stu ->
                            FilterChip(
                                selected = stu.id == selectedStudent?.id,
                                onClick = {
                                    selectedStudentId = stu.id
                                    selectedDestination = stu.approvedDestinations.firstOrNull() ?: school?.name ?: "School"
                                },
                                label = { Text("${stu.fullName} (${stu.className})") }
                            )
                        }
                    }
                }
            }

            if (selectedStudent != null) {
                val activeJourneys = journeys.filter {
                    it.studentId == selectedStudent.id &&
                        it.status != MovementStatus.ARRIVED_HOME.key &&
                        it.status != MovementStatus.AT_APPROVED_ALTERNATIVE_DESTINATION.key &&
                        it.status != MovementStatus.ARRIVED_AT_SCHOOL.key
                }

                item {
                    Card(
                        modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(selectedStudent.fullName, style = MaterialTheme.typography.titleLarge)
                                    Text(
                                        "${selectedStudent.className} • Adm #${selectedStudent.admissionNumber}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        incidentTargetJourney = activeJourneys.firstOrNull()
                                        showIncidentDialog = true
                                    }
                                ) {
                                    Icon(Icons.Filled.Warning, contentDescription = null, tint = EmergencyRed600, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(4.dp))
                                    Text("Emergency", color = EmergencyRed600)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Approved Destinations", style = MaterialTheme.typography.titleSmall)
                                TextButton(onClick = { showAddDestDialog = true }) {
                                    Icon(Icons.Filled.AddLocationAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(4.dp))
                                    Text("Add")
                                }
                            }

                            val destinations = selectedStudent.approvedDestinations.ifEmpty {
                                listOf(school?.name ?: "School")
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                destinations.forEach { dest ->
                                    FilterChip(
                                        selected = selectedDestination == dest,
                                        onClick = { selectedDestination = dest },
                                        label = { Text(dest) }
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = manualNote,
                                onValueChange = { manualNote = it },
                                label = { Text("Manual Journey Note (optional, used if GPS/data is limited)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Button(
                                onClick = {
                                    onStartMorningJourney(selectedStudent, selectedDestination, manualNote)
                                    manualNote = ""
                                },
                                enabled = !isBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("start_morning_journey_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = SafeBlue700)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text("ON THE WAY TO SCHOOL")
                            }
                            Text(
                                text = "Note: Pressing ON THE WAY notifies teachers only. School attendance is marked Present solely after a teacher physically confirms arrival.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                val todayRecord = attendance.firstOrNull { it.studentId == selectedStudent.id && it.dateKey == todayKey }
                item {
                    Card(
                        modifier = Modifier
                            .widthIn(max = 760.dp)
                            .fillMaxWidth()
                            .testTag("parent_today_attendance_card"),
                        colors = CardDefaults.cardColors(
                            containerColor = when (todayRecord?.status) {
                                AttendanceStatus.PRESENT.key -> SafeGreen100.copy(alpha = 0.4f)
                                AttendanceStatus.ABSENT.key -> EmergencyRed600.copy(alpha = 0.08f)
                                AttendanceStatus.LATE.key -> DelayAmber700.copy(alpha = 0.08f)
                                else -> MaterialTheme.colorScheme.surface
                            }
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Today's Attendance Status (Real-Time)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${selectedStudent.fullName} • $todayKey",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                AttendanceStatusBadge(todayRecord?.status ?: AttendanceStatus.PENDING_CONFIRMATION.key)
                            }
                            if (todayRecord != null) {
                                Text(
                                    text = buildString {
                                        append("Recorded by ${todayRecord.recordedByName}")
                                        if (!todayRecord.arrivalTime.isNullOrBlank()) {
                                            append(" at ${todayRecord.arrivalTime}")
                                        }
                                        if (todayRecord.isOfflineSynced) {
                                            append(" (synced from offline register)")
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SafeGreen700
                                )
                                if (!todayRecord.correctionReason.isNullOrBlank()) {
                                    Text(
                                        text = "Teacher note: ${todayRecord.correctionReason}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                Text(
                                    text = "Awaiting teacher roll call or physical gate arrival confirmation for today.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                val studentJourneys = journeys.filter { it.studentId == selectedStudent.id }
                item {
                    Column(modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                        Text("Active & Recent Journeys", style = MaterialTheme.typography.titleLarge)
                    }
                }

                if (studentJourneys.isEmpty()) {
                    item {
                        Column(modifier = Modifier.widthIn(max = 760.dp)) {
                            EmptyStateCard(
                                title = "No Journeys Recorded Yet",
                                subtitle = "Start a morning journey above when your child leaves for school."
                            )
                        }
                    }
                } else {
                    items(studentJourneys.take(10), key = { it.id }) { journey ->
                        Card(
                            modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(
                                            imageVector = if (journey.direction == "morning_to_school") Icons.Filled.School else Icons.Filled.Home,
                                            contentDescription = null,
                                            tint = SafeBlue700
                                        )
                                        Column {
                                            Text(journey.destinationName, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "Initiated by ${journey.initiatedByName} (${journey.initiatedByRole})",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    MovementStatusBadge(journey.status)
                                }

                                JourneyLocationCard(journey = journey)

                                val isInProgress = journey.status in listOf(
                                    MovementStatus.ON_THE_WAY_TO_SCHOOL.key,
                                    MovementStatus.ON_THE_WAY_HOME.key,
                                    MovementStatus.JOURNEY_DELAYED.key
                                )
                                if (isInProgress) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { onUpdateJourneyStatus(journey, MovementStatus.JOURNEY_DELAYED, "Delayed in transit") },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Filled.Schedule, contentDescription = null, tint = DelayAmber700, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.size(4.dp))
                                            Text("Report Delay")
                                        }
                                        if (journey.direction != "morning_to_school") {
                                            Button(
                                                onClick = { onConfirmSafeArrivalHome(journey) },
                                                modifier = Modifier
                                                    .weight(1.3f)
                                                    .testTag("confirm_arrived_home_button"),
                                                colors = ButtonDefaults.buttonColors(containerColor = SafeGreen600)
                                            ) {
                                                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.size(4.dp))
                                                Text("Confirm Arrived Home")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val studentAttendance = attendance.filter { it.studentId == selectedStudent.id }
                item {
                    Column(modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                        Text("Confirmed School Attendance History", style = MaterialTheme.typography.titleLarge)
                    }
                }
                if (studentAttendance.isEmpty()) {
                    item {
                        Text(
                            "No confirmed attendance records yet for ${selectedStudent.fullName}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth()
                        )
                    }
                } else {
                    items(studentAttendance.take(14), key = { it.id }) { record ->
                        Card(
                            modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(record.dateKey, style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        "Recorded by ${record.recordedByName}${record.arrivalTime?.let { " at $it" } ?: ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (!record.correctionReason.isNullOrBlank()) {
                                        Text(
                                            "Note: ${record.correctionReason}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                AttendanceStatusBadge(record.status)
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showAddDestDialog && selectedStudent != null) {
        AlertDialog(
            onDismissRequest = { showAddDestDialog = false },
            title = { Text("Add Approved Destination") },
            text = {
                OutlinedTextField(
                    value = newDestName,
                    onValueChange = { newDestName = it },
                    label = { Text("Destination Label & Area") },
                    placeholder = { Text("e.g., Grandma's Home - Airport Residential") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddDestination(selectedStudent, newDestName)
                        newDestName = ""
                        showAddDestDialog = false
                    },
                    enabled = newDestName.trim().length >= 2
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDestDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showIncidentDialog && selectedStudent != null) {
        EmergencyReportDialog(
            studentName = selectedStudent.fullName,
            onDismiss = { showIncidentDialog = false },
            onSubmit = { sev, title, desc ->
                onReportIncident(selectedStudent, incidentTargetJourney?.id, sev, title, desc)
                showIncidentDialog = false
            }
        )
    }
}
