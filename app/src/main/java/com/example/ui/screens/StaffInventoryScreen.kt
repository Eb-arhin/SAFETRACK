package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.example.data.model.InventoryItem
import com.example.data.model.School
import com.example.data.model.SchoolClass
import com.example.data.model.SchoolInvite
import com.example.data.model.SchoolMember
import com.example.data.model.Subject
import com.example.data.model.UserRole
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.EditTeacherMemberDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.TeachersDirectoryCard
import com.example.ui.theme.DelayAmber100
import com.example.ui.theme.DelayAmber700
import com.example.ui.theme.EmergencyRed600
import com.example.ui.theme.SafeBlue700
import com.example.ui.theme.SafeGreen600
import com.example.ui.theme.SafeGreen700

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StaffInventoryScreen(
    role: UserRole,
    school: School?,
    classes: List<SchoolClass>,
    subjects: List<Subject>,
    members: List<SchoolMember>,
    invites: List<SchoolInvite>,
    inventoryItems: List<InventoryItem>,
    isBusy: Boolean,
    onCreateInvite: (email: String, role: UserRole) -> Unit,
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
    onSaveInventoryItem: (
        existingItem: InventoryItem?,
        name: String,
        category: String,
        quantity: Int,
        minimumStock: Int,
        condition: String,
        assignedTo: String,
        notes: String
    ) -> Unit,
    onDeleteInventoryItem: (InventoryItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isAdmin = role == UserRole.SCHOOL_ADMIN || role == UserRole.SUPER_ADMIN
    var selectedTab by remember { mutableIntStateOf(0) }
    var showInviteDialog by remember { mutableStateOf(false) }
    var showTeacherDialog by remember { mutableStateOf(false) }
    var editingMember by remember { mutableStateOf<SchoolMember?>(null) }
    var deletingMember by remember { mutableStateOf<SchoolMember?>(null) }

    var categoryFilter by remember { mutableStateOf("All") }
    var showInventoryDialog by remember { mutableStateOf(false) }
    var editingInventory by remember { mutableStateOf<InventoryItem?>(null) }
    var deletingInventory by remember { mutableStateOf<InventoryItem?>(null) }

    val categories = remember(inventoryItems) {
        (listOf("All", "ICT & Devices", "Furniture", "Textbooks", "Sports & Safety", "Lab & Supplies") +
            inventoryItems.map { it.category }).distinct()
    }
    val filteredInventory = remember(inventoryItems, categoryFilter) {
        if (categoryFilter == "All") inventoryItems
        else inventoryItems.filter { it.category.equals(categoryFilter, ignoreCase = true) }
    }
    val lowStockCount = inventoryItems.count { it.quantity <= it.minimumStock }
    val repairNeededCount = inventoryItems.count {
        it.condition.contains("Repair", ignoreCase = true) || it.condition.contains("Damaged", ignoreCase = true)
    }

    Column(modifier = modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Staff & Invites") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("School Inventory") })
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            if (selectedTab == 0) {
                if (isAdmin) {
                    item {
                        Button(
                            onClick = { showInviteDialog = true },
                            enabled = !isBusy,
                            modifier = Modifier.widthIn(max = 920.dp).fillMaxWidth().testTag("staff_create_invite_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text("Generate Teacher / Parent / Pickup Invite")
                        }
                    }
                    if (invites.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.widthIn(max = 920.dp).fillMaxWidth()) {
                                Text("Active School Invites (${invites.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                        items(invites, key = { it.id }) { inv ->
                            Card(modifier = Modifier.widthIn(max = 920.dp).fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(inv.inviteCode, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${UserRole.fromKey(inv.role).label} • ${inv.targetEmail.ifBlank { "Open invite code" }} • ${inv.status.uppercase()}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SafeGreen700
                                        )
                                    }
                                    IconButton(onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        cm?.setPrimaryClip(ClipData.newPlainText("SafeTrack Invite Code", inv.inviteCode))
                                        Toast.makeText(context, "Copied invite ${inv.inviteCode}", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy invite code")
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Column(modifier = Modifier.widthIn(max = 920.dp)) {
                        TeachersDirectoryCard(
                            members = members,
                            classes = classes,
                            currentUserRole = role,
                            onAddTeacher = {
                                editingMember = null
                                showTeacherDialog = true
                            },
                            onEditMember = { m ->
                                editingMember = m
                                showTeacherDialog = true
                            },
                            onDeleteMember = { m -> deletingMember = m }
                        )
                    }
                }
            } else {
                item {
                    Column(modifier = Modifier.widthIn(max = 920.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Campus Assets & Inventory", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Button(
                                onClick = {
                                    editingInventory = null
                                    showInventoryDialog = true
                                },
                                enabled = !isBusy,
                                modifier = Modifier.testTag("add_inventory_item_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("Add Asset")
                            }
                        }
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            KpiMetricCard(
                                label = "Asset Units",
                                value = inventoryItems.sumOf { it.quantity }.toString(),
                                subtitle = "${inventoryItems.size} item lines",
                                icon = Icons.Filled.Inventory2,
                                accentColor = SafeBlue700,
                                modifier = Modifier.weight(1f)
                            )
                            KpiMetricCard(
                                label = "Low Stock",
                                value = lowStockCount.toString(),
                                icon = Icons.Filled.Warning,
                                accentColor = if (lowStockCount > 0) DelayAmber700 else SafeGreen600,
                                modifier = Modifier.weight(1f)
                            )
                            KpiMetricCard(
                                label = "Repairs",
                                value = repairNeededCount.toString(),
                                icon = Icons.Filled.Build,
                                accentColor = if (repairNeededCount > 0) EmergencyRed600 else SafeGreen600,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            categories.forEach { cat ->
                                FilterChip(
                                    selected = categoryFilter.equals(cat, ignoreCase = true),
                                    onClick = { categoryFilter = cat },
                                    label = { Text(cat) }
                                )
                            }
                        }
                    }
                }

                if (filteredInventory.isEmpty()) {
                    item {
                        Column(modifier = Modifier.widthIn(max = 920.dp)) {
                            EmptyStateCard(
                                "No Inventory Items Yet",
                                "Add classroom projectors, tablets, desks, textbooks, or safety kits to track school stock."
                            )
                        }
                    }
                } else {
                    items(filteredInventory, key = { it.id }) { item ->
                        val isLow = item.quantity <= item.minimumStock
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
                                        Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        if (isLow) {
                                            Surface(shape = RoundedCornerShape(50), color = DelayAmber100) {
                                                Text(
                                                    "Low Stock",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = DelayAmber700,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        "${item.category} • Condition: ${item.condition} • Assigned: ${item.assignedTo.ifBlank { "General Store" }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (item.notes.isNotBlank()) {
                                        Text(item.notes, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = {
                                        onSaveInventoryItem(item, item.name, item.category, (item.quantity - 1).coerceAtLeast(0), item.minimumStock, item.condition, item.assignedTo, item.notes)
                                    }) {
                                        Icon(Icons.Filled.Remove, contentDescription = "Decrease quantity")
                                    }
                                    Text("${item.quantity}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    IconButton(onClick = {
                                        onSaveInventoryItem(item, item.name, item.category, item.quantity + 1, item.minimumStock, item.condition, item.assignedTo, item.notes)
                                    }) {
                                        Icon(Icons.Filled.Add, contentDescription = "Increase quantity")
                                    }
                                    IconButton(onClick = {
                                        editingInventory = item
                                        showInventoryDialog = true
                                    }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Edit ${item.name}", tint = SafeBlue700)
                                    }
                                    if (isAdmin) {
                                        IconButton(onClick = { deletingInventory = item }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete ${item.name}", tint = EmergencyRed600)
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

    if (showInviteDialog) {
        var targetEmail by remember { mutableStateOf("") }
        var inviteRole by remember { mutableStateOf(UserRole.TEACHER) }
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
                            FilterChip(selected = inviteRole == r, onClick = { inviteRole = r }, label = { Text(r.label) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onCreateInvite(targetEmail, inviteRole)
                    showInviteDialog = false
                }) { Text("Generate Invite") }
            },
            dismissButton = { TextButton(onClick = { showInviteDialog = false }) { Text("Cancel") } }
        )
    }

    if (showTeacherDialog) {
        EditTeacherMemberDialog(
            existingMember = editingMember,
            classes = classes,
            subjects = subjects,
            allowRoleChange = isAdmin,
            onDismiss = { showTeacherDialog = false },
            onSave = { name, email, phone, mRole, classIds, subjectIds, subjectNames, status ->
                onSaveTeacherMember(editingMember, name, email, phone, mRole, classIds, subjectIds, subjectNames, status)
            }
        )
    }

    if (deletingMember != null) {
        ConfirmDeleteDialog(
            title = "Delete Staff / Member?",
            message = "Remove ${deletingMember!!.displayName} (${UserRole.fromKey(deletingMember!!.role).label}) from the directory?",
            onDismiss = { deletingMember = null },
            onConfirmDelete = { onDeleteTeacherMember(deletingMember!!) }
        )
    }

    if (showInventoryDialog) {
        var itemName by remember(editingInventory?.id) { mutableStateOf(editingInventory?.name.orEmpty()) }
        var category by remember(editingInventory?.id) { mutableStateOf(editingInventory?.category ?: "ICT & Devices") }
        var quantityStr by remember(editingInventory?.id) { mutableStateOf((editingInventory?.quantity ?: 1).toString()) }
        var minStockStr by remember(editingInventory?.id) { mutableStateOf((editingInventory?.minimumStock ?: 2).toString()) }
        var condition by remember(editingInventory?.id) { mutableStateOf(editingInventory?.condition ?: "Good") }
        var assignedTo by remember(editingInventory?.id) { mutableStateOf(editingInventory?.assignedTo ?: "General Store") }
        var notes by remember(editingInventory?.id) { mutableStateOf(editingInventory?.notes.orEmpty()) }

        AlertDialog(
            onDismissRequest = { showInventoryDialog = false },
            title = { Text(if (editingInventory != null) "Edit Inventory Asset" else "Add Inventory Asset") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = itemName, onValueChange = { itemName = it }, label = { Text("Item Name *") }, modifier = Modifier.fillMaxWidth().testTag("inventory_name_input"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("ICT & Devices", "Furniture", "Textbooks", "Sports & Safety", "Lab & Supplies").forEach { c ->
                            FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = quantityStr,
                            onValueChange = { quantityStr = it },
                            label = { Text("Qty") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = minStockStr,
                            onValueChange = { minStockStr = it },
                            label = { Text("Min Alert") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Good", "Fair", "Needs Repair", "Damaged").forEach { cond ->
                            FilterChip(selected = condition == cond, onClick = { condition = cond }, label = { Text(cond) })
                        }
                    }
                    OutlinedTextField(value = assignedTo, onValueChange = { assignedTo = it }, label = { Text("Assigned To (Room / Staff)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes / Serial No.") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveInventoryItem(
                            editingInventory,
                            itemName.trim(),
                            category,
                            quantityStr.toIntOrNull()?.coerceAtLeast(0) ?: 1,
                            minStockStr.toIntOrNull()?.coerceAtLeast(0) ?: 2,
                            condition,
                            assignedTo.trim(),
                            notes.trim()
                        )
                        showInventoryDialog = false
                    },
                    enabled = itemName.trim().length >= 2,
                    modifier = Modifier.testTag("save_inventory_button")
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showInventoryDialog = false }) { Text("Cancel") } }
        )
    }

    if (deletingInventory != null) {
        ConfirmDeleteDialog(
            title = "Delete Inventory Asset?",
            message = "Permanently remove ${deletingInventory!!.name} from campus inventory?",
            onDismiss = { deletingInventory = null },
            onConfirmDelete = { onDeleteInventoryItem(deletingInventory!!) }
        )
    }
}
