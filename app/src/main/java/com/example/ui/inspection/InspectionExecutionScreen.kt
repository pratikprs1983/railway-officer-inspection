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
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.models.FieldType
import com.example.models.InspectionField
import com.example.models.InspectionStatus
import com.google.android.gms.location.*
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
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.ArrowDropDown
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
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
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
                    onStart = { lat, lng, acc, selfieUri, executionDate, remarks -> 
                        viewModel.startInspection(lat, lng, acc, selfieUri, executionDate, remarks)
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
                                        if (insp.startLatitude != null) {
                                            Text(
                                                "GPS: ${String.format("%.5f", insp.startLatitude)}, ${String.format("%.5f", insp.startLongitude)} (±${insp.startAccuracy?.toInt() ?: 0}m)",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium
                                            )
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

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    DynamicField(
                                        field = field,
                                        value = currentVal,
                                        onValueChange = { newValue ->
                                            viewModel.updateResponse(field.fieldName, newValue)
                                        }
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
                                        attachments = attachments[field.fieldName] ?: emptyList(),
                                        onAttachmentAdded = { newAtt ->
                                            viewModel.addAttachment(field.fieldName, newAtt)
                                        },
                                        onAttachmentRemoved = { attId ->
                                            viewModel.removeAttachment(field.fieldName, attId)
                                        }
                                    )

                                    // Quick action: Assign / Send point for compliance
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(
                                            onClick = {
                                                complianceDialogFieldTarget = ComplianceTargetPoint(
                                                    pointId = field.id.ifBlank { field.fieldName },
                                                    pointTitle = field.label,
                                                    currentValue = currentVal
                                                )
                                            },
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
                    }

                    // Section: Additional Text Fields added by Officer
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Additional Observations & Notes",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${customFields.size} custom field${if (customFields.size == 1) "" else "s"} added by officer",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Button(
                                onClick = { showAddCustomFieldDialog = true },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add Text Field", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Field")
                            }
                        }
                    }

                    if (customFields.isEmpty()) {
                        item {
                            OutlinedCard(
                                onClick = { showAddCustomFieldDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.AddCircleOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            "+ Add Custom Text Field",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            "Tap here with + icon to add custom observations or extra text notes.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
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
    onStart: (Double?, Double?, Float?, String, Long, String) -> Unit
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
    
    var isFetchingLocation by remember { mutableStateOf(false) }
    var locationCaptured by remember {
        mutableStateOf<Location?>(
            if (inspection.startLatitude != null && inspection.startLongitude != null) {
                Location("GPS").apply {
                    latitude = inspection.startLatitude!!
                    longitude = inspection.startLongitude!!
                    accuracy = inspection.startAccuracy ?: 5.0f
                }
            } else null
        )
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
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = if (locationCaptured != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("GPS Verification", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (isFetchingLocation) {
                        Text("Fetching GPS coordinates...", style = MaterialTheme.typography.bodySmall)
                    } else if (locationCaptured != null) {
                        Text(
                            "Lat: ${String.format("%.5f", locationCaptured!!.latitude)}, Lng: ${String.format("%.5f", locationCaptured!!.longitude)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Accuracy: ±${locationCaptured!!.accuracy}m",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text("Location required to start inspection", style = MaterialTheme.typography.bodySmall)
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
                        }
                    ) {
                        Text(if (locationCaptured == null) "Get GPS" else "Refresh")
                    }
                }
            }
        }

        // Section 3: Officer Selfie Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Officer Selfie", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (selfieUri != null) {
                        Text(
                            "Selfie captured & verified",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Text("Selfie required for physical presence check", style = MaterialTheme.typography.bodySmall)
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
                        remarks
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DynamicField(
    field: InspectionField,
    value: String,
    onValueChange: (String) -> Unit
) {
    val context = LocalContext.current
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
                                    isFetching = false
                                    if (location != null) {
                                        onValueChange("${location.latitude}, ${location.longitude}")
                                    } else {
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

                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.weight(1f),
                        readOnly = true,
                        placeholder = { Text("No location captured") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            locationPermissionLauncher.launch(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                            )
                        },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        if (isFetching) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.LocationOn, "Get Location", tint = MaterialTheme.colorScheme.onSecondaryContainer)
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
                        compliances.forEach { comp ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                                    Text(
                                        "Assigned to: ${comp.assignedOfficerName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (comp.description.isNotBlank()) {
                                        Text(
                                            "Observation: ${comp.description}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    if (comp.complianceRemarks.isNotBlank()) {
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
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Inspection Details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Number: ${inspection.inspectionNumber}")
                    Text("Type: ${inspection.inspectionTypeName}")
                    Text("Location: ${inspection.locationName}")
                    Text("Officer: ${inspection.assignedOfficerName}")
                    
                    val dateOnlyFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    val scheduledDateStr = if (inspection.scheduledDate > 0) dateOnlyFormat.format(Date(inspection.scheduledDate)) else "Not set"
                    val execDate = inspection.executionDate ?: inspection.startedAt ?: inspection.scheduledDate
                    val executedDateStr = if (execDate > 0) dateOnlyFormat.format(Date(execDate)) else "N/A"
                    
                    Text("Assigned Date: $scheduledDateStr")
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
                        Text("Execution Notes: ${inspection.remarks}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                    Column {
                        Text("Geo & Identity Verification", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        if (inspection.startLatitude != null) {
                            Text("GPS: ${String.format("%.5f", inspection.startLatitude)}, ${String.format("%.5f", inspection.startLongitude)}")
                            Text("Accuracy: ${inspection.startAccuracy ?: 0f}m", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text("GPS: Not captured")
                        }
                        Text(if (inspection.selfieURL.isNotBlank()) "Selfie: Verified" else "Selfie: None", style = MaterialTheme.typography.bodySmall)
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
                        if (field.fieldType != FieldType.PHOTO && field.fieldType != FieldType.SELFIE && field.fieldType != FieldType.SIGNATURE) {
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
                            parentInspection = inspection
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
    var expandedSeverityDropdown by remember { mutableStateOf(false) }
    
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

                // Severity Level Dropdown
                Text(
                    "Priority / Severity",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                ExposedDropdownMenuBox(
                    expanded = expandedSeverityDropdown,
                    onExpandedChange = { expandedSeverityDropdown = !expandedSeverityDropdown },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedSeverity.name,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSeverityDropdown) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = expandedSeverityDropdown,
                        onDismissRequest = { expandedSeverityDropdown = false }
                    ) {
                        ComplianceSeverity.values().forEach { severity ->
                            val severityColor = when (severity) {
                                ComplianceSeverity.CRITICAL -> MaterialTheme.colorScheme.error
                                ComplianceSeverity.MAJOR -> MaterialTheme.colorScheme.tertiary
                                ComplianceSeverity.MINOR -> MaterialTheme.colorScheme.primary
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        severity.name,
                                        color = severityColor,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                onClick = {
                                    selectedSeverity = severity
                                    expandedSeverityDropdown = false
                                }
                            )
                        }
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


