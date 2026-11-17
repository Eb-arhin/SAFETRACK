package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ClassDepartureEntry
import com.example.data.model.School
import com.example.data.model.SchoolClass
import com.example.data.model.SchoolMember
import com.example.data.model.Student
import com.example.data.model.UserRole
import com.example.ui.theme.DelayAmber100
import com.example.ui.theme.DelayAmber700
import com.example.ui.theme.EmergencyRed600
import com.example.ui.theme.SafeBlue100
import com.example.ui.theme.SafeBlue700
import com.example.ui.theme.SafeBlue900
import com.example.ui.theme.SafeGreen100
import com.example.ui.theme.SafeGreen600
import com.example.ui.theme.SafeGreen700

private val LATE_REASON_PRESETS = listOf(
    "Extra lessons",
    "Parent running late",
    "Sports / Club",
    "Waiting for pickup",
    "Catch-up work"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClassDepartureTableCard(
    school: School?,
    classes: List<SchoolClass>,
    students: List<Student>,
    isBusy: Boolean,
    onDispatchClassDeparture: (entries: List<ClassDepartureEntry>, notifyLateExceptions: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    currentUserId: String = "",
    currentUserRole: UserRole = UserRole.TEACHER
) {
    val activeStudents = remember(students) { students.filter { it.status == "active" } }
    if (activeStudents.isEmpty()) return

    val assignedStudents = remember(activeStudents, currentUserId, currentUserRole) {
        if (currentUserRole == UserRole.TEACHER) {
            if (currentUserId.isBlank()) activeStudents
            else activeStudents.filter { currentUserId in it.teacherUids }
        } else {
            emptyList()
        }
    }

    if (currentUserRole != UserRole.TEACHER || assignedStudents.isEmpty()) {
        Card(
            modifier = modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Filled.DirectionsBus, contentDescription = null, tint = SafeBlue700)
                Column {
                    Text(
                        text = "Whole-Class Dismissal (Assigned Teacher Only)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (currentUserRole != UserRole.TEACHER) {
                            "Only a pupil's assigned class teacher may authorize dismissal or dispatch whole-class departure alerts."
                        } else {
                            "No active pupils are assigned to your teacher account yet. Ask your School Admin to assign your account to a class."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        return
    }

    var selectedClassId by remember { mutableStateOf("all") }
    var notifyLateExceptions by remember { mutableStateOf(true) }
    val lateFlags = remember { mutableStateMapOf<String, Boolean>() }
    val lateReasons = remember { mutableStateMapOf<String, String>() }
    val customDestinations = remember { mutableStateMapOf<String, String>() }

    val classStudents = remember(assignedStudents, selectedClassId) {
        if (selectedClassId == "all") assignedStudents else assignedStudents.filter { it.classId == selectedClassId }
    }
    val departingCount = classStudents.count { lateFlags[it.id] != true }
    val lateCount = classStudents.count { lateFlags[it.id] == true }

    Card(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, SafeBlue700.copy(alpha = 0.28f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.DirectionsBus, contentDescription = null, tint = SafeBlue700)
                    Column {
                        Text("Whole-Class Departure Alert Table", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Alert parents of your assigned class in one click, or mark late-leaving exceptions with reasons.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (classes.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = selectedClassId == "all",
                        onClick = { selectedClassId = "all" },
                        label = { Text("All (${assignedStudents.size})") }
                    )
                    classes.forEach { cls ->
                        val count = assignedStudents.count { it.classId == cls.id }
                        if (count > 0) {
                            FilterChip(
                                selected = selectedClassId == cls.id,
                                onClick = { selectedClassId = cls.id },
                                label = { Text("${cls.name} ($count)") }
                            )
                        }
                    }
                }
            }

            // Table summary pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = RoundedCornerShape(50), color = SafeGreen100) {
                    Text(
                        text = "Departing Now: $departingCount",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = SafeGreen700,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                Surface(shape = RoundedCornerShape(50), color = if (lateCount > 0) DelayAmber100 else MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        text = "Leaving Late: $lateCount",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (lateCount > 0) DelayAmber700 else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                if (lateCount > 0) {
                    TextButton(
                        onClick = {
                            classStudents.forEach {
                                lateFlags[it.id] = false
                                lateReasons.remove(it.id)
                            }
                        }
                    ) {
                        Text("Reset Exceptions")
                    }
                }
            }

            // Table Header Row
            Surface(
                shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
                color = SafeBlue900
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Pupil & Class", style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
                    Text("Departure / Late Exception", style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            // Class Departure Rows
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                classStudents.forEach { stu ->
                    val isLate = lateFlags[stu.id] == true
                    val defaultDest = stu.approvedDestinations.firstOrNull {
                        !it.equals(school?.name, ignoreCase = true)
                    } ?: "Home"
                    val selectedDest = customDestinations[stu.id] ?: defaultDest
                    val destOptions = (stu.approvedDestinations.filter {
                        !it.equals(school?.name, ignoreCase = true)
                    } + "Home").distinct()
                    val reason = lateReasons[stu.id].orEmpty()

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isLate) DelayAmber100.copy(alpha = 0.45f) else SafeBlue100.copy(alpha = 0.18f),
                        border = BorderStroke(
                            1.dp,
                            if (isLate) DelayAmber700.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stu.fullName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${stu.className} • ${stu.admissionNumber} • To: $selectedDest",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(
                                        selected = !isLate,
                                        onClick = { lateFlags[stu.id] = false },
                                        label = { Text("Departing") },
                                        modifier = Modifier.testTag("departing_chip_${stu.id}")
                                    )
                                    FilterChip(
                                        selected = isLate,
                                        onClick = { lateFlags[stu.id] = true },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(14.dp))
                                        },
                                        label = { Text("Late") },
                                        modifier = Modifier.testTag("late_exception_chip_${stu.id}")
                                    )
                                }
                            }

                            if (!isLate && destOptions.size > 1) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    destOptions.forEach { d ->
                                        FilterChip(
                                            selected = selectedDest == d,
                                            onClick = { customDestinations[stu.id] = d },
                                            label = { Text(d, style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                            }

                            AnimatedVisibility(visible = isLate) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        "Reason for leaving late:",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = DelayAmber700,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        LATE_REASON_PRESETS.forEach { preset ->
                                            FilterChip(
                                                selected = reason == preset,
                                                onClick = { lateReasons[stu.id] = preset },
                                                label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }
                                    }
                                    OutlinedTextField(
                                        value = reason,
                                        onValueChange = { lateReasons[stu.id] = it },
                                        label = { Text("Late departure reason *") },
                                        placeholder = { Text("e.g., Staying for extra math coaching until 16:30") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("late_reason_input_${stu.id}")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (lateCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Notify parents of late-leaving exceptions", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            "Parents of marked late pupils receive their exact reason instead of a departure alert.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = notifyLateExceptions, onCheckedChange = { notifyLateExceptions = it })
                }
            }

            Button(
                onClick = {
                    val entries = classStudents.map { stu ->
                        val isLate = lateFlags[stu.id] == true
                        val defaultDest = stu.approvedDestinations.firstOrNull {
                            !it.equals(school?.name, ignoreCase = true)
                        } ?: "Home"
                        ClassDepartureEntry(
                            student = stu,
                            isLeavingLate = isLate,
                            lateReason = lateReasons[stu.id].orEmpty(),
                            destinationName = customDestinations[stu.id] ?: defaultDest
                        )
                    }
                    onDispatchClassDeparture(entries, notifyLateExceptions)
                },
                enabled = !isBusy && classStudents.isNotEmpty() && (departingCount > 0 || (lateCount > 0 && notifyLateExceptions)),
                colors = ButtonDefaults.buttonColors(containerColor = SafeGreen600),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("single_click_class_departure_button")
            ) {
                Icon(Icons.Filled.NotificationsActive, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(
                    buildString {
                        append("Alert Parents: Class Departed ($departingCount)")
                        if (lateCount > 0 && notifyLateExceptions) {
                            append(" + $lateCount Late Notice")
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeachersDirectoryCard(
    members: List<SchoolMember>,
    currentUserRole: UserRole,
    onAddTeacher: () -> Unit,
    onEditMember: (SchoolMember) -> Unit,
    onDeleteMember: (SchoolMember) -> Unit,
    modifier: Modifier = Modifier,
    classes: List<SchoolClass> = emptyList()
) {
    val canManageAllRoles = currentUserRole == UserRole.SCHOOL_ADMIN || currentUserRole == UserRole.SUPER_ADMIN
    val displayedMembers = remember(members, canManageAllRoles) {
        if (canManageAllRoles) members
        else members.filter { it.role == UserRole.TEACHER.key }
    }
    val classNamesById = remember(classes) { classes.associate { it.id to it.name } }

    Card(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (canManageAllRoles) "Teachers & Staff Directory (${displayedMembers.size})"
                        else "Teachers Directory (${displayedMembers.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Add, edit, or remove teacher names, contact details, assigned classes, and assigned subjects.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = onAddTeacher,
                    modifier = Modifier.testTag("add_teacher_directory_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Add Teacher")
                }
            }

            if (displayedMembers.isEmpty()) {
                Text(
                    "No teachers listed yet. Tap '+ Add Teacher' to add teacher names, classes & subjects.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                displayedMembers.forEach { member ->
                    val canEditOrDelete = canManageAllRoles || member.role == UserRole.TEACHER.key
                    val assignedClassNames = member.assignedClassIds.mapNotNull { classNamesById[it] }
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(member.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Surface(shape = RoundedCornerShape(50), color = SafeBlue100) {
                                    Text(
                                        text = UserRole.fromKey(member.role).label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SafeBlue700,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                if (member.status != "active") {
                                    Surface(shape = RoundedCornerShape(50), color = DelayAmber100) {
                                        Text(
                                            text = member.status.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = DelayAmber700,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = listOfNotNull(
                                    member.email.takeIf { it.isNotBlank() },
                                    member.phone?.takeIf { it.isNotBlank() }
                                ).joinToString(" • ").ifBlank { "No contact details added" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (assignedClassNames.isNotEmpty() || member.assignedSubjectNames.isNotEmpty()) {
                                val classLabel = if (assignedClassNames.isNotEmpty()) "Classes: ${assignedClassNames.joinToString()}" else null
                                val subjectLabel = if (member.assignedSubjectNames.isNotEmpty()) "Subjects: ${member.assignedSubjectNames.joinToString()}" else null
                                Text(
                                    text = listOfNotNull(classLabel, subjectLabel).joinToString(" • "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SafeBlue700,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        if (canEditOrDelete) {
                            Row {
                                IconButton(
                                    onClick = { onEditMember(member) },
                                    modifier = Modifier.testTag("edit_member_${member.userId}")
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit ${member.displayName}", tint = SafeBlue700)
                                }
                                IconButton(
                                    onClick = { onDeleteMember(member) },
                                    modifier = Modifier.testTag("delete_member_${member.userId}")
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete ${member.displayName}", tint = EmergencyRed600)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditTeacherMemberDialog(
    existingMember: SchoolMember?,
    classes: List<SchoolClass>,
    subjects: List<com.example.data.model.Subject> = emptyList(),
    allowRoleChange: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        displayName: String,
        email: String,
        phone: String,
        role: UserRole,
        assignedClassIds: List<String>,
        assignedSubjectIds: List<String>,
        assignedSubjectNames: List<String>,
        status: String
    ) -> Unit
) {
    var displayName by remember(existingMember?.userId) { mutableStateOf(existingMember?.displayName.orEmpty()) }
    var email by remember(existingMember?.userId) { mutableStateOf(existingMember?.email.orEmpty()) }
    var phone by remember(existingMember?.userId) { mutableStateOf(existingMember?.phone.orEmpty()) }
    var selectedRole by remember(existingMember?.userId) {
        mutableStateOf(
            existingMember?.let { UserRole.fromKey(it.role) } ?: UserRole.TEACHER
        )
    }
    var status by remember(existingMember?.userId) { mutableStateOf(existingMember?.status ?: "active") }
    var selectedClassIds by remember(existingMember?.userId) {
        mutableStateOf(existingMember?.assignedClassIds?.toSet() ?: emptySet())
    }
    var selectedSubjectNames by remember(existingMember?.userId) {
        val initialNames = buildSet {
            existingMember?.assignedSubjectNames?.forEach { add(it) }
            existingMember?.assignedSubjectIds?.forEach { id ->
                subjects.firstOrNull { it.id == id }?.let { add(it.name) }
            }
        }
        mutableStateOf(initialNames)
    }
    var customSubjectInput by remember { mutableStateOf("") }

    val availableSubjectNames = remember(subjects, selectedSubjectNames) {
        (subjects.map { it.name } + selectedSubjectNames).distinct().sorted()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingMember != null) "Edit Staff / Teacher Details" else "Add Teacher") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Full Name *") },
                    placeholder = { Text("e.g., Mr. Samuel Osei") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("teacher_name_input")
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    placeholder = { Text("teacher@school.edu.gh") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("teacher_email_input")
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    placeholder = { Text("+233240000000") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("teacher_phone_input")
                )
                if (allowRoleChange) {
                    Text("Role", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(UserRole.TEACHER, UserRole.SCHOOL_ADMIN, UserRole.PARENT, UserRole.PICKUP_PERSON).forEach { r ->
                            FilterChip(
                                selected = selectedRole == r,
                                onClick = { selectedRole = r },
                                label = { Text(r.label) }
                            )
                        }
                    }
                }
                Text("Status", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("active" to "Active", "suspended" to "Suspended").forEach { (key, label) ->
                        FilterChip(
                            selected = status == key,
                            onClick = { status = key },
                            label = { Text(label) }
                        )
                    }
                }
                if (classes.isNotEmpty()) {
                    Text("Assigned Classes", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        classes.forEach { cls ->
                            val isSelected = cls.id in selectedClassIds
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedClassIds = if (isSelected) selectedClassIds - cls.id else selectedClassIds + cls.id
                                },
                                label = { Text(cls.name) }
                            )
                        }
                    }
                }
                Text("Assigned Subjects", style = MaterialTheme.typography.labelLarge)
                if (availableSubjectNames.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        availableSubjectNames.forEach { subName ->
                            val isSelected = subName in selectedSubjectNames
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedSubjectNames = if (isSelected) selectedSubjectNames - subName else selectedSubjectNames + subName
                                },
                                label = { Text(subName) }
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customSubjectInput,
                        onValueChange = { customSubjectInput = it },
                        label = { Text("Add subject taught") },
                        placeholder = { Text("e.g., Mathematics") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("teacher_subject_input")
                    )
                    OutlinedButton(
                        onClick = {
                            val clean = customSubjectInput.trim()
                            if (clean.isNotEmpty()) {
                                selectedSubjectNames = selectedSubjectNames + clean
                                customSubjectInput = ""
                            }
                        },
                        enabled = customSubjectInput.trim().isNotEmpty()
                    ) {
                        Text("Add")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val subjectNamesList = selectedSubjectNames.toList()
                    val matchedSubjectIds = subjects
                        .filter { it.name in selectedSubjectNames }
                        .map { it.id }
                    onSave(
                        displayName,
                        email,
                        phone,
                        selectedRole,
                        selectedClassIds.toList(),
                        matchedSubjectIds,
                        subjectNamesList,
                        status
                    )
                    onDismiss()
                },
                enabled = displayName.trim().isNotEmpty(),
                modifier = Modifier.testTag("save_teacher_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = EmergencyRed600) },
        title = { Text(title) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmDelete()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed600),
                modifier = Modifier.testTag("confirm_delete_button")
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
