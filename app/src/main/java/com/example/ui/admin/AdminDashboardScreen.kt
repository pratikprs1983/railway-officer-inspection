package com.example.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.FirebaseSyncManager
import kotlinx.coroutines.launch

data class AdminMenu(val title: String, val icon: ImageVector, val route: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit
) {
    val menus = listOf(
        AdminMenu("Users & Roles", Icons.Default.Group, "admin_users"),
        AdminMenu("Master Data", Icons.Default.Map, "admin_master_data"),
        AdminMenu("Form Builder", Icons.Default.Build, "admin_forms"),
        AdminMenu("Inspections", Icons.Default.Assessment, "admin_inspections"),
        AdminMenu("Compliances", Icons.Default.CheckCircle, "admin_compliances"),
        AdminMenu("Field Officer Mode", Icons.Default.DirectionsRun, "dashboard"),
        AdminMenu("Reports", Icons.Default.PictureAsPdf, "admin_reports")
    )

    val syncStatus by FirebaseSyncManager.syncStatus.collectAsState()
    val isSyncing by FirebaseSyncManager.isSyncing.collectAsState()
    val isPlaceholder = FirebaseSyncManager.isPlaceholderConfig
    val currentProject = FirebaseSyncManager.currentProjectId
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    val notifRepo = remember { com.example.data.NotificationRepository() }
    val allNotifications by notifRepo.getAllNotifications().collectAsState(initial = emptyList())
    val unreadNotifsCount = remember(allNotifications) { allNotifications.count { !it.isRead } }
    var showNotificationCenter by remember { mutableStateOf(false) }
    var showFirebaseConfigDialog by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Admin Dashboard") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(
                        onClick = { showNotificationCenter = true },
                        modifier = Modifier.testTag("admin_notifications_btn")
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
                    IconButton(
                        onClick = {
                            FirebaseSyncManager.syncAllToCloud { success, message ->
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(message)
                                }
                            }
                        }
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.CloudSync, contentDescription = "Sync to Cloud", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    IconButton(onClick = { onNavigate("dashboard") }) {
                        Icon(Icons.Default.DirectionsRun, contentDescription = "Field Officer Mode", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Logout", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("admin_bottom_navigation"),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = true,
                    onClick = { /* already on Admin Hub */ },
                    icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = "Admin Hub") },
                    label = { Text("Admin Hub", fontWeight = FontWeight.Bold) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { onNavigate("admin_inspections") },
                    icon = { Icon(Icons.Default.Assessment, contentDescription = "Inspections") },
                    label = { Text("Inspections") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { onNavigate("admin_compliances") },
                    icon = { Icon(Icons.Default.PendingActions, contentDescription = "Compliances") },
                    label = { Text("Compliances") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { onNavigate("admin_reports") },
                    icon = { Icon(Icons.Default.PictureAsPdf, contentDescription = "Reports") },
                    label = { Text("Reports") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { onNavigate("dashboard") },
                    icon = { Icon(Icons.Default.DirectionsRun, contentDescription = "Field Officer Mode") },
                    label = { Text("Field Mode") }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Cloud Firestore Status Banner
            Surface(
                color = if (isPlaceholder) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
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
                        Icon(
                            imageVector = if (isPlaceholder) Icons.Default.CloudOff else Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = if (isPlaceholder) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isPlaceholder) "Firebase: Disconnected (Offline Mode)" else "Firebase Firestore: $currentProject",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isPlaceholder) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = if (isPlaceholder) {
                                    "App using placeholder credentials. Connect 'roicms-de75c' to sync."
                                } else {
                                    if (isSyncing) "Syncing data to Cloud..." else syncStatus
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isPlaceholder) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

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
                                FirebaseSyncManager.syncAllToCloud { success, message ->
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(message)
                                    }
                                }
                            },
                            enabled = !isSyncing,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(if (isSyncing) "Syncing..." else "Sync Cloud")
                        }
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(menus) { menu ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clickable { onNavigate(menu.route) },
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = menu.icon,
                                contentDescription = menu.title,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = menu.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        if (showNotificationCenter) {
            com.example.ui.common.NotificationCenterDialog(
                onDismiss = { showNotificationCenter = false },
                onNavigateToInspection = {
                    onNavigate("admin_inspections")
                },
                onNavigateToCompliances = {
                    onNavigate("admin_compliances")
                }
            )
        }

        if (showFirebaseConfigDialog) {
            FirebaseConfigDialog(
                currentProjectId = currentProject,
                isPlaceholder = isPlaceholder,
                onDismiss = { showFirebaseConfigDialog = false },
                onApplyJson = { jsonString ->
                    val result = FirebaseSyncManager.applyGoogleServicesJson(context, jsonString)
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
    }
}
