package com.example.data

import com.example.MyApplication
import com.example.models.Compliance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ComplianceRepository {
    private val complianceDao = MyApplication.instance.database.complianceDao()

    fun getAllCompliances(): Flow<List<Compliance>> = complianceDao.getAllCompliances()

    fun getCompliancesByInspection(inspectionId: String): Flow<List<Compliance>> =
        complianceDao.getCompliancesByInspection(inspectionId)

    fun saveCompliance(compliance: Compliance, onComplete: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val existing = complianceDao.getComplianceById(compliance.id)
                complianceDao.insertCompliance(compliance)
                FirebaseSyncManager.syncCompliance(compliance)

                // Notification Triggers
                if (existing == null) {
                    // Scenario 2: Officer assigned with new compliance
                    if (compliance.assignedOfficerId.isNotBlank()) {
                        com.example.util.NotificationService.notifyComplianceAssigned(
                            compliance = compliance,
                            assignedBy = compliance.inspectingOfficerName.ifBlank {
                                SessionManager.currentUser.value?.name ?: "Inspecting Officer"
                            }
                        )
                    }
                } else {
                    // Check if reassigned to a new officer
                    if (existing.assignedOfficerId != compliance.assignedOfficerId && compliance.assignedOfficerId.isNotBlank()) {
                        com.example.util.NotificationService.notifyComplianceAssigned(
                            compliance = compliance,
                            assignedBy = SessionManager.currentUser.value?.name ?: "Supervisor"
                        )
                    }
                    // Scenario 3: Compliance attended by officer
                    val statusChangedToAttended = (compliance.status != existing.status) &&
                            (compliance.status == com.example.models.ComplianceStatus.UNDER_ACTION ||
                             compliance.status == com.example.models.ComplianceStatus.RESOLVED ||
                             compliance.status == com.example.models.ComplianceStatus.CLOSED)
                    val remarksAdded = compliance.complianceRemarks.isNotBlank() &&
                            compliance.complianceRemarks != existing.complianceRemarks

                    if (statusChangedToAttended || remarksAdded) {
                        val attendingOfficer = SessionManager.currentUser.value?.name
                            ?: compliance.assignedOfficerName.ifBlank { "Officer" }
                        com.example.util.NotificationService.notifyComplianceAttended(
                            compliance = compliance,
                            attendedByOfficerName = attendingOfficer,
                            remarks = compliance.complianceRemarks.ifBlank { compliance.actionTaken }
                        )
                    }
                }

                withContext(Dispatchers.Main) { onComplete(true) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onComplete(false) }
            }
        }
    }

    fun deleteCompliance(compliance: Compliance, onComplete: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                complianceDao.deleteCompliance(compliance)
                FirebaseSyncManager.deleteCompliance(compliance.id)
                withContext(Dispatchers.Main) { onComplete(true) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onComplete(false) }
            }
        }
    }
}
