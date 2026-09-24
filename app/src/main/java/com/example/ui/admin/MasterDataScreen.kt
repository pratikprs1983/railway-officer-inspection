package com.example.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.MasterDataRepository
import com.example.models.LCGate
import com.example.models.Section
import com.example.models.Station
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class MasterDataViewModel : ViewModel() {
    private val repo = MasterDataRepository()

    private val _stations = MutableStateFlow<List<Station>>(emptyList())
    val stations: StateFlow<List<Station>> = _stations.asStateFlow()

    private val _sections = MutableStateFlow<List<Section>>(emptyList())
    val sections: StateFlow<List<Section>> = _sections.asStateFlow()

    private val _lcGates = MutableStateFlow<List<LCGate>>(emptyList())
    val lcGates: StateFlow<List<LCGate>> = _lcGates.asStateFlow()

    init {
        viewModelScope.launch {
            repo.getStations().catch { }.collect { _stations.value = it }
        }
        viewModelScope.launch {
            repo.getSections().catch { }.collect { _sections.value = it }
        }
        viewModelScope.launch {
            repo.getLCGates().catch { }.collect { _lcGates.value = it }
        }
    }

    fun saveStation(station: Station) = repo.saveStation(station) {}
    fun deleteStation(station: Station) = repo.deleteStation(station) {}

    fun saveSection(section: Section) = repo.saveSection(section) {}
    fun deleteSection(section: Section) = repo.deleteSection(section) {}

    fun saveLCGate(lcGate: LCGate) = repo.saveLCGate(lcGate) {}
    fun deleteLCGate(lcGate: LCGate) = repo.deleteLCGate(lcGate) {}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterDataScreen(
    onBack: () -> Unit,
    viewModel: MasterDataViewModel = viewModel()
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Stations", "Sections", "LC Gates")

    val stations by viewModel.stations.collectAsState()
    val sections by viewModel.sections.collectAsState()
    val lcGates by viewModel.lcGates.collectAsState()

    var showStationDialog by remember { mutableStateOf(false) }
    var editingStation by remember { mutableStateOf<Station?>(null) }
    var stationToDelete by remember { mutableStateOf<Station?>(null) }

    var showSectionDialog by remember { mutableStateOf(false) }
    var editingSection by remember { mutableStateOf<Section?>(null) }
    var sectionToDelete by remember { mutableStateOf<Section?>(null) }

    var showLCGateDialog by remember { mutableStateOf(false) }
    var editingLCGate by remember { mutableStateOf<LCGate?>(null) }
    var lcGateToDelete by remember { mutableStateOf<LCGate?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Master Data Management") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
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
            FloatingActionButton(onClick = {
                when (selectedTabIndex) {
                    0 -> { editingStation = null; showStationDialog = true }
                    1 -> { editingSection = null; showSectionDialog = true }
                    2 -> { editingLCGate = null; showLCGateDialog = true }
                }
            }) {
                Icon(Icons.Default.Add, "Add")
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (selectedTabIndex) {
                    0 -> StationsList(
                        stations = stations,
                        onEdit = { editingStation = it; showStationDialog = true },
                        onDelete = { stationToDelete = it }
                    )
                    1 -> SectionsList(
                        sections = sections,
                        onEdit = { editingSection = it; showSectionDialog = true },
                        onDelete = { sectionToDelete = it }
                    )
                    2 -> LCGatesList(
                        lcGates = lcGates,
                        onEdit = { editingLCGate = it; showLCGateDialog = true },
                        onDelete = { lcGateToDelete = it }
                    )
                }
            }
        }

        // Station Dialogs
        if (showStationDialog) {
            StationFormDialog(
                station = editingStation,
                onDismiss = { showStationDialog = false },
                onSave = {
                    viewModel.saveStation(it)
                    showStationDialog = false
                }
            )
        }
        stationToDelete?.let { st ->
            DeleteConfirmDialog(
                title = "Delete Station",
                text = "Are you sure you want to delete ${st.stationName}?",
                onConfirm = { viewModel.deleteStation(st); stationToDelete = null },
                onDismiss = { stationToDelete = null }
            )
        }

        // Section Dialogs
        if (showSectionDialog) {
            SectionFormDialog(
                section = editingSection,
                onDismiss = { showSectionDialog = false },
                onSave = {
                    viewModel.saveSection(it)
                    showSectionDialog = false
                }
            )
        }
        sectionToDelete?.let { sec ->
            DeleteConfirmDialog(
                title = "Delete Section",
                text = "Are you sure you want to delete ${sec.sectionName}?",
                onConfirm = { viewModel.deleteSection(sec); sectionToDelete = null },
                onDismiss = { sectionToDelete = null }
            )
        }

        // LCGate Dialogs
        if (showLCGateDialog) {
            LCGateFormDialog(
                lcGate = editingLCGate,
                onDismiss = { showLCGateDialog = false },
                onSave = {
                    viewModel.saveLCGate(it)
                    showLCGateDialog = false
                }
            )
        }
        lcGateToDelete?.let { gate ->
            DeleteConfirmDialog(
                title = "Delete LC Gate",
                text = "Are you sure you want to delete ${gate.lcNumber}?",
                onConfirm = { viewModel.deleteLCGate(gate); lcGateToDelete = null },
                onDismiss = { lcGateToDelete = null }
            )
        }
    }
}

@Composable
fun StationsList(stations: List<Station>, onEdit: (Station) -> Unit, onDelete: (Station) -> Unit) {
    if (stations.isEmpty()) {
        EmptyStateMessage("No stations found.")
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(stations) { station ->
                MasterDataCard(
                    title = station.stationName,
                    subtitle = "Code: ${station.stationCode} | Section: ${station.section}",
                    onEdit = { onEdit(station) },
                    onDelete = { onDelete(station) }
                )
            }
        }
    }
}

@Composable
fun SectionsList(sections: List<Section>, onEdit: (Section) -> Unit, onDelete: (Section) -> Unit) {
    if (sections.isEmpty()) {
        EmptyStateMessage("No sections found.")
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sections) { section ->
                MasterDataCard(
                    title = section.sectionName,
                    subtitle = "From: ${section.fromLocation} | To: ${section.toLocation}",
                    onEdit = { onEdit(section) },
                    onDelete = { onDelete(section) }
                )
            }
        }
    }
}

@Composable
fun LCGatesList(lcGates: List<LCGate>, onEdit: (LCGate) -> Unit, onDelete: (LCGate) -> Unit) {
    if (lcGates.isEmpty()) {
        EmptyStateMessage("No LC Gates found.")
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(lcGates) { gate ->
                MasterDataCard(
                    title = gate.lcNumber,
                    subtitle = "Type: ${gate.gateType} | Section: ${gate.section} | Loc: ${gate.location}",
                    onEdit = { onEdit(gate) },
                    onDelete = { onDelete(gate) }
                )
            }
        }
    }
}

@Composable
fun EmptyStateMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun MasterDataCard(title: String, subtitle: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun DeleteConfirmDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun StationFormDialog(station: Station?, onDismiss: () -> Unit, onSave: (Station) -> Unit) {
    var name by remember { mutableStateOf(station?.stationName ?: "") }
    var code by remember { mutableStateOf(station?.stationCode ?: "") }
    var section by remember { mutableStateOf(station?.section ?: "") }
    var division by remember { mutableStateOf(station?.division ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (station == null) "Add Station" else "Edit Station") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Station Name") }, singleLine = true)
                OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Station Code") }, singleLine = true)
                OutlinedTextField(value = section, onValueChange = { section = it }, label = { Text("Section") }, singleLine = true)
                OutlinedTextField(value = division, onValueChange = { division = it }, label = { Text("Division") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank() && code.isNotBlank()) {
                    onSave((station ?: Station()).copy(stationName = name, stationCode = code, section = section, division = division))
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun SectionFormDialog(section: Section?, onDismiss: () -> Unit, onSave: (Section) -> Unit) {
    var name by remember { mutableStateOf(section?.sectionName ?: "") }
    var fromLoc by remember { mutableStateOf(section?.fromLocation ?: "") }
    var toLoc by remember { mutableStateOf(section?.toLocation ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (section == null) "Add Section" else "Edit Section") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Section Name") }, singleLine = true)
                OutlinedTextField(value = fromLoc, onValueChange = { fromLoc = it }, label = { Text("From Location") }, singleLine = true)
                OutlinedTextField(value = toLoc, onValueChange = { toLoc = it }, label = { Text("To Location") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    onSave((section ?: Section()).copy(sectionName = name, fromLocation = fromLoc, toLocation = toLoc))
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun LCGateFormDialog(lcGate: LCGate?, onDismiss: () -> Unit, onSave: (LCGate) -> Unit) {
    var number by remember { mutableStateOf(lcGate?.lcNumber ?: "") }
    var type by remember { mutableStateOf(lcGate?.gateType ?: "") }
    var section by remember { mutableStateOf(lcGate?.section ?: "") }
    var location by remember { mutableStateOf(lcGate?.location ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (lcGate == null) "Add LC Gate" else "Edit LC Gate") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(value = number, onValueChange = { number = it }, label = { Text("LC Gate Number") }, singleLine = true)
                OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("Gate Type") }, singleLine = true)
                OutlinedTextField(value = section, onValueChange = { section = it }, label = { Text("Section") }, singleLine = true)
                OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Location (KM)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (number.isNotBlank()) {
                    onSave((lcGate ?: LCGate()).copy(lcNumber = number, gateType = type, section = section, location = location))
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
