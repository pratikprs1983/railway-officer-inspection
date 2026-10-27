package com.example.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.getSelectedText
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.InspectionRepository
import com.example.models.FieldType
import com.example.models.FormCategory
import com.example.models.FormSubcategory
import com.example.models.InspectionField
import com.example.models.InspectionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class FormBuilderViewModel : ViewModel() {
    private val repo = InspectionRepository()

    private val _types = MutableStateFlow<List<InspectionType>>(emptyList())
    val types: StateFlow<List<InspectionType>> = _types.asStateFlow()

    private val _categories = MutableStateFlow<List<FormCategory>>(emptyList())
    val categories: StateFlow<List<FormCategory>> = _categories.asStateFlow()

    private val _subcategories = MutableStateFlow<List<FormSubcategory>>(emptyList())
    val subcategories: StateFlow<List<FormSubcategory>> = _subcategories.asStateFlow()

    private val _fields = MutableStateFlow<List<InspectionField>>(emptyList())
    val fields: StateFlow<List<InspectionField>> = _fields.asStateFlow()

    private val _selectedTypeId = MutableStateFlow<String?>(null)
    val selectedTypeId: StateFlow<String?> = _selectedTypeId.asStateFlow()
    private var selectedTypeName: String? = null

    private var typesJob: kotlinx.coroutines.Job? = null
    private var categoriesJob: kotlinx.coroutines.Job? = null
    private var subcategoriesJob: kotlinx.coroutines.Job? = null
    private var fieldsJob: kotlinx.coroutines.Job? = null

    init {
        loadTypes()
    }

    private fun loadTypes() {
        typesJob?.cancel()
        typesJob = viewModelScope.launch {
            repo.getInspectionTypes().collect { typesList ->
                _types.value = typesList
                val current = _selectedTypeId.value
                val currentName = selectedTypeName
                if (typesList.isNotEmpty()) {
                    val matchingById = typesList.find { it.id == current }
                    val matchingByName = if (currentName != null) typesList.find { it.name.equals(currentName, ignoreCase = true) } else null
                    val bestMatch = matchingById ?: matchingByName

                    if (bestMatch != null) {
                        if (_selectedTypeId.value != bestMatch.id) {
                            _selectedTypeId.value = bestMatch.id
                            selectedTypeName = bestMatch.name
                            loadCategories(bestMatch.id)
                            loadSubcategories(bestMatch.id)
                            loadFields(bestMatch.id)
                        }
                    } else if (current == null) {
                        selectType(typesList.first().id)
                    }
                }
            }
        }
    }

    fun selectType(typeId: String) {
        if (typeId.isBlank()) return
        _selectedTypeId.value = typeId
        val typeObj = _types.value.find { it.id == typeId }
        if (typeObj != null) {
            selectedTypeName = typeObj.name
        }
        repo.purgeOrphanedFormData(typeId)
        loadCategories(typeId)
        loadSubcategories(typeId)
        loadFields(typeId)
    }

    private fun loadCategories(typeId: String) {
        categoriesJob?.cancel()
        categoriesJob = viewModelScope.launch {
            repo.getCategoriesForType(typeId).collect { catList ->
                if (_selectedTypeId.value != typeId) return@collect
                val sorted = catList.sortedBy { it.displayOrder }
                _categories.value = sorted
                filterSubcategoriesAndFields(sorted)
            }
        }
    }

    private fun filterSubcategoriesAndFields(currentCats: List<FormCategory>) {
        if (currentCats.isEmpty()) return
        val validCatIds = currentCats.map { it.id }.toSet()
        val filteredSubs = _subcategories.value.filter { it.categoryId in validCatIds }
        if (filteredSubs.size != _subcategories.value.size) {
            _subcategories.value = filteredSubs
        }
        val validSubIds = filteredSubs.map { it.id }.toSet()
        val filteredFields = _fields.value.filter { f ->
            val hasValidCat = f.categoryId in validCatIds || currentCats.any { it.name.equals(f.categoryName, ignoreCase = true) }
            val isUncat = f.categoryId.isBlank() && f.categoryName.isBlank()
            if (!hasValidCat && !isUncat) false
            else if (hasValidCat && f.subcategoryId.isNotBlank()) {
                f.subcategoryId in validSubIds || filteredSubs.any { it.categoryId == f.categoryId && it.name.equals(f.subcategoryName, ignoreCase = true) }
            } else true
        }
        if (filteredFields.size != _fields.value.size) {
            _fields.value = filteredFields
        }
    }

    private fun loadSubcategories(typeId: String) {
        subcategoriesJob?.cancel()
        subcategoriesJob = viewModelScope.launch {
            repo.getSubcategoriesForType(typeId).collect { subList ->
                if (_selectedTypeId.value != typeId) return@collect
                val currentCats = _categories.value
                val valid = if (currentCats.isEmpty()) subList else {
                    val validCatIds = currentCats.map { it.id }.toSet()
                    subList.filter { it.categoryId in validCatIds }
                }
                _subcategories.value = valid.sortedBy { it.displayOrder }
            }
        }
    }

    private fun loadFields(typeId: String) {
        fieldsJob?.cancel()
        fieldsJob = viewModelScope.launch {
            repo.getInspectionFields(typeId).collect { fieldsList ->
                if (_selectedTypeId.value != typeId) return@collect
                val currentCats = _categories.value
                val currentSubs = _subcategories.value
                val valid = if (currentCats.isEmpty()) fieldsList else {
                    val validCatIds = currentCats.map { it.id }.toSet()
                    val validSubIds = currentSubs.map { it.id }.toSet()
                    fieldsList.filter { f ->
                        val hasValidCat = f.categoryId in validCatIds || currentCats.any { it.name.equals(f.categoryName, ignoreCase = true) }
                        val isUncat = f.categoryId.isBlank() && f.categoryName.isBlank()
                        if (!hasValidCat && !isUncat) false
                        else if (hasValidCat && f.subcategoryId.isNotBlank()) {
                            f.subcategoryId in validSubIds || currentSubs.any { it.categoryId == f.categoryId && it.name.equals(f.subcategoryName, ignoreCase = true) }
                        } else true
                    }
                }
                _fields.value = valid.sortedBy { it.displayOrder }
            }
        }
    }

    fun saveType(type: InspectionType) {
        selectedTypeName = type.name
        repo.addInspectionType(type) { success ->
            selectType(type.id)
        }
    }

    fun deleteType(type: InspectionType) {
        repo.deleteInspectionType(type) {
            categoriesJob?.cancel()
            subcategoriesJob?.cancel()
            fieldsJob?.cancel()
            val remaining = _types.value.filter { it.id != type.id }
            if (remaining.isNotEmpty()) {
                selectType(remaining.first().id)
            } else {
                _selectedTypeId.value = null
                _categories.value = emptyList()
                _subcategories.value = emptyList()
                _fields.value = emptyList()
            }
        }
    }

    fun saveCategory(name: String, categoryId: String? = null) {
        val typeId = _selectedTypeId.value ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val existing = _categories.value.find { it.id == categoryId }
        val category = existing?.copy(name = trimmed) ?: FormCategory(
            inspectionTypeId = typeId,
            name = trimmed,
            displayOrder = _categories.value.size
        )
        repo.addCategory(category)
    }

    fun deleteCategory(category: FormCategory) {
        _categories.value = _categories.value.filter { it.id != category.id }
        _subcategories.value = _subcategories.value.filter { it.categoryId != category.id }
        _fields.value = _fields.value.filter { it.categoryId != category.id }
        repo.deleteCategory(category)
        _selectedTypeId.value?.let { repo.purgeOrphanedFormData(it) }
    }

    fun saveSubcategory(categoryId: String, name: String, subcategoryId: String? = null) {
        val typeId = _selectedTypeId.value ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val existing = _subcategories.value.find { it.id == subcategoryId }
        val subcategory = existing?.copy(name = trimmed, categoryId = categoryId) ?: FormSubcategory(
            categoryId = categoryId,
            inspectionTypeId = typeId,
            name = trimmed,
            displayOrder = _subcategories.value.count { it.categoryId == categoryId }
        )
        repo.addSubcategory(subcategory)
    }

    fun deleteSubcategory(subcategory: FormSubcategory) {
        _subcategories.value = _subcategories.value.filter { it.id != subcategory.id }
        _fields.value = _fields.value.filter {
            it.subcategoryId != subcategory.id &&
            !(it.categoryId == subcategory.categoryId && it.subcategoryName.equals(subcategory.name, ignoreCase = true))
        }
        repo.deleteSubcategory(subcategory)
        _selectedTypeId.value?.let { repo.purgeOrphanedFormData(it) }
    }

    fun saveField(field: InspectionField) {
        val currentType = _selectedTypeId.value
        val safeField = if (!currentType.isNullOrBlank()) {
            field.copy(inspectionTypeId = currentType)
        } else {
            field
        }
        repo.addInspectionField(safeField)
    }

    fun deleteField(field: InspectionField) {
        _fields.value = _fields.value.filter { it.id != field.id }
        repo.deleteInspectionField(field)
    }

    fun loadCrewMemberSampleTemplate(typeId: String) {
        viewModelScope.launch {
            // Category: Crew Member Information
            val catId = UUID.randomUUID().toString()
            val category = FormCategory(
                id = catId,
                inspectionTypeId = typeId,
                name = "Crew Member Information",
                displayOrder = _categories.value.size
            )
            repo.addCategory(category)

            // Subcategory 1: Loco Pilot Details
            val sub1Id = UUID.randomUUID().toString()
            val sub1 = FormSubcategory(
                id = sub1Id,
                categoryId = catId,
                inspectionTypeId = typeId,
                name = "Loco Pilot Details",
                displayOrder = 0
            )
            repo.addSubcategory(sub1)

            // Fields for Loco Pilot Details
            val sub1Fields = listOf(
                InspectionField(
                    inspectionTypeId = typeId,
                    categoryId = catId,
                    categoryName = "Crew Member Information",
                    subcategoryId = sub1Id,
                    subcategoryName = "Loco Pilot Details",
                    label = "Loco Pilot Name",
                    fieldName = "lp_name_${System.currentTimeMillis()}",
                    fieldType = FieldType.TEXT,
                    required = true,
                    placeholder = "Enter Loco Pilot full name",
                    displayOrder = 0
                ),
                InspectionField(
                    inspectionTypeId = typeId,
                    categoryId = catId,
                    categoryName = "Crew Member Information",
                    subcategoryId = sub1Id,
                    subcategoryName = "Loco Pilot Details",
                    label = "HRMS ID / Staff No.",
                    fieldName = "lp_hrms_${System.currentTimeMillis()}",
                    fieldType = FieldType.TEXT,
                    required = true,
                    placeholder = "e.g. 50201234567",
                    displayOrder = 1
                ),
                InspectionField(
                    inspectionTypeId = typeId,
                    categoryId = catId,
                    categoryName = "Crew Member Information",
                    subcategoryId = sub1Id,
                    subcategoryName = "Loco Pilot Details",
                    label = "Headquarters (HQ)",
                    fieldName = "lp_hq_${System.currentTimeMillis()}",
                    fieldType = FieldType.TEXT,
                    required = true,
                    placeholder = "e.g. NDLS / BCT",
                    displayOrder = 2
                ),
                InspectionField(
                    inspectionTypeId = typeId,
                    categoryId = catId,
                    categoryName = "Crew Member Information",
                    subcategoryId = sub1Id,
                    subcategoryName = "Loco Pilot Details",
                    label = "Breathalyzer Test Result",
                    fieldName = "lp_ba_result_${System.currentTimeMillis()}",
                    fieldType = FieldType.DROPDOWN,
                    options = "0.0 mg/100ml (Negative), Positive, Refused",
                    required = true,
                    displayOrder = 3
                ),
                InspectionField(
                    inspectionTypeId = typeId,
                    categoryId = catId,
                    categoryName = "Crew Member Information",
                    subcategoryId = sub1Id,
                    subcategoryName = "Loco Pilot Details",
                    label = "Competency Certificate Valid Up To",
                    fieldName = "lp_competency_validity_${System.currentTimeMillis()}",
                    fieldType = FieldType.DATE,
                    required = true,
                    displayOrder = 4
                ),
                InspectionField(
                    inspectionTypeId = typeId,
                    categoryId = catId,
                    categoryName = "Crew Member Information",
                    subcategoryId = sub1Id,
                    subcategoryName = "Loco Pilot Details",
                    label = "Safety Equipment Carried (Torch, Flags, Detonators)",
                    fieldName = "lp_safety_equipment_${System.currentTimeMillis()}",
                    fieldType = FieldType.YES_NO,
                    required = true,
                    displayOrder = 5
                )
            )
            sub1Fields.forEach { repo.addInspectionField(it) }

            // Subcategory 2: ALP Details
            val sub2Id = UUID.randomUUID().toString()
            val sub2 = FormSubcategory(
                id = sub2Id,
                categoryId = catId,
                inspectionTypeId = typeId,
                name = "ALP Details",
                displayOrder = 1
            )
            repo.addSubcategory(sub2)

            val sub2Fields = listOf(
                InspectionField(
                    inspectionTypeId = typeId,
                    categoryId = catId,
                    categoryName = "Crew Member Information",
                    subcategoryId = sub2Id,
                    subcategoryName = "ALP Details",
                    label = "Assistant Loco Pilot Name",
                    fieldName = "alp_name_${System.currentTimeMillis()}",
                    fieldType = FieldType.TEXT,
                    required = true,
                    placeholder = "Enter Assistant Loco Pilot name",
                    displayOrder = 6
                ),
                InspectionField(
                    inspectionTypeId = typeId,
                    categoryId = catId,
                    categoryName = "Crew Member Information",
                    subcategoryId = sub2Id,
                    subcategoryName = "ALP Details",
                    label = "HRMS ID / Staff No.",
                    fieldName = "alp_hrms_${System.currentTimeMillis()}",
                    fieldType = FieldType.TEXT,
                    required = false,
                    displayOrder = 7
                ),
                InspectionField(
                    inspectionTypeId = typeId,
                    categoryId = catId,
                    categoryName = "Crew Member Information",
                    subcategoryId = sub2Id,
                    subcategoryName = "ALP Details",
                    label = "Breathalyzer Test Result",
                    fieldName = "alp_ba_result_${System.currentTimeMillis()}",
                    fieldType = FieldType.DROPDOWN,
                    options = "0.0 mg/100ml (Negative), Positive, Refused",
                    required = true,
                    displayOrder = 8
                ),
                InspectionField(
                    inspectionTypeId = typeId,
                    categoryId = catId,
                    categoryName = "Crew Member Information",
                    subcategoryId = sub2Id,
                    subcategoryName = "ALP Details",
                    label = "Road Learning Validity Up To",
                    fieldName = "alp_lr_validity_${System.currentTimeMillis()}",
                    fieldType = FieldType.DATE,
                    required = true,
                    displayOrder = 9
                ),
                InspectionField(
                    inspectionTypeId = typeId,
                    categoryId = catId,
                    categoryName = "Crew Member Information",
                    subcategoryId = sub2Id,
                    subcategoryName = "ALP Details",
                    label = "Proper Calling Out of Signals Checked",
                    fieldName = "alp_signals_calling_${System.currentTimeMillis()}",
                    fieldType = FieldType.YES_NO,
                    required = true,
                    displayOrder = 10
                )
            )
            sub2Fields.forEach { repo.addInspectionField(it) }

            // Subcategory 3: Guard Details
            val sub3Id = UUID.randomUUID().toString()
            val sub3 = FormSubcategory(
                id = sub3Id,
                categoryId = catId,
                inspectionTypeId = typeId,
                name = "Guard Details",
                displayOrder = 2
            )
            repo.addSubcategory(sub3)

            val sub3Fields = listOf(
                InspectionField(
                    inspectionTypeId = typeId,
                    categoryId = catId,
                    categoryName = "Crew Member Information",
                    subcategoryId = sub3Id,
                    subcategoryName = "Guard Details",
                    label = "Train Manager / Guard Name",
                    fieldName = "guard_name_${System.currentTimeMillis()}",
                    fieldType = FieldType.TEXT,
                    required = true,
                    placeholder = "Enter Train Manager / Guard name",
                    displayOrder = 11
                ),
                InspectionField(
                    inspectionTypeId = typeId,
                    categoryId = catId,
                    categoryName = "Crew Member Information",
                    subcategoryId = sub3Id,
                    subcategoryName = "Guard Details",
                    label = "HRMS ID / Staff No.",
                    fieldName = "guard_hrms_${System.currentTimeMillis()}",
                    fieldType = FieldType.TEXT,
                    required = false,
                    displayOrder = 12
                ),
                InspectionField(
                    inspectionTypeId = typeId,
                    categoryId = catId,
                    categoryName = "Crew Member Information",
                    subcategoryId = sub3Id,
                    subcategoryName = "Guard Details",
                    label = "Brake Pipe Pressure Checked (kg/cm²)",
                    fieldName = "guard_bp_pressure_${System.currentTimeMillis()}",
                    fieldType = FieldType.NUMBER,
                    required = true,
                    placeholder = "e.g. 5.0",
                    displayOrder = 13
                ),
                InspectionField(
                    inspectionTypeId = typeId,
                    categoryId = catId,
                    categoryName = "Crew Member Information",
                    subcategoryId = sub3Id,
                    subcategoryName = "Guard Details",
                    label = "Walkie-Talkie Set Working",
                    fieldName = "guard_walkie_talkie_${System.currentTimeMillis()}",
                    fieldType = FieldType.YES_NO,
                    required = true,
                    displayOrder = 14
                ),
                InspectionField(
                    inspectionTypeId = typeId,
                    categoryId = catId,
                    categoryName = "Crew Member Information",
                    subcategoryId = sub3Id,
                    subcategoryName = "Guard Details",
                    label = "Tail Lamp & Flasher Working Checked",
                    fieldName = "guard_tail_lamp_${System.currentTimeMillis()}",
                    fieldType = FieldType.YES_NO,
                    required = true,
                    displayOrder = 15
                )
            )
            sub3Fields.forEach { repo.addInspectionField(it) }
        }
    }
}

data class FormFieldClipboard(
    val field: InspectionField,
    val isCut: Boolean = false
)

@Composable
fun CutCopyPasteTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    supportingText: @Composable (() -> Unit)? = null
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var showManualPasteDialog by remember { mutableStateOf(false) }
    var manualPasteInput by remember { mutableStateOf("") }

    var tfValue by remember {
        mutableStateOf(TextFieldValue(value, TextRange(value.length)))
    }

    LaunchedEffect(value) {
        if (value != tfValue.text) {
            tfValue = TextFieldValue(value, TextRange(value.length))
        }
    }

    // Comprehensive paste function handling both System & Compose Clipboards
    fun pasteTextIntoField(incomingText: String) {
        if (incomingText.isEmpty()) return
        val newText = if (tfValue.selection.collapsed) {
            val pos = tfValue.selection.start.coerceIn(0, tfValue.text.length)
            val prefix = tfValue.text.take(pos)
            val suffix = tfValue.text.drop(pos)
            prefix + incomingText + suffix
        } else {
            val min = tfValue.selection.min.coerceIn(0, tfValue.text.length)
            val max = tfValue.selection.max.coerceIn(0, tfValue.text.length)
            tfValue.text.replaceRange(min, max, incomingText)
        }
        val newPos = if (tfValue.selection.collapsed) {
            (tfValue.selection.start + incomingText.length).coerceIn(0, newText.length)
        } else {
            (tfValue.selection.min + incomingText.length).coerceIn(0, newText.length)
        }
        tfValue = TextFieldValue(newText, TextRange(newPos))
        onValueChange(newText)
        Toast.makeText(context, "Pasted: \"$incomingText\"", Toast.LENGTH_SHORT).show()
    }

    fun handlePaste() {
        var foundText: String? = null
        // 1. Check Android system ClipboardManager
        try {
            val sysCm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val clip = sysCm?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val item = clip.getItemAt(0)
                val str = item.coerceToText(context)?.toString()
                if (!str.isNullOrBlank()) {
                    foundText = str
                }
            }
        } catch (_: Exception) {}

        // 2. Check Compose LocalClipboardManager
        if (foundText.isNullOrBlank()) {
            try {
                val str = clipboardManager.getText()?.text
                if (!str.isNullOrBlank()) {
                    foundText = str
                }
            } catch (_: Exception) {}
        }

        if (!foundText.isNullOrBlank()) {
            pasteTextIntoField(foundText)
        } else {
            // If Android OS clipboard hasn't captured desktop text in streaming emulator,
            // prompt manual paste input so user can press Ctrl+V or enter text
            manualPasteInput = ""
            showManualPasteDialog = true
        }
    }

    fun handleCopy() {
        val textToCopy = if (tfValue.selection.collapsed) tfValue.text else tfValue.getSelectedText().text
        if (textToCopy.isNotBlank()) {
            try {
                clipboardManager.setText(AnnotatedString(textToCopy))
                val sysCm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("text", textToCopy)
                sysCm?.setPrimaryClip(clip)
                Toast.makeText(context, "Copied: \"$textToCopy\"", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "No text to copy", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleCut() {
        val textToCut = if (tfValue.selection.collapsed) tfValue.text else tfValue.getSelectedText().text
        if (textToCut.isNotBlank()) {
            handleCopy()
            val newText = if (tfValue.selection.collapsed) {
                ""
            } else {
                val min = tfValue.selection.min.coerceIn(0, tfValue.text.length)
                val max = tfValue.selection.max.coerceIn(0, tfValue.text.length)
                tfValue.text.removeRange(min, max)
            }
            val newPos = if (tfValue.selection.collapsed) 0 else tfValue.selection.min.coerceIn(0, newText.length)
            tfValue = TextFieldValue(newText, TextRange(newPos))
            onValueChange(newText)
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFEA580C)
            )

            // Direct Click Actions: Paste, Cut, Copy, Clear
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Prominent PASTE Button (Left-click triggers paste immediately!)
                FilledTonalButton(
                    onClick = { handlePaste() },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFFFFF7ED),
                        contentColor = Color(0xFFC2410C)
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Color(0xFFFFEDD5))
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste", modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Paste", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // CUT Button
                FilledTonalButton(
                    onClick = { handleCut() },
                    enabled = tfValue.text.isNotBlank(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFFF8FAFC),
                        contentColor = Color(0xFF475569)
                    ),
                    contentPadding = PaddingValues(horizontal = 7.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Icon(Icons.Default.ContentCut, contentDescription = "Cut", modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Cut", fontSize = 11.sp)
                }

                // COPY Button
                FilledTonalButton(
                    onClick = { handleCopy() },
                    enabled = tfValue.text.isNotBlank(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFFF8FAFC),
                        contentColor = Color(0xFF475569)
                    ),
                    contentPadding = PaddingValues(horizontal = 7.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Copy", fontSize = 11.sp)
                }

                // CLEAR Button
                if (tfValue.text.isNotBlank()) {
                    IconButton(
                        onClick = {
                            tfValue = TextFieldValue("")
                            onValueChange("")
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color(0xFF64748B), modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        OutlinedTextField(
            value = tfValue,
            onValueChange = {
                tfValue = it
                onValueChange(it.text)
            },
            placeholder = {
                if (placeholder.isNotBlank()) {
                    Text(placeholder, color = Color(0xFF64748B))
                }
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { handlePaste() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentPaste,
                            contentDescription = "Paste",
                            tint = Color(0xFFEA580C),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color(0xFF0F172A),
                unfocusedTextColor = Color(0xFF0F172A),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                cursorColor = Color(0xFFEA580C),
                focusedBorderColor = Color(0xFFEA580C),
                unfocusedBorderColor = Color(0xFFCBD5E1),
                focusedLabelColor = Color(0xFFEA580C),
                unfocusedLabelColor = Color(0xFF64748B),
                focusedPlaceholderColor = Color(0xFF94A3B8),
                unfocusedPlaceholderColor = Color(0xFF94A3B8)
            ),
            singleLine = singleLine,
            maxLines = maxLines,
            isError = isError,
            supportingText = supportingText,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        )

        if (showManualPasteDialog) {
            AlertDialog(
                onDismissRequest = { showManualPasteDialog = false },
                title = { Text("Paste or Enter Text", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Paste text from your computer (Ctrl+V) or type below to insert into '$label':",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF475569)
                        )
                        OutlinedTextField(
                            value = manualPasteInput,
                            onValueChange = { manualPasteInput = it },
                            placeholder = { Text("Paste text here...") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF0F172A),
                                unfocusedTextColor = Color(0xFF0F172A),
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                cursorColor = Color(0xFFEA580C),
                                focusedBorderColor = Color(0xFFEA580C),
                                unfocusedBorderColor = Color(0xFFCBD5E1)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (manualPasteInput.isNotBlank()) {
                                pasteTextIntoField(manualPasteInput)
                            }
                            showManualPasteDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C))
                    ) {
                        Text("Insert Text")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showManualPasteDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FormBuilderScreen(onBack: () -> Unit, viewModel: FormBuilderViewModel = viewModel()) {
    val types by viewModel.types.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val subcategories by viewModel.subcategories.collectAsState()
    val fields by viewModel.fields.collectAsState()
    val selectedTypeId by viewModel.selectedTypeId.collectAsState()

    var showTypeDialog by remember { mutableStateOf(false) }
    var editingType by remember { mutableStateOf<InspectionType?>(null) }
    var typeToDelete by remember { mutableStateOf<InspectionType?>(null) }

    var showCategoryDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<FormCategory?>(null) }
    var categoryToDelete by remember { mutableStateOf<FormCategory?>(null) }

    var showSubcategoryDialog by remember { mutableStateOf(false) }
    var activeCategoryIdForSub by remember { mutableStateOf<String?>(null) }
    var editingSubcategory by remember { mutableStateOf<FormSubcategory?>(null) }
    var subcategoryToDelete by remember { mutableStateOf<FormSubcategory?>(null) }

    var showFieldDialog by remember { mutableStateOf(false) }
    var targetInspectionTypeIdForField by remember { mutableStateOf<String?>(null) }
    var targetCategoryIdForField by remember { mutableStateOf<String?>(null) }
    var targetSubcategoryIdForField by remember { mutableStateOf<String?>(null) }
    var editingField by remember { mutableStateOf<InspectionField?>(null) }
    var fieldToDelete by remember { mutableStateOf<InspectionField?>(null) }

    var expandedTypeDropdown by remember { mutableStateOf(false) }

    var fieldClipboard by remember { mutableStateOf<FormFieldClipboard?>(null) }
    val context = LocalContext.current

    val onPasteFieldToSubcategory: (FormCategory, FormSubcategory) -> Unit = { cat, sub ->
        val clipboard = fieldClipboard
        if (clipboard != null) {
            val src = clipboard.field
            val newField = src.copy(
                id = UUID.randomUUID().toString(),
                inspectionTypeId = selectedTypeId ?: src.inspectionTypeId,
                categoryId = cat.id,
                categoryName = cat.name,
                subcategoryId = sub.id,
                subcategoryName = sub.name,
                fieldName = "${src.fieldName.replace(Regex("_\\d+$"), "")}_${System.currentTimeMillis()}",
                displayOrder = fields.count { it.subcategoryId == sub.id }
            )
            viewModel.saveField(newField)
            if (clipboard.isCut) {
                viewModel.deleteField(src)
                fieldClipboard = null
                Toast.makeText(context, "Field moved (cut & pasted) to '${sub.name}'", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Field copied to '${sub.name}'", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val onPasteFieldToCategory: (FormCategory) -> Unit = { cat ->
        val clipboard = fieldClipboard
        if (clipboard != null) {
            val src = clipboard.field
            val newField = src.copy(
                id = UUID.randomUUID().toString(),
                inspectionTypeId = selectedTypeId ?: src.inspectionTypeId,
                categoryId = cat.id,
                categoryName = cat.name,
                subcategoryId = "",
                subcategoryName = "",
                fieldName = "${src.fieldName.replace(Regex("_\\d+$"), "")}_${System.currentTimeMillis()}",
                displayOrder = fields.count { it.categoryId == cat.id }
            )
            viewModel.saveField(newField)
            if (clipboard.isCut) {
                viewModel.deleteField(src)
                fieldClipboard = null
                Toast.makeText(context, "Field moved to '${cat.name}'", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Field copied to '${cat.name}'", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Dynamic Form Builder", fontWeight = FontWeight.Bold)
                        Text(
                            "Categories • Subcategories • Field Types",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    val selectedType = types.find { it.id == selectedTypeId }
                    if (selectedType != null) {
                        IconButton(onClick = { editingType = selectedType; showTypeDialog = true }) {
                            Icon(Icons.Default.Edit, "Edit Type", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        IconButton(onClick = { typeToDelete = selectedType }) {
                            Icon(Icons.Default.Delete, "Delete Type", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedTypeId != null) {
                ExtendedFloatingActionButton(
                    onClick = {
                        targetInspectionTypeIdForField = selectedTypeId
                        targetCategoryIdForField = categories.firstOrNull()?.id
                        targetSubcategoryIdForField = subcategories.firstOrNull { it.categoryId == targetCategoryIdForField }?.id
                        editingField = null
                        showFieldDialog = true
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add Field") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Type Selector & Header Bar (Ultra-Compact Space-Saving Design)
            // Ultra-Compact Header & Inspection Type Strip (Ultra Space-Saving Single-Row Design)
            item {
                val selectedType = types.find { it.id == selectedTypeId }
                val validSubcategories = remember(categories, subcategories) {
                    if (categories.isEmpty()) subcategories
                    else {
                        val catIds = categories.map { it.id }.toSet()
                        subcategories.filter { it.categoryId in catIds }
                    }
                }
                val validFields = remember(categories, validSubcategories, fields) {
                    if (categories.isEmpty()) fields
                    else {
                        val catIds = categories.map { it.id }.toSet()
                        val subIds = validSubcategories.map { it.id }.toSet()
                        fields.filter { field ->
                            val hasValidCat = field.categoryId in catIds || categories.any { it.name.equals(field.categoryName, ignoreCase = true) }
                            val isUncat = field.categoryId.isBlank() && field.categoryName.isBlank()
                            if (!hasValidCat && !isUncat) false
                            else if (hasValidCat && field.subcategoryId.isNotBlank()) {
                                field.subcategoryId in subIds || validSubcategories.any { it.categoryId == field.categoryId && it.name.equals(field.subcategoryName, ignoreCase = true) }
                            } else true
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White, contentColor = Color(0xFF0F172A)),
                    border = BorderStroke(1.dp, Color(0xFFFED7AA)),
                    shape = RoundedCornerShape(8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Compact Type Dropdown with embedded fields count pill
                        Box(modifier = Modifier.weight(1f)) {
                            ExposedDropdownMenuBox(
                                expanded = expandedTypeDropdown,
                                onExpandedChange = { expandedTypeDropdown = !expandedTypeDropdown }
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                        .fillMaxWidth()
                                        .height(34.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFFFFBF5),
                                    border = BorderStroke(1.dp, Color(0xFFFDBA74))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                Icons.Default.Tune,
                                                contentDescription = null,
                                                tint = Color(0xFFEA580C),
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = selectedType?.name ?: "Select Inspection Type",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0F172A),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = Color(0xFFFFF7ED),
                                                shape = RoundedCornerShape(4.dp),
                                                border = BorderStroke(1.dp, Color(0xFFFFEDD5))
                                            ) {
                                                Text(
                                                    text = "${validFields.size} fields",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFEA580C),
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTypeDropdown)
                                    }
                                }
                                ExposedDropdownMenu(
                                    expanded = expandedTypeDropdown,
                                    onDismissRequest = { expandedTypeDropdown = false }
                                ) {
                                    types.forEach { type ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    type.name,
                                                    color = Color(0xFF0F172A),
                                                    fontWeight = if (type.id == selectedTypeId) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            onClick = {
                                                viewModel.selectType(type.id)
                                                targetInspectionTypeIdForField = type.id
                                                expandedTypeDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Compact Quick-Action Buttons
                        FilledTonalButton(
                            onClick = { editingType = null; showTypeDialog = true },
                            contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(34.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFFFFF7ED),
                                contentColor = Color(0xFFC2410C)
                            ),
                            border = BorderStroke(1.dp, Color(0xFFFFEDD5))
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Type", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        FilledTonalButton(
                            onClick = {
                                editingCategory = null
                                showCategoryDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(34.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFFEA580C),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Cat", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        if (selectedTypeId != null && categories.none { it.name.contains("Crew", ignoreCase = true) }) {
                            IconButton(
                                onClick = { selectedTypeId?.let { viewModel.loadCrewMemberSampleTemplate(it) } },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = "Crew Template", tint = Color(0xFFEA580C), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // Clipboard Indicator Banner
                if (fieldClipboard != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFEF3C7),
                            contentColor = Color(0xFF78350F)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    if (fieldClipboard?.isCut == true) Icons.Default.ContentCut else Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = if (fieldClipboard?.isCut == true) "Cut: '${fieldClipboard?.field?.label}'" else "Copied: '${fieldClipboard?.field?.label}'",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "Click 'Paste Field' or 'Move Here' on any Subcategory or Category.",
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            TextButton(onClick = { fieldClipboard = null }) {
                                Text("Cancel", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Categories, Subcategories, and Fields Section
            if (selectedTypeId == null) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Text("Please select or create an Inspection Type to build form templates.")
                    }
                }
            } else if (categories.isEmpty() && fields.isEmpty()) {
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
                        Column(
                            modifier = Modifier.padding(32.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(56.dp)
                            )
                            Text(
                                "No Form Structure Defined Yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                "Structure your inspection form into Categories (e.g. Crew Member Information), Subcategories (e.g. Loco Pilot Details, ALP Details, Guard Details), and Form Fields with custom field types.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF475569),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(onClick = { editingCategory = null; showCategoryDialog = true }) {
                                    Icon(Icons.Default.Add, null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add First Category")
                                }
                                FilledTonalButton(onClick = {
                                    selectedTypeId?.let { viewModel.loadCrewMemberSampleTemplate(it) }
                                }) {
                                    Icon(Icons.Default.Tune, null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Load Crew Template")
                                }
                            }
                        }
                    }
                }
            } else {
                // Render Each Category
                items(categories) { category ->
                    val catSubcategories = subcategories.filter { it.categoryId == category.id }
                    val catFields = fields.filter { field ->
                        (field.categoryId == category.id) &&
                        (catSubcategories.isEmpty() || field.subcategoryId.isBlank() || catSubcategories.any { sub -> sub.id == field.subcategoryId || sub.name.equals(field.subcategoryName, ignoreCase = true) })
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Category Header - Clean Sonpur Indian Railways Header
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(Color(0xFFC2410C), Color(0xFFEA580C))
                                        ),
                                        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            color = Color.White.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.Folder,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                category.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Text(
                                                "CATEGORY • ${catSubcategories.size} Subcategories • ${catFields.size} Fields",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (fieldClipboard != null) {
                                            FilledTonalButton(
                                                onClick = { onPasteFieldToCategory(category) },
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = Color(0xFFFEF3C7),
                                                    contentColor = Color(0xFF78350F)
                                                ),
                                                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp),
                                                modifier = Modifier.height(28.dp),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    if (fieldClipboard?.isCut == true) "Move Here" else "Paste Field",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(3.dp))
                                        }

                                        FilledTonalButton(
                                            onClick = {
                                                activeCategoryIdForSub = category.id
                                                editingSubcategory = null
                                                showSubcategoryDialog = true
                                            },
                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                containerColor = Color.White.copy(alpha = 0.25f),
                                                contentColor = Color.White
                                            ),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                            modifier = Modifier.height(28.dp),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("+ Subcategory", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        Spacer(modifier = Modifier.width(2.dp))
                                        IconButton(
                                            onClick = {
                                                editingCategory = category
                                                showCategoryDialog = true
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                "Edit Category",
                                                tint = Color.White,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { categoryToDelete = category },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                "Delete Category",
                                                tint = Color(0xFFFECDD3),
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Subcategories Inside Category
                            Column(
                                modifier = Modifier
                                    .padding(14.dp)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (catSubcategories.isEmpty()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                "No subcategories in this category yet.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF475569)
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                if (fieldClipboard != null) {
                                                    FilledTonalButton(onClick = { onPasteFieldToCategory(category) }) {
                                                        Icon(Icons.Default.ContentPaste, null, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Paste Field", fontSize = 12.sp)
                                                    }
                                                }
                                                TextButton(
                                                    onClick = {
                                                        activeCategoryIdForSub = category.id
                                                        editingSubcategory = null
                                                        showSubcategoryDialog = true
                                                    }
                                                ) {
                                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Add Subcategory")
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    catSubcategories.forEach { subcategory ->
                                        val subFields = fields.filter {
                                            (it.subcategoryId == subcategory.id) ||
                                            (it.categoryId == category.id && it.subcategoryName.equals(subcategory.name, ignoreCase = true))
                                        }

                                        SubcategoryCard(
                                            category = category,
                                            subcategory = subcategory,
                                            fields = subFields,
                                            onAddField = {
                                                targetInspectionTypeIdForField = selectedTypeId ?: subcategory.inspectionTypeId.ifBlank { category.inspectionTypeId }
                                                targetCategoryIdForField = category.id
                                                targetSubcategoryIdForField = subcategory.id
                                                editingField = null
                                                showFieldDialog = true
                                            },
                                            onEditSubcategory = {
                                                activeCategoryIdForSub = category.id
                                                editingSubcategory = subcategory
                                                showSubcategoryDialog = true
                                            },
                                            onDeleteSubcategory = {
                                                subcategoryToDelete = subcategory
                                            },
                                            onEditField = { field ->
                                                targetInspectionTypeIdForField = selectedTypeId ?: field.inspectionTypeId.ifBlank { category.inspectionTypeId }
                                                targetCategoryIdForField = category.id
                                                targetSubcategoryIdForField = subcategory.id
                                                editingField = field
                                                showFieldDialog = true
                                            },
                                            onDeleteField = { field ->
                                                fieldToDelete = field
                                            },
                                            onCopyField = { field ->
                                                fieldClipboard = FormFieldClipboard(field = field, isCut = false)
                                                Toast.makeText(context, "Copied field: '${field.label}'", Toast.LENGTH_SHORT).show()
                                            },
                                            onCutField = { field ->
                                                fieldClipboard = FormFieldClipboard(field = field, isCut = true)
                                                Toast.makeText(context, "Cut field: '${field.label}'. Select a subcategory to paste.", Toast.LENGTH_SHORT).show()
                                            },
                                            onPasteField = {
                                                onPasteFieldToSubcategory(category, subcategory)
                                            },
                                            fieldClipboard = fieldClipboard
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Uncategorized / Legacy Fields (if any exist)
                val uncategorizedFields = fields.filter { it.categoryId.isBlank() && it.categoryName.isBlank() }
                if (uncategorizedFields.isNotEmpty()) {
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
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "General / Uncategorized Fields (${uncategorizedFields.size})",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color(0xFF0F172A)
                                    )
                                    Text(
                                        "Edit each to assign a category",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFEA580C),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                uncategorizedFields.forEach { field ->
                                    FieldItemRow(
                                        field = field,
                                        onEdit = {
                                            targetInspectionTypeIdForField = field.inspectionTypeId.ifBlank { selectedTypeId }
                                            targetCategoryIdForField = null
                                            targetSubcategoryIdForField = null
                                            editingField = field
                                            showFieldDialog = true
                                        },
                                        onDelete = { fieldToDelete = field },
                                        onCopy = {
                                            fieldClipboard = FormFieldClipboard(field = field, isCut = false)
                                            Toast.makeText(context, "Copied field: '${field.label}'", Toast.LENGTH_SHORT).show()
                                        },
                                        onCut = {
                                            fieldClipboard = FormFieldClipboard(field = field, isCut = true)
                                            Toast.makeText(context, "Cut field: '${field.label}'", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Dialog: Create / Edit Inspection Type
        if (showTypeDialog) {
            var typeName by remember { mutableStateOf(editingType?.name ?: "") }
            Dialog(
                onDismissRequest = { showTypeDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card(
                        modifier = Modifier.fillMaxWidth(0.9f).padding(16.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = Color.White, contentColor = Color(0xFF0F172A))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                if (editingType == null) "New Inspection Type" else "Edit Inspection Type",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            CutCopyPasteTextField(
                                value = typeName,
                                onValueChange = { typeName = it },
                                label = "Inspection Type Name *",
                                placeholder = "e.g. Footplate Inspection, Safety Audit",
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { showTypeDialog = false }) { Text("Cancel", color = Color(0xFF64748B)) }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (typeName.isNotBlank()) {
                                            val newType = (editingType ?: InspectionType()).copy(name = typeName.trim())
                                            viewModel.saveType(newType)
                                        }
                                        showTypeDialog = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C))
                                ) { Text("Save") }
                            }
                        }
                    }
                }
            }
        }

        // Dialog: Delete Type
        typeToDelete?.let { type ->
            AlertDialog(
                onDismissRequest = { typeToDelete = null },
                title = { Text("Delete Inspection Type", color = Color(0xFF0F172A)) },
                text = { Text("Are you sure you want to delete '${type.name}'? This will also remove all its categories, subcategories, and fields.", color = Color(0xFF475569)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteType(type)
                        typeToDelete = null
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { typeToDelete = null }) { Text("Cancel", color = Color(0xFF64748B)) } }
            )
        }

        // Dialog: Add / Edit Category
        if (showCategoryDialog) {
            var catName by remember { mutableStateOf(editingCategory?.name ?: "") }
            val suggestions = listOf(
                "Crew Member Information",
                "Locomotive Condition & Equipments",
                "Safety Equipments & P-Way",
                "Operating & Signal Observance",
                "Running Room & Staff Amenities",
                "Track & Signaling Checkpoints"
            )

            Dialog(
                onDismissRequest = { showCategoryDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card(
                        modifier = Modifier.fillMaxWidth(0.92f).padding(16.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = Color.White, contentColor = Color(0xFF0F172A))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Folder, null, tint = Color(0xFFEA580C))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (editingCategory == null) "Add Form Category" else "Edit Form Category",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                            }
                            Text(
                                "Categories group your subcategories (e.g. 'Crew Member Information', 'Locomotive Details').",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF475569)
                            )

                            CutCopyPasteTextField(
                                value = catName,
                                onValueChange = { catName = it },
                                label = "Category Name *",
                                placeholder = "e.g. Crew Member Information",
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text("Quick Suggestions:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                suggestions.forEach { sug ->
                                    SuggestionChip(
                                        onClick = { catName = sug },
                                        label = { Text(sug, fontSize = 12.sp, color = Color(0xFF0F172A)) },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = Color(0xFFF1F5F9),
                                            labelColor = Color(0xFF0F172A)
                                        )
                                    )
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { showCategoryDialog = false }) { Text("Cancel", color = Color(0xFF64748B)) }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (catName.isNotBlank()) {
                                            viewModel.saveCategory(catName, editingCategory?.id)
                                        }
                                        showCategoryDialog = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C))
                                ) { Text("Save Category") }
                            }
                        }
                    }
                }
            }
        }

        // Dialog: Delete Category
        categoryToDelete?.let { category ->
            AlertDialog(
                onDismissRequest = { categoryToDelete = null },
                title = { Text("Delete Category", color = Color(0xFF0F172A)) },
                text = { Text("Are you sure you want to delete category '${category.name}'? All subcategories and form fields within it will also be deleted.", color = Color(0xFF475569)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteCategory(category)
                        categoryToDelete = null
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { categoryToDelete = null }) { Text("Cancel", color = Color(0xFF64748B)) } }
            )
        }

        // Dialog: Add / Edit Subcategory
        if (showSubcategoryDialog) {
            val parentCat = categories.find { it.id == activeCategoryIdForSub }
            var subName by remember { mutableStateOf(editingSubcategory?.name ?: "") }
            val suggestions = if (parentCat?.name?.contains("Crew", ignoreCase = true) == true) {
                listOf("Loco Pilot Details", "ALP Details", "Guard Details", "Train Staff Details")
            } else {
                listOf("General Details", "Safety Checkpoints", "Equipment Status", "Observance of Rules", "Defects & Irregularities")
            }

            Dialog(
                onDismissRequest = { showSubcategoryDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card(
                        modifier = Modifier.fillMaxWidth(0.92f).padding(16.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = Color.White, contentColor = Color(0xFF0F172A))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Layers, null, tint = Color(0xFFEA580C))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (editingSubcategory == null) "Add Subcategory" else "Edit Subcategory",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                            }
                            if (parentCat != null) {
                                Text(
                                    "Parent Category: ${parentCat.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFEA580C)
                                )
                            }

                            CutCopyPasteTextField(
                                value = subName,
                                onValueChange = { subName = it },
                                label = "Subcategory Name *",
                                placeholder = "e.g. Loco Pilot Details, ALP Details, Guard Details",
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text("Quick Suggestions:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                suggestions.forEach { sug ->
                                    SuggestionChip(
                                        onClick = { subName = sug },
                                        label = { Text(sug, fontSize = 12.sp, color = Color(0xFF0F172A)) },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = Color(0xFFF1F5F9),
                                            labelColor = Color(0xFF0F172A)
                                        )
                                    )
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { showSubcategoryDialog = false }) { Text("Cancel", color = Color(0xFF64748B)) }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (subName.isNotBlank() && activeCategoryIdForSub != null) {
                                            viewModel.saveSubcategory(
                                                categoryId = activeCategoryIdForSub!!,
                                                name = subName,
                                                subcategoryId = editingSubcategory?.id
                                            )
                                        }
                                        showSubcategoryDialog = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C))
                                ) { Text("Save Subcategory") }
                            }
                        }
                    }
                }
            }
        }

        // Dialog: Delete Subcategory
        subcategoryToDelete?.let { subcategory ->
            AlertDialog(
                onDismissRequest = { subcategoryToDelete = null },
                title = { Text("Delete Subcategory", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to delete '${subcategory.name}'? All form fields inside this subcategory will also be deleted.", color = Color(0xFF334155)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteSubcategory(subcategory)
                        subcategoryToDelete = null
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { subcategoryToDelete = null }) { Text("Cancel", color = Color(0xFF334155)) } }
            )
        }

        // Dialog: Add / Edit Form Field
        if (showFieldDialog) {
            val activeTargetTypeId = selectedTypeId
                ?: targetInspectionTypeIdForField
                ?: (if (editingField != null) editingField?.inspectionTypeId?.ifBlank { null } else null)
                ?: types.firstOrNull()?.id
                ?: ""

            val currentInspectionType = types.find { it.id == activeTargetTypeId } ?: types.find { it.id == selectedTypeId }
            val categoriesForTarget = if (activeTargetTypeId.isNotBlank()) categories.filter { it.inspectionTypeId == activeTargetTypeId } else categories
            val subcategoriesForTarget = if (activeTargetTypeId.isNotBlank()) subcategories.filter { it.inspectionTypeId == activeTargetTypeId } else subcategories

            val initialCatId = editingField?.categoryId?.ifBlank { null } ?: targetCategoryIdForField ?: categoriesForTarget.firstOrNull()?.id
            val initialSubId = editingField?.subcategoryId?.ifBlank { null } ?: targetSubcategoryIdForField ?: subcategoriesForTarget.firstOrNull { it.categoryId == initialCatId }?.id

            var selectedCatId by remember(showFieldDialog, activeTargetTypeId, editingField?.id, targetCategoryIdForField) { mutableStateOf(initialCatId) }
            var selectedSubId by remember(showFieldDialog, activeTargetTypeId, editingField?.id, targetSubcategoryIdForField) { mutableStateOf(initialSubId) }
            var label by remember(showFieldDialog, activeTargetTypeId, editingField?.id) { mutableStateOf(editingField?.label ?: "") }
            var fieldType by remember(showFieldDialog, activeTargetTypeId, editingField?.id) { mutableStateOf(editingField?.fieldType ?: FieldType.TEXT) }
            var required by remember(showFieldDialog, activeTargetTypeId, editingField?.id) { mutableStateOf(editingField?.required ?: false) }
            var options by remember(showFieldDialog, activeTargetTypeId, editingField?.id) { mutableStateOf(editingField?.options ?: "") }
            var placeholder by remember(showFieldDialog, activeTargetTypeId, editingField?.id) { mutableStateOf(editingField?.placeholder ?: "") }
            var helpText by remember(showFieldDialog, activeTargetTypeId, editingField?.id) { mutableStateOf(editingField?.helpText ?: "") }

            val availableSubs = if (selectedCatId != null) subcategoriesForTarget.filter { it.categoryId == selectedCatId } else emptyList()

            Dialog(
                onDismissRequest = { showFieldDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .heightIn(max = 720.dp)
                            .padding(16.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = Color.White, contentColor = Color(0xFF0F172A))
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.FormatListBulleted, contentDescription = null, tint = Color(0xFFEA580C))
                                Text(
                                    if (editingField == null) "Add Form Field" else "Edit Form Field",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                            }

                            // Prominent Inspection Type indicator (Locked to the active type)
                            Surface(
                                color = Color(0xFFFFF7ED),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFFFFEDD5)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Tune, contentDescription = null, tint = Color(0xFFC2410C), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Target Inspection Type: ",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF475569)
                                    )
                                    Text(
                                        currentInspectionType?.name ?: "Inspection Form",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFC2410C)
                                    )
                                }
                            }

                            // 1. Category Selection
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Parent Category *", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    FilterChip(
                                        selected = selectedCatId == null,
                                        onClick = {
                                            selectedCatId = null
                                            selectedSubId = null
                                        },
                                        label = { Text("General (No Category)", color = if (selectedCatId == null) Color(0xFFC2410C) else Color(0xFF1E293B)) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = Color(0xFFF1F5F9),
                                            labelColor = Color(0xFF1E293B),
                                            selectedContainerColor = Color(0xFFFFEDD5),
                                            selectedLabelColor = Color(0xFFC2410C)
                                        ),
                                        leadingIcon = if (selectedCatId == null) {
                                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFC2410C)) }
                                        } else null
                                    )
                                    categoriesForTarget.forEach { c ->
                                        FilterChip(
                                            selected = selectedCatId == c.id,
                                            onClick = {
                                                selectedCatId = c.id
                                                val subsForC = subcategories.filter { it.categoryId == c.id }
                                                if (selectedSubId !in subsForC.map { it.id }) {
                                                    selectedSubId = subsForC.firstOrNull()?.id
                                                }
                                            },
                                            label = { Text(c.name, color = if (selectedCatId == c.id) Color(0xFFC2410C) else Color(0xFF1E293B)) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                containerColor = Color(0xFFF1F5F9),
                                                labelColor = Color(0xFF1E293B),
                                                selectedContainerColor = Color(0xFFFFEDD5),
                                                selectedLabelColor = Color(0xFFC2410C)
                                            ),
                                            leadingIcon = if (selectedCatId == c.id) {
                                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFC2410C)) }
                                            } else null
                                        )
                                    }
                                }
                            }

                            // 2. Subcategory Selection
                            if (availableSubs.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Subcategory", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        availableSubs.forEach { s ->
                                            FilterChip(
                                                selected = selectedSubId == s.id,
                                                onClick = { selectedSubId = s.id },
                                                label = { Text(s.name, color = if (selectedSubId == s.id) Color(0xFFC2410C) else Color(0xFF1E293B)) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    containerColor = Color(0xFFF1F5F9),
                                                    labelColor = Color(0xFF1E293B),
                                                    selectedContainerColor = Color(0xFFFFEDD5),
                                                    selectedLabelColor = Color(0xFFC2410C)
                                                ),
                                                leadingIcon = if (selectedSubId == s.id) {
                                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFC2410C)) }
                                                } else null
                                            )
                                        }
                                    }
                                }
                            }

                            // 3. Field Label
                            CutCopyPasteTextField(
                                value = label,
                                onValueChange = { label = it },
                                label = "Field Label *",
                                placeholder = "e.g. Loco Pilot Name, Breathalyzer Test Result",
                                modifier = Modifier.fillMaxWidth()
                            )

                            // 4. Field Type Option Selector
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Field Type *", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    FieldType.values().forEach { ft ->
                                        FilterChip(
                                            selected = fieldType == ft,
                                            onClick = { fieldType = ft },
                                            label = { Text(ft.name.replace("_", " "), color = if (fieldType == ft) Color(0xFFC2410C) else Color(0xFF1E293B)) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                containerColor = Color(0xFFF1F5F9),
                                                labelColor = Color(0xFF1E293B),
                                                selectedContainerColor = Color(0xFFFFEDD5),
                                                selectedLabelColor = Color(0xFFC2410C)
                                            ),
                                            leadingIcon = if (fieldType == ft) {
                                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFC2410C)) }
                                            } else null
                                        )
                                    }
                                }
                            }

                            // 5. Options for Dropdown or Multi-Select
                            if (fieldType == FieldType.DROPDOWN || fieldType == FieldType.MULTI_SELECT) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    CutCopyPasteTextField(
                                        value = options,
                                        onValueChange = { options = it },
                                        label = if (fieldType == FieldType.MULTI_SELECT) "Multi-Select Options *" else "Dropdown Options *",
                                        placeholder = "Comma separated (e.g. Negative, Positive, Refused)",
                                        supportingText = {
                                            Text("Enter available choices separated by commas (,)", color = Color(0xFF64748B))
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    val parsedOptions = options.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                    if (parsedOptions.isNotEmpty()) {
                                        Text(
                                            "Options Preview (${parsedOptions.size}):",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF0284C7)
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            parsedOptions.forEach { opt ->
                                                SuggestionChip(
                                                    onClick = {},
                                                    label = { Text(opt, style = MaterialTheme.typography.labelSmall, color = Color(0xFF0F172A)) },
                                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                                        containerColor = Color(0xFFF1F5F9),
                                                        labelColor = Color(0xFF0F172A)
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Time and Date Field Configuration Information with interactive default preview and pickers
                            if (fieldType == FieldType.TIME) {
                                var previewTime by remember {
                                    mutableStateOf(java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date()))
                                }
                                Surface(
                                    color = Color(0xFFFFF7ED),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFFFED7AA)),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFFEA580C), modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Time Field Configuration", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF9A3412))
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            "• Default Value in box: Current Time (auto-populated)\n• Selector: Analog clock face dial",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF78350F)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        // Interactive Preview Box
                                        OutlinedTextField(
                                            value = previewTime,
                                            onValueChange = { previewTime = it },
                                            readOnly = true,
                                            label = { Text("Default Time in Box (Current Time)") },
                                            leadingIcon = {
                                                Icon(Icons.Default.Schedule, contentDescription = "Analog Clock", tint = Color(0xFFEA580C))
                                            },
                                            trailingIcon = {
                                                Button(
                                                    onClick = {
                                                        val cal = java.util.Calendar.getInstance()
                                                        android.app.TimePickerDialog(
                                                            context,
                                                            { _, h, m ->
                                                                val c = java.util.Calendar.getInstance().apply {
                                                                    set(java.util.Calendar.HOUR_OF_DAY, h)
                                                                    set(java.util.Calendar.MINUTE, m)
                                                                }
                                                                previewTime = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(c.time)
                                                            },
                                                            cal.get(java.util.Calendar.HOUR_OF_DAY),
                                                            cal.get(java.util.Calendar.MINUTE),
                                                            false
                                                        ).show()
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(34.dp).padding(end = 4.dp)
                                                ) {
                                                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Analog Clock", fontSize = 11.sp)
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedContainerColor = Color.White,
                                                unfocusedContainerColor = Color.White
                                            )
                                        )
                                    }
                                }
                            } else if (fieldType == FieldType.DATE) {
                                var previewDate by remember {
                                    mutableStateOf(java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault()).format(java.util.Date()))
                                }
                                Surface(
                                    color = Color(0xFFFFF7ED),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFFFED7AA)),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Color(0xFFEA580C), modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Date Field Configuration", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF9A3412))
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            "• Default Value in box: Current Date (auto-populated)\n• Selector: Calendar popup picker",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF78350F)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        // Interactive Preview Box
                                        OutlinedTextField(
                                            value = previewDate,
                                            onValueChange = { previewDate = it },
                                            readOnly = true,
                                            label = { Text("Default Date in Box (Current Date)") },
                                            leadingIcon = {
                                                Icon(Icons.Default.CalendarToday, contentDescription = "Calendar", tint = Color(0xFFEA580C))
                                            },
                                            trailingIcon = {
                                                Button(
                                                    onClick = {
                                                        val cal = java.util.Calendar.getInstance()
                                                        android.app.DatePickerDialog(
                                                            context,
                                                            { _, y, m, d ->
                                                                val c = java.util.Calendar.getInstance().apply {
                                                                    set(java.util.Calendar.YEAR, y)
                                                                    set(java.util.Calendar.MONTH, m)
                                                                    set(java.util.Calendar.DAY_OF_MONTH, d)
                                                                }
                                                                previewDate = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault()).format(c.time)
                                                            },
                                                            cal.get(java.util.Calendar.YEAR),
                                                            cal.get(java.util.Calendar.MONTH),
                                                            cal.get(java.util.Calendar.DAY_OF_MONTH)
                                                        ).show()
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(34.dp).padding(end = 4.dp)
                                                ) {
                                                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Calendar", fontSize = 11.sp)
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedContainerColor = Color.White,
                                                unfocusedContainerColor = Color.White
                                            )
                                        )
                                    }
                                }
                            }

                            // 6. Placeholder
                            CutCopyPasteTextField(
                                value = placeholder,
                                onValueChange = { placeholder = it },
                                label = "Placeholder (Optional)",
                                placeholder = "e.g. Enter name as per HRMS",
                                modifier = Modifier.fillMaxWidth()
                            )

                            // 7. Help Guidance Text
                            CutCopyPasteTextField(
                                value = helpText,
                                onValueChange = { helpText = it },
                                label = "Help / Guidance Text (Optional)",
                                placeholder = "e.g. Guidelines for inspecting this item",
                                modifier = Modifier.fillMaxWidth()
                            )

                            // 7. Mandatory Checkbox
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = required,
                                    onCheckedChange = { required = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFFEA580C),
                                        checkmarkColor = Color.White
                                    )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Mandatory / Required Field", color = Color(0xFF0F172A), fontWeight = FontWeight.Medium)
                            }

                            // Buttons
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { showFieldDialog = false }) {
                                    Text("Cancel", color = Color(0xFF64748B))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (label.isNotBlank()) {
                                            val typeId = selectedTypeId ?: activeTargetTypeId.ifBlank { types.firstOrNull()?.id ?: return@Button }
                                            val catObj = categories.find { it.id == selectedCatId }
                                            val subObj = subcategories.find { it.id == selectedSubId }

                                            val fieldName = editingField?.fieldName ?: label.lowercase().replace(Regex("[^a-z0-9_]"), "_").take(30) + "_${System.currentTimeMillis().toString().takeLast(5)}"

                                            val newField = (editingField ?: InspectionField(inspectionTypeId = typeId)).copy(
                                                inspectionTypeId = typeId,
                                                categoryId = selectedCatId ?: "",
                                                categoryName = catObj?.name ?: "",
                                                subcategoryId = selectedSubId ?: "",
                                                subcategoryName = subObj?.name ?: "",
                                                label = label.trim(),
                                                fieldName = fieldName,
                                                fieldType = fieldType,
                                                required = required,
                                                options = options.trim(),
                                                placeholder = placeholder.trim(),
                                                helpText = helpText.trim(),
                                                displayOrder = editingField?.displayOrder ?: fields.size
                                            )
                                            viewModel.saveField(newField)
                                            if (selectedTypeId == null && typeId.isNotBlank()) {
                                                viewModel.selectType(typeId)
                                            }
                                        }
                                        showFieldDialog = false
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFEA580C),
                                        contentColor = Color.White
                                    )
                                ) { Text("Save Field") }
                            }
                        }
                    }
                }
            }
        }

        // Dialog: Delete Field
        fieldToDelete?.let { field ->
            AlertDialog(
                onDismissRequest = { fieldToDelete = null },
                title = { Text("Delete Field", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to delete '${field.label}'?", color = Color(0xFF334155)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteField(field)
                        fieldToDelete = null
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { fieldToDelete = null }) { Text("Cancel", color = Color(0xFF334155)) } }
            )
        }
    }
}

@Composable
fun SubcategoryCard(
    category: FormCategory,
    subcategory: FormSubcategory,
    fields: List<InspectionField>,
    onAddField: () -> Unit,
    onEditSubcategory: () -> Unit,
    onDeleteSubcategory: () -> Unit,
    onEditField: (InspectionField) -> Unit,
    onDeleteField: (InspectionField) -> Unit,
    onCopyField: (InspectionField) -> Unit = {},
    onCutField: (InspectionField) -> Unit = {},
    onPasteField: () -> Unit = {},
    fieldClipboard: FormFieldClipboard? = null
) {
    var isExpanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC), contentColor = Color(0xFF0F172A)),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Ultra-Compact Subcategory Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = Color(0xFFCCFBF1),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Layers,
                                contentDescription = null,
                                tint = Color(0xFF0F766E),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        subcategory.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = Color(0xFFE0F2FE),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, Color(0xFFBAE6FD))
                    ) {
                        Text(
                            "${fields.size} fields",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0369A1),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    // Paste button if field is in clipboard
                    if (fieldClipboard != null) {
                        FilledTonalButton(
                            onClick = onPasteField,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFFFEF3C7),
                                contentColor = Color(0xFF78350F)
                            ),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.height(26.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Paste", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    FilledTonalButton(
                        onClick = onAddField,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier.height(26.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFFFFF7ED),
                            contentColor = Color(0xFFC2410C)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFFFEDD5))
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("+ Field", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onEditSubcategory, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.Edit, "Edit Subcategory", tint = Color(0xFF334155), modifier = Modifier.size(14.dp))
                    }
                    IconButton(onClick = onDeleteSubcategory, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.Delete, "Delete Subcategory", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                    }
                    IconButton(onClick = { isExpanded = !isExpanded }, modifier = Modifier.size(26.dp)) {
                        Icon(
                            if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            "Toggle Expand",
                            tint = Color(0xFF334155),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Fields inside Subcategory
            if (isExpanded) {
                if (fields.isEmpty()) {
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFEA580C), modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "0 fields in subcategory",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF334155),
                                    fontSize = 11.sp
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (fieldClipboard != null) {
                                    FilledTonalButton(
                                        onClick = onPasteField,
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        modifier = Modifier.height(24.dp)
                                    ) {
                                        Icon(Icons.Default.ContentPaste, null, modifier = Modifier.size(11.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("Paste", fontSize = 10.sp)
                                    }
                                }
                                FilledTonalButton(
                                    onClick = onAddField,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(24.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = Color(0xFFEA580C),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("Add Field", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        fields.forEach { field ->
                            FieldItemRow(
                                field = field,
                                onEdit = { onEditField(field) },
                                onDelete = { onDeleteField(field) },
                                onCopy = { onCopyField(field) },
                                onCut = { onCutField(field) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FieldItemRow(
    field: InspectionField,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit = {},
    onCut: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White, contentColor = Color(0xFF0F172A)),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        field.label,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF0F172A)
                    )
                    if (field.required) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("*", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val badgeColor = when (field.fieldType) {
                        FieldType.TEXT, FieldType.TEXTAREA -> Color(0xFF2563EB)
                        FieldType.NUMBER -> Color(0xFF059669)
                        FieldType.DATE, FieldType.TIME -> Color(0xFFD97706)
                        FieldType.YES_NO -> Color(0xFF7C3AED)
                        FieldType.DROPDOWN, FieldType.MULTI_SELECT -> Color(0xFF0D9488)
                        FieldType.PHOTO, FieldType.VIDEO, FieldType.SELFIE -> Color(0xFF0284C7)
                        FieldType.PDF, FieldType.DOCUMENT -> Color(0xFFBE123C)
                        FieldType.GPS -> Color(0xFF4338CA)
                        FieldType.SIGNATURE -> Color(0xFF334155)
                    }

                    Surface(
                        color = badgeColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = field.fieldType.name.replace("_", " "),
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }

                    if (field.required) {
                        Surface(
                            color = Color(0xFFFEE2E2),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                        ) {
                            Text(
                                "Mandatory",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFDC2626),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (field.placeholder.isNotBlank()) {
                        Text(
                            "Hint: ${field.placeholder}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF475569),
                            maxLines = 1
                        )
                    }
                }

                if ((field.fieldType == FieldType.DROPDOWN || field.fieldType == FieldType.MULTI_SELECT) && field.options.isNotBlank()) {
                    Text(
                        "Options: ${field.options}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF0284C7),
                        modifier = Modifier.padding(top = 2.dp),
                        maxLines = 1
                    )
                }

                if (field.fieldType == FieldType.TIME) {
                    Text(
                        "• Analog clock dial picker (Defaults to current time)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFD97706),
                        modifier = Modifier.padding(top = 2.dp),
                        maxLines = 1
                    )
                } else if (field.fieldType == FieldType.DATE) {
                    Text(
                        "• Calendar popup picker (Defaults to current date)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFD97706),
                        modifier = Modifier.padding(top = 2.dp),
                        maxLines = 1
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Field", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onCut, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ContentCut, contentDescription = "Cut Field", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Field", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Field", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
