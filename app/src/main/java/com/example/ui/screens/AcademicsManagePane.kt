package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.UploadFile
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ExamGrading
import com.example.data.model.School
import com.example.data.model.SchoolClass
import com.example.data.model.Student
import com.example.data.model.Subject
import com.example.ui.components.EmptyStateCard
import com.example.ui.theme.SafeBlue100
import com.example.ui.theme.SafeBlue700
import com.example.ui.theme.SafeGreen700
import com.example.util.CsvStudentParser
import com.example.util.ParsedStudentRow

@OptIn(ExperimentalLayoutApi::class)
fun LazyListScope.subjectsManagerSection(
    subjects: List<Subject>,
    isBusy: Boolean,
    onSeedDefaultSubjects: () -> Unit,
    onOpenSubjectDialog: (Subject?) -> Unit,
    onDeleteSubject: (Subject) -> Unit
) {
    item {
        Card(modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Subjects & Core Configuration", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Manage subjects taught across classes and mark core subjects for aggregate grading.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onOpenSubjectDialog(null) },
                        modifier = Modifier.weight(1f).testTag("add_subject_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Add Subject")
                    }
                    OutlinedButton(
                        onClick = onSeedDefaultSubjects,
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f).testTag("seed_subjects_button")
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Load Ghana Subjects")
                    }
                }
            }
        }
    }

    if (subjects.isEmpty()) {
        item {
            Column(modifier = Modifier.widthIn(max = 860.dp)) {
                EmptyStateCard(
                    title = "No Subjects Added Yet",
                    subtitle = "Tap 'Load Ghana Subjects' to add Mathematics, English, Science, Computing, Social Studies & electives, or add custom subjects."
                )
            }
        }
    } else {
        items(subjects, key = { "sub_${it.id}" }) { sub ->
            Card(
                modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(sub.name, style = MaterialTheme.typography.titleMedium)
                            if (sub.isCore) {
                                Surface(color = SafeBlue100, shape = MaterialTheme.shapes.small) {
                                    Text(
                                        "CORE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SafeBlue700,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            "Code: ${sub.code.ifBlank { "—" }} • Level: ${sub.level.uppercase()}${sub.coreSlot.takeIf { it.isNotBlank() }?.let { " • Slot: $it" } ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row {
                        IconButton(onClick = { onOpenSubjectDialog(sub) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit ${sub.name}", tint = SafeBlue700)
                        }
                        IconButton(onClick = { onDeleteSubject(sub) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete ${sub.name}", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubjectEditorDialog(
    existingSubject: Subject?,
    onDismiss: () -> Unit,
    onSave: (name: String, code: String, level: String, isCore: Boolean, coreSlot: String) -> Unit
) {
    var name by remember(existingSubject?.id) { mutableStateOf(existingSubject?.name.orEmpty()) }
    var code by remember(existingSubject?.id) { mutableStateOf(existingSubject?.code.orEmpty()) }
    var level by remember(existingSubject?.id) { mutableStateOf(existingSubject?.level ?: "both") }
    var isCore by remember(existingSubject?.id) { mutableStateOf(existingSubject?.isCore ?: false) }
    var coreSlot by remember(existingSubject?.id) { mutableStateOf(existingSubject?.coreSlot.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingSubject != null) "Edit Subject" else "Add New Subject") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (coreSlot.isBlank() || existingSubject == null) {
                            val inferred = ExamGrading.inferCoreSlot(it)
                            if (inferred.isNotBlank()) {
                                coreSlot = inferred
                                isCore = true
                            }
                        }
                    },
                    label = { Text("Subject Name *") },
                    placeholder = { Text("e.g., Mathematics, Computing, French") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("subject_name_input")
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Subject Code (optional)") },
                    placeholder = { Text("MATH") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("School Level", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("both" to "Primary & JHS", "primary" to "Primary", "jhs" to "JHS").forEach { (key, label) ->
                        FilterChip(selected = level == key, onClick = { level = key }, label = { Text(label) })
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Counts toward Core Aggregate", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isCore, onCheckedChange = { isCore = it })
                }
                if (isCore) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "mathematics" to "Math",
                            "english" to "English",
                            "science" to "Science",
                            "computing" to "Computing",
                            "social_studies" to "Social Studies"
                        ).forEach { (slot, label) ->
                            FilterChip(selected = coreSlot == slot, onClick = { coreSlot = slot }, label = { Text(label) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(name, code, level, isCore, if (isCore) coreSlot else "")
                    onDismiss()
                },
                enabled = name.trim().length >= 2
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CsvImportAndBrandingCard(
    school: School?,
    classes: List<SchoolClass>,
    students: List<Student>,
    isBusy: Boolean,
    onEditSchoolBranding: () -> Unit,
    onBulkImportStudents: (defaultClass: SchoolClass, rows: List<ParsedStudentRow>, linkSelf: Boolean) -> Unit,
    onEditStudent: (Student) -> Unit,
    onDeleteStudent: (Student) -> Unit
) {
    val context = LocalContext.current
    var csvText by remember { mutableStateOf("") }
    var linkSelf by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedClassId by remember(classes) { mutableStateOf(classes.firstOrNull()?.id.orEmpty()) }
    val defaultClass = classes.firstOrNull { it.id == selectedClassId } ?: classes.firstOrNull()
    val parsedRows = remember(csvText, defaultClass?.name) {
        CsvStudentParser.parse(csvText, defaultClass?.name ?: "Primary 1")
    }

    val csvFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()?.let { content ->
                csvText = content
            }
        }
    }

    Column(modifier = Modifier.widthIn(max = 860.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // School Name & Logo Card
        school?.let { s ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SchoolBrandCrest(schoolName = s.name, logoUri = s.logoUri, sizeDp = 52)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(s.name, style = MaterialTheme.typography.titleMedium)
                        Text("${s.city} • ${s.phone.orEmpty()} • ${s.email.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = onEditSchoolBranding, modifier = Modifier.testTag("edit_school_branding_button")) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Logo & Name")
                    }
                }
            }
        }

        // Bulk Student CSV Import Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("List & Import Students via CSV or Name Roster", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Pick a .csv file or paste student rows (supports full 7-column CSV or simply one pupil name per line).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { csvFilePicker.launch(arrayOf("text/*", "application/csv", "*/*")) },
                        modifier = Modifier.weight(1f).testTag("pick_csv_file_button")
                    ) {
                        Icon(Icons.Filled.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Pick .CSV File")
                    }
                    OutlinedButton(
                        onClick = { csvText = CsvStudentParser.SAMPLE_CSV_TEMPLATE },
                        modifier = Modifier.weight(1f).testTag("load_sample_csv_button")
                    ) {
                        Text("Load Sample CSV")
                    }
                }

                if (classes.isEmpty()) {
                    Text(
                        "Create at least one class first (in School Admin -> Classes & Pupils) before importing students.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text("Default Class (used when CSV ClassName is omitted)", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        classes.forEach { cls ->
                            FilterChip(
                                selected = defaultClass?.id == cls.id,
                                onClick = { selectedClassId = cls.id },
                                label = { Text(cls.name) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = csvText,
                    onValueChange = { csvText = it },
                    label = { Text("Paste CSV rows or student names (one per line)") },
                    placeholder = { Text("ADM-101,Kwame Mensah,Primary 4A,Abena Mensah,+233244000111,East Legon,Abena Mensah") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth().testTag("csv_paste_input")
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = linkSelf, onCheckedChange = { linkSelf = it })
                    Text("Link my account as guardian for quick access", style = MaterialTheme.typography.bodySmall)
                }

                if (parsedRows.isNotEmpty()) {
                    Text(
                        text = "Ready to import ${parsedRows.size} pupil(s): ${parsedRows.take(3).joinToString { "${it.fullName} (${it.admissionNumber})" }}${if (parsedRows.size > 3) "..." else ""}",
                        style = MaterialTheme.typography.labelMedium,
                        color = SafeGreen700,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = {
                        if (defaultClass != null) {
                            onBulkImportStudents(defaultClass, parsedRows, linkSelf)
                            csvText = ""
                        }
                    },
                    enabled = !isBusy && defaultClass != null && parsedRows.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().testTag("import_csv_students_button")
                ) {
                    Icon(Icons.Filled.UploadFile, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Import ${parsedRows.size} Pupil(s) to School")
                }
            }
        }

        // Editable Student Roster
        Text("Editable Student Roster (${students.size})", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search pupil by name, admission # or class") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        val filteredStudents = students.filter {
            searchQuery.isBlank() ||
                it.fullName.contains(searchQuery, ignoreCase = true) ||
                it.admissionNumber.contains(searchQuery, ignoreCase = true) ||
                it.className.contains(searchQuery, ignoreCase = true)
        }
        filteredStudents.forEach { stu ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stu.fullName, style = MaterialTheme.typography.titleSmall)
                        Text("${stu.admissionNumber} • ${stu.className} • ${stu.status.uppercase()}", style = MaterialTheme.typography.bodySmall)
                    }
                    Row {
                        IconButton(onClick = { onEditStudent(stu) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit ${stu.fullName}", tint = SafeBlue700)
                        }
                        IconButton(onClick = { onDeleteStudent(stu) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete ${stu.fullName}", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
