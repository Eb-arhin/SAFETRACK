package com.example.ui.screens

import android.Manifest
import android.os.Build
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AppNotification
import com.example.data.model.Incident
import com.example.data.model.Message
import com.example.data.model.Student
import com.example.data.model.UserRole
import com.example.ui.components.EmergencyReportDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.LocationConsentBanner
import com.example.ui.theme.EmergencyRed600
import com.example.ui.theme.SafeBlue100
import com.example.ui.theme.SafeGreen600
import com.example.util.NotificationHelper

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SafetyCommsScreen(
    role: UserRole,
    students: List<Student>,
    notifications: List<AppNotification>,
    messages: List<Message>,
    incidents: List<Incident>,
    locationConsentGranted: Boolean,
    isBusy: Boolean = false,
    onToggleConsent: (Boolean) -> Unit,
    onMarkNotificationRead: (String) -> Unit,
    onSendMessage: (Student, String) -> Unit,
    onReportIncident: (Student, journeyId: String?, severity: String, title: String, desc: String) -> Unit,
    onResolveIncident: (Incident, String) -> Unit,
    onDeleteAccount: () -> Unit = {},
    onOpenGuide: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var tabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Alerts (${notifications.count { !it.isRead }})", "Messages", "Incidents", "Privacy")

    var selectedStudentId by remember(students) { mutableStateOf(students.firstOrNull()?.id.orEmpty()) }
    val selectedStudent = students.firstOrNull { it.id == selectedStudentId } ?: students.firstOrNull()
    var messageBody by remember { mutableStateOf("") }
    var showIncidentDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    Column(modifier = modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        ScrollableTabRow(selectedTabIndex = tabIndex, edgePadding = 16.dp) {
            tabs.forEachIndexed { idx, label ->
                Tab(selected = tabIndex == idx, onClick = { tabIndex = idx }, text = { Text(label) })
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            when (tabIndex) {
                0 -> {
                    if (notifications.isEmpty()) {
                        item {
                            Column(modifier = Modifier.widthIn(max = 760.dp)) {
                                EmptyStateCard("No Notifications Yet", "Journey updates, physical arrivals, dismissals, and attendance changes appear here.")
                            }
                        }
                    } else {
                        items(notifications, key = { it.id }) { notif ->
                            Card(
                                modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (notif.isRead) MaterialTheme.colorScheme.surface else SafeBlue100.copy(alpha = 0.45f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(notif.title, style = MaterialTheme.typography.titleSmall)
                                        Text(notif.body, style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (!notif.isRead) {
                                        OutlinedButton(onClick = { onMarkNotificationRead(notif.id) }) {
                                            Text("Mark Read")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    item {
                        Column(modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Secure Parent-Teacher Messaging", style = MaterialTheme.typography.titleLarge)
                            if (students.isNotEmpty()) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    students.forEach { stu ->
                                        FilterChip(
                                            selected = stu.id == selectedStudent?.id,
                                            onClick = { selectedStudentId = stu.id },
                                            label = { Text(stu.fullName) }
                                        )
                                    }
                                }
                            }
                            if (selectedStudent != null) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = messageBody,
                                        onValueChange = { messageBody = it },
                                        label = { Text("Message regarding ${selectedStudent.fullName}") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            onSendMessage(selectedStudent, messageBody)
                                            messageBody = ""
                                        },
                                        enabled = messageBody.trim().isNotEmpty()
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message")
                                    }
                                }
                            }
                        }
                    }
                    val filteredMsgs = messages.filter { selectedStudent == null || it.studentId == selectedStudent.id }
                    items(filteredMsgs, key = { it.id }) { msg ->
                        Card(modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("${msg.senderName} (${msg.senderRole}) • ${msg.studentName}", style = MaterialTheme.typography.labelMedium)
                                Text(msg.body, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                2 -> {
                    item {
                        Column(modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (selectedStudent != null) {
                                Button(
                                    onClick = { showIncidentDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed600),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.Warning, contentDescription = null)
                                    Spacer(Modifier.size(6.dp))
                                    Text("Report Emergency / Safety Incident")
                                }
                            }
                        }
                    }
                    if (incidents.isEmpty()) {
                        item {
                            Column(modifier = Modifier.widthIn(max = 760.dp)) {
                                EmptyStateCard("No Active Incidents", "Emergency reports and safety alerts are logged here with restricted access.")
                            }
                        }
                    } else {
                        items(incidents, key = { it.id }) { inc ->
                            Card(
                                modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                                border = BorderStroke(1.dp, EmergencyRed600.copy(alpha = 0.35f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("[${inc.severity.uppercase()}] ${inc.title} — ${inc.studentName}", style = MaterialTheme.typography.titleSmall)
                                    Text(inc.description, style = MaterialTheme.typography.bodySmall)
                                    Text("Status: ${inc.status.uppercase()} • Reported by ${inc.reportedByName}", style = MaterialTheme.typography.labelSmall)
                                    if (role == UserRole.TEACHER || role == UserRole.SCHOOL_ADMIN || role == UserRole.SUPER_ADMIN) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(onClick = { onResolveIncident(inc, "investigating") }) { Text("Investigating") }
                                            Button(
                                                onClick = { onResolveIncident(inc, "resolved") },
                                                colors = ButtonDefaults.buttonColors(containerColor = SafeGreen600)
                                            ) { Text("Resolve") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                3 -> {
                    item {
                        Column(modifier = Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            LocationConsentBanner(consentGranted = locationConsentGranted, onToggleConsent = onToggleConsent)
                            val notifGranted = NotificationHelper.hasNotificationPermission(context)
                            Card {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(
                                            imageVector = if (notifGranted) Icons.Filled.CheckCircle else Icons.Filled.Notifications,
                                            contentDescription = null,
                                            tint = if (notifGranted) SafeGreen600 else MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = if (notifGranted) "Device Notifications Enabled" else "Device Notifications Not Permitted Yet",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }
                                    Text(
                                        text = if (notifGranted) {
                                            "Arrival, dismissal, attendance, and perimeter alerts can appear on this device while the app session is active."
                                        } else {
                                            "Allow Android notifications to see real-time arrival, departure, and perimeter updates on this device."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notifGranted) {
                                        Button(
                                            onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Icon(Icons.Filled.Notifications, contentDescription = null)
                                            Spacer(Modifier.size(6.dp))
                                            Text("Allow Notifications")
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = onOpenGuide,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .testTag("privacy_open_guide_button")
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.size(6.dp))
                                        Text("View 5-Slide SafeTrack Usage & Safety Guide")
                                    }
                                }
                            }
                            Card {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Filled.Security, contentDescription = null)
                                        Text("Data Protection & Play Safety Compliance", style = MaterialTheme.typography.titleMedium)
                                    }
                                    Text(
                                        "• Ghana Data Protection Act, 2012 (Act 843): Pupil records, medical notes, and GPS coordinates are strictly isolated per school and restricted to authorised guardians and staff.\n" +
                                            "• GPS Transparency: Foreground location is collected only when consented and during an active authorised journey, and stops immediately on arrival.\n" +
                                            "• Zero Broad Storage Permissions: Pupil photos use the zero-permission Android Photo Picker.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            Card {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Filled.Devices, contentDescription = null)
                                        Text("All-Device & Store Distribution Readiness", style = MaterialTheme.typography.titleMedium)
                                    }
                                    Text(
                                        "SafeTrack is configured for Android phones, tablets, foldables, Chromebooks, and desktop computers (Android 7.0+ through Android 16). GPS, camera, and touchscreen hardware are optional so Wi-Fi tablets and computers run smoothly.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            Card(border = BorderStroke(1.dp, EmergencyRed600.copy(alpha = 0.35f))) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = EmergencyRed600)
                                        Text("Account & Data Deletion", style = MaterialTheme.typography.titleMedium)
                                    }
                                    Text(
                                        "You can permanently remove your user profile, membership links, and cached offline data on this device at any time.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    OutlinedButton(
                                        onClick = { showDeleteAccountDialog = true },
                                        enabled = !isBusy,
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = EmergencyRed600),
                                        modifier = Modifier.testTag("delete_account_button")
                                    ) {
                                        Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.size(6.dp))
                                        Text("Delete My Account & Profile")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showIncidentDialog && selectedStudent != null) {
        EmergencyReportDialog(
            studentName = selectedStudent.fullName,
            onDismiss = { showIncidentDialog = false },
            onSubmit = { sev, title, desc ->
                onReportIncident(selectedStudent, null, sev, title, desc)
                showIncidentDialog = false
            }
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("Delete Account & Profile Data?") },
            text = {
                Text(
                    "This removes your SafeTrack user profile, school membership index links, active device geofences, and local offline queue, then signs you out."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountDialog = false
                        onDeleteAccount()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed600)
                ) { Text("Delete & Sign Out") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) { Text("Cancel") }
            }
        )
    }
}
