package com.example.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.InspectionRepository
import com.example.data.MasterDataRepository
import com.example.data.UserRepository
import com.example.models.Inspection
import com.example.models.InspectionType
import com.example.models.LCGate
import com.example.models.Role
import com.example.models.Section
import com.example.models.Station
import com.example.models.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.platform.LocalContext
import com.example.ui.common.InspectionFilterBar
import com.example.ui.common.InspectionFilterCriteria
import com.example.ui.common.applyInspectionFilters
import com.example.ui.common.ExportExcelConfirmDialog
import com.example.ui.common.ExportWordConfirmDialog
import com.example.ui.common.InspectionDossierPreviewDialog
import com.example.ui.common.InspectionRegisterExcelPreviewDialog
import com.example.util.InspectionExcelExporter
import com.example.util.InspectionWordExporter

enum class LocationCategory {
    STATION,
    SECTION, // For footplating inspections
    LC_GATE,
    ALL
}

fun detectLocationCategory(typeName: String?): LocationCategory {
    if (typeName.isNullOrBlank()) return LocationCategory.ALL
    val lower = typeName.lowercase()
    return when {
        lower.contains("footplate") || lower.contains("footplating") -> LocationCategory.SECTION
        lower.contains("lc") || lower.contains("gate") || lower.contains("crossing") -> LocationCategory.LC_GATE
        lower.contains("station") || lower.contains("stn") || lower.contains("yard") -> LocationCategory.STATION
        else -> LocationCategory.ALL
    }
}

class AssignInspectionViewModel : ViewModel() {
    private val repo = InspectionRepository()
    private val userRepo = UserRepository()
    private val masterDataRepo = MasterDataRepository()

    private val _inspections = MutableStateFlow<List<Inspection>>(emptyList())
    val inspections: StateFlow<List<Inspection>> = _inspections.asStateFlow()

    private val _types = MutableStateFlow<List<InspectionType>>(emptyList())
    val types: StateFlow<List<InspectionType>> = _types.asStateFlow()

    private val _officers = MutableStateFlow<List<User>>(emptyList())
    val officers: StateFlow<List<User>> = _officers.asStateFlow()

    private val _stations = MutableStateFlow<List<Station>>(emptyList())
    val stations: StateFlow<List<Station>> = _stations.asStateFlow()

    private val _sections = MutableStateFlow<List<Section>>(emptyList())
    val sections: StateFlow<List<Section>> = _sections.asStateFlow()

    private val _lcGates = MutableStateFlow<List<LCGate>>(emptyList())
    val lcGates: StateFlow<List<LCGate>> = _lcGates.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            repo.getInspections().collect { list ->
                _inspections.value = list.sortedByDescending { it.createdAt }
            }
        }
        viewModelScope.launch {
            repo.getInspectionTypes().collect { list ->
                if (list.isEmpty()) {
                    seedDefaultInspectionTypes()
                } else {
                    _types.value = list
                }
            }
        }
        viewModelScope.launch {
            userRepo.getUsers().collect { list ->
                _officers.value = list.filter { it.role != Role.SUPER_ADMIN }
            }
        }
        viewModelScope.launch {
            masterDataRepo.getStations().collect { list ->
                if (list.isEmpty()) {
                    seedDefaultStations()
                } else {
                    _stations.value = list
                }
            }
        }
        viewModelScope.launch {
            masterDataRepo.getSections().collect { list ->
                if (list.isEmpty()) {
                    seedDefaultSections()
                } else {
                    _sections.value = list
                }
            }
        }
        viewModelScope.launch {
            masterDataRepo.getLCGates().collect { list ->
                if (list.isEmpty()) {
                    seedDefaultLCGates()
                } else {
                    _lcGates.value = list
                }
            }
        }
    }

    private fun seedDefaultInspectionTypes() {
        val defaultTypes = listOf(
            InspectionType(name = "Station Inspection", description = "Periodic safety & operational station audit"),
            InspectionType(name = "Footplating Inspection", description = "Locomotive cab & track section run inspection"),
            InspectionType(name = "LC Gate Inspection", description = "Level Crossing Gate equipment & safety inspection")
        )
        defaultTypes.forEach { t -> repo.addInspectionType(t) {} }
    }

    private fun seedDefaultStations() {
        val defaults = listOf(
            Station(stationName = "New Delhi", stationCode = "NDLS", section = "Delhi - Ghaziabad", division = "Delhi"),
            Station(stationName = "Kanpur Central", stationCode = "CNB", section = "Ghaziabad - Kanpur", division = "Prayagraj"),
            Station(stationName = "Prayagraj Junction", stationCode = "PRYJ", section = "Kanpur - Prayagraj", division = "Prayagraj"),
            Station(stationName = "Lucknow Charbagh", stationCode = "LKO", section = "Lucknow - Sultanpur", division = "Lucknow"),
            Station(stationName = "Varanasi Junction", stationCode = "BSB", section = "Prayagraj - Varanasi", division = "Varanasi")
        )
        defaults.forEach { masterDataRepo.saveStation(it) {} }
    }

    private fun seedDefaultSections() {
        val defaults = listOf(
            Section(sectionName = "New Delhi - Ghaziabad", fromLocation = "NDLS", toLocation = "GZB"),
            Section(sectionName = "Ghaziabad - Kanpur Central", fromLocation = "GZB", toLocation = "CNB"),
            Section(sectionName = "Kanpur Central - Prayagraj", fromLocation = "CNB", toLocation = "PRYJ"),
            Section(sectionName = "Lucknow - Sultanpur", fromLocation = "LKO", toLocation = "SLN")
        )
        defaults.forEach { masterDataRepo.saveSection(it) {} }
    }

    private fun seedDefaultLCGates() {
        val defaults = listOf(
            LCGate(lcNumber = "LC-101", gateType = "Manned Special Class", section = "New Delhi - Ghaziabad", location = "KM 14/2-4"),
            LCGate(lcNumber = "LC-42", gateType = "Manned Interlocked Class A", section = "Ghaziabad - Kanpur Central", location = "KM 88/6-8"),
            LCGate(lcNumber = "LC-18A", gateType = "Engineering Gate Class B", section = "Kanpur Central - Prayagraj", location = "KM 112/4-6"),
            LCGate(lcNumber = "LC-67", gateType = "Traffic Gate Class C", section = "Lucknow - Sultanpur", location = "KM 45/1-3")
        )
        defaults.forEach { masterDataRepo.saveLCGate(it) {} }
    }

    fun addStation(station: Station, onSaved: (Station) -> Unit) {
        masterDataRepo.saveStation(station) { success ->
            viewModelScope.launch(Dispatchers.Main) {
                if (success) onSaved(station)
            }
        }
    }

    fun addSection(section: Section, onSaved: (Section) -> Unit) {
        masterDataRepo.saveSection(section) { success ->
            viewModelScope.launch(Dispatchers.Main) {
                if (success) onSaved(section)
            }
        }
    }

    fun addLCGate(lcGate: LCGate, onSaved: (LCGate) -> Unit) {
        masterDataRepo.saveLCGate(lcGate) { success ->
            viewModelScope.launch(Dispatchers.Main) {
                if (success) onSaved(lcGate)
            }
        }
    }

    fun saveInspection(inspection: Inspection) {
        val type = _types.value.find { it.id == inspection.inspectionTypeId }
        val officer = _officers.value.find { it.uid == inspection.assignedOfficerId }
        val toSave = inspection.copy(
            inspectionTypeName = type?.name ?: inspection.inspectionTypeName,
            assignedOfficerName = officer?.name ?: inspection.assignedOfficerName,
            locationName = inspection.locationName.trim().ifEmpty { "TBD" }
        )
        repo.assignInspection(toSave) {}
    }

    fun deleteInspection(inspection: Inspection) {
        repo.deleteInspection(inspection) {}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignInspectionScreen(
    onBack: () -> Unit,
    viewModel: AssignInspectionViewModel = viewModel()
) {
    val inspections by viewModel.inspections.collectAsState()
    val types by viewModel.types.collectAsState()
    val officers by viewModel.officers.collectAsState()
    val stations by viewModel.stations.collectAsState()
    val sections by viewModel.sections.collectAsState()
    val lcGates by viewModel.lcGates.collectAsState()

    val context = LocalContext.current
    var showAssignDialog by remember { mutableStateOf(false) }
    var editingInspection by remember { mutableStateOf<Inspection?>(null) }
    var inspectionToDelete by remember { mutableStateOf<Inspection?>(null) }
    var filterCriteria by remember { mutableStateOf(InspectionFilterCriteria()) }
    var showExportExcelDialog by remember { mutableStateOf(false) }
    var showExportWordDialog by remember { mutableStateOf(false) }
    var showDossierPreview by remember { mutableStateOf(false) }
    var showRegisterPreview by remember { mutableStateOf(false) }
    var previewInspectionsList by remember { mutableStateOf<List<Inspection>>(emptyList()) }

    val filteredInspections = remember(inspections, filterCriteria) {
        inspections.applyInspectionFilters(filterCriteria)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Assign & Review Inspections") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = {
                        previewInspectionsList = if (filterCriteria.isActive) filteredInspections else inspections
                        showDossierPreview = true
                    }) {
                        Icon(
                            Icons.Default.Visibility,
                            contentDescription = "View Dossier Preview",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(onClick = { showExportExcelDialog = true }) {
                        Icon(
                            Icons.Default.FileDownload,
                            contentDescription = "Export Excel",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(onClick = { showExportWordDialog = true }) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = "Download Word Dossier",
                            tint = MaterialTheme.colorScheme.onPrimary
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
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editingInspection = null; showAssignDialog = true }) {
                Icon(Icons.Default.Add, "Assign New")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Enhanced Filter Bar (Officer-wise, Type-wise, Date-wise, Category, Status)
            InspectionFilterBar(
                criteria = filterCriteria,
                onCriteriaChange = { filterCriteria = it },
                officers = officers,
                types = types,
                showOfficerFilter = true,
                totalCount = inspections.size,
                filteredCount = filteredInspections.size,
                onExportExcel = { showExportExcelDialog = true },
                onExportWord = { showExportWordDialog = true },
                onViewDossier = {
                    previewInspectionsList = filteredInspections
                    showDossierPreview = true
                },
                onViewRegister = {
                    previewInspectionsList = filteredInspections
                    showRegisterPreview = true
                }
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

            if (filteredInspections.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No inspections found for selected filter. Click + to schedule.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(filteredInspections, key = { it.id }) { insp ->
                    InspectionCard(
                        inspection = insp,
                        onEdit = { editingInspection = insp; showAssignDialog = true },
                        onDelete = { inspectionToDelete = insp }
                    )
                }
            }
        }
    }

    if (showAssignDialog) {
            AssignInspectionDialog(
                inspection = editingInspection,
                types = types,
                officers = officers,
                stations = stations,
                sections = sections,
                lcGates = lcGates,
                onAddStation = { st, cb -> viewModel.addStation(st, cb) },
                onAddSection = { sec, cb -> viewModel.addSection(sec, cb) },
                onAddLCGate = { gate, cb -> viewModel.addLCGate(gate, cb) },
                onDismiss = { showAssignDialog = false },
                onSave = { newInsp ->
                    viewModel.saveInspection(newInsp)
                    showAssignDialog = false
                }
            )
        }

        inspectionToDelete?.let { insp ->
            AlertDialog(
                onDismissRequest = { inspectionToDelete = null },
                title = { Text("Delete Inspection") },
                text = { Text("Are you sure you want to delete assignment ${insp.inspectionNumber}?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteInspection(insp)
                        inspectionToDelete = null
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { inspectionToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showExportExcelDialog) {
            ExportExcelConfirmDialog(
                totalInspectionsCount = inspections.size,
                filteredInspectionsCount = filteredInspections.size,
                isFilterActive = filterCriteria.isActive,
                onDismiss = { showExportExcelDialog = false },
                onViewPreview = { exportOnlyFiltered, chronological ->
                    showExportExcelDialog = false
                    val list = if (exportOnlyFiltered) filteredInspections else inspections
                    previewInspectionsList = if (chronological) list.sortedBy { it.scheduledDate } else list.sortedByDescending { it.scheduledDate }
                    showRegisterPreview = true
                },
                onExport = { exportOnlyFiltered, chronological ->
                    showExportExcelDialog = false
                    val listToExport = if (exportOnlyFiltered) filteredInspections else inspections
                    val file = InspectionExcelExporter.exportInspectionsToExcel(
                        context = context,
                        inspections = listToExport,
                        ascendingDate = chronological
                    )
                    if (file != null) {
                        InspectionExcelExporter.openOrShareExcelFile(context, file)
                    }
                }
            )
        }

        if (showExportWordDialog) {
            ExportWordConfirmDialog(
                totalInspectionsCount = inspections.size,
                filteredInspectionsCount = filteredInspections.size,
                isFilterActive = filterCriteria.isActive,
                onDismiss = { showExportWordDialog = false },
                onViewPreview = { exportOnlyFiltered, chronological ->
                    showExportWordDialog = false
                    val list = if (exportOnlyFiltered) filteredInspections else inspections
                    previewInspectionsList = if (chronological) list.sortedBy { it.scheduledDate } else list.sortedByDescending { it.scheduledDate }
                    showDossierPreview = true
                },
                onExport = { exportOnlyFiltered, chronological ->
                    showExportWordDialog = false
                    val listToExport = if (exportOnlyFiltered) filteredInspections else inspections
                    val file = InspectionWordExporter.exportFilteredInspectionsToWord(
                        context = context,
                        inspections = listToExport,
                        criteria = if (exportOnlyFiltered) filterCriteria else null,
                        ascendingDate = chronological
                    )
                    if (file != null) {
                        InspectionWordExporter.openOrShareWordFile(
                            context,
                            file,
                            title = if (exportOnlyFiltered) "Filtered Inspections Dossier" else "All Inspections Dossier"
                        )
                    }
                }
            )
        }

        if (showDossierPreview) {
            val list = if (previewInspectionsList.isNotEmpty()) previewInspectionsList else if (filterCriteria.isActive) filteredInspections else inspections
            InspectionDossierPreviewDialog(
                inspections = list,
                criteria = if (filterCriteria.isActive) filterCriteria else null,
                onDismiss = { showDossierPreview = false },
                onDownloadWord = {
                    val file = InspectionWordExporter.exportFilteredInspectionsToWord(
                        context = context,
                        inspections = list,
                        criteria = if (filterCriteria.isActive) filterCriteria else null,
                        ascendingDate = false
                    )
                    if (file != null) {
                        InspectionWordExporter.openOrShareWordFile(
                            context,
                            file,
                            title = "Inspection Dossier"
                        )
                    }
                },
                onDownloadExcel = {
                    val file = InspectionExcelExporter.exportInspectionsToExcel(
                        context = context,
                        inspections = list,
                        ascendingDate = false
                    )
                    if (file != null) {
                        InspectionExcelExporter.openOrShareExcelFile(context, file)
                    }
                }
            )
        }

        if (showRegisterPreview) {
            val list = if (previewInspectionsList.isNotEmpty()) previewInspectionsList else if (filterCriteria.isActive) filteredInspections else inspections
            InspectionRegisterExcelPreviewDialog(
                inspections = list,
                criteria = if (filterCriteria.isActive) filterCriteria else null,
                onDismiss = { showRegisterPreview = false },
                onDownloadExcel = {
                    val file = InspectionExcelExporter.exportInspectionsToExcel(
                        context = context,
                        inspections = list,
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
fun InspectionCard(inspection: Inspection, onEdit: () -> Unit, onDelete: () -> Unit) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val dateString = if (inspection.scheduledDate > 0) dateFormat.format(Date(inspection.scheduledDate)) else "Not Scheduled"

    val isLocationTbd = inspection.locationName.isBlank() || inspection.locationName.equals("TBD", ignoreCase = true)

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when {
                                inspection.inspectionTypeName.contains("Station", ignoreCase = true) -> Icons.Default.Place
                                inspection.inspectionTypeName.contains("Footplate", ignoreCase = true) -> Icons.Default.Train
                                else -> Icons.Default.Assignment
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            inspection.inspectionNumber.ifEmpty { "INSPECTION" },
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (inspection.isSpotInspection) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = if (inspection.inspectionCategory == "SELF") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (inspection.inspectionCategory == "SELF") Icons.Default.AssignmentInd else Icons.Default.FlashOn,
                                        contentDescription = null,
                                        modifier = Modifier.size(11.dp),
                                        tint = if (inspection.inspectionCategory == "SELF") MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = if (inspection.inspectionCategory == "SELF") "SELF" else "SPOT",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (inspection.inspectionCategory == "SELF") MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        inspection.inspectionTypeName.ifEmpty { "General Inspection" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = inspection.status.name.replace("_", " "),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Location Highlight
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = if (isLocationTbd) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Location: ${if (isLocationTbd) "TBD (Click Edit to assign location)" else inspection.locationName}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isLocationTbd) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Officer: ${inspection.assignedOfficerName.ifEmpty { "Unassigned" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Scheduled: $dateString",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isLocationTbd) "Assign Location" else "Edit")
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignInspectionDialog(
    inspection: Inspection?,
    types: List<InspectionType>,
    officers: List<User>,
    stations: List<Station>,
    sections: List<Section>,
    lcGates: List<LCGate>,
    onAddStation: (Station, (Station) -> Unit) -> Unit,
    onAddSection: (Section, (Section) -> Unit) -> Unit,
    onAddLCGate: (LCGate, (LCGate) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onSave: (Inspection) -> Unit
) {
    var selectedType by remember { mutableStateOf<InspectionType?>(types.find { it.id == inspection?.inspectionTypeId } ?: types.firstOrNull()) }
    var selectedOfficer by remember { mutableStateOf<User?>(officers.find { it.uid == inspection?.assignedOfficerId }) }
    var selectedLocationName by remember { mutableStateOf(inspection?.locationName?.takeIf { it != "TBD" } ?: "") }
    var selectedLocationId by remember { mutableStateOf(inspection?.locationId ?: "") }

    var activeCategory by remember(selectedType) {
        mutableStateOf(detectLocationCategory(selectedType?.name))
    }

    var typeExpanded by remember { mutableStateOf(false) }
    var officerExpanded by remember { mutableStateOf(false) }
    var locationExpanded by remember { mutableStateOf(false) }

    // Search filters for dropdowns
    var locationSearchQuery by remember { mutableStateOf("") }
    var officerSearchQuery by remember { mutableStateOf("") }
    var typeSearchQuery by remember { mutableStateOf("") }

    var scheduledDate by remember { mutableStateOf(inspection?.scheduledDate ?: System.currentTimeMillis()) }
    var inspectionCategory by remember {
        mutableStateOf(inspection?.inspectionCategory ?: if (inspection?.isSpotInspection == true) "SPOT" else "ASSIGNED")
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showAddLocationDialog by remember { mutableStateOf(false) }

    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
            Card(
                modifier = Modifier.fillMaxWidth(0.95f).heightIn(max = 750.dp).padding(16.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        if (inspection == null) "Assign Inspection" else "Edit Inspection Assignment",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    // Inspection Category / Mode Selection
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Inspection Option / Mode *", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = inspectionCategory == "ASSIGNED",
                                onClick = { inspectionCategory = "ASSIGNED" },
                                leadingIcon = { Icon(Icons.Default.Assignment, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("Assigned") }
                            )
                            FilterChip(
                                selected = inspectionCategory == "SPOT",
                                onClick = { inspectionCategory = "SPOT" },
                                leadingIcon = { Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("Spot") }
                            )
                            FilterChip(
                                selected = inspectionCategory == "SELF",
                                onClick = { inspectionCategory = "SELF" },
                                leadingIcon = { Icon(Icons.Default.AssignmentInd, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("Self") }
                            )
                        }
                    }

                    // 1. Inspection Type Dropdown with Search
                    ExposedDropdownMenuBox(
                        expanded = typeExpanded,
                        onExpandedChange = { typeExpanded = !typeExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedType?.name ?: "Select Inspection Type",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Inspection Type *") },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = typeExpanded,
                            onDismissRequest = {
                                typeExpanded = false
                                typeSearchQuery = ""
                            },
                            modifier = Modifier.heightIn(max = 350.dp)
                        ) {
                            OutlinedTextField(
                                value = typeSearchQuery,
                                onValueChange = { typeSearchQuery = it },
                                placeholder = { Text("Search inspection types...", style = MaterialTheme.typography.bodySmall) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(18.dp)) },
                                trailingIcon = {
                                    if (typeSearchQuery.isNotEmpty()) {
                                        IconButton(onClick = { typeSearchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            )
                            HorizontalDivider()

                            val filteredTypes = remember(types, typeSearchQuery) {
                                if (typeSearchQuery.isBlank()) types
                                else {
                                    val q = typeSearchQuery.trim().lowercase()
                                    types.filter {
                                        it.name.lowercase().contains(q) || it.description.lowercase().contains(q)
                                    }
                                }
                            }

                            if (filteredTypes.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No inspection types matching \"$typeSearchQuery\"") },
                                    onClick = {}
                                )
                            } else {
                                filteredTypes.forEach { type ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(type.name, fontWeight = FontWeight.SemiBold)
                                                val cat = detectLocationCategory(type.name)
                                                val hint = when (cat) {
                                                    LocationCategory.STATION -> "Locations from: Stations List"
                                                    LocationCategory.SECTION -> "Locations from: Sections List (Footplating)"
                                                    LocationCategory.LC_GATE -> "Locations from: LC Gates List"
                                                    else -> "General Master Locations"
                                                }
                                                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        },
                                        onClick = {
                                            selectedType = type
                                            activeCategory = detectLocationCategory(type.name)
                                            typeExpanded = false
                                            typeSearchQuery = ""
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 2. Location Category Indicator & Selector
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when (activeCategory) {
                                    LocationCategory.STATION -> "Station Master Locations"
                                    LocationCategory.SECTION -> "Section Master Locations (Footplating)"
                                    LocationCategory.LC_GATE -> "LC Gate Master Locations"
                                    LocationCategory.ALL -> "Master Data Locations"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            // Quick Category Override Tabs
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilterChip(
                                    selected = activeCategory == LocationCategory.STATION,
                                    onClick = { activeCategory = LocationCategory.STATION },
                                    label = { Text("Stations", style = MaterialTheme.typography.labelSmall) }
                                )
                                FilterChip(
                                    selected = activeCategory == LocationCategory.SECTION,
                                    onClick = { activeCategory = LocationCategory.SECTION },
                                    label = { Text("Sections", style = MaterialTheme.typography.labelSmall) }
                                )
                                FilterChip(
                                    selected = activeCategory == LocationCategory.LC_GATE,
                                    onClick = { activeCategory = LocationCategory.LC_GATE },
                                    label = { Text("LC Gates", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }

                        // Location Dropdown with Real-Time Search
                        ExposedDropdownMenuBox(
                            expanded = locationExpanded,
                            onExpandedChange = { locationExpanded = !locationExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedLocationName.ifEmpty { "Select Location *" },
                                onValueChange = {},
                                readOnly = true,
                                label = {
                                    Text(
                                        when (activeCategory) {
                                            LocationCategory.STATION -> "Station Location *"
                                            LocationCategory.SECTION -> "Track Section (Footplating) *"
                                            LocationCategory.LC_GATE -> "LC Gate Location *"
                                            LocationCategory.ALL -> "Location (Master Data) *"
                                        }
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = when (activeCategory) {
                                            LocationCategory.STATION -> Icons.Default.Place
                                            LocationCategory.SECTION -> Icons.Default.Train
                                            LocationCategory.LC_GATE -> Icons.Default.LocationOn
                                            else -> Icons.Default.LocationOn
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = locationExpanded) },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                            )

                            ExposedDropdownMenu(
                                expanded = locationExpanded,
                                onDismissRequest = { locationExpanded = false },
                                modifier = Modifier.heightIn(max = 380.dp)
                            ) {
                                // Search Input pinned at top of Location Dropdown
                                OutlinedTextField(
                                    value = locationSearchQuery,
                                    onValueChange = { locationSearchQuery = it },
                                    placeholder = {
                                        Text(
                                            when (activeCategory) {
                                                LocationCategory.STATION -> "Search stations by name, code, division..."
                                                LocationCategory.SECTION -> "Search sections by name, terminal..."
                                                LocationCategory.LC_GATE -> "Search LC gates by number, section..."
                                                LocationCategory.ALL -> "Search any location..."
                                            },
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(18.dp)) },
                                    trailingIcon = {
                                        if (locationSearchQuery.isNotEmpty()) {
                                            IconButton(onClick = { locationSearchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                                Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp)
                                )

                                // Add New Item Action (Pre-fills searched term if any)
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    },
                                    text = {
                                        Text(
                                            text = if (locationSearchQuery.isNotBlank()) {
                                                "+ Add \"${locationSearchQuery.trim()}\" to Master Data"
                                            } else {
                                                when (activeCategory) {
                                                    LocationCategory.STATION -> "+ Add New Station to Master Data"
                                                    LocationCategory.SECTION -> "+ Add New Section to Master Data"
                                                    LocationCategory.LC_GATE -> "+ Add New LC Gate to Master Data"
                                                    LocationCategory.ALL -> "+ Add New Location to Master Data"
                                                }
                                            },
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    onClick = {
                                        locationExpanded = false
                                        showAddLocationDialog = true
                                    }
                                )

                                HorizontalDivider()

                                when (activeCategory) {
                                    LocationCategory.STATION -> {
                                        val filteredStations = remember(stations, locationSearchQuery) {
                                            if (locationSearchQuery.isBlank()) stations
                                            else {
                                                val q = locationSearchQuery.trim().lowercase()
                                                stations.filter {
                                                    it.stationName.lowercase().contains(q) ||
                                                    it.stationCode.lowercase().contains(q) ||
                                                    it.section.lowercase().contains(q) ||
                                                    it.division.lowercase().contains(q)
                                                }
                                            }
                                        }

                                        if (filteredStations.isEmpty()) {
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        if (locationSearchQuery.isBlank()) "No stations in master data. Click above to add."
                                                        else "No stations matching \"$locationSearchQuery\". Click above to add."
                                                    )
                                                },
                                                onClick = {
                                                    locationExpanded = false
                                                    showAddLocationDialog = true
                                                }
                                            )
                                        } else {
                                            filteredStations.forEach { station ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Column {
                                                            Text(
                                                                "${station.stationName} (${station.stationCode})",
                                                                fontWeight = FontWeight.Medium
                                                            )
                                                            if (station.section.isNotBlank() || station.division.isNotBlank()) {
                                                                Text(
                                                                    "Section: ${station.section} • Div: ${station.division}".trim(' ', '•'),
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                            }
                                                        }
                                                    },
                                                    onClick = {
                                                        selectedLocationName = "${station.stationName} (${station.stationCode})"
                                                        selectedLocationId = station.id
                                                        locationExpanded = false
                                                        locationSearchQuery = ""
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    LocationCategory.SECTION -> {
                                        val filteredSections = remember(sections, locationSearchQuery) {
                                            if (locationSearchQuery.isBlank()) sections
                                            else {
                                                val q = locationSearchQuery.trim().lowercase()
                                                sections.filter {
                                                    it.sectionName.lowercase().contains(q) ||
                                                    it.fromLocation.lowercase().contains(q) ||
                                                    it.toLocation.lowercase().contains(q)
                                                }
                                            }
                                        }

                                        if (filteredSections.isEmpty()) {
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        if (locationSearchQuery.isBlank()) "No sections in master data. Click above to add."
                                                        else "No sections matching \"$locationSearchQuery\". Click above to add."
                                                    )
                                                },
                                                onClick = {
                                                    locationExpanded = false
                                                    showAddLocationDialog = true
                                                }
                                            )
                                        } else {
                                            filteredSections.forEach { section ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Column {
                                                            Text(section.sectionName, fontWeight = FontWeight.Medium)
                                                            if (section.fromLocation.isNotBlank() && section.toLocation.isNotBlank()) {
                                                                Text(
                                                                    "${section.fromLocation} ➔ ${section.toLocation}",
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                            }
                                                        }
                                                    },
                                                    onClick = {
                                                        selectedLocationName = section.sectionName
                                                        selectedLocationId = section.id
                                                        locationExpanded = false
                                                        locationSearchQuery = ""
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    LocationCategory.LC_GATE -> {
                                        val filteredLCGates = remember(lcGates, locationSearchQuery) {
                                            if (locationSearchQuery.isBlank()) lcGates
                                            else {
                                                val q = locationSearchQuery.trim().lowercase()
                                                lcGates.filter {
                                                    it.lcNumber.lowercase().contains(q) ||
                                                    it.gateType.lowercase().contains(q) ||
                                                    it.section.lowercase().contains(q) ||
                                                    it.location.lowercase().contains(q)
                                                }
                                            }
                                        }

                                        if (filteredLCGates.isEmpty()) {
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        if (locationSearchQuery.isBlank()) "No LC gates in master data. Click above to add."
                                                        else "No LC gates matching \"$locationSearchQuery\". Click above to add."
                                                    )
                                                },
                                                onClick = {
                                                    locationExpanded = false
                                                    showAddLocationDialog = true
                                                }
                                            )
                                        } else {
                                            filteredLCGates.forEach { gate ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Column {
                                                            Text("${gate.lcNumber} (${gate.gateType})", fontWeight = FontWeight.Medium)
                                                            Text(
                                                                "Loc: ${gate.location} • Section: ${gate.section}".trim(' ', '•'),
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    },
                                                    onClick = {
                                                        selectedLocationName = "${gate.lcNumber} (${gate.gateType}) - ${gate.location}".trim(' ', '-')
                                                        selectedLocationId = gate.id
                                                        locationExpanded = false
                                                        locationSearchQuery = ""
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    LocationCategory.ALL -> {
                                        val q = locationSearchQuery.trim().lowercase()
                                        val filStations = if (q.isBlank()) stations else stations.filter {
                                            it.stationName.lowercase().contains(q) || it.stationCode.lowercase().contains(q)
                                        }
                                        val filSections = if (q.isBlank()) sections else sections.filter {
                                            it.sectionName.lowercase().contains(q)
                                        }
                                        val filGates = if (q.isBlank()) lcGates else lcGates.filter {
                                            it.lcNumber.lowercase().contains(q) || it.gateType.lowercase().contains(q)
                                        }

                                        filStations.forEach { station ->
                                            DropdownMenuItem(
                                                text = { Text("Station: ${station.stationName} (${station.stationCode})") },
                                                onClick = {
                                                    selectedLocationName = "${station.stationName} (${station.stationCode})"
                                                    selectedLocationId = station.id
                                                    locationExpanded = false
                                                    locationSearchQuery = ""
                                                }
                                            )
                                        }
                                        filSections.forEach { section ->
                                            DropdownMenuItem(
                                                text = { Text("Section: ${section.sectionName}") },
                                                onClick = {
                                                    selectedLocationName = section.sectionName
                                                    selectedLocationId = section.id
                                                    locationExpanded = false
                                                    locationSearchQuery = ""
                                                }
                                            )
                                        }
                                        filGates.forEach { gate ->
                                            DropdownMenuItem(
                                                text = { Text("LC Gate: ${gate.lcNumber} (${gate.gateType})") },
                                                onClick = {
                                                    selectedLocationName = "${gate.lcNumber} (${gate.gateType})"
                                                    selectedLocationId = gate.id
                                                    locationExpanded = false
                                                    locationSearchQuery = ""
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Officer Dropdown with Real-Time Search
                    ExposedDropdownMenuBox(
                        expanded = officerExpanded,
                        onExpandedChange = { officerExpanded = !officerExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedOfficer?.let { "${it.name} (${if (it.role == Role.ADMIN) "Admin" else "Officer"})" } ?: "Select Officer / Admin *",
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                            label = { Text("Assigned Officer / Admin *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = officerExpanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = officerExpanded,
                            onDismissRequest = {
                                officerExpanded = false
                                officerSearchQuery = ""
                            },
                            modifier = Modifier.heightIn(max = 350.dp)
                        ) {
                            OutlinedTextField(
                                value = officerSearchQuery,
                                onValueChange = { officerSearchQuery = it },
                                placeholder = { Text("Search officer by name, HRMS, designation...", style = MaterialTheme.typography.bodySmall) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(18.dp)) },
                                trailingIcon = {
                                    if (officerSearchQuery.isNotEmpty()) {
                                        IconButton(onClick = { officerSearchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            )
                            HorizontalDivider()

                            val filteredOfficers = remember(officers, officerSearchQuery) {
                                if (officerSearchQuery.isBlank()) officers
                                else {
                                    val q = officerSearchQuery.trim().lowercase()
                                    officers.filter {
                                        it.name.lowercase().contains(q) ||
                                        it.designation.lowercase().contains(q) ||
                                        it.departmentName.lowercase().contains(q) ||
                                        it.hrmsId.lowercase().contains(q)
                                    }
                                }
                            }

                            if (filteredOfficers.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No officers matching \"$officerSearchQuery\"") },
                                    onClick = {}
                                )
                            } else {
                                filteredOfficers.forEach { officer ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(officer.name, fontWeight = FontWeight.SemiBold)
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        color = if (officer.role == Role.ADMIN) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                                                        shape = MaterialTheme.shapes.extraSmall
                                                    ) {
                                                        Text(
                                                            text = if (officer.role == Role.ADMIN) "ADMIN" else "OFFICER",
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
                                            officerSearchQuery = ""
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 4. Scheduled Date
                    OutlinedTextField(
                        value = dateFormat.format(Date(scheduledDate)),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Scheduled Date *") },
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(Icons.Default.DateRange, contentDescription = "Select Date")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }
                    )

                    // Action Buttons
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (selectedType != null && selectedOfficer != null) {
                                    val locName = selectedLocationName.trim().ifEmpty { "TBD" }
                                    val newInsp = (inspection ?: Inspection()).copy(
                                        inspectionTypeId = selectedType!!.id,
                                        inspectionTypeName = selectedType!!.name,
                                        assignedOfficerId = selectedOfficer!!.uid,
                                        assignedOfficerName = selectedOfficer!!.name,
                                        locationId = selectedLocationId,
                                        locationName = locName,
                                        scheduledDate = scheduledDate,
                                        isSpotInspection = inspectionCategory != "ASSIGNED",
                                        inspectionCategory = inspectionCategory
                                    )
                                    onSave(newInsp)
                                }
                            },
                            enabled = selectedType != null && selectedOfficer != null
                        ) {
                            Text("Save Assignment")
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = scheduledDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        scheduledDate = it
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Modal to add a new location into master data on-the-fly
    if (showAddLocationDialog) {
        AddNewMasterLocationDialog(
            initialCategory = activeCategory,
            initialName = locationSearchQuery.trim(),
            onDismiss = { showAddLocationDialog = false },
            onSaveStation = { station ->
                onAddStation(station) { savedStation ->
                    selectedLocationName = "${savedStation.stationName} (${savedStation.stationCode})"
                    selectedLocationId = savedStation.id
                    locationSearchQuery = ""
                    showAddLocationDialog = false
                }
            },
            onSaveSection = { section ->
                onAddSection(section) { savedSection ->
                    selectedLocationName = savedSection.sectionName
                    selectedLocationId = savedSection.id
                    locationSearchQuery = ""
                    showAddLocationDialog = false
                }
            },
            onSaveLCGate = { gate ->
                onAddLCGate(gate) { savedGate ->
                    selectedLocationName = "${savedGate.lcNumber} (${savedGate.gateType})"
                    selectedLocationId = savedGate.id
                    locationSearchQuery = ""
                    showAddLocationDialog = false
                }
            }
        )
    }
}

@Composable
fun AddNewMasterLocationDialog(
    initialCategory: LocationCategory,
    initialName: String = "",
    onDismiss: () -> Unit,
    onSaveStation: (Station) -> Unit,
    onSaveSection: (Section) -> Unit,
    onSaveLCGate: (LCGate) -> Unit
) {
    var category by remember { mutableStateOf(if (initialCategory == LocationCategory.ALL) LocationCategory.STATION else initialCategory) }

    // Station fields
    var stationName by remember { mutableStateOf(if (category == LocationCategory.STATION) initialName else "") }
    var stationCode by remember { mutableStateOf("") }
    var stationSection by remember { mutableStateOf("") }
    var stationDivision by remember { mutableStateOf("") }

    // Section fields
    var sectionName by remember { mutableStateOf(if (category == LocationCategory.SECTION) initialName else "") }
    var fromLoc by remember { mutableStateOf("") }
    var toLoc by remember { mutableStateOf("") }

    // LC Gate fields
    var lcNumber by remember { mutableStateOf(if (category == LocationCategory.LC_GATE) initialName else "") }
    var gateType by remember { mutableStateOf("") }
    var lcSection by remember { mutableStateOf("") }
    var lcLocation by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = when (category) {
                        LocationCategory.STATION -> "Add Station to Master Data"
                        LocationCategory.SECTION -> "Add Section to Master Data"
                        LocationCategory.LC_GATE -> "Add LC Gate to Master Data"
                        LocationCategory.ALL -> "Add Location to Master Data"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "This location will be saved permanently for future assignments.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Category switch chips
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = category == LocationCategory.STATION,
                        onClick = {
                            category = LocationCategory.STATION
                            if (stationName.isBlank() && initialName.isNotBlank()) stationName = initialName
                        },
                        label = { Text("Station") }
                    )
                    FilterChip(
                        selected = category == LocationCategory.SECTION,
                        onClick = {
                            category = LocationCategory.SECTION
                            if (sectionName.isBlank() && initialName.isNotBlank()) sectionName = initialName
                        },
                        label = { Text("Section (Footplate)") }
                    )
                    FilterChip(
                        selected = category == LocationCategory.LC_GATE,
                        onClick = {
                            category = LocationCategory.LC_GATE
                            if (lcNumber.isBlank() && initialName.isNotBlank()) lcNumber = initialName
                        },
                        label = { Text("LC Gate") }
                    )
                }

                when (category) {
                    LocationCategory.STATION -> {
                        OutlinedTextField(
                            value = stationName,
                            onValueChange = { stationName = it },
                            label = { Text("Station Name *") },
                            placeholder = { Text("e.g. Kanpur Central") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = stationCode,
                            onValueChange = { stationCode = it },
                            label = { Text("Station Code *") },
                            placeholder = { Text("e.g. CNB") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = stationSection,
                            onValueChange = { stationSection = it },
                            label = { Text("Section (Optional)") },
                            placeholder = { Text("e.g. Ghaziabad - Kanpur") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = stationDivision,
                            onValueChange = { stationDivision = it },
                            label = { Text("Division (Optional)") },
                            placeholder = { Text("e.g. Prayagraj") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    LocationCategory.SECTION -> {
                        OutlinedTextField(
                            value = sectionName,
                            onValueChange = { sectionName = it },
                            label = { Text("Section Name *") },
                            placeholder = { Text("e.g. Moradabad - Bareilly") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = fromLoc,
                            onValueChange = { fromLoc = it },
                            label = { Text("From Station / KM (Optional)") },
                            placeholder = { Text("e.g. MB") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = toLoc,
                            onValueChange = { toLoc = it },
                            label = { Text("To Station / KM (Optional)") },
                            placeholder = { Text("e.g. BE") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    LocationCategory.LC_GATE -> {
                        OutlinedTextField(
                            value = lcNumber,
                            onValueChange = { lcNumber = it },
                            label = { Text("LC Gate Number *") },
                            placeholder = { Text("e.g. LC-125") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = gateType,
                            onValueChange = { gateType = it },
                            label = { Text("Gate Type (Optional)") },
                            placeholder = { Text("e.g. Manned Special Class / Interlocked") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = lcLocation,
                            onValueChange = { lcLocation = it },
                            label = { Text("Location (KM) (Optional)") },
                            placeholder = { Text("e.g. KM 24/6") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = lcSection,
                            onValueChange = { lcSection = it },
                            label = { Text("Section (Optional)") },
                            placeholder = { Text("e.g. NDLS - GZB") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    else -> {}
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (category) {
                        LocationCategory.STATION -> {
                            if (stationName.isNotBlank()) {
                                val code = stationCode.ifBlank { stationName.take(4).uppercase() }
                                onSaveStation(
                                    Station(
                                        stationName = stationName.trim(),
                                        stationCode = code.trim().uppercase(),
                                        section = stationSection.trim(),
                                        division = stationDivision.trim()
                                    )
                                )
                            }
                        }
                        LocationCategory.SECTION -> {
                            if (sectionName.isNotBlank()) {
                                onSaveSection(
                                    Section(
                                        sectionName = sectionName.trim(),
                                        fromLocation = fromLoc.trim(),
                                        toLocation = toLoc.trim()
                                    )
                                )
                            }
                        }
                        LocationCategory.LC_GATE -> {
                            if (lcNumber.isNotBlank()) {
                                onSaveLCGate(
                                    LCGate(
                                        lcNumber = lcNumber.trim(),
                                        gateType = gateType.trim().ifEmpty { "Manned" },
                                        section = lcSection.trim(),
                                        location = lcLocation.trim()
                                    )
                                )
                            }
                        }
                        else -> {}
                    }
                },
                enabled = when (category) {
                    LocationCategory.STATION -> stationName.isNotBlank()
                    LocationCategory.SECTION -> sectionName.isNotBlank()
                    LocationCategory.LC_GATE -> lcNumber.isNotBlank()
                    else -> false
                }
            ) {
                Text("Save to Master Data")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
