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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.AppSettingsState
import com.example.data.model.AppNotification
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceStatus
import com.example.data.model.AuditLog
import com.example.data.model.FeeRecord
import com.example.data.model.Incident
import com.example.data.model.Journey
import com.example.data.model.MovementStatus
import com.example.data.model.School
import com.example.data.model.SchoolMember
import com.example.data.model.Student
import com.example.data.model.UserRole
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.InteractiveGeofenceMapPreview
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.MovementStatusBadge
import com.example.ui.components.SchoolBrandCrest as ComponentSchoolCrest
import com.example.ui.theme.DelayAmber700
import com.example.ui.theme.EmergencyRed600
import com.example.ui.theme.ReportGold
import com.example.ui.theme.SafeBlue700
import com.example.ui.theme.SafeGreen600
import com.example.ui.theme.SafeGreen700
import com.example.util.GeofenceMonitoringConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class QuickShortcut(
    val key: String,
    val label: String,
    val icon: ImageVector
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExecutiveDashboardScreen(
    role: UserRole,
    displayName: String,
    school: School?,
    students: List<Student>,
    members: List<SchoolMember>,
    attendance: List<AttendanceRecord>,
    journeys: List<Journey>,
    incidents: List<Incident>,
    notifications: List<AppNotification>,
    auditLogs: List<AuditLog>,
    feeRecords: List<FeeRecord>,
    geofenceStatus: GeofenceMonitoringConfig,
    lastGpsAccuracyMeters: Float?,
    appSettings: AppSettingsState,
    onNavigateSection: (String) -> Unit,
    onOpenSettingsTab: () -> Unit,
    onMarkNotificationRead: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val todayKey = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val todayAttendance = remember(attendance, todayKey) {
        val todays = attendance.filter { it.dateKey == todayKey }
        todays.ifEmpty { attendance }
    }
    val presentCount = todayAttendance.count {
        it.status == AttendanceStatus.PRESENT.key || it.status == AttendanceStatus.LATE.key
    }
    val attendanceRate = if (todayAttendance.isNotEmpty()) {
        "${(presentCount * 100 / todayAttendance.size)}%"
    } else "—"
    val openIncidents = incidents.count { it.status != "resolved" }
    val activeJourneys = journeys.count {
        it.status == MovementStatus.ON_THE_WAY_TO_SCHOOL.key ||
            it.status == MovementStatus.ON_THE_WAY_HOME.key
    }
    val unreadNotifs = remember(notifications) { notifications.filter { !it.isRead } }
    val cardPad = if (appSettings.compactCards) 12.dp else 16.dp

    val shortcuts = remember(role) {
        buildList {
            if (role == UserRole.SCHOOL_ADMIN || role == UserRole.TEACHER || role == UserRole.SUPER_ADMIN) {
                add(QuickShortcut("classes_pupils", "Classes & Pupils", Icons.Filled.Class))
                add(QuickShortcut("staff_inventory", "Staff & Inventory", Icons.Filled.Inventory2))
            }
            add(QuickShortcut("attendance_movement", "Attendance & Movement", Icons.Filled.FactCheck))
            add(QuickShortcut("academics", "Academics", Icons.Filled.MenuBook))
            add(QuickShortcut("safety_security", "Safety & Security", Icons.Filled.Security))
            add(QuickShortcut("finance", "Finance", Icons.Filled.AccountBalanceWallet))
            add(QuickShortcut("notifications", "Notifications", Icons.Filled.Notifications))
            add(QuickShortcut("settings", "Settings & Share", Icons.Filled.Settings))
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (appSettings.compactCards) 10.dp else 14.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        // Hero Welcome + School Identity Card
        item {
            Card(
                modifier = Modifier.widthIn(max = 920.dp).fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(cardPad), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ComponentSchoolCrest(school = school, size = 56.dp)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = "Welcome back, ${displayName.ifBlank { role.label }}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${school?.name ?: "SafeTrack Campus"} • ${role.label}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (school != null) {
                                Text(
                                    text = buildString {
                                        if (school.motto.isNotBlank()) {
                                            append("\"${school.motto}\" • ")
                                        }
                                        append("${school.academicYear} • ${school.currentTerm}")
                                        if (school.operatingHours.isNotBlank()) {
                                            append(" • ${school.operatingHours}")
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = ReportGold.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, ReportGold.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = school?.planName ?: "Active",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        // Core KPIs (Real live data)
        item {
            FlowRow(
                modifier = Modifier.widthIn(max = 920.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                maxItemsInEachRow = 4
            ) {
                KpiMetricCard(
                    label = if (role == UserRole.PARENT) "My Children" else "Pupil Enrolment",
                    value = students.size.toString(),
                    subtitle = "${students.count { it.status == "active" }} active",
                    icon = Icons.Filled.Groups,
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCard(
                    label = if (role == UserRole.PARENT) "Active Trips" else "Total Staff",
                    value = if (role == UserRole.PARENT) activeJourneys.toString() else members.size.toString(),
                    subtitle = if (role == UserRole.PARENT) "Morning & home routes" else "${members.count { it.role == UserRole.TEACHER.key }} teachers",
                    icon = if (role == UserRole.PARENT) Icons.AutoMirrored.Filled.DirectionsWalk else Icons.Filled.Badge,
                    accentColor = SafeBlue700,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCard(
                    label = "Daily Attendance",
                    value = attendanceRate,
                    subtitle = "$presentCount/${todayAttendance.size} marked",
                    icon = Icons.Filled.FactCheck,
                    accentColor = SafeGreen600,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCard(
                    label = "Open Incidents",
                    value = openIncidents.toString(),
                    subtitle = "${unreadNotifs.size} unread alerts",
                    icon = Icons.Filled.Warning,
                    accentColor = if (openIncidents > 0) EmergencyRed600 else SafeGreen700,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Quick Action Shortcuts
        item {
            Card(
                modifier = Modifier.widthIn(max = 920.dp).fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(cardPad), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Quick Actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        shortcuts.forEach { item ->
                            OutlinedButton(
                                onClick = {
                                    if (item.key == "settings") onOpenSettingsTab()
                                    else onNavigateSection(item.key)
                                },
                                modifier = Modifier.testTag("dashboard_shortcut_${item.key}")
                            ) {
                                Icon(item.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(6.dp))
                                Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

        // Parent / Teacher Live Child Movement Summary
        if (role == UserRole.PARENT && students.isNotEmpty()) {
            item {
                Column(modifier = Modifier.widthIn(max = 920.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Linked Children Live Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    students.forEach { child ->
                        val latestJourney = journeys.firstOrNull { it.studentId == child.id }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(child.fullName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text("${child.className} • ${child.admissionNumber}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (latestJourney != null) {
                                    MovementStatusBadge(latestJourney.status)
                                } else {
                                    Text("No trips today", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }

        // School Geofencing Overview (respects Settings toggle)
        if (appSettings.showDashboardGeofence && school != null) {
            item {
                Card(
                    modifier = Modifier.widthIn(max = 920.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                ) {
                    Column(modifier = Modifier.padding(cardPad), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.Radar, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text("School Geofencing Overview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = if (geofenceStatus.isActive) {
                                            "Monitoring active for ${geofenceStatus.trackedStudents.size} pupil(s)"
                                        } else if (school.geofenceEnabled) {
                                            "Campus boundary ready (${school.geofenceRadiusMeters.toInt()}m radius)"
                                        } else {
                                            "Campus coordinates pending setup"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            TextButton(onClick = { onNavigateSection("attendance_movement") }) {
                                Text("Manage")
                            }
                        }
                        InteractiveGeofenceMapPreview(
                            schoolName = school.name,
                            latitude = school.geofenceLatitude,
                            longitude = school.geofenceLongitude,
                            radiusMeters = school.geofenceRadiusMeters,
                            enabled = school.geofenceEnabled,
                            accuracyMeters = lastGpsAccuracyMeters
                        )
                    }
                }
            }
        }

        // Notifications Summary
        item {
            Card(
                modifier = Modifier.widthIn(max = 920.dp).fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
            ) {
                Column(modifier = Modifier.padding(cardPad), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Notification Summary (${unreadNotifs.size} unread)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = { onNavigateSection("notifications") }) {
                            Text("View All")
                        }
                    }
                    if (notifications.isEmpty()) {
                        Text(
                            "No alerts or movement notifications yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        notifications.take(4).forEachIndexed { index, ntf ->
                            if (index > 0) HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(ntf.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(ntf.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                if (!ntf.isRead) {
                                    TextButton(onClick = { onMarkNotificationRead(ntf.id) }) { Text("Read") }
                                } else {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = "Read", tint = SafeGreen600, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Fee Ledger Quick Snapshot if available
        if (feeRecords.isNotEmpty()) {
            val totalDue = feeRecords.sumOf { it.amountDueGhs }
            val totalPaid = feeRecords.sumOf { it.amountPaidGhs }
            item {
                Card(
                    modifier = Modifier.widthIn(max = 920.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(cardPad),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Finance Snapshot", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Collected GH₵${"%.2f".format(totalPaid)} of GH₵${"%.2f".format(totalDue)} across ${feeRecords.size} invoice(s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(onClick = { onNavigateSection("finance") }) { Text("Open Finance") }
                    }
                }
            }
        }

        // Recent School Activities (audit logs)
        if (appSettings.showDashboardActivities && (role == UserRole.SCHOOL_ADMIN || role == UserRole.SUPER_ADMIN)) {
            item {
                Card(
                    modifier = Modifier.widthIn(max = 920.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                ) {
                    Column(modifier = Modifier.padding(cardPad), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Recent School Activities (${auditLogs.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        if (auditLogs.isEmpty()) {
                            EmptyStateCard("No Activity Logged Yet", "Attendance updates, enrollment changes, and security actions appear here.")
                        } else {
                            auditLogs.take(6).forEachIndexed { idx, log ->
                                if (idx > 0) HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("${log.action} • ${log.actorName} (${log.actorRole})", style = MaterialTheme.typography.titleSmall)
                                    Text(log.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (!log.reason.isNullOrBlank()) {
                                        Text("Reason: ${log.reason}", style = MaterialTheme.typography.labelSmall, color = DelayAmber700)
                                    }
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
