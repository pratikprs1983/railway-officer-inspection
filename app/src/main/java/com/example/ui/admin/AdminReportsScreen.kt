package com.example.ui.admin

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.models.ComplianceSeverity
import com.example.models.ComplianceStatus
import com.example.models.Inspection
import com.example.models.InspectionField
import com.example.models.InspectionStatus
import com.example.models.InspectionType
import com.example.ui.common.InspectionDossierPreviewDialog
import com.example.ui.common.InspectionRegisterExcelPreviewDialog
import com.example.util.LocationHelper
import com.example.util.ClickableGpsLocationCard
import com.example.util.ClickableGpsBadge
import com.example.util.InspectionExcelExporter
import com.example.util.InspectionWordExporter
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

    val context = LocalContext.current
    var selectedStatusFilter by remember { mutableStateOf<InspectionStatus?>(null) }
    var selectedTypeFilter by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    var isSearchExpanded by remember { mutableStateOf(false) }
    var selectedInspectionForReport by remember { mutableStateOf<Inspection?>(null) }
    var allCompliancesExpanded by remember { mutableStateOf(false) }
    var showWordDossierPreview by remember { mutableStateOf(false) }
    var showExcelRegisterPreview by remember { mutableStateOf(false) }
    var previewInspectionsList by remember { mutableStateOf<List<Inspection>>(emptyList()) }
    var isExecutiveSummaryExpanded by remember { mutableStateOf(false) }

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
                actions = {
                    IconButton(
                        onClick = {
                            val list = if (filteredInspections.isNotEmpty() && (selectedStatusFilter != null || selectedTypeFilter != null || searchQuery.isNotBlank())) filteredInspections else inspections
                            val file = InspectionWordExporter.exportFilteredInspectionsToWord(
                                context = context,
                                inspections = list,
                                compliances = compliances,
                                ascendingDate = false
                            )
                            if (file != null) {
                                InspectionWordExporter.openOrShareWordFile(context, file, "Overall Railway Inspections Dossier")
                            }
                        }
                    ) {
                        Icon(Icons.Default.Description, contentDescription = "Download Overall Word (.docx)")
                    }
                    IconButton(
                        onClick = {
                            val list = if (filteredInspections.isNotEmpty() && (selectedStatusFilter != null || selectedTypeFilter != null || searchQuery.isNotBlank())) filteredInspections else inspections
                            val file = InspectionExcelExporter.exportInspectionsToExcel(
                                context = context,
                                inspections = list,
                                compliances = compliances,
                                ascendingDate = false
                            )
                            if (file != null) {
                                InspectionExcelExporter.openOrShareExcelFile(context, file)
                            }
                        }
                    ) {
                        Icon(Icons.Default.TableChart, contentDescription = "Download Overall Excel (.xlsx)")
                    }
                    IconButton(onClick = { isSearchExpanded = !isSearchExpanded }) {
                        Icon(
                            if (isSearchExpanded || searchQuery.isNotEmpty()) Icons.Default.SearchOff else Icons.Default.Search,
                            contentDescription = "Toggle Search"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Compact & Collapsible Analytics Dashboard Summary
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        // Dynamic Header Bar (Always visible)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isExecutiveSummaryExpanded = !isExecutiveSummaryExpanded },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Assessment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Overall Executive Summary",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "$totalCount Inspections • $complianceRate% Compliance",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        val list = if (filteredInspections.isNotEmpty() && (selectedStatusFilter != null || selectedTypeFilter != null || searchQuery.isNotBlank())) filteredInspections else inspections
                                        val file = InspectionWordExporter.exportFilteredInspectionsToWord(
                                            context = context,
                                            inspections = list,
                                            compliances = compliances,
                                            ascendingDate = false
                                        )
                                        if (file != null) {
                                            InspectionWordExporter.openOrShareWordFile(context, file, "Overall Railway Inspections Dossier")
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Description,
                                        contentDescription = "Quick Download Word",
                                        tint = Color(0xFFEA580C),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        val list = if (filteredInspections.isNotEmpty() && (selectedStatusFilter != null || selectedTypeFilter != null || searchQuery.isNotBlank())) filteredInspections else inspections
                                        val file = InspectionExcelExporter.exportInspectionsToExcel(
                                            context = context,
                                            inspections = list,
                                            compliances = compliances,
                                            ascendingDate = false
                                        )
                                        if (file != null) {
                                            InspectionExcelExporter.openOrShareExcelFile(context, file)
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.TableChart,
                                        contentDescription = "Quick Download Excel",
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { isExecutiveSummaryExpanded = !isExecutiveSummaryExpanded },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        if (isExecutiveSummaryExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (isExecutiveSummaryExpanded) "Collapse Summary" else "Expand Summary",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Detailed breakdown and report options (collapsible)
                        AnimatedVisibility(
                            visible = isExecutiveSummaryExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                // 5 Metrics evenly distributed with equal weights
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    ReportMetricTile("Total", totalCount.toString(), MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                                    ReportMetricTile("Completed", completedCount.toString(), Color(0xFF2E7D32), modifier = Modifier.weight(1f))
                                    ReportMetricTile("In Progress", inProgressCount.toString(), MaterialTheme.colorScheme.tertiary, modifier = Modifier.weight(1f))
                                    ReportMetricTile("Scheduled", assignedCount.toString(), MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                                    ReportMetricTile("Compliance", "$complianceRate%", Color(0xFFEA580C), modifier = Modifier.weight(1f))
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
                                Spacer(modifier = Modifier.height(8.dp))

                                // Section Header for Reports
                                Text(
                                    "Overall Reports (${if (filteredInspections.size < inspections.size) "${filteredInspections.size}/${inspections.size}" else "${inspections.size} Records"})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                // Previews Row: Both options get equal 50% width dynamic space
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            previewInspectionsList = if (filteredInspections.isNotEmpty() && (selectedStatusFilter != null || selectedTypeFilter != null || searchQuery.isNotBlank())) filteredInspections else inspections
                                            showWordDossierPreview = true
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp)
                                            .testTag("admin_preview_word_dossier_btn"),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = Color(0xFFEA580C),
                                            containerColor = Color(0xFFFFF7ED)
                                        ),
                                        border = BorderStroke(1.dp, Color(0xFFFFEDD5)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "Preview Dossier",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            previewInspectionsList = if (filteredInspections.isNotEmpty() && (selectedStatusFilter != null || selectedTypeFilter != null || searchQuery.isNotBlank())) filteredInspections else inspections
                                            showExcelRegisterPreview = true
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp)
                                            .testTag("admin_preview_excel_register_btn"),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = Color(0xFF2E7D32),
                                            containerColor = Color(0xFF2E7D32).copy(alpha = 0.08f)
                                        ),
                                        border = BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.4f)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "Preview Register",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Downloads Row: Both buttons get equal 50% width dynamic space
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            val list = if (filteredInspections.isNotEmpty() && (selectedStatusFilter != null || selectedTypeFilter != null || searchQuery.isNotBlank())) filteredInspections else inspections
                                            val file = InspectionWordExporter.exportFilteredInspectionsToWord(
                                                context = context,
                                                inspections = list,
                                                compliances = compliances,
                                                ascendingDate = false
                                            )
                                            if (file != null) {
                                                InspectionWordExporter.openOrShareWordFile(context, file, "Overall Railway Inspections Dossier")
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp)
                                            .testTag("admin_download_word_dossier_btn"),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "Word (.docx)",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            val list = if (filteredInspections.isNotEmpty() && (selectedStatusFilter != null || selectedTypeFilter != null || searchQuery.isNotBlank())) filteredInspections else inspections
                                            val file = InspectionExcelExporter.exportInspectionsToExcel(
                                                context = context,
                                                inspections = list,
                                                compliances = compliances,
                                                ascendingDate = false
                                            )
                                            if (file != null) {
                                                InspectionExcelExporter.openOrShareExcelFile(context, file)
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp)
                                            .testTag("admin_download_excel_register_btn"),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "Excel (.xlsx)",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Expandable Search Bar
            item {
                AnimatedVisibility(
                    visible = isSearchExpanded || searchQuery.isNotEmpty(),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
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

            // Dynamic Compliance Summary and Collapse/Expand All toggle
            val totalCompliancesCount = compliances.size
            if (totalCompliancesCount > 0 && filteredInspections.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${filteredInspections.size} Inspections ($totalCompliancesCount Compliances)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        FilledTonalButton(
                            onClick = { allCompliancesExpanded = !allCompliancesExpanded },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                if (allCompliancesExpanded) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (allCompliancesExpanded) "Collapse All" else "Expand All",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
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
                    val related = compliances.filter {
                        it.inspectionId == inspection.id || (it.inspectionNumber.isNotBlank() && it.inspectionNumber == inspection.inspectionNumber)
                    }
                    ReportInspectionCard(
                        inspection = inspection,
                        compliances = related,
                        forceExpanded = allCompliancesExpanded,
                        onClick = { selectedInspectionForReport = inspection }
                    )
                }
            }
        }

        // Detailed Report Viewer Modal
        selectedInspectionForReport?.let { insp ->
            val fields = fieldsMap[insp.inspectionTypeId] ?: emptyList()
            val related = compliances.filter {
                it.inspectionId == insp.id || (it.inspectionNumber.isNotBlank() && it.inspectionNumber == insp.inspectionNumber)
            }
            InspectionReportDialog(
                inspection = insp,
                fields = fields,
                compliances = related,
                onDismiss = { selectedInspectionForReport = null }
            )
        }

        if (showWordDossierPreview) {
            val list = if (previewInspectionsList.isNotEmpty()) previewInspectionsList else if (filteredInspections.isNotEmpty() && (selectedStatusFilter != null || selectedTypeFilter != null || searchQuery.isNotBlank())) filteredInspections else inspections
            InspectionDossierPreviewDialog(
                inspections = list,
                compliances = compliances,
                onDismiss = { showWordDossierPreview = false },
                onDownloadWord = {
                    val file = InspectionWordExporter.exportFilteredInspectionsToWord(
                        context = context,
                        inspections = list,
                        compliances = compliances,
                        ascendingDate = false
                    )
                    if (file != null) {
                        InspectionWordExporter.openOrShareWordFile(
                            context = context,
                            file = file,
                            title = "Overall Railway Inspections Dossier"
                        )
                    }
                },
                onDownloadExcel = {
                    val file = InspectionExcelExporter.exportInspectionsToExcel(
                        context = context,
                        inspections = list,
                        compliances = compliances,
                        ascendingDate = false
                    )
                    if (file != null) {
                        InspectionExcelExporter.openOrShareExcelFile(context, file)
                    }
                }
            )
        }

        if (showExcelRegisterPreview) {
            val list = if (previewInspectionsList.isNotEmpty()) previewInspectionsList else if (filteredInspections.isNotEmpty() && (selectedStatusFilter != null || selectedTypeFilter != null || searchQuery.isNotBlank())) filteredInspections else inspections
            InspectionRegisterExcelPreviewDialog(
                inspections = list,
                compliances = compliances,
                onDismiss = { showExcelRegisterPreview = false },
                onDownloadExcel = {
                    val file = InspectionExcelExporter.exportInspectionsToExcel(
                        context = context,
                        inspections = list,
                        compliances = compliances,
                        ascendingDate = false
                    )
                    if (file != null) {
                        InspectionExcelExporter.openOrShareExcelFile(context, file)
                    }
                }
            )
        }
    }
}

@Composable
fun ReportMetricTile(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFFFED7AA)),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF475569),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ReportInspectionCard(
    inspection: Inspection,
    compliances: List<Compliance> = emptyList(),
    forceExpanded: Boolean = false,
    onClick: () -> Unit
) {
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

    val pendingCount = compliances.count { it.status != ComplianceStatus.RESOLVED && it.status != ComplianceStatus.CLOSED }
    var isCompliancesExpanded by remember { mutableStateOf(forceExpanded) }
    LaunchedEffect(forceExpanded) {
        isCompliancesExpanded = forceExpanded
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White,
            contentColor = Color(0xFF0F172A)
        ),
        border = BorderStroke(
            1.dp,
            Color(0xFFE2E8F0)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                        color = Color(0xFFFFF7ED),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFFFEDD5)),
                        modifier = Modifier.size(54.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Assessment,
                                contentDescription = null,
                                tint = Color(0xFFEA580C),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = inspection.inspectionNumber.ifEmpty { "INSP-RECORD" },
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEA580C)
                        )
                        if (inspection.isSpotInspection) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = Color(0xFFDCFCE7),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "SPOT",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF15803D),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = inspection.inspectionTypeName.ifEmpty { "Inspection" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "Inspecting Officer: ${inspection.assignedOfficerName.ifEmpty { "Unassigned" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF475569)
                    )
                    Text(
                        text = "Location: ${inspection.locationName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF475569)
                    )
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
                    Spacer(modifier = Modifier.height(10.dp))
                    IconButton(
                        onClick = onClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = "View Report Dossier",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (inspection.executionDate != null && inspection.executionDate!! > 0) {
                    Text(
                        text = "Executed: ${dateFormat.format(Date(inspection.executionDate!!))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text(
                        text = "Scheduled: $scheduledDateStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (inspection.completedAt != null) {
                    Text(
                        text = "Completed: ${dateFormat.format(Date(inspection.completedAt))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Compliance status indicator tag on inspection card (same as officer role inspection page)
            if (compliances.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = if (pendingCount > 0) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isCompliancesExpanded = !isCompliancesExpanded }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Icon(
                                if (pendingCount > 0) Icons.Default.PendingActions else Icons.Default.AssignmentTurnedIn,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (pendingCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (pendingCount > 0)
                                    "${compliances.size} Compliances raised ($pendingCount pending)"
                                else
                                    "${compliances.size} Compliances raised (All Resolved)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (pendingCount > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isCompliancesExpanded) "Hide" else "View Details",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (pendingCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                if (isCompliancesExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (pendingCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Expandable point-level compliance items under the inspection card
                AnimatedVisibility(
                    visible = isCompliancesExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        compliances.forEach { comp ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = comp.title,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Surface(
                                            color = when (comp.status) {
                                                ComplianceStatus.RESOLVED, ComplianceStatus.CLOSED -> Color(0xFF2E7D32).copy(alpha = 0.15f)
                                                ComplianceStatus.UNDER_ACTION -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                                                else -> Color(0xFFB71C1C).copy(alpha = 0.15f)
                                            },
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = comp.status.name.replace("_", " "),
                                                color = when (comp.status) {
                                                    ComplianceStatus.RESOLVED, ComplianceStatus.CLOSED -> Color(0xFF2E7D32)
                                                    ComplianceStatus.UNDER_ACTION -> MaterialTheme.colorScheme.tertiary
                                                    else -> Color(0xFFB71C1C)
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }

                                    if (comp.inspectionPointTitle.isNotBlank() && comp.inspectionPointTitle != comp.title) {
                                        Text(
                                            text = "Point: ${comp.inspectionPointTitle}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    if (comp.description.isNotBlank()) {
                                        Text(
                                            text = comp.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Assigned: ${comp.assignedOfficerName.ifBlank { "Unassigned" }}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (comp.actionTaken.isNotBlank()) {
                                            Text(
                                                text = "Action: ${comp.actionTaken}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF2E7D32),
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InspectionReportDialog(
    inspection: Inspection,
    fields: List<InspectionField>,
    compliances: List<Compliance> = emptyList(),
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
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .heightIn(max = 740.dp)
                    .padding(16.dp),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF0F172A)
                ),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
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
                                color = Color(0xFFEA580C),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                inspection.inspectionNumber,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF475569))
                        }
                    }

                    HorizontalDivider(color = Color(0xFFE2E8F0))

                    // Key Metadata Grid
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF8FAFC),
                            contentColor = Color(0xFF0F172A)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
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
                                    ClickableGpsBadge(
                                        latitude = inspection.startLatitude,
                                        longitude = inspection.startLongitude,
                                        locationName = inspection.locationName,
                                        accuracy = inspection.startAccuracy
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                } else {
                                    Text("No GPS Captured", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
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

                    // Related Compliances & Action Items
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Related Compliances (${compliances.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        FilledTonalButton(
                            onClick = {
                                val group = com.example.ui.dashboard.InspectionComplianceGroup(
                                    inspectionKey = inspection.id,
                                    inspectionNumber = inspection.inspectionNumber,
                                    inspectionTypeName = inspection.inspectionTypeName,
                                    locationName = inspection.locationName,
                                    inspectingOfficerName = inspection.assignedOfficerName,
                                    inspectionDateMillis = inspection.executionDate ?: inspection.startedAt ?: inspection.scheduledDate,
                                    isSpotInspection = inspection.isSpotInspection,
                                    compliances = compliances,
                                    parentInspection = inspection,
                                    fields = fields
                                )
                                val file = com.example.util.ComplianceWordExporter.exportInspectionComplianceToWord(context, group)
                                if (file != null) {
                                    com.example.util.ComplianceWordExporter.openOrShareWordFile(
                                        context,
                                        file,
                                        "Inspection Dossier ${inspection.inspectionNumber}"
                                    )
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Download Word Dossier", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (compliances.isEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "No compliance deficiencies logged for this inspection.",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            compliances.forEach { comp ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                color = when (comp.severity) {
                                                    ComplianceSeverity.CRITICAL -> Color(0xFFB71C1C).copy(alpha = 0.15f)
                                                    ComplianceSeverity.MAJOR -> Color(0xFFE65100).copy(alpha = 0.15f)
                                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                                },
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "${comp.severity.name} PRIORITY",
                                                    color = when (comp.severity) {
                                                        ComplianceSeverity.CRITICAL -> Color(0xFFB71C1C)
                                                        ComplianceSeverity.MAJOR -> Color(0xFFE65100)
                                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Surface(
                                                color = when (comp.status) {
                                                    ComplianceStatus.RESOLVED, ComplianceStatus.CLOSED -> Color(0xFF2E7D32).copy(alpha = 0.15f)
                                                    ComplianceStatus.UNDER_ACTION -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                                                    else -> Color(0xFFB71C1C).copy(alpha = 0.15f)
                                                },
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = comp.status.name.replace("_", " "),
                                                    color = when (comp.status) {
                                                        ComplianceStatus.RESOLVED, ComplianceStatus.CLOSED -> Color(0xFF2E7D32)
                                                        ComplianceStatus.UNDER_ACTION -> MaterialTheme.colorScheme.tertiary
                                                        else -> Color(0xFFB71C1C)
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            comp.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (comp.description.isNotBlank()) {
                                            Text(
                                                comp.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                "Assigned: ${comp.assignedOfficerName.ifBlank { "Unassigned" }}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium
                                            )
                                            if (comp.targetDate != null && comp.targetDate!! > 0) {
                                                Text(
                                                    "Target: ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(comp.targetDate!!))}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        }
                                        if (comp.actionTaken.isNotBlank()) {
                                            Text(
                                                "Action Taken: ${comp.actionTaken}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF2E7D32),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
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
                                    if (compliances.isNotEmpty()) {
                                        appendLine("\n--- Related Compliances & Action Points (${compliances.size}) ---")
                                        compliances.forEachIndexed { idx, c ->
                                            appendLine("${idx + 1}. [${c.severity.name}] ${c.title} - Status: ${c.status.name}")
                                            if (c.description.isNotBlank()) appendLine("   Details: ${c.description}")
                                            appendLine("   Assigned to: ${c.assignedOfficerName.ifBlank { "Unassigned" }}")
                                            if (c.actionTaken.isNotBlank()) appendLine("   Action: ${c.actionTaken}")
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
        Text(title, style = MaterialTheme.typography.bodySmall, color = Color(0xFF475569))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
    }
}

@Composable
fun AnswerCard(label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF8FAFC),
            contentColor = Color(0xFF0F172A)
        ),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
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
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0F172A),
                    modifier = Modifier.weight(1f)
                )
                if (!value.startsWith("content://") && !value.startsWith("file://")) {
                    val coords = LocationHelper.parseCoordinates(value)
                    if (coords != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        val addr = if (value.contains("|")) value.substringAfter("|").trim() else null
                        ClickableGpsBadge(
                            latitude = coords.first,
                            longitude = coords.second,
                            locationName = addr
                        )
                    } else {
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
