package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.School
import com.example.data.model.SchoolClass
import com.example.data.model.SchoolMember
import com.example.data.model.Student
import com.example.data.model.UserRole
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.KpiMetricCard
import com.example.ui.theme.EmergencyRed600
import com.example.ui.theme.SafeBlue700
import com.example.ui.theme.SafeGreen600

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClassesPupilsScreen(
    role: UserRole,
    school: School?,
    classes: List<SchoolClass>,
    students: List<Student>,
    members: List<SchoolMember>,
    isBusy: Boolean,
    onCreateClass: (name: String, grade: String, year: String) -> Unit,
    onEnrollStudent: (
        adm: String, name: String, schoolClass: SchoolClass,
        emName: String, emPhone: String, medical: String,
        homeDest: String, pickup: String, photoUri: String?, linkSelf: Boolean
    ) -> Unit,
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
    onUpdateStudentLifecycle: (Student, newStatus: String) -> Unit,
    onLinkMemberToStudent: (Student, memberUid: String, UserRole) -> Unit,
    modifier: Modifier = Modifier
) {
    val canManageClasses = role == UserRole.SCHOOL_ADMIN || role == UserRole.SUPER_ADMIN
    val canEditPupils = canManageClasses || role == UserRole.TEACHER

    var searchQuery by remember { mutableStateOf("") }
    var selectedClassFilter by remember { mutableStateOf("all") }
    var showCreateClassDialog by remember { mutableStateOf(false) }
    var showEnrollDialog by remember { mutableStateOf(false) }
    var editingStudent by remember { mutableStateOf<Student?>(null) }
    var deletingStudent by remember { mutableStateOf<Student?>(null) }
    var linkTargetStudent by remember { mutableStateOf<Student?>(null) }

    val filteredStudents = remember(students, searchQuery, selectedClassFilter) {
        students.filter { stu ->
            val matchesClass = selectedClassFilter == "all" || stu.classId == selectedClassFilter
            val q = searchQuery.trim()
            val matchesQuery = q.isBlank() ||
                stu.fullName.contains(q, ignoreCase = true) ||
                stu.admissionNumber.contains(q, ignoreCase = true) ||
                stu.className.contains(q, ignoreCase = true)
            matchesClass && matchesQuery
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

        item {
            Column(modifier = Modifier.widthIn(max = 920.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Classes & Pupils Management", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiMetricCard(
                        label = "Total Classes",
                        value = classes.size.toString(),
                        icon = Icons.Filled.Class,
                        accentColor = SafeBlue700,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMetricCard(
                        label = "Total Pupils",
                        value = students.size.toString(),
                        subtitle = "${students.count { it.status == "active" }} active",
                        icon = Icons.Filled.Groups,
                        accentColor = SafeGreen600,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (canManageClasses) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { showCreateClassDialog = true },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f).testTag("classes_add_class_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("New Class")
                        }
                        Button(
                            onClick = { showEnrollDialog = true },
                            enabled = !isBusy && classes.isNotEmpty(),
                            modifier = Modifier.weight(1f).testTag("classes_enroll_pupil_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("Enroll Pupil")
                        }
                    }
                }
            }
        }

        if (classes.isEmpty()) {
            item {
                Column(modifier = Modifier.widthIn(max = 920.dp)) {
                    EmptyStateCard("No Classes Created", "Ask your School Administrator to create classes before enrolling pupils.")
                }
            }
        } else {
            item {
                Column(modifier = Modifier.widthIn(max = 920.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Filter by Class", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = selectedClassFilter == "all",
                            onClick = { selectedClassFilter = "all" },
                            label = { Text("All (${students.size})") }
                        )
                        classes.forEach { cls ->
                            val count = students.count { it.classId == cls.id }
                            FilterChip(
                                selected = selectedClassFilter == cls.id,
                                onClick = { selectedClassFilter = cls.id },
                                label = { Text("${cls.name} ($count)") }
                            )
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                label = { Text("Search pupil by name, admission number, or class") },
                singleLine = true,
                modifier = Modifier.widthIn(max = 920.dp).fillMaxWidth().testTag("classes_pupil_search_input")
            )
        }

        if (filteredStudents.isEmpty()) {
            item {
                Column(modifier = Modifier.widthIn(max = 920.dp)) {
                    EmptyStateCard("No Pupils Matching Filter", "Adjust the class filter or enroll new pupils to see records here.")
                }
            }
        } else {
            items(filteredStudents, key = { it.id }) { stu ->
                Card(
                    modifier = Modifier.widthIn(max = 920.dp).fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!stu.photoUri.isNullOrBlank()) {
                                AsyncImage(
                                    model = stu.photoUri,
                                    contentDescription = stu.fullName,
                                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(stu.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${stu.className} • ${stu.admissionNumber} • ${stu.status.uppercase()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (!stu.emergencyContactName.isNullOrBlank()) {
                                    Text(
                                        "Emergency: ${stu.emergencyContactName} (${stu.emergencyContactPhone.orEmpty()})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (canEditPupils) {
                                Row {
                                    IconButton(
                                        onClick = { editingStudent = stu },
                                        modifier = Modifier.testTag("classes_edit_student_${stu.id}")
                                    ) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Edit ${stu.fullName}", tint = SafeBlue700)
                                    }
                                    IconButton(
                                        onClick = { deletingStudent = stu },
                                        modifier = Modifier.testTag("classes_delete_student_${stu.id}")
                                    ) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete ${stu.fullName}", tint = EmergencyRed600)
                                    }
                                    if (canManageClasses) {
                                        IconButton(onClick = { linkTargetStudent = stu }) {
                                            Icon(Icons.Filled.Link, contentDescription = "Link parent or teacher")
                                        }
                                    }
                                }
                            }
                        }

                        if (canManageClasses) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("active", "promoted", "transferred", "graduated", "archived").forEach { lifecycle ->
                                    FilterChip(
                                        selected = stu.status == lifecycle,
                                        onClick = { onUpdateStudentLifecycle(stu, lifecycle) },
                                        label = { Text(lifecycle.replaceFirstChar { it.uppercase() }) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showCreateClassDialog) {
        var name by remember { mutableStateOf("") }
        var grade by remember { mutableStateOf("") }
        var year by remember { mutableStateOf(school?.academicYear ?: "2026/2027") }
        AlertDialog(
            onDismissRequest = { showCreateClassDialog = false },
            title = { Text("Create School Class") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Class Name *") }, placeholder = { Text("Primary 4A") })
                    OutlinedTextField(value = grade, onValueChange = { grade = it }, label = { Text("Grade / Level") }, placeholder = { Text("Primary 4") })
                    OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text("Academic Year") })
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateClass(name, grade, year)
                        showCreateClassDialog = false
                    },
                    enabled = name.trim().isNotEmpty()
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreateClassDialog = false }) { Text("Cancel") } }
        )
    }

    if (showEnrollDialog && classes.isNotEmpty()) {
        var adm by remember { mutableStateOf("ADM-${(100..999).random()}") }
        var fullName by remember { mutableStateOf("") }
        var selectedClass by remember { mutableStateOf(classes.first()) }
        var emName by remember { mutableStateOf("") }
        var emPhone by remember { mutableStateOf("") }
        var medical by remember { mutableStateOf("") }
        var homeDest by remember { mutableStateOf("Home") }
        var pickup by remember { mutableStateOf("") }
        var photoUri by remember { mutableStateOf<String?>(null) }
        var linkSelf by remember { mutableStateOf(true) }

        val photoPicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri -> photoUri = uri?.toString() }

        AlertDialog(
            onDismissRequest = { showEnrollDialog = false },
            title = { Text("Enroll New Pupil") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Pupil Full Name *") })
                    OutlinedTextField(value = adm, onValueChange = { adm = it }, label = { Text("Admission Number *") })
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        classes.forEach { cls ->
                            FilterChip(selected = selectedClass.id == cls.id, onClick = { selectedClass = cls }, label = { Text(cls.name) })
                        }
                    }
                    OutlinedTextField(value = homeDest, onValueChange = { homeDest = it }, label = { Text("Approved Home Destination") })
                    OutlinedTextField(value = pickup, onValueChange = { pickup = it }, label = { Text("Authorised Pickup Person") })
                    OutlinedTextField(value = emName, onValueChange = { emName = it }, label = { Text("Emergency Contact Name") })
                    OutlinedTextField(value = emPhone, onValueChange = { emPhone = it }, label = { Text("Emergency Phone") })
                    OutlinedTextField(value = medical, onValueChange = { medical = it }, label = { Text("Restricted Medical Notes") })
                    OutlinedButton(
                        onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text(if (photoUri == null) "Pick Pupil Photo (Photo Picker)" else "Photo Selected")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = linkSelf, onCheckedChange = { linkSelf = it })
                        Text("Link my account for guardian preview/testing", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onEnrollStudent(adm, fullName, selectedClass, emName, emPhone, medical, homeDest, pickup, photoUri, linkSelf)
                        showEnrollDialog = false
                    },
                    enabled = fullName.trim().length >= 2 && adm.trim().isNotEmpty()
                ) { Text("Enroll") }
            },
            dismissButton = { TextButton(onClick = { showEnrollDialog = false }) { Text("Cancel") } }
        )
    }

    if (linkTargetStudent != null) {
        val stu = linkTargetStudent!!
        AlertDialog(
            onDismissRequest = { linkTargetStudent = null },
            title = { Text("Link Member to ${stu.fullName}") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (members.isEmpty()) {
                        Text("No active members found yet. Invite teachers or parents first.")
                    } else {
                        members.forEach { m ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(m.displayName, style = MaterialTheme.typography.titleSmall)
                                    Text(UserRole.fromKey(m.role).label, style = MaterialTheme.typography.bodySmall)
                                }
                                Button(onClick = {
                                    onLinkMemberToStudent(stu, m.userId, UserRole.fromKey(m.role))
                                    linkTargetStudent = null
                                }) { Text("Link") }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { linkTargetStudent = null }) { Text("Close") } }
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
            message = "Permanently remove ${deletingStudent!!.fullName} (${deletingStudent!!.admissionNumber})?",
            onDismiss = { deletingStudent = null },
            onConfirmDelete = { onDeleteStudent(deletingStudent!!) }
        )
    }
}
