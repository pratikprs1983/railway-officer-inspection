package com.example.models
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class InspectionStatus {
    ASSIGNED,
    ACCEPTED,
    DATE_CHANGE_REQUESTED,
    RESCHEDULED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
@Entity(tableName = "inspections")
data class Inspection(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val inspectionNumber: String = "",
    val inspectionTypeId: String = "",
    val inspectionTypeName: String = "",
    val assignedOfficerId: String = "",
    val assignedOfficerName: String = "",
    val locationId: String = "",
    val locationName: String = "",
    val status: InspectionStatus = InspectionStatus.ASSIGNED,
    val isSpotInspection: Boolean = false,
    val inspectionCategory: String = "ASSIGNED", // "ASSIGNED", "SPOT", "SELF"
    val scheduledDate: Long = 0L,
    val executionDate: Long? = null,
    val startLatitude: Double? = null,
    val startLongitude: Double? = null,
    val startAccuracy: Float? = null,
    val selfieURL: String = "",
    val remarks: String = "",
    val responses: String = "{}", // JSON map of fieldName to value
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val completedAt: Long? = null
)
