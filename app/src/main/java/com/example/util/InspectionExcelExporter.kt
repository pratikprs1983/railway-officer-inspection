package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.models.Compliance
import com.example.models.Inspection
import com.example.ui.dashboard.InspectionComplianceGroup
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object InspectionExcelExporter {

    private val displayDateFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("dd-MMM-yyyy HH:mm", Locale.getDefault())
    private val fileTimestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    /**
     * Exports the given inspections to an Excel-compatible CSV file with UTF-8 BOM.
     * Inspections are sorted date-wise (latest first or chronological).
     * Includes the number of compliances assigned to officers for each inspection.
     */
    fun exportInspectionsToExcel(
        context: Context,
        inspections: List<Inspection>,
        compliances: List<Compliance> = emptyList(),
        ascendingDate: Boolean = false
    ): File? {
        if (inspections.isEmpty()) {
            Toast.makeText(context, "No inspections to export", Toast.LENGTH_SHORT).show()
            return null
        }

        return try {
            // Sort inspections date-wise
            val sortedList = if (ascendingDate) {
                inspections.sortedWith(compareBy { getRelevantDate(it) })
            } else {
                inspections.sortedWith(compareByDescending { getRelevantDate(it) })
            }

            val fileName = "Inspections_Report_${fileTimestampFormat.format(Date())}.csv"
            val exportDir = File(context.cacheDir, "exports")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }
            val file = File(exportDir, fileName)

            FileOutputStream(file).use { fos ->
                // Write UTF-8 Byte Order Mark (BOM) so Excel opens UTF-8 encoded text correctly
                fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))

                OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                    // Headers
                    val headers = listOf(
                        "S.No.",
                        "Inspection Date",
                        "Inspection Number",
                        "Category",
                        "Inspection Type",
                        "Assigned Officer",
                        "Location",
                        "Status",
                        "No. of Compliances Assigned to Officer",
                        "Compliance Details & Action Officers",
                        "Scheduled Date",
                        "Execution Date",
                        "Started At",
                        "Completed At",
                        "GPS Latitude",
                        "GPS Longitude",
                        "GPS Accuracy (m)",
                        "Selfie Verified",
                        "Observations & Remarks",
                        "Checklist Responses Summary",
                        "Record Created At"
                    )
                    writer.write(headers.joinToString(",") { escapeCsvCell(it) })
                    writer.write("\r\n")

                    // Data Rows
                    sortedList.forEachIndexed { index, insp ->
                        val dateLong = getRelevantDate(insp)
                        val formattedDate = if (dateLong > 0) displayDateFormat.format(Date(dateLong)) else "N/A"
                        val scheduledStr = if (insp.scheduledDate > 0) dateTimeFormat.format(Date(insp.scheduledDate)) else "N/A"
                        val executionStr = if (insp.executionDate != null && insp.executionDate > 0) {
                            dateTimeFormat.format(Date(insp.executionDate))
                        } else "N/A"
                        val startedStr = if (insp.startedAt != null && insp.startedAt > 0) {
                            dateTimeFormat.format(Date(insp.startedAt))
                        } else "N/A"
                        val completedStr = if (insp.completedAt != null && insp.completedAt > 0) {
                            dateTimeFormat.format(Date(insp.completedAt))
                        } else "N/A"
                        val createdStr = if (insp.createdAt > 0) dateTimeFormat.format(Date(insp.createdAt)) else "N/A"

                        val categoryStr = when {
                            insp.isSpotInspection && insp.inspectionCategory == "SELF" -> "Self Inspection"
                            insp.isSpotInspection -> "Spot Inspection"
                            else -> "Assigned Inspection"
                        }

                        val matchingCompliances = compliances.filter { c ->
                            (c.inspectionId.isNotBlank() && c.inspectionId == insp.id) ||
                            (c.inspectionNumber.isNotBlank() && c.inspectionNumber.equals(insp.inspectionNumber, ignoreCase = true))
                        }
                        val compCountStr = matchingCompliances.size.toString()
                        val compDetailsStr = if (matchingCompliances.isNotEmpty()) {
                            matchingCompliances.joinToString("; ") { c ->
                                val officer = if (c.assignedOfficerName.isNotBlank()) " (Officer: ${c.assignedOfficerName})" else ""
                                "${c.title.ifBlank { c.inspectionPointTitle }} [${c.status.name}$officer]"
                            }
                        } else "0 (No compliances)"

                        val selfieVerifiedStr = if (insp.selfieURL.isNotBlank()) "YES (Verified)" else "NO"
                        val latStr = insp.startLatitude?.toString() ?: "N/A"
                        val lngStr = insp.startLongitude?.toString() ?: "N/A"
                        val accStr = insp.startAccuracy?.let { String.format(Locale.getDefault(), "%.1f m", it) } ?: "N/A"

                        val responsesSummary = formatResponsesSummary(insp.responses)

                        val row = listOf(
                            (index + 1).toString(),
                            formattedDate,
                            insp.inspectionNumber,
                            categoryStr,
                            insp.inspectionTypeName,
                            insp.assignedOfficerName.ifBlank { "Unassigned" },
                            insp.locationName.ifBlank { "TBD" },
                            insp.status.name.replace("_", " "),
                            compCountStr,
                            compDetailsStr,
                            scheduledStr,
                            executionStr,
                            startedStr,
                            completedStr,
                            latStr,
                            lngStr,
                            accStr,
                            selfieVerifiedStr,
                            insp.remarks,
                            responsesSummary,
                            createdStr
                        )

                        writer.write(row.joinToString(",") { escapeCsvCell(it) })
                        writer.write("\r\n")
                    }

                    writer.flush()
                }
            }

            file
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to export Excel: ${e.message}", Toast.LENGTH_LONG).show()
            null
        }
    }

    /**
     * Shares or opens the exported file using Android system Chooser.
     */
    fun shareExcelFile(context: Context, file: File, title: String = "Inspection Details (Date-Wise)") {
        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, "Attached is the Railway Inspection Report generated date-wise.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Export / Share Excel Report")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Could not open share dialog: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Directly attempts to open the Excel file in Microsoft Excel or Google Sheets.
     */
    fun openExcelFile(context: Context, file: File) {
        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "text/csv")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(viewIntent, "Open with Excel / Sheets"))
        } catch (e: Exception) {
            e.printStackTrace()
            // Fall back to share
            shareExcelFile(context, file)
        }
    }

    /**
     * Shows system chooser to share or open the Excel report.
     */
    fun openOrShareExcelFile(context: Context, file: File) {
        shareExcelFile(context, file)
    }

    /**
     * Exports compliances belonging to a single inspection compliance group to an Excel CSV document.
     */
    fun exportSingleInspectionComplianceToExcel(
        context: Context,
        group: InspectionComplianceGroup
    ): File? {
        val sanitizedNumber = group.inspectionNumber.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        return exportCompliancesToExcel(
            context = context,
            groups = listOf(group),
            reportTitle = "Compliance_Register_${sanitizedNumber}"
        )
    }

    /**
     * Exports a list of InspectionComplianceGroups to an Excel-compatible CSV file with UTF-8 BOM.
     * Includes all inspection details, compliance points, severity, target dates, and statuses.
     */
    fun exportCompliancesToExcel(
        context: Context,
        groups: List<InspectionComplianceGroup>,
        reportTitle: String = "Inspection_Compliances_Report"
    ): File? {
        if (groups.isEmpty()) {
            Toast.makeText(context, "No compliances to export", Toast.LENGTH_SHORT).show()
            return null
        }

        return try {
            val fileName = "${reportTitle.replace("[^a-zA-Z0-9_-]".toRegex(), "_")}_${fileTimestampFormat.format(Date())}.csv"
            val exportDir = File(context.cacheDir, "exports")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }
            val file = File(exportDir, fileName)

            FileOutputStream(file).use { fos ->
                // Write UTF-8 BOM for Microsoft Excel / Google Sheets compatibility
                fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))

                OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                    val headers = listOf(
                        "S.No.",
                        "Inspection Number",
                        "Category",
                        "Inspection Type",
                        "Location / Station",
                        "Inspecting Officer",
                        "Inspection Date",
                        "Inspection Status",
                        "Point #",
                        "Checklist / Action Point",
                        "Non-Compliance Observation / Deficiency",
                        "Severity",
                        "Target Date",
                        "Current Compliance Status",
                        "Assigned Compliance Officer",
                        "Action Taken / Remarks",
                        "Closed / Resolved Date"
                    )
                    writer.write(headers.joinToString(",") { escapeCsvCell(it) })
                    writer.write("\r\n")

                    var serialNo = 1
                    groups.forEach { group ->
                        val parent = group.parentInspection
                        val categoryStr = if (group.isSpotInspection) "Spot / Self" else "Assigned"
                        val inspDateStr = if (group.inspectionDateMillis > 0)
                            dateTimeFormat.format(Date(group.inspectionDateMillis)) else "Unscheduled"
                        val inspStatusStr = parent?.status?.name?.replace("_", " ") ?: "COMPLETED"

                        if (group.compliances.isEmpty()) {
                            val row = listOf(
                                serialNo++.toString(),
                                group.inspectionNumber,
                                categoryStr,
                                group.inspectionTypeName,
                                group.locationName,
                                group.inspectingOfficerName,
                                inspDateStr,
                                inspStatusStr,
                                "-",
                                "No Compliances Logged",
                                "-",
                                "-",
                                "-",
                                "-",
                                "-",
                                "-",
                                "-"
                            )
                            writer.write(row.joinToString(",") { escapeCsvCell(it) })
                            writer.write("\r\n")
                        } else {
                            group.compliances.forEachIndexed { idx, comp ->
                                val targetStr = if (comp.targetDate != null && comp.targetDate!! > 0)
                                    displayDateFormat.format(Date(comp.targetDate!!)) else "No deadline"
                                val closedStr = if (comp.closedAt != null && comp.closedAt!! > 0)
                                    displayDateFormat.format(Date(comp.closedAt!!)) else "-"

                                val row = listOf(
                                    serialNo++.toString(),
                                    group.inspectionNumber,
                                    categoryStr,
                                    group.inspectionTypeName,
                                    group.locationName,
                                    group.inspectingOfficerName,
                                    inspDateStr,
                                    inspStatusStr,
                                    "Point ${idx + 1}",
                                    comp.inspectionPointTitle.ifBlank { "Observation Point ${idx + 1}" },
                                    comp.description.ifBlank { "Deficiency noted during inspection." },
                                    comp.severity.name,
                                    targetStr,
                                    comp.status.name.replace("_", " "),
                                    comp.assignedOfficerName.ifBlank { "Unassigned" },
                                    comp.complianceRemarks.ifBlank { comp.actionTaken.ifBlank { "Pending action" } },
                                    closedStr
                                )
                                writer.write(row.joinToString(",") { escapeCsvCell(it) })
                                writer.write("\r\n")
                            }
                        }
                    }
                }
            }

            file
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to export Excel: ${e.message}", Toast.LENGTH_LONG).show()
            null
        }
    }

    private fun getRelevantDate(inspection: Inspection): Long {
        return if (inspection.scheduledDate > 0) {
            inspection.scheduledDate
        } else if (inspection.executionDate != null && inspection.executionDate > 0) {
            inspection.executionDate
        } else {
            inspection.createdAt
        }
    }

    private fun escapeCsvCell(text: String): String {
        var clean = text.replace("\r", " ").replace("\n", " ")
        if (clean.contains(",") || clean.contains("\"") || clean.contains(";")) {
            clean = "\"" + clean.replace("\"", "\"\"") + "\""
        }
        return clean
    }

    private fun formatResponsesSummary(responsesJson: String): String {
        if (responsesJson.isBlank() || responsesJson == "{}") return "None"
        return try {
            val json = JSONObject(responsesJson)
            val keys = json.keys()
            val list = mutableListOf<String>()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = json.optString(key, "")
                if (value.isNotBlank() && !value.startsWith("content://") && !value.startsWith("file://")) {
                    list.add("$key: $value")
                }
            }
            if (list.isEmpty()) "Checklist answers recorded" else list.joinToString("; ")
        } catch (e: Exception) {
            "Checklist answers recorded"
        }
    }
}
