package com.example.models

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ComplianceStatus {
    OPEN,
    UNDER_ACTION,
    RESOLVED,
    CLOSED
}

enum class ComplianceSeverity {
    CRITICAL,
    MAJOR,
    MINOR
}

@Entity(tableName = "compliances")
data class Compliance(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val inspectionId: String = "",
    val inspectionNumber: String = "",
    val inspectionPointId: String = "",    // Unique ID or key of the inspection checklist point / observation
    val inspectionPointTitle: String = "", // Specific inspection observation point / checklist item
    val title: String = "",
    val description: String = "",
    val locationName: String = "",
    val inspectingOfficerId: String = "",   // The officer who inspected and raised this compliance
    val inspectingOfficerName: String = "", // Name of inspecting officer
    val assignedOfficerId: String = "",     // The officer responsible for compliance action
    val assignedOfficerName: String = "",   // Name of compliance action officer
    val severity: ComplianceSeverity = ComplianceSeverity.MAJOR,
    val status: ComplianceStatus = ComplianceStatus.OPEN,
    val targetDate: Long = System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000), // default +7 days
    val actionTaken: String = "",
    val complianceRemarks: String = "",     // Remarks by receiving officer
    val compliancePhotoUri: String = "",    // Proof photo of compliance work
    val closedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
