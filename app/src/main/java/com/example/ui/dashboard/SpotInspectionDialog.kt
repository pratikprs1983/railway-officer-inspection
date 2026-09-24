package com.example.ui.dashboard

import android.Manifest
import android.location.Location
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.models.InspectionType
import com.example.models.LCGate
import com.example.models.Section
import com.example.models.Station
import com.example.ui.admin.AddNewMasterLocationDialog
import com.example.ui.admin.LocationCategory
import com.example.ui.admin.detectLocationCategory
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotInspectionDialog(
    types: List<InspectionType>,
    stations: List<Station>,
    sections: List<Section>,
    lcGates: List<LCGate>,
    onDismiss: () -> Unit,
    onAddStation: (Station, (Station) -> Unit) -> Unit,
    onAddSection: (Section, (Section) -> Unit) -> Unit,
    onAddLCGate: (LCGate, (LCGate) -> Unit) -> Unit,
    onSaveInspection: (
        type: InspectionType,
        locationName: String,
        locationId: String,
        isSelfInspection: Boolean,
        remarks: String,
        executionDate: Long,
        selfieUrl: String,
        startLat: Double?,
        startLng: Double?,
        startAcc: Float?,
        startNow: Boolean
    ) -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    var isSelfInspection by remember { mutableStateOf(false) } // false = Spot Inspection, true = Self Inspection
    var selectedType by remember { mutableStateOf<InspectionType?>(types.firstOrNull()) }
    var typeExpanded by remember { mutableStateOf(false) }
    var typeSearchQuery by remember { mutableStateOf("") }

    var activeCategory by remember(selectedType) {
        mutableStateOf(detectLocationCategory(selectedType?.name))
    }

    var selectedLocationName by remember { mutableStateOf("") }
    var selectedLocationId by remember { mutableStateOf("") }
    var specificSpotDetail by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }

    var locationExpanded by remember { mutableStateOf(false) }
    var locationSearchQuery by remember { mutableStateOf("") }
    var showAddMasterLocationDialog by remember { mutableStateOf(false) }

    // Date (Default today)
    var executionDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Real-time GPS Location
    var isFetchingLocation by remember { mutableStateOf(false) }
    var locationCaptured by remember { mutableStateOf<Location?>(null) }
    val locationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            isFetchingLocation = true
            try {
                locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { loc ->
                        isFetchingLocation = false
                        locationCaptured = loc
                    }
                    .addOnFailureListener {
                        isFetchingLocation = false
                    }
            } catch (e: SecurityException) {
                isFetchingLocation = false
            }
        }
    }

    // Officer Selfie
    var selfieUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        // selfieUri is retained if picture successfully taken
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            val photoFile = File(context.cacheDir, "spot_selfie_${UUID.randomUUID()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
            selfieUri = uri
            cameraLauncher.launch(uri)
        }
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
                    .heightIn(max = 780.dp)
                    .padding(16.dp),
                shape = MaterialTheme.shapes.large,
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(22.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelfInspection) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isSelfInspection) Icons.Default.AssignmentInd else Icons.Default.FlashOn,
                                        contentDescription = null,
                                        tint = if (isSelfInspection) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isSelfInspection) "Self Inspection" else "Spot Inspection",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isSelfInspection) "Officer-initiated self audit" else "Immediate unscheduled spot inspection",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    // Inspection Nature Toggle (Spot vs Self Inspection)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Select Inspection Option *",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = !isSelfInspection,
                                onClick = { isSelfInspection = false },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.FlashOn,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text("Spot Inspection", fontWeight = if (!isSelfInspection) FontWeight.Bold else FontWeight.Normal) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = isSelfInspection,
                                onClick = { isSelfInspection = true },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.AssignmentInd,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text("Self Inspection", fontWeight = if (isSelfInspection) FontWeight.Bold else FontWeight.Normal) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 1. Inspection Type Selector (Chosen by the officer himself)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "1. Inspection Type (Select by yourself) *",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        ExposedDropdownMenuBox(
                            expanded = typeExpanded,
                            onExpandedChange = { typeExpanded = !typeExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedType?.name ?: "Select Inspection Type",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Choose Inspection Type *") },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                    .fillMaxWidth(),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                                leadingIcon = {
                                    Icon(Icons.Default.Assignment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
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
                                        text = { Text("No matching inspection types") },
                                        onClick = {}
                                    )
                                } else {
                                    filteredTypes.forEach { type ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(type.name, fontWeight = FontWeight.SemiBold)
                                                    if (type.description.isNotBlank()) {
                                                        Text(type.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
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
                    }

                    // 2. Date Field (Default Today)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        "Inspection Date (Default Today)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        dateFormat.format(Date(executionDate)),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                val isToday = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(executionDate)) ==
                                        SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
                                FilterChip(
                                    selected = isToday,
                                    onClick = { executionDate = System.currentTimeMillis() },
                                    label = { Text("Today", style = MaterialTheme.typography.labelSmall) }
                                )
                                IconButton(
                                    onClick = { showDatePicker = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.EditCalendar, contentDescription = "Change Date", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }

                    // 3. Location Field: Searching or Adding place in Station, Section, or LC Gate (Saved to Master Data)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "2. Location (Master Data Search or Add) *",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            // Quick button to add new location into master data
                            TextButton(
                                onClick = { showAddMasterLocationDialog = true },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.AddLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = when (activeCategory) {
                                        LocationCategory.STATION -> "+ Add Station"
                                        LocationCategory.SECTION -> "+ Add Section"
                                        LocationCategory.LC_GATE -> "+ Add LC Gate"
                                        else -> "+ Add Place"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Category tabs: Stations, Sections, LC Gates
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = activeCategory == LocationCategory.STATION,
                                onClick = { activeCategory = LocationCategory.STATION },
                                leadingIcon = { Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                label = { Text("Station (${stations.size})", style = MaterialTheme.typography.labelSmall) }
                            )
                            FilterChip(
                                selected = activeCategory == LocationCategory.SECTION,
                                onClick = { activeCategory = LocationCategory.SECTION },
                                leadingIcon = { Icon(Icons.Default.Train, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                label = { Text("Section (${sections.size})", style = MaterialTheme.typography.labelSmall) }
                            )
                            FilterChip(
                                selected = activeCategory == LocationCategory.LC_GATE,
                                onClick = { activeCategory = LocationCategory.LC_GATE },
                                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                label = { Text("LC Gate (${lcGates.size})", style = MaterialTheme.typography.labelSmall) }
                            )
                        }

                        // Location Dropdown with Real-Time Search & Add to Master Data Option
                        ExposedDropdownMenuBox(
                            expanded = locationExpanded,
                            onExpandedChange = { locationExpanded = !locationExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedLocationName.ifEmpty { "Search or select location *" },
                                onValueChange = {},
                                readOnly = true,
                                label = {
                                    Text(
                                        when (activeCategory) {
                                            LocationCategory.STATION -> "Select / Search Station *"
                                            LocationCategory.SECTION -> "Select / Search Section *"
                                            LocationCategory.LC_GATE -> "Select / Search LC Gate *"
                                            else -> "Select Location *"
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
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                    .fillMaxWidth()
                            )

                            ExposedDropdownMenu(
                                expanded = locationExpanded,
                                onDismissRequest = {
                                    locationExpanded = false
                                    locationSearchQuery = ""
                                },
                                modifier = Modifier.heightIn(max = 360.dp)
                            ) {
                                OutlinedTextField(
                                    value = locationSearchQuery,
                                    onValueChange = { locationSearchQuery = it },
                                    placeholder = { Text("Type to search place...", style = MaterialTheme.typography.bodySmall) },
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

                                // Add New to Master Data item at the top of menu
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.AddCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    if (locationSearchQuery.isNotBlank()) {
                                                        "+ Add \"${locationSearchQuery.trim()}\" to Master Data"
                                                    } else {
                                                        "+ Add New ${when (activeCategory) {
                                                            LocationCategory.STATION -> "Station"
                                                            LocationCategory.SECTION -> "Section"
                                                            LocationCategory.LC_GATE -> "LC Gate"
                                                            else -> "Location"
                                                        }} to Master Data"
                                                    },
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    "Saves permanently to Master Data for all officers",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        locationExpanded = false
                                        showAddMasterLocationDialog = true
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
                                                    it.division.lowercase().contains(q)
                                                }
                                            }
                                        }

                                        if (filteredStations.isEmpty()) {
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text("No existing stations match \"$locationSearchQuery\"")
                                                        Text("Tap above to add it to Master Data!", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                                                    }
                                                },
                                                onClick = {
                                                    locationExpanded = false
                                                    showAddMasterLocationDialog = true
                                                }
                                            )
                                        } else {
                                            filteredStations.forEach { station ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Column {
                                                                Text(station.stationName, fontWeight = FontWeight.SemiBold)
                                                                Text(
                                                                    "Division: ${station.division.ifEmpty { "HQ" }}",
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                            }
                                                            Surface(
                                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                                shape = RoundedCornerShape(4.dp)
                                                            ) {
                                                                Text(
                                                                    station.stationCode,
                                                                    fontWeight = FontWeight.Bold,
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
                                                    Column {
                                                        Text("No existing sections match \"$locationSearchQuery\"")
                                                        Text("Tap above to add it to Master Data!", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                                                    }
                                                },
                                                onClick = {
                                                    locationExpanded = false
                                                    showAddMasterLocationDialog = true
                                                }
                                            )
                                        } else {
                                            filteredSections.forEach { section ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Column {
                                                            Text(section.sectionName, fontWeight = FontWeight.SemiBold)
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
                                                    it.location.lowercase().contains(q)
                                                }
                                            }
                                        }

                                        if (filteredLCGates.isEmpty()) {
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text("No existing LC gates match \"$locationSearchQuery\"")
                                                        Text("Tap above to add it to Master Data!", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                                                    }
                                                },
                                                onClick = {
                                                    locationExpanded = false
                                                    showAddMasterLocationDialog = true
                                                }
                                            )
                                        } else {
                                            filteredLCGates.forEach { gate ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Column {
                                                            Text(gate.lcNumber, fontWeight = FontWeight.SemiBold)
                                                            Text(
                                                                "${gate.gateType} • ${gate.location}",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    },
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
                                    else -> {}
                                }
                            }
                        }

                        // Specific Spot / Sub-location detail (optional)
                        OutlinedTextField(
                            value = specificSpotDetail,
                            onValueChange = { specificSpotDetail = it },
                            label = { Text("Specific Spot / Landmark (Optional)") },
                            placeholder = { Text("e.g. Platform 1, Signal Post #4, Gate approach, Yard Line 3") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // 4. Officer Selfie Capture (Same as Assigned Inspection)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selfieUri != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selfieUri != null) {
                                Box {
                                    AsyncImage(
                                        model = selfieUri,
                                        contentDescription = "Officer Selfie",
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFF2E7D32),
                                        modifier = Modifier
                                            .size(18.dp)
                                            .align(Alignment.BottomEnd)
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.padding(2.dp)
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    modifier = Modifier.size(54.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.PhotoCameraFront,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Take Officer Selfie",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (selfieUri != null) "Verified physical presence selfie captured" else "Required verification selfie",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (selfieUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                colors = if (selfieUri != null) ButtonDefaults.outlinedButtonColors() else ButtonDefaults.buttonColors()
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (selfieUri == null) "Take" else "Retake")
                            }
                        }
                    }

                    // 5. Add Location / Real-Time GPS (Same as Assigned Inspection)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (locationCaptured != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (locationCaptured != null) Color(0xFF2E7D32).copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                modifier = Modifier.size(50.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = if (locationCaptured != null) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Add Location (GPS)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (isFetchingLocation) {
                                    Text("Acquiring GPS coordinates...", style = MaterialTheme.typography.bodySmall)
                                } else if (locationCaptured != null) {
                                    Text(
                                        "Lat: ${String.format(Locale.US, "%.5f", locationCaptured!!.latitude)}, Lng: ${String.format(Locale.US, "%.5f", locationCaptured!!.longitude)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Accuracy: ±${locationCaptured!!.accuracy}m",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text("Geotag current physical location", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            if (isFetchingLocation) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Button(
                                    onClick = {
                                        locationPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    },
                                    colors = if (locationCaptured != null) ButtonDefaults.outlinedButtonColors() else ButtonDefaults.buttonColors()
                                ) {
                                    Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (locationCaptured == null) "Get GPS" else "Refresh")
                                }
                            }
                        }
                    }

                    // 6. Remarks / Reason Field (Optional)
                    OutlinedTextField(
                        value = remarks,
                        onValueChange = { remarks = it },
                        label = { Text("Spot Inspection Objective / Remarks (Optional)") },
                        placeholder = { Text("e.g. Unscheduled night check, passenger complaint check, monsoon drive") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 3
                    )

                    val resolvedLocation = selectedLocationName.trim().let {
                        if (specificSpotDetail.isNotBlank()) {
                            if (it.isNotBlank()) "$it - ${specificSpotDetail.trim()}" else specificSpotDetail.trim()
                        } else it
                    }

                    val canSave = selectedType != null && resolvedLocation.isNotBlank()

                    Spacer(modifier = Modifier.height(4.dp))

                    // Action Buttons: Save to List vs Start Now
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        OutlinedButton(
                            onClick = {
                                if (canSave) {
                                    onSaveInspection(
                                        selectedType!!,
                                        resolvedLocation,
                                        selectedLocationId,
                                        isSelfInspection,
                                        remarks,
                                        executionDate,
                                        selfieUri?.toString() ?: "",
                                        locationCaptured?.latitude,
                                        locationCaptured?.longitude,
                                        locationCaptured?.accuracy,
                                        false // Save only (Status ASSIGNED)
                                    )
                                }
                            },
                            enabled = canSave
                        ) {
                            Text("Save to List")
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = {
                                if (canSave) {
                                    onSaveInspection(
                                        selectedType!!,
                                        resolvedLocation,
                                        selectedLocationId,
                                        isSelfInspection,
                                        remarks,
                                        executionDate,
                                        selfieUri?.toString() ?: "",
                                        locationCaptured?.latitude,
                                        locationCaptured?.longitude,
                                        locationCaptured?.accuracy,
                                        true // Start Now directly into inspection!
                                    )
                                }
                            },
                            enabled = canSave,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelfInspection) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Start Now")
                        }
                    }
                }
            }
        }
    }

    // Modal to add a new location into Master Data on-the-fly and save permanently
    if (showAddMasterLocationDialog) {
        AddNewMasterLocationDialog(
            initialCategory = activeCategory,
            initialName = locationSearchQuery.trim(),
            onDismiss = { showAddMasterLocationDialog = false },
            onSaveStation = { station ->
                onAddStation(station) { savedStation ->
                    selectedLocationName = "${savedStation.stationName} (${savedStation.stationCode})"
                    selectedLocationId = savedStation.id
                    locationSearchQuery = ""
                    showAddMasterLocationDialog = false
                }
            },
            onSaveSection = { section ->
                onAddSection(section) { savedSection ->
                    selectedLocationName = savedSection.sectionName
                    selectedLocationId = savedSection.id
                    locationSearchQuery = ""
                    showAddMasterLocationDialog = false
                }
            },
            onSaveLCGate = { gate ->
                onAddLCGate(gate) { savedGate ->
                    selectedLocationName = "${savedGate.lcNumber} (${savedGate.gateType})"
                    selectedLocationId = savedGate.id
                    locationSearchQuery = ""
                    showAddMasterLocationDialog = false
                }
            }
        )
    }

    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = executionDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        executionDate = it
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
