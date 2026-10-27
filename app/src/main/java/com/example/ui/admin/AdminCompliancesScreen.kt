package com.example.ui.admin

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.ui.common.ComplianceDossierPreviewDialog
import com.example.ui.common.CompliancePreviewMode
import com.example.ui.dashboard.InspectionComplianceGroup
import com.example.util.ComplianceWordExporter
import com.example.util.InspectionExcelExporter
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

enum class AdminComplianceViewMode {
    GROUPED_BY_INSPECTION,
    FLAT_LIST
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminCompliancesScreen(
    onBack: () -> Unit,
    viewModel: AdminCompliancesViewModel = viewModel()
) {
    val context = LocalContext.current
    val compliances by viewModel.compliances.collectAsState()
    val inspections by viewModel.inspections.collectAsState()
    val officers by viewModel.officers.collectAsState()

    var selectedStatusFilter by remember { mutableStateOf<ComplianceStatus?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(AdminComplianceViewMode.GROUPED_BY_INSPECTION) }
    var allGroupsExpanded by remember { mutableStateOf(false) }

    var showDialog by remember { mutableStateOf(false) }
    var editingCompliance by remember { mutableStateOf<Compliance?>(null) }
    var complianceToDelete by remember { mutableStateOf<Compliance?>(null) }
    var previewComplianceGroup by remember { mutableStateOf<InspectionComplianceGroup?>(null) }
    var showAllCompliancesPreview by remember { mutableStateOf(false) }
    var showInfoBanner by remember { mutableStateOf(false) }

    val isDark = isSystemInDarkTheme()
    val wordBtnColors = ButtonDefaults.filledTonalButtonColors(
        containerColor = if (isDark) Color(0xFF1E3A8A) else Color(0xFFDBEAFE),
        contentColor = if (isDark) Color(0xFF93C5FD) else Color(0xFF1E40AF)
    )
    val excelBtnColors = ButtonDefaults.filledTonalButtonColors(
        containerColor = if (isDark) Color(0xFF064E3B) else Color(0xFFDCFCE7),
        contentColor = if (isDark) Color(0xFF86EFAC) else Color(0xFF166534)
    )

    val validInspectionIds = remember(inspections) { inspections.map { it.id }.toSet() }
    val validInspectionNumbers = remember(inspections) { inspections.map { it.inspectionNumber }.filter { it.isNotBlank() }.toSet() }

    // CRITICAL: Compliances MUST belong to an existing inspection in the inspection list.
    // If there is no inspection in the inspection list, NO compliances or fake inspection groups should be shown!
    val validCompliances = remember(compliances, inspections, validInspectionIds, validInspectionNumbers) {
        if (inspections.isEmpty()) {
            emptyList()
        } else {
            compliances.filter { comp ->
                (comp.inspectionId.isNotBlank() && comp.inspectionId in validInspectionIds) ||
                (comp.inspectionNumber.isNotBlank() && comp.inspectionNumber in validInspectionNumbers)
            }
        }
    }

    val filteredList = remember(validCompliances, selectedStatusFilter, searchQuery) {
        validCompliances.filter { comp ->
            (selectedStatusFilter == null || comp.status == selectedStatusFilter) &&
            (searchQuery.isBlank() ||
             comp.title.contains(searchQuery, ignoreCase = true) ||
             comp.inspectionNumber.contains(searchQuery, ignoreCase = true) ||
             comp.locationName.contains(searchQuery, ignoreCase = true) ||
             comp.assignedOfficerName.contains(searchQuery, ignoreCase = true) ||
             comp.description.contains(searchQuery, ignoreCase = true))
        }
    }

    val groupedCompliances = remember(filteredList, inspections) {
        if (inspections.isEmpty()) {
            emptyList()
        } else {
            val map = inspections.associateBy { it.id }
            val mapNum = inspections.associateBy { it.inspectionNumber }
            filteredList.groupBy { comp ->
                comp.inspectionNumber.ifBlank { comp.inspectionId }
            }.mapNotNull { (key, compList) ->
                val firstComp = compList.first()
                val parent = map[firstComp.inspectionId] ?: mapNum[firstComp.inspectionNumber] ?: map[key] ?: mapNum[key]
                // STRICT: If parent inspection does NOT exist in inspections, DO NOT create a group!
                if (parent == null) return@mapNotNull null

                val inspNum = parent.inspectionNumber.ifBlank { firstComp.inspectionNumber }
                val inspType = parent.inspectionTypeName.ifBlank { "Safety Inspection" }
                val locName = parent.locationName.ifBlank { firstComp.locationName }.ifBlank { "Location unspecified" }
                val officerName = parent.assignedOfficerName.ifBlank { firstComp.inspectingOfficerName }.ifBlank { "Inspecting Officer" }
                val dateMillis = parent.executionDate ?: parent.startedAt ?: parent.scheduledDate ?: firstComp.createdAt
                val isSpot = parent.isSpotInspection

                InspectionComplianceGroup(
                    inspectionKey = key,
                    inspectionNumber = inspNum,
                    inspectionTypeName = inspType,
                    locationName = locName,
                    inspectingOfficerName = officerName,
                    inspectionDateMillis = dateMillis,
                    isSpotInspection = isSpot,
                    compliances = compList,
                    parentInspection = parent
                )
            }.sortedByDescending { it.inspectionDateMillis }
        }
    }

    val openCount = validCompliances.count { it.status == ComplianceStatus.OPEN }
    val underActionCount = validCompliances.count { it.status == ComplianceStatus.UNDER_ACTION }
    val resolvedCount = validCompliances.count { it.status == ComplianceStatus.RESOLVED || it.status == ComplianceStatus.CLOSED }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compliances & Deficiencies") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    var showExportMenu by remember { mutableStateOf(false) }

                    IconButton(onClick = { isSearchExpanded = !isSearchExpanded }) {
                        Icon(
                            if (isSearchExpanded || searchQuery.isNotEmpty()) Icons.Default.SearchOff else Icons.Default.Search,
                            contentDescription = "Toggle Search"
                        )
                    }

                    Box {
                        IconButton(onClick = { showExportMenu = true }) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "Dossier & Exports"
                            )
                        }

                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("View Overall Dossier") },
                                onClick = {
                                    showExportMenu = false
                                    showAllCompliancesPreview = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Visibility, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Download Word Dossier (.docx)") },
                                onClick = {
                                    showExportMenu = false
                                    val filterSuffix = selectedStatusFilter?.name?.replace("_", " ") ?: "All"
                                    val title = if (viewMode == AdminComplianceViewMode.FLAT_LIST)
                                        "Compliances Register ($filterSuffix - Flat List)"
                                    else
                                        "Master Compliance Dossier ($filterSuffix)"

                                    val file = if (viewMode == AdminComplianceViewMode.FLAT_LIST) {
                                        ComplianceWordExporter.exportFlatCompliancesToWord(
                                            context = context,
                                            compliances = filteredList,
                                            title = title,
                                            officerName = "Super Admin"
                                        )
                                    } else {
                                        ComplianceWordExporter.exportAllCompliancesToWord(
                                            context = context,
                                            groups = groupedCompliances,
                                            officerName = "Super Admin",
                                            customTitle = title
                                        )
                                    }
                                    if (file != null) {
                                        ComplianceWordExporter.openOrShareWordFile(context, file, title)
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Description, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Download Excel Register (.xlsx)") },
                                onClick = {
                                    showExportMenu = false
                                    val filterSuffix = selectedStatusFilter?.name ?: "All"
                                    val title = "Master_Compliances_${filterSuffix}_${if (viewMode == AdminComplianceViewMode.FLAT_LIST) "Flat" else "Grouped"}"
                                    val file = InspectionExcelExporter.exportCompliancesToExcel(
                                        context = context,
                                        groups = groupedCompliances,
                                        reportTitle = title
                                    )
                                    if (file != null) {
                                        InspectionExcelExporter.openOrShareExcelFile(context, file)
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.TableChart, contentDescription = null)
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Point-Level Compliance Guidance Banner (Compact & Collapsible)
            item {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showInfoBanner = !showInfoBanner }
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Point-Level Compliance System",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Icon(
                                if (showInfoBanner) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (showInfoBanner) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        if (showInfoBanner) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Each inspection point can be assigned to different officers for compliance. The inspecting officer retains ownership of the parent inspection.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            // Metrics / KPI row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ComplianceMetricCard("Total", validCompliances.size.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    ComplianceMetricCard("Open", openCount.toString(), MaterialTheme.colorScheme.error, Modifier.weight(1f))
                    ComplianceMetricCard("In Action", underActionCount.toString(), MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                    ComplianceMetricCard("Resolved", resolvedCount.toString(), Color(0xFF2E7D32), Modifier.weight(1f))
                }
            }

            // Expandable Search Bar (Revealed by search icon or active query)
            item {
                AnimatedVisibility(
                    visible = isSearchExpanded || searchQuery.isNotEmpty(),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by title, location, or inspection #", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Status Filter Chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selectedStatusFilter == null,
                        onClick = { selectedStatusFilter = null },
                        label = { Text("All (${validCompliances.size})", maxLines = 1, softWrap = false) }
                    )
                    ComplianceStatus.values().forEach { status ->
                        val count = validCompliances.count { it.status == status }
                        FilterChip(
                            selected = selectedStatusFilter == status,
                            onClick = { selectedStatusFilter = if (selectedStatusFilter == status) null else status },
                            label = { Text("${status.name.replace("_", " ")} ($count)", maxLines = 1, softWrap = false) }
                        )
                    }
                }
            }

            // Consolidated Compact Dossier Presentation & Action Panel
            if (filteredList.isNotEmpty()) {
                item {
                    val statusLabel = when (selectedStatusFilter) {
                        ComplianceStatus.OPEN -> "Open"
                        ComplianceStatus.UNDER_ACTION -> "In Action"
                        ComplianceStatus.RESOLVED -> "Resolved"
                        ComplianceStatus.CLOSED -> "Closed"
                        null -> "All"
                    }

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Top Row: Count & Status Badge + Compact View Mode Switcher
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Icon(
                                        if (viewMode == AdminComplianceViewMode.GROUPED_BY_INSPECTION) Icons.Default.FolderSpecial else Icons.Default.FormatListBulleted,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = if (viewMode == AdminComplianceViewMode.GROUPED_BY_INSPECTION)
                                            "${groupedCompliances.size} Dossiers (${filteredList.size} pts)"
                                        else
                                            "${filteredList.size} Compliances (Flat)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (selectedStatusFilter != null) {
                                        Surface(
                                            color = when (selectedStatusFilter) {
                                                ComplianceStatus.OPEN -> MaterialTheme.colorScheme.errorContainer
                                                ComplianceStatus.UNDER_ACTION -> MaterialTheme.colorScheme.tertiaryContainer
                                                else -> Color(0xFFDCFCE7)
                                            },
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = statusLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = when (selectedStatusFilter) {
                                                    ComplianceStatus.OPEN -> MaterialTheme.colorScheme.onErrorContainer
                                                    ComplianceStatus.UNDER_ACTION -> MaterialTheme.colorScheme.onTertiaryContainer
                                                    else -> Color(0xFF15803D)
                                                },
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }

                                // Compact View Mode Switcher Pills
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    FilterChip(
                                        selected = viewMode == AdminComplianceViewMode.GROUPED_BY_INSPECTION,
                                        onClick = { viewMode = AdminComplianceViewMode.GROUPED_BY_INSPECTION },
                                        leadingIcon = {
                                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(13.dp))
                                        },
                                        label = { Text("Grouped", style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.height(28.dp)
                                    )
                                    FilterChip(
                                        selected = viewMode == AdminComplianceViewMode.FLAT_LIST,
                                        onClick = { viewMode = AdminComplianceViewMode.FLAT_LIST },
                                        leadingIcon = {
                                            Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(13.dp))
                                        },
                                        label = { Text("Flat (${filteredList.size})", style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.height(28.dp)
                                    )
                                }
                            }

                            // Bottom Row: Action buttons with high-contrast theme-aware styling
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (viewMode == AdminComplianceViewMode.GROUPED_BY_INSPECTION) {
                                    FilledTonalButton(
                                        onClick = { allGroupsExpanded = !allGroupsExpanded },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(
                                            if (allGroupsExpanded) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            if (allGroupsExpanded) "Collapse All" else "Expand All",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                // Interactive View Master Dossier
                                OutlinedButton(
                                    onClick = { showAllCompliancesPreview = true },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "View Master Dossier",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Word (.docx) Dossier - theme-aware, 100% visible in dark & light modes
                                FilledTonalButton(
                                    onClick = {
                                        val filterSuffix = selectedStatusFilter?.name?.replace("_", " ") ?: "All"
                                        val title = if (viewMode == AdminComplianceViewMode.FLAT_LIST)
                                            "Compliances Register ($filterSuffix - Flat List)"
                                        else
                                            "Master Compliance Dossier ($filterSuffix)"

                                        val file = if (viewMode == AdminComplianceViewMode.FLAT_LIST) {
                                            ComplianceWordExporter.exportFlatCompliancesToWord(
                                                context = context,
                                                compliances = filteredList,
                                                title = title,
                                                officerName = "Super Admin"
                                            )
                                        } else {
                                            ComplianceWordExporter.exportAllCompliancesToWord(
                                                context = context,
                                                groups = groupedCompliances,
                                                officerName = "Super Admin",
                                                customTitle = title
                                            )
                                        }

                                        if (file != null) {
                                            ComplianceWordExporter.openOrShareWordFile(
                                                context = context,
                                                file = file,
                                                title = title
                                            )
                                        }
                                    },
                                    colors = wordBtnColors,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp).testTag("master_word_dossier_btn")
                                ) {
                                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "Word Dossier",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Excel (.xlsx) Register - theme-aware, 100% visible in dark & light modes
                                FilledTonalButton(
                                    onClick = {
                                        val filterSuffix = selectedStatusFilter?.name ?: "All"
                                        val title = "Master_Compliances_${filterSuffix}_${if (viewMode == AdminComplianceViewMode.FLAT_LIST) "Flat" else "Grouped"}"
                                        val file = InspectionExcelExporter.exportCompliancesToExcel(
                                            context = context,
                                            groups = groupedCompliances,
                                            reportTitle = title
                                        )
                                        if (file != null) {
                                            InspectionExcelExporter.openOrShareExcelFile(context, file)
                                        }
                                    },
                                    colors = excelBtnColors,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp).testTag("master_excel_register_btn")
                                ) {
                                    Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "Excel Register",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Compliances Content List (Grouped or Flat)
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
                                if (inspections.isEmpty()) "No inspections scheduled. Compliances and deficiencies will appear here once inspections are created."
                                else if (validCompliances.isEmpty()) "No compliances logged for scheduled inspections."
                                else "No matching compliances found.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else if (viewMode == AdminComplianceViewMode.GROUPED_BY_INSPECTION) {
                items(groupedCompliances, key = { it.inspectionKey }) { group ->
                    AdminInspectionComplianceGroupCard(
                        group = group,
                        forceExpanded = allGroupsExpanded,
                        onEdit = { comp ->
                            editingCompliance = comp
                            showDialog = true
                        },
                        onDelete = { comp ->
                            complianceToDelete = comp
                        },
                        onStatusChange = { comp, newStatus ->
                            viewModel.saveCompliance(
                                comp.copy(
                                    status = newStatus,
                                    closedAt = if (newStatus == ComplianceStatus.CLOSED || newStatus == ComplianceStatus.RESOLVED) System.currentTimeMillis() else null,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        },
                        onPreview = { group ->
                            previewComplianceGroup = group
                        }
                    )
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

        // Preview & Download dialog for single inspection compliance dossier
        previewComplianceGroup?.let { group ->
            ComplianceDossierPreviewDialog(
                groups = listOf(group),
                allGroups = groupedCompliances,
                officerName = group.inspectingOfficerName.ifBlank { "Super Admin" },
                onDismiss = { previewComplianceGroup = null },
                onDownloadWordWithGroupsAndMode = { groupsToExport, mode ->
                    val file = if (mode == CompliancePreviewMode.FLAT_LIST) {
                        ComplianceWordExporter.exportFlatCompliancesToWord(
                            context = context,
                            compliances = groupsToExport.flatMap { it.compliances },
                            title = "Compliances Action Register (Flat List)",
                            officerName = "Super Admin"
                        )
                    } else if (groupsToExport.size == 1) {
                        ComplianceWordExporter.exportInspectionComplianceToWord(context, groupsToExport.first())
                    } else {
                        ComplianceWordExporter.exportAllCompliancesToWord(
                            context = context,
                            groups = groupsToExport,
                            officerName = "Super Admin",
                            customTitle = "Master Compliance Dossier (${groupsToExport.size} Inspections)"
                        )
                    }
                    if (file != null) {
                        ComplianceWordExporter.openOrShareWordFile(
                            context,
                            file,
                            if (groupsToExport.size == 1) "Inspection Compliance Dossier ${groupsToExport.first().inspectionNumber}" else "Master Compliance Dossier"
                        )
                    }
                },
                onDownloadExcelWithGroups = { groupsToExport ->
                    val file = if (groupsToExport.size == 1) {
                        InspectionExcelExporter.exportSingleInspectionComplianceToExcel(context, groupsToExport.first())
                    } else {
                        InspectionExcelExporter.exportCompliancesToExcel(
                            context = context,
                            groups = groupsToExport,
                            reportTitle = "Compliances_Export_${groupsToExport.size}_Inspections"
                        )
                    }
                    if (file != null) {
                        InspectionExcelExporter.openOrShareExcelFile(context, file)
                    }
                }
            )
        }

        // Preview & Download dialog for master all compliances
        if (showAllCompliancesPreview) {
            val filterSuffix = selectedStatusFilter?.name?.replace("_", " ") ?: "All"
            val reportTitle = if (viewMode == AdminComplianceViewMode.FLAT_LIST)
                "Compliances Register ($filterSuffix - Flat List)"
            else
                "Master Compliance Dossier ($filterSuffix)"

            ComplianceDossierPreviewDialog(
                groups = groupedCompliances,
                allGroups = groupedCompliances,
                officerName = "Super Admin",
                initialViewMode = if (viewMode == AdminComplianceViewMode.FLAT_LIST) CompliancePreviewMode.FLAT_LIST else CompliancePreviewMode.BY_INSPECTION,
                reportTitle = reportTitle,
                onDismiss = { showAllCompliancesPreview = false },
                onDownloadWordWithGroupsAndMode = { groupsToExport, mode ->
                    val file = if (mode == CompliancePreviewMode.FLAT_LIST) {
                        ComplianceWordExporter.exportFlatCompliancesToWord(
                            context = context,
                            compliances = groupsToExport.flatMap { it.compliances },
                            title = reportTitle,
                            officerName = "Super Admin"
                        )
                    } else if (groupsToExport.size == 1) {
                        ComplianceWordExporter.exportInspectionComplianceToWord(context, groupsToExport.first())
                    } else {
                        ComplianceWordExporter.exportAllCompliancesToWord(
                            context = context,
                            groups = groupsToExport,
                            officerName = "Super Admin",
                            customTitle = reportTitle
                        )
                    }
                    if (file != null) {
                        ComplianceWordExporter.openOrShareWordFile(
                            context,
                            file,
                            reportTitle
                        )
                    }
                },
                onDownloadExcelWithGroups = { groupsToExport ->
                    val file = if (groupsToExport.size == 1) {
                        InspectionExcelExporter.exportSingleInspectionComplianceToExcel(context, groupsToExport.first())
                    } else {
                        InspectionExcelExporter.exportCompliancesToExcel(
                            context = context,
                            groups = groupsToExport,
                            reportTitle = "Master_Compliances_${filterSuffix}_${groupsToExport.size}_Inspections"
                        )
                    }
                    if (file != null) {
                        InspectionExcelExporter.openOrShareExcelFile(context, file)
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
        colors = CardDefaults.cardColors(
            containerColor = Color.White,
            contentColor = Color(0xFF0F172A)
        ),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(count, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            Text(title, style = MaterialTheme.typography.labelSmall, color = Color(0xFF475569), fontWeight = FontWeight.Medium)
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

@Composable
fun AdminInspectionComplianceGroupCard(
    group: InspectionComplianceGroup,
    forceExpanded: Boolean,
    onEdit: (Compliance) -> Unit,
    onDelete: (Compliance) -> Unit,
    onStatusChange: (Compliance, ComplianceStatus) -> Unit,
    onPreview: (InspectionComplianceGroup) -> Unit = {}
) {
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val inspectionDateStr = if (group.inspectionDateMillis > 0)
        dateFormat.format(Date(group.inspectionDateMillis)) else "Date unspecified"

    var isExpanded by remember(forceExpanded) { mutableStateOf(forceExpanded) }
    val arrowRotation by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "arrowRotation")

    val openCount = group.compliances.count { it.status == ComplianceStatus.OPEN }
    val inProgressCount = group.compliances.count { it.status == ComplianceStatus.UNDER_ACTION }
    val resolvedCount = group.compliances.count { it.status == ComplianceStatus.RESOLVED || it.status == ComplianceStatus.CLOSED }

    val isDark = isSystemInDarkTheme()
    val cardWordBtnColors = ButtonDefaults.filledTonalButtonColors(
        containerColor = if (isDark) Color(0xFF1E3A8A) else Color(0xFFDBEAFE),
        contentColor = if (isDark) Color(0xFF93C5FD) else Color(0xFF1E40AF)
    )
    val cardExcelBtnColors = ButtonDefaults.filledTonalButtonColors(
        containerColor = if (isDark) Color(0xFF064E3B) else Color(0xFFDCFCE7),
        contentColor = if (isDark) Color(0xFF86EFAC) else Color(0xFF166534)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_inspection_group_${group.inspectionNumber}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Clickable Header to Expand / Collapse
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(14.dp)
            ) {
                // Top row: Inspection Badge, Spot tag, Expand chevron
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Assignment,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    group.inspectionNumber,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        if (group.isSpotInspection) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    "SPOT",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "${group.compliances.size} pt${if (group.compliances.size > 1) "s" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { isExpanded = !isExpanded },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                modifier = Modifier.rotate(arrowRotation)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Title: Inspection Type
                Text(
                    text = group.inspectionTypeName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Meta Info: Location & Inspecting Officer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = group.locationName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = inspectionDateStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Inspecting Officer: ${group.inspectingOfficerName}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Status Chips Summary
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (openCount > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "$openCount Open",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (inProgressCount > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "$inProgressCount In Action",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (resolvedCount > 0) {
                        Surface(
                            color = Color(0xFF2E7D32).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "$resolvedCount Resolved",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                Spacer(modifier = Modifier.height(8.dp))

                // Responsive Quick Actions: View & Download on Card
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onPreview(group) },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("View", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }

                        FilledTonalButton(
                            onClick = {
                                val file = ComplianceWordExporter.exportInspectionComplianceToWord(context, group)
                                if (file != null) {
                                    ComplianceWordExporter.openOrShareWordFile(
                                        context,
                                        file,
                                        "Inspection Compliance Dossier ${group.inspectionNumber}"
                                    )
                                }
                            },
                            colors = cardWordBtnColors,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Word", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }

                        FilledTonalButton(
                            onClick = {
                                val file = InspectionExcelExporter.exportSingleInspectionComplianceToExcel(context, group)
                                if (file != null) {
                                    InspectionExcelExporter.openOrShareExcelFile(context, file)
                                }
                            },
                            colors = cardExcelBtnColors,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Excel", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }

                    Text(
                        if (isExpanded) "Hide ▲" else "Points (${group.compliances.size}) ▼",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Expanded Point-Level Compliances
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // View and Download Actions for this Inspection's compliances (referencing screenshot 4)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Points Raised for Compliance:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onPreview(group) },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("View", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }

                            FilledTonalButton(
                                onClick = {
                                    val file = ComplianceWordExporter.exportInspectionComplianceToWord(context, group)
                                    if (file != null) {
                                        ComplianceWordExporter.openOrShareWordFile(
                                            context,
                                            file,
                                            "Inspection Compliance Dossier ${group.inspectionNumber}"
                                        )
                                    }
                                },
                                colors = cardWordBtnColors,
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Word", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }

                            FilledTonalButton(
                                onClick = {
                                    val file = InspectionExcelExporter.exportSingleInspectionComplianceToExcel(context, group)
                                    if (file != null) {
                                        InspectionExcelExporter.openOrShareExcelFile(context, file)
                                    }
                                },
                                colors = cardExcelBtnColors,
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Excel", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // List of compliances under this inspection
                    group.compliances.forEach { comp ->
                        ComplianceCard(
                            compliance = comp,
                            onEdit = { onEdit(comp) },
                            onDelete = { onDelete(comp) },
                            onStatusChange = { newStatus -> onStatusChange(comp, newStatus) }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Bottom full action row inside expanded section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { onPreview(group) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("View Dossier", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val file = ComplianceWordExporter.exportInspectionComplianceToWord(context, group)
                                if (file != null) {
                                    ComplianceWordExporter.openOrShareWordFile(
                                        context,
                                        file,
                                        "Inspection Compliance Dossier ${group.inspectionNumber}"
                                    )
                                }
                            },
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Word (.docx)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }

                        FilledTonalButton(
                            onClick = {
                                val file = InspectionExcelExporter.exportSingleInspectionComplianceToExcel(context, group)
                                if (file != null) {
                                    InspectionExcelExporter.openOrShareExcelFile(context, file)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = cardExcelBtnColors,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Excel (.xlsx)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
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
        onDismissRequest = {
            inspectionExpanded = false
            officerExpanded = false
            severityExpanded = false
            statusExpanded = false
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
