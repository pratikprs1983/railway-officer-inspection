package com.example.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.models.Compliance
import com.example.models.ComplianceSeverity
import com.example.models.ComplianceStatus
import com.example.models.Inspection
import com.example.models.InspectionStatus
import com.example.models.Role
import com.example.models.User
import com.example.ui.compliance.ComplianceActionDialog
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
import com.example.ui.common.ComplianceDossierPreviewDialog
import com.example.util.InspectionExcelExporter
import com.example.util.InspectionWordExporter
import com.example.util.ComplianceWordExporter
import com.example.data.FirebaseSyncManager

enum class ComplianceViewMode {
    GROUPED_BY_INSPECTION,
    FLAT_LIST
}

data class InspectionComplianceGroup(
    val inspectionKey: String,
    val inspectionNumber: String,
    val inspectionTypeName: String,
    val locationName: String,
    val inspectingOfficerName: String,
    val inspectionDateMillis: Long,
    val isSpotInspection: Boolean,
    val compliances: List<Compliance>,
    val parentInspection: Inspection? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onLogout: () -> Unit,
    onNavigateToInspection: (String) -> Unit = {},
    onSwitchToAdmin: (() -> Unit)? = null,
    onNavigateToUserManagement: (() -> Unit)? = null,
    onNavigateToMasterData: (() -> Unit)? = null,
    onNavigateToFormBuilder: (() -> Unit)? = null,
    onNavigateToAssignInspection: (() -> Unit)? = null,
    onNavigateToAdminCompliances: (() -> Unit)? = null,
    onNavigateToAdminReports: (() -> Unit)? = null,
    initialTab: Int = 0,
    viewModel: DashboardViewModel = viewModel()
) {
    val inspections by viewModel.inspections.collectAsState()
    val allInspections by viewModel.allInspections.collectAsState()
    val assignedCompliances by viewModel.assignedCompliances.collectAsState()
    val allCompliances by viewModel.allCompliances.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val authenticatedUser by viewModel.authenticatedUser.collectAsState()
    val officers by viewModel.officers.collectAsState()
    val types by viewModel.types.collectAsState()
    val stations by viewModel.stations.collectAsState()
    val sections by viewModel.sections.collectAsState()
    val lcGates by viewModel.lcGates.collectAsState()

    val isAdminRole = remember(currentUser, authenticatedUser) {
        currentUser?.role == Role.ADMIN || currentUser?.role == Role.SUPER_ADMIN ||
        authenticatedUser?.role == Role.ADMIN || authenticatedUser?.role == Role.SUPER_ADMIN
    }

    val context = LocalContext.current
    var selectedTab by remember(initialTab) { mutableStateOf(initialTab) } // 0: My Inspections, 1: Compliances Assigned to Me
    var selectedComplianceStatusFilter by remember { mutableStateOf<ComplianceStatus?>(null) }
    var complianceSearchQuery by remember { mutableStateOf("") }
    var complianceViewMode by remember { mutableStateOf(ComplianceViewMode.GROUPED_BY_INSPECTION) }
    var allGroupsExpanded by remember { mutableStateOf(true) }
    var showOfficerMenu by remember { mutableStateOf(false) }
    var showSwitchOfficerDialog by remember { mutableStateOf(false) }
    var showSpotInspectionDialog by remember { mutableStateOf(false) }
    var showExportExcelDialog by remember { mutableStateOf(false) }
    var showExportWordDialog by remember { mutableStateOf(false) }
    var showWordDossierPreview by remember { mutableStateOf(false) }
    var showExcelRegisterPreview by remember { mutableStateOf(false) }
    var showComplianceDossierPreview by remember { mutableStateOf(false) }
    var previewInspectionsList by remember { mutableStateOf<List<Inspection>>(emptyList()) }
    var activeComplianceForAction by remember { mutableStateOf<Compliance?>(null) }
    var inspectionFilterCriteria by remember { mutableStateOf(InspectionFilterCriteria()) }

    val filteredInspections = remember(inspections, inspectionFilterCriteria) {
        inspections.applyInspectionFilters(inspectionFilterCriteria)
    }

    val inspectionMap = remember(allInspections) {
        val map = mutableMapOf<String, Inspection>()
        allInspections.forEach { insp ->
            if (insp.id.isNotBlank()) map[insp.id] = insp
            if (insp.inspectionNumber.isNotBlank()) map[insp.inspectionNumber] = insp
        }
        map
    }

    val filteredCompliances = remember(assignedCompliances, selectedComplianceStatusFilter, complianceSearchQuery) {
        assignedCompliances.filter { comp ->
            (selectedComplianceStatusFilter == null || comp.status == selectedComplianceStatusFilter) &&
            (complianceSearchQuery.isBlank() ||
             comp.inspectionNumber.contains(complianceSearchQuery, ignoreCase = true) ||
             comp.inspectionPointTitle.contains(complianceSearchQuery, ignoreCase = true) ||
             comp.locationName.contains(complianceSearchQuery, ignoreCase = true) ||
             comp.inspectingOfficerName.contains(complianceSearchQuery, ignoreCase = true) ||
             comp.description.contains(complianceSearchQuery, ignoreCase = true))
        }
    }

    val groupedCompliances = remember(filteredCompliances, inspectionMap) {
        filteredCompliances.groupBy { comp ->
            comp.inspectionNumber.ifBlank { comp.inspectionId.ifBlank { "INSP-GENERAL" } }
        }.map { (key, compList) ->
            val firstComp = compList.first()
            val parent = inspectionMap[firstComp.inspectionId] ?: inspectionMap[firstComp.inspectionNumber] ?: inspectionMap[key]
            
            val inspNum = parent?.inspectionNumber?.ifBlank { firstComp.inspectionNumber } 
                ?: firstComp.inspectionNumber.ifBlank { key }
            val inspType = parent?.inspectionTypeName?.ifBlank { "Safety Inspection" } ?: "Safety Inspection"
            val locName = parent?.locationName?.ifBlank { firstComp.locationName } ?: firstComp.locationName.ifBlank { "Location unspecified" }
            val officerName = parent?.assignedOfficerName?.ifBlank { firstComp.inspectingOfficerName } 
                ?: firstComp.inspectingOfficerName.ifBlank { "Inspecting Officer" }
            val dateMillis = parent?.executionDate 
                ?: parent?.startedAt 
                ?: parent?.scheduledDate 
                ?: firstComp.createdAt
            val isSpot = parent?.isSpotInspection ?: false

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

    val assignedCount = inspections.count { !it.isSpotInspection }
    val spotCount = inspections.count { it.isSpotInspection }
    val inProgressCount = inspections.count { it.status == InspectionStatus.IN_PROGRESS }
    val completedCount = inspections.count { it.status == InspectionStatus.COMPLETED }

    val pendingCompliancesCount = assignedCompliances.count { it.status == ComplianceStatus.OPEN }
    val inProgressCompliancesCount = assignedCompliances.count { it.status == ComplianceStatus.UNDER_ACTION }
    val resolvedCompliancesCount = assignedCompliances.count { it.status == ComplianceStatus.RESOLVED || it.status == ComplianceStatus.CLOSED }

    val activeUserName = currentUser?.name ?: "Field Officer"

    val notifRepo = remember { com.example.data.NotificationRepository() }
    val notifications by notifRepo.getAllNotifications().collectAsState(initial = emptyList())
    val currentUserId = currentUser?.uid ?: ""
    val unreadNotifsCount = remember(notifications, currentUserId) {
        notifications.count { notif ->
            !notif.isRead && (
                com.example.data.SessionManager.isAdmin() ||
                notif.recipientOfficerId.isBlank() ||
                notif.recipientOfficerId == "ALL" ||
                notif.recipientOfficerId == "ADMIN" ||
                notif.recipientOfficerId == currentUserId
            )
        }
    }
    var showNotificationCenter by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Officer Portal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "$activeUserName • Dual-Role Workspace",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(
                        onClick = { showNotificationCenter = true },
                        modifier = Modifier.testTag("officer_notifications_btn")
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadNotifsCount > 0) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ) {
                                        Text("$unreadNotifsCount")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                if (unreadNotifsCount > 0) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                                contentDescription = "Notifications",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    IconButton(onClick = { showSpotInspectionDialog = true }) {
                        Icon(Icons.Default.AddLocationAlt, contentDescription = "Spot / Self Inspection", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    if (isAdminRole) {
                        IconButton(
                            onClick = { showSwitchOfficerDialog = true },
                            modifier = Modifier.testTag("topbar_switch_officer_btn")
                        ) {
                            Icon(Icons.Default.SwitchAccount, contentDescription = "Switch Profile", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    if (isAdminRole && onSwitchToAdmin != null) {
                        IconButton(onClick = onSwitchToAdmin) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = "Admin Dashboard", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Logout", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                ExtendedFloatingActionButton(
                    onClick = { showSpotInspectionDialog = true },
                    icon = { Icon(Icons.Default.AddLocationAlt, contentDescription = null) },
                    text = { Text("Spot / Self Inspection") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("dashboard_bottom_navigation"),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.testTag("bottom_tab_inspections"),
                    icon = {
                        BadgedBox(
                            badge = {
                                if (inspections.isNotEmpty()) {
                                    Badge(
                                        containerColor = if (selectedTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ) {
                                        Text("${inspections.size}")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Assignment,
                                contentDescription = "Inspections"
                            )
                        }
                    },
                    label = {
                        Text(
                            "Inspections",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.testTag("bottom_tab_compliances"),
                    icon = {
                        BadgedBox(
                            badge = {
                                if (pendingCompliancesCount > 0) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ) {
                                        Text("$pendingCompliancesCount")
                                    }
                                } else if (assignedCompliances.isNotEmpty()) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ) {
                                        Text("${assignedCompliances.size}")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.PendingActions,
                                contentDescription = "Compliances"
                            )
                        }
                    },
                    label = {
                        Text(
                            "Compliances",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    modifier = Modifier.testTag("bottom_tab_reports"),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Assessment,
                            contentDescription = "Reports & Dossiers"
                        )
                    },
                    label = {
                        Text(
                            "Reports",
                            fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )

                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    modifier = Modifier.testTag("bottom_tab_admin_profile"),
                    icon = {
                        Icon(
                            imageVector = if (isAdminRole) Icons.Default.AdminPanelSettings else Icons.Default.AccountCircle,
                            contentDescription = if (isAdminRole) "Admin Hub" else "Profile"
                        )
                    },
                    label = {
                        Text(
                            if (isAdminRole) "Admin Hub" else "Profile",
                            fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Active Officer Identity Banner
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = isAdminRole) { showSwitchOfficerDialog = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    activeUserName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = if (currentUser?.role == Role.ADMIN || currentUser?.role == Role.SUPER_ADMIN) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                                    shape = MaterialTheme.shapes.extraSmall
                                ) {
                                    Text(
                                        text = when (currentUser?.role) {
                                            Role.SUPER_ADMIN -> "SUPER ADMIN"
                                            Role.ADMIN -> "ADMIN"
                                            else -> "OFFICER"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = "${currentUser?.designation?.ifEmpty { "Inspecting Officer" } ?: "Inspecting Officer"} • ${currentUser?.departmentName?.ifEmpty { "Field Operations" } ?: "Field Operations"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Profile Switcher (Switch between officers to test dual tasks - Strictly for ADMIN role only)
                    if (isAdminRole) {
                        FilledTonalButton(
                            onClick = { showSwitchOfficerDialog = true },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("switch_officer_button")
                        ) {
                            Icon(Icons.Default.SwitchAccount, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Switch", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            when (selectedTab) {
                0 -> {
                    // TAB 0: My Inspections & Monitored Compliances
                    // Quick Officer Metric Banner
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        OfficerMetric("Assigned", assignedCount.toString(), MaterialTheme.colorScheme.error)
                        OfficerMetric("Spot/Self", spotCount.toString(), MaterialTheme.colorScheme.tertiary)
                        OfficerMetric("In Progress", inProgressCount.toString(), MaterialTheme.colorScheme.primary)
                        OfficerMetric("Completed", completedCount.toString(), Color(0xFF2E7D32))
                    }
                }

                // Enhanced Filter Bar (Officer-wise, Type-wise, Date-wise, Category, Status)
                InspectionFilterBar(
                    criteria = inspectionFilterCriteria,
                    onCriteriaChange = { inspectionFilterCriteria = it },
                    officers = officers,
                    types = types,
                    showOfficerFilter = isAdminRole && officers.size > 1,
                    totalCount = inspections.size,
                    filteredCount = filteredInspections.size,
                    onExportExcel = { showExportExcelDialog = true },
                    onExportWord = { showExportWordDialog = true },
                    onViewDossier = {
                        previewInspectionsList = filteredInspections
                        showWordDossierPreview = true
                    },
                    onViewRegister = {
                        previewInspectionsList = filteredInspections
                        showExcelRegisterPreview = true
                    }
                )

                if (filteredInspections.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Assignment,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                if (inspections.isEmpty()) "No inspections found for $activeUserName." else "No matching inspections found.",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "You can perform an on-demand Spot Inspection or Self Inspection anytime.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { showSpotInspectionDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.AddLocationAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Start Spot / Self Inspection")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredInspections, key = { it.id }) { inspection ->
                            // Calculate compliances related to this inspection
                            val inspCompliances = allCompliances.filter { it.inspectionId == inspection.id }
                            InspectionCard(
                                inspection = inspection,
                                compliancesCount = inspCompliances.size,
                                pendingCompliancesCount = inspCompliances.count { it.status == ComplianceStatus.OPEN || it.status == ComplianceStatus.UNDER_ACTION },
                                onClick = { onNavigateToInspection(inspection.id) }
                            )
                        }
                    }
                }
            }
            1 -> {
                // TAB 1: Compliances Assigned to Me (Action Officer Role)
                // Metric Banner for Compliances
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            OfficerMetric("Pending", pendingCompliancesCount.toString(), MaterialTheme.colorScheme.error)
                            OfficerMetric("In Progress", inProgressCompliancesCount.toString(), MaterialTheme.colorScheme.tertiary)
                            OfficerMetric("Resolved", resolvedCompliancesCount.toString(), Color(0xFF2E7D32))
                        }

                        if (assignedCompliances.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "${assignedCompliances.size} total compliance action items across ${groupedCompliances.size} inspection dossiers",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Search Bar & Filter Controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = complianceSearchQuery,
                        onValueChange = { complianceSearchQuery = it },
                        placeholder = { Text("Search point, inspection #, officer, location") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (complianceSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { complianceSearchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Status Chips
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = selectedComplianceStatusFilter == null,
                                onClick = { selectedComplianceStatusFilter = null },
                                label = { Text("All (${assignedCompliances.size})") }
                            )
                        }
                        items(listOf(ComplianceStatus.OPEN, ComplianceStatus.UNDER_ACTION, ComplianceStatus.RESOLVED)) { status ->
                            val count = assignedCompliances.count { it.status == status }
                            FilterChip(
                                selected = selectedComplianceStatusFilter == status,
                                onClick = { selectedComplianceStatusFilter = if (selectedComplianceStatusFilter == status) null else status },
                                label = { Text("${status.name.replace("_", " ")} ($count)") }
                            )
                        }
                    }

                    // View Mode Toggle & Expand/Collapse Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = complianceViewMode == ComplianceViewMode.GROUPED_BY_INSPECTION,
                                onClick = { complianceViewMode = ComplianceViewMode.GROUPED_BY_INSPECTION },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Folder,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text("By Inspection (${groupedCompliances.size})") }
                            )

                            FilterChip(
                                selected = complianceViewMode == ComplianceViewMode.FLAT_LIST,
                                onClick = { complianceViewMode = ComplianceViewMode.FLAT_LIST },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.List,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text("Flat List (${filteredCompliances.size})") }
                            )
                        }

                        if (complianceViewMode == ComplianceViewMode.GROUPED_BY_INSPECTION && groupedCompliances.isNotEmpty()) {
                            TextButton(
                                onClick = { allGroupsExpanded = !allGroupsExpanded },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    if (allGroupsExpanded) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    if (allGroupsExpanded) "Collapse All" else "Expand All",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }

                    if (groupedCompliances.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${groupedCompliances.size} Inspection Dossier${if (groupedCompliances.size > 1) "s" else ""}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            FilledTonalButton(
                                onClick = {
                                    val file = ComplianceWordExporter.exportAllCompliancesToWord(
                                        context = context,
                                        groups = groupedCompliances,
                                        officerName = activeUserName
                                    )
                                    if (file != null) {
                                        ComplianceWordExporter.openOrShareWordFile(
                                            context = context,
                                            file = file,
                                            title = "All Assigned Compliances Word Dossier"
                                        )
                                    }
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFF1E3A8A).copy(alpha = 0.12f),
                                    contentColor = Color(0xFF1E3A8A)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Download All Word Dossier",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                if (filteredCompliances.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                if (assignedCompliances.isEmpty()) "No compliance points assigned to you!" else "No matching compliances found.",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "When inspecting officers mark checklist observations for your department, they will appear grouped by inspection here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else {
                    if (complianceViewMode == ComplianceViewMode.GROUPED_BY_INSPECTION) {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(groupedCompliances, key = { it.inspectionKey }) { group ->
                                InspectionComplianceGroupCard(
                                    group = group,
                                    forceExpanded = allGroupsExpanded,
                                    onTakeAction = { compliance ->
                                        activeComplianceForAction = compliance
                                    }
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredCompliances, key = { it.id }) { compliance ->
                                val parent = inspectionMap[compliance.inspectionId] ?: inspectionMap[compliance.inspectionNumber]
                                AssignedComplianceCard(
                                    compliance = compliance,
                                    parentInspection = parent,
                                    onTakeAction = { activeComplianceForAction = compliance }
                                )
                            }
                        }
                    }
                }
            }
            2 -> {
                // TAB 2: Reports & Dossiers (Word & Excel Exporters, Analytics)
                DashboardReportsTab(
                    inspections = inspections,
                    filteredInspections = filteredInspections,
                    compliances = assignedCompliances,
                    filterCriteria = inspectionFilterCriteria,
                    onExportExcel = { showExportExcelDialog = true },
                    onExportWord = { showExportWordDialog = true },
                    onExportComplianceWord = {
                        val file = ComplianceWordExporter.exportAllCompliancesToWord(
                            context = context,
                            groups = groupedCompliances,
                            officerName = activeUserName
                        )
                        if (file != null) {
                            ComplianceWordExporter.openOrShareWordFile(
                                context,
                                file,
                                "Compliance Action Dossier"
                            )
                        }
                    },
                    onViewWordDossier = {
                        previewInspectionsList = if (inspectionFilterCriteria.isActive) filteredInspections else inspections
                        showWordDossierPreview = true
                    },
                    onViewExcelRegister = {
                        previewInspectionsList = if (inspectionFilterCriteria.isActive) filteredInspections else inspections
                        showExcelRegisterPreview = true
                    },
                    onViewComplianceDossier = {
                        showComplianceDossierPreview = true
                    },
                    onNavigateToInspection = onNavigateToInspection
                )
            }
            else -> {
                // TAB 3: Admin Hub / Officer Profile
                DashboardAdminProfileTab(
                    currentUser = currentUser,
                    isAdminRole = isAdminRole,
                    activeUserName = activeUserName,
                    inspectionsCount = inspections.size,
                    pendingCompliancesCount = pendingCompliancesCount,
                    completedCount = completedCount,
                    onSwitchOfficer = { showSwitchOfficerDialog = true },
                    onNavigateToAssignInspection = onNavigateToAssignInspection ?: { onSwitchToAdmin?.invoke(); Unit },
                    onNavigateToMasterData = onNavigateToMasterData ?: { onSwitchToAdmin?.invoke(); Unit },
                    onNavigateToFormBuilder = onNavigateToFormBuilder ?: { onSwitchToAdmin?.invoke(); Unit },
                    onNavigateToUserManagement = onNavigateToUserManagement ?: { onSwitchToAdmin?.invoke(); Unit },
                    onNavigateToAdminCompliances = onNavigateToAdminCompliances ?: { onSwitchToAdmin?.invoke(); Unit },
                    onNavigateToAdminReports = onNavigateToAdminReports ?: { onSwitchToAdmin?.invoke(); Unit },
                    onSwitchToAdmin = onSwitchToAdmin,
                    onLogout = onLogout
                )
            }
        }
    }
}

    // Modal Action Dialog for Officer to fulfill / update compliance
    activeComplianceForAction?.let { comp ->
        ComplianceActionDialog(
            compliance = comp,
            onDismiss = { activeComplianceForAction = null },
            onSubmit = { newStatus, remarks ->
                viewModel.updateComplianceAction(comp, newStatus, remarks) {
                    activeComplianceForAction = null
                }
            }
        )
    }

    // Switch Field Officer Dialog (Strictly for Admin to change view context)
    if (showSwitchOfficerDialog) {
        SwitchOfficerDialog(
            officers = officers,
            currentUser = currentUser,
            onDismiss = { showSwitchOfficerDialog = false },
            onSelectOfficer = { officer ->
                viewModel.switchOfficer(officer)
                showSwitchOfficerDialog = false
                android.widget.Toast.makeText(context, "Switched active workspace to ${officer.name}", android.widget.Toast.LENGTH_SHORT).show()
            },
            onAddOfficer = {
                showSwitchOfficerDialog = false
                if (onNavigateToUserManagement != null) {
                    onNavigateToUserManagement()
                } else if (onSwitchToAdmin != null) {
                    onSwitchToAdmin()
                }
            }
        )
    }

    // Notification Center Dialog
    if (showNotificationCenter) {
        com.example.ui.common.NotificationCenterDialog(
            onDismiss = { showNotificationCenter = false },
            onNavigateToInspection = { inspectionId ->
                onNavigateToInspection(inspectionId)
            },
            onNavigateToCompliances = {
                selectedTab = 1
            }
        )
    }

    // Spot or Self Inspection Dialog for on-demand field inspections
    if (showSpotInspectionDialog) {
        SpotInspectionDialog(
            types = types,
            stations = stations,
            sections = sections,
            lcGates = lcGates,
            onDismiss = { showSpotInspectionDialog = false },
            onAddStation = { station, cb -> viewModel.addStation(station, cb) },
            onAddSection = { section, cb -> viewModel.addSection(section, cb) },
            onAddLCGate = { gate, cb -> viewModel.addLCGate(gate, cb) },
            onSaveInspection = { type, locName, locId, isSelf, remarks, date, selfieUrl, lat, lng, acc, startNow ->
                viewModel.createSpotInspection(
                    type = type,
                    locationName = locName,
                    locationId = locId,
                    isSelfInspection = isSelf,
                    remarks = remarks,
                    scheduledDate = date,
                    executionDate = date,
                    selfieUrl = selfieUrl,
                    startLatitude = lat,
                    startLongitude = lng,
                    startAccuracy = acc,
                    startNow = startNow
                ) { newInspection ->
                    showSpotInspectionDialog = false
                    if (startNow) {
                        onNavigateToInspection(newInspection.id)
                    }
                }
            }
        )
    }

    if (showExportExcelDialog) {
        ExportExcelConfirmDialog(
            totalInspectionsCount = inspections.size,
            filteredInspectionsCount = filteredInspections.size,
            isFilterActive = inspectionFilterCriteria.isActive,
            onDismiss = { showExportExcelDialog = false },
            onViewPreview = { exportOnlyFiltered, chronological ->
                showExportExcelDialog = false
                val list = if (exportOnlyFiltered) filteredInspections else inspections
                previewInspectionsList = if (chronological) list.sortedBy { it.scheduledDate } else list.sortedByDescending { it.scheduledDate }
                showExcelRegisterPreview = true
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
            isFilterActive = inspectionFilterCriteria.isActive,
            onDismiss = { showExportWordDialog = false },
            onViewPreview = { exportOnlyFiltered, chronological ->
                showExportWordDialog = false
                val list = if (exportOnlyFiltered) filteredInspections else inspections
                previewInspectionsList = if (chronological) list.sortedBy { it.scheduledDate } else list.sortedByDescending { it.scheduledDate }
                showWordDossierPreview = true
            },
            onExport = { exportOnlyFiltered, chronological ->
                showExportWordDialog = false
                val listToExport = if (exportOnlyFiltered) filteredInspections else inspections
                val file = InspectionWordExporter.exportFilteredInspectionsToWord(
                    context = context,
                    inspections = listToExport,
                    criteria = if (exportOnlyFiltered) inspectionFilterCriteria else null,
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

    if (showWordDossierPreview) {
        val list = if (previewInspectionsList.isNotEmpty()) previewInspectionsList else if (inspectionFilterCriteria.isActive) filteredInspections else inspections
        InspectionDossierPreviewDialog(
            inspections = list,
            criteria = if (inspectionFilterCriteria.isActive) inspectionFilterCriteria else null,
            onDismiss = { showWordDossierPreview = false },
            onDownloadWord = {
                val file = InspectionWordExporter.exportFilteredInspectionsToWord(
                    context = context,
                    inspections = list,
                    criteria = if (inspectionFilterCriteria.isActive) inspectionFilterCriteria else null,
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

    if (showExcelRegisterPreview) {
        val list = if (previewInspectionsList.isNotEmpty()) previewInspectionsList else if (inspectionFilterCriteria.isActive) filteredInspections else inspections
        InspectionRegisterExcelPreviewDialog(
            inspections = list,
            criteria = if (inspectionFilterCriteria.isActive) inspectionFilterCriteria else null,
            onDismiss = { showExcelRegisterPreview = false },
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

    if (showComplianceDossierPreview) {
        ComplianceDossierPreviewDialog(
            groups = groupedCompliances,
            officerName = activeUserName,
            onDismiss = { showComplianceDossierPreview = false },
            onDownloadWord = {
                val file = ComplianceWordExporter.exportAllCompliancesToWord(
                    context = context,
                    groups = groupedCompliances,
                    officerName = activeUserName
                )
                if (file != null) {
                    ComplianceWordExporter.openOrShareWordFile(
                        context,
                        file,
                        "Compliance Action Dossier"
                    )
                }
            }
        )
    }
}

@Composable
fun DashboardReportsTab(
    inspections: List<Inspection>,
    filteredInspections: List<Inspection>,
    compliances: List<Compliance>,
    filterCriteria: InspectionFilterCriteria,
    onExportExcel: () -> Unit,
    onExportWord: () -> Unit,
    onExportComplianceWord: () -> Unit,
    onViewWordDossier: () -> Unit,
    onViewExcelRegister: () -> Unit,
    onViewComplianceDossier: () -> Unit,
    onNavigateToInspection: (String) -> Unit
) {
    val totalInspections = inspections.size
    val completedInspections = inspections.count { it.status == InspectionStatus.COMPLETED }
    val totalCompliances = compliances.size
    val resolvedCompliances = compliances.count { it.status == ComplianceStatus.RESOLVED || it.status == ComplianceStatus.CLOSED }
    val resolutionRate = if (totalCompliances > 0) ((resolvedCompliances * 100) / totalCompliances) else 100

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_reports_tab_content"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Executive Summary Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Assessment,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                "Inspection & Compliance Dossiers",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "Official Ministry / Railway Documentation Center",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text("$totalInspections", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Total Inspections", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text("$completedInspections", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                            Text("Completed", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text("$totalCompliances", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("Compliances", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text("$resolutionRate%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if (resolutionRate >= 80) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error)
                            Text("Resolved %", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // Export Action 1: Word Inspection Dossier (.docx)
        item {
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1565C0).copy(alpha = 0.12f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFF1565C0), modifier = Modifier.size(24.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Word Inspection Dossier (.docx)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "Official Indian Railways formatted report with cover page, observations, ratings, and photographic evidence.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onViewWordDossier,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("view_word_dossier_btn"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1565C0)),
                            border = BorderStroke(1.dp, Color(0xFF1565C0))
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("View Dossier", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onExportWord,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_word_dossier_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download")
                        }
                    }
                }
            }
        }

        // Export Action 2: Excel Inspection Register (.xlsx)
        item {
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF2E7D32).copy(alpha = 0.12f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.TableChart, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(24.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Excel Inspection Register (.xlsx)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "Tabular spreadsheet register designed for statutory audits, MIS reporting, and divisional tracking.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onViewExcelRegister,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("view_excel_register_btn"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2E7D32)),
                            border = BorderStroke(1.dp, Color(0xFF2E7D32))
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("View Register", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onExportExcel,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_excel_register_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download")
                        }
                    }
                }
            }
        }

        // Export Action 3: Word Compliance Action Dossier (.docx)
        item {
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.FactCheck, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Compliance Action Dossier (.docx)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "Comprehensive audit report covering point-wise compliance notes, action officer remarks, and closure statuses.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onViewComplianceDossier,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("view_compliance_dossier_btn"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("View Dossier", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onExportComplianceWord,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_compliance_dossier_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download ($totalCompliances)")
                        }
                    }
                }
            }
        }

        // Recent Inspections Quick Access
        if (inspections.isNotEmpty()) {
            item {
                Text(
                    "Recent Inspections ($totalInspections)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            items(inspections.take(5), key = { "report_insp_${it.id}" }) { insp ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToInspection(insp.id) },
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                insp.inspectionNumber.ifBlank { "Inspection #${insp.id.take(8)}" },
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "${insp.inspectionTypeName} • ${insp.locationName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(
                            onClick = { onNavigateToInspection(insp.id) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Open", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardAdminProfileTab(
    currentUser: User?,
    isAdminRole: Boolean,
    activeUserName: String,
    inspectionsCount: Int,
    pendingCompliancesCount: Int,
    completedCount: Int,
    onSwitchOfficer: () -> Unit,
    onNavigateToAssignInspection: () -> Unit,
    onNavigateToMasterData: () -> Unit,
    onNavigateToFormBuilder: () -> Unit,
    onNavigateToUserManagement: () -> Unit,
    onNavigateToAdminCompliances: () -> Unit,
    onNavigateToAdminReports: () -> Unit,
    onSwitchToAdmin: (() -> Unit)?,
    onLogout: () -> Unit
) {
    val isSyncing by FirebaseSyncManager.isSyncing.collectAsState()
    val syncStatus by FirebaseSyncManager.syncStatus.collectAsState()
    val isPlaceholder = FirebaseSyncManager.isPlaceholderConfig

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_admin_profile_tab_content"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // User Profile Header Card
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isAdminRole) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isAdminRole) Icons.Default.AdminPanelSettings else Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    activeUserName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isAdminRole) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                ) {
                                    Text(
                                        text = when (currentUser?.role) {
                                            Role.SUPER_ADMIN -> "SUPER ADMIN"
                                            Role.ADMIN -> "ADMIN"
                                            else -> "FIELD OFFICER"
                                        },
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "${currentUser?.designation?.ifBlank { "Officer" }} • ${currentUser?.departmentName?.ifBlank { "HQ" }}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!currentUser?.email.isNullOrBlank()) {
                                Text(
                                    text = currentUser?.email ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }

                    if (isAdminRole) {
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = onSwitchOfficer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.SwitchAccount, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Switch Viewing Officer Workspace")
                        }
                    }
                }
            }
        }

        // Cloud Synchronization Card
        item {
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = if (isPlaceholder) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Cloud Synchronization", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                syncStatus,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    Button(
                        onClick = {
                            FirebaseSyncManager.syncAllToCloud { _, _ -> }
                        },
                        enabled = !isSyncing,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Sync")
                        }
                    }
                }
            }
        }

        if (isAdminRole) {
            item {
                Text(
                    "Administration Modules",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AdminNavCard(
                        title = "Assign New Inspection",
                        subtitle = "Schedule officer inspections with locations & dates",
                        icon = Icons.Default.Assessment,
                        onClick = onNavigateToAssignInspection
                    )
                    AdminNavCard(
                        title = "Form & Checklist Builder",
                        subtitle = "Create dynamic inspection checklists & field types",
                        icon = Icons.Default.Build,
                        onClick = onNavigateToFormBuilder
                    )
                    AdminNavCard(
                        title = "User & Role Directory",
                        subtitle = "Manage officers, designations, and system roles",
                        icon = Icons.Default.Group,
                        onClick = onNavigateToUserManagement
                    )
                    AdminNavCard(
                        title = "Master Data Management",
                        subtitle = "Configure Railway Stations, Sections, and LC Gates",
                        icon = Icons.Default.Map,
                        onClick = onNavigateToMasterData
                    )
                    AdminNavCard(
                        title = "Cross-Division Compliance Center",
                        subtitle = "Track open observations across all departments",
                        icon = Icons.Default.CheckCircle,
                        onClick = onNavigateToAdminCompliances
                    )
                    AdminNavCard(
                        title = "Executive MIS Reports",
                        subtitle = "Deep analytics, completion ratios, and summaries",
                        icon = Icons.Default.PictureAsPdf,
                        onClick = onNavigateToAdminReports
                    )
                    if (onSwitchToAdmin != null) {
                        AdminNavCard(
                            title = "Full Admin Dashboard Grid",
                            subtitle = "Open standard administrative control panel",
                            icon = Icons.Default.Dashboard,
                            onClick = onSwitchToAdmin
                        )
                    }
                }
            }
        }

        // Duty Summary
        item {
            Text(
                "My Workspace Summary",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    OfficerMetric("Inspections", "$inspectionsCount", MaterialTheme.colorScheme.primary)
                    OfficerMetric("Completed", "$completedCount", Color(0xFF2E7D32))
                    OfficerMetric("Pending Comp.", "$pendingCompliancesCount", if (pendingCompliancesCount > 0) MaterialTheme.colorScheme.error else Color(0xFF2E7D32))
                }
            }
        }

        // Account Logout Button
        item {
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_profile_logout_btn"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Logout from Account")
            }
        }
    }
}

@Composable
fun AdminNavCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun OfficerMetric(label: String, count: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun InspectionCard(
    inspection: Inspection,
    compliancesCount: Int = 0,
    pendingCompliancesCount: Int = 0,
    onClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val dateString = if (inspection.scheduledDate > 0) dateFormat.format(Date(inspection.scheduledDate)) else "Unscheduled"

    val statusColor = when (inspection.status) {
        InspectionStatus.COMPLETED -> Color(0xFF2E7D32)
        InspectionStatus.IN_PROGRESS -> MaterialTheme.colorScheme.tertiary
        InspectionStatus.ASSIGNED, InspectionStatus.ACCEPTED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val buttonLabel = when (inspection.status) {
        InspectionStatus.ASSIGNED, InspectionStatus.ACCEPTED -> "Verify & Start Inspection"
        InspectionStatus.IN_PROGRESS -> "Continue Inspection"
        InspectionStatus.COMPLETED -> "View Inspection Dossier"
        else -> "Open Inspection"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                            imageVector = when (inspection.status) {
                                InspectionStatus.COMPLETED -> Icons.Default.CheckCircle
                                InspectionStatus.IN_PROGRESS -> Icons.Default.EditNote
                                else -> Icons.Default.PlayArrow
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
                            text = inspection.inspectionNumber.ifEmpty { "INSPECTION" },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
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
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (inspection.inspectionCategory == "SELF") MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = inspection.inspectionTypeName.ifEmpty { "General Inspection" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Location: ${inspection.locationName.ifEmpty { "Unspecified" }}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Surface(
                    color = statusColor.copy(alpha = 0.12f),
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
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Inspecting Officer: ${inspection.assignedOfficerName.ifEmpty { "Unassigned" }}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val execDate = inspection.executionDate
                if (execDate != null && execDate > 0) {
                    Text(
                        text = "Executed: ${dateFormat.format(Date(execDate))}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = "Scheduled: $dateString",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (inspection.executionDate != null && inspection.executionDate!! > 0 && inspection.scheduledDate > 0) {
                val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = inspection.executionDate!! }
                val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = inspection.scheduledDate }
                val isDiff = cal1.get(java.util.Calendar.YEAR) != cal2.get(java.util.Calendar.YEAR) ||
                             cal1.get(java.util.Calendar.DAY_OF_YEAR) != cal2.get(java.util.Calendar.DAY_OF_YEAR)
                if (isDiff) {
                    Text(
                        text = "Scheduled: $dateString (Executed on different date)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Compliance status indicator tag on inspection card
            if (compliancesCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = if (pendingCompliancesCount > 0) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (pendingCompliancesCount > 0) Icons.Default.PendingActions else Icons.Default.AssignmentTurnedIn,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (pendingCompliancesCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (pendingCompliancesCount > 0)
                                "$compliancesCount Compliances raised ($pendingCompliancesCount pending)"
                            else "$compliancesCount Compliances raised (All Resolved)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (pendingCompliancesCount > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(8.dp))

            // Direct Primary Action Button
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                colors = if (inspection.status == InspectionStatus.COMPLETED) {
                    ButtonDefaults.outlinedButtonColors()
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Icon(
                    imageVector = when (inspection.status) {
                        InspectionStatus.COMPLETED -> Icons.Default.Assessment
                        InspectionStatus.IN_PROGRESS -> Icons.Default.Edit
                        else -> Icons.Default.PlayArrow
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(buttonLabel)
            }
        }
    }
}

@Composable
fun InspectionComplianceGroupCard(
    group: InspectionComplianceGroup,
    forceExpanded: Boolean,
    onTakeAction: (Compliance) -> Unit
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("inspection_group_${group.inspectionNumber}"),
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "SPOT INSPECTION",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            modifier = Modifier.rotate(arrowRotation),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Inspection Type Name
                Text(
                    text = group.inspectionTypeName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Inspection Dossier Highlight Box: Date, Inspecting Officer, Location
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Date
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Inspection Date: ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                inspectionDateStr,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Inspecting Officer
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Inspecting Officer: ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                group.inspectingOfficerName,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Location
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Location: ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                group.locationName,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Summary status pills & toggle hint
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "${group.compliances.size} Points Total",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (openCount > 0) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
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
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    "$inProgressCount In Progress",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (resolvedCount > 0) {
                            Surface(
                                color = Color(0xFFE8F5E9),
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
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            val file = ComplianceWordExporter.exportInspectionComplianceToWord(context, group)
                            if (file != null) {
                                ComplianceWordExporter.openOrShareWordFile(
                                    context = context,
                                    file = file,
                                    title = "Compliance Dossier - ${group.inspectionNumber}"
                                )
                            }
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF1E3A8A).copy(alpha = 0.12f),
                            contentColor = Color(0xFF1E3A8A)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("download_word_${group.inspectionNumber}")
                    ) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = "Word Doc",
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFF1E3A8A)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Download Word File",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E3A8A)
                        )
                    }

                    Text(
                        if (isExpanded) "Hide Points ▲" else "View Points (${group.compliances.size}) ▼",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Expandable List of Compliance Action Points
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Text(
                        "Action Points Under This Inspection (${group.compliances.size}):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    group.compliances.forEach { compliance ->
                        GroupedComplianceItemCard(
                            compliance = compliance,
                            onTakeAction = { onTakeAction(compliance) }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = {
                            val file = ComplianceWordExporter.exportInspectionComplianceToWord(context, group)
                            if (file != null) {
                                ComplianceWordExporter.openOrShareWordFile(
                                    context = context,
                                    file = file,
                                    title = "Compliance Dossier - ${group.inspectionNumber}"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF1E3A8A)
                        ),
                        border = BorderStroke(1.dp, Color(0xFF1E3A8A).copy(alpha = 0.4f))
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Download Detailed Word Dossier (${group.inspectionNumber})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
fun GroupedComplianceItemCard(
    compliance: Compliance,
    onTakeAction: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val targetDateStr = if (compliance.targetDate != null && compliance.targetDate!! > 0)
        dateFormat.format(Date(compliance.targetDate!!)) else "No target date"

    val statusColor = when (compliance.status) {
        ComplianceStatus.RESOLVED, ComplianceStatus.CLOSED -> Color(0xFF2E7D32)
        ComplianceStatus.UNDER_ACTION -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Badges row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = when (compliance.severity) {
                        ComplianceSeverity.CRITICAL -> MaterialTheme.colorScheme.errorContainer
                        ComplianceSeverity.MAJOR -> MaterialTheme.colorScheme.tertiaryContainer
                        ComplianceSeverity.MINOR -> MaterialTheme.colorScheme.primaryContainer
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        "${compliance.severity.name} PRIORITY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        compliance.status.name.replace("_", " "),
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                compliance.inspectionPointTitle.ifEmpty { compliance.title.ifEmpty { "Observation Item" } },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        "Observation / Deficiency:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        compliance.description.ifBlank { "No description entered." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (compliance.complianceRemarks.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            "Your Action Remarks:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            compliance.complianceRemarks,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Target: $targetDateStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Button(
                    onClick = onTakeAction,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        if (compliance.status == ComplianceStatus.RESOLVED) Icons.Default.Edit else Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (compliance.status == ComplianceStatus.RESOLVED) "Update Remarks" else "Take Action",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
fun AssignedComplianceCard(
    compliance: Compliance,
    parentInspection: Inspection? = null,
    onTakeAction: () -> Unit
) {
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val targetDateStr = if (compliance.targetDate != null && compliance.targetDate!! > 0)
        dateFormat.format(Date(compliance.targetDate!!)) else "No target date"

    val statusColor = when (compliance.status) {
        ComplianceStatus.RESOLVED, ComplianceStatus.CLOSED -> Color(0xFF2E7D32)
        ComplianceStatus.UNDER_ACTION -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Priority and Status Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = when (compliance.severity) {
                        ComplianceSeverity.CRITICAL -> MaterialTheme.colorScheme.errorContainer
                        ComplianceSeverity.MAJOR -> MaterialTheme.colorScheme.tertiaryContainer
                        ComplianceSeverity.MINOR -> MaterialTheme.colorScheme.primaryContainer
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        "${compliance.severity.name} PRIORITY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        compliance.status.name.replace("_", " "),
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Inspection Header Dossier Box
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Assignment,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Inspection: ${compliance.inspectionNumber} • ${parentInspection?.inspectionTypeName ?: "Safety Inspection"}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    val rawDate = parentInspection?.executionDate ?: parentInspection?.startedAt ?: parentInspection?.scheduledDate ?: compliance.createdAt
                    val dateFormatted = if (rawDate > 0) dateFormat.format(Date(rawDate)) else "Date unspecified"
                    val officerName = parentInspection?.assignedOfficerName?.ifBlank { compliance.inspectingOfficerName } ?: compliance.inspectingOfficerName.ifBlank { "Inspecting Officer" }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "📅 $dateFormatted • 👮 Officer: $officerName",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "📍 ${compliance.locationName.ifBlank { parentInspection?.locationName ?: "Location unspecified" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                compliance.inspectionPointTitle.ifEmpty { compliance.title },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        "Observation / Deficiency:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        compliance.description,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (compliance.complianceRemarks.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            "Your Action Remarks:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            compliance.complianceRemarks,
                            style = MaterialTheme.typography.bodySmall
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Target: $targetDateStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            val singleGroup = InspectionComplianceGroup(
                                inspectionKey = compliance.inspectionNumber,
                                inspectionNumber = compliance.inspectionNumber,
                                inspectionTypeName = parentInspection?.inspectionTypeName ?: "Safety Inspection",
                                locationName = compliance.locationName.ifBlank { parentInspection?.locationName ?: "Location unspecified" },
                                inspectingOfficerName = compliance.inspectingOfficerName.ifBlank { parentInspection?.assignedOfficerName ?: "Inspecting Officer" },
                                inspectionDateMillis = parentInspection?.executionDate ?: compliance.createdAt,
                                isSpotInspection = parentInspection?.isSpotInspection ?: false,
                                compliances = listOf(compliance),
                                parentInspection = parentInspection
                            )
                            val file = ComplianceWordExporter.exportInspectionComplianceToWord(context, singleGroup)
                            if (file != null) {
                                ComplianceWordExporter.openOrShareWordFile(
                                    context = context,
                                    file = file,
                                    title = "Compliance Dossier - ${compliance.inspectionNumber}"
                                )
                            }
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color(0xFF1E3A8A).copy(alpha = 0.12f),
                            contentColor = Color(0xFF1E3A8A)
                        ),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Description, contentDescription = "Download Word File", modifier = Modifier.size(18.dp))
                    }

                    Button(
                        onClick = onTakeAction,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            if (compliance.status == ComplianceStatus.RESOLVED) Icons.Default.Edit else Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (compliance.status == ComplianceStatus.RESOLVED) "Update Remarks" else "Take Action")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwitchOfficerDialog(
    officers: List<User>,
    currentUser: User?,
    onDismiss: () -> Unit,
    onSelectOfficer: (User) -> Unit,
    onAddOfficer: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredOfficers = remember(officers, searchQuery) {
        if (searchQuery.isBlank()) officers
        else officers.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.hrmsId.contains(searchQuery, ignoreCase = true) ||
            it.designation.contains(searchQuery, ignoreCase = true) ||
            it.departmentName.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SwitchAccount,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Switch Field Officer Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Select any officer or admin to switch into their field workspace and inspect their assigned tasks and compliances.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                if (officers.size > 3) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search officer by name, HRMS, dept...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (filteredOfficers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isBlank()) "No officers found. Add a user in User Management to switch." else "No officers match \"$searchQuery\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredOfficers, key = { it.uid.ifEmpty { it.hrmsId } }) { officer ->
                            val isSelected = officer.uid == currentUser?.uid || 
                                (officer.hrmsId.isNotBlank() && officer.hrmsId.equals(currentUser?.hrmsId, ignoreCase = true))

                            Card(
                                onClick = { onSelectOfficer(officer) },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                ),
                                border = if (isSelected) {
                                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                } else {
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Person,
                                                contentDescription = null,
                                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = officer.name.ifBlank { "Officer (${officer.hrmsId})" },
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = when (officer.role) {
                                                    Role.SUPER_ADMIN -> MaterialTheme.colorScheme.tertiaryContainer
                                                    Role.ADMIN -> MaterialTheme.colorScheme.primary
                                                    Role.OFFICER -> MaterialTheme.colorScheme.secondaryContainer
                                                },
                                                shape = MaterialTheme.shapes.extraSmall
                                            ) {
                                                Text(
                                                    text = when (officer.role) {
                                                        Role.SUPER_ADMIN -> "Super Admin"
                                                        Role.ADMIN -> "Admin"
                                                        Role.OFFICER -> "Officer"
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = when (officer.role) {
                                                        Role.SUPER_ADMIN -> MaterialTheme.colorScheme.onTertiaryContainer
                                                        Role.ADMIN -> MaterialTheme.colorScheme.onPrimary
                                                        Role.OFFICER -> MaterialTheme.colorScheme.onSecondaryContainer
                                                    },
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }

                                        Text(
                                            text = "${officer.designation.ifBlank { "Officer" }} • ${officer.departmentName.ifBlank { "Railway" }}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Text(
                                            text = "HRMS: ${officer.hrmsId.ifBlank { "N/A" }}${if (officer.mobile.isNotBlank()) " | Tel: ${officer.mobile}" else ""}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    if (isSelected) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = MaterialTheme.shapes.small
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = "Active",
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    "Active",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    } else {
                                        Icon(
                                            Icons.Default.ChevronRight,
                                            contentDescription = "Select",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAddOfficer) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Manage Users")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

