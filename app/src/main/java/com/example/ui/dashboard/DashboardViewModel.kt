package com.example.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ComplianceRepository
import com.example.data.InspectionRepository
import com.example.data.MasterDataRepository
import com.example.data.SessionManager
import com.example.data.UserRepository
import com.example.models.Compliance
import com.example.models.ComplianceStatus
import com.example.models.Inspection
import com.example.models.InspectionStatus
import com.example.models.InspectionType
import com.example.models.LCGate
import com.example.models.Role
import com.example.models.Section
import com.example.models.Station
import com.example.models.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DashboardViewModel : ViewModel() {
    private val repo = InspectionRepository()
    private val userRepo = UserRepository()
    private val complianceRepo = ComplianceRepository()
    private val masterDataRepo = MasterDataRepository()

    private val _inspections = MutableStateFlow<List<Inspection>>(emptyList())
    val inspections: StateFlow<List<Inspection>> = _inspections.asStateFlow()

    private val _allInspections = MutableStateFlow<List<Inspection>>(emptyList())
    val allInspections: StateFlow<List<Inspection>> = _allInspections.asStateFlow()

    val currentUser: StateFlow<User?> = SessionManager.currentUser
    val authenticatedUser: StateFlow<User?> = SessionManager.authenticatedUser

    private val _officers = MutableStateFlow<List<User>>(emptyList())
    val officers: StateFlow<List<User>> = _officers.asStateFlow()

    private val _types = MutableStateFlow<List<InspectionType>>(emptyList())
    val types: StateFlow<List<InspectionType>> = _types.asStateFlow()

    private val _stations = MutableStateFlow<List<Station>>(emptyList())
    val stations: StateFlow<List<Station>> = _stations.asStateFlow()

    private val _sections = MutableStateFlow<List<Section>>(emptyList())
    val sections: StateFlow<List<Section>> = _sections.asStateFlow()

    private val _lcGates = MutableStateFlow<List<LCGate>>(emptyList())
    val lcGates: StateFlow<List<LCGate>> = _lcGates.asStateFlow()

    // Compliances assigned to the current officer (Action Officer role)
    private val _assignedCompliances = MutableStateFlow<List<Compliance>>(emptyList())
    val assignedCompliances: StateFlow<List<Compliance>> = _assignedCompliances.asStateFlow()

    // All compliances across the system (used for inspecting authority tracking per inspection)
    private val _allCompliances = MutableStateFlow<List<Compliance>>(emptyList())
    val allCompliances: StateFlow<List<Compliance>> = _allCompliances.asStateFlow()

    init {
        // Load available officers (Officers, Admins, and Super Admin)
        viewModelScope.launch {
            combine(userRepo.getUsers(), SessionManager.authenticatedUser) { userList, authUser ->
                val combined = mutableListOf<User>()
                if (authUser != null) {
                    combined.add(authUser)
                }
                userList.forEach { u ->
                    if (combined.none { it.uid == u.uid || (it.hrmsId.isNotBlank() && it.hrmsId.equals(u.hrmsId, ignoreCase = true)) }) {
                        combined.add(u)
                    }
                }
                combined
            }.catch { e -> android.util.Log.e("Dashboard", "Error fetching officers", e) }
            .collect { list ->
                _officers.value = list

                // If no active user session, default to authenticated user or first available officer
                if (SessionManager.currentUser.value == null) {
                    val auth = SessionManager.authenticatedUser.value
                    val defaultUser = auth ?: list.firstOrNull()
                    if (defaultUser != null) {
                        SessionManager.setCurrentUser(defaultUser)
                    }
                }
            }
        }

        // Load all inspections for reference & lookup
        viewModelScope.launch {
            repo.getInspections().catch { e ->
                android.util.Log.e("Dashboard", "Error loading all inspections", e)
            }.collect { list ->
                _allInspections.value = list
            }
        }

        // Filter inspections strictly by active officer
        viewModelScope.launch {
            combine(repo.getInspections(), SessionManager.currentUser) { list, user ->
                if (user != null) {
                    list.filter { inspection ->
                        SessionManager.isInspectionAssignedToUser(inspection, user)
                    }
                } else {
                    emptyList()
                }
            }.catch { e ->
                android.util.Log.e("Dashboard", "Error filtering inspections for officer", e)
            }.collect { filteredList ->
                _inspections.value = filteredList
            }
        }

        // Compliances observation and dual-role separation
        viewModelScope.launch {
            combine(complianceRepo.getAllCompliances(), SessionManager.currentUser) { list, user ->
                _allCompliances.value = list
                if (user != null) {
                    list.filter { comp ->
                        comp.assignedOfficerId == user.uid ||
                        (comp.assignedOfficerName.isNotBlank() && comp.assignedOfficerName.equals(user.name, ignoreCase = true))
                    }
                } else {
                    emptyList()
                }
            }.catch { e ->
                android.util.Log.e("Dashboard", "Error filtering compliances for officer", e)
            }.collect { userCompliances ->
                _assignedCompliances.value = userCompliances
            }
        }

        // Load inspection types & master locations for Spot / Self Inspection
        viewModelScope.launch {
            repo.getInspectionTypes()
                .catch { e -> android.util.Log.e("Dashboard", "Error fetching types", e) }
                .collect { _types.value = it }
        }
        viewModelScope.launch {
            masterDataRepo.getStations()
                .catch { e -> android.util.Log.e("Dashboard", "Error fetching stations", e) }
                .collect { _stations.value = it }
        }
        viewModelScope.launch {
            masterDataRepo.getSections()
                .catch { e -> android.util.Log.e("Dashboard", "Error fetching sections", e) }
                .collect { _sections.value = it }
        }
        viewModelScope.launch {
            masterDataRepo.getLCGates()
                .catch { e -> android.util.Log.e("Dashboard", "Error fetching lc gates", e) }
                .collect { _lcGates.value = it }
        }
    }

    fun switchOfficer(user: User) {
        if (SessionManager.isAdmin()) {
            SessionManager.setCurrentUser(user)
        }
    }

    fun addStation(station: Station, onComplete: (Station) -> Unit) {
        masterDataRepo.saveStation(station) { success ->
            if (success) {
                onComplete(station)
            }
        }
    }

    fun addSection(section: Section, onComplete: (Section) -> Unit) {
        masterDataRepo.saveSection(section) { success ->
            if (success) {
                onComplete(section)
            }
        }
    }

    fun addLCGate(gate: LCGate, onComplete: (LCGate) -> Unit) {
        masterDataRepo.saveLCGate(gate) { success ->
            if (success) {
                onComplete(gate)
            }
        }
    }

    fun createSpotInspection(
        type: InspectionType,
        locationName: String,
        locationId: String = "",
        isSelfInspection: Boolean = false,
        remarks: String = "",
        scheduledDate: Long = System.currentTimeMillis(),
        executionDate: Long = System.currentTimeMillis(),
        selfieUrl: String = "",
        startLatitude: Double? = null,
        startLongitude: Double? = null,
        startAccuracy: Float? = null,
        startNow: Boolean = false,
        onComplete: (Inspection) -> Unit
    ) {
        val user = currentUser.value ?: return
        val prefix = if (isSelfInspection) "SELF" else "SPOT"
        val timestamp = System.currentTimeMillis()
        val formattedNumber = "$prefix-2026-${java.util.UUID.randomUUID().toString().take(6).uppercase()}"
        val newInspection = Inspection(
            id = java.util.UUID.randomUUID().toString(),
            inspectionNumber = formattedNumber,
            inspectionTypeId = type.id,
            inspectionTypeName = type.name,
            assignedOfficerId = user.uid,
            assignedOfficerName = user.name,
            locationId = locationId,
            locationName = locationName.trim().ifEmpty { "Spot Location" },
            status = if (startNow) InspectionStatus.IN_PROGRESS else InspectionStatus.ASSIGNED,
            isSpotInspection = true,
            inspectionCategory = if (isSelfInspection) "SELF" else "SPOT",
            scheduledDate = scheduledDate,
            executionDate = executionDate,
            selfieURL = selfieUrl,
            startLatitude = startLatitude,
            startLongitude = startLongitude,
            startAccuracy = startAccuracy,
            startedAt = if (startNow) timestamp else 0L,
            remarks = remarks.trim(),
            createdAt = timestamp,
            updatedAt = timestamp
        )
        repo.assignInspection(newInspection) { success ->
            if (success) {
                onComplete(newInspection)
            }
        }
    }

    fun updateComplianceAction(
        compliance: Compliance,
        newStatus: ComplianceStatus,
        remarks: String,
        photoUri: String = "",
        onComplete: (Boolean) -> Unit = {}
    ) {
        val updated = compliance.copy(
            status = newStatus,
            complianceRemarks = remarks.ifBlank { compliance.complianceRemarks },
            actionTaken = remarks.ifBlank { compliance.actionTaken },
            compliancePhotoUri = photoUri.ifBlank { compliance.compliancePhotoUri },
            closedAt = if (newStatus == ComplianceStatus.RESOLVED || newStatus == ComplianceStatus.CLOSED) System.currentTimeMillis() else compliance.closedAt,
            updatedAt = System.currentTimeMillis()
        )
        complianceRepo.saveCompliance(updated) { success ->
            onComplete(success)
        }
    }
}
