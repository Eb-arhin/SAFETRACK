package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.FeeRecord
import com.example.data.model.School
import com.example.data.model.Student
import com.example.data.model.SubscriptionPlan
import com.example.data.model.UserRole
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.KpiMetricCard
import com.example.ui.theme.DelayAmber100
import com.example.ui.theme.DelayAmber700
import com.example.ui.theme.EmergencyRed100
import com.example.ui.theme.EmergencyRed600
import com.example.ui.theme.EmergencyRed700
import com.example.ui.theme.SafeBlue700
import com.example.ui.theme.SafeGreen100
import com.example.ui.theme.SafeGreen600
import com.example.ui.theme.SafeGreen700

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FinanceScreen(
    role: UserRole,
    school: School?,
    students: List<Student>,
    feeRecords: List<FeeRecord>,
    plans: List<SubscriptionPlan>,
    isBusy: Boolean,
    onSaveFeeRecord: (
        student: Student,
        existingRecord: FeeRecord?,
        title: String,
        term: String,
        academicYear: String,
        amountDueGhs: Double,
        amountPaidGhs: Double,
        paymentMethod: String,
        receiptNumber: String,
        dueDate: String
    ) -> Unit,
    onDeleteFeeRecord: (FeeRecord) -> Unit,
    onUpdateSubscription: (School, SubscriptionPlan?, billingCycle: String, provider: String, ref: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isAdmin = role == UserRole.SCHOOL_ADMIN || role == UserRole.SUPER_ADMIN
    var selectedTab by remember { mutableIntStateOf(0) }
    var statusFilter by remember { mutableStateOf("all") }
    var showFeeDialog by remember { mutableStateOf(false) }
    var editingFee by remember { mutableStateOf<FeeRecord?>(null) }
    var deletingFee by remember { mutableStateOf<FeeRecord?>(null) }

    val totalDue = feeRecords.sumOf { it.amountDueGhs }
    val totalPaid = feeRecords.sumOf { it.amountPaidGhs }
    val outstanding = (totalDue - totalPaid).coerceAtLeast(0.0)
    val filteredFees = remember(feeRecords, statusFilter) {
        if (statusFilter == "all") feeRecords else feeRecords.filter { it.status == statusFilter }
    }

    Column(modifier = modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (isAdmin) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Pupil Fees & Ledger") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Platform Subscription") })
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

            if (selectedTab == 0 || !isAdmin) {
                item {
                    Column(modifier = Modifier.widthIn(max = 920.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isAdmin) "Tuition & School Fees Ledger" else "Child Fee Statements",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (feeRecords.isNotEmpty()) {
                                    OutlinedButton(onClick = {
                                        val csvSummary = buildString {
                                            appendLine("Pupil,Class,Invoice,Term,Due (GHS),Paid (GHS),Status,Receipt")
                                            feeRecords.forEach { f ->
                                                appendLine("\"${f.studentName}\",\"${f.className}\",\"${f.title}\",\"${f.term}\",${f.amountDueGhs},${f.amountPaidGhs},${f.status},\"${f.receiptNumber}\"")
                                            }
                                        }
                                        val share = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, "${school?.name ?: "School"} Fee Statement")
                                            putExtra(Intent.EXTRA_TEXT, csvSummary)
                                        }
                                        runCatching { context.startActivity(Intent.createChooser(share, "Share Fee Ledger")) }
                                    }) {
                                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.size(4.dp))
                                        Text("Export")
                                    }
                                }
                                if (isAdmin) {
                                    Button(
                                        onClick = {
                                            editingFee = null
                                            showFeeDialog = true
                                        },
                                        enabled = !isBusy && students.isNotEmpty(),
                                        modifier = Modifier.testTag("add_fee_record_button")
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.size(4.dp))
                                        Text("Add Fee")
                                    }
                                }
                            }
                        }

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            KpiMetricCard(
                                label = "Total Billed",
                                value = "GH₵${"%.0f".format(totalDue)}",
                                icon = Icons.Filled.AccountBalanceWallet,
                                accentColor = SafeBlue700,
                                modifier = Modifier.weight(1f)
                            )
                            KpiMetricCard(
                                label = "Collected",
                                value = "GH₵${"%.0f".format(totalPaid)}",
                                icon = Icons.Filled.CheckCircle,
                                accentColor = SafeGreen600,
                                modifier = Modifier.weight(1f)
                            )
                            KpiMetricCard(
                                label = "Outstanding",
                                value = "GH₵${"%.0f".format(outstanding)}",
                                icon = Icons.Filled.Warning,
                                accentColor = if (outstanding > 0.0) DelayAmber700 else SafeGreen600,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("all" to "All", "paid" to "Paid", "partial" to "Partial", "unpaid" to "Unpaid").forEach { (k, label) ->
                                FilterChip(
                                    selected = statusFilter == k,
                                    onClick = { statusFilter = k },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }

                if (filteredFees.isEmpty()) {
                    item {
                        Column(modifier = Modifier.widthIn(max = 920.dp)) {
                            EmptyStateCard(
                                "No Fee Records",
                                if (isAdmin) "Tap '+ Add Fee' to record tuition invoices or term fee payments for enrolled pupils."
                                else "No tuition invoices have been published for your linked children yet."
                            )
                        }
                    }
                } else {
                    items(filteredFees, key = { it.id }) { fee ->
                        val (bg, fg) = when (fee.status) {
                            "paid", "waived" -> SafeGreen100 to SafeGreen700
                            "partial" -> DelayAmber100 to DelayAmber700
                            else -> EmergencyRed100 to EmergencyRed700
                        }
                        val balance = (fee.amountDueGhs - fee.amountPaidGhs).coerceAtLeast(0.0)
                        Card(
                            modifier = Modifier.widthIn(max = 920.dp).fillMaxWidth(),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("${fee.studentName} • ${fee.title}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        Surface(shape = RoundedCornerShape(50), color = bg) {
                                            Text(
                                                text = fee.status.uppercase(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = fg,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        "${fee.className} • ${fee.term} (${fee.academicYear}) • Paid: GH₵${"%.2f".format(fee.amountPaidGhs)} / GH₵${"%.2f".format(fee.amountDueGhs)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "Balance: GH₵${"%.2f".format(balance)} • Method: ${fee.paymentMethod}${if (fee.receiptNumber.isNotBlank()) " • Receipt #${fee.receiptNumber}" else ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (isAdmin) {
                                    Row {
                                        IconButton(onClick = {
                                            editingFee = fee
                                            showFeeDialog = true
                                        }) {
                                            Icon(Icons.Filled.Edit, contentDescription = "Edit fee", tint = SafeBlue700)
                                        }
                                        IconButton(onClick = { deletingFee = fee }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete fee", tint = EmergencyRed600)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    school?.let { s ->
                        var selectedCycle by remember(s.id, s.billingCycle) { mutableStateOf(s.billingCycle.ifBlank { "monthly" }) }
                        var selectedProvider by remember(s.id, s.paymentProvider) { mutableStateOf(s.paymentProvider.ifBlank { "Paystack" }) }
                        var paymentRef by remember(s.id, s.paymentReference) { mutableStateOf(s.paymentReference.orEmpty()) }
                        var chosenPlanId by remember(s.id, s.planId) { mutableStateOf(s.planId.ifBlank { "starter" }) }
                        val chosenPlan = plans.firstOrNull { it.id == chosenPlanId } ?: plans.firstOrNull()

                        Card(
                            modifier = Modifier.widthIn(max = 920.dp).fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                        ) {
                            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Filled.Payment, contentDescription = null, tint = SafeBlue700)
                                    Text("School Subscription & Billing", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    "Current plan: ${s.planName} • Status: ${s.subscriptionStatus.uppercase()} • Capacity: ${students.size}/${s.pupilLimit} pupils",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (plans.isNotEmpty()) {
                                    Text("Select Platform Plan", style = MaterialTheme.typography.labelLarge)
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
                                        onUpdateSubscription(s, chosenPlan, selectedCycle, selectedProvider, paymentRef.trim().ifBlank { null })
                                    },
                                    enabled = !isBusy,
                                    modifier = Modifier.fillMaxWidth().testTag("finance_save_subscription_button")
                                ) {
                                    Text("Save Subscription & Billing Selection")
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showFeeDialog && students.isNotEmpty()) {
        var selectedStudent by remember(editingFee?.id) {
            mutableStateOf(
                students.firstOrNull { it.id == editingFee?.studentId } ?: students.first()
            )
        }
        var title by remember(editingFee?.id) { mutableStateOf(editingFee?.title ?: "Tuition & Development Fee") }
        var term by remember(editingFee?.id) { mutableStateOf(editingFee?.term ?: (school?.currentTerm ?: "Term 1")) }
        var academicYear by remember(editingFee?.id) { mutableStateOf(editingFee?.academicYear ?: (school?.academicYear ?: "2026/2027")) }
        var dueStr by remember(editingFee?.id) { mutableStateOf(editingFee?.amountDueGhs?.toString() ?: "650") }
        var paidStr by remember(editingFee?.id) { mutableStateOf(editingFee?.amountPaidGhs?.toString() ?: "0") }
        var method by remember(editingFee?.id) { mutableStateOf(editingFee?.paymentMethod ?: "Mobile Money") }
        var receipt by remember(editingFee?.id) { mutableStateOf(editingFee?.receiptNumber.orEmpty()) }
        var dueDate by remember(editingFee?.id) { mutableStateOf(editingFee?.dueDate.orEmpty()) }

        AlertDialog(
            onDismissRequest = { showFeeDialog = false },
            title = { Text(if (editingFee != null) "Update Pupil Fee Record" else "Create Pupil Fee Invoice") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (editingFee == null) {
                        Text("Select Pupil", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            students.take(12).forEach { stu ->
                                FilterChip(
                                    selected = selectedStudent.id == stu.id,
                                    onClick = { selectedStudent = stu },
                                    label = { Text(stu.fullName) }
                                )
                            }
                        }
                    } else {
                        Text("Pupil: ${selectedStudent.fullName} (${selectedStudent.className})", style = MaterialTheme.typography.labelLarge)
                    }
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Fee Description *") }, modifier = Modifier.fillMaxWidth().testTag("fee_title_input"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = term, onValueChange = { term = it }, label = { Text("Term") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = academicYear, onValueChange = { academicYear = it }, label = { Text("Year") }, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = dueStr,
                            onValueChange = { dueStr = it },
                            label = { Text("Due (GH₵)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f).testTag("fee_due_input")
                        )
                        OutlinedTextField(
                            value = paidStr,
                            onValueChange = { paidStr = it },
                            label = { Text("Paid (GH₵)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f).testTag("fee_paid_input")
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Mobile Money", "Cash", "Bank Transfer", "Paystack").forEach { m ->
                            FilterChip(selected = method == m, onClick = { method = m }, label = { Text(m) })
                        }
                    }
                    OutlinedTextField(value = receipt, onValueChange = { receipt = it }, label = { Text("Receipt / Txn Ref") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = dueDate, onValueChange = { dueDate = it }, label = { Text("Due Date (e.g., 2026-10-30)") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveFeeRecord(
                            selectedStudent,
                            editingFee,
                            title.trim(),
                            term.trim(),
                            academicYear.trim(),
                            dueStr.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                            paidStr.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                            method,
                            receipt.trim(),
                            dueDate.trim()
                        )
                        showFeeDialog = false
                    },
                    enabled = title.trim().isNotEmpty(),
                    modifier = Modifier.testTag("save_fee_record_button")
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showFeeDialog = false }) { Text("Cancel") } }
        )
    }

    if (deletingFee != null) {
        ConfirmDeleteDialog(
            title = "Delete Fee Record?",
            message = "Delete ${deletingFee!!.title} for ${deletingFee!!.studentName}?",
            onDismiss = { deletingFee = null },
            onConfirmDelete = { onDeleteFeeRecord(deletingFee!!) }
        )
    }
}
