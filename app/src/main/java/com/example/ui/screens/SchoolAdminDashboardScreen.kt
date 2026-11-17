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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.AppDistributionConfig
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceStatus
import com.example.data.model.AuditLog
import com.example.data.model.DeviceViewLayoutMode
import com.example.data.model.Incident
import com.example.data.model.Journey
import com.example.data.model.School
import com.example.data.model.SchoolClass
import com.example.data.model.SchoolInvite
import com.example.data.model.SchoolMember
import com.example.data.model.Student
import com.example.data.model.SubscriptionPlan
import com.example.data.model.UserRole
import com.example.ui.components.AppShareDownloadCard
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.EditTeacherMemberDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.SchoolGeofenceConfigCard
import com.example.ui.components.TeachersDirectoryCard
import com.example.ui.theme.DelayAmber700
import com.example.ui.theme.EmergencyRed600
import com.example.ui.theme.SafeBlue700
import com.example.ui.theme.SafeGreen600
import com.example.ui.theme.SafeGreen700
import com.example.util.ReportType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SchoolAdminDashboardScreen(
    school: School?,
    classes: List<SchoolClass>,
    students: List<Student>,
    journeys: List<Journey>,
    attendance: List<AttendanceRecord>,
    incidents: List<Incident>,
    members: List<SchoolMember>,
    invites: List<SchoolInvite>,
    auditLogs: List<AuditLog>,
    plans: List<SubscriptionPlan>,
    subjects: List<com.example.data.model.Subject> = emptyList(),
    isBusy: Boolean,
    onCreateClass: (name: String, grade: String, year: String) -> Unit,
    onEnrollStudent: (
        adm: String, name: String, schoolClass: SchoolClass,
        emName: String, emPhone: String, medical: String,
        homeDest: String, pickup: String, photoUri: String?, linkSelf: Boolean
    ) -> Unit,
    onCreateInvite: (email: String, role: UserRole) -> Unit,
    onLinkMemberToStudent: (Student, memberUid: String, UserRole) -> Unit,
    onUpdateStudentLifecycle: (Student, newStatus: String) -> Unit,
    onUpdateSchoolProfile: (name: String, city: String, phone: String, email: String, logoUri: String?) -> Unit,
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
    onUpdateSubscription: (School, SubscriptionPlan?, billingCycle: String, provider: String, ref: String?) -> Unit,
    onCaptureSchoolGps: ((Double, Double) -> Unit) -> Unit,
    onSaveSchoolGeofence: (latitude: Double, longitude: Double, radiusMeters: Double, enabled: Boolean) -> Unit,
    onExportPdf: (ReportType) -> Unit,
    onExportCsv: (ReportType) -> Unit,
    appDistributionConfig: AppDistributionConfig = AppDistributionConfig(),
    deviceViewLayoutMode: DeviceViewLayoutMode = DeviceViewLayoutMode.AUTO,
    onOpenAppAccessDialog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedSection by remember { mutableIntStateOf(0) }
    val sections = listOf("Overview", "Classes & Pupils", "Staff & Invites", "School Access", "Reports & Audit")

    var showCreateClassDialog by remember { mutableStateOf(false) }
    var showEnrollDialog by remember { mutableStateOf(false) }
    var showInviteDialog by remember { mutableStateOf(false) }
    var showBrandingDialog by remember { mutableStateOf(false) }
    var editingStudent by remember { mutableStateOf<Student?>(null) }
    var deletingStudent by remember { mutableStateOf<Student?>(null) }
    var linkTargetStudent by remember { mutableStateOf<Student?>(null) }
    var editingMember by remember { mutableStateOf<SchoolMember?>(null) }
    var showTeacherDialog by remember { mutableStateOf(false) }
    var deletingMember by remember { mutableStateOf<SchoolMember?>(null) }

    val presentTotal = attendance.count { it.status == AttendanceStatus.PRESENT.key }
    val attendancePct = if (attendance.isNotEmpty()) "${(presentTotal * 100 / attendance.size)}%" else "—"

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ScrollableTabRow(selectedTabIndex = selectedSection, edgePadding = 16.dp) {
            sections.forEachIndexed { idx, label ->
                Tab(
                    selected = selectedSection == idx,
                    onClick = { selectedSection = idx },
                    text = { Text(label) }
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            when (selectedSection) {
                0 -> {
                    item {
                        Column(modifier = Modifier.widthIn(max = 860.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            school?.let { s ->
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        SchoolBrandCrest(schoolName = s.name, logoUri = s.logoUri, sizeDp = 50)
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("${s.name} (${s.code})", style = MaterialTheme.typography.titleLarge)
                                            Text(
                                                "${s.city}, ${s.country} • ${s.planName} (${s.subscriptionStatus.uppercase()})",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        OutlinedButton(onClick = { showBrandingDialog = true }) {
                                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.size(4.dp))
                                            Text("Logo")
                                        }
                                    }
                                }
                            }
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                maxItemsInEachRow = 4
                            ) {
                                KpiMetricCard("Enrollment", "${students.size}", Icons.Filled.Groups, modifier = Modifier.weight(1f))
                                KpiMetricCard("Staff & Members", members.size.toString(), Icons.Filled.Badge, accentColor = SafeBlue700, modifier = Modifier.weight(1f))
                                KpiMetricCard("Attendance Rate", attendancePct, Icons.Filled.Class, accentColor = SafeGreen600, modifier = Modifier.weight(1f))
                                KpiMetricCard("Open Incidents", incidents.count { it.status != "resolved" }.toString(), Icons.Filled.Warning, accentColor = EmergencyRed600, modifier = Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = { showCreateClassDialog = true }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Filled.Add, contentDescription = null)
                                    Spacer(Modifier.size(4.dp))
                                    Text("Add Class")
                                }
                                Button(
                                    onClick = { showEnrollDialog = true },
                                    enabled = classes.isNotEmpty(),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null)
                                    Spacer(Modifier.size(4.dp))
                                    Text("Enroll Pupil")
                                }
                            }
                            school?.let { s ->
                                SchoolGeofenceConfigCard(
                                    school = s,
                                    isBusy = isBusy,
                                    onCaptureCurrentGps = onCaptureSchoolGps,
                                    onSaveGeofence = onSaveSchoolGeofence
                                )
                            }
                        }
                    }
                }

                1 -> {
                    item {
                        Row(
                            modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(onClick = { showCreateClassDialog = true }, modifier = Modifier.weight(1f).testTag("add_class_button")) {
                                Text("New Class")
                            }
                            Button(
                                onClick = { showEnrollDialog = true },
                                enabled = classes.isNotEmpty(),
                                modifier = Modifier.weight(1f).testTag("enroll_pupil_button")
                            ) {
                                Text("Enroll Pupil")
                            }
                        }
                    }
                    if (classes.isEmpty()) {
                        item {
                            Column(modifier = Modifier.widthIn(max = 860.dp)) {
                                EmptyStateCard("No Classes Created", "Create your first class before enrolling pupils.")
                            }
                        }
                    } else {
                        item {
                            Column(modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth()) {
                                Text("Classes (${classes.size})", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        items(classes, key = { it.id }) { cls ->
                            Card(modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(cls.name, style = MaterialTheme.typography.titleMedium)
                                        Text("${cls.gradeLevel} • ${cls.academicYear}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text("${cls.teacherUids.size} teacher(s)", style = MaterialTheme.typography.labelMedium, color = SafeBlue700)
                                }
                            }
                        }
                    }
                    item {
                        Column(modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth()) {
                            Text("Pupils Directory (${students.size})", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    items(students, key = { it.id }) { stu ->
                        Card(
                            modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth(),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
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
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(stu.fullName, style = MaterialTheme.typography.titleMedium)
                                        Text("${stu.className} • ${stu.admissionNumber} • ${stu.status.uppercase()}", style = MaterialTheme.typography.labelSmall)
                                        if (!stu.emergencyContactName.isNullOrBlank()) {
                                            Text("Emergency: ${stu.emergencyContactName} (${stu.emergencyContactPhone.orEmpty()})", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row {
                                            IconButton(
                                                onClick = { editingStudent = stu },
                                                modifier = Modifier.testTag("admin_edit_student_${stu.id}")
                                            ) {
                                                Icon(Icons.Filled.Edit, contentDescription = "Edit ${stu.fullName}", tint = SafeBlue700)
                                            }
                                            IconButton(
                                                onClick = { deletingStudent = stu },
                                                modifier = Modifier.testTag("admin_delete_student_${stu.id}")
                                            ) {
                                                Icon(Icons.Filled.Delete, contentDescription = "Delete ${stu.fullName}", tint = EmergencyRed600)
                                            }
                                        }
                                        OutlinedButton(onClick = { linkTargetStudent = stu }) {
                                            Text("Link")
                                        }
                                    }
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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

                2 -> {
                    item {
                        Button(
                            onClick = { showInviteDialog = true },
                            modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth().testTag("create_invite_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text("Generate Teacher / Parent / Pickup Invite")
                        }
                    }
                    item {
                        Column(modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth()) {
                            Text("School Invites (${invites.size})", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    items(invites, key = { it.id }) { inv ->
                        Card(modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(inv.inviteCode, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${UserRole.fromKey(inv.role).label} • ${inv.targetEmail.ifBlank { "Open invite code" }}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Text(inv.status.uppercase(), style = MaterialTheme.typography.labelMedium, color = SafeGreen700)
                            }
                        }
                    }
                    item {
                        Column(modifier = Modifier.widthIn(max = 860.dp)) {
                            TeachersDirectoryCard(
                                members = members,
                                classes = classes,
                                currentUserRole = UserRole.SCHOOL_ADMIN,
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
                }

                3 -> {
                    item {
                        school?.let { s ->
                            var selectedCycle by remember(s.id, s.billingCycle) { mutableStateOf(s.billingCycle.ifBlank { "monthly" }) }
                            var selectedProvider by remember(s.id, s.paymentProvider) { mutableStateOf(s.paymentProvider.ifBlank { "Paystack" }) }
                            var paymentRef by remember(s.id, s.paymentReference) { mutableStateOf(s.paymentReference.orEmpty()) }
                            var chosenPlanId by remember(s.id, s.planId) { mutableStateOf(s.planId.ifBlank { "starter" }) }
                            val chosenPlan = plans.firstOrNull { it.id == chosenPlanId } ?: plans.firstOrNull()

                            Card(modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth()) {
                                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Filled.Payment, contentDescription = null, tint = SafeBlue700)
                                        Text("School Subscription & Plan", style = MaterialTheme.typography.titleLarge)
                                    }
                                    val priceLabel = if (s.monthlyPriceGhs <= 0.0 && s.planId == "free_forever") {
                                        "Legacy Free Plan (GH₵0/mo)"
                                    } else {
                                        "GH₵${s.monthlyPriceGhs.toInt()}/mo"
                                    }
                                    Text(
                                        "Current plan: ${s.planName} • $priceLabel • Status: ${s.subscriptionStatus.uppercase()} • Enrolled: ${students.size}/${s.pupilLimit} pupils",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (plans.isNotEmpty()) {
                                        Text("Select Plan Tier", style = MaterialTheme.typography.labelLarge)
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            plans.forEach { p ->
                                                FilterChip(
                                                    selected = chosenPlanId == p.id,
                                                    onClick = { chosenPlanId = p.id },
                                                    label = { Text("${p.name} (GH₵${p.monthlyPriceGhs.toInt()}/mo • ${p.pupilLimit} pupils)") }
                                                )
                                            }
                                        }
                                    }
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf("monthly" to "Monthly Billing", "annual" to "Annual Billing").forEach { (cycleKey, label) ->
                                            FilterChip(
                                                selected = selectedCycle == cycleKey,
                                                onClick = { selectedCycle = cycleKey },
                                                label = { Text(label) }
                                            )
                                        }
                                    }
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf("Paystack", "Hubtel", "Bank Transfer").forEach { prov ->
                                            FilterChip(
                                                selected = selectedProvider == prov,
                                                onClick = { selectedProvider = prov },
                                                label = { Text(prov) }
                                            )
                                        }
                                    }
                                    OutlinedTextField(
                                        value = paymentRef,
                                        onValueChange = { paymentRef = it },
                                        label = { Text("Payment / Invoice Reference (optional)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Button(
                                        onClick = {
                                            onUpdateSubscription(
                                                s,
                                                chosenPlan,
                                                selectedCycle,
                                                selectedProvider,
                                                paymentRef.trim().ifBlank { null }
                                            )
                                        },
                                        enabled = !isBusy,
                                        modifier = Modifier.fillMaxWidth().testTag("update_subscription_button")
                                    ) {
                                        Text("Save Subscription & Billing Selection")
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Column(modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth()) {
                            AppShareDownloadCard(
                                config = appDistributionConfig,
                                currentDeviceLayoutMode = deviceViewLayoutMode,
                                canEditAccessSettings = true,
                                onOpenFullDialog = onOpenAppAccessDialog
                            )
                        }
                    }
                }

                4 -> {
                    item {
                        Card(modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Generate Printable PDF & Excel CSV Reports", style = MaterialTheme.typography.titleLarge)
                                ReportType.entries.forEach { reportType ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(reportType.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            OutlinedButton(onClick = { onExportPdf(reportType) }) {
                                                Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.size(4.dp))
                                                Text("PDF")
                                            }
                                            OutlinedButton(onClick = { onExportCsv(reportType) }) {
                                                Icon(Icons.Filled.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.size(4.dp))
                                                Text("Excel CSV")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Column(modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth()) {
                            Text("Immutable School Audit Log (${auditLogs.size})", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    items(auditLogs.take(25), key = { it.id }) { log ->
                        Card(modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("${log.action} • ${log.actorName} (${log.actorRole})", style = MaterialTheme.typography.titleSmall)
                                Text(log.details, style = MaterialTheme.typography.bodySmall)
                                if (!log.reason.isNullOrBlank()) {
                                    Text("Reason: ${log.reason}", style = MaterialTheme.typography.labelSmall, color = DelayAmber700)
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showCreateClassDialog) {
        var name by remember { mutableStateOf("") }
        var grade by remember { mutableStateOf("") }
        var year by remember { mutableStateOf("2026/2027") }
        AlertDialog(
            onDismissRequest = { showCreateClassDialog = false },
            title = { Text("Create School Class") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Class Name *") }, placeholder = { Text("Primary 4A") })
                    OutlinedTextField(value = grade, onValueChange = { grade = it }, label = { Text("Grade / Level") }, placeholder = { Text("Grade 4") })
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        Text("Link my account as guardian for testing/management", style = MaterialTheme.typography.bodySmall)
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

    if (showInviteDialog) {
        var targetEmail by remember { mutableStateOf("") }
        var role by remember { mutableStateOf(UserRole.TEACHER) }
        AlertDialog(
            onDismissRequest = { showInviteDialog = false },
            title = { Text("Invite Member to ${school?.name.orEmpty()}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = targetEmail,
                        onValueChange = { targetEmail = it },
                        label = { Text("Email (optional; leave blank for code-only)") },
                        singleLine = true
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(UserRole.TEACHER, UserRole.PARENT, UserRole.PICKUP_PERSON, UserRole.SCHOOL_ADMIN).forEach { r ->
                            FilterChip(selected = role == r, onClick = { role = r }, label = { Text(r.label) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onCreateInvite(targetEmail, role)
                    showInviteDialog = false
                }) { Text("Generate Invite") }
            },
            dismissButton = { TextButton(onClick = { showInviteDialog = false }) { Text("Cancel") } }
        )
    }

    if (linkTargetStudent != null) {
        val stu = linkTargetStudent!!
        AlertDialog(
            onDismissRequest = { linkTargetStudent = null },
            title = { Text("Link Member to ${stu.fullName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
            message = "Permanently remove ${deletingStudent!!.fullName} (${deletingStudent!!.admissionNumber}) from ${school?.name ?: "school"}? This cannot be undone.",
            onDismiss = { deletingStudent = null },
            onConfirmDelete = { onDeleteStudent(deletingStudent!!) }
        )
    }

    if (showTeacherDialog) {
        EditTeacherMemberDialog(
            existingMember = editingMember,
            classes = classes,
            subjects = subjects,
            allowRoleChange = true,
            onDismiss = { showTeacherDialog = false },
            onSave = { name, email, phone, role, assignedIds, subjectIds, subjectNames, status ->
                onSaveTeacherMember(editingMember, name, email, phone, role, assignedIds, subjectIds, subjectNames, status)
            }
        )
    }

    if (deletingMember != null) {
        ConfirmDeleteDialog(
            title = "Delete Staff / Member?",
            message = "Remove ${deletingMember!!.displayName} (${UserRole.fromKey(deletingMember!!.role).label}) from the school directory?",
            onDismiss = { deletingMember = null },
            onConfirmDelete = { onDeleteTeacherMember(deletingMember!!) }
        )
    }
}
