package com.example.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.example.util.LocationHelper
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.models.Compliance
import com.example.models.ComplianceSeverity
import com.example.models.ComplianceStatus
import com.example.models.Inspection
import com.example.models.InspectionStatus
import com.example.ui.dashboard.InspectionComplianceGroup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Interactive Full-Screen Preview Dialog for Word Inspection Dossiers.
 * Allows officers to thoroughly inspect all records, observations, and findings
 * before downloading the official Microsoft Word (.docx) file.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionDossierPreviewDialog(
    inspections: List<Inspection>,
    compliances: List<Compliance> = emptyList(),
    criteria: InspectionFilterCriteria? = null,
    onDismiss: () -> Unit,
    onDownloadWord: () -> Unit,
    onDownloadExcel: (() -> Unit)? = null
) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val completedCount = inspections.count { it.status == InspectionStatus.COMPLETED }
    val inProgressCount = inspections.count { it.status == InspectionStatus.IN_PROGRESS }
    val assignedCount = inspections.count { it.status == InspectionStatus.ASSIGNED || it.status == InspectionStatus.ACCEPTED }
    val totalAssignedCompliances = remember(inspections, compliances) {
        inspections.sumOf { insp ->
            compliances.count { comp ->
                (comp.inspectionId.isNotBlank() && comp.inspectionId == insp.id) ||
                (comp.inspectionNumber.isNotBlank() && comp.inspectionNumber.equals(insp.inspectionNumber, ignoreCase = true))
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "Inspection Dossier Preview",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${inspections.size} inspection record(s) • Word (.docx) layout",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    actions = {
                        FilledTonalButton(
                            onClick = onDownloadWord,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.onPrimary,
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("preview_header_download_word_btn")
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download (.docx)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF1E3A8A),
                        titleContentColor = Color.White
                    )
                )
            },
            bottomBar = {
                val view = LocalView.current
                val density = LocalDensity.current
                val navBarInsetDp = remember(view) {
                    val rootInsets = ViewCompat.getRootWindowInsets(view)
                    val bottomPx = rootInsets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
                    if (bottomPx > 0) with(density) { bottomPx.toDp() } else 0.dp
                }
                val effectiveBottomPadding = maxOf(
                    navBarInsetDp,
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                    56.dp
                )

                Surface(
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .padding(bottom = effectiveBottomPadding),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Text("Close Preview")
                        }

                        if (onDownloadExcel != null) {
                            val isDark = isSystemInDarkTheme()
                            FilledTonalButton(
                                onClick = onDownloadExcel,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (isDark) Color(0xFF064E3B) else Color(0xFFDCFCE7),
                                    contentColor = if (isDark) Color(0xFF86EFAC) else Color(0xFF166534)
                                )
                            ) {
                                Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Excel (.xlsx)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = onDownloadWord,
                            modifier = Modifier
                                .weight(1.5f)
                                .heightIn(min = 48.dp)
                                .testTag("preview_bottom_download_word_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download Word (.docx)", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color(0xFFF1F5F9))
                    .testTag("inspection_dossier_preview_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Official Document Header Card
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF1E3A8A).copy(alpha = 0.1f),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.AccountBalance,
                                        contentDescription = null,
                                        tint = Color(0xFF1E3A8A),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "GOVERNMENT OF INDIA • MINISTRY OF RAILWAYS",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF1E3A8A),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "INDIAN RAILWAYS E-INSPECTION PORTAL",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = Color(0xFFE2E8F0))
                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                "OFFICIAL FIELD INSPECTION DOSSIER",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                "Generated on ${dateFormat.format(Date())} • Total Records: ${inspections.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF64748B)
                            )

                            if (criteria != null && criteria.isActive) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = Color(0xFFEFF6FF),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, Color(0xFFBFDBFE))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.FilterList, contentDescription = null, tint = Color(0xFF1D4ED8), modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "Filter Active: ${criteria.activeCount} criteria applied (${inspections.size} matching)",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF1D4ED8)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${inspections.size}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color(0xFF0F172A))
                                    Text("Total Dossiers", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$completedCount", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color(0xFF15803D))
                                    Text("Completed", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$inProgressCount", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color(0xFFB45309))
                                    Text("In Progress", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$assignedCount", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color(0xFFDC2626))
                                    Text("Assigned", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$totalAssignedCompliances", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color(0xFF1D4ED8))
                                    Text("Compliances", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                                }
                            }
                        }
                    }
                }

                // Inspection Items List (Formatted as Document Sections)
                itemsIndexed(inspections, key = { _, insp -> insp.id }) { index, inspection ->
                    val statusColor = when (inspection.status) {
                        InspectionStatus.COMPLETED -> Color(0xFF15803D)
                        InspectionStatus.IN_PROGRESS -> Color(0xFFB45309)
                        else -> Color(0xFFDC2626)
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Header: Serial number & Inspection Number
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = Color(0xFF1E3A8A),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                "${index + 1}",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        inspection.inspectionNumber.ifBlank { "INSP-${inspection.id.take(8).uppercase()}" },
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFF0F172A)
                                    )
                                }

                                Surface(
                                    color = statusColor.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        text = inspection.status.name.replace("_", " "),
                                        color = statusColor,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                            Spacer(modifier = Modifier.height(10.dp))

                            // 2-column Metadata Grid
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Inspection Type", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                                    Text(inspection.inspectionTypeName.ifBlank { "General" }, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, color = Color(0xFF0F172A))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Location / Station", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                                    Text(inspection.locationName.ifBlank { "Headquarters" }, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, color = Color(0xFF0F172A))
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Inspecting Officer", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                                    Text(inspection.assignedOfficerName.ifBlank { "Duty Officer" }, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, color = Color(0xFF0F172A))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Scheduled / Execution Date", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                                    val exec = inspection.executionDate
                                    val dateMillis = if (exec != null && exec > 0) exec else inspection.scheduledDate
                                    Text(if (dateMillis > 0) dateFormat.format(Date(dateMillis)) else "Unscheduled", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, color = Color(0xFF0F172A))
                                }
                            }

                            val matchingCompliances = compliances.filter { comp ->
                                (comp.inspectionId.isNotBlank() && comp.inspectionId == inspection.id) ||
                                (comp.inspectionNumber.isNotBlank() && comp.inspectionNumber.equals(inspection.inspectionNumber, ignoreCase = true))
                            }
                            val compCount = matchingCompliances.size
                            val compAssignedOfficers = matchingCompliances.mapNotNull { it.assignedOfficerName.ifBlank { null } }.distinct()

                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                color = if (compCount > 0) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (compCount > 0) Color(0xFFBFDBFE) else Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Icon(
                                                Icons.Default.AssignmentTurnedIn,
                                                contentDescription = null,
                                                tint = if (compCount > 0) Color(0xFF1D4ED8) else Color(0xFF64748B),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                "No. of Compliance Assigned to Officer:",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (compCount > 0) Color(0xFF1E3A8A) else Color(0xFF475569)
                                            )
                                        }
                                        Surface(
                                            color = if (compCount > 0) Color(0xFF1D4ED8) else Color(0xFF94A3B8),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(
                                                text = if (compCount > 0) "$compCount Assigned" else "0 Assigned",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (compCount > 0) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        if (compAssignedOfficers.isNotEmpty()) {
                                            Text(
                                                text = "Assigned Action Officer(s): ${compAssignedOfficers.joinToString(", ")}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF1D4ED8)
                                            )
                                        }
                                        val openCount = matchingCompliances.count { it.status == ComplianceStatus.OPEN }
                                        val underAction = matchingCompliances.count { it.status == ComplianceStatus.UNDER_ACTION }
                                        val resolved = matchingCompliances.count { it.status == ComplianceStatus.RESOLVED || it.status == ComplianceStatus.CLOSED }
                                        Text(
                                            text = "Status: $openCount Open • $underAction Under Action • $resolved Resolved",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF475569)
                                        )
                                    }
                                }
                            }

                            if (inspection.remarks.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = Color(0xFFF8FAFC),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text("Field Remarks / Observations:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                                        Text(inspection.remarks, style = MaterialTheme.typography.bodySmall, color = Color(0xFF334155))
                                    }
                                }
                            }

                            // Mode / Category tag
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = Color(0xFFF1F5F9),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = if (inspection.isSpotInspection) "SPOT INSPECTION" else if (inspection.inspectionCategory.equals("SELF", ignoreCase = true)) "SELF INSPECTION" else "ASSIGNED INSPECTION",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF475569),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                if (inspection.status == InspectionStatus.COMPLETED) {
                                    Surface(
                                        color = Color(0xFFDCFCE7),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "VERIFIED",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF15803D),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                if (inspection.startLatitude != null && inspection.startLongitude != null) {
                                    val context = LocalContext.current
                                    Surface(
                                        color = Color(0xFFE0E7FF),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.clickable {
                                            LocationHelper.openGoogleMaps(
                                                context = context,
                                                latitude = inspection.startLatitude,
                                                longitude = inspection.startLongitude,
                                                label = inspection.locationName
                                            )
                                        }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFF4338CA), modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(
                                                "GPS Tagged (Map)",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFF4338CA)
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

/**
 * Interactive Full-Screen Preview Dialog for Excel Inspection Registers.
 * Presents a tabular spreadsheet layout of all inspections with columns matching
 * the official Excel (.xlsx / .csv) export.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionRegisterExcelPreviewDialog(
    inspections: List<Inspection>,
    compliances: List<Compliance> = emptyList(),
    criteria: InspectionFilterCriteria? = null,
    onDismiss: () -> Unit,
    onDownloadExcel: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val totalCompCount = remember(inspections, compliances) {
        inspections.sumOf { insp ->
            compliances.count { comp ->
                (comp.inspectionId.isNotBlank() && comp.inspectionId == insp.id) ||
                (comp.inspectionNumber.isNotBlank() && comp.inspectionNumber.equals(insp.inspectionNumber, ignoreCase = true))
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "Excel Register Preview (.xlsx)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${inspections.size} rows • Tabular audit spreadsheet format",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    actions = {
                        FilledTonalButton(
                            onClick = onDownloadExcel,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.onPrimary,
                                contentColor = Color(0xFF1B5E20)
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("preview_header_download_excel_btn")
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download (.xlsx)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF1B5E20),
                        titleContentColor = Color.White
                    )
                )
            },
            bottomBar = {
                val view = LocalView.current
                val density = LocalDensity.current
                val navBarInsetDp = remember(view) {
                    val rootInsets = ViewCompat.getRootWindowInsets(view)
                    val bottomPx = rootInsets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
                    if (bottomPx > 0) with(density) { bottomPx.toDp() } else 0.dp
                }
                val effectiveBottomPadding = maxOf(
                    navBarInsetDp,
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                    56.dp
                )

                Surface(
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .padding(bottom = effectiveBottomPadding),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Close Preview")
                        }

                        Button(
                            onClick = onDownloadExcel,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("preview_bottom_download_excel_btn")
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download Excel File (.xlsx)", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color.White)
            ) {
                // Table Subheader banner
                Surface(
                    color = Color(0xFFE8F5E9),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "CENTRAL RAILWAY INSPECTION REGISTER SHEET",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF1B5E20)
                        )
                        Text(
                            "Total Rows: ${inspections.size} • Compliances Assigned: $totalCompCount",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }

                // Horizontally & Vertically Scrollable Spreadsheet Table
                val horizontalScroll = rememberScrollState()

                Box(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(horizontalScroll)
                    ) {
                        // Header Row
                        Row(
                            modifier = Modifier
                                .background(Color(0xFF2E7D32))
                                .padding(vertical = 10.dp)
                        ) {
                            TableCell("Sr", width = 50.dp, isHeader = true)
                            TableCell("Inspection ID", width = 160.dp, isHeader = true)
                            TableCell("Date", width = 100.dp, isHeader = true)
                            TableCell("Type", width = 140.dp, isHeader = true)
                            TableCell("Location / Station", width = 180.dp, isHeader = true)
                            TableCell("Officer Name", width = 150.dp, isHeader = true)
                            TableCell("Compliances Assigned", width = 160.dp, isHeader = true)
                            TableCell("Mode", width = 100.dp, isHeader = true)
                            TableCell("Status", width = 110.dp, isHeader = true)
                            TableCell("Remarks", width = 220.dp, isHeader = true)
                        }

                        // Data Rows
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            itemsIndexed(inspections, key = { _, insp -> insp.id }) { index, insp ->
                                val rowBg = if (index % 2 == 0) Color.White else Color(0xFFF8FAFC)
                                val exec = insp.executionDate
                                val dateStr = if (exec != null && exec > 0) dateFormat.format(Date(exec))
                                else if (insp.scheduledDate > 0) dateFormat.format(Date(insp.scheduledDate))
                                else "-"

                                val matchingComp = compliances.filter { comp ->
                                    (comp.inspectionId.isNotBlank() && comp.inspectionId == insp.id) ||
                                    (comp.inspectionNumber.isNotBlank() && comp.inspectionNumber.equals(insp.inspectionNumber, ignoreCase = true))
                                }
                                val compCount = matchingComp.size
                                val compText = if (compCount > 0) "$compCount Assigned" else "0"

                                Row(
                                    modifier = Modifier
                                        .background(rowBg)
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TableCell("${index + 1}", width = 50.dp)
                                    TableCell(insp.inspectionNumber.ifBlank { insp.id.take(8) }, width = 160.dp, isMono = true)
                                    TableCell(dateStr, width = 100.dp)
                                    TableCell(insp.inspectionTypeName, width = 140.dp)
                                    TableCell(insp.locationName, width = 180.dp)
                                    TableCell(insp.assignedOfficerName, width = 150.dp)
                                    TableCell(compText, width = 160.dp)
                                    TableCell(if (insp.isSpotInspection) "Spot" else if (insp.inspectionCategory.equals("SELF", ignoreCase = true)) "Self" else "Assigned", width = 100.dp)
                                    TableCell(insp.status.name, width = 110.dp)
                                    TableCell(insp.remarks.ifBlank { "N/A" }, width = 220.dp)
                                }
                                HorizontalDivider(color = Color(0xFFE2E8F0))
                            }
                        }
                    }
                }
            }
        }
    }
}

enum class CompliancePreviewMode {
    BY_INSPECTION,
    FLAT_LIST
}

/**
 * Interactive Full-Screen Preview Dialog for Compliance Action Dossiers.
 * Allows officers and administrators to view point-wise compliance notes,
 * responsible officers, target dates, and statuses prior to Word export,
 * both by inspection groups and as a consolidated flat action list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComplianceDossierPreviewDialog(
    groups: List<InspectionComplianceGroup>,
    officerName: String,
    onDismiss: () -> Unit,
    onDownloadWord: () -> Unit = {},
    onDownloadExcel: (() -> Unit)? = null,
    initialViewMode: CompliancePreviewMode = CompliancePreviewMode.BY_INSPECTION,
    reportTitle: String = "Compliance Action Dossier Preview",
    allGroups: List<InspectionComplianceGroup>? = null,
    onDownloadWordWithMode: ((CompliancePreviewMode) -> Unit)? = null,
    onDownloadWordWithGroupsAndMode: ((List<InspectionComplianceGroup>, CompliancePreviewMode) -> Unit)? = null,
    onDownloadExcelWithGroups: ((List<InspectionComplianceGroup>) -> Unit)? = null
) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val isDark = isSystemInDarkTheme()

    // Default to allGroups if provided so the dossier preview and export always show the full data by default
    var activeGroups by remember(groups, allGroups) {
        mutableStateOf(if (allGroups != null && allGroups.isNotEmpty()) allGroups else groups)
    }
    val totalCompliances = activeGroups.sumOf { it.compliances.size }
    val resolvedCount = activeGroups.sumOf { it.compliances.count { c -> c.status == ComplianceStatus.RESOLVED || c.status == ComplianceStatus.CLOSED } }
    val underActionCount = activeGroups.sumOf { it.compliances.count { c -> c.status == ComplianceStatus.UNDER_ACTION } }
    val openCount = activeGroups.sumOf { it.compliances.count { c -> c.status == ComplianceStatus.OPEN } }

    var previewMode by remember { mutableStateOf(initialViewMode) }
    val flatItems = remember(activeGroups) {
        activeGroups.flatMap { g -> g.compliances.map { c -> Pair(g, c) } }
    }

    val excelBtnColors = ButtonDefaults.filledTonalButtonColors(
        containerColor = if (isDark) Color(0xFF064E3B) else Color(0xFFDCFCE7),
        contentColor = if (isDark) Color(0xFF86EFAC) else Color(0xFF166534)
    )

    val triggerWordDownload = {
        if (onDownloadWordWithGroupsAndMode != null) {
            onDownloadWordWithGroupsAndMode(activeGroups, previewMode)
        } else if (onDownloadWordWithMode != null) {
            onDownloadWordWithMode(previewMode)
        } else {
            onDownloadWord()
        }
    }

    val triggerExcelDownload = {
        if (onDownloadExcelWithGroups != null) {
            onDownloadExcelWithGroups(activeGroups)
        } else if (onDownloadExcel != null) {
            onDownloadExcel()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                reportTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "$totalCompliances finding(s) in ${activeGroups.size} inspection(s) • Dossier Action Report",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    actions = {
                        if (onDownloadExcel != null || onDownloadExcelWithGroups != null) {
                            FilledTonalButton(
                                onClick = triggerExcelDownload,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (isDark) Color(0xFF064E3B) else Color(0xFFDCFCE7),
                                    contentColor = if (isDark) Color(0xFF86EFAC) else Color(0xFF166534)
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.padding(end = 4.dp).testTag("compliance_preview_download_excel_btn")
                            ) {
                                Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Excel", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        FilledTonalButton(
                            onClick = triggerWordDownload,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.onPrimary,
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("compliance_preview_download_btn")
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (activeGroups.size > 1) "Word (${activeGroups.size} Insp)" else "Word (.docx)",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = Color.White
                    )
                )
            },
            bottomBar = {
                val view = LocalView.current
                val density = LocalDensity.current
                val navBarInsetDp = remember(view) {
                    val rootInsets = ViewCompat.getRootWindowInsets(view)
                    val bottomPx = rootInsets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
                    if (bottomPx > 0) with(density) { bottomPx.toDp() } else 0.dp
                }
                val effectiveBottomPadding = maxOf(
                    navBarInsetDp,
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                    56.dp
                )

                Surface(
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .padding(bottom = effectiveBottomPadding),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(0.7f)
                                .heightIn(min = 48.dp)
                        ) {
                            Text("Close")
                        }

                        if (onDownloadExcel != null || onDownloadExcelWithGroups != null) {
                            FilledTonalButton(
                                onClick = triggerExcelDownload,
                                colors = excelBtnColors,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("compliance_preview_bottom_download_excel_btn")
                            ) {
                                Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Excel (${activeGroups.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        Button(
                            onClick = triggerWordDownload,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .weight(1.3f)
                                .heightIn(min = 48.dp)
                                .testTag("compliance_preview_bottom_download_btn")
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (previewMode == CompliancePreviewMode.FLAT_LIST)
                                    "Word Flat (${flatItems.size})"
                                else if (activeGroups.size > 1)
                                    "Word All (${activeGroups.size} Insp)"
                                else
                                    "Word (.docx)",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Compact Header: Scope & Mode Toggles + Inline KPI Badges (Ultra sleek, zero text wrapping)
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Row 1: KPI Stats Grid - 4 equal-width compact badges (NEVER wraps text vertically)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.weight(1f).height(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp)) {
                                        Text(
                                            "Total: $totalCompliances",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.weight(1f).height(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp)) {
                                        Text(
                                            "Open: $openCount",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                                Surface(
                                    color = Color(0xFFFEF3C7),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.weight(1f).height(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp)) {
                                        Text(
                                            "Action: $underActionCount",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF92400E),
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                                Surface(
                                    color = Color(0xFFDCFCE7),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.weight(1.05f).height(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp)) {
                                        Text(
                                            "Resolved: $resolvedCount",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF166534),
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }

                            // Row 2: Controls row (Inspection scope toggle + View Mode toggle) - Horizontally scrollable so nothing is ever squeezed or clipped
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (allGroups != null && allGroups.size > 1) {
                                    val isShowingAll = activeGroups.size == allGroups.size
                                    FilterChip(
                                        selected = isShowingAll,
                                        onClick = {
                                            activeGroups = if (isShowingAll) groups else allGroups
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(13.dp))
                                        },
                                        label = {
                                            Text(
                                                if (isShowingAll) "All Inspections (${allGroups.size})" else "Single (${groups.firstOrNull()?.inspectionNumber ?: "1"})",
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        },
                                        modifier = Modifier.height(28.dp)
                                    )
                                } else {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            "${activeGroups.size} Inspection(s)",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }

                                FilterChip(
                                    selected = previewMode == CompliancePreviewMode.BY_INSPECTION,
                                    onClick = { previewMode = CompliancePreviewMode.BY_INSPECTION },
                                    leadingIcon = {
                                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(13.dp))
                                    },
                                    label = { Text("By Inspection (${activeGroups.size})", style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false) },
                                    modifier = Modifier.height(28.dp)
                                )

                                FilterChip(
                                    selected = previewMode == CompliancePreviewMode.FLAT_LIST,
                                    onClick = { previewMode = CompliancePreviewMode.FLAT_LIST },
                                    leadingIcon = {
                                        Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(13.dp))
                                    },
                                    label = { Text("Flat List ($totalCompliances)", style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false) },
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }
                    }
                }

                if (previewMode == CompliancePreviewMode.BY_INSPECTION) {
                    // Group items (Inspection-wise)
                    items(activeGroups, key = { it.inspectionKey }) { group ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            group.inspectionNumber.ifBlank { "Inspection" },
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            "${group.inspectionTypeName} • ${group.locationName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF64748B)
                                        )
                                    }

                                    Surface(
                                        color = Color(0xFFF1F5F9),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            "${group.compliances.size} items",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = Color(0xFFF1F5F9))
                                Spacer(modifier = Modifier.height(10.dp))

                                group.compliances.forEachIndexed { idx, compliance ->
                                    val compStatusColor = when (compliance.status) {
                                        ComplianceStatus.RESOLVED, ComplianceStatus.CLOSED -> Color(0xFF15803D)
                                        ComplianceStatus.UNDER_ACTION -> Color(0xFFB45309)
                                        else -> Color(0xFFDC2626)
                                    }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                            .padding(10.dp)
                                    ) {
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
                                                Text(
                                                    "#${idx + 1} Finding",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                val sevColor = when (compliance.severity) {
                                                    ComplianceSeverity.CRITICAL -> Color(0xFFDC2626)
                                                    ComplianceSeverity.MAJOR -> Color(0xFFEA580C)
                                                    ComplianceSeverity.MINOR -> Color(0xFF0284C7)
                                                }
                                                Surface(
                                                    color = sevColor.copy(alpha = 0.12f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        compliance.severity.name,
                                                        color = sevColor,
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }

                                            Surface(
                                                color = compStatusColor.copy(alpha = 0.12f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    compliance.status.name.replace("_", " "),
                                                    color = compStatusColor,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        if (compliance.inspectionPointTitle.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                "Point: ${compliance.inspectionPointTitle}",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            compliance.description.ifBlank { compliance.title.ifBlank { "Defect / Deviation detected during inspection" } },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF0F172A)
                                        )

                                        val remarks = compliance.actionTaken.ifBlank { compliance.complianceRemarks }
                                        if (remarks.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                "Action Taken: $remarks",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF15803D),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Action Officer: ${compliance.assignedOfficerName.ifBlank { "Unassigned" }}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Medium
                                            )

                                            if (compliance.targetDate > 0) {
                                                Text(
                                                    "Target: ${dateFormat.format(Date(compliance.targetDate))}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                    if (idx < group.compliances.size - 1) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    itemsIndexed(flatItems, key = { idx, pair -> "${pair.second.id}_$idx" }) { index, (group, compliance) ->
                        val compStatusColor = when (compliance.status) {
                            ComplianceStatus.RESOLVED, ComplianceStatus.CLOSED -> Color(0xFF15803D)
                            ComplianceStatus.UNDER_ACTION -> Color(0xFFB45309)
                            else -> Color(0xFFDC2626)
                        }
                        val sevColor = when (compliance.severity) {
                            ComplianceSeverity.CRITICAL -> Color(0xFFDC2626)
                            ComplianceSeverity.MAJOR -> Color(0xFFEA580C)
                            ComplianceSeverity.MINOR -> Color(0xFF0284C7)
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                // Header: S.No, Inspection # badge, Severity, Status
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
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                "#${index + 1}",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        Surface(
                                            color = Color(0xFFF1F5F9),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                group.inspectionNumber.ifBlank { "Inspection" },
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF1E3A8A),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        Surface(
                                            color = sevColor.copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                compliance.severity.name,
                                                color = sevColor,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Surface(
                                        color = compStatusColor.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            compliance.status.name.replace("_", " "),
                                            color = compStatusColor,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Inspection point & Location
                                if (compliance.inspectionPointTitle.isNotBlank()) {
                                    Text(
                                        compliance.inspectionPointTitle,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                } else {
                                    Text(
                                        compliance.title.ifBlank { "Observation #${index + 1}" },
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Text(
                                    "Location: ${group.locationName} • Inspected by: ${group.inspectingOfficerName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF64748B)
                                )

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    compliance.description.ifBlank { "No detailed description provided." },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                val remarks = compliance.actionTaken.ifBlank { compliance.complianceRemarks }
                                if (remarks.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Surface(
                                        color = if (isDark) Color(0xFF14532D).copy(alpha = 0.4f) else Color(0xFFF0FDF4),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            "Action Taken: $remarks",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isDark) Color(0xFF86EFAC) else Color(0xFF15803D),
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Assigned Officer: ${compliance.assignedOfficerName.ifBlank { "Unassigned" }}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )

                                    if (compliance.targetDate > 0) {
                                        Text(
                                            "Target: ${dateFormat.format(Date(compliance.targetDate))}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun TableCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    isHeader: Boolean = false,
    isMono: Boolean = false
) {
    Text(
        text = text,
        modifier = Modifier
            .width(width)
            .padding(horizontal = 8.dp),
        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
        color = if (isHeader) Color.White else Color(0xFF0F172A),
        style = if (isHeader) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
        fontFamily = if (isMono) FontFamily.Monospace else FontFamily.Default,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
