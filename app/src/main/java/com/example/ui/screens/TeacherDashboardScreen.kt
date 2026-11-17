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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.example.data.local.OfflineAttendanceEntity
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceStatus
import com.example.data.model.ClassDepartureEntry
import com.example.data.model.Journey
import com.example.data.model.MovementStatus
import com.example.data.model.School
import com.example.data.model.SchoolClass
import com.example.data.model.SchoolMember
import com.example.data.model.Student
import com.example.data.model.UserRole
import com.example.ui.components.AttendanceStatusBadge
import com.example.ui.components.ClassDepartureTableCard
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.EditTeacherMemberDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.JourneyLocationCard
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.MovementStatusBadge
import com.example.ui.components.TeachersDirectoryCard
import com.example.ui.theme.DelayAmber700
import com.example.ui.theme.EmergencyRed600
import com.example.ui.theme.SafeBlue700
import com.example.ui.theme.SafeGreen600
import com.example.ui.theme.SafeGreen100
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeacherDashboardScreen(
    school: School?,
    classes: List<SchoolClass>,
    students: List<Student>,
    subjects: List<com.example.data.model.Subject> = emptyList(),
    members: List<SchoolMember>,
    journeys: List<Journey>,
    attendance: List<AttendanceRecord>,
    pendingOffline: List<OfflineAttendanceEntity>,
    isOfflineCaptureMode: Boolean,
    isBusy: Boolean,
    currentUserId: String = "",
    onToggleOfflineMode: (Boolean) -> Unit,
    onSyncOffline: () -> Unit,
    onOpenDailyAttendance: () -> Unit,
    onConfirmArrival: (Journey, Student, markAsLate: Boolean) -> Unit,
    onInitiateDismissal: (Student, destination: String, isAlternative: Boolean, manualNote: String) -> Unit,
    onDispatchClassDeparture: (entries: List<ClassDepartureEntry>, notifyLateExceptions: Boolean) -> Unit,
    onRecordAttendance: (Student, dateKey: String, AttendanceStatus, reason: String) -> Unit,
    onUpdateStudentProfile: (
        student: Student,
        admissionNumber: String,
        fullName: String,
        schoolClass: SchoolClass,
        emergencyName: String,
        emergencyPhone: String,
        medicalNotes: String,
        approvedDestinations: List<String>,
        authorisedPickups: List<String>
    ) -> Unit,
    onDeleteStudent: (Student) -> Unit,
    onSaveTeacherMember: (
        existingMember: SchoolMember?,
        displayName: String,
        email: String,
        phone: String,
        role: UserRole,
        assignedClassIds: List<String>,
        assignedSubjectIds: List<String>,
        assignedSubjectNames: List<String>,
        status: String
    ) -> Unit,
    onDeleteTeacherMember: (SchoolMember) -> Unit,
    modifier: Modifier = Modifier
) {
    val todayKey = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val todayAttendance = attendance.filter { it.dateKey == todayKey }
    val presentCount = todayAttendance.count { it.status == AttendanceStatus.PRESENT.key }
    val absentCount = todayAttendance.count { it.status == AttendanceStatus.ABSENT.key }
    val lateCount = todayAttendance.count { it.status == AttendanceStatus.LATE.key }
    val onTheWayCount = journeys.count { it.status == MovementStatus.ON_THE_WAY_TO_SCHOOL.key }
    val arrivedCount = journeys.count { it.status == MovementStatus.ARRIVED_AT_SCHOOL.key }
    val dismissedCount = journeys.count { it.status == MovementStatus.ON_THE_WAY_HOME.key }
    val pendingConfirmCount = (students.size - todayAttendance.size).coerceAtLeast(0) +
        todayAttendance.count { it.status == AttendanceStatus.PENDING_CONFIRMATION.key }

    var dismissTargetStudent by remember { mutableStateOf<Student?>(null) }
    var dismissDest by remember { mutableStateOf("") }
    var dismissNote by remember { mutableStateOf("") }
    var dismissIsAlternative by remember { mutableStateOf(false) }

    var correctionTarget by remember { mutableStateOf<Pair<Student, AttendanceStatus>?>(null) }
    var correctionReason by remember { mutableStateOf("") }
    var editingStudent by remember { mutableStateOf<Student?>(null) }
    var deletingStudent by remember { mutableStateOf<Student?>(null) }
    var editingMember by remember { mutableStateOf<SchoolMember?>(null) }
    var showTeacherDialog by remember { mutableStateOf(false) }
    var deletingMember by remember { mutableStateOf<SchoolMember?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        item {
            Column(modifier = Modifier.widthIn(max = 840.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Teacher Daily Operations • $todayKey", style = MaterialTheme.typography.titleLarge)

                Button(
                    onClick = onOpenDailyAttendance,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("open_daily_attendance_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = SafeBlue700)
                ) {
                    Icon(Icons.Filled.FactCheck, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Open Daily Attendance Roll Call (Present / Tardy / Absent)")
                }

                // Offline Capture Mode & Sync Banner
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isOfflineCaptureMode || pendingOffline.isNotEmpty()) {
                            SafeGreen100.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.CloudOff, contentDescription = null, tint = SafeBlue700)
                                Column {
                                    Text("Offline Attendance Capture", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${pendingOffline.size} queued record(s) awaiting cloud sync",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(checked = isOfflineCaptureMode, onCheckedChange = onToggleOfflineMode)
                        }
                        if (pendingOffline.isNotEmpty()) {
                            Button(
                                onClick = onSyncOffline,
                                enabled = !isBusy,
                                modifier = Modifier.fillMaxWidth().testTag("sync_offline_button")
                            ) {
                                Icon(Icons.Filled.CloudSync, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text("Sync ${pendingOffline.size} Offline Record(s) Now")
                            }
                        }
                    }
                }

                // 8 Teacher KPI Metrics in responsive 2x4 FlowRow
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    maxItemsInEachRow = 4
                ) {
                    KpiMetricCard("Total Pupils", students.size.toString(), Icons.Filled.Groups, modifier = Modifier.weight(1f))
                    KpiMetricCard("Present", presentCount.toString(), Icons.Filled.CheckCircle, accentColor = SafeGreen600, modifier = Modifier.weight(1f))
                    KpiMetricCard("Absent", absentCount.toString(), Icons.Filled.PersonOff, accentColor = EmergencyRed600, modifier = Modifier.weight(1f))
                    KpiMetricCard("Tardy", lateCount.toString(), Icons.Filled.Schedule, accentColor = DelayAmber700, modifier = Modifier.weight(1f))
                    KpiMetricCard("On the Way", onTheWayCount.toString(), Icons.AutoMirrored.Filled.DirectionsWalk, accentColor = SafeBlue700, modifier = Modifier.weight(1f))
                    KpiMetricCard("Arrived", arrivedCount.toString(), Icons.Filled.Home, accentColor = SafeGreen600, modifier = Modifier.weight(1f))
                    KpiMetricCard("Dismissed", dismissedCount.toString(), Icons.Filled.DirectionsBus, accentColor = SafeBlue700, modifier = Modifier.weight(1f))
                    KpiMetricCard("Pending", pendingConfirmCount.toString(), Icons.Filled.HourglassTop, accentColor = DelayAmber700, modifier = Modifier.weight(1f))
                }
            }
        }

        // Whole-Class Single-Click Departure Alert & Late Exceptions Table
        if (students.any { it.status == "active" }) {
            item {
                Column(modifier = Modifier.widthIn(max = 840.dp)) {
                    ClassDepartureTableCard(
                        school = school,
                        classes = classes,
                        students = students,
                        isBusy = isBusy,
                        onDispatchClassDeparture = onDispatchClassDeparture,
                        currentUserId = currentUserId,
                        currentUserRole = UserRole.TEACHER
                    )
                }
            }
        }

        // Morning Arrival Confirmation Queue
        val incomingQueue = journeys.filter { it.status == MovementStatus.ON_THE_WAY_TO_SCHOOL.key }
        item {
            Column(modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth()) {
                Text("On The Way — Confirm Physical Arrival (${incomingQueue.size})", style = MaterialTheme.typography.titleLarge)
            }
        }
        if (incomingQueue.isEmpty()) {
            item {
                Column(modifier = Modifier.widthIn(max = 840.dp)) {
                    EmptyStateCard(
                        title = "No Pupils Currently En Route to School",
                        subtitle = "When parents press ON THE WAY TO SCHOOL, pupils appear here for physical gate/class confirmation."
                    )
                }
            }
        } else {
            items(incomingQueue, key = { it.id }) { journey ->
                val student = students.firstOrNull { it.id == journey.studentId }
                Card(
                    modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                    border = BorderStroke(1.dp, SafeBlue700.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(journey.studentName, style = MaterialTheme.typography.titleMedium)
                                Text("${journey.className} • Started by ${journey.initiatedByName}", style = MaterialTheme.typography.bodySmall)
                            }
                            MovementStatusBadge(journey.status)
                        }
                        JourneyLocationCard(journey)
                        if (student != null) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onConfirmArrival(journey, student, false) },
                                    enabled = !isBusy,
                                    modifier = Modifier.weight(1f).testTag("confirm_present_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = SafeGreen600)
                                ) {
                                    Text("Confirm Present")
                                }
                                OutlinedButton(
                                    onClick = { onConfirmArrival(journey, student, true) },
                                    enabled = !isBusy,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Confirm Late")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Class Register & Authorised Afternoon Dismissal
        item {
            Column(modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth()) {
                Text("Class Attendance Register & Authorised Dismissal", style = MaterialTheme.typography.titleLarge)
            }
        }
        if (students.isEmpty()) {
            item {
                Text(
                    "No assigned pupils yet. School Admin can assign you to a class and enroll pupils.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth()
                )
            }
        } else {
            items(students.filter { it.status == "active" }, key = { it.id }) { student ->
                val currentAtt = todayAttendance.firstOrNull { it.studentId == student.id }
                Card(
                    modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(student.fullName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${student.className} • ${student.admissionNumber}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!student.medicalNotes.isNullOrBlank()) {
                                    Text(
                                        "Medical note: ${student.medicalNotes}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = EmergencyRed600
                                    )
                                }
                            }
                            AttendanceStatusBadge(currentAtt?.status ?: AttendanceStatus.PENDING_CONFIRMATION.key)
                        }

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                AttendanceStatus.PRESENT,
                                AttendanceStatus.LATE,
                                AttendanceStatus.ABSENT,
                                AttendanceStatus.EXCUSED_ABSENCE,
                                AttendanceStatus.EARLY_DEPARTURE
                            ).forEach { targetStatus ->
                                FilterChip(
                                    selected = currentAtt?.status == targetStatus.key,
                                    onClick = {
                                        if (currentAtt != null && currentAtt.status != targetStatus.key && !isOfflineCaptureMode) {
                                            correctionReason = ""
                                            correctionTarget = student to targetStatus
                                        } else {
                                            onRecordAttendance(student, todayKey, targetStatus, "")
                                        }
                                    },
                                    label = { Text(if (targetStatus == AttendanceStatus.LATE) "Tardy" else targetStatus.label) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Authorised pickups: ${student.authorisedPickups.joinToString().ifBlank { "Guardian only" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { editingStudent = student },
                                    modifier = Modifier.testTag("edit_student_${student.id}")
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit ${student.fullName}", tint = SafeBlue700)
                                }
                                IconButton(
                                    onClick = { deletingStudent = student },
                                    modifier = Modifier.testTag("delete_student_${student.id}")
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete ${student.fullName}", tint = EmergencyRed600)
                                }
                                val isAssignedTeacher = currentUserId.isBlank() || currentUserId in student.teacherUids
                                Button(
                                    onClick = {
                                        dismissTargetStudent = student
                                        dismissDest = student.approvedDestinations.firstOrNull { !it.equals(school?.name, ignoreCase = true) }
                                            ?: "Home"
                                        dismissIsAlternative = false
                                        dismissNote = ""
                                    },
                                    enabled = !isBusy && isAssignedTeacher,
                                    modifier = Modifier.testTag("dismiss_pupil_button")
                                ) {
                                    Icon(Icons.Filled.DirectionsBus, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text(if (isAssignedTeacher) "Dismiss" else "Unassigned")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Teachers & Colleagues Directory (Editable & Deletable by Teachers & Admins)
        item {
            Column(modifier = Modifier.widthIn(max = 840.dp)) {
                TeachersDirectoryCard(
                    members = members,
                    classes = classes,
                    currentUserRole = UserRole.TEACHER,
                    onAddTeacher = {
                        editingMember = null
                        showTeacherDialog = true
                    },
                    onEditMember = { member ->
                        editingMember = member
                        showTeacherDialog = true
                    },
                    onDeleteMember = { member -> deletingMember = member }
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (dismissTargetStudent != null) {
        val stu = dismissTargetStudent!!
        val destinations = (stu.approvedDestinations.filter { !it.equals(school?.name, ignoreCase = true) } + "Home")
            .distinct()
        AlertDialog(
            onDismissRequest = { dismissTargetStudent = null },
            title = { Text("Authorise Dismissal — ${stu.fullName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select Approved Destination", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        destinations.forEach { d ->
                            FilterChip(
                                selected = dismissDest == d,
                                onClick = {
                                    dismissDest = d
                                    dismissIsAlternative = !d.contains("Home", ignoreCase = true)
                                },
                                label = { Text(d) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = dismissNote,
                        onValueChange = { dismissNote = it },
                        label = { Text("Pickup / Handover Note (optional)") },
                        placeholder = { Text("e.g., Released to Aunt Ama after ID check") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onInitiateDismissal(stu, dismissDest, dismissIsAlternative, dismissNote)
                        dismissTargetStudent = null
                    },
                    modifier = Modifier.testTag("confirm_dismissal_button")
                ) {
                    Text("DISMISS / ON THE WAY HOME")
                }
            },
            dismissButton = {
                TextButton(onClick = { dismissTargetStudent = null }) { Text("Cancel") }
            }
        )
    }

    if (correctionTarget != null) {
        val (stu, newStatus) = correctionTarget!!
        AlertDialog(
            onDismissRequest = { correctionTarget = null },
            title = { Text("Attendance Correction Reason Required") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Changing ${stu.fullName} to ${newStatus.label} requires an audit reason.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = correctionReason,
                        onValueChange = { correctionReason = it },
                        label = { Text("Reason (min 3 characters) *") },
                        placeholder = { Text("e.g., Late bus arrived after morning roll call") },
                        modifier = Modifier.fillMaxWidth().testTag("correction_reason_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRecordAttendance(stu, todayKey, newStatus, correctionReason)
                        correctionTarget = null
                    },
                    enabled = correctionReason.trim().length >= 3
                ) {
                    Text("Save & Log Audit")
                }
            },
            dismissButton = {
                TextButton(onClick = { correctionTarget = null }) { Text("Cancel") }
            }
        )
    }

    if (editingStudent != null) {
        EditStudentDialog(
            student = editingStudent!!,
            classes = classes,
            onDismiss = { editingStudent = null },
            onSave = { adm, fullName, cls, emN, emP, med, dests, pickups ->
                onUpdateStudentProfile(editingStudent!!, adm, fullName, cls, emN, emP, med, dests, pickups)
            }
        )
    }

    if (deletingStudent != null) {
        ConfirmDeleteDialog(
            title = "Delete Pupil Record?",
            message = "Permanently remove ${deletingStudent!!.fullName} (${deletingStudent!!.admissionNumber}) from this school's pupil directory? This cannot be undone.",
            onDismiss = { deletingStudent = null },
            onConfirmDelete = { onDeleteStudent(deletingStudent!!) }
        )
    }

    if (showTeacherDialog) {
        EditTeacherMemberDialog(
            existingMember = editingMember,
            classes = classes,
            subjects = subjects,
            allowRoleChange = false,
            onDismiss = { showTeacherDialog = false },
            onSave = { name, email, phone, role, assignedIds, subjectIds, subjectNames, status ->
                onSaveTeacherMember(editingMember, name, email, phone, role, assignedIds, subjectIds, subjectNames, status)
            }
        )
    }

    if (deletingMember != null) {
        ConfirmDeleteDialog(
            title = "Delete Teacher Record?",
            message = "Remove ${deletingMember!!.displayName} from the school's teachers directory?",
            onDismiss = { deletingMember = null },
            onConfirmDelete = { onDeleteTeacherMember(deletingMember!!) }
        )
    }
}
