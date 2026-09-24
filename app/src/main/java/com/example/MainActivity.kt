package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.example.ui.auth.LoginScreen
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.admin.AdminDashboardScreen
import com.example.ui.admin.UserManagementScreen
import com.example.ui.theme.MyApplicationTheme

import androidx.lifecycle.Lifecycle

fun androidx.navigation.NavController.safePopBackStack() {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        popBackStack()
    }
}

class MainActivity : ComponentActivity() {
  private val pendingRouteFlow = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

  override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    val route = intent.getStringExtra(com.example.util.NotificationService.EXTRA_TARGET_ROUTE)
    if (!route.isNullOrBlank()) {
      pendingRouteFlow.value = route
    }
  }

  @OptIn(ExperimentalMaterial3Api::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val initialRoute = intent?.getStringExtra(com.example.util.NotificationService.EXTRA_TARGET_ROUTE)
    if (!initialRoute.isNullOrBlank()) {
      pendingRouteFlow.value = initialRoute
    }

    setContent {
      MyApplicationTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          val navController = rememberNavController()

          // Request Notification Permission on Android 13+ (API 33+)
          val postNotificationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
          ) { /* permission result handled */ }

          LaunchedEffect(Unit) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
              if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                postNotificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
              }
            }
          }

          // Handle Deep Links from Notifications
          val pendingRoute by pendingRouteFlow.collectAsState()
          LaunchedEffect(pendingRoute) {
            val route = pendingRoute
            if (!route.isNullOrBlank()) {
              try {
                navController.navigate(route)
              } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to navigate to route: $route", e)
              }
              pendingRouteFlow.value = null
            }
          }

          NavHost(navController = navController, startDestination = "login") {
            composable("login") {
              LoginScreen(
                onLoginSuccess = { role ->
                  val target = pendingRouteFlow.value
                  if (!target.isNullOrBlank()) {
                    navController.navigate(target) {
                      popUpTo("login") { inclusive = true }
                    }
                    pendingRouteFlow.value = null
                  } else if (role == com.example.models.Role.ADMIN || role == com.example.models.Role.SUPER_ADMIN) {
                    navController.navigate("admin_dashboard") {
                      popUpTo("login") { inclusive = true }
                    }
                  } else {
                    navController.navigate("dashboard") {
                      popUpTo("login") { inclusive = true }
                    }
                  }
                }
              )
            }
            composable("dashboard") {
              DashboardScreen(
                initialTab = 0,
                onLogout = {
                  com.example.data.SessionManager.logout()
                  navController.navigate("login") {
                    popUpTo("dashboard") { inclusive = true }
                  }
                },
                onNavigateToInspection = { inspectionId ->
                  navController.navigate("inspection/$inspectionId")
                },
                onSwitchToAdmin = {
                  navController.navigate("admin_dashboard")
                },
                onNavigateToUserManagement = {
                  navController.navigate("admin_users")
                },
                onNavigateToMasterData = {
                  navController.navigate("admin_master_data")
                },
                onNavigateToFormBuilder = {
                  navController.navigate("admin_forms")
                },
                onNavigateToAssignInspection = {
                  navController.navigate("admin_inspections")
                },
                onNavigateToAdminCompliances = {
                  navController.navigate("admin_compliances")
                },
                onNavigateToAdminReports = {
                  navController.navigate("admin_reports")
                }
              )
            }
            composable("dashboard_compliances") {
              DashboardScreen(
                initialTab = 1,
                onLogout = {
                  com.example.data.SessionManager.logout()
                  navController.navigate("login") {
                    popUpTo("dashboard_compliances") { inclusive = true }
                  }
                },
                onNavigateToInspection = { inspectionId ->
                  navController.navigate("inspection/$inspectionId")
                },
                onSwitchToAdmin = {
                  navController.navigate("admin_dashboard")
                },
                onNavigateToUserManagement = {
                  navController.navigate("admin_users")
                },
                onNavigateToMasterData = {
                  navController.navigate("admin_master_data")
                },
                onNavigateToFormBuilder = {
                  navController.navigate("admin_forms")
                },
                onNavigateToAssignInspection = {
                  navController.navigate("admin_inspections")
                },
                onNavigateToAdminCompliances = {
                  navController.navigate("admin_compliances")
                },
                onNavigateToAdminReports = {
                  navController.navigate("admin_reports")
                }
              )
            }
            composable(
              route = "inspection/{inspectionId}",
              arguments = listOf(navArgument("inspectionId") { type = NavType.StringType })
            ) { backStackEntry ->
              val inspectionId = backStackEntry.arguments?.getString("inspectionId") ?: ""
              com.example.ui.inspection.InspectionExecutionScreen(
                inspectionId = inspectionId,
                onBack = { navController.safePopBackStack() }
              )
            }
            composable("admin_dashboard") {
              AdminDashboardScreen(
                onNavigate = { route -> navController.navigate(route) },
                onLogout = {
                  com.example.data.SessionManager.logout()
                  navController.navigate("login") {
                    popUpTo("admin_dashboard") { inclusive = true }
                  }
                }
              )
            }
            composable("admin_users") {
              UserManagementScreen(
                onBack = { navController.safePopBackStack() }
              )
            }
            composable("admin_master_data") {
                com.example.ui.admin.MasterDataScreen(
                    onBack = { navController.safePopBackStack() }
                )
            }
            composable("admin_forms") {
                com.example.ui.admin.FormBuilderScreen(
                    onBack = { navController.safePopBackStack() }
                )
            }
            composable("admin_inspections") {
                com.example.ui.admin.AssignInspectionScreen(
                    onBack = { navController.safePopBackStack() }
                )
            }
            composable("admin_compliances") {
                com.example.ui.admin.AdminCompliancesScreen(
                    onBack = { navController.safePopBackStack() }
                )
            }
            composable("admin_reports") {
                com.example.ui.admin.AdminReportsScreen(
                    onBack = { navController.safePopBackStack() }
                )
            }
          }
        }
      }
    }
  }
}
