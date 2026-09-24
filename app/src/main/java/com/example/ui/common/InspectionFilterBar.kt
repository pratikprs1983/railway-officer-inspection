package com.example.ui.common

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.models.Inspection
import com.example.models.InspectionStatus
import com.example.models.InspectionType
import com.example.models.User
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class DateFilterType(val label: String) {
    ALL("All Dates"),
    TODAY("Today"),
    LAST_7_DAYS("Last 7 Days"),
    THIS_MONTH("This Month"),
    CUSTOM_DATE("Specific Date")
}

data class InspectionFilterCriteria(
    val searchQuery: String = "",
    val officerId: String? = null,
    val officerName: String? = null,
    val inspectionTypeId: String? = null,
    val inspectionTypeName: String? = null,
    val dateFilterType: DateFilterType = DateFilterType.ALL,
    val customDateMillis: Long? = null,
    val status: InspectionStatus? = null,
    val category: String? = null // null = All, "ASSIGNED", "SPOT", "SELF"
) {
    val isActive: Boolean
        get() = searchQuery.isNotBlank() ||
                officerId != null ||
                inspectionTypeId != null ||
                dateFilterType != DateFilterType.ALL ||
                status != null ||
                category != null

    val activeCount: Int
        get() {
            var c = 0
            if (searchQuery.isNotBlank()) c++
            if (officerId != null) c++
            if (inspectionTypeId != null) c++
            if (dateFilterType != DateFilterType.ALL) c++
            if (status != null) c++
            if (category != null) c++
            return c
        }
}

fun List<Inspection>.applyInspectionFilters(criteria: InspectionFilterCriteria): List<Inspection> {
    val now = System.currentTimeMillis()
    val todayStart = getStartOfDay(now)
    val todayEnd = getEndOfDay(now)
    val sevenDaysAgo = todayStart - (7L * 24 * 60 * 60 * 1000L)
    val thisMonthStart = getStartOfMonth(now)
    val thisMonthEnd = getEndOfMonth(now)

    return this.filter { insp ->
        // 1. Search Query
        if (criteria.searchQuery.isNotBlank()) {
            val q = criteria.searchQuery.trim()
            val matches = insp.inspectionNumber.contains(q, ignoreCase = true) ||
                    insp.inspectionTypeName.contains(q, ignoreCase = true) ||
                    insp.assignedOfficerName.contains(q, ignoreCase = true) ||
                    insp.locationName.contains(q, ignoreCase = true) ||
                    insp.remarks.contains(q, ignoreCase = true)
            if (!matches) return@filter false
        }

        // 2. Officer filter
        if (criteria.officerId != null) {
            if (insp.assignedOfficerId != criteria.officerId) return@filter false
        } else if (criteria.officerName != null) {
            if (!insp.assignedOfficerName.equals(criteria.officerName, ignoreCase = true)) return@filter false
        }

        // 3. Inspection Type filter
        if (criteria.inspectionTypeId != null) {
            if (insp.inspectionTypeId != criteria.inspectionTypeId && !insp.inspectionTypeName.equals(criteria.inspectionTypeName, ignoreCase = true)) return@filter false
        } else if (criteria.inspectionTypeName != null) {
            if (!insp.inspectionTypeName.equals(criteria.inspectionTypeName, ignoreCase = true)) return@filter false
        }

        // 4. Category filter
        if (criteria.category != null) {
            when (criteria.category) {
                "ASSIGNED" -> if (insp.isSpotInspection) return@filter false
                "SPOT" -> if (!insp.isSpotInspection || insp.inspectionCategory != "SPOT") return@filter false
                "SELF" -> if (!insp.isSpotInspection || insp.inspectionCategory != "SELF") return@filter false
            }
        }

        // 5. Status filter
        if (criteria.status != null) {
            if (insp.status != criteria.status) return@filter false
        }

        // 6. Date filter
        val relevantDate = if (insp.scheduledDate > 0) insp.scheduledDate else (insp.executionDate ?: insp.createdAt)
        when (criteria.dateFilterType) {
            DateFilterType.ALL -> true
            DateFilterType.TODAY -> {
                relevantDate in todayStart..todayEnd
            }
            DateFilterType.LAST_7_DAYS -> {
                relevantDate in sevenDaysAgo..todayEnd
            }
            DateFilterType.THIS_MONTH -> {
                relevantDate in thisMonthStart..thisMonthEnd
            }
            DateFilterType.CUSTOM_DATE -> {
                if (criteria.customDateMillis != null) {
                    val customStart = getStartOfDay(criteria.customDateMillis)
                    val customEnd = getEndOfDay(criteria.customDateMillis)
                    relevantDate in customStart..customEnd
                } else true
            }
        }
    }
}

fun getStartOfDay(millis: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = millis
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

fun getEndOfDay(millis: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = millis
    cal.set(Calendar.HOUR_OF_DAY, 23)
    cal.set(Calendar.MINUTE, 59)
    cal.set(Calendar.SECOND, 59)
    cal.set(Calendar.MILLISECOND, 999)
    return cal.timeInMillis
}

fun getStartOfMonth(millis: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = millis
    cal.set(Calendar.DAY_OF_MONTH, 1)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

fun getEndOfMonth(millis: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = millis
    cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
    cal.set(Calendar.HOUR_OF_DAY, 23)
    cal.set(Calendar.MINUTE, 59)
    cal.set(Calendar.SECOND, 59)
    cal.set(Calendar.MILLISECOND, 999)
    return cal.timeInMillis
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionFilterBar(
    criteria: InspectionFilterCriteria,
    onCriteriaChange: (InspectionFilterCriteria) -> Unit,
    officers: List<User>,
    types: List<InspectionType>,
    showOfficerFilter: Boolean = true,
    totalCount: Int,
    filteredCount: Int,
    onExportExcel: (() -> Unit)? = null,
    onExportWord: (() -> Unit)? = null,
    onViewDossier: (() -> Unit)? = null,
    onViewRegister: (() -> Unit)? = null
) {
    var showAdvancedFilterDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Top Search Bar & Filter Dialog Trigger
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = criteria.searchQuery,
                onValueChange = { onCriteriaChange(criteria.copy(searchQuery = it)) },
                placeholder = {
                    Text(
                        "Search by #, type, location...",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (criteria.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onCriteriaChange(criteria.copy(searchQuery = "")) }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("inspection_search_input"),
                shape = RoundedCornerShape(12.dp)
            )

            // Filter Button with Badge
            FilledTonalButton(
                onClick = { showAdvancedFilterDialog = true },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(50.dp)
                    .testTag("filter_button"),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (criteria.isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                BadgedBox(
                    badge = {
                        if (criteria.activeCount > 0) {
                            Badge { Text("${criteria.activeCount}") }
                        }
                    }
                ) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = "Filter",
                        modifier = Modifier.size(20.dp),
                        tint = if (criteria.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Filters",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (criteria.isActive) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (criteria.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Quick Filter Chips Row (Action Buttons + Category + Date + Status)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Action Buttons (Preview Dossier, Export Excel, Download Word)
            if (onViewDossier != null) {
                AssistChip(
                    onClick = onViewDossier,
                    label = { Text("View Dossier", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Visibility,
                            contentDescription = "View Dossier Preview",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        labelColor = MaterialTheme.colorScheme.primary
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.testTag("view_dossier_button")
                )
            }

            if (onExportExcel != null) {
                AssistChip(
                    onClick = onExportExcel,
                    label = { Text("Export Excel", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.TableChart,
                            contentDescription = "Export Excel",
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFF2E7D32)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Color(0xFF1B5E20).copy(alpha = 0.12f),
                        labelColor = Color(0xFF2E7D32)
                    ),
                    border = BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.3f)),
                    modifier = Modifier.testTag("export_excel_button")
                )
            }

            if (onExportWord != null) {
                AssistChip(
                    onClick = onExportWord,
                    label = { Text("Download Word", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = "Download Word Dossier",
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFF1E3A8A)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Color(0xFF1E3A8A).copy(alpha = 0.12f),
                        labelColor = Color(0xFF1E3A8A)
                    ),
                    border = BorderStroke(1.dp, Color(0xFF1E3A8A).copy(alpha = 0.3f)),
                    modifier = Modifier.testTag("export_word_button")
                )
            }

            if (onViewDossier != null || onExportExcel != null || onExportWord != null) {
                VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 2.dp))
            }

            // Category Chips
            FilterChip(
                selected = criteria.category == null,
                onClick = { onCriteriaChange(criteria.copy(category = null)) },
                label = { Text("All ($totalCount)", style = MaterialTheme.typography.labelSmall) }
            )
            FilterChip(
                selected = criteria.category == "ASSIGNED",
                onClick = { onCriteriaChange(criteria.copy(category = if (criteria.category == "ASSIGNED") null else "ASSIGNED")) },
                leadingIcon = { Icon(Icons.Default.Assignment, contentDescription = null, modifier = Modifier.size(14.dp)) },
                label = { Text("Assigned", style = MaterialTheme.typography.labelSmall) }
            )
            FilterChip(
                selected = criteria.category == "SPOT",
                onClick = { onCriteriaChange(criteria.copy(category = if (criteria.category == "SPOT") null else "SPOT")) },
                leadingIcon = { Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(14.dp)) },
                label = { Text("Spot", style = MaterialTheme.typography.labelSmall) }
            )
            FilterChip(
                selected = criteria.category == "SELF",
                onClick = { onCriteriaChange(criteria.copy(category = if (criteria.category == "SELF") null else "SELF")) },
                leadingIcon = { Icon(Icons.Default.AssignmentInd, contentDescription = null, modifier = Modifier.size(14.dp)) },
                label = { Text("Self", style = MaterialTheme.typography.labelSmall) }
            )

            VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 2.dp))

            // Quick Date Filter Presets
            FilterChip(
                selected = criteria.dateFilterType == DateFilterType.TODAY,
                onClick = {
                    onCriteriaChange(
                        criteria.copy(
                            dateFilterType = if (criteria.dateFilterType == DateFilterType.TODAY) DateFilterType.ALL else DateFilterType.TODAY
                        )
                    )
                },
                leadingIcon = { Icon(Icons.Default.Today, contentDescription = null, modifier = Modifier.size(14.dp)) },
                label = { Text("Today", style = MaterialTheme.typography.labelSmall) }
            )
            FilterChip(
                selected = criteria.dateFilterType == DateFilterType.LAST_7_DAYS,
                onClick = {
                    onCriteriaChange(
                        criteria.copy(
                            dateFilterType = if (criteria.dateFilterType == DateFilterType.LAST_7_DAYS) DateFilterType.ALL else DateFilterType.LAST_7_DAYS
                        )
                    )
                },
                leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp)) },
                label = { Text("7 Days", style = MaterialTheme.typography.labelSmall) }
            )
            FilterChip(
                selected = criteria.dateFilterType == DateFilterType.THIS_MONTH,
                onClick = {
                    onCriteriaChange(
                        criteria.copy(
                            dateFilterType = if (criteria.dateFilterType == DateFilterType.THIS_MONTH) DateFilterType.ALL else DateFilterType.THIS_MONTH
                        )
                    )
                },
                label = { Text("This Month", style = MaterialTheme.typography.labelSmall) }
            )

            VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 2.dp))

            // Status Filter Chips
            listOf(InspectionStatus.ASSIGNED, InspectionStatus.IN_PROGRESS, InspectionStatus.COMPLETED).forEach { status ->
                FilterChip(
                    selected = criteria.status == status,
                    onClick = {
                        onCriteriaChange(
                            criteria.copy(
                                status = if (criteria.status == status) null else status
                            )
                        )
                    },
                    label = { Text(status.name.replace("_", " "), style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        // Active Filter Badges & Summary Row
        if (criteria.isActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Showing $filteredCount of $totalCount",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Officer badge
                if (criteria.officerName != null) {
                    InputChip(
                        selected = true,
                        onClick = { onCriteriaChange(criteria.copy(officerId = null, officerName = null)) },
                        label = { Text("Officer: ${criteria.officerName}", style = MaterialTheme.typography.labelSmall) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(14.dp)) }
                    )
                }

                // Type badge
                if (criteria.inspectionTypeName != null) {
                    InputChip(
                        selected = true,
                        onClick = { onCriteriaChange(criteria.copy(inspectionTypeId = null, inspectionTypeName = null)) },
                        label = { Text("Type: ${criteria.inspectionTypeName}", style = MaterialTheme.typography.labelSmall) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(14.dp)) }
                    )
                }

                // Date badge
                if (criteria.dateFilterType != DateFilterType.ALL) {
                    val dateLabel = if (criteria.dateFilterType == DateFilterType.CUSTOM_DATE && criteria.customDateMillis != null) {
                        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(criteria.customDateMillis))
                    } else {
                        criteria.dateFilterType.label
                    }
                    InputChip(
                        selected = true,
                        onClick = { onCriteriaChange(criteria.copy(dateFilterType = DateFilterType.ALL, customDateMillis = null)) },
                        label = { Text("Date: $dateLabel", style = MaterialTheme.typography.labelSmall) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(14.dp)) }
                    )
                }

                // Status badge
                if (criteria.status != null) {
                    InputChip(
                        selected = true,
                        onClick = { onCriteriaChange(criteria.copy(status = null)) },
                        label = { Text("Status: ${criteria.status.name.replace("_", " ")}", style = MaterialTheme.typography.labelSmall) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(14.dp)) }
                    )
                }

                // Category badge
                if (criteria.category != null) {
                    InputChip(
                        selected = true,
                        onClick = { onCriteriaChange(criteria.copy(category = null)) },
                        label = { Text("Mode: ${criteria.category}", style = MaterialTheme.typography.labelSmall) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(14.dp)) }
                    )
                }

                TextButton(
                    onClick = { onCriteriaChange(InspectionFilterCriteria()) },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("Clear All", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showAdvancedFilterDialog) {
        InspectionAdvancedFilterDialog(
            criteria = criteria,
            officers = officers,
            types = types,
            showOfficerFilter = showOfficerFilter,
            onDismiss = { showAdvancedFilterDialog = false },
            onApply = { newCriteria ->
                onCriteriaChange(newCriteria)
                showAdvancedFilterDialog = false
            },
            onExportWord = if (onExportWord != null) {
                { criteriaToExport ->
                    onCriteriaChange(criteriaToExport)
                    showAdvancedFilterDialog = false
                    onExportWord()
                }
            } else null,
            onViewDossier = if (onViewDossier != null) {
                { criteriaToView ->
                    onCriteriaChange(criteriaToView)
                    showAdvancedFilterDialog = false
                    onViewDossier()
                }
            } else if (onExportWord != null) {
                { criteriaToView ->
                    onCriteriaChange(criteriaToView)
                    showAdvancedFilterDialog = false
                    onExportWord()
                }
            } else null
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionAdvancedFilterDialog(
    criteria: InspectionFilterCriteria,
    officers: List<User>,
    types: List<InspectionType>,
    showOfficerFilter: Boolean,
    onDismiss: () -> Unit,
    onApply: (InspectionFilterCriteria) -> Unit,
    onExportWord: ((InspectionFilterCriteria) -> Unit)? = null,
    onViewDossier: ((InspectionFilterCriteria) -> Unit)? = null
) {
    val context = LocalContext.current
    var tempCriteria by remember { mutableStateOf(criteria) }

    var officerExpanded by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.86f)
                .padding(vertical = 12.dp),
            shape = MaterialTheme.shapes.large,
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // Header (Fixed at top)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.FilterList,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "Filter Inspections",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (tempCriteria.isActive) "${tempCriteria.activeCount} filter(s) active" else "Select filters and click OK to view filtered data",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                // 1. OFFICER-WISE FILTER
                if (showOfficerFilter) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Officer-wise Filter",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(
                                1.dp,
                                if (officerExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { officerExpanded = !officerExpanded }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Select Inspecting Officer",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                        Text(
                                            text = tempCriteria.officerName ?: "All Officers (No Filter)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Icon(
                                        imageVector = if (officerExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (officerExpanded) "Collapse" else "Expand",
                                        tint = MaterialTheme.colorScheme.outline
                                    )
                                }

                                AnimatedVisibility(visible = officerExpanded) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        HorizontalDivider(modifier = Modifier.padding(bottom = 6.dp))
                                        val isAllOfficersSelected = tempCriteria.officerId == null
                                        Surface(
                                            onClick = {
                                                tempCriteria = tempCriteria.copy(officerId = null, officerName = null)
                                                officerExpanded = false
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isAllOfficersSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(
                                                    selected = isAllOfficersSelected,
                                                    onClick = {
                                                        tempCriteria = tempCriteria.copy(officerId = null, officerName = null)
                                                        officerExpanded = false
                                                    }
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    "All Officers (No Filter)",
                                                    fontWeight = if (isAllOfficersSelected) FontWeight.Bold else FontWeight.Normal,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                        }

                                        officers.forEach { officer ->
                                            val isSelected = tempCriteria.officerId == officer.uid
                                            Surface(
                                                onClick = {
                                                    tempCriteria = tempCriteria.copy(officerId = officer.uid, officerName = officer.name)
                                                    officerExpanded = false
                                                },
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    RadioButton(
                                                        selected = isSelected,
                                                        onClick = {
                                                            tempCriteria = tempCriteria.copy(officerId = officer.uid, officerName = officer.name)
                                                            officerExpanded = false
                                                        }
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Column {
                                                        Text(
                                                            officer.name,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                            style = MaterialTheme.typography.bodyMedium
                                                        )
                                                        Text(
                                                            "${officer.designation.ifBlank { "Officer" }} • ${officer.departmentName.ifBlank { "HQ" }}",
                                                            style = MaterialTheme.typography.bodySmall,
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

                // 2. INSPECTION TYPE-WISE FILTER
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Inspection Type-wise Filter",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(
                            1.dp,
                            if (typeExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { typeExpanded = !typeExpanded }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Assignment,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Select Inspection Type",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Text(
                                        text = tempCriteria.inspectionTypeName ?: "All Inspection Types",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Icon(
                                    imageVector = if (typeExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (typeExpanded) "Collapse" else "Expand",
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }

                            AnimatedVisibility(visible = typeExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                    HorizontalDivider(modifier = Modifier.padding(bottom = 6.dp))
                                    val isAllTypesSelected = tempCriteria.inspectionTypeId == null
                                    Surface(
                                        onClick = {
                                            tempCriteria = tempCriteria.copy(inspectionTypeId = null, inspectionTypeName = null)
                                            typeExpanded = false
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isAllTypesSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = isAllTypesSelected,
                                                onClick = {
                                                    tempCriteria = tempCriteria.copy(inspectionTypeId = null, inspectionTypeName = null)
                                                    typeExpanded = false
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                "All Types (No Filter)",
                                                fontWeight = if (isAllTypesSelected) FontWeight.Bold else FontWeight.Normal,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }

                                    types.forEach { type ->
                                        val isSelected = tempCriteria.inspectionTypeId == type.id
                                        Surface(
                                            onClick = {
                                                tempCriteria = tempCriteria.copy(inspectionTypeId = type.id, inspectionTypeName = type.name)
                                                typeExpanded = false
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(
                                                    selected = isSelected,
                                                    onClick = {
                                                        tempCriteria = tempCriteria.copy(inspectionTypeId = type.id, inspectionTypeName = type.name)
                                                        typeExpanded = false
                                                    }
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(
                                                        type.name,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    if (type.description.isNotBlank()) {
                                                        Text(
                                                            type.description,
                                                            style = MaterialTheme.typography.bodySmall,
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

                // 3. DATE-WISE FILTER
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Date-wise Filter",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = tempCriteria.dateFilterType == DateFilterType.ALL,
                            onClick = { tempCriteria = tempCriteria.copy(dateFilterType = DateFilterType.ALL, customDateMillis = null) },
                            label = { Text("All Dates", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = tempCriteria.dateFilterType == DateFilterType.TODAY,
                            onClick = { tempCriteria = tempCriteria.copy(dateFilterType = DateFilterType.TODAY, customDateMillis = null) },
                            label = { Text("Today", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = tempCriteria.dateFilterType == DateFilterType.LAST_7_DAYS,
                            onClick = { tempCriteria = tempCriteria.copy(dateFilterType = DateFilterType.LAST_7_DAYS, customDateMillis = null) },
                            label = { Text("Last 7 Days", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = tempCriteria.dateFilterType == DateFilterType.THIS_MONTH,
                            onClick = { tempCriteria = tempCriteria.copy(dateFilterType = DateFilterType.THIS_MONTH, customDateMillis = null) },
                            label = { Text("This Month", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Custom Specific Date Picker Option
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (tempCriteria.dateFilterType == DateFilterType.CUSTOM_DATE) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            } else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Pick Specific Date",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (tempCriteria.dateFilterType == DateFilterType.CUSTOM_DATE && tempCriteria.customDateMillis != null) {
                                        dateFormat.format(Date(tempCriteria.customDateMillis!!))
                                    } else "Select custom calendar date",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (tempCriteria.dateFilterType == DateFilterType.CUSTOM_DATE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = {
                                    val currentCal = Calendar.getInstance()
                                    if (tempCriteria.customDateMillis != null) {
                                        currentCal.timeInMillis = tempCriteria.customDateMillis!!
                                    }
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            val selectedCal = Calendar.getInstance()
                                            selectedCal.set(y, m, d)
                                            tempCriteria = tempCriteria.copy(
                                                dateFilterType = DateFilterType.CUSTOM_DATE,
                                                customDateMillis = selectedCal.timeInMillis
                                            )
                                        },
                                        currentCal.get(Calendar.YEAR),
                                        currentCal.get(Calendar.MONTH),
                                        currentCal.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (tempCriteria.customDateMillis == null) "Choose Date" else "Change")
                            }
                        }
                    }
                }

                // 4. STATUS FILTER
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Status",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = tempCriteria.status == null,
                            onClick = { tempCriteria = tempCriteria.copy(status = null) },
                            label = { Text("All", style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = tempCriteria.status == InspectionStatus.ASSIGNED,
                            onClick = { tempCriteria = tempCriteria.copy(status = if (tempCriteria.status == InspectionStatus.ASSIGNED) null else InspectionStatus.ASSIGNED) },
                            label = { Text("Assigned", style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = tempCriteria.status == InspectionStatus.IN_PROGRESS,
                            onClick = { tempCriteria = tempCriteria.copy(status = if (tempCriteria.status == InspectionStatus.IN_PROGRESS) null else InspectionStatus.IN_PROGRESS) },
                            label = { Text("In Progress", style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = tempCriteria.status == InspectionStatus.COMPLETED,
                            onClick = { tempCriteria = tempCriteria.copy(status = if (tempCriteria.status == InspectionStatus.COMPLETED) null else InspectionStatus.COMPLETED) },
                            label = { Text("Completed", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // 5. INSPECTION CATEGORY / MODE
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Inspection Mode / Category",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = tempCriteria.category == null,
                            onClick = { tempCriteria = tempCriteria.copy(category = null) },
                            label = { Text("All Modes", style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = tempCriteria.category == "ASSIGNED",
                            onClick = { tempCriteria = tempCriteria.copy(category = if (tempCriteria.category == "ASSIGNED") null else "ASSIGNED") },
                            label = { Text("Assigned", style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = tempCriteria.category == "SPOT",
                            onClick = { tempCriteria = tempCriteria.copy(category = if (tempCriteria.category == "SPOT") null else "SPOT") },
                            label = { Text("Spot", style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = tempCriteria.category == "SELF",
                            onClick = { tempCriteria = tempCriteria.copy(category = if (tempCriteria.category == "SELF") null else "SELF") },
                            label = { Text("Self", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            // Fixed Bottom Actions Bar - ALWAYS visible to user!
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Secondary action row: Reset and Cancel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { tempCriteria = InspectionFilterCriteria() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset All", color = MaterialTheme.colorScheme.error)
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }

                // Primary action row: View Dossier & Apply Filters
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val viewAction = onViewDossier ?: onExportWord
                    if (viewAction != null) {
                        OutlinedButton(
                            onClick = { viewAction(tempCriteria) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("filter_dialog_view_dossier_btn"),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                Icons.Default.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFF1E3A8A)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("View Dossier", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Prominent Apply & View button to immediately show filtered inspections on screen
                    Button(
                        onClick = { onApply(tempCriteria) },
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("filter_dialog_ok_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Apply & View", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
}

@Composable
fun ExportExcelConfirmDialog(
    totalInspectionsCount: Int,
    filteredInspectionsCount: Int,
    isFilterActive: Boolean,
    onDismiss: () -> Unit,
    onExport: (exportOnlyFiltered: Boolean, chronological: Boolean) -> Unit,
    onViewPreview: ((exportOnlyFiltered: Boolean, chronological: Boolean) -> Unit)? = null
) {
    var exportFilteredOnly by remember { mutableStateOf(isFilterActive) }
    var chronological by remember { mutableStateOf(false) } // false = Latest date first, true = oldest first

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1B5E20).copy(alpha = 0.12f),
                modifier = Modifier.size(50.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.TableChart,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        title = {
            Text(
                "Export Excel Spreadsheet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Export all inspection details sorted date-wise into an Excel-ready spreadsheet file (.csv) compatible with Microsoft Excel and Google Sheets.",
                    style = MaterialTheme.typography.bodyMedium
                )

                if (isFilterActive) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Select Records to Export:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = exportFilteredOnly,
                                onClick = { exportFilteredOnly = true }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export Filtered ($filteredInspectionsCount inspections)", style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = !exportFilteredOnly,
                                onClick = { exportFilteredOnly = false }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export All ($totalInspectionsCount inspections)", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Exporting all $totalInspectionsCount inspections with complete details.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                HorizontalDivider()

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Date-wise Ordering:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = !chronological,
                            onClick = { chronological = false }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Latest Date First (Newest on top)", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = chronological,
                            onClick = { chronological = true }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Chronological (Oldest to Newest)", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (onViewPreview != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onViewPreview(if (isFilterActive) exportFilteredOnly else false, chronological)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("view_register_preview_first_btn"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2E7D32)),
                        border = BorderStroke(1.dp, Color(0xFF2E7D32))
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("View Register Preview First", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onExport(if (isFilterActive) exportFilteredOnly else false, chronological)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Export Excel File")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ExportWordConfirmDialog(
    totalInspectionsCount: Int,
    filteredInspectionsCount: Int,
    isFilterActive: Boolean,
    onDismiss: () -> Unit,
    onExport: (exportOnlyFiltered: Boolean, chronological: Boolean) -> Unit,
    onViewPreview: ((exportOnlyFiltered: Boolean, chronological: Boolean) -> Unit)? = null
) {
    var exportFilteredOnly by remember { mutableStateOf(isFilterActive) }
    var chronological by remember { mutableStateOf(false) } // false = Latest date first, true = oldest first

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E3A8A).copy(alpha = 0.12f),
                modifier = Modifier.size(50.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        tint = Color(0xFF1E3A8A),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        title = {
            Text(
                "Export Word Dossier (.docx)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Export complete inspection records, observations, and findings into an authentic Microsoft Word (.docx) document formatted with official headers.",
                    style = MaterialTheme.typography.bodyMedium
                )

                if (isFilterActive) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Select Records to Export:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = exportFilteredOnly,
                                onClick = { exportFilteredOnly = true }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export Filtered ($filteredInspectionsCount inspections)", style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = !exportFilteredOnly,
                                onClick = { exportFilteredOnly = false }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export All ($totalInspectionsCount inspections)", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    Surface(
                        color = Color(0xFF1E3A8A).copy(alpha = 0.08f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Exporting all $totalInspectionsCount inspections with complete details.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                HorizontalDivider()

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Date-wise Ordering:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = !chronological,
                            onClick = { chronological = false }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Latest Date First (Newest on top)", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = chronological,
                            onClick = { chronological = true }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Chronological (Oldest to Newest)", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (onViewPreview != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onViewPreview(if (isFilterActive) exportFilteredOnly else false, chronological)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("view_dossier_preview_first_btn"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1E3A8A)),
                        border = BorderStroke(1.dp, Color(0xFF1E3A8A))
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("View Dossier Preview First", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onExport(if (isFilterActive) exportFilteredOnly else false, chronological)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A))
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Download Word (.docx)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
