package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.MyApplication
import com.example.R
import com.example.data.NotificationRepository
import com.example.models.AppNotification
import com.example.models.Compliance
import com.example.models.Inspection
import com.example.models.NotificationType

object NotificationService {
    private const val TAG = "NotificationService"

    const val CHANNEL_INSPECTIONS = "roicms_assigned_inspections"
    const val CHANNEL_COMPLIANCES = "roicms_compliances"

    const val EXTRA_TARGET_ROUTE = "EXTRA_TARGET_ROUTE"
    const val EXTRA_INSPECTION_ID = "EXTRA_INSPECTION_ID"
    const val EXTRA_COMPLIANCE_ID = "EXTRA_COMPLIANCE_ID"
    const val EXTRA_NOTIFICATION_ID = "EXTRA_NOTIFICATION_ID"

    private val repository = NotificationRepository()

    fun initChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            // Channel 1: Assigned Inspections
            val inspectionChannel = NotificationChannel(
                CHANNEL_INSPECTIONS,
                "Assigned Safety Inspections",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent alerts when admin or supervisor assigns a safety inspection"
                enableVibration(true)
                enableLights(true)
            }

            // Channel 2: Compliances & Attended Notifications
            val complianceChannel = NotificationChannel(
                CHANNEL_COMPLIANCES,
                "Compliance & Action Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for newly assigned and attended compliance observations"
                enableVibration(true)
                enableLights(true)
            }

            notificationManager.createNotificationChannel(inspectionChannel)
            notificationManager.createNotificationChannel(complianceChannel)
            Log.d(TAG, "Notification channels initialized successfully")
        }
    }

    /**
     * Posts an Android System Notification with deep link PendingIntent and Action Buttons.
     */
    fun showSystemNotification(context: Context, notification: AppNotification) {
        try {
            initChannels(context)

            val channelId = if (notification.type == NotificationType.INSPECTION_ASSIGNED) {
                CHANNEL_INSPECTIONS
            } else {
                CHANNEL_COMPLIANCES
            }

            // Main tap Intent
            val contentIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_TARGET_ROUTE, notification.deepLinkRoute)
                putExtra(EXTRA_INSPECTION_ID, notification.inspectionId)
                putExtra(EXTRA_COMPLIANCE_ID, notification.complianceId)
                putExtra(EXTRA_NOTIFICATION_ID, notification.id)
            }

            val requestCode = (notification.id.hashCode() and 0x7FFFFFFF)
            val pendingContentIntent = PendingIntent.getActivity(
                context,
                requestCode,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Direct Action Button on Notification with link
            val actionTitle = when (notification.type) {
                NotificationType.INSPECTION_ASSIGNED -> "Open Inspection ➔"
                NotificationType.COMPLIANCE_ASSIGNED -> "Attend Compliance ➔"
                NotificationType.COMPLIANCE_ATTENDED -> "View Details ➔"
            }

            val actionPendingIntent = PendingIntent.getActivity(
                context,
                requestCode + 1,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(notification.title)
                .setContentText(notification.message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(notification.message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingContentIntent)
                .addAction(android.R.drawable.ic_menu_send, actionTitle, actionPendingIntent)

            val notificationManager = NotificationManagerCompat.from(context)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationManager.notify(requestCode, builder.build())
                Log.d(TAG, "System notification dispatched for: ${notification.title}")
            } else {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted. Notification recorded in-app.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to display system notification: ${e.message}", e)
        }
    }

    /**
     * 1. When an Admin assigns any inspection to an officer:
     * Officer should get notification with link.
     */
    fun notifyInspectionAssigned(inspection: Inspection, adminName: String = "Admin") {
        if (inspection.assignedOfficerId.isBlank() && inspection.assignedOfficerName.isBlank()) {
            return
        }

        val formattedDate = if (inspection.scheduledDate > 0) {
            java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
                .format(java.util.Date(inspection.scheduledDate))
        } else "Immediate"

        val title = "📋 Inspection Assigned: ${inspection.inspectionNumber}"
        val message = "Admin $adminName has assigned inspection ${inspection.inspectionNumber} (${inspection.inspectionTypeName}) at ${inspection.locationName} to you. Scheduled: $formattedDate. Tap to begin."
        val deepLink = "inspection/${inspection.id}"

        val notification = AppNotification(
            recipientOfficerId = inspection.assignedOfficerId,
            recipientOfficerName = inspection.assignedOfficerName,
            senderName = adminName,
            title = title,
            message = message,
            type = NotificationType.INSPECTION_ASSIGNED,
            inspectionId = inspection.id,
            inspectionNumber = inspection.inspectionNumber,
            deepLinkRoute = deepLink
        )

        // Save locally and sync
        repository.saveNotification(notification)

        // Dispatch Android system alert
        showSystemNotification(MyApplication.instance, notification)
    }

    /**
     * 2. When any officer is assigned with any compliance:
     * Officer should get notification with link.
     */
    fun notifyComplianceAssigned(compliance: Compliance, assignedBy: String = "Inspecting Officer") {
        if (compliance.assignedOfficerId.isBlank() && compliance.assignedOfficerName.isBlank()) {
            return
        }

        val pointName = compliance.inspectionPointTitle.ifBlank { compliance.title.ifBlank { "Deficiency Point" } }
        val title = "⚠️ Compliance Assigned: ${compliance.inspectionNumber}"
        val message = "$assignedBy has assigned you a compliance point for '$pointName' at ${compliance.locationName} (${compliance.severity.name} Priority). Tap to review and attend."
        val deepLink = "dashboard_compliances"

        val notification = AppNotification(
            recipientOfficerId = compliance.assignedOfficerId,
            recipientOfficerName = compliance.assignedOfficerName,
            senderName = assignedBy,
            title = title,
            message = message,
            type = NotificationType.COMPLIANCE_ASSIGNED,
            inspectionId = compliance.inspectionId,
            inspectionNumber = compliance.inspectionNumber,
            complianceId = compliance.id,
            deepLinkRoute = deepLink
        )

        repository.saveNotification(notification)
        showSystemNotification(MyApplication.instance, notification)
    }

    /**
     * 3. When any compliance by an officer is attended:
     * Reporting officer (and admins) should get notification.
     */
    fun notifyComplianceAttended(
        compliance: Compliance,
        attendedByOfficerName: String,
        remarks: String = ""
    ) {
        val pointName = compliance.inspectionPointTitle.ifBlank { compliance.title.ifBlank { "Observation Point" } }
        val statusText = compliance.status.name.replace("_", " ")
        val title = "✅ Compliance Attended: ${compliance.inspectionNumber}"
        val message = "Officer $attendedByOfficerName has attended compliance for '$pointName'. Status: $statusText. Remarks: \"${remarks.ifBlank { "Action Taken" }}\"."
        val deepLink = "dashboard_compliances"

        // Notify the inspecting officer who raised the deficiency
        val recipientId = compliance.inspectingOfficerId.ifBlank { "ADMIN" }
        val recipientName = compliance.inspectingOfficerName.ifBlank { "Inspecting Officer / Admin" }

        val notification = AppNotification(
            recipientOfficerId = recipientId,
            recipientOfficerName = recipientName,
            senderName = attendedByOfficerName,
            title = title,
            message = message,
            type = NotificationType.COMPLIANCE_ATTENDED,
            inspectionId = compliance.inspectionId,
            inspectionNumber = compliance.inspectionNumber,
            complianceId = compliance.id,
            deepLinkRoute = deepLink
        )

        repository.saveNotification(notification)
        showSystemNotification(MyApplication.instance, notification)
    }
}
