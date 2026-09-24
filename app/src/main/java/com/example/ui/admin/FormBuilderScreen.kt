package com.example.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.InspectionRepository
import com.example.models.FieldType
import com.example.models.InspectionField
import com.example.models.InspectionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FormBuilderViewModel : ViewModel() {
    private val repo = InspectionRepository()

    private val _types = MutableStateFlow<List<InspectionType>>(emptyList())
    val types: StateFlow<List<InspectionType>> = _types.asStateFlow()

    private val _fields = MutableStateFlow<List<InspectionField>>(emptyList())
    val fields: StateFlow<List<InspectionField>> = _fields.asStateFlow()

    private val _selectedTypeId = MutableStateFlow<String?>(null)
    val selectedTypeId: StateFlow<String?> = _selectedTypeId.asStateFlow()

    init {
        loadTypes()
    }

    private fun loadTypes() {
        viewModelScope.launch {
            repo.getInspectionTypes().collect { typesList ->
                _types.value = typesList
                if (typesList.isNotEmpty() && _selectedTypeId.value == null) {
                    selectType(typesList.first().id)
                }
            }
        }
    }

    fun selectType(typeId: String) {
        _selectedTypeId.value = typeId
        loadFields(typeId)
    }

    private fun loadFields(typeId: String) {
        viewModelScope.launch {
            repo.getInspectionFields(typeId).collect { fieldsList ->
                _fields.value = fieldsList.sortedBy { it.displayOrder }
            }
        }
    }

    fun saveType(type: InspectionType) {
        repo.addInspectionType(type) {
            if (_selectedTypeId.value == null) selectType(type.id)
        }
    }

    fun deleteType(type: InspectionType) {
        repo.deleteInspectionType(type) {
            _selectedTypeId.value = null
            _fields.value = emptyList()
        }
    }

    fun saveField(field: InspectionField) {
        repo.addInspectionField(field) {
            // Flow will update automatically
        }
    }

    fun deleteField(field: InspectionField) {
        repo.deleteInspectionField(field) {
            // Flow will update automatically
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FormBuilderScreen(onBack: () -> Unit, viewModel: FormBuilderViewModel = viewModel()) {
    val types by viewModel.types.collectAsState()
    val fields by viewModel.fields.collectAsState()
    val selectedTypeId by viewModel.selectedTypeId.collectAsState()

    var showTypeDialog by remember { mutableStateOf(false) }
    var editingType by remember { mutableStateOf<InspectionType?>(null) }
    var typeToDelete by remember { mutableStateOf<InspectionType?>(null) }

    var showFieldDialog by remember { mutableStateOf(false) }
    var editingField by remember { mutableStateOf<InspectionField?>(null) }
    var fieldToDelete by remember { mutableStateOf<InspectionField?>(null) }

    var expandedTypeDropdown by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dynamic Form Builder") },
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
                FloatingActionButton(onClick = { editingField = null; showFieldDialog = true }) {
                    Icon(Icons.Default.Add, "Add Field")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                // Type Selector
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    ExposedDropdownMenuBox(
                        expanded = expandedTypeDropdown,
                        onExpandedChange = { expandedTypeDropdown = !expandedTypeDropdown },
                        modifier = Modifier.weight(1f)
                    ) {
                        val selectedType = types.find { it.id == selectedTypeId }
                        OutlinedTextField(
                            value = selectedType?.name ?: "Select Inspection Type",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTypeDropdown) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expandedTypeDropdown, onDismissRequest = { expandedTypeDropdown = false }) {
                            types.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.name) },
                                    onClick = {
                                        viewModel.selectType(type.id)
                                        expandedTypeDropdown = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { editingType = null; showTypeDialog = true }) {
                        Text("New Type")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Fields List
            if (fields.isEmpty()) {
                item {
                    Text("No fields defined for this inspection type yet.")
                }
            } else {
                item {
                    Text("Form Fields:", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                items(fields) { field ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(field.label, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                Text("Type: ${field.fieldType.name}", style = MaterialTheme.typography.bodySmall)
                                if ((field.fieldType == FieldType.DROPDOWN || field.fieldType == FieldType.MULTI_SELECT) && field.options.isNotBlank()) {
                                    Text("Options: ${field.options}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                                if (field.required) {
                                    Text("* Required", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = { editingField = field; showFieldDialog = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Field", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { fieldToDelete = field }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Field", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        if (showTypeDialog) {
            var typeName by remember { mutableStateOf(editingType?.name ?: "") }
            Dialog(
                onDismissRequest = { showTypeDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
            ) {
                Box(modifier = Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
                    Card(
                        modifier = Modifier.fillMaxWidth(0.9f).padding(16.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(if (editingType == null) "New Inspection Type" else "Edit Inspection Type", style = MaterialTheme.typography.titleLarge)
                            OutlinedTextField(
                                value = typeName,
                                onValueChange = { typeName = it },
                                label = { Text("Name") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { showTypeDialog = false }) { Text("Cancel") }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = {
                                    if (typeName.isNotBlank()) {
                                        val newType = (editingType ?: InspectionType()).copy(name = typeName)
                                        viewModel.saveType(newType)
                                    }
                                    showTypeDialog = false
                                }) { Text("Save") }
                            }
                        }
                    }
                }
            }
        }

        typeToDelete?.let { type ->
            AlertDialog(
                onDismissRequest = { typeToDelete = null },
                title = { Text("Delete Type") },
                text = { Text("Are you sure you want to delete '${type.name}'? This will also orphan any fields assigned to this type.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteType(type)
                        typeToDelete = null
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { typeToDelete = null }) { Text("Cancel") } }
            )
        }

        if (showFieldDialog) {
            var label by remember { mutableStateOf(editingField?.label ?: "") }
            var fieldType by remember { mutableStateOf(editingField?.fieldType ?: FieldType.TEXT) }
            var required by remember { mutableStateOf(editingField?.required ?: false) }
            var options by remember { mutableStateOf(editingField?.options ?: "") }
            Dialog(
                onDismissRequest = { showFieldDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
            ) {
                Box(modifier = Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
                    Card(
                        modifier = Modifier.fillMaxWidth(0.95f).heightIn(max = 700.dp).padding(16.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(if (editingField == null) "Add Form Field" else "Edit Form Field", style = MaterialTheme.typography.titleLarge)
                            
                            OutlinedTextField(
                                value = label,
                                onValueChange = { label = it },
                                label = { Text("Field Label") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Field Type *", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    FieldType.values().forEach { ft ->
                                        FilterChip(
                                            selected = fieldType == ft,
                                            onClick = { fieldType = ft },
                                            label = { Text(ft.name.replace("_", " ")) },
                                            leadingIcon = if (fieldType == ft) {
                                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                            } else null
                                        )
                                    }
                                }
                            }

                            if (fieldType == FieldType.DROPDOWN || fieldType == FieldType.MULTI_SELECT) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedTextField(
                                        value = options,
                                        onValueChange = { options = it },
                                        label = {
                                            Text(if (fieldType == FieldType.MULTI_SELECT) "Multi-Select Options *" else "Dropdown Options *")
                                        },
                                        placeholder = { Text("Comma separated (e.g. Option 1, Option 2, Option 3)") },
                                        supportingText = {
                                            Text("Enter available choices separated by commas (,)")
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    val parsedOptions = options.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                    if (parsedOptions.isNotEmpty()) {
                                        Text(
                                            "Options Preview (${parsedOptions.size}):",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            parsedOptions.forEach { opt ->
                                                SuggestionChip(
                                                    onClick = {},
                                                    label = { Text(opt, style = MaterialTheme.typography.labelSmall) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = required, onCheckedChange = { required = it })
                                Text("Mandatory Field")
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { showFieldDialog = false }) { Text("Cancel") }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = {
                                    if (label.isNotBlank()) {
                                        val typeId = selectedTypeId ?: return@Button
                                        val newField = (editingField ?: InspectionField(inspectionTypeId = typeId)).copy(
                                            label = label,
                                            fieldName = label.lowercase().replace(" ", "_"),
                                            fieldType = fieldType,
                                            required = required,
                                            options = options,
                                            displayOrder = editingField?.displayOrder ?: fields.size
                                        )
                                        viewModel.saveField(newField)
                                    }
                                    showFieldDialog = false
                                }) { Text("Save") }
                            }
                        }
                    }
                }
            }
        }

        fieldToDelete?.let { field ->
            AlertDialog(
                onDismissRequest = { fieldToDelete = null },
                title = { Text("Delete Field") },
                text = { Text("Are you sure you want to delete the field '${field.label}'?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteField(field)
                        fieldToDelete = null
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { fieldToDelete = null }) { Text("Cancel") } }
            )
        }
    }
}
