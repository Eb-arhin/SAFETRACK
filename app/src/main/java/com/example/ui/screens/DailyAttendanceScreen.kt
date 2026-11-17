package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.OfflineAttendanceEntity
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceStatus
import com.example.data.model.School
import com.example.data.model.SchoolClass
import com.example.data.model.Student
import com.example.ui.components.EmptyStateCard
import com.example.ui.theme.DelayAmber100
import com.example.ui.theme.DelayAmber700
import com.example.ui.theme.EmergencyRed100
import com.example.ui.theme.EmergencyRed600
import com.example.ui.theme.EmergencyRed700
import com.example.ui.theme.SafeBlue100
import com.example.ui.theme.SafeBlue700
import com.example.ui.theme.SafeBlue900
import com.example.ui.theme.SafeGreen100
import com.example.ui.theme.SafeGreen600
import com.example.ui.theme.SafeGreen700
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DailyAttendanceScreen(
    school: School?,
    classes: List<SchoolClass>,
    students: List<Student>,
    attendance: List<AttendanceRecord>,
    pendingOffline: List<OfflineAttendanceEntity>,
    isOfflineCaptureMode: Boolean,
    isBusy: Boolean,
    onToggleOfflineMode: (Boolean) -> Unit,
    onSyncOffline: () -> Unit,
    onRecordAttendance: (Student, dateKey: String, AttendanceStatus, reason: String) -> Unit,
    onMarkAllPresent: (List<Student>, dateKey: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var dayOffset by remember { mutableIntStateOf(0) }
    val (dateKey, friendlyDate) = remember(dayOffset) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, dayOffset)
        val key = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        val label = SimpleDateFormat("EEE, dd MMM yyyy", Locale.US).format(cal.time)
        key to label
    }

    var selectedClassId by remember { mutableStateOf("all") }
    var statusFilter by remember { mutableStateOf("all") }
    var searchQuery by remember { mutableStateOf("") }

    var correctionTarget by remember { mutableStateOf<Pair<Student, AttendanceStatus>?>(null) }
    var correctionReason by remember { mutableStateOf("") }

    val activeStudents = remember(students) { students.filter { it.status == "active" } }
    val classFilteredStudents = remember(activeStudents, selectedClassId) {
        if (selectedClassId == "all") activeStudents else activeStudents.filter { it.classId == selectedClassId }
    }

    val dateRecordsByStudentId = remember(attendance, dateKey) {
        attendance.filter { it.dateKey == dateKey }.associateBy { it.studentId }
    }
    val offlineQueuedByStudentId = remember(pendingOffline, dateKey) {
        pendingOffline.filter { it.dateKey == dateKey }.associateBy { it.studentId }
    }

    val presentCount = classFilteredStudents.count { stu ->
        val st = offlineQueuedByStudentId[stu.id]?.status ?: dateRecordsByStudentId[stu.id]?.status
        st == AttendanceStatus.PRESENT.key
    }
    val tardyCount = classFilteredStudents.count { stu ->
        val st = offlineQueuedByStudentId[stu.id]?.status ?: dateRecordsByStudentId[stu.id]?.status
        st == AttendanceStatus.LATE.key
    }
    val absentCount = classFilteredStudents.count { stu ->
        val st = offlineQueuedByStudentId[stu.id]?.status ?: dateRecordsByStudentId[stu.id]?.status
        st == AttendanceStatus.ABSENT.key
    }
    val unmarkedStudents = remember(classFilteredStudents, dateRecordsByStudentId, offlineQueuedByStudentId) {
        classFilteredStudents.filter { stu ->
            val st = offlineQueuedByStudentId[stu.id]?.status ?: dateRecordsByStudentId[stu.id]?.status
            st == null || st == AttendanceStatus.PENDING_CONFIRMATION.key
        }
    }
    val markedCount = (classFilteredStudents.size - unmarkedStudents.size).coerceAtLeast(0)
    val completionFraction = if (classFilteredStudents.isNotEmpty()) {
        markedCount.toFloat() / classFilteredStudents.size.toFloat()
    } else 0f

    val visibleStudents = remember(
        classFilteredStudents,
        statusFilter,
        searchQuery,
        dateRecordsByStudentId,
        offlineQueuedByStudentId
    ) {
        classFilteredStudents.filter { stu ->
            val st = offlineQueuedByStudentId[stu.id]?.status ?: dateRecordsByStudentId[stu.id]?.status
            val matchesStatus = when (statusFilter) {
                "present" -> st == AttendanceStatus.PRESENT.key
                "tardy" -> st == AttendanceStatus.LATE.key
                "absent" -> st == AttendanceStatus.ABSENT.key
                "unmarked" -> st == null || st == AttendanceStatus.PENDING_CONFIRMATION.key
                else -> true
            }
            val matchesQuery = searchQuery.isBlank() ||
                stu.fullName.contains(searchQuery, ignoreCase = true) ||
                stu.admissionNumber.contains(searchQuery, ignoreCase = true) ||
                stu.className.contains(searchQuery, ignoreCase = true)
            matchesStatus && matchesQuery
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        // Hero Date, Sync Status & Progress Card
        item {
            Card(
                modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(SafeBlue900, SafeBlue700)))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Daily Roll Call & Attendance",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = school?.name ?: "SafeTrack Classroom Sync",
                                style = MaterialTheme.typography.bodySmall,
                                color = SafeBlue100
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isOfflineCaptureMode) DelayAmber100 else SafeGreen100
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isOfflineCaptureMode) Icons.Filled.CloudOff else Icons.Filled.CloudDone,
                                    contentDescription = "Sync status",
                                    tint = if (isOfflineCaptureMode) DelayAmber700 else SafeGreen700,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (isOfflineCaptureMode) "Offline Queue" else "Live Parent Sync",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isOfflineCaptureMode) DelayAmber700 else SafeGreen700
                                )
                            }
                        }
                    }

                    // Date Stepper Row
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = 0.14f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { dayOffset-- }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day", tint = Color.White)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (dayOffset == 0) "Today • $friendlyDate" else friendlyDate,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Text(dateKey, style = MaterialTheme.typography.labelSmall, color = SafeBlue100)
                            }
                            Row {
                                if (dayOffset != 0) {
                                    IconButton(onClick = { dayOffset = 0 }) {
                                        Icon(Icons.Filled.Today, contentDescription = "Jump to today", tint = Color.White)
                                    }
                                }
                                IconButton(
                                    onClick = { if (dayOffset < 0) dayOffset++ },
                                    enabled = dayOffset < 0
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "Next day",
                                        tint = if (dayOffset < 0) Color.White else Color.White.copy(alpha = 0.35f)
                                    )
                                }
                            }
                        }
                    }

                    // Roll Call Progress & Breakdown
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = "$markedCount of ${classFilteredStudents.size} pupils marked",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                            Text(
                                text = "${(completionFraction * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = SafeGreen100
                            )
                        }
                        LinearProgressIndicator(
                            progress = { completionFraction },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                            color = SafeGreen100,
                            trackColor = Color.White.copy(alpha = 0.22f)
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            AttendanceCountPill("Present: $presentCount", SafeGreen100, SafeGreen700)
                            AttendanceCountPill("Tardy: $tardyCount", DelayAmber100, DelayAmber700)
                            AttendanceCountPill("Absent: $absentCount", EmergencyRed100, EmergencyRed700)
                            AttendanceCountPill("Left: ${unmarkedStudents.size}", Color.White.copy(alpha = 0.2f), Color.White)
                        }
                    }
                }
            }
        }

        // Offline Sync Controls
        item {
            Card(
                modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Offline Queue Mode", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (isOfflineCaptureMode) "Saving locally in Room until you tap Sync"
                                else "Marks sync immediately to Firestore & notify parents in real time",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = isOfflineCaptureMode, onCheckedChange = onToggleOfflineMode)
                    }
                    AnimatedVisibility(visible = pendingOffline.isNotEmpty()) {
                        Button(
                            onClick = onSyncOffline,
                            enabled = !isBusy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.CloudSync, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Sync ${pendingOffline.size} Pending Record(s) to Firestore")
                        }
                    }
                }
            }
        }

        // Class Filter, Status Filter, Search & Bulk Action
        item {
            Column(modifier = Modifier.widthIn(max = 860.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (classes.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = selectedClassId == "all",
                            onClick = { selectedClassId = "all" },
                            label = { Text("All Classes (${activeStudents.size})") }
                        )
                        classes.forEach { cls ->
                            FilterChip(
                                selected = selectedClassId == cls.id,
                                onClick = { selectedClassId = cls.id },
                                label = { Text(cls.name) }
                            )
                        }
                    }
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        "all" to "All (${classFilteredStudents.size})",
                        "unmarked" to "Unmarked (${unmarkedStudents.size})",
                        "present" to "Present ($presentCount)",
                        "tardy" to "Tardy ($tardyCount)",
                        "absent" to "Absent ($absentCount)"
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = statusFilter == key,
                            onClick = { statusFilter = key },
                            label = { Text(label) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        placeholder = { Text("Search pupil name or admission #") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    if (unmarkedStudents.isNotEmpty()) {
                        Button(
                            onClick = { onMarkAllPresent(unmarkedStudents, dateKey) },
                            enabled = !isBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = SafeGreen600),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                            modifier = Modifier.testTag("mark_all_present_button")
                        ) {
                            Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("All Present")
                        }
                    }
                }
            }
        }

        if (visibleStudents.isEmpty()) {
            item {
                Column(modifier = Modifier.widthIn(max = 860.dp)) {
                    EmptyStateCard(
                        title = if (activeStudents.isEmpty()) "No Active Pupils Assigned" else "No Pupils Match Filter",
                        subtitle = if (activeStudents.isEmpty()) {
                            "Once students are enrolled or imported into your class, you can take daily roll call here."
                        } else {
                            "Clear the search box or switch status filter to see all pupils."
                        }
                    )
                }
            }
        } else {
            items(visibleStudents, key = { "att_row_${it.id}" }) { student ->
                val syncedRecord = dateRecordsByStudentId[student.id]
                val offlineRecord = offlineQueuedByStudentId[student.id]
                val currentStatusKey = offlineRecord?.status ?: syncedRecord?.status
                val arrivalTime = offlineRecord?.arrivalTime ?: syncedRecord?.arrivalTime
                val isQueuedOffline = offlineRecord != null

                AttendanceRollCallCard(
                    student = student,
                    currentStatusKey = currentStatusKey,
                    arrivalTime = arrivalTime,
                    isQueuedOffline = isQueuedOffline,
                    isBusy = isBusy,
                    onSelectStatus = { targetStatus ->
                        if (syncedRecord != null && syncedRecord.status != targetStatus.key && !isOfflineCaptureMode) {
                            correctionReason = ""
                            correctionTarget = student to targetStatus
                        } else {
                            onRecordAttendance(student, dateKey, targetStatus, "")
                        }
                    }
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (correctionTarget != null) {
        val (stu, targetStatus) = correctionTarget!!
        val statusLabel = if (targetStatus == AttendanceStatus.LATE) "Tardy" else targetStatus.label
        AlertDialog(
            onDismissRequest = { correctionTarget = null },
            title = { Text("Update Attendance — ${stu.fullName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Changing today's synced attendance to $statusLabel requires an audit note for parents & school records.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Arrived late to class", "Marked by mistake", "Parent called ahead").forEach { quick ->
                            FilterChip(
                                selected = correctionReason == quick,
                                onClick = { correctionReason = quick },
                                label = { Text(quick) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = correctionReason,
                        onValueChange = { correctionReason = it },
                        label = { Text("Reason (min 3 characters) *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("daily_correction_reason_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRecordAttendance(stu, dateKey, targetStatus, correctionReason)
                        correctionTarget = null
                    },
                    enabled = correctionReason.trim().length >= 3
                ) {
                    Text("Update & Sync")
                }
            },
            dismissButton = {
                TextButton(onClick = { correctionTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AttendanceCountPill(text: String, bg: Color, fg: Color) {
    Surface(shape = RoundedCornerShape(50), color = bg) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun AttendanceRollCallCard(
    student: Student,
    currentStatusKey: String?,
    arrivalTime: String?,
    isQueuedOffline: Boolean,
    isBusy: Boolean,
    onSelectStatus: (AttendanceStatus) -> Unit
) {
    val isPresent = currentStatusKey == AttendanceStatus.PRESENT.key
    val isTardy = currentStatusKey == AttendanceStatus.LATE.key
    val isAbsent = currentStatusKey == AttendanceStatus.ABSENT.key

    val borderColor = when {
        isPresent -> SafeGreen600.copy(alpha = 0.4f)
        isTardy -> DelayAmber700.copy(alpha = 0.45f)
        isAbsent -> EmergencyRed600.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    }

    Card(
        modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth(),
        border = BorderStroke(1.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!student.photoUri.isNullOrBlank()) {
                    AsyncImage(
                        model = student.photoUri,
                        contentDescription = student.fullName,
                        modifier = Modifier.size(44.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    val initials = student.fullName.split(" ")
                        .filter { it.isNotBlank() }
                        .take(2)
                        .joinToString("") { it.first().uppercase() }
                        .ifBlank { "P" }
                    Surface(modifier = Modifier.size(44.dp), shape = CircleShape, color = SafeBlue100) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(initials, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = SafeBlue900)
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(student.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "${student.className} • ${student.admissionNumber}" +
                            (arrivalTime?.let { " • $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val (badgeLabel, badgeBg, badgeFg) = when {
                    isQueuedOffline -> Triple("Queued", DelayAmber100, DelayAmber700)
                    isPresent -> Triple("Present", SafeGreen100, SafeGreen700)
                    isTardy -> Triple("Tardy", DelayAmber100, DelayAmber700)
                    isAbsent -> Triple("Absent", EmergencyRed100, EmergencyRed700)
                    currentStatusKey == AttendanceStatus.EXCUSED_ABSENCE.key -> Triple("Excused", SafeBlue100, SafeBlue900)
                    else -> Triple("Pending", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = RoundedCornerShape(50), color = badgeBg) {
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = badgeFg,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Three 48dp+ primary marking buttons: Present, Tardy, Absent
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RollCallActionButton(
                    label = "Present",
                    icon = Icons.Filled.CheckCircle,
                    selected = isPresent,
                    activeContainer = SafeGreen600,
                    activeContent = Color.White,
                    inactiveTint = SafeGreen700,
                    enabled = !isBusy,
                    onClick = { onSelectStatus(AttendanceStatus.PRESENT) },
                    modifier = Modifier.weight(1f).testTag("mark_present_btn_${student.id}")
                )
                RollCallActionButton(
                    label = "Tardy",
                    icon = Icons.Filled.Schedule,
                    selected = isTardy,
                    activeContainer = DelayAmber700,
                    activeContent = Color.White,
                    inactiveTint = DelayAmber700,
                    enabled = !isBusy,
                    onClick = { onSelectStatus(AttendanceStatus.LATE) },
                    modifier = Modifier.weight(1f).testTag("mark_tardy_btn_${student.id}")
                )
                RollCallActionButton(
                    label = "Absent",
                    icon = Icons.Filled.PersonOff,
                    selected = isAbsent,
                    activeContainer = EmergencyRed600,
                    activeContent = Color.White,
                    inactiveTint = EmergencyRed700,
                    enabled = !isBusy,
                    onClick = { onSelectStatus(AttendanceStatus.ABSENT) },
                    modifier = Modifier.weight(1f).testTag("mark_absent_btn_${student.id}")
                )
            }
        }
    }
}

@Composable
private fun RollCallActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    activeContainer: Color,
    activeContent: Color,
    inactiveTint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(
            onClick = onClick,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = activeContainer,
                contentColor = activeContent
            ),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
            modifier = modifier.height(48.dp)
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(4.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            border = BorderStroke(1.dp, inactiveTint.copy(alpha = 0.45f)),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
            modifier = modifier.height(48.dp)
        ) {
            Icon(icon, contentDescription = label, tint = inactiveTint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(4.dp))
            Text(label, color = inactiveTint, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}
