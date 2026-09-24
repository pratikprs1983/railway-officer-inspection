package com.example.models

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class NotificationType {
    INSPECTION_ASSIGNED,    // Admin assigned inspection to officer
    COMPLIANCE_ASSIGNED,    // Officer assigned a compliance point
    COMPLIANCE_ATTENDED     // Officer attended/resolved a compliance point
}

@Entity(tableName = "notifications")
data class AppNotification(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val recipientOfficerId: String = "",       // Target officer UID (or "ADMIN", "ALL", etc.)
    val recipientOfficerName: String = "",
    val senderName: String = "System",         // Admin name or Inspecting Officer name
    val title: String = "",
    val message: String = "",
    val type: NotificationType = NotificationType.INSPECTION_ASSIGNED,
    val inspectionId: String = "",
    val inspectionNumber: String = "",
    val complianceId: String = "",
    val deepLinkRoute: String = "",            // e.g. "inspection/{inspectionId}" or "dashboard_compliances"
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
