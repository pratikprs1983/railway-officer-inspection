package com.example.ui.admin

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.FirebaseSyncManager
import com.example.models.Role
import com.example.models.User
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementScreen(
    onBack: () -> Unit,
    viewModel: UserViewModel = viewModel()
) {
    val users by viewModel.users.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val isPlaceholder = viewModel.isPlaceholderConfig
    val currentProject = viewModel.currentProjectId

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showFormDialog by remember { mutableStateOf(false) }
    var showFirebaseConfigDialog by remember { mutableStateOf(false) }
    var editingUser by remember { mutableStateOf<User?>(null) }
    var userToDelete by remember { mutableStateOf<User?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("User Management") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.syncAllUsers { success, message ->
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(message)
                                }
                            }
                        },
                        enabled = !isSyncing
                    ) {
                        Icon(Icons.Default.CloudSync, contentDescription = "Sync with Firebase", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    IconButton(onClick = { showFirebaseConfigDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Firebase Settings", tint = MaterialTheme.colorScheme.onPrimary)
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
                editingUser = null
                showFormDialog = true 
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add User")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Cloud Sync Status Banner
            Surface(
                color = if (isPlaceholder) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isPlaceholder) Icons.Default.CloudOff else Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = if (isPlaceholder) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (isPlaceholder) "Cloud Sync: Disconnected (Offline Mode)" else "Firebase: $currentProject",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPlaceholder) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = if (isPlaceholder) {
                                        "Saved users are on device. Connect 'roicms-de75c' to sync."
                                    } else {
                                        if (isSyncing) "Syncing with Firestore..." else syncStatus
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isPlaceholder) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isPlaceholder) {
                                FilledTonalButton(
                                    onClick = { showFirebaseConfigDialog = true },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Connect", style = MaterialTheme.typography.labelMedium)
                                }
                            } else {
                                FilledTonalButton(
                                    onClick = {
                                        viewModel.syncAllUsers { success, msg ->
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(msg)
                                            }
                                        }
                                    },
                                    enabled = !isSyncing,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(if (isSyncing) "Syncing..." else "Sync", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                if (isLoading && users.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (users.isEmpty()) {
                    Text(
                        text = "No users found. Tap '+' to add an officer.",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Registered Officers & Admins (${users.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                TextButton(
                                    onClick = {
                                        viewModel.syncAllUsers { success, msg ->
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(msg)
                                            }
                                        }
                                    },
                                    enabled = !isSyncing
                                ) {
                                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Push to Cloud")
                                }
                            }
                        }
                        items(users) { user ->
                            UserCard(
                                user = user,
                                onEdit = {
                                    editingUser = user
                                    showFormDialog = true
                                },
                                onDelete = {
                                    userToDelete = user
                                }
                            )
                        }
                    }
                }
            }
        }
        
        if (showFormDialog) {
            UserFormDialog(
                user = editingUser,
                onDismiss = { showFormDialog = false },
                onSaveUser = { newUser ->
                    viewModel.addUser(newUser) { success, msg ->
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(msg)
                        }
                    }
                    showFormDialog = false
                }
            )
        }

        if (showFirebaseConfigDialog) {
            FirebaseConfigDialog(
                currentProjectId = currentProject,
                isPlaceholder = isPlaceholder,
                onDismiss = { showFirebaseConfigDialog = false },
                onApplyJson = { jsonString ->
                    val result = viewModel.applyGoogleServicesJson(context, jsonString)
                    result.fold(
                        onSuccess = { msg ->
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(msg)
                            }
                            showFirebaseConfigDialog = false
                        },
                        onFailure = { err ->
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Error: ${err.localizedMessage}")
                            }
                        }
                    )
                }
            )
        }

        userToDelete?.let { user ->
            AlertDialog(
                onDismissRequest = { userToDelete = null },
                title = { Text("Delete User") },
                text = { Text("Are you sure you want to delete ${user.name}?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteUser(user) { success, msg ->
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(msg)
                            }
                        }
                        userToDelete = null
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { userToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun UserCard(user: User, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(user.name.ifEmpty { "Unknown Name" }, style = MaterialTheme.typography.titleMedium)
                Text("${user.designation} • ${user.departmentName}", style = MaterialTheme.typography.bodyMedium)
                Text("Phone: ${user.mobile.ifEmpty { "N/A" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("HRMS: ${user.hrmsId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Role Badge
            Surface(
                color = when (user.role) {
                    Role.SUPER_ADMIN -> MaterialTheme.colorScheme.tertiaryContainer
                    Role.ADMIN -> MaterialTheme.colorScheme.primaryContainer
                    Role.OFFICER -> MaterialTheme.colorScheme.secondaryContainer
                },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = when (user.role) {
                        Role.SUPER_ADMIN -> "Super Admin"
                        Role.ADMIN -> "Admin"
                        Role.OFFICER -> "Officer"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when (user.role) {
                        Role.SUPER_ADMIN -> MaterialTheme.colorScheme.onTertiaryContainer
                        Role.ADMIN -> MaterialTheme.colorScheme.onPrimaryContainer
                        Role.OFFICER -> MaterialTheme.colorScheme.onSecondaryContainer
                    },
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit User", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete User", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserFormDialog(user: User?, onDismiss: () -> Unit, onSaveUser: (User) -> Unit) {
    var name by remember { mutableStateOf(user?.name ?: "") }
    var mobile by remember { mutableStateOf(user?.mobile ?: "") }
    var hrmsId by remember { mutableStateOf(user?.hrmsId ?: "") }
    var designation by remember { mutableStateOf(user?.designation ?: "") }
    
    val departments = listOf("MECHANICAL", "ELECTRICAL", "ENGINEERING", "PERSONNEL", "SAFETY", "SIGNAL & TELECOM", "GENERAL ADMINISTRATION")
    var department by remember { mutableStateOf(user?.departmentName ?: departments.first()) }
    if (department.isEmpty() || !departments.contains(department)) {
        department = departments.first()
    }
    var expandedDept by remember { mutableStateOf(false) }

    val roles = listOf(Role.OFFICER to "Officer", Role.ADMIN to "Admin")
    var role by remember { mutableStateOf(user?.role ?: Role.OFFICER) }
    var expandedRole by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (user == null) "Add New User" else "Edit User") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = mobile, 
                    onValueChange = { if (it.length <= 10 && it.all { char -> char.isDigit() }) mobile = it }, 
                    label = { Text("Phone Number (10 digits)") }, 
                    singleLine = true, 
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = mobile.isNotEmpty() && mobile.length != 10
                )
                OutlinedTextField(
                    value = hrmsId, 
                    onValueChange = { if (it.length <= 6 && it.all { char -> char.isLetter() }) hrmsId = it.uppercase() }, 
                    label = { Text("HRMS ID (6 letters)") }, 
                    singleLine = true, 
                    modifier = Modifier.fillMaxWidth(),
                    isError = hrmsId.isNotEmpty() && hrmsId.length != 6
                )
                OutlinedTextField(value = designation, onValueChange = { designation = it }, label = { Text("Designation") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                
                ExposedDropdownMenuBox(
                    expanded = expandedDept,
                    onExpandedChange = { expandedDept = !expandedDept }
                ) {
                    OutlinedTextField(
                        value = department,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Department") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDept) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedDept,
                        onDismissRequest = { expandedDept = false }
                    ) {
                        departments.forEach { dept ->
                            DropdownMenuItem(
                                text = { Text(dept) },
                                onClick = {
                                    department = dept
                                    expandedDept = false
                                }
                            )
                        }
                    }
                }
                
                ExposedDropdownMenuBox(
                    expanded = expandedRole,
                    onExpandedChange = { expandedRole = !expandedRole }
                ) {
                    OutlinedTextField(
                        value = roles.first { it.first == role }.second,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Role") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedRole) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedRole,
                        onDismissRequest = { expandedRole = false }
                    ) {
                        roles.forEach { r ->
                            DropdownMenuItem(
                                text = { Text(r.second) },
                                onClick = {
                                    role = r.first
                                    expandedRole = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && hrmsId.length == 6 && mobile.length == 10,
                onClick = {
                    if (name.isNotBlank() && hrmsId.length == 6 && mobile.length == 10) {
                        val newUser = (user ?: User()).copy(
                            name = name,
                            mobile = mobile,
                            hrmsId = hrmsId,
                            designation = designation,
                            departmentName = department,
                            role = role,
                            updatedAt = System.currentTimeMillis()
                        )
                        onSaveUser(newUser)
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun FirebaseConfigDialog(
    currentProjectId: String,
    isPlaceholder: Boolean,
    onDismiss: () -> Unit,
    onApplyJson: (String) -> Unit
) {
    var jsonText by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isPlaceholder) Icons.Default.CloudOff else Icons.Default.CloudDone,
                    contentDescription = null,
                    tint = if (isPlaceholder) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Firebase Cloud Connection")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Status banner
                Surface(
                    color = if (isPlaceholder) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = if (isPlaceholder) "Status: Disconnected (Placeholder Config)" else "Status: Connected ($currentProjectId)",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isPlaceholder) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = if (isPlaceholder) {
                                "The app is currently using template credentials ('$currentProjectId'). User records are saved locally on the device and will not reach Firebase until connected."
                            } else {
                                "Connected to Cloud Firestore. Users and inspections are syncing in realtime."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isPlaceholder) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // App Identifiers for Firebase Console
                Text("App Registration Details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Target Project ID:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(FirebaseSyncManager.EXPECTED_PROJECT_ID, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(FirebaseSyncManager.EXPECTED_PROJECT_ID))
                                    Toast.makeText(context, "Copied Project ID", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                            }
                        }

                        Divider()

                        Text("Android Package Name:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(FirebaseSyncManager.APP_PACKAGE_NAME, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(FirebaseSyncManager.APP_PACKAGE_NAME))
                                    Toast.makeText(context, "Copied Package Name", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                            }
                        }

                        Divider()

                        Text("Debug Keystore SHA-1:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(FirebaseSyncManager.DEBUG_SHA1, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(FirebaseSyncManager.DEBUG_SHA1))
                                    Toast.makeText(context, "Copied SHA-1", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                Text(
                    "Paste google-services.json content below to connect:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )

                OutlinedTextField(
                    value = jsonText,
                    onValueChange = { jsonText = it },
                    label = { Text("google-services.json content") },
                    placeholder = { Text("{\n  \"project_info\": {\n    \"project_id\": \"roicms-de75c\" ...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onApplyJson(jsonText) },
                enabled = jsonText.isNotBlank() && jsonText.contains("project_info")
            ) {
                Text("Connect & Sync")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

