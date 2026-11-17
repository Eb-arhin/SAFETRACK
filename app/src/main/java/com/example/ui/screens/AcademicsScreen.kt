package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AttendanceRecord
import com.example.data.model.ExamReport
import com.example.data.model.School
import com.example.data.model.SchoolClass
import com.example.data.model.SchoolMember
import com.example.data.model.Student
import com.example.data.model.Subject
import com.example.data.model.UserRole
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.EditTeacherMemberDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.PerformanceAnalyticsDashboard
import com.example.ui.components.TeachersDirectoryCard
import com.example.ui.theme.SafeBlue100
import com.example.util.ParsedStudentRow
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AcademicsScreen(
    role: UserRole,
    school: School?,
    classes: List<SchoolClass>,
    students: List<Student>,
    members: List<SchoolMember>,
    subjects: List<Subject>,
    examReports: List<ExamReport>,
    attendance: List<AttendanceRecord>,
    currentUserDisplayName: String,
    isBusy: Boolean,
    onUpdateSchoolProfile: (name: String, city: String, phone: String, email: String, logoUri: String?) -> Unit,
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
    onBulkImportStudents: (defaultClass: SchoolClass, rows: List<ParsedStudentRow>, linkSelf: Boolean) -> Unit,
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
    onSaveSubject: (existing: Subject?, name: String, code: String, level: String, isCore: Boolean, coreSlot: String) -> Unit,
    onDeleteSubject: (Subject) -> Unit,
    onSeedDefaultSubjects: () -> Unit,
    onSaveExamReport: (report: ExamReport, autoRankPosition: Boolean) -> Unit,
    onExportExamReportPdf: (ExamReport) -> Unit,
    onExportBlankMasterPdf: () -> Unit = {},
    onApplyCefelixTemplate: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (role == UserRole.PARENT || role == UserRole.PICKUP_PERSON) {
        ParentReportCardScreen(
            school = school,
            students = students,
            examReports = examReports,
            onExportPdf = onExportExamReportPdf,
            modifier = modifier
        )
        return
    }

    val teacherCount = remember(members) {
        members.count { it.role == UserRole.TEACHER.key }
    }
    val slideTitles = listOf(
        "Students (${students.size})",
        "Teachers ($teacherCount)",
        "Subjects (${subjects.size})",
        "Reports",
        "Analytics"
    )
    val pagerState = rememberPagerState(pageCount = { slideTitles.size })
    val coroutineScope = rememberCoroutineScope()

    var subjectDialogTarget by remember { mutableStateOf<Subject?>(null) }
    var showSubjectDialog by remember { mutableStateOf(false) }
    var showEnrollDialog by remember { mutableStateOf(false) }
    var editingStudent by remember { mutableStateOf<Student?>(null) }
    var deletingStudent by remember { mutableStateOf<Student?>(null) }
    var editingTeacher by remember { mutableStateOf<SchoolMember?>(null) }
    var showTeacherDialog by remember { mutableStateOf(false) }
    var deletingTeacher by remember { mutableStateOf<SchoolMember?>(null) }
    var showBrandingDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        ScrollableTabRow(selectedTabIndex = pagerState.currentPage, edgePadding = 16.dp) {
            slideTitles.forEachIndexed { idx, title ->
                Tab(
                    selected = pagerState.currentPage == idx,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(idx) } },
                    text = { Text(title) },
                    modifier = Modifier.testTag("academics_slide_tab_$idx")
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { Spacer(Modifier.height(4.dp)) }

                when (page) {
                    // Slide 0: Independent Students Slide
                    0 -> {
                        item {
                            Column(
                                modifier = Modifier.widthIn(max = 860.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = SafeBlue100.copy(alpha = 0.45f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Students & Class Roster", style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "Enroll single pupils, import CSV/name rosters, edit details, or remove pupils.",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        Button(
                                            onClick = { showEnrollDialog = true },
                                            enabled = classes.isNotEmpty() && !isBusy,
                                            modifier = Modifier.testTag("students_slide_enroll_button")
                                        ) {
                                            Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.size(6.dp))
                                            Text("Enroll Pupil")
                                        }
                                    }
                                }

                                CsvImportAndBrandingCard(
                                    school = school,
                                    classes = classes,
                                    students = students,
                                    isBusy = isBusy,
                                    onEditSchoolBranding = { showBrandingDialog = true },
                                    onBulkImportStudents = onBulkImportStudents,
                                    onEditStudent = { editingStudent = it },
                                    onDeleteStudent = { deletingStudent = it }
                                )
                            }
                        }
                    }

                    // Slide 1: Independent Teachers Slide
                    1 -> {
                        item {
                            Column(
                                modifier = Modifier.widthIn(max = 860.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = SafeBlue100.copy(alpha = 0.45f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Teachers & Class/Subject Assignments", style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "Add teachers and configure their contact info, assigned classes, and subjects taught.",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                editingTeacher = null
                                                showTeacherDialog = true
                                            },
                                            enabled = !isBusy,
                                            modifier = Modifier.testTag("teachers_slide_add_button")
                                        ) {
                                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.size(6.dp))
                                            Text("Add Teacher")
                                        }
                                    }
                                }

                                TeachersDirectoryCard(
                                    members = members,
                                    currentUserRole = role,
                                    classes = classes,
                                    onAddTeacher = {
                                        editingTeacher = null
                                        showTeacherDialog = true
                                    },
                                    onEditMember = { member ->
                                        editingTeacher = member
                                        showTeacherDialog = true
                                    },
                                    onDeleteMember = { member -> deletingTeacher = member }
                                )
                            }
                        }
                    }

                    // Slide 2: Independent Subjects Slide
                    2 -> {
                        subjectsManagerSection(
                            subjects = subjects,
                            isBusy = isBusy,
                            onSeedDefaultSubjects = onSeedDefaultSubjects,
                            onOpenSubjectDialog = { sub ->
                                subjectDialogTarget = sub
                                showSubjectDialog = true
                            },
                            onDeleteSubject = onDeleteSubject
                        )
                    }

                    // Slide 3: Independent Reports Slide (Select Class -> All Names & Subjects)
                    3 -> {
                        item {
                            IndependentReportsSlide(
                                school = school,
                                classes = classes,
                                students = students,
                                subjects = subjects,
                                examReports = examReports,
                                attendance = attendance,
                                currentUserDisplayName = currentUserDisplayName,
                                isBusy = isBusy,
                                onSaveExamReport = onSaveExamReport,
                                onExportPdf = onExportExamReportPdf,
                                onExportBlankMasterPdf = onExportBlankMasterPdf,
                                onApplyCefelixTemplate = onApplyCefelixTemplate,
                                onGoToSubjects = {
                                    coroutineScope.launch { pagerState.animateScrollToPage(2) }
                                },
                                onEditBranding = { showBrandingDialog = true }
                            )
                        }
                    }

                    // Slide 4: Independent Analytics Slide (Subject Averages, Grade Distribution, Term Trends)
                    4 -> {
                        item {
                            Column(modifier = Modifier.widthIn(max = 920.dp)) {
                                PerformanceAnalyticsDashboard(
                                    classes = classes,
                                    subjects = subjects,
                                    examReports = examReports
                                )
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    if (showEnrollDialog && classes.isNotEmpty()) {
        QuickEnrollStudentDialog(
            classes = classes,
            onDismiss = { showEnrollDialog = false },
            onEnroll = onEnrollStudent
        )
    }

    if (showTeacherDialog) {
        EditTeacherMemberDialog(
            existingMember = editingTeacher,
            classes = classes,
            subjects = subjects,
            allowRoleChange = (role == UserRole.SCHOOL_ADMIN || role == UserRole.SUPER_ADMIN),
            onDismiss = { showTeacherDialog = false },
            onSave = { name, email, phone, memberRole, classIds, subjectIds, subjectNames, status ->
                onSaveTeacherMember(editingTeacher, name, email, phone, memberRole, classIds, subjectIds, subjectNames, status)
            }
        )
    }

    if (deletingTeacher != null) {
        ConfirmDeleteDialog(
            title = "Delete Teacher / Staff?",
            message = "Remove ${deletingTeacher!!.displayName} (${UserRole.fromKey(deletingTeacher!!.role).label}) from the school directory?",
            onDismiss = { deletingTeacher = null },
            onConfirmDelete = { onDeleteTeacherMember(deletingTeacher!!) }
        )
    }

    if (showSubjectDialog) {
        SubjectEditorDialog(
            existingSubject = subjectDialogTarget,
            onDismiss = { showSubjectDialog = false },
            onSave = { name, code, level, isCore, slot ->
                onSaveSubject(subjectDialogTarget, name, code, level, isCore, slot)
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

    if (showBrandingDialog && school != null) {
        EditSchoolBrandingDialog(
            school = school,
            onDismiss = { showBrandingDialog = false },
            onSave = onUpdateSchoolProfile
        )
    }

    if (deletingStudent != null) {
        ConfirmDeleteDialog(
            title = "Delete Pupil Record?",
            message = "Permanently remove ${deletingStudent!!.fullName} (${deletingStudent!!.admissionNumber}) from the school roster?",
            onDismiss = { deletingStudent = null },
            onConfirmDelete = { onDeleteStudent(deletingStudent!!) }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickEnrollStudentDialog(
    classes: List<SchoolClass>,
    onDismiss: () -> Unit,
    onEnroll: (
        adm: String, name: String, schoolClass: SchoolClass,
        emName: String, emPhone: String, medical: String,
        homeDest: String, pickup: String, photoUri: String?, linkSelf: Boolean
    ) -> Unit
) {
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
        onDismissRequest = onDismiss,
        title = { Text("Enroll New Pupil") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Pupil Full Name *") })
                OutlinedTextField(value = adm, onValueChange = { adm = it }, label = { Text("Admission Number *") })
                Text("Select Class", style = MaterialTheme.typography.labelLarge)
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
                    Text("Link my account for quick testing/management", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onEnroll(adm, fullName, selectedClass, emName, emPhone, medical, homeDest, pickup, photoUri, linkSelf)
                    onDismiss()
                },
                enabled = fullName.trim().length >= 2 && adm.trim().isNotEmpty()
            ) { Text("Enroll") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParentReportCardScreen(
    school: School?,
    students: List<Student>,
    examReports: List<ExamReport>,
    onExportPdf: (ExamReport) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedStudentId by remember(students) { mutableStateOf(students.firstOrNull()?.id.orEmpty()) }
    val selectedStudent = students.firstOrNull { it.id == selectedStudentId } ?: students.firstOrNull()
    val childReports = examReports.filter { selectedStudent == null || it.studentId == selectedStudent.id }
    var selectedTerm by remember(selectedStudent?.id, childReports) {
        mutableStateOf(childReports.firstOrNull()?.term ?: "Term 1")
    }
    val activeReport = childReports.firstOrNull { it.term == selectedTerm } ?: childReports.firstOrNull()

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
                Text("Terminal Examination Report Cards", style = MaterialTheme.typography.titleLarge)
                if (students.isEmpty()) {
                    EmptyStateCard("No Linked Pupils", "Ask your school admin to link your child to view report cards.")
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        students.forEach { stu ->
                            FilterChip(
                                selected = stu.id == selectedStudent?.id,
                                onClick = { selectedStudentId = stu.id },
                                label = { Text("${stu.fullName} (${stu.className})") }
                            )
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Term 1", "Term 2", "Term 3").forEach { term ->
                            FilterChip(
                                selected = selectedTerm == term,
                                onClick = { selectedTerm = term },
                                label = { Text(term) }
                            )
                        }
                    }
                }
            }
        }

        if (activeReport != null) {
            item {
                Column(modifier = Modifier.widthIn(max = 860.dp)) {
                    TerminalReportCardView(
                        report = activeReport,
                        school = school,
                        onExportPdf = { onExportPdf(activeReport) }
                    )
                }
            }
        } else if (students.isNotEmpty()) {
            item {
                Column(modifier = Modifier.widthIn(max = 860.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    EmptyStateCard(
                        title = "No Published Report for $selectedTerm",
                        subtitle = "Once the class teacher or school administrator saves ${selectedStudent?.fullName.orEmpty()}'s $selectedTerm scores, the full terminal report card will appear here."
                    )
                    GradingScaleSummaryCard()
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
