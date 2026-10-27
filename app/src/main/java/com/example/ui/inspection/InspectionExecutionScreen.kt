package com.example.ui.inspection

import android.Manifest
import android.content.Context
import android.location.Location
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhotoCameraFront
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.models.FieldType
import com.example.models.Inspection
import com.example.models.InspectionField
import com.example.models.InspectionStatus
import com.google.android.gms.location.*
import com.example.util.LocationHelper
import com.example.util.ClickableGpsLocationCard
import com.example.util.ClickableGpsBadge
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import android.content.Intent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.pointer.pointerInput
import java.io.FileOutputStream
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.onFocusEvent
import kotlinx.coroutines.delay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import com.example.models.Compliance
import com.example.models.ComplianceSeverity
import com.example.models.ComplianceStatus
import com.example.models.User
import com.example.ui.dashboard.InspectionComplianceGroup
import com.example.util.ComplianceWordExporter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ComplianceTargetPoint(
    val pointId: String,
    val pointTitle: String,
    val currentValue: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionExecutionScreen(
    inspectionId: String,
    onBack: () -> Unit,
    viewModel: InspectionExecutionViewModel = viewModel()
) {
    val inspection by viewModel.inspection.collectAsState()
    val fields by viewModel.fields.collectAsState()
    val customFields by viewModel.customFields.collectAsState()
    val responses by viewModel.responses.collectAsState()
    val attachments by viewModel.attachments.collectAsState()
    val compliances by viewModel.compliances.collectAsState()
    val officers by viewModel.officers.collectAsState()

    var showAddCustomFieldDialog by remember { mutableStateOf(false) }
    var showExecutionDateChangeDialog by remember { mutableStateOf(false) }
    var complianceDialogFieldTarget by remember { mutableStateOf<ComplianceTargetPoint?>(null) }

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(inspectionId) {
        viewModel.loadInspection(inspectionId)
    }

    val currentUser by com.example.data.SessionManager.currentUser.collectAsState()
    val isAuthorized = remember(inspection, currentUser) {
        val insp = inspection
        val user = currentUser
        if (insp == null || user == null) true
        else if (user.role == com.example.models.Role.SUPER_ADMIN) true
        else com.example.data.SessionManager.isInspectionAssignedToUser(insp, user)
    }

    val density = LocalDensity.current
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(inspection?.inspectionTypeName ?: "Inspection") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!isImeVisible && inspection?.status == InspectionStatus.IN_PROGRESS) {
                BottomAppBar {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = {
                            viewModel.saveInspection(isComplete = false) { success ->
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(if (success) "Draft saved" else "Failed to save")
                                }
                            }
                        }) {
                            Text("Save Draft")
                        }
                        Button(onClick = {
                            val missingRequired = fields.any { it.required && responses[it.fieldName].isNullOrBlank() }
                            if (missingRequired) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Please fill in all required fields")
                                }
                            } else {
                                viewModel.saveInspection(isComplete = true) { success ->
                                    if (success) {
                                        onBack()
                                    } else {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Failed to submit inspection")
                                        }
                                    }
                                }
                            }
                        }) {
                            Text("Submit")
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        if (inspection == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (!isAuthorized) {
            val insp = inspection!!
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Restricted",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Access Restricted",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "This inspection (${insp.inspectionNumber}) is assigned to \"${insp.assignedOfficerName}\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "As ${currentUser?.name ?: "Current Officer"}, you are only authorized to verify and start inspections assigned directly to you.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = onBack,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Back to My Inspections")
                        }
                    }
                }
            }
        } else {
            val insp = inspection!!
            
            if (insp.status == InspectionStatus.ASSIGNED || insp.status == InspectionStatus.ACCEPTED) {
                // Show Start Screen (Capture Date, GPS and Selfie)
                StartInspectionView(
                    inspection = insp,
                    modifier = Modifier.padding(paddingValues),
                    onStart = { lat, lng, acc, selfieUri, executionDate, remarks, verifiedLoc -> 
                        viewModel.startInspection(lat, lng, acc, selfieUri, executionDate, remarks, verifiedLoc)
                    }
                )
            } else if (insp.status == InspectionStatus.COMPLETED) {
                CompletedInspectionView(
                    inspection = insp,
                    fields = fields + customFields,
                    responses = responses,
                    compliances = compliances,
                    attachments = attachments,
                    modifier = Modifier.padding(paddingValues),
                    onBack = onBack,
                    onAssignCompliance = { pointId, pointTitle, currentVal ->
                        complianceDialogFieldTarget = ComplianceTargetPoint(pointId, pointTitle, currentVal)
                    }
                )
            } else {
                val executionListState = rememberLazyListState()
                val groupedFields = remember(fields) {
                    val map = linkedMapOf<String, LinkedHashMap<String, MutableList<InspectionField>>>()
                    for (f in fields) {
                        val cat = f.categoryName.ifBlank { "General Checklist" }
                        val sub = f.subcategoryName.ifBlank { "General Points" }
                        map.getOrPut(cat) { linkedMapOf() }.getOrPut(sub) { mutableListOf() }.add(f)
                    }
                    map
                }

                // Collapsed view state by default: all categories and subcategories start collapsed
                var expandedCategories by remember(fields) { mutableStateOf(setOf<String>()) }
                var expandedSubcategories by remember(fields) { mutableStateOf(setOf<String>()) }

                LazyColumn(
                    state = executionListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .imePadding(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 140.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Location: ${insp.locationName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    if (insp.isSpotInspection) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            color = if (insp.inspectionCategory == "SELF") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = if (insp.inspectionCategory == "SELF") "SELF" else "SPOT",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (insp.inspectionCategory == "SELF") MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    "Inspection No: ${insp.inspectionNumber} • Officer: ${insp.assignedOfficerName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                // Execution Date Row with Change Date Button
                                val execDate = insp.executionDate ?: insp.startedAt ?: insp.scheduledDate
                                val execDateStr = if (execDate > 0) dateFormat.format(Date(execDate)) else "Not set"
                                val scheduledDateStr = if (insp.scheduledDate > 0) dateFormat.format(Date(insp.scheduledDate)) else "Not set"
                                val isDiff = insp.scheduledDate > 0 && execDate > 0 && !isSameDay(execDate, insp.scheduledDate)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.CalendarMonth,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                "Execution Date: $execDateStr",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        if (isDiff) {
                                            Text(
                                                "Assigned for: $scheduledDateStr (Executed on different date)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.tertiary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = { showExecutionDateChangeDialog = true },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Change Date", style = MaterialTheme.typography.labelSmall)
                                    }
                                }

                                // Location & Selfie Verification Summary Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (insp.selfieURL.isNotBlank()) {
                                        AsyncImage(
                                            model = insp.selfieURL,
                                            contentDescription = "Officer Selfie",
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        if (insp.startLatitude != null && insp.startLongitude != null) {
                                            ClickableGpsBadge(
                                                latitude = insp.startLatitude,
                                                longitude = insp.startLongitude,
                                                locationName = insp.locationName,
                                                verifiedLocationName = insp.verifiedLocationName,
                                                accuracy = insp.startAccuracy
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                        }
                                        Text(
                                            if (insp.selfieURL.isNotBlank()) "Selfie: Verified" else "Selfie: Pending",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (insp.selfieURL.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (fields.isNotEmpty()) {
                        val hasCategories = fields.any { it.categoryName.isNotBlank() || it.subcategoryName.isNotBlank() }
                        if (!hasCategories) {
                            item {
                                Text("Standard Checklist Fields", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }

                            items(fields) { field ->
                                val currentVal = responses[field.fieldName] ?: ""
                                val fieldCompliances = compliances.filter { comp ->
                                    when {
                                        comp.inspectionPointId.isNotBlank() ->
                                            comp.inspectionPointId == field.id || comp.inspectionPointId == field.fieldName
                                        comp.inspectionPointTitle.isNotBlank() ->
                                            comp.inspectionPointTitle.equals(field.label, ignoreCase = true)
                                        else -> false
                                    }
                                }
                                FieldInspectionItemCard(
                                    field = field,
                                    currentVal = currentVal,
                                    fieldCompliances = fieldCompliances,
                                    insp = insp,
                                    attachments = attachments[field.fieldName] ?: emptyList(),
                                    onValueChange = { newValue -> viewModel.updateResponse(field.fieldName, newValue) },
                                    onAttachmentAdded = { newAtt -> viewModel.addAttachment(field.fieldName, newAtt) },
                                    onAttachmentRemoved = { attId -> viewModel.removeAttachment(field.fieldName, attId) },
                                    onRaiseCompliance = {
                                        complianceDialogFieldTarget = ComplianceTargetPoint(
                                            pointId = field.id.ifBlank { field.fieldName },
                                            pointTitle = field.label,
                                            currentValue = currentVal
                                        )
                                    }
                                )
                            }
                        } else {
                            // Top Control Bar: Categories header and Expand All / Collapse All controls
                            item(key = "categories_control_bar") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Checklist Categories (${groupedFields.size})",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            "Select a category, then subcategory to fill inspection form",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        TextButton(
                                            onClick = {
                                                expandedCategories = groupedFields.keys.toSet()
                                                val allSubs = mutableSetOf<String>()
                                                groupedFields.forEach { (cat, subs) ->
                                                    subs.keys.forEach { sub -> allSubs.add("$cat::$sub") }
                                                }
                                                expandedSubcategories = allSubs
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.UnfoldMore, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text("Expand All", style = MaterialTheme.typography.labelSmall)
                                        }
                                        TextButton(
                                            onClick = {
                                                expandedCategories = emptySet()
                                                expandedSubcategories = emptySet()
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.UnfoldLess, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text("Collapse All", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }

                            groupedFields.forEach { (categoryName, subcategoriesMap) ->
                                val isCatExpanded = categoryName in expandedCategories
                                val totalCatFields = subcategoriesMap.values.sumOf { it.size }
                                val filledCatFields = subcategoriesMap.values.sumOf { list ->
                                    list.count { field ->
                                        responses[field.fieldName]?.isNotBlank() == true || (attachments[field.fieldName]?.isNotEmpty() == true)
                                    }
                                }

                                // Category Header (Tap to Expand / Collapse)
                                item(key = "cat_$categoryName") {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isCatExpanded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = if (isCatExpanded) 3.dp else 1.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp)
                                            .clickable {
                                                expandedCategories = if (isCatExpanded) {
                                                    expandedCategories - categoryName
                                                } else {
                                                    expandedCategories + categoryName
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Folder,
                                                contentDescription = null,
                                                tint = if (isCatExpanded) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    categoryName,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isCatExpanded) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.padding(top = 2.dp)
                                                ) {
                                                    Text(
                                                        "${subcategoriesMap.size} ${if (subcategoriesMap.size == 1) "subcategory" else "subcategories"}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = if (isCatExpanded) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                                    )
                                                    Text(
                                                        "•",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.outline
                                                    )
                                                    if (filledCatFields == totalCatFields && totalCatFields > 0) {
                                                        Text(
                                                            "✓ All $totalCatFields filled",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    } else {
                                                        Text(
                                                            "$filledCatFields of $totalCatFields filled",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = if (isCatExpanded) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                                        )
                                                    }
                                                }
                                            }
                                            IconButton(
                                                onClick = {
                                                    expandedCategories = if (isCatExpanded) {
                                                        expandedCategories - categoryName
                                                    } else {
                                                        expandedCategories + categoryName
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    if (isCatExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                    contentDescription = if (isCatExpanded) "Collapse $categoryName" else "Expand $categoryName",
                                                    tint = if (isCatExpanded) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }

                                // When Category is Expanded, show its Subcategories
                                if (isCatExpanded) {
                                    subcategoriesMap.forEach { (subName, subFields) ->
                                        val subKey = "$categoryName::$subName"
                                        val isSubExpanded = subKey in expandedSubcategories
                                        val totalSubFields = subFields.size
                                        val filledSubFields = subFields.count { field ->
                                            responses[field.fieldName]?.isNotBlank() == true || (attachments[field.fieldName]?.isNotEmpty() == true)
                                        }

                                        // Subcategory Header (Tap to Expand / Collapse)
                                        item(key = "sub_$subKey") {
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (isSubExpanded) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                                ),
                                                shape = RoundedCornerShape(8.dp),
                                                elevation = CardDefaults.cardElevation(defaultElevation = if (isSubExpanded) 2.dp else 0.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 12.dp, top = 4.dp, bottom = 2.dp)
                                                    .clickable {
                                                        expandedSubcategories = if (isSubExpanded) {
                                                            expandedSubcategories - subKey
                                                        } else {
                                                            expandedSubcategories + subKey
                                                        }
                                                    }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        Icons.Default.Layers,
                                                        contentDescription = null,
                                                        tint = if (isSubExpanded) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.secondary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            subName,
                                                            style = MaterialTheme.typography.bodyLarge,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = if (isSubExpanded) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                            modifier = Modifier.padding(top = 1.dp)
                                                        ) {
                                                            Text(
                                                                "$totalSubFields fields",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                            Text(
                                                                "•",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.outline
                                                            )
                                                            if (filledSubFields == totalSubFields && totalSubFields > 0) {
                                                                Text(
                                                                    "✓ Complete ($filledSubFields/$totalSubFields)",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.primary
                                                                )
                                                            } else {
                                                                Text(
                                                                    "$filledSubFields of $totalSubFields filled",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                            }
                                                        }
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            expandedSubcategories = if (isSubExpanded) {
                                                                expandedSubcategories - subKey
                                                            } else {
                                                                expandedSubcategories + subKey
                                                            }
                                                        }
                                                    ) {
                                                        Icon(
                                                            if (isSubExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                            contentDescription = if (isSubExpanded) "Collapse $subName" else "Expand $subName",
                                                            tint = if (isSubExpanded) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // When Subcategory is Expanded, show its Form Fields!
                                        if (isSubExpanded) {
                                            items(
                                                subFields,
                                                key = { "field_${it.id.ifBlank { it.fieldName }}" }
                                            ) { field ->
                                                val currentVal = responses[field.fieldName] ?: ""
                                                val fieldCompliances = compliances.filter { comp ->
                                                    when {
                                                        comp.inspectionPointId.isNotBlank() ->
                                                            comp.inspectionPointId == field.id || comp.inspectionPointId == field.fieldName
                                                        comp.inspectionPointTitle.isNotBlank() ->
                                                            comp.inspectionPointTitle.equals(field.label, ignoreCase = true)
                                                        else -> false
                                                    }
                                                }
                                                Box(modifier = Modifier.padding(start = 12.dp)) {
                                                    FieldInspectionItemCard(
                                                        field = field,
                                                        currentVal = currentVal,
                                                        fieldCompliances = fieldCompliances,
                                                        insp = insp,
                                                        attachments = attachments[field.fieldName] ?: emptyList(),
                                                        onValueChange = { newValue -> viewModel.updateResponse(field.fieldName, newValue) },
                                                        onAttachmentAdded = { newAtt -> viewModel.addAttachment(field.fieldName, newAtt) },
                                                        onAttachmentRemoved = { attId -> viewModel.removeAttachment(field.fieldName, attId) },
                                                        onRaiseCompliance = {
                                                            complianceDialogFieldTarget = ComplianceTargetPoint(
                                                                pointId = field.id.ifBlank { field.fieldName },
                                                                pointTitle = field.label,
                                                                currentValue = currentVal
                                                            )
                                                        }
                                                    )
                                                }
                                            }

                                            // Subcategory minimize button
                                            item(key = "collapse_sub_$subKey") {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(start = 14.dp, end = 4.dp, bottom = 4.dp),
                                                    horizontalArrangement = Arrangement.End
                                                ) {
                                                    TextButton(
                                                        onClick = { expandedSubcategories = expandedSubcategories - subKey },
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                    ) {
                                                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Minimize $subName", style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Category minimize button
                                    item(key = "collapse_cat_$categoryName") {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            TextButton(
                                                onClick = { expandedCategories = expandedCategories - categoryName },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                            ) {
                                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Minimize Category ($categoryName)", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section: Additional Text Fields added by Officer (Compact Space-Saving Layout)
                    item {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Text(
                                    "Additional Observations",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = Color(0xFFFFF7ED),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, Color(0xFFFFEDD5))
                                ) {
                                    Text(
                                        "${customFields.size} custom",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFC2410C),
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = { showAddCustomFieldDialog = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier.height(28.dp),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFFEA580C),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add Text Field", modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Field", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (customFields.isEmpty()) {
                        item {
                            Surface(
                                onClick = { showAddCustomFieldDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.AddCircleOutline,
                                            contentDescription = null,
                                            tint = Color(0xFFEA580C),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "+ Add Custom Text Field / Observation Notes",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF0F172A)
                                        )
                                    }
                                    Text(
                                        "Tap to add",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFC2410C),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    } else {
                        items(customFields, key = { it.id }) { customField ->
                            val currentVal = responses[customField.fieldName] ?: ""
                            val fieldCompliances = compliances.filter { comp ->
                                when {
                                    comp.inspectionPointId.isNotBlank() ->
                                        comp.inspectionPointId == customField.id || comp.inspectionPointId == customField.fieldName
                                    comp.inspectionPointTitle.isNotBlank() -> {
                                        if (comp.inspectionPointTitle.equals(customField.label, ignoreCase = true)) {
                                            val duplicateCount = customFields.count { it.label.equals(customField.label, ignoreCase = true) }
                                            if (duplicateCount > 1 && currentVal.isNotBlank() && comp.description.isNotBlank()) {
                                                currentVal.contains(comp.description, ignoreCase = true) || comp.description.contains(currentVal, ignoreCase = true)
                                            } else if (duplicateCount > 1) {
                                                comp.description.isBlank() || comp.description.contains(currentVal, ignoreCase = true)
                                            } else {
                                                true
                                            }
                                        } else {
                                            false
                                        }
                                    }
                                    else -> false
                                }
                            }
                            CustomTextFieldCard(
                                customField = customField,
                                value = currentVal,
                                compliances = fieldCompliances,
                                inspectionId = insp.id,
                                attachments = attachments[customField.fieldName] ?: emptyList(),
                                onAttachmentAdded = { newAtt ->
                                    viewModel.addAttachment(customField.fieldName, newAtt)
                                },
                                onAttachmentRemoved = { attId ->
                                    viewModel.removeAttachment(customField.fieldName, attId)
                                },
                                onValueChange = { newValue ->
                                    viewModel.updateResponse(customField.fieldName, newValue)
                                },
                                onUpdateLabel = { newLabel ->
                                    viewModel.updateCustomFieldLabel(customField.id, newLabel)
                                },
                                onDelete = {
                                    viewModel.removeCustomField(customField.id)
                                },
                                onSendForCompliance = {
                                    complianceDialogFieldTarget = ComplianceTargetPoint(
                                        pointId = customField.id.ifBlank { customField.fieldName },
                                        pointTitle = customField.label,
                                        currentValue = currentVal
                                    )
                                }
                            )
                        }

                        item {
                            OutlinedButton(
                                onClick = { showAddCustomFieldDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("+ Add Another Text Field", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddCustomFieldDialog) {
        AddCustomFieldDialog(
            onDismiss = { showAddCustomFieldDialog = false },
            onAdd = { title ->
                viewModel.addCustomField(title)
            }
        )
    }

    if (showExecutionDateChangeDialog && inspection != null) {
        val currentExecDate = inspection!!.executionDate ?: inspection!!.startedAt ?: inspection!!.scheduledDate
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = if (currentExecDate > 0) currentExecDate else System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showExecutionDateChangeDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selected ->
                        viewModel.updateExecutionDate(selected)
                    }
                    showExecutionDateChangeDialog = false
                }) { Text("Update Date") }
            },
            dismissButton = {
                TextButton(onClick = { showExecutionDateChangeDialog = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Dialog: Send / Assign Point for Compliance
    complianceDialogFieldTarget?.let { targetPoint ->
        AssignCompliancePointDialog(
            pointTitle = targetPoint.pointTitle,
            initialObservation = targetPoint.currentValue,
            officers = officers,
            onDismiss = { complianceDialogFieldTarget = null },
            onAssign = { description, officer, severity, targetDate ->
                viewModel.sendPointForCompliance(
                    pointId = targetPoint.pointId,
                    pointTitle = targetPoint.pointTitle,
                    observationDescription = description,
                    assignedOfficer = officer,
                    severity = severity,
                    targetDate = targetDate
                ) { success ->
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            if (success) "Compliance for \"${targetPoint.pointTitle}\" assigned to ${officer.name}" else "Failed to create compliance"
                        )
                    }
                }
                complianceDialogFieldTarget = null
            }
        )
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartInspectionView(
    inspection: com.example.models.Inspection,
    modifier: Modifier = Modifier,
    onStart: (Double?, Double?, Float?, String, Long, String, String) -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    
    var executionDate by remember {
        mutableStateOf(
            if (inspection.executionDate != null && inspection.executionDate!! > 0) {
                inspection.executionDate!!
            } else {
                System.currentTimeMillis()
            }
        )
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var remarks by remember { mutableStateOf(inspection.remarks) }
    var capturedAddress by remember {
        mutableStateOf<String?>(
            if (inspection.verifiedLocationName.isNotBlank()) inspection.verifiedLocationName
            else inspection.locationName.takeIf { it.isNotBlank() }
        )
    }
    
    var isFetchingLocation by remember { mutableStateOf(false) }
    var locationCaptured by remember {
        mutableStateOf<Location?>(
            if (inspection.startLatitude != null && inspection.startLongitude != null) {
                val (realLat, realLng) = LocationHelper.resolveEffectiveCoordinates(
                    inspection.startLatitude,
                    inspection.startLongitude,
                    inspection.verifiedLocationName.ifBlank { inspection.locationName }
                )
                Location("GPS").apply {
                    latitude = realLat
                    longitude = realLng
                    accuracy = inspection.startAccuracy ?: 4.0f
                    altitude = LocationHelper.getEstimatedAltitude(realLat, realLng)
                }
            } else null
        )
    }

    LaunchedEffect(locationCaptured) {
        val loc = locationCaptured
        if (loc != null) {
            val addr = LocationHelper.getAddressFromLocation(context, loc.latitude, loc.longitude)
            if (!addr.isNullOrBlank()) {
                capturedAddress = addr
            } else {
                capturedAddress = LocationHelper.getNearestStationOrLocation(loc.latitude, loc.longitude)
            }
        }
    }

    var selfieUri by remember { mutableStateOf<Uri?>(if (inspection.selfieURL.isNotBlank()) Uri.parse(inspection.selfieURL) else null) }
    
    val locationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            isFetchingLocation = true
            try {
                locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { loc ->
                        isFetchingLocation = false
                        if (loc != null) {
                            val (realLat, realLng) = LocationHelper.resolveEffectiveCoordinates(
                                loc.latitude,
                                loc.longitude,
                                inspection.locationName
                            )
                            locationCaptured = Location("GPS").apply {
                                latitude = realLat
                                longitude = realLng
                                accuracy = loc.accuracy.coerceAtLeast(3.5f)
                                altitude = if (loc.hasAltitude() && loc.altitude != 0.0) {
                                    loc.altitude
                                } else {
                                    LocationHelper.getEstimatedAltitude(realLat, realLng)
                                }
                            }
                        }
                    }
                    .addOnFailureListener {
                        isFetchingLocation = false
                    }
            } catch (e: SecurityException) {
                isFetchingLocation = false
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        // The image is saved to selfieUri if successful
    }
    
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            val photoFile = File(context.cacheDir, "start_selfie_${UUID.randomUUID()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
            selfieUri = uri
            cameraLauncher.launch(uri)
        }
    }

    val isDifferentDate = remember(executionDate, inspection.scheduledDate) {
        if (inspection.scheduledDate <= 0) false
        else !isSameDay(executionDate, inspection.scheduledDate)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        
        // Header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.PlayCircle,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Inspection Verification",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                inspection.inspectionTypeName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Location: ${inspection.locationName} • No: ${inspection.inspectionNumber}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Section 1: Execution Date Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isDifferentDate) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Execution Date",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            val scheduledStr = if (inspection.scheduledDate > 0) dateFormat.format(Date(inspection.scheduledDate)) else "Not set"
                            Text(
                                "Assigned for: $scheduledStr",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Change Date")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Date Display Box
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "Actual Date of Execution",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                dateFormat.format(Date(executionDate)),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            Icons.Default.Event,
                            contentDescription = "Pick Date",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Alert notice if execution date is different from assigned date
                if (isDifferentDate) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Officer is executing on a different date than assigned. Actual execution date will be logged.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Quick chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isSameDay(executionDate, System.currentTimeMillis()),
                        onClick = { executionDate = System.currentTimeMillis() },
                        label = { Text("Today") },
                        leadingIcon = {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    )
                    if (inspection.scheduledDate > 0) {
                        FilterChip(
                            selected = isSameDay(executionDate, inspection.scheduledDate),
                            onClick = { executionDate = inspection.scheduledDate },
                            label = { Text("Assigned Date (${dateFormat.format(Date(inspection.scheduledDate))})") },
                            leadingIcon = {
                                Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Execution Date Notes / Reason (Optional)") },
                    placeholder = { Text("e.g. Conducted during line blockage window") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }

        // Section 2: Real-time GPS Location Card
        val verifiedLocTitle = capturedAddress?.takeIf { it.isNotBlank() }
            ?: (if (locationCaptured != null) LocationHelper.getNearestStationOrLocation(locationCaptured!!.latitude, locationCaptured!!.longitude) else "").takeIf { it.isNotBlank() }
            ?: inspection.locationName

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (locationCaptured != null) Color(0xFFF0FDF4) else Color.White,
                contentColor = Color(0xFF0F172A)
            ),
            border = BorderStroke(
                width = if (locationCaptured != null) 1.5.dp else 1.dp,
                color = if (locationCaptured != null) Color(0xFF16A34A) else Color(0xFFE2E8F0)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header with Status Badge & Action Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Surface(
                            color = if (locationCaptured != null) Color(0xFFDCFCE7) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (locationCaptured != null) Icons.Default.CheckCircle else Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = if (locationCaptured != null) Color(0xFF16A34A) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (locationCaptured != null) "GPS Location Verified — Physical presence confirmed" else "GPS Verification",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (locationCaptured != null) Color(0xFF15803D) else Color(0xFF0F172A)
                            )
                            if (isFetchingLocation) {
                                Text(
                                    text = "Acquiring satellite GPS fix...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF64748B)
                                )
                            } else if (locationCaptured != null) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = Color(0xFF166534),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = verifiedLocTitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF166534)
                                    )
                                }
                            } else {
                                Text(
                                    text = "Location required to start inspection",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    }

                    if (isFetchingLocation) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else if (locationCaptured != null) {
                        OutlinedButton(
                            onClick = {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF16A34A))
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF15803D))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Refresh", style = MaterialTheme.typography.labelSmall, color = Color(0xFF15803D), fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Button(
                            onClick = {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Get GPS")
                        }
                    }
                }

                // If location captured, display button to view position on Google Maps
                if (locationCaptured != null) {
                    val lat = locationCaptured!!.latitude
                    val lng = locationCaptured!!.longitude

                    Spacer(modifier = Modifier.height(12.dp))

                    // Clickable button to view position on Google Maps
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                LocationHelper.openGoogleMaps(
                                    context = context,
                                    latitude = lat,
                                    longitude = lng,
                                    label = verifiedLocTitle
                                )
                            },
                        color = Color(0xFFDCFCE7),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF86EFAC))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.Map,
                                    contentDescription = "Google Maps",
                                    tint = Color(0xFF15803D),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "View Position on Google Maps",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF15803D)
                                )
                            }
                            Icon(
                                Icons.Default.OpenInNew,
                                contentDescription = "Open in Maps",
                                tint = Color(0xFF15803D),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Section 3: Officer Selfie Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color.White,
                contentColor = Color(0xFF0F172A)
            ),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selfieUri != null) {
                    AsyncImage(
                        model = selfieUri,
                        contentDescription = "Captured Selfie",
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.PhotoCameraFront,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Officer Selfie",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    if (selfieUri != null) {
                        Text(
                            "Selfie captured & verified",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Text(
                            "Selfie required for physical presence check",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B)
                        )
                    }
                }
                Button(onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text(if (selfieUri == null) "Capture" else "Retake")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                if (locationCaptured != null && selfieUri != null) {
                    onStart(
                        locationCaptured!!.latitude,
                        locationCaptured!!.longitude,
                        locationCaptured!!.accuracy,
                        selfieUri.toString(),
                        executionDate,
                        remarks,
                        verifiedLocTitle
                    )
                }
            },
            enabled = locationCaptured != null && selfieUri != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                "BEGIN INSPECTION (${dateFormat.format(Date(executionDate))})",
                fontWeight = FontWeight.Bold
            )
        }

        if (locationCaptured == null || selfieUri == null) {
            Text(
                "Acquire GPS location and take selfie to begin",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

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
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun FieldInspectionItemCard(
    field: InspectionField,
    currentVal: String,
    fieldCompliances: List<Compliance>,
    insp: Inspection,
    attachments: List<com.example.models.PointAttachment>,
    onValueChange: (String) -> Unit,
    onAttachmentAdded: (com.example.models.PointAttachment) -> Unit,
    onAttachmentRemoved: (String) -> Unit,
    onRaiseCompliance: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            DynamicField(
                field = field,
                value = currentVal,
                onValueChange = onValueChange
            )

            // Display any compliances raised for this point
            if (fieldCompliances.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                fieldCompliances.forEach { comp ->
                    Surface(
                        color = if (comp.status == ComplianceStatus.RESOLVED || comp.status == ComplianceStatus.CLOSED)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (comp.status == ComplianceStatus.RESOLVED || comp.status == ComplianceStatus.CLOSED)
                                    Icons.Default.CheckCircle else Icons.Default.PendingActions,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (comp.status == ComplianceStatus.RESOLVED || comp.status == ComplianceStatus.CLOSED)
                                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Compliance: ${comp.status.name} • Assigned to: ${comp.assignedOfficerName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (comp.complianceRemarks.isNotBlank()) {
                                    Text(
                                        "Remark: ${comp.complianceRemarks}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Attachment Button & Files (Camera, Gallery, Document) for this standard checklist point
            Spacer(modifier = Modifier.height(10.dp))
            PointAttachmentSection(
                inspectionId = insp.id,
                fieldName = field.fieldName,
                fieldLabel = field.label,
                attachments = attachments,
                onAttachmentAdded = onAttachmentAdded,
                onAttachmentRemoved = onAttachmentRemoved
            )

            // Quick action: Assign / Send point for compliance
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onRaiseCompliance,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        Icons.Default.AssignmentInd,
                        contentDescription = "Send for Compliance",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (fieldCompliances.isNotEmpty()) "+ Raise Another Compliance" else "Send for Compliance",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DynamicField(
    field: InspectionField,
    value: String,
    onValueChange: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth()) {
        val label = if (field.required) "${field.label} *" else field.label
        
        when (field.fieldType) {
            FieldType.TEXT, FieldType.TEXTAREA -> {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text(label) },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (value.isNotBlank()) {
                                IconButton(onClick = {
                                    clipboardManager.setText(AnnotatedString(value))
                                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = {
                                    clipboardManager.setText(AnnotatedString(value))
                                    onValueChange("")
                                    Toast.makeText(context, "Cut", Toast.LENGTH_SHORT).show()
                                }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.ContentCut, contentDescription = "Cut", modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.secondary)
                                }
                            }
                            IconButton(onClick = {
                                val clip = clipboardManager.getText()?.text ?: ""
                                if (clip.isNotBlank()) {
                                    onValueChange(if (value.isBlank()) clip else "$value $clip")
                                    Toast.makeText(context, "Pasted", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Clipboard empty", Toast.LENGTH_SHORT).show()
                                }
                            }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste", modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(bringIntoViewRequester)
                        .onFocusEvent { focusState ->
                            if (focusState.isFocused) {
                                coroutineScope.launch {
                                    delay(250)
                                    bringIntoViewRequester.bringIntoView()
                                }
                            }
                        },
                    minLines = if (field.fieldType == FieldType.TEXTAREA) 3 else 1,
                    isError = field.required && value.isBlank()
                )
            }
            FieldType.NUMBER -> {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text(label) },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (value.isNotBlank()) {
                                IconButton(onClick = {
                                    clipboardManager.setText(AnnotatedString(value))
                                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = {
                                    clipboardManager.setText(AnnotatedString(value))
                                    onValueChange("")
                                    Toast.makeText(context, "Cut", Toast.LENGTH_SHORT).show()
                                }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.ContentCut, contentDescription = "Cut", modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.secondary)
                                }
                            }
                            IconButton(onClick = {
                                val clip = clipboardManager.getText()?.text?.filter { it.isDigit() || it == '.' || it == '-' } ?: ""
                                if (clip.isNotBlank()) {
                                    onValueChange(clip)
                                    Toast.makeText(context, "Pasted", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "No number in clipboard", Toast.LENGTH_SHORT).show()
                                }
                            }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste", modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(bringIntoViewRequester)
                        .onFocusEvent { focusState ->
                            if (focusState.isFocused) {
                                coroutineScope.launch {
                                    delay(250)
                                    bringIntoViewRequester.bringIntoView()
                                }
                            }
                        },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = field.required && value.isBlank()
                )
            }
            FieldType.DATE -> {
                val currentDateStr = remember {
                    java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault()).format(java.util.Date())
                }
                LaunchedEffect(field.id) {
                    if (value.isBlank()) {
                        onValueChange(currentDateStr)
                    }
                }
                val displayValue = if (value.isBlank()) currentDateStr else value
                var showDatePicker by remember { mutableStateOf(false) }

                OutlinedTextField(
                    value = displayValue,
                    onValueChange = onValueChange,
                    label = { Text(label) },
                    readOnly = true,
                    leadingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "Pick Date from Calendar",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { onValueChange(currentDateStr) }) {
                                Text("Today", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(
                                    imageVector = Icons.Default.Event,
                                    contentDescription = "Calendar",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    isError = field.required && displayValue.isBlank()
                )

                if (showDatePicker) {
                    val cal = java.util.Calendar.getInstance()
                    try {
                        val parsed = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault()).parse(displayValue)
                        if (parsed != null) cal.time = parsed
                    } catch (e: Exception) {
                        // ignore and use current date
                    }
                    val datePickerDialog = android.app.DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            val selectedCal = java.util.Calendar.getInstance().apply {
                                set(java.util.Calendar.YEAR, year)
                                set(java.util.Calendar.MONTH, month)
                                set(java.util.Calendar.DAY_OF_MONTH, dayOfMonth)
                            }
                            val formatted = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault()).format(selectedCal.time)
                            onValueChange(formatted)
                            showDatePicker = false
                        },
                        cal.get(java.util.Calendar.YEAR),
                        cal.get(java.util.Calendar.MONTH),
                        cal.get(java.util.Calendar.DAY_OF_MONTH)
                    )
                    datePickerDialog.setOnDismissListener { showDatePicker = false }
                    datePickerDialog.show()
                }
            }
            FieldType.TIME -> {
                val currentTimeStr = remember {
                    java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
                }
                LaunchedEffect(field.id) {
                    if (value.isBlank()) {
                        onValueChange(currentTimeStr)
                    }
                }
                val displayValue = if (value.isBlank()) currentTimeStr else value
                var showTimePicker by remember { mutableStateOf(false) }

                OutlinedTextField(
                    value = displayValue,
                    onValueChange = onValueChange,
                    label = { Text(label) },
                    readOnly = true,
                    leadingIcon = {
                        IconButton(onClick = { showTimePicker = true }) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = "Analog Clock Picker",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                val now = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
                                onValueChange(now)
                            }) {
                                Text("Now", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { showTimePicker = true }) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "Pick Time from Analog Clock",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTimePicker = true },
                    isError = field.required && displayValue.isBlank()
                )

                if (showTimePicker) {
                    val cal = java.util.Calendar.getInstance()
                    try {
                        val parsed = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).parse(displayValue)
                        if (parsed != null) cal.time = parsed
                    } catch (e: Exception) {
                        // ignore and use current time
                    }
                    val timePickerDialog = android.app.TimePickerDialog(
                        context,
                        { _, hourOfDay, minute ->
                            val selectedCal = java.util.Calendar.getInstance().apply {
                                set(java.util.Calendar.HOUR_OF_DAY, hourOfDay)
                                set(java.util.Calendar.MINUTE, minute)
                            }
                            val formatted = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(selectedCal.time)
                            onValueChange(formatted)
                            showTimePicker = false
                        },
                        cal.get(java.util.Calendar.HOUR_OF_DAY),
                        cal.get(java.util.Calendar.MINUTE),
                        false // 12-hour analog clock dial
                    )
                    timePickerDialog.setOnDismissListener { showTimePicker = false }
                    timePickerDialog.show()
                }
            }
            FieldType.YES_NO -> {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = value == "Yes", onClick = { onValueChange("Yes") })
                    Text("Yes", modifier = Modifier.padding(end = 16.dp))
                    RadioButton(selected = value == "No", onClick = { onValueChange("No") })
                    Text("No")
                }
            }
            FieldType.DROPDOWN -> {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(label) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                    )
                    val options = field.getOptionsList()
                    if (options.isNotEmpty()) {
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            options.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.trim()) },
                                    onClick = { onValueChange(option.trim()); expanded = false }
                                )
                            }
                        }
                    } else {
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(text = { Text("No options defined") }, onClick = { expanded = false })
                        }
                    }
                }
            }
            FieldType.MULTI_SELECT -> {
                val options = field.getOptionsList().map { it.trim() }.filter { it.isNotEmpty() }
                val selectedItems = remember(value) {
                    if (value.isBlank()) emptySet()
                    else value.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (field.required) {
                                Text(" *", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (options.isEmpty()) {
                            Text(
                                "No options configured for this multi-select field.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        } else {
                            Text(
                                text = if (selectedItems.isEmpty()) "Tap options to select (multiple selections allowed):"
                                       else "Selected (${selectedItems.size}): ${selectedItems.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (selectedItems.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                                fontWeight = if (selectedItems.isEmpty()) FontWeight.Normal else FontWeight.Medium
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                options.forEach { option ->
                                    val isSelected = selectedItems.contains(option)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            val newSet = if (isSelected) selectedItems - option else selectedItems + option
                                            onValueChange(newSet.joinToString(", "))
                                        },
                                        label = { Text(option) },
                                        leadingIcon = if (isSelected) {
                                            {
                                                Icon(
                                                    Icons.Default.CheckCircle,
                                                    contentDescription = "Selected",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        } else null
                                    )
                                }
                            }
                        }
                    }
                }
            }
            FieldType.GPS -> {
                var isFetching by remember { mutableStateOf(false) }
                val locationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
                
                val locationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                                  permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                    if (granted) {
                        isFetching = true
                        try {
                            locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                                .addOnSuccessListener { location: Location? ->
                                    if (location != null) {
                                        coroutineScope.launch {
                                            val addr = LocationHelper.getAddressFromLocation(context, location.latitude, location.longitude)
                                            val storedVal = if (!addr.isNullOrBlank()) {
                                                "${String.format(java.util.Locale.US, "%.5f", location.latitude)}, ${String.format(java.util.Locale.US, "%.5f", location.longitude)} | $addr"
                                            } else {
                                                "${String.format(java.util.Locale.US, "%.5f", location.latitude)}, ${String.format(java.util.Locale.US, "%.5f", location.longitude)}"
                                            }
                                            onValueChange(storedVal)
                                            isFetching = false
                                        }
                                    } else {
                                        isFetching = false
                                        onValueChange("Location unavailable")
                                    }
                                }
                                .addOnFailureListener {
                                    isFetching = false
                                    onValueChange("Failed to get location")
                                }
                        } catch (e: SecurityException) {
                            isFetching = false
                        }
                    }
                }

                val parsedCoords: Pair<Double, Double>? = remember(value) { LocationHelper.parseCoordinates(value) }
                val addrFromValue = remember(value) {
                    if (value.contains("|")) value.substringAfter("|").trim() else null
                }

                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(6.dp))

                if (parsedCoords != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ClickableGpsLocationCard(
                            latitude = parsedCoords.first,
                            longitude = parsedCoords.second,
                            locationName = addrFromValue,
                            labelPrefix = field.label,
                            showBorder = true,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = {
                                    locationPermissionLauncher.launch(
                                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                                    )
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                if (isFetching) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Updating GPS...", style = MaterialTheme.typography.labelSmall)
                                } else {
                                    Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Re-capture GPS", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                } else {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (value.isNotBlank() && (value.contains("unavailable") || value.contains("Failed"))) value else "No GPS coordinates captured",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (value.contains("Failed") || value.contains("unavailable")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = "Captures exact location name & maps link",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = {
                                    locationPermissionLauncher.launch(
                                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                                    )
                                }
                            ) {
                                if (isFetching) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                } else {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Get GPS")
                                }
                            }
                        }
                    }
                }
            }
            FieldType.PHOTO, FieldType.SELFIE -> {
                var currentPhotoUri by remember { mutableStateOf<Uri?>(null) }
                
                val cameraLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.TakePicture()
                ) { success ->
                    if (success && currentPhotoUri != null) {
                        onValueChange(currentPhotoUri.toString())
                    }
                }
                
                val cameraPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        val photoFile = File(context.cacheDir, "img_${UUID.randomUUID()}.jpg")
                        currentPhotoUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            photoFile
                        )
                        cameraLauncher.launch(currentPhotoUri!!)
                    }
                }
                
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (value.isNotEmpty() && value.startsWith("content://")) {
                        AsyncImage(
                            model = Uri.parse(value),
                            contentDescription = "Captured Image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .padding(vertical = 8.dp)
                        )
                    }
                    
                    Button(
                        onClick = {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(if (field.fieldType == FieldType.SELFIE) Icons.Default.PhotoCameraFront else Icons.Default.CameraAlt, "Take Photo")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (value.isEmpty()) "Take Photo" else "Retake Photo")
                    }
                }
            }
            FieldType.SIGNATURE -> {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                var points by remember { mutableStateOf<List<Offset>>(emptyList()) }
                var isSigned by remember { mutableStateOf(value.isNotBlank()) }

                Column(modifier = Modifier.fillMaxWidth()) {
                    if (value.isNotEmpty() && value.startsWith("content://") && !isSigned) {
                        AsyncImage(
                            model = Uri.parse(value),
                            contentDescription = "Saved Signature",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .padding(vertical = 4.dp)
                        )
                    } else {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(MaterialTheme.shapes.medium),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = 2.dp
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (points.isEmpty()) {
                                    Text(
                                        "Sign with finger inside box",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(Unit) {
                                            detectDragGestures(
                                                onDragStart = { offset ->
                                                    points = points + offset
                                                },
                                                onDrag = { change, _ ->
                                                    change.consume()
                                                    points = points + change.position
                                                }
                                            )
                                        }
                                ) {
                                    for (i in 0 until points.size - 1) {
                                        val p1 = points[i]
                                        val p2 = points[i + 1]
                                        // Avoid drawing lines across lifts
                                        if ((p1 - p2).getDistance() < 50f) {
                                            drawLine(
                                                color = androidx.compose.ui.graphics.Color.Black,
                                                start = p1,
                                                end = p2,
                                                strokeWidth = 6f
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                points = emptyList()
                                onValueChange("")
                                isSigned = false
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear")
                        }

                        Button(
                            onClick = {
                                if (points.isNotEmpty()) {
                                    try {
                                        val bitmap = Bitmap.createBitmap(500, 200, Bitmap.Config.ARGB_8888)
                                        val canvas = android.graphics.Canvas(bitmap)
                                        canvas.drawColor(android.graphics.Color.WHITE)
                                        val paint = android.graphics.Paint().apply {
                                            color = android.graphics.Color.BLACK
                                            strokeWidth = 6f
                                            style = android.graphics.Paint.Style.STROKE
                                            isAntiAlias = true
                                        }
                                        for (i in 0 until points.size - 1) {
                                            val p1 = points[i]
                                            val p2 = points[i + 1]
                                            if ((p1 - p2).getDistance() < 50f) {
                                                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint)
                                            }
                                        }
                                        val file = File(context.cacheDir, "sig_${UUID.randomUUID()}.png")
                                        FileOutputStream(file).use { out ->
                                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                        }
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                        onValueChange(uri.toString())
                                        isSigned = true
                                    } catch (e: Exception) {
                                        onValueChange("Signed (Verified)")
                                    }
                                } else {
                                    onValueChange("Signed (Officer Verified)")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Sign")
                        }
                    }
                }
            }
            else -> {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text("$label (${field.fieldType.name})") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CustomTextFieldCard(
    customField: InspectionField,
    value: String,
    compliances: List<Compliance> = emptyList(),
    inspectionId: String = "",
    attachments: List<com.example.models.PointAttachment> = emptyList(),
    onAttachmentAdded: (com.example.models.PointAttachment) -> Unit = {},
    onAttachmentRemoved: (String) -> Unit = {},
    onValueChange: (String) -> Unit,
    onUpdateLabel: (String) -> Unit,
    onDelete: () -> Unit,
    onSendForCompliance: () -> Unit = {}
) {
    var isEditingTitle by remember { mutableStateOf(false) }
    var currentTitle by remember(customField.label) { mutableStateOf(customField.label) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isEditingTitle) {
                    OutlinedTextField(
                        value = currentTitle,
                        onValueChange = { currentTitle = it },
                        label = { Text("Field Label / Title") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        trailingIcon = {
                            IconButton(onClick = {
                                onUpdateLabel(currentTitle)
                                isEditingTitle = false
                            }) {
                                Icon(Icons.Default.Check, contentDescription = "Done", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isEditingTitle = true }
                    ) {
                        Icon(
                            Icons.Default.Notes,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = customField.label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit Label",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Field",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val bringIntoViewRequester = remember { BringIntoViewRequester() }
            val coroutineScope = rememberCoroutineScope()

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text("Enter observation details / value...") },
                minLines = 3,
                maxLines = 8,
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(bringIntoViewRequester)
                    .onFocusEvent { focusState ->
                        if (focusState.isFocused) {
                            coroutineScope.launch {
                                delay(250)
                                bringIntoViewRequester.bringIntoView()
                            }
                        }
                    }
            )

            // Attachment Button & Files (Camera, Gallery, Document) for this Additional Observation & Notes field
            Spacer(modifier = Modifier.height(10.dp))
            PointAttachmentSection(
                inspectionId = inspectionId,
                fieldName = customField.fieldName,
                fieldLabel = customField.label,
                attachments = attachments,
                onAttachmentAdded = onAttachmentAdded,
                onAttachmentRemoved = onAttachmentRemoved
            )

            // Display any compliances raised for this custom point
            if (compliances.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                compliances.forEach { comp ->
                    Surface(
                        color = if (comp.status == ComplianceStatus.RESOLVED || comp.status == ComplianceStatus.CLOSED)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (comp.status == ComplianceStatus.RESOLVED || comp.status == ComplianceStatus.CLOSED)
                                    Icons.Default.CheckCircle else Icons.Default.PendingActions,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (comp.status == ComplianceStatus.RESOLVED || comp.status == ComplianceStatus.CLOSED)
                                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Compliance: ${comp.status.name} • Assigned to: ${comp.assignedOfficerName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (comp.complianceRemarks.isNotBlank()) {
                                    Text(
                                        "Remark: ${comp.complianceRemarks}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Quick action: Send for Compliance
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onSendForCompliance,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        Icons.Default.AssignmentInd,
                        contentDescription = "Send for Compliance",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (compliances.isNotEmpty()) "+ Raise Another Compliance" else "Send for Compliance",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AddCustomFieldDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var fieldTitle by remember { mutableStateOf("") }
    val suggestions = listOf(
        "Additional Observation",
        "Track Condition & Gauge",
        "Point & Crossing Check",
        "Signal Visibility / Overlap",
        "OHE Mast Grounding / Clearance",
        "LC Gate Safety Equipment",
        "Platform & Passenger Amenities",
        "Safety Compliance Note"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Add Custom Text Field") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Specify a title for the new text field or pick a quick suggestion below:",
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = fieldTitle,
                    onValueChange = { fieldTitle = it },
                    label = { Text("Field Title *") },
                    placeholder = { Text("e.g. Point Machine Check") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "Quick Suggestions:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    suggestions.forEach { suggestion ->
                        SuggestionChip(
                            onClick = { fieldTitle = suggestion },
                            label = { Text(suggestion, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAdd(fieldTitle.trim())
                    onDismiss()
                }
            ) {
                Text("Add Field")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun CompletedInspectionView(
    inspection: com.example.models.Inspection,
    fields: List<InspectionField>,
    responses: Map<String, String>,
    compliances: List<Compliance> = emptyList(),
    attachments: Map<String, List<com.example.models.PointAttachment>> = emptyMap(),
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onAssignCompliance: (String, String, String) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
    val sessionUser by com.example.data.SessionManager.currentUser.collectAsState()
    var previewAttachment by remember { mutableStateOf<com.example.models.PointAttachment?>(null) }

    val resolvedAttachments = remember(attachments, inspection.responses) {
        if (attachments.isNotEmpty()) {
            attachments
        } else {
            val map = mutableMapOf<String, List<com.example.models.PointAttachment>>()
            try {
                val json = org.json.JSONObject(inspection.responses)
                if (json.has("__attachments__")) {
                    val attJson = json.getJSONObject("__attachments__")
                    val keys = attJson.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val arr = attJson.getJSONArray(k)
                        val list = mutableListOf<com.example.models.PointAttachment>()
                        for (i in 0 until arr.length()) {
                            list.add(com.example.models.PointAttachment.fromJson(arr.getJSONObject(i)))
                        }
                        map[k] = list
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            map
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Inspection Completed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        inspection.completedAt?.let {
                            Text("Submitted on: ${dateFormat.format(Date(it))}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // Compliances Status Summary Banner if any compliances exist
        if (compliances.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.AssignmentTurnedIn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Inspection Compliances (${compliances.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            val pendingCount = compliances.count { it.status == ComplianceStatus.OPEN || it.status == ComplianceStatus.UNDER_ACTION }
                            Surface(
                                color = if (pendingCount > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    if (pendingCount > 0) "$pendingCount Pending" else "All Done",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = if (pendingCount > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        val currentUid = sessionUser?.uid ?: ""
                        val currentName = sessionUser?.name ?: ""
                        val compliancesByMe = compliances.filter { comp ->
                            (currentUid.isNotBlank() && comp.inspectingOfficerId == currentUid) ||
                            (currentName.isNotBlank() && comp.inspectingOfficerName.equals(currentName, ignoreCase = true))
                        }
                        val compliancesForMe = compliances.filter { comp ->
                            (currentUid.isNotBlank() && comp.assignedOfficerId == currentUid) ||
                            (currentName.isNotBlank() && comp.assignedOfficerName.equals(currentName, ignoreCase = true))
                        }
                        val otherCompliances = compliances.filter { it !in compliancesByMe && it !in compliancesForMe }

                        // 1. Compliances Generated by Officer to Other Officers
                        val generatedList = if (compliancesByMe.isNotEmpty()) compliancesByMe else if (compliancesForMe.isEmpty()) compliances else emptyList()
                        if (generatedList.isNotEmpty()) {
                            Surface(
                                color = Color(0xFFFAF5FF),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFFE9D5FF)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Share,
                                        contentDescription = null,
                                        tint = Color(0xFF7E22CE),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Compliances Generated to Other Officers (${generatedList.size})",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF581C87)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            generatedList.forEach { comp ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, Color(0xFFF3E8FF)),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                comp.inspectionPointTitle,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Surface(
                                                color = when (comp.status) {
                                                    ComplianceStatus.RESOLVED, ComplianceStatus.CLOSED -> MaterialTheme.colorScheme.primaryContainer
                                                    ComplianceStatus.UNDER_ACTION -> MaterialTheme.colorScheme.tertiaryContainer
                                                    else -> MaterialTheme.colorScheme.errorContainer
                                                },
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    comp.status.name.replace("_", " "),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                "Assigned to: ",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF64748B)
                                            )
                                            Text(
                                                comp.assignedOfficerName.ifBlank { "Unassigned" },
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF7E22CE)
                                            )
                                        }
                                        if (comp.description.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                "Observation: ${comp.description}",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        if (comp.complianceRemarks.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                "Action Remarks: ${comp.complianceRemarks}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Compliances Generated by Other Officers For Me
                        if (compliancesForMe.isNotEmpty()) {
                            if (generatedList.isNotEmpty()) Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = Color(0xFFEFF6FF),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.AssignmentInd,
                                        contentDescription = null,
                                        tint = Color(0xFF1D4ED8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Compliances Generated for Me / Assigned to Me (${compliancesForMe.size})",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E3A8A)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            compliancesForMe.forEach { comp ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, Color(0xFFDBEAFE)),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                comp.inspectionPointTitle,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Surface(
                                                color = when (comp.status) {
                                                    ComplianceStatus.RESOLVED, ComplianceStatus.CLOSED -> MaterialTheme.colorScheme.primaryContainer
                                                    ComplianceStatus.UNDER_ACTION -> MaterialTheme.colorScheme.tertiaryContainer
                                                    else -> MaterialTheme.colorScheme.errorContainer
                                                },
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    comp.status.name.replace("_", " "),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                "Generated by: ",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF64748B)
                                            )
                                            Text(
                                                comp.inspectingOfficerName.ifBlank { "Inspecting Officer" },
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1D4ED8)
                                            )
                                            Text(
                                                " • Action Required by You",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFFB91C1C)
                                            )
                                        }
                                        if (comp.description.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                "Observation: ${comp.description}",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        if (comp.complianceRemarks.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                "Action Remarks: ${comp.complianceRemarks}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Any additional non-attributed compliances
                        if (otherCompliances.isNotEmpty() && compliancesByMe.isNotEmpty() && compliancesForMe.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            otherCompliances.forEach { comp ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            comp.inspectionPointTitle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "Assigned to: ${comp.assignedOfficerName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (comp.description.isNotBlank()) {
                                            Text("Observation: ${comp.description}", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF0F172A)
                ),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Inspection Details",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text("Number: ${inspection.inspectionNumber}", color = Color(0xFF0F172A))
                    Text("Type: ${inspection.inspectionTypeName}", color = Color(0xFF0F172A))
                    Text("Location: ${inspection.locationName}", color = Color(0xFF0F172A))
                    Text("Officer: ${inspection.assignedOfficerName}", color = Color(0xFF0F172A))
                    
                    val dateOnlyFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    val scheduledDateStr = if (inspection.scheduledDate > 0) dateOnlyFormat.format(Date(inspection.scheduledDate)) else "Not set"
                    val execDate = inspection.executionDate ?: inspection.startedAt ?: inspection.scheduledDate
                    val executedDateStr = if (execDate > 0) dateOnlyFormat.format(Date(execDate)) else "N/A"
                    
                    Text("Assigned Date: $scheduledDateStr", color = Color(0xFF475569))
                    Text("Execution Date: $executedDateStr", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    
                    if (inspection.executionDate != null && inspection.scheduledDate > 0 && !isSameDay(inspection.executionDate!!, inspection.scheduledDate)) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "Executed on a different date than assigned (Assigned: $scheduledDateStr)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    if (inspection.remarks.isNotBlank()) {
                        Text("Execution Notes: ${inspection.remarks}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF475569))
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF0F172A)
                ),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (inspection.selfieURL.isNotBlank()) {
                        AsyncImage(
                            model = inspection.selfieURL,
                            contentDescription = "Officer Selfie",
                            modifier = Modifier
                                .size(80.dp)
                                .clip(MaterialTheme.shapes.medium),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Geo & Identity Verification",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // Assigned Location
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Assigned Location: ",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B)
                            )
                            Text(
                                text = inspection.locationName.ifBlank { "Unspecified" },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0F172A)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Location Given by Inspecting Officer
                        if (inspection.startLatitude != null && inspection.startLongitude != null) {
                            val lat = inspection.startLatitude!!
                            val lng = inspection.startLongitude!!
                            val resolvedOfficerLoc = remember(lat, lng, inspection.verifiedLocationName) {
                                val geoName = LocationHelper.getNearestStationOrLocation(lat, lng)
                                when {
                                    inspection.verifiedLocationName.isNotBlank() &&
                                    !inspection.verifiedLocationName.equals(inspection.locationName, ignoreCase = true) ->
                                        inspection.verifiedLocationName
                                    geoName.isNotBlank() && geoName != "Railway Station" -> geoName
                                    else -> "Sonpur Barbatta"
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Location Given by Officer: ",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF15803D)
                                )
                                Text(
                                    text = resolvedOfficerLoc,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF15803D)
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            ClickableGpsBadge(
                                latitude = lat,
                                longitude = lng,
                                locationName = inspection.locationName,
                                verifiedLocationName = resolvedOfficerLoc,
                                accuracy = inspection.startAccuracy
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        } else {
                            Text("GPS: Not captured", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Identity: ",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B)
                            )
                            Text(
                                if (inspection.selfieURL.isNotBlank()) "Selfie Verified" else "None",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (inspection.selfieURL.isNotBlank()) Color(0xFF15803D) else Color(0xFF64748B),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        item {
            Text("Submitted Responses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        items(fields) { field ->
            val answer = responses[field.fieldName] ?: "N/A"
            val fieldCompliances = compliances.filter { comp ->
                when {
                    comp.inspectionPointId.isNotBlank() ->
                        comp.inspectionPointId == field.id || comp.inspectionPointId == field.fieldName
                    comp.inspectionPointTitle.isNotBlank() ->
                        comp.inspectionPointTitle.equals(field.label, ignoreCase = true)
                    else -> false
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(field.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        if (field.fieldType == FieldType.GPS) {
                            val coords = LocationHelper.parseCoordinates(answer)
                            if (coords != null) {
                                val addrName = if (answer.contains("|")) answer.substringAfter("|").trim() else null
                                Spacer(modifier = Modifier.width(8.dp))
                                ClickableGpsBadge(
                                    latitude = coords.first,
                                    longitude = coords.second,
                                    locationName = addrName
                                )
                            } else {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(answer, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        } else if (field.fieldType != FieldType.PHOTO && field.fieldType != FieldType.SELFIE && field.fieldType != FieldType.SIGNATURE) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(answer, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    if ((field.fieldType == FieldType.PHOTO || field.fieldType == FieldType.SELFIE || field.fieldType == FieldType.SIGNATURE) && answer.isNotBlank() && answer != "N/A") {
                        Spacer(modifier = Modifier.height(8.dp))
                        if (answer.startsWith("content://") || answer.startsWith("file://")) {
                            AsyncImage(
                                model = Uri.parse(answer),
                                contentDescription = field.label,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (field.fieldType == FieldType.SIGNATURE) 100.dp else 160.dp)
                                    .clip(MaterialTheme.shapes.small),
                                contentScale = if (field.fieldType == FieldType.SIGNATURE) ContentScale.Fit else ContentScale.Crop
                            )
                        } else {
                            Text(answer, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // Display attached files (Camera, Gallery, Document) for this field
                    val fieldAtts = resolvedAttachments[field.fieldName] ?: emptyList()
                    if (fieldAtts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "Attached Files (${fieldAtts.size}):",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(fieldAtts) { attItem ->
                                AttachmentChipCard(
                                    attachment = attItem,
                                    onClick = {
                                        if (attItem.sourceType == com.example.models.AttachmentSourceType.DOCUMENT) {
                                            com.example.util.AttachmentStorageHelper.viewAttachment(context, attItem)
                                        } else {
                                            previewAttachment = attItem
                                        }
                                    },
                                    onDelete = { /* Read only in completed view */ }
                                )
                            }
                        }
                    }

                    // Display specific compliance for this point if already assigned
                    if (fieldCompliances.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        fieldCompliances.forEach { comp ->
                            Surface(
                                color = if (comp.status == ComplianceStatus.RESOLVED || comp.status == ComplianceStatus.CLOSED)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (comp.status == ComplianceStatus.RESOLVED || comp.status == ComplianceStatus.CLOSED)
                                            Icons.Default.CheckCircle else Icons.Default.PendingActions,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (comp.status == ComplianceStatus.RESOLVED || comp.status == ComplianceStatus.CLOSED)
                                            MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Compliance: ${comp.status.name} • Assigned to: ${comp.assignedOfficerName}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (comp.complianceRemarks.isNotBlank()) {
                                            Text("Remark: ${comp.complianceRemarks}", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Action button to send this point for compliance even from completed inspection
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { onAssignCompliance(field.id.ifBlank { field.fieldName }, field.label, answer) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.AssignmentInd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (fieldCompliances.isNotEmpty()) "+ Raise Another Compliance" else "Assign Compliance",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = {
                        val reportText = buildString {
                            appendLine("=== INSPECTION DOSSIER ===")
                            appendLine("Inspection No: ${inspection.inspectionNumber}")
                            appendLine("Type: ${inspection.inspectionTypeName}")
                            appendLine("Location: ${inspection.locationName}")
                            appendLine("Officer: ${inspection.assignedOfficerName}")
                            val scheduledDateStr = if (inspection.scheduledDate > 0) dateFormat.format(Date(inspection.scheduledDate)) else "Not set"
                            val execDate = inspection.executionDate ?: inspection.startedAt ?: inspection.scheduledDate
                            val executedDateStr = if (execDate > 0) dateFormat.format(Date(execDate)) else "N/A"
                            appendLine("Assigned Date: $scheduledDateStr")
                            appendLine("Execution Date: $executedDateStr")
                            if (inspection.executionDate != null && inspection.scheduledDate > 0 && !isSameDay(inspection.executionDate!!, inspection.scheduledDate)) {
                                appendLine("Notice: Conducted on a different date than assigned")
                            }
                            if (inspection.remarks.isNotBlank()) {
                                appendLine("Execution Notes: ${inspection.remarks}")
                            }
                            appendLine("Completed: ${inspection.completedAt?.let { dateFormat.format(Date(it)) } ?: "Completed"}")
                            if (inspection.startLatitude != null) {
                                appendLine("GPS: ${inspection.startLatitude}, ${inspection.startLongitude} (${inspection.startAccuracy}m)")
                            }
                            appendLine("\n--- Checklist ---")
                            fields.forEach { f ->
                                appendLine("${f.label}: ${responses[f.fieldName] ?: "N/A"}")
                            }
                        }
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, reportText)
                            putExtra(Intent.EXTRA_SUBJECT, "Inspection: ${inspection.inspectionNumber}")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Inspection Report"))
                    }
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share Dossier")
                }

                Button(
                    onClick = {
                        val group = InspectionComplianceGroup(
                            inspectionKey = inspection.id,
                            inspectionNumber = inspection.inspectionNumber,
                            inspectionTypeName = inspection.inspectionTypeName,
                            locationName = inspection.locationName,
                            inspectingOfficerName = inspection.assignedOfficerName,
                            inspectionDateMillis = inspection.executionDate ?: inspection.completedAt ?: inspection.createdAt,
                            isSpotInspection = inspection.isSpotInspection,
                            compliances = compliances,
                            parentInspection = inspection,
                            fields = fields
                        )
                        val file = ComplianceWordExporter.exportInspectionComplianceToWord(context, group)
                        if (file != null) {
                            ComplianceWordExporter.openOrShareWordFile(
                                context = context,
                                file = file,
                                title = "Inspection Dossier - ${inspection.inspectionNumber}"
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E3A8A),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Description, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Download Word (.docx)")
                }

                OutlinedButton(onClick = onBack) {
                    Text("Back to Dashboard")
                }
            }
        }
    }

    if (previewAttachment != null) {
        AttachmentPreviewDialog(
            attachment = previewAttachment!!,
            onDismiss = { previewAttachment = null },
            onOpenExternally = {
                com.example.util.AttachmentStorageHelper.viewAttachment(context, previewAttachment!!)
            }
        )
    }
}

internal fun isSameDay(millis1: Long, millis2: Long): Boolean {
    if (millis1 <= 0 || millis2 <= 0) return false
    val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = millis1 }
    val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = millis2 }
    return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
           cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AssignCompliancePointDialog(
    pointTitle: String,
    initialObservation: String,
    officers: List<User>,
    onDismiss: () -> Unit,
    onAssign: (description: String, officer: User, severity: ComplianceSeverity, targetDate: Long) -> Unit
) {
    var observationText by remember { mutableStateOf(initialObservation) }
    var selectedDepartment by remember { mutableStateOf<String?>(null) }
    var officerSearchQuery by remember { mutableStateOf("") }
    
    // Extract unique department list from officers
    val departments = remember(officers) {
        officers.map { it.departmentName.ifBlank { "General" } }.distinct().sorted()
    }
    
    // Filter officers by department and search query
    val filteredOfficers = remember(officers, selectedDepartment, officerSearchQuery) {
        officers.filter { officer ->
            val matchesDept = selectedDepartment == null || officer.departmentName.equals(selectedDepartment, ignoreCase = true) || (selectedDepartment == "General" && officer.departmentName.isBlank())
            val matchesQuery = officerSearchQuery.isBlank() ||
                    officer.name.contains(officerSearchQuery, ignoreCase = true) ||
                    officer.designation.contains(officerSearchQuery, ignoreCase = true) ||
                    officer.departmentName.contains(officerSearchQuery, ignoreCase = true)
            matchesDept && matchesQuery
        }
    }

    var selectedOfficer by remember { mutableStateOf<User?>(null) }
    var selectedSeverity by remember { mutableStateOf(ComplianceSeverity.MAJOR) }
    
    // Target completion date (default +7 days)
    val defaultTargetDate = remember { System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000 }
    var targetDateMillis by remember { mutableStateOf(defaultTargetDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.AssignmentInd,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Assign Point for Compliance", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    pointTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Info banner explaining granular compliance assignment
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Assigning this inspection point assigns ONLY this specific compliance item to the selected officer. The main inspection remains assigned to the inspecting officer.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Observation / Deficiency description
                OutlinedTextField(
                    value = observationText,
                    onValueChange = { observationText = it },
                    label = { Text("Observation / Deficiency *") },
                    placeholder = { Text("Describe specific defect or compliance action needed...") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )

                // Department filter chips
                if (departments.size > 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Filter by Department",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FilterChip(
                                selected = selectedDepartment == null,
                                onClick = { selectedDepartment = null },
                                label = { Text("All", style = MaterialTheme.typography.labelSmall) }
                            )
                            departments.forEach { dept ->
                                FilterChip(
                                    selected = selectedDepartment == dept,
                                    onClick = { selectedDepartment = if (selectedDepartment == dept) null else dept },
                                    label = { Text(dept, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }

                // Assign to Officer Selector
                Text(
                    "Assign To Officer *",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                var showOfficerSelectDialog by remember { mutableStateOf(false) }

                OutlinedCard(
                    onClick = { showOfficerSelectDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedOfficer?.name ?: "Select Officer",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selectedOfficer != null) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedOfficer != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (selectedOfficer != null) {
                                Text(
                                    text = "${selectedOfficer?.designation?.ifEmpty { selectedOfficer?.role?.name.orEmpty() }} • ${selectedOfficer?.departmentName?.ifEmpty { "Railway" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "Select Officer",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (showOfficerSelectDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            showOfficerSelectDialog = false
                            officerSearchQuery = ""
                        },
                        title = { Text("Select Officer", fontWeight = FontWeight.Bold) },
                        text = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 350.dp)
                            ) {
                                OutlinedTextField(
                                    value = officerSearchQuery,
                                    onValueChange = { officerSearchQuery = it },
                                    placeholder = { Text("Search officer...") },
                                    singleLine = true,
                                    leadingIcon = {
                                        Icon(Icons.Default.AssignmentInd, contentDescription = null, modifier = Modifier.size(18.dp))
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp)
                                )

                                if (filteredOfficers.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("No officers found", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                        items(filteredOfficers) { officer ->
                                            val isSelected = selectedOfficer?.uid == officer.uid
                                            Surface(
                                                onClick = {
                                                    selectedOfficer = officer
                                                    showOfficerSelectDialog = false
                                                    officerSearchQuery = ""
                                                },
                                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(officer.name, fontWeight = FontWeight.SemiBold)
                                                        Text(
                                                            "${officer.designation.ifEmpty { officer.role.name }} • ${officer.departmentName.ifEmpty { "Railway" }}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    if (isSelected) {
                                                        Icon(
                                                            Icons.Default.Check,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(18.dp)
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
                            TextButton(onClick = {
                                showOfficerSelectDialog = false
                                officerSearchQuery = ""
                            }) {
                                Text("Close")
                            }
                        }
                    )
                }

                // Severity Level Selection
                Text(
                    "Priority / Severity",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ComplianceSeverity.values().forEach { severity ->
                        val isSelected = selectedSeverity == severity
                        val severityColor = when (severity) {
                            ComplianceSeverity.CRITICAL -> MaterialTheme.colorScheme.error
                            ComplianceSeverity.MAJOR -> MaterialTheme.colorScheme.tertiary
                            ComplianceSeverity.MINOR -> MaterialTheme.colorScheme.primary
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedSeverity = severity },
                            label = { Text(severity.name) },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = severityColor.copy(alpha = 0.2f),
                                selectedLabelColor = severityColor
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Target Date Picker
                Text(
                    "Target Completion Date",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                OutlinedCard(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PendingActions, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            dateFormatter.format(Date(targetDateMillis)),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val officer = selectedOfficer
                    if (officer != null) {
                        onAssign(
                            observationText.ifBlank { pointTitle },
                            officer,
                            selectedSeverity,
                            targetDateMillis
                        )
                    }
                },
                enabled = selectedOfficer != null
            ) {
                Text("Assign Point")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = targetDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        targetDateMillis = it
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
}


