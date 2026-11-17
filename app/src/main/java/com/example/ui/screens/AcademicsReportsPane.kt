package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceStatus
import com.example.data.model.CefelixAcademyTemplate
import com.example.data.model.ExamGrading
import com.example.data.model.ExamReport
import com.example.data.model.School
import com.example.data.model.SchoolClass
import com.example.data.model.SchoolLevel
import com.example.data.model.Student
import com.example.data.model.Subject
import com.example.ui.components.EmptyStateCard
import com.example.ui.theme.DelayAmber700
import com.example.ui.theme.EmergencyRed600
import com.example.ui.theme.ReportGold
import com.example.ui.theme.ReportNavy
import com.example.ui.theme.SafeBlue100
import com.example.ui.theme.SafeBlue700
import com.example.ui.theme.SafeGreen100
import com.example.ui.theme.SafeGreen700

private data class ClassOption(
    val id: String,
    val name: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IndependentReportsSlide(
    school: School?,
    classes: List<SchoolClass>,
    students: List<Student>,
    subjects: List<Subject>,
    examReports: List<ExamReport>,
    attendance: List<AttendanceRecord>,
    currentUserDisplayName: String,
    isBusy: Boolean,
    onSaveExamReport: (ExamReport, autoRankPosition: Boolean) -> Unit,
    onExportPdf: (ExamReport) -> Unit,
    onExportBlankMasterPdf: () -> Unit = {},
    onApplyCefelixTemplate: () -> Unit = {},
    onGoToSubjects: () -> Unit,
    onEditBranding: () -> Unit
) {
    val activeStudents = remember(students) {
        students.filter { it.status == "active" }.ifEmpty { students }
    }
    val classOptions = remember(classes, activeStudents) {
        if (classes.isNotEmpty()) {
            classes.map { ClassOption(it.id, it.name) }
        } else {
            activeStudents
                .map { ClassOption(it.classId.ifBlank { it.className }, it.className.ifBlank { "Unassigned" }) }
                .distinctBy { it.id }
        }
    }

    var selectedClassId by remember(classOptions) {
        mutableStateOf(classOptions.firstOrNull()?.id.orEmpty())
    }
    val selectedClass = classOptions.firstOrNull { it.id == selectedClassId } ?: classOptions.firstOrNull()

    val classStudents = remember(activeStudents, selectedClass?.id, selectedClass?.name) {
        if (selectedClass == null) {
            activeStudents
        } else {
            activeStudents.filter {
                it.classId == selectedClass.id || it.className.equals(selectedClass.name, ignoreCase = true)
            }
        }
    }

    var selectedTerm by remember { mutableStateOf("Term 1") }
    var schoolLevel by remember(selectedClass?.id, selectedClass?.name) {
        mutableStateOf(
            if (selectedClass?.name?.contains("jhs", ignoreCase = true) == true) {
                SchoolLevel.JHS
            } else {
                SchoolLevel.PRIMARY
            }
        )
    }

    val applicableSubjects = remember(subjects, schoolLevel) {
        subjects.filter { it.level == "both" || it.level == schoolLevel.key }
            .ifEmpty { subjects }
            .sortedBy { it.name.lowercase() }
    }

    var selectedStudentId by remember(classStudents) {
        mutableStateOf(classStudents.firstOrNull()?.id.orEmpty())
    }
    val selectedStudent = classStudents.firstOrNull { it.id == selectedStudentId } ?: classStudents.firstOrNull()

    var academicYear by remember(selectedStudent?.id) {
        mutableStateOf(selectedStudent?.academicYear ?: "2026/2027")
    }

    val existingReport = remember(selectedStudent?.id, selectedTerm, examReports) {
        examReports.firstOrNull { it.studentId == selectedStudent?.id && it.term == selectedTerm }
    }

    val rawClassScores = remember { mutableStateMapOf<String, String>() }
    val rawExamScores = remember { mutableStateMapOf<String, String>() }

    var positionText by remember { mutableStateOf("") }
    var autoRankPosition by remember { mutableStateOf(true) }
    var aggregateOverride by remember { mutableStateOf("") }
    var attendanceSummary by remember { mutableStateOf("") }
    var attitudeAndConduct by remember { mutableStateOf("") }
    var respectfulness by remember { mutableStateOf("") }
    var punctuality by remember { mutableStateOf("") }
    var classroomParticipation by remember { mutableStateOf("") }
    var neatnessAndResponsibility by remember { mutableStateOf("") }
    var teacherRemarks by remember { mutableStateOf("") }
    var headTeacherRemarks by remember { mutableStateOf("") }
    var classTeacherName by remember { mutableStateOf(currentUserDisplayName) }
    var classTeacherSignature by remember { mutableStateOf("") }
    var headTeacherName by remember { mutableStateOf("") }
    var headTeacherSignature by remember { mutableStateOf("") }
    var issuanceDate by remember { mutableStateOf("") }
    var useCefelixPresetHeader by remember(school?.id, school?.name) {
        mutableStateOf(school?.name?.contains("cefelix", ignoreCase = true) == true)
    }

    LaunchedEffect(selectedStudent?.id, selectedTerm, existingReport?.id, applicableSubjects.size) {
        rawClassScores.clear()
        rawExamScores.clear()
        applicableSubjects.forEach { sub ->
            val saved = existingReport?.scores?.firstOrNull {
                it.subjectId == sub.id || it.subjectName.equals(sub.name, ignoreCase = true)
            }
            rawClassScores[sub.id] = saved?.rawClassScore?.let {
                if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it)
            }.orEmpty()
            rawExamScores[sub.id] = saved?.rawExamScore?.let {
                if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it)
            }.orEmpty()
        }
        positionText = existingReport?.positionText.orEmpty()
        autoRankPosition = existingReport?.positionText.isNullOrBlank()
        aggregateOverride = ""
        val stuAtt = attendance.filter { it.studentId == selectedStudent?.id }
        val presentDays = stuAtt.count { it.status == AttendanceStatus.PRESENT.key || it.status == AttendanceStatus.LATE.key }
        val defaultAtt = if (stuAtt.isNotEmpty()) "$presentDays / ${stuAtt.size} days" else ""
        attendanceSummary = existingReport?.attendanceSummary ?: defaultAtt
        attitudeAndConduct = existingReport?.attitudeAndConduct.orEmpty()
        respectfulness = existingReport?.respectfulness.orEmpty()
        punctuality = existingReport?.punctuality.orEmpty()
        classroomParticipation = existingReport?.classroomParticipation.orEmpty()
        neatnessAndResponsibility = existingReport?.neatnessAndResponsibility.orEmpty()
        teacherRemarks = existingReport?.teacherRemarks.orEmpty()
        headTeacherRemarks = existingReport?.headTeacherRemarks.orEmpty()
        classTeacherName = existingReport?.classTeacherName?.ifBlank { currentUserDisplayName } ?: currentUserDisplayName
        classTeacherSignature = existingReport?.classTeacherSignature.orEmpty()
        headTeacherName = existingReport?.headTeacherName.orEmpty()
        headTeacherSignature = existingReport?.headTeacherSignature.orEmpty()
        issuanceDate = existingReport?.issuanceDate.orEmpty()
        if (existingReport?.schoolName?.contains("cefelix", ignoreCase = true) == true) {
            useCefelixPresetHeader = true
        }
    }

    val computedScores = applicableSubjects.map { sub ->
        val rawC = rawClassScores[sub.id]?.toDoubleOrNull() ?: 0.0
        val rawE = rawExamScores[sub.id]?.toDoubleOrNull() ?: 0.0
        ExamGrading.buildScore(sub, rawC, rawE)
    }
    val overallTotal = computedScores.sumOf { it.totalScore }.let { kotlin.math.round(it * 10.0) / 10.0 }
    val averageScore = if (computedScores.isNotEmpty()) {
        (overallTotal / computedScores.size).let { kotlin.math.round(it * 10.0) / 10.0 }
    } else 0.0
    val (autoAggregate, aggregateBreakdown) = ExamGrading.computeAggregate(computedScores, schoolLevel)
    val effectiveAggregate = aggregateOverride.toIntOrNull()?.coerceIn(0, 36) ?: autoAggregate

    val headerSchoolName = if (useCefelixPresetHeader) {
        CefelixAcademyTemplate.SCHOOL_NAME
    } else {
        school?.name?.ifBlank { CefelixAcademyTemplate.SCHOOL_NAME } ?: CefelixAcademyTemplate.SCHOOL_NAME
    }
    val headerSchoolCity = if (useCefelixPresetHeader) {
        CefelixAcademyTemplate.SCHOOL_LOCATION
    } else {
        school?.city?.ifBlank { CefelixAcademyTemplate.SCHOOL_LOCATION } ?: CefelixAcademyTemplate.SCHOOL_LOCATION
    }
    val headerSchoolPhone = if (useCefelixPresetHeader) {
        CefelixAcademyTemplate.CONTACT_PHONE
    } else {
        school?.phone?.ifBlank { CefelixAcademyTemplate.CONTACT_PHONE } ?: CefelixAcademyTemplate.CONTACT_PHONE
    }
    val headerSchoolEmail = if (useCefelixPresetHeader) {
        CefelixAcademyTemplate.CONTACT_EMAIL
    } else {
        school?.email?.ifBlank { CefelixAcademyTemplate.CONTACT_EMAIL } ?: CefelixAcademyTemplate.CONTACT_EMAIL
    }

    val draftReport = selectedStudent?.let { stu ->
        ExamReport(
            id = existingReport?.id.orEmpty(),
            schoolId = school?.id ?: stu.schoolId,
            schoolName = headerSchoolName,
            schoolLogoUri = school?.logoUri,
            schoolCity = headerSchoolCity,
            schoolPhone = headerSchoolPhone,
            schoolEmail = headerSchoolEmail,
            studentId = stu.id,
            studentName = stu.fullName,
            admissionNumber = stu.admissionNumber,
            classId = stu.classId,
            className = stu.className,
            academicYear = academicYear,
            term = selectedTerm,
            level = schoolLevel.key,
            scores = computedScores,
            overallTotal = overallTotal,
            averageScore = averageScore,
            positionText = positionText.ifBlank { existingReport?.positionText ?: "Auto-ranked on save" },
            aggregate = effectiveAggregate,
            aggregateBreakdown = aggregateBreakdown,
            attendanceSummary = attendanceSummary,
            attitudeAndConduct = attitudeAndConduct,
            respectfulness = respectfulness,
            punctuality = punctuality,
            classroomParticipation = classroomParticipation,
            neatnessAndResponsibility = neatnessAndResponsibility,
            teacherRemarks = teacherRemarks,
            headTeacherRemarks = headTeacherRemarks,
            classTeacherName = classTeacherName,
            classTeacherSignature = classTeacherSignature,
            headTeacherName = headTeacherName,
            headTeacherSignature = headTeacherSignature,
            issuanceDate = issuanceDate,
            guardianUids = stu.guardianUids,
            teacherUids = stu.teacherUids,
            adminUids = stu.adminUids
        )
    }

    Column(modifier = Modifier.widthIn(max = 920.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ReportNavy.copy(alpha = 0.06f)),
            border = BorderStroke(1.dp, ReportGold.copy(alpha = 0.45f))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SchoolBrandCrest(schoolName = headerSchoolName, logoUri = school?.logoUri, sizeDp = 48)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(headerSchoolName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ReportNavy)
                        Text(
                            "$headerSchoolCity • $headerSchoolPhone • $headerSchoolEmail",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "International A4 Terminal Report Studio (50% Class + 50% Exam)",
                            style = MaterialTheme.typography.labelSmall,
                            color = SafeBlue700
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEditBranding, enabled = school != null) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Edit School Logo/Name")
                    }
                    OutlinedButton(
                        onClick = {
                            useCefelixPresetHeader = true
                            academicYear = CefelixAcademyTemplate.ACADEMIC_YEAR
                            selectedTerm = CefelixAcademyTemplate.TERM
                            schoolLevel = SchoolLevel.PRIMARY
                            onApplyCefelixTemplate()
                        },
                        enabled = !isBusy,
                        modifier = Modifier.testTag("apply_cefelix_preset_button")
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Cefelix Master Preset")
                    }
                    OutlinedButton(
                        onClick = onExportBlankMasterPdf,
                        modifier = Modifier.testTag("export_blank_master_pdf_top_button")
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Blank Master A4 PDF")
                    }
                }
            }
        }

        if (classOptions.isEmpty()) {
            EmptyStateCard(
                title = "No Classes Found",
                subtitle = "Create a class in School Admin or enroll pupils first to view class reports."
            )
            return
        }

        // Step 1: Select Class & Term
        Card(
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("1. Select Class & Term", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    classOptions.forEach { cls ->
                        FilterChip(
                            selected = cls.id == selectedClass?.id,
                            onClick = { selectedClassId = cls.id },
                            label = { Text(cls.name) },
                            modifier = Modifier.testTag("reports_class_chip_${cls.id}")
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
                Text("School Level", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SchoolLevel.entries.forEach { lvl ->
                        FilterChip(
                            selected = schoolLevel == lvl,
                            onClick = { schoolLevel = lvl },
                            label = { Text(lvl.label) }
                        )
                    }
                }
            }
        }

        if (subjects.isEmpty()) {
            Card(border = BorderStroke(1.dp, DelayAmber700.copy(alpha = 0.4f))) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No Subjects Configured", style = MaterialTheme.typography.titleMedium)
                    Text("Add subjects or load Ghana subjects in the Subjects slide first.", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = onGoToSubjects) { Text("Open Subjects Slide") }
                }
            }
            return
        }

        if (classStudents.isEmpty()) {
            EmptyStateCard(
                title = "No Pupils in ${selectedClass?.name ?: "Class"}",
                subtitle = "Enroll or CSV-import pupils into ${selectedClass?.name ?: "this class"} on the Students slide."
            )
            return
        }

        // Step 2: Whole-Class Gradebook Table (All Pupil Names & All Subjects for selected class)
        ClassGradebookMatrixCard(
            className = selectedClass?.name ?: "Selected Class",
            term = selectedTerm,
            classStudents = classStudents,
            subjects = applicableSubjects,
            examReports = examReports,
            selectedStudentId = selectedStudent?.id.orEmpty(),
            onSelectStudent = { selectedStudentId = it.id }
        )

        // Step 3: Selected Pupil Editable Score Sheet
        if (selectedStudent != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Editing Scores: ${selectedStudent.fullName} (${selectedStudent.className} • $selectedTerm)",
                        style = MaterialTheme.typography.titleMedium,
                        color = SafeBlue700
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        classStudents.forEach { stu ->
                            FilterChip(
                                selected = stu.id == selectedStudent.id,
                                onClick = { selectedStudentId = stu.id },
                                label = { Text(stu.fullName) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = academicYear,
                        onValueChange = { academicYear = it },
                        label = { Text("Academic Year") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Text("Subject Scores (Enter /100; Scaled to 50% Class + 50% Exam)", style = MaterialTheme.typography.titleMedium)
            applicableSubjects.forEach { sub ->
                val rawC = rawClassScores[sub.id]?.toDoubleOrNull() ?: 0.0
                val rawE = rawExamScores[sub.id]?.toDoubleOrNull() ?: 0.0
                val liveScore = ExamGrading.buildScore(sub, rawC, rawE)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (sub.isCore) "${sub.name} (Core)" else sub.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Total: ${"%.1f".format(liveScore.totalScore)}% • Grade ${liveScore.grade} (${liveScore.remark})",
                                style = MaterialTheme.typography.labelMedium,
                                color = when {
                                    liveScore.grade <= 2 -> SafeGreen700
                                    liveScore.grade <= 4 -> SafeBlue700
                                    liveScore.grade == 5 -> DelayAmber700
                                    else -> EmergencyRed600
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = rawClassScores[sub.id].orEmpty(),
                                onValueChange = { rawClassScores[sub.id] = it },
                                label = { Text("Class (/100) -> ${"%.1f".format(liveScore.classScore50)} (50%)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = rawExamScores[sub.id].orEmpty(),
                                onValueChange = { rawExamScores[sub.id] = it },
                                label = { Text("Exam (/100) -> ${"%.1f".format(liveScore.examScore50)} (50%)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Report Summary, Remarks & Signatures (All Editable)", style = MaterialTheme.typography.titleMedium)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Auto-Rank Position in Class on Save", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = autoRankPosition, onCheckedChange = { autoRankPosition = it })
                    }

                    OutlinedTextField(
                        value = positionText,
                        onValueChange = {
                            positionText = it
                            if (it.isNotBlank()) autoRankPosition = false
                        },
                        label = { Text("Position (e.g., 1st of ${classStudents.size})") },
                        placeholder = { Text(if (autoRankPosition) "Auto-calculated on save" else "Enter position manually") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Auto Core Aggregate (${schoolLevel.label}): $autoAggregate • $aggregateBreakdown",
                        style = MaterialTheme.typography.labelMedium,
                        color = SafeGreen700
                    )
                    OutlinedTextField(
                        value = aggregateOverride,
                        onValueChange = { aggregateOverride = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Core Aggregate Override (optional; auto=$autoAggregate)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = attendanceSummary,
                            onValueChange = { attendanceSummary = it },
                            label = { Text("Attendance (e.g., 58 / 60 days)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            val stuAtt = attendance.filter { it.studentId == selectedStudent.id }
                            val presentDays = stuAtt.count { it.status == AttendanceStatus.PRESENT.key || it.status == AttendanceStatus.LATE.key }
                            attendanceSummary = "$presentDays / ${stuAtt.size} days"
                        }) { Text("Auto-Fill") }
                    }

                    Text(
                        "Section E — Learner Conduct & Attitude (Leave blank if not yet assessed)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = ReportNavy
                    )
                    ConductFieldWithChips(
                        label = "Attitude and Conduct",
                        value = attitudeAndConduct,
                        onValueChange = { attitudeAndConduct = it }
                    )
                    ConductFieldWithChips(
                        label = "Respectfulness",
                        value = respectfulness,
                        onValueChange = { respectfulness = it }
                    )
                    ConductFieldWithChips(
                        label = "Punctuality",
                        value = punctuality,
                        onValueChange = { punctuality = it }
                    )
                    ConductFieldWithChips(
                        label = "Participation in Classroom Activities",
                        value = classroomParticipation,
                        onValueChange = { classroomParticipation = it }
                    )
                    ConductFieldWithChips(
                        label = "Neatness and Responsibility",
                        value = neatnessAndResponsibility,
                        onValueChange = { neatnessAndResponsibility = it }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        "Section F & G — Remarks, Sign-Offs & Issuance",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = ReportNavy
                    )
                    OutlinedTextField(
                        value = teacherRemarks,
                        onValueChange = { teacherRemarks = it },
                        label = { Text("Class Teacher's Remarks (Performance, strengths & recommendations)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = headTeacherRemarks,
                        onValueChange = { headTeacherRemarks = it },
                        label = { Text("Head Teacher's Remarks (Overall assessment & encouragement)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = classTeacherName,
                            onValueChange = { classTeacherName = it },
                            label = { Text("Class Teacher Name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = classTeacherSignature,
                            onValueChange = { classTeacherSignature = it },
                            label = { Text("Class Teacher Signature") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = headTeacherName,
                            onValueChange = { headTeacherName = it },
                            label = { Text("Head Teacher Name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = headTeacherSignature,
                            onValueChange = { headTeacherSignature = it },
                            label = { Text("Head Teacher Signature") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = issuanceDate,
                        onValueChange = { issuanceDate = it },
                        label = { Text("Date of Issuance (optional, e.g., 18 Dec 2026)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            if (draftReport != null) {
                                onSaveExamReport(draftReport, autoRankPosition)
                            }
                        },
                        enabled = !isBusy && draftReport != null && computedScores.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().testTag("save_exam_report_button")
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(if (existingReport != null) "Update & Publish Report Card" else "Save & Publish Report Card")
                    }
                }
            }

            if (draftReport != null) {
                Text("Live A4 Terminal Report Card Preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ReportNavy)
                TerminalReportCardView(
                    report = existingReport?.let {
                        draftReport.copy(positionText = if (positionText.isBlank() && autoRankPosition) it.positionText else draftReport.positionText)
                    } ?: draftReport,
                    school = school,
                    onExportPdf = { onExportPdf(draftReport) },
                    onExportBlankMasterPdf = onExportBlankMasterPdf
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConductFieldWithChips(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    val quickOptions = listOf("Excellent", "Very Good", "Good", "Needs Support")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text("Leave blank if not yet rated") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            quickOptions.forEach { option ->
                FilterChip(
                    selected = value.equals(option, ignoreCase = true),
                    onClick = {
                        onValueChange(if (value.equals(option, ignoreCase = true)) "" else option)
                    },
                    label = { Text(option) }
                )
            }
        }
    }
}

@Composable
private fun ClassGradebookMatrixCard(
    className: String,
    term: String,
    classStudents: List<Student>,
    subjects: List<Subject>,
    examReports: List<ExamReport>,
    selectedStudentId: String,
    onSelectStudent: (Student) -> Unit
) {
    val scrollState = rememberScrollState()
    Card(
        modifier = Modifier.fillMaxWidth().testTag("class_reports_matrix_card"),
        border = BorderStroke(1.dp, SafeBlue700.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "$className • $term — All Pupils (${classStudents.size}) & Subjects (${subjects.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Tap any pupil row to edit their 50% class / 50% exam scores, remarks, and print their report card.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column(modifier = Modifier.horizontalScroll(scrollState)) {
                Row(
                    modifier = Modifier
                        .background(SafeBlue100.copy(alpha = 0.65f))
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Pupil Name",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(150.dp)
                    )
                    subjects.forEach { sub ->
                        Text(
                            text = sub.code.ifBlank { sub.name.take(8) },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(74.dp)
                        )
                    }
                    Text(
                        "Avg %",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(64.dp)
                    )
                    Text(
                        "Agg.",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(52.dp)
                    )
                    Text(
                        "Pos.",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(74.dp)
                    )
                }

                classStudents.forEach { stu ->
                    val report = examReports.firstOrNull { it.studentId == stu.id && it.term == term }
                    val isSelected = stu.id == selectedStudentId
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Row(
                        modifier = Modifier
                            .background(
                                if (isSelected) SafeGreen100.copy(alpha = 0.45f)
                                else MaterialTheme.colorScheme.surface
                            )
                            .clickable { onSelectStudent(stu) }
                            .padding(horizontal = 8.dp, vertical = 10.dp)
                            .testTag("class_report_row_${stu.id}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.width(150.dp)) {
                            Text(
                                text = stu.fullName,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (report != null) SafeGreen100 else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = if (report != null) "Published" else "Tap to score",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (report != null) SafeGreen700 else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }

                        subjects.forEach { sub ->
                            val score = report?.scores?.firstOrNull {
                                it.subjectId == sub.id || it.subjectName.equals(sub.name, ignoreCase = true)
                            }
                            Text(
                                text = if (score != null) "${score.totalScore.toInt()}% (G${score.grade})" else "—",
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                color = when {
                                    score == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                    score.grade <= 2 -> SafeGreen700
                                    score.grade <= 4 -> SafeBlue700
                                    score.grade == 5 -> DelayAmber700
                                    else -> EmergencyRed600
                                },
                                fontWeight = if (score != null) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.width(74.dp)
                            )
                        }

                        Text(
                            text = if (report != null) "${"%.1f".format(report.averageScore)}%" else "—",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(64.dp)
                        )
                        Text(
                            text = if (report != null && report.aggregate > 0) report.aggregate.toString() else "—",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(52.dp)
                        )
                        Text(
                            text = report?.positionText?.ifBlank { "—" } ?: "—",
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(74.dp)
                        )
                    }
                }
            }
        }
    }
}
