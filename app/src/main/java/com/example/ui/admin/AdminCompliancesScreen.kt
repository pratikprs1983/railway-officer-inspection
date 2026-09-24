package com.example.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.ComplianceRepository
import com.example.data.InspectionRepository
import com.example.models.Compliance
import com.example.models.ComplianceSeverity
import com.example.models.ComplianceStatus
import com.example.models.Inspection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminCompliancesViewModel : ViewModel() {
    private val repo = ComplianceRepository()
    private val inspectionRepo = InspectionRepository()
    private val userRepo = com.example.data.UserRepository()

    private val _compliances = MutableStateFlow<List<Compliance>>(emptyList())
    val compliances: StateFlow<List<Compliance>> = _compliances.asStateFlow()

    private val _inspections = MutableStateFlow<List<Inspection>>(emptyList())
    val inspections: StateFlow<List<Inspection>> = _inspections.asStateFlow()

    private val _officers = MutableStateFlow<List<com.example.models.User>>(emptyList())
    val officers: StateFlow<List<com.example.models.User>> = _officers.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            repo.getAllCompliances().collect { list ->
                _compliances.value = list
            }
        }
        viewModelScope.launch {
            inspectionRepo.getInspections().collect { list ->
                _inspections.value = list
            }
        }
        viewModelScope.launch {
            userRepo.getUsers().collect { list ->
                _officers.value = list.filter { it.role != com.example.models.Role.SUPER_ADMIN }
            }
        }
    }

    fun saveCompliance(compliance: Compliance) {
        repo.saveCompliance(compliance) {}
    }

    fun deleteCompliance(compliance: Compliance) {
        repo.deleteCompliance(compliance) {}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminCompliancesScreen(
    onBack: () -> Unit,
    viewModel: AdminCompliancesViewModel = viewModel()
) {
    val compliances by viewModel.compliances.collectAsState()
    val inspections by viewModel.inspections.collectAsState()
    val officers by viewModel.officers.collectAsState()

    var selectedStatusFilter by remember { mutableStateOf<ComplianceStatus?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    var showDialog by remember { mutableStateOf(false) }
    var editingCompliance by remember { mutableStateOf<Compliance?>(null) }
    var complianceToDelete by remember { mutableStateOf<Compliance?>(null) }

    val filteredList = remember(compliances, selectedStatusFilter, searchQuery) {
        compliances.filter { comp ->
            (selectedStatusFilter == null || comp.status == selectedStatusFilter) &&
            (searchQuery.isBlank() ||
             comp.title.contains(searchQuery, ignoreCase = true) ||
             comp.inspectionNumber.contains(searchQuery, ignoreCase = true) ||
             comp.locationName.contains(searchQuery, ignoreCase = true) ||
             comp.assignedOfficerName.contains(searchQuery, ignoreCase = true))
        }
    }

    val openCount = compliances.count { it.status == ComplianceStatus.OPEN }
    val underActionCount = compliances.count { it.status == ComplianceStatus.UNDER_ACTION }
    val resolvedCount = compliances.count { it.status == ComplianceStatus.RESOLVED || it.status == ComplianceStatus.CLOSED }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compliances & Deficiencies") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingCompliance = null
                    showDialog = true
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Compliance")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Point-Level Compliance Guidance Banner
            item {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Point-Level Compliance: Each inspection point can be assigned to different officers for compliance. The inspecting officer retains ownership of the parent inspection.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Metrics / KPI row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ComplianceMetricCard("Total", compliances.size.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    ComplianceMetricCard("Open", openCount.toString(), MaterialTheme.colorScheme.error, Modifier.weight(1f))
                    ComplianceMetricCard("In Action", underActionCount.toString(), MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                    ComplianceMetricCard("Resolved", resolvedCount.toString(), Color(0xFF2E7D32), Modifier.weight(1f))
                }
            }

            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by title, location, or inspection #") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Status Filter Chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedStatusFilter == null,
                            onClick = { selectedStatusFilter = null },
                            label = { Text("All (${compliances.size})") }
                        )
                    }
                    items(ComplianceStatus.values()) { status ->
                        val count = compliances.count { it.status == status }
                        FilterChip(
                            selected = selectedStatusFilter == status,
                            onClick = { selectedStatusFilter = if (selectedStatusFilter == status) null else status },
                            label = { Text("${status.name.replace("_", " ")} ($count)") }
                        )
                    }
                }
            }

            // Compliances List
            if (filteredList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                if (compliances.isEmpty()) "No compliances logged yet." else "No matching compliances found.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredList, key = { it.id }) { comp ->
                    ComplianceCard(
                        compliance = comp,
                        onEdit = {
                            editingCompliance = comp
                            showDialog = true
                        },
                        onDelete = {
                            complianceToDelete = comp
                        },
                        onStatusChange = { newStatus ->
                            viewModel.saveCompliance(
                                comp.copy(
                                    status = newStatus,
                                    closedAt = if (newStatus == ComplianceStatus.CLOSED || newStatus == ComplianceStatus.RESOLVED) System.currentTimeMillis() else null,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        }
                    )
                }
            }
        }

        if (showDialog) {
            ComplianceDialog(
                compliance = editingCompliance,
                inspections = inspections,
                officers = officers,
                onDismiss = { showDialog = false },
                onSave = { toSave ->
                    viewModel.saveCompliance(toSave)
                    showDialog = false
                }
            )
        }

        complianceToDelete?.let { comp ->
            AlertDialog(
                onDismissRequest = { complianceToDelete = null },
                title = { Text("Delete Compliance Record") },
                text = { Text("Are you sure you want to delete '${comp.title}'?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteCompliance(comp)
                            complianceToDelete = null
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { complianceToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun ComplianceMetricCard(title: String, count: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(count, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ComplianceCard(
    compliance: Compliance,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onStatusChange: (ComplianceStatus) -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val targetDateStr = dateFormat.format(Date(compliance.targetDate))

    val severityColor = when (compliance.severity) {
        ComplianceSeverity.CRITICAL -> MaterialTheme.colorScheme.error
        ComplianceSeverity.MAJOR -> Color(0xFFE65100)
        ComplianceSeverity.MINOR -> MaterialTheme.colorScheme.primary
    }

    val statusColor = when (compliance.status) {
        ComplianceStatus.OPEN -> MaterialTheme.colorScheme.error
        ComplianceStatus.UNDER_ACTION -> MaterialTheme.colorScheme.tertiary
        ComplianceStatus.RESOLVED, ComplianceStatus.CLOSED -> Color(0xFF2E7D32)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = severityColor.copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        text = compliance.severity.name,
                        color = severityColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        text = compliance.status.name.replace("_", " "),
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            if (compliance.inspectionPointTitle.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        text = "Point: ${compliance.inspectionPointTitle}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(compliance.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            if (compliance.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    compliance.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1.1f)) {
                    if (compliance.inspectionNumber.isNotBlank()) {
                        Text("Insp: ${compliance.inspectionNumber}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                    if (compliance.locationName.isNotBlank()) {
                        Text("Loc: ${compliance.locationName}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (compliance.inspectingOfficerName.isNotBlank()) {
                        Text("Inspector: ${compliance.inspectingOfficerName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                    Text("Target: $targetDateStr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Text("Compliance Officer:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = compliance.assignedOfficerName.ifEmpty { "Unassigned" },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (compliance.actionTaken.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("Action Taken / Remarks:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text(compliance.actionTaken, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Quick status advance
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (compliance.status == ComplianceStatus.OPEN) {
                        OutlinedButton(
                            onClick = { onStatusChange(ComplianceStatus.UNDER_ACTION) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Take Action", style = MaterialTheme.typography.labelSmall)
                        }
                    } else if (compliance.status == ComplianceStatus.UNDER_ACTION) {
                        Button(
                            onClick = { onStatusChange(ComplianceStatus.RESOLVED) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Mark Resolved", style = MaterialTheme.typography.labelSmall)
                        }
                    } else if (compliance.status == ComplianceStatus.RESOLVED) {
                        OutlinedButton(
                            onClick = { onStatusChange(ComplianceStatus.CLOSED) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Close", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComplianceDialog(
    compliance: Compliance?,
    inspections: List<Inspection>,
    officers: List<com.example.models.User>,
    onDismiss: () -> Unit,
    onSave: (Compliance) -> Unit
) {
    var title by remember { mutableStateOf(compliance?.title ?: "") }
    var description by remember { mutableStateOf(compliance?.description ?: "") }
    var actionTaken by remember { mutableStateOf(compliance?.actionTaken ?: "") }
    var selectedSeverity by remember { mutableStateOf(compliance?.severity ?: ComplianceSeverity.MAJOR) }
    var selectedStatus by remember { mutableStateOf(compliance?.status ?: ComplianceStatus.OPEN) }
    var selectedInspection by remember { mutableStateOf<Inspection?>(inspections.find { it.id == compliance?.inspectionId }) }
    var inspectionPointTitle by remember { mutableStateOf(compliance?.inspectionPointTitle ?: "") }
    var selectedPointId by remember { mutableStateOf(compliance?.inspectionPointId ?: "") }
    var selectedOfficer by remember {
        mutableStateOf<com.example.models.User?>(
            officers.find { it.uid == compliance?.assignedOfficerId }
                ?: officers.find { it.name.equals(compliance?.assignedOfficerName, ignoreCase = true) }
        )
    }

    val availablePoints = remember(selectedInspection) {
        val pts = mutableListOf<Pair<String, String>>()
        selectedInspection?.let { insp ->
            try {
                if (insp.responses.isNotBlank()) {
                    val json = org.json.JSONObject(insp.responses)
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        if (k != "__custom_fields__" && k.isNotBlank()) {
                            val title = k.replace("_", " ").split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                            pts.add(Pair(k, title))
                        }
                    }
                    if (json.has("__custom_fields__")) {
                        val arr = org.json.JSONArray(json.getString("__custom_fields__"))
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val lbl = obj.optString("label")
                            val id = obj.optString("id").ifBlank { obj.optString("fieldName") }
                            if (lbl.isNotBlank()) pts.add(Pair(id, lbl))
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        pts
    }

    var inspectionExpanded by remember { mutableStateOf(false) }
    var officerExpanded by remember { mutableStateOf(false) }
    var severityExpanded by remember { mutableStateOf(false) }
    var statusExpanded by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .heightIn(max = 700.dp)
                    .padding(16.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = if (compliance == null) "Log New Compliance / Deficiency" else "Edit Compliance",
                        style = MaterialTheme.typography.titleLarge
                    )

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Deficiency / Observation Title *") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Detailed Observation") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Linked Inspection Selector (Optional)
                    ExposedDropdownMenuBox(
                        expanded = inspectionExpanded,
                        onExpandedChange = { inspectionExpanded = !inspectionExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedInspection?.let { "${it.inspectionNumber} (${it.locationName})" } ?: "Select Linked Inspection (Optional)",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Linked Inspection") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = inspectionExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = inspectionExpanded, onDismissRequest = { inspectionExpanded = false }) {
                            inspections.forEach { insp ->
                                DropdownMenuItem(
                                    text = { Text("${insp.inspectionNumber} - ${insp.inspectionTypeName} (${insp.locationName})") },
                                    onClick = {
                                        selectedInspection = insp
                                        inspectionExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Specific Inspection Point / Checklist Item
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = inspectionPointTitle,
                            onValueChange = { inspectionPointTitle = it },
                            label = { Text("Inspection Point / Defect Item") },
                            placeholder = { Text(if (availablePoints.isNotEmpty()) "Select below or enter point name" else "e.g. Waiting Hall Cleanliness, Signal Lamp") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (availablePoints.isNotEmpty()) {
                            Text("Points from Linked Inspection:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(availablePoints) { (ptId, ptTitle) ->
                                    FilterChip(
                                        selected = inspectionPointTitle.equals(ptTitle, ignoreCase = true),
                                        onClick = {
                                            inspectionPointTitle = ptTitle
                                            selectedPointId = ptId
                                            if (title.isBlank()) title = ptTitle
                                        },
                                        label = { Text(ptTitle, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }
                    }

                    if (selectedInspection != null && selectedInspection!!.assignedOfficerName.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Inspecting Officer: ${selectedInspection!!.assignedOfficerName} • You can assign this point to any officer below.",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Assigned Officer or Admin Selector (Any Officer or Admin except Super Admin)
                    ExposedDropdownMenuBox(
                        expanded = officerExpanded,
                        onExpandedChange = { officerExpanded = !officerExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedOfficer?.let { "${it.name} (${if (it.role == com.example.models.Role.ADMIN) "Admin" else "Officer"})" }
                                ?: (compliance?.assignedOfficerName.takeIf { !it.isNullOrBlank() } ?: "Select Responsible Compliance Officer"),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Responsible Compliance Officer *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = officerExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = officerExpanded, onDismissRequest = { officerExpanded = false }) {
                            officers.forEach { officer ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(officer.name, fontWeight = FontWeight.SemiBold)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    color = if (officer.role == com.example.models.Role.ADMIN) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                                                    shape = MaterialTheme.shapes.extraSmall
                                                ) {
                                                    Text(
                                                        text = if (officer.role == com.example.models.Role.ADMIN) "ADMIN" else "OFFICER",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                            if (officer.designation.isNotBlank() || officer.departmentName.isNotBlank()) {
                                                Text(
                                                    text = "${officer.designation} • ${officer.departmentName}".trim(' ', '•'),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        selectedOfficer = officer
                                        officerExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Severity Selector
                    ExposedDropdownMenuBox(
                        expanded = severityExpanded,
                        onExpandedChange = { severityExpanded = !severityExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedSeverity.name,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Severity") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = severityExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = severityExpanded, onDismissRequest = { severityExpanded = false }) {
                            ComplianceSeverity.values().forEach { sev ->
                                DropdownMenuItem(
                                    text = { Text(sev.name) },
                                    onClick = {
                                        selectedSeverity = sev
                                        severityExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Status Selector
                    ExposedDropdownMenuBox(
                        expanded = statusExpanded,
                        onExpandedChange = { statusExpanded = !statusExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedStatus.name.replace("_", " "),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Status") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = statusExpanded, onDismissRequest = { statusExpanded = false }) {
                            ComplianceStatus.values().forEach { st ->
                                DropdownMenuItem(
                                    text = { Text(st.name.replace("_", " ")) },
                                    onClick = {
                                        selectedStatus = st
                                        statusExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = actionTaken,
                        onValueChange = { actionTaken = it },
                        label = { Text("Corrective Action Taken / Notes") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (title.isNotBlank()) {
                                    val toSave = (compliance ?: Compliance()).copy(
                                        title = title,
                                        description = description,
                                        inspectionPointId = selectedPointId.ifBlank { compliance?.inspectionPointId ?: "" },
                                        inspectionPointTitle = inspectionPointTitle.trim().ifEmpty { title.trim() },
                                        inspectionId = selectedInspection?.id ?: compliance?.inspectionId ?: "",
                                        inspectionNumber = selectedInspection?.inspectionNumber ?: compliance?.inspectionNumber ?: "",
                                        locationName = selectedInspection?.locationName ?: compliance?.locationName ?: "",
                                        inspectingOfficerId = selectedInspection?.assignedOfficerId ?: compliance?.inspectingOfficerId ?: "",
                                        inspectingOfficerName = selectedInspection?.assignedOfficerName ?: compliance?.inspectingOfficerName ?: "",
                                        assignedOfficerId = selectedOfficer?.uid ?: compliance?.assignedOfficerId ?: "",
                                        assignedOfficerName = selectedOfficer?.name ?: compliance?.assignedOfficerName ?: "",
                                        severity = selectedSeverity,
                                        status = selectedStatus,
                                        actionTaken = actionTaken,
                                        closedAt = if (selectedStatus == ComplianceStatus.CLOSED || selectedStatus == ComplianceStatus.RESOLVED) System.currentTimeMillis() else null,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                    onSave(toSave)
                                }
                            }
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}
