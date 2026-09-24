package com.example.ui.admin

import android.content.Intent
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.ComplianceRepository
import com.example.data.InspectionRepository
import com.example.models.Compliance
import com.example.models.ComplianceStatus
import com.example.models.Inspection
import com.example.models.InspectionField
import com.example.models.InspectionStatus
import com.example.models.InspectionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminReportsViewModel : ViewModel() {
    private val inspectionRepo = InspectionRepository()
    private val complianceRepo = ComplianceRepository()

    private val _inspections = MutableStateFlow<List<Inspection>>(emptyList())
    val inspections: StateFlow<List<Inspection>> = _inspections.asStateFlow()

    private val _types = MutableStateFlow<List<InspectionType>>(emptyList())
    val types: StateFlow<List<InspectionType>> = _types.asStateFlow()

    private val _compliances = MutableStateFlow<List<Compliance>>(emptyList())
    val compliances: StateFlow<List<Compliance>> = _compliances.asStateFlow()

    private val _fieldsMap = MutableStateFlow<Map<String, List<InspectionField>>>(emptyMap())
    val fieldsMap: StateFlow<Map<String, List<InspectionField>>> = _fieldsMap.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            inspectionRepo.getInspections().collect { list ->
                _inspections.value = list
            }
        }
        viewModelScope.launch {
            inspectionRepo.getInspectionTypes().collect { typeList ->
                _types.value = typeList
                typeList.forEach { t ->
                    loadFieldsForType(t.id)
                }
            }
        }
        viewModelScope.launch {
            complianceRepo.getAllCompliances().collect { compList ->
                _compliances.value = compList
            }
        }
    }

    private fun loadFieldsForType(typeId: String) {
        viewModelScope.launch {
            inspectionRepo.getInspectionFields(typeId).collect { fields ->
                val current = _fieldsMap.value.toMutableMap()
                current[typeId] = fields
                _fieldsMap.value = current
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminReportsScreen(
    onBack: () -> Unit,
    viewModel: AdminReportsViewModel = viewModel()
) {
    val inspections by viewModel.inspections.collectAsState()
    val types by viewModel.types.collectAsState()
    val compliances by viewModel.compliances.collectAsState()
    val fieldsMap by viewModel.fieldsMap.collectAsState()

    var selectedStatusFilter by remember { mutableStateOf<InspectionStatus?>(null) }
    var selectedTypeFilter by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    var selectedInspectionForReport by remember { mutableStateOf<Inspection?>(null) }

    val filteredInspections = remember(inspections, selectedStatusFilter, selectedTypeFilter, searchQuery) {
        inspections.filter { insp ->
            (selectedStatusFilter == null || insp.status == selectedStatusFilter) &&
            (selectedTypeFilter == null || insp.inspectionTypeId == selectedTypeFilter) &&
            (searchQuery.isBlank() ||
             insp.inspectionNumber.contains(searchQuery, ignoreCase = true) ||
             insp.assignedOfficerName.contains(searchQuery, ignoreCase = true) ||
             insp.locationName.contains(searchQuery, ignoreCase = true) ||
             insp.inspectionTypeName.contains(searchQuery, ignoreCase = true))
        }
    }

    val totalCount = inspections.size
    val completedCount = inspections.count { it.status == InspectionStatus.COMPLETED }
    val inProgressCount = inspections.count { it.status == InspectionStatus.IN_PROGRESS }
    val assignedCount = inspections.count { it.status == InspectionStatus.ASSIGNED }

    val complianceRate = if (compliances.isNotEmpty()) {
        val resolved = compliances.count { it.status == ComplianceStatus.RESOLVED || it.status == ComplianceStatus.CLOSED }
        (resolved * 100) / compliances.size
    } else 100

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inspection Reports & Analytics") },
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
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Analytics Dashboard Summary
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Overall Executive Summary",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            ReportMetricTile("Total", totalCount.toString(), MaterialTheme.colorScheme.primary)
                            ReportMetricTile("Completed", completedCount.toString(), Color(0xFF2E7D32))
                            ReportMetricTile("In Progress", inProgressCount.toString(), MaterialTheme.colorScheme.tertiary)
                            ReportMetricTile("Scheduled", assignedCount.toString(), MaterialTheme.colorScheme.error)
                            ReportMetricTile("Compliance", "$complianceRate%", Color(0xFF1565C0))
                        }
                    }
                }
            }

            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search officer, location, or report #") },
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

            // Filter Chips by Status
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Filter by Status:", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = selectedStatusFilter == null,
                                onClick = { selectedStatusFilter = null },
                                label = { Text("All ($totalCount)") }
                            )
                        }
                        items(listOf(InspectionStatus.COMPLETED, InspectionStatus.IN_PROGRESS, InspectionStatus.ASSIGNED)) { status ->
                            val count = inspections.count { it.status == status }
                            FilterChip(
                                selected = selectedStatusFilter == status,
                                onClick = { selectedStatusFilter = if (selectedStatusFilter == status) null else status },
                                label = { Text("${status.name.replace("_", " ")} ($count)") }
                            )
                        }
                    }
                }
            }

            // Inspections List
            if (filteredInspections.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No inspection records match the selected filter.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(filteredInspections, key = { it.id }) { inspection ->
                    ReportInspectionCard(
                        inspection = inspection,
                        onClick = { selectedInspectionForReport = inspection }
                    )
                }
            }
        }

        // Detailed Report Viewer Modal
        selectedInspectionForReport?.let { insp ->
            val fields = fieldsMap[insp.inspectionTypeId] ?: emptyList()
            InspectionReportDialog(
                inspection = insp,
                fields = fields,
                onDismiss = { selectedInspectionForReport = null }
            )
        }
    }
}

@Composable
fun ReportMetricTile(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun ReportInspectionCard(inspection: Inspection, onClick: () -> Unit) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    val scheduledDateStr = if (inspection.scheduledDate > 0) {
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(inspection.scheduledDate))
    } else "Unscheduled"

    val statusColor = when (inspection.status) {
        InspectionStatus.COMPLETED -> Color(0xFF2E7D32)
        InspectionStatus.IN_PROGRESS -> MaterialTheme.colorScheme.tertiary
        InspectionStatus.ASSIGNED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon or Selfie Thumbnail
            if (inspection.selfieURL.isNotBlank()) {
                AsyncImage(
                    model = inspection.selfieURL,
                    contentDescription = "Officer Selfie",
                    modifier = Modifier
                        .size(54.dp)
                        .clip(MaterialTheme.shapes.medium),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Assessment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = inspection.inspectionNumber.ifEmpty { "INSP-RECORD" },
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = inspection.inspectionTypeName.ifEmpty { "Inspection" },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Inspecting Officer: ${inspection.assignedOfficerName.ifEmpty { "Unassigned" }}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Location: ${inspection.locationName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (inspection.executionDate != null && inspection.executionDate!! > 0) {
                    Text(
                        text = "Executed: ${dateFormat.format(Date(inspection.executionDate!!))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (inspection.completedAt != null) {
                    Text(
                        text = "Completed: ${dateFormat.format(Date(inspection.completedAt))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF2E7D32)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = inspection.status.name.replace("_", " "),
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = "View Report",
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun InspectionReportDialog(
    inspection: Inspection,
    fields: List<InspectionField>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())

    // Parse responses JSON
    val responseMap = remember(inspection.responses) {
        val map = mutableMapOf<String, String>()
        try {
            val json = JSONObject(inspection.responses)
            json.keys().forEach { key ->
                map[key] = json.optString(key, "")
            }
        } catch (_: Exception) {}
        map
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .heightIn(max = 740.dp)
                    .padding(16.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "INSPECTION DOSSIER",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                inspection.inspectionNumber,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    HorizontalDivider()

                    // Key Metadata Grid
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReportRow("Type", inspection.inspectionTypeName)
                            ReportRow("Location", inspection.locationName)
                            ReportRow("Inspecting Officer", inspection.assignedOfficerName)
                            ReportRow("Status", inspection.status.name)
                            if (inspection.scheduledDate > 0) {
                                ReportRow("Assigned Date", dateFormat.format(Date(inspection.scheduledDate)))
                            }
                            val execDate = inspection.executionDate ?: inspection.startedAt
                            if (execDate != null && execDate > 0) {
                                ReportRow("Execution Date", dateFormat.format(Date(execDate)))
                            }
                            if (inspection.executionDate != null && inspection.scheduledDate > 0) {
                                val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = inspection.executionDate!! }
                                val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = inspection.scheduledDate }
                                val isDiff = cal1.get(java.util.Calendar.YEAR) != cal2.get(java.util.Calendar.YEAR) ||
                                             cal1.get(java.util.Calendar.DAY_OF_YEAR) != cal2.get(java.util.Calendar.DAY_OF_YEAR)
                                if (isDiff) {
                                    ReportRow("Execution Status", "Executed on a different date than assigned")
                                }
                            }
                            if (inspection.remarks.isNotBlank()) {
                                ReportRow("Execution Notes", inspection.remarks)
                            }
                            if (inspection.startedAt != null) {
                                ReportRow("Started At", dateFormat.format(Date(inspection.startedAt)))
                            }
                            if (inspection.completedAt != null) {
                                ReportRow("Completed At", dateFormat.format(Date(inspection.completedAt)))
                            }
                        }
                    }

                    // GPS & Selfie Verification Evidence
                    Text("Geo-Verification Evidence", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (inspection.selfieURL.isNotBlank()) {
                                AsyncImage(
                                    model = inspection.selfieURL,
                                    contentDescription = "Officer Selfie",
                                    modifier = Modifier
                                        .size(90.dp)
                                        .clip(MaterialTheme.shapes.medium),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                if (inspection.startLatitude != null && inspection.startLongitude != null) {
                                    Text(
                                        "GPS: ${String.format("%.6f", inspection.startLatitude)}, ${String.format("%.6f", inspection.startLongitude)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Accuracy: ${inspection.startAccuracy ?: 0f} meters",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text("No GPS Captured", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    if (inspection.selfieURL.isNotBlank()) "Verified Officer Selfie" else "No Selfie Captured",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (inspection.selfieURL.isNotBlank()) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    // Dynamic Form Field Checklist Responses
                    Text("Inspection Checklist Responses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (responseMap.isEmpty() && fields.isEmpty()) {
                        Text("No inspection responses recorded.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (fields.isNotEmpty()) {
                                fields.forEach { field ->
                                    val answer = responseMap[field.fieldName] ?: "Not Recorded"
                                    AnswerCard(field.label, answer)
                                }
                            } else {
                                responseMap.forEach { (k, v) ->
                                    if (k != "__custom_fields__") {
                                        AnswerCard(k.replace("_", " ").capitalize(Locale.getDefault()), v)
                                    }
                                }
                            }
                        }
                    }

                    // Officer Added Custom Fields (if any)
                    val customFieldsList = remember(inspection.responses) {
                        val list = mutableListOf<Pair<String, String>>()
                        try {
                            val json = JSONObject(inspection.responses)
                            if (json.has("__custom_fields__")) {
                                val arr = org.json.JSONArray(json.getString("__custom_fields__"))
                                for (i in 0 until arr.length()) {
                                    val obj = arr.getJSONObject(i)
                                    val lbl = obj.optString("label", "Additional Field")
                                    val fn = obj.optString("fieldName", "")
                                    val ans = json.optString(fn, "")
                                    if (lbl.isNotBlank() && ans.isNotBlank()) {
                                        list.add(lbl to ans)
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                        list
                    }

                    if (customFieldsList.isNotEmpty()) {
                        Text("Additional Observations (Added by Officer)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            customFieldsList.forEach { (lbl, ans) ->
                                AnswerCard(lbl, ans)
                            }
                        }
                    }

                    // Remarks
                    if (inspection.remarks.isNotBlank()) {
                        Text("Official Remarks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                inspection.remarks,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Action buttons: Share/Export and Close
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                val reportText = buildString {
                                    appendLine("=== RAILWAY INSPECTION REPORT ===")
                                    appendLine("Inspection No: ${inspection.inspectionNumber}")
                                    appendLine("Type: ${inspection.inspectionTypeName}")
                                    appendLine("Location: ${inspection.locationName}")
                                    appendLine("Officer: ${inspection.assignedOfficerName}")
                                    appendLine("Status: ${inspection.status.name}")
                                    if (inspection.completedAt != null) {
                                        appendLine("Completed: ${dateFormat.format(Date(inspection.completedAt))}")
                                    }
                                    if (inspection.startLatitude != null) {
                                        appendLine("GPS: ${inspection.startLatitude}, ${inspection.startLongitude} (Acc: ${inspection.startAccuracy}m)")
                                    }
                                    appendLine("\n--- Checklist Responses ---")
                                    if (fields.isNotEmpty()) {
                                        fields.forEach { f ->
                                            appendLine("${f.label}: ${responseMap[f.fieldName] ?: "N/A"}")
                                        }
                                    } else {
                                        responseMap.forEach { (k, v) ->
                                            if (k != "__custom_fields__") {
                                                appendLine("$k: $v")
                                            }
                                        }
                                    }
                                    if (customFieldsList.isNotEmpty()) {
                                        appendLine("\n--- Additional Observations (Added by Officer) ---")
                                        customFieldsList.forEach { (lbl, ans) ->
                                            appendLine("$lbl: $ans")
                                        }
                                    }
                                    if (inspection.remarks.isNotBlank()) {
                                        appendLine("\nRemarks: ${inspection.remarks}")
                                    }
                                }
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, reportText)
                                    putExtra(Intent.EXTRA_SUBJECT, "Inspection Report: ${inspection.inspectionNumber}")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share Inspection Report"))
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Share Report")
                        }

                        TextButton(onClick = onDismiss) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReportRow(title: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AnswerCard(label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                if (!value.startsWith("content://") && !value.startsWith("file://")) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = value.ifBlank { "N/A" },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (value.startsWith("content://") || value.startsWith("file://")) {
                Spacer(modifier = Modifier.height(8.dp))
                AsyncImage(
                    model = android.net.Uri.parse(value),
                    contentDescription = label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(MaterialTheme.shapes.small),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}
