package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.models.Inspection
import com.example.models.InspectionStatus
import com.example.ui.common.DateFilterType
import com.example.ui.common.InspectionFilterCriteria
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Utility to generate authentic Microsoft Word (.docx) documents
 * for Filtered Inspections in rich detail.
 *
 * Uses standard Office Open XML (ISO/IEC 29500) packaged in a ZIP archive,
 * fully compatible with Microsoft Word, Google Docs, WPS Office, and LibreOffice.
 */
object InspectionWordExporter {

    private val displayDateFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("dd-MMM-yyyy HH:mm", Locale.getDefault())
    private val fileTimestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    /**
     * Exports the given filtered inspections to an authentic Word (.docx) document.
     */
    fun exportFilteredInspectionsToWord(
        context: Context,
        inspections: List<Inspection>,
        criteria: InspectionFilterCriteria? = null,
        ascendingDate: Boolean = false
    ): File? {
        if (inspections.isEmpty()) {
            Toast.makeText(context, "No inspections to export", Toast.LENGTH_SHORT).show()
            return null
        }

        return try {
            val exportDir = File(context.cacheDir, "exports")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            val sortedList = if (ascendingDate) {
                inspections.sortedWith(compareBy { getRelevantDate(it) })
            } else {
                inspections.sortedWith(compareByDescending { getRelevantDate(it) })
            }

            val fileName = "Inspections_Report_${fileTimestampFormat.format(Date())}.docx"
            val file = File(exportDir, fileName)

            val documentXml = buildFilteredInspectionsDocXml(sortedList, criteria)
            writeDocxPackage(file, documentXml)

            file
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to generate Word document: ${e.message}", Toast.LENGTH_LONG).show()
            null
        }
    }

    /**
     * Shares or opens the exported Word file using Android system Chooser.
     */
    fun shareWordFile(
        context: Context,
        file: File,
        title: String = "Inspections Report (Word Document)"
    ) {
        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, "Please find attached the detailed Railway Inspections Report in Microsoft Word (.docx) format.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share or Save Word Document")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Cannot open Word file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun openOrShareWordFile(context: Context, file: File, title: String = "Filtered Inspections Dossier") {
        shareWordFile(context, file, title)
    }

    // =========================================================================
    // Office Open XML (.docx) Package Writer
    // =========================================================================

    private fun writeDocxPackage(outputFile: File, documentXmlContent: String) {
        val contentTypesXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
              <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
            </Types>
        """.trimIndent()

        val packageRelsXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
            </Relationships>
        """.trimIndent()

        val documentRelsXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
            </Relationships>
        """.trimIndent()

        val stylesXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:docDefaults>
                <w:rPrDefault>
                  <w:rPr>
                    <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:cs="Calibri"/>
                    <w:sz w:val="22"/>
                    <w:szCs w:val="22"/>
                    <w:color w:val="1E293B"/>
                  </w:rPr>
                </w:rPrDefault>
                <w:pPrDefault>
                  <w:pPr>
                    <w:spacing w:after="120" w:line="240" w:lineRule="auto"/>
                  </w:pPr>
                </w:pPrDefault>
              </w:docDefaults>
            </w:styles>
        """.trimIndent()

        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            // [Content_Types].xml
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            zos.write(contentTypesXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // _rels/.rels
            zos.putNextEntry(ZipEntry("_rels/.rels"))
            zos.write(packageRelsXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // word/_rels/document.xml.rels
            zos.putNextEntry(ZipEntry("word/_rels/document.xml.rels"))
            zos.write(documentRelsXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // word/styles.xml
            zos.putNextEntry(ZipEntry("word/styles.xml"))
            zos.write(stylesXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // word/document.xml
            zos.putNextEntry(ZipEntry("word/document.xml"))
            zos.write(documentXmlContent.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
    }

    // =========================================================================
    // Word Document XML Content Builder
    // =========================================================================

    private fun buildFilteredInspectionsDocXml(
        inspections: List<Inspection>,
        criteria: InspectionFilterCriteria?
    ): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""")
        sb.append("<w:body>")

        val nowStr = dateTimeFormat.format(Date())

        // 1. Official Header
        sb.append("""
            <w:p>
              <w:pPr>
                <w:jc w:val="center"/>
                <w:spacing w:before="120" w:after="40"/>
              </w:pPr>
              <w:r>
                <w:rPr>
                  <w:b/>
                  <w:sz w:val="32"/>
                  <w:color w:val="1E3A8A"/>
                </w:rPr>
                <w:t>INDIAN RAILWAYS / ROI-CMS</w:t>
              </w:r>
            </w:p>
            <w:p>
              <w:pPr>
                <w:jc w:val="center"/>
                <w:spacing w:after="80"/>
              </w:pPr>
              <w:r>
                <w:rPr>
                  <w:b/>
                  <w:sz w:val="24"/>
                  <w:color w:val="0F172A"/>
                </w:rPr>
                <w:t>FILTERED INSPECTIONS DOSSIER &amp; COMPREHENSIVE REPORT</w:t>
              </w:r>
            </w:p>
            <w:p>
              <w:pPr>
                <w:jc w:val="center"/>
                <w:spacing w:after="240"/>
              </w:pPr>
              <w:r>
                <w:rPr>
                  <w:i/>
                  <w:sz w:val="18"/>
                  <w:color w:val="64748B"/>
                </w:rPr>
                <w:t>Generated on: $nowStr | Total Inspections in Dossier: ${inspections.size}</w:t>
              </w:r>
            </w:p>
        """.trimIndent())

        // 2. Active Filter Criteria Box
        val filterRows = mutableListOf<List<String>>()
        filterRows.add(listOf("Report Scope", "Filtered Inspection Records Export"))
        filterRows.add(listOf("Total Records", "${inspections.size} Inspection(s)"))

        if (criteria != null) {
            filterRows.add(listOf("Inspecting Officer", criteria.officerName ?: "All Officers"))
            filterRows.add(listOf("Inspection Type", criteria.inspectionTypeName ?: "All Types"))
            val dateLabel = when (criteria.dateFilterType) {
                DateFilterType.ALL -> "All Dates"
                DateFilterType.TODAY -> "Today Only"
                DateFilterType.LAST_7_DAYS -> "Last 7 Days"
                DateFilterType.THIS_MONTH -> "This Month"
                DateFilterType.CUSTOM_DATE -> if (criteria.customDateMillis != null) displayDateFormat.format(Date(criteria.customDateMillis!!)) else "Custom Date"
            }
            filterRows.add(listOf("Date Filter", dateLabel))
            filterRows.add(listOf("Status Filter", criteria.status?.name ?: "All Statuses"))
            filterRows.add(listOf("Category Filter", criteria.category ?: "All Categories (Assigned, Spot, Self)"))
            if (criteria.searchQuery.isNotBlank()) {
                filterRows.add(listOf("Search Query", criteria.searchQuery))
            }
        }

        appendSectionHeading(sb, "1. FILTER CRITERIA & EXECUTIVE SUMMARY", "1E3A8A")
        appendKeyValueTable(sb, filterRows, "1E3A8A")

        // 3. Status Breakdown Cards / Metrics
        val completedCount = inspections.count { it.status == InspectionStatus.COMPLETED }
        val inProgressCount = inspections.count { it.status == InspectionStatus.IN_PROGRESS }
        val assignedCount = inspections.count { it.status == InspectionStatus.ASSIGNED }
        val spotCount = inspections.count { it.isSpotInspection && it.inspectionCategory == "SPOT" }
        val selfCount = inspections.count { it.isSpotInspection && it.inspectionCategory == "SELF" }

        val kpiRows = listOf(
            listOf("Completed Inspections", "$completedCount (${if (inspections.isNotEmpty()) (completedCount * 100 / inspections.size) else 0}%)"),
            listOf("In Progress Inspections", "$inProgressCount"),
            listOf("Assigned / Pending Inspections", "$assignedCount"),
            listOf("Spot / Self Initiated Inspections", "${spotCount + selfCount} (Spot: $spotCount, Self: $selfCount)")
        )
        sb.append("<w:p><w:pPr><w:spacing w:before=\"160\" w:after=\"80\"/></w:pPr></w:p>")
        appendKeyValueTable(sb, kpiRows, "0369A1")

        // 4. Master Table of Filtered Inspections
        sb.append("<w:p><w:pPr><w:spacing w:before=\"280\" w:after=\"80\"/></w:pPr></w:p>")
        appendSectionHeading(sb, "2. FILTERED INSPECTIONS MASTER TABLE", "1E3A8A")

        appendMasterTable(sb, inspections)

        // 5. Detailed Inspection-Wise Findings
        sb.append("<w:p><w:pPr><w:spacing w:before=\"320\" w:after=\"80\"/></w:pPr></w:p>")
        appendSectionHeading(sb, "3. DETAILED INSPECTION-WISE OBSERVATIONS & RESPONSES", "1E3A8A")

        inspections.forEachIndexed { index, insp ->
            appendInspectionDetailBlock(sb, index + 1, insp)
        }

        // 6. Sign-off / Verification Block
        appendSignatureBlock(sb)

        sb.append("</w:body>")
        sb.append("</w:document>")
        return sb.toString()
    }

    private fun appendSectionHeading(sb: StringBuilder, title: String, colorHex: String) {
        sb.append("""
            <w:p>
              <w:pPr>
                <w:spacing w:before="240" w:after="100"/>
              </w:pPr>
              <w:r>
                <w:rPr>
                  <w:b/>
                  <w:sz w:val="22"/>
                  <w:color w:val="$colorHex"/>
                </w:rPr>
                <w:t>${escapeXml(title)}</w:t>
              </w:r>
            </w:p>
        """.trimIndent())
    }

    private fun appendMasterTable(sb: StringBuilder, inspections: List<Inspection>) {
        sb.append("""
            <w:tbl>
              <w:tblPr>
                <w:tblW w:w="9638" w:type="dxa"/>
                <w:jc w:val="center"/>
                <w:tblBorders>
                  <w:top w:val="single" w:sz="6" w:space="0" w:color="CBD5E1"/>
                  <w:left w:val="single" w:sz="6" w:space="0" w:color="CBD5E1"/>
                  <w:bottom w:val="single" w:sz="6" w:space="0" w:color="CBD5E1"/>
                  <w:right w:val="single" w:sz="6" w:space="0" w:color="CBD5E1"/>
                  <w:insideH w:val="single" w:sz="4" w:space="0" w:color="E2E8F0"/>
                  <w:insideV w:val="single" w:sz="4" w:space="0" w:color="E2E8F0"/>
                </w:tblBorders>
                <w:tblCellMar>
                  <w:top w:w="100" w:type="dxa"/>
                  <w:left w:w="120" w:type="dxa"/>
                  <w:bottom w:w="100" w:type="dxa"/>
                  <w:right w:w="120" w:type="dxa"/>
                </w:tblCellMar>
              </w:tblPr>
              <w:tr>
                <w:trPr><w:tblHeader/></w:trPr>
                <w:tc>
                  <w:tcPr><w:tcW w:w="600" w:type="dxa"/><w:shd w:val="clear" w:color="auto" w:fill="1E3A8A"/></w:tcPr>
                  <w:p><w:r><w:rPr><w:b/><w:sz w:val="18"/><w:color w:val="FFFFFF"/></w:rPr><w:t>S.No</w:t></w:r></w:p>
                </w:tc>
                <w:tc>
                  <w:tcPr><w:tcW w:w="1600" w:type="dxa"/><w:shd w:val="clear" w:color="auto" w:fill="1E3A8A"/></w:tcPr>
                  <w:p><w:r><w:rPr><w:b/><w:sz w:val="18"/><w:color w:val="FFFFFF"/></w:rPr><w:t>Inspection #</w:t></w:r></w:p>
                </w:tc>
                <w:tc>
                  <w:tcPr><w:tcW w:w="1400" w:type="dxa"/><w:shd w:val="clear" w:color="auto" w:fill="1E3A8A"/></w:tcPr>
                  <w:p><w:r><w:rPr><w:b/><w:sz w:val="18"/><w:color w:val="FFFFFF"/></w:rPr><w:t>Date</w:t></w:r></w:p>
                </w:tc>
                <w:tc>
                  <w:tcPr><w:tcW w:w="2200" w:type="dxa"/><w:shd w:val="clear" w:color="auto" w:fill="1E3A8A"/></w:tcPr>
                  <w:p><w:r><w:rPr><w:b/><w:sz w:val="18"/><w:color w:val="FFFFFF"/></w:rPr><w:t>Type &amp; Category</w:t></w:r></w:p>
                </w:tc>
                <w:tc>
                  <w:tcPr><w:tcW w:w="2200" w:type="dxa"/><w:shd w:val="clear" w:color="auto" w:fill="1E3A8A"/></w:tcPr>
                  <w:p><w:r><w:rPr><w:b/><w:sz w:val="18"/><w:color w:val="FFFFFF"/></w:rPr><w:t>Officer &amp; Location</w:t></w:r></w:p>
                </w:tc>
                <w:tc>
                  <w:tcPr><w:tcW w:w="1638" w:type="dxa"/><w:shd w:val="clear" w:color="auto" w:fill="1E3A8A"/></w:tcPr>
                  <w:p><w:r><w:rPr><w:b/><w:sz w:val="18"/><w:color w:val="FFFFFF"/></w:rPr><w:t>Status</w:t></w:r></w:p>
                </w:tc>
              </w:tr>
        """.trimIndent())

        inspections.forEachIndexed { index, insp ->
            val bg = if (index % 2 == 0) "FFFFFF" else "F8FAFC"
            val dateVal = getRelevantDate(insp)
            val dateStr = if (dateVal > 0) displayDateFormat.format(Date(dateVal)) else "N/A"
            val categoryStr = when {
                insp.isSpotInspection && insp.inspectionCategory == "SELF" -> "Self"
                insp.isSpotInspection -> "Spot"
                else -> "Assigned"
            }
            val statusColor = when (insp.status) {
                InspectionStatus.COMPLETED -> "15803D"
                InspectionStatus.IN_PROGRESS -> "B45309"
                InspectionStatus.ASSIGNED -> "1D4ED8"
                else -> "64748B"
            }

            sb.append("""
              <w:tr>
                <w:tc>
                  <w:tcPr><w:tcW w:w="600" w:type="dxa"/><w:shd w:val="clear" w:color="auto" w:fill="$bg"/></w:tcPr>
                  <w:p><w:r><w:rPr><w:sz w:val="18"/></w:rPr><w:t>${index + 1}</w:t></w:r></w:p>
                </w:tc>
                <w:tc>
                  <w:tcPr><w:tcW w:w="1600" w:type="dxa"/><w:shd w:val="clear" w:color="auto" w:fill="$bg"/></w:tcPr>
                  <w:p><w:r><w:rPr><w:b/><w:sz w:val="18"/><w:color w:val="0F172A"/></w:rPr><w:t xml:space="preserve">${escapeXml(insp.inspectionNumber.ifBlank { "INSP-${insp.id.take(6)}" })}</w:t></w:r></w:p>
                </w:tc>
                <w:tc>
                  <w:tcPr><w:tcW w:w="1400" w:type="dxa"/><w:shd w:val="clear" w:color="auto" w:fill="$bg"/></w:tcPr>
                  <w:p><w:r><w:rPr><w:sz w:val="18"/></w:rPr><w:t>$dateStr</w:t></w:r></w:p>
                </w:tc>
                <w:tc>
                  <w:tcPr><w:tcW w:w="2200" w:type="dxa"/><w:shd w:val="clear" w:color="auto" w:fill="$bg"/></w:tcPr>
                  <w:p><w:r><w:rPr><w:b/><w:sz w:val="18"/></w:rPr><w:t xml:space="preserve">${escapeXml(insp.inspectionTypeName.ifBlank { "Standard Inspection" })}</w:t></w:r></w:p>
                  <w:p><w:r><w:rPr><w:sz w:val="16"/><w:color w:val="64748B"/></w:rPr><w:t xml:space="preserve">Mode: $categoryStr</w:t></w:r></w:p>
                </w:tc>
                <w:tc>
                  <w:tcPr><w:tcW w:w="2200" w:type="dxa"/><w:shd w:val="clear" w:color="auto" w:fill="$bg"/></w:tcPr>
                  <w:p><w:r><w:rPr><w:sz w:val="18"/></w:rPr><w:t xml:space="preserve">${escapeXml(insp.assignedOfficerName.ifBlank { "Unassigned" })}</w:t></w:r></w:p>
                  <w:p><w:r><w:rPr><w:sz w:val="16"/><w:color w:val="64748B"/></w:rPr><w:t xml:space="preserve">Loc: ${escapeXml(insp.locationName.ifBlank { "Field Track" })}</w:t></w:r></w:p>
                </w:tc>
                <w:tc>
                  <w:tcPr><w:tcW w:w="1638" w:type="dxa"/><w:shd w:val="clear" w:color="auto" w:fill="$bg"/></w:tcPr>
                  <w:p><w:r><w:rPr><w:b/><w:sz w:val="18"/><w:color w:val="$statusColor"/></w:rPr><w:t>${insp.status.name}</w:t></w:r></w:p>
                </w:tc>
              </w:tr>
            """.trimIndent())
        }

        sb.append("</w:tbl>")
    }

    private fun appendInspectionDetailBlock(sb: StringBuilder, index: Int, insp: Inspection) {
        val numberStr = insp.inspectionNumber.ifBlank { "INSP-${insp.id.take(6)}" }
        val categoryStr = when {
            insp.isSpotInspection && insp.inspectionCategory == "SELF" -> "Self Inspection"
            insp.isSpotInspection -> "Spot Inspection"
            else -> "Scheduled Inspection"
        }

        sb.append("""
            <w:p>
              <w:pPr>
                <w:spacing w:before="240" w:after="80"/>
              </w:pPr>
              <w:r>
                <w:rPr>
                  <w:b/>
                  <w:sz w:val="22"/>
                  <w:color w:val="0369A1"/>
                </w:rPr>
                <w:t xml:space="preserve">Inspection #$index: $numberStr - ${escapeXml(insp.inspectionTypeName)} ($categoryStr)</w:t>
              </w:r>
            </w:p>
        """.trimIndent())

        val scheduledStr = if (insp.scheduledDate > 0) dateTimeFormat.format(Date(insp.scheduledDate)) else "N/A"
        val executionStr = if (insp.executionDate != null && insp.executionDate > 0) dateTimeFormat.format(Date(insp.executionDate)) else "Pending"
        val gpsStr = if (insp.startLatitude != null && insp.startLongitude != null) {
            String.format(Locale.US, "%.5f, %.5f (Accuracy: %.1fm)", insp.startLatitude, insp.startLongitude, insp.startAccuracy ?: 0f)
        } else "Not Captured"

        val metadataRows = listOf(
            listOf("Inspection Number", numberStr),
            listOf("Inspection Type", insp.inspectionTypeName.ifBlank { "General Inspection" }),
            listOf("Category / Mode", categoryStr),
            listOf("Assigned Officer", insp.assignedOfficerName.ifBlank { "Unassigned" }),
            listOf("Inspection Location", insp.locationName.ifBlank { "Railway Section" }),
            listOf("Status", insp.status.name),
            listOf("Scheduled Date & Time", scheduledStr),
            listOf("Execution Date & Time", executionStr),
            listOf("GPS Coordinates", gpsStr),
            listOf("Remarks / Notes", insp.remarks.ifBlank { "None recorded" })
        )

        appendKeyValueTable(sb, metadataRows, "0284C7")

        // Checklist Responses Table (if available)
        val responses = parseChecklistResponses(insp.responses)
        if (responses.isNotEmpty()) {
            sb.append("""
                <w:p>
                  <w:pPr>
                    <w:spacing w:before="120" w:after="60"/>
                  </w:pPr>
                  <w:r>
                    <w:rPr>
                      <w:b/>
                      <w:sz w:val="18"/>
                      <w:color w:val="475569"/>
                    </w:rPr>
                    <w:t>Recorded Checklist Findings &amp; Responses:</w:t>
                  </w:r>
                </w:p>
            """.trimIndent())
            appendKeyValueTable(sb, responses, "475569")
        }

        sb.append("""<w:p><w:pPr><w:spacing w:after="160"/></w:pPr></w:p>""")
    }

    private fun appendKeyValueTable(
        sb: StringBuilder,
        rows: List<List<String>>,
        headerBg: String = "1E3A8A"
    ) {
        sb.append("""
            <w:tbl>
              <w:tblPr>
                <w:tblW w:w="9638" w:type="dxa"/>
                <w:jc w:val="center"/>
                <w:tblBorders>
                  <w:top w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/>
                  <w:left w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/>
                  <w:bottom w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/>
                  <w:right w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/>
                  <w:insideH w:val="single" w:sz="4" w:space="0" w:color="E2E8F0"/>
                  <w:insideV w:val="single" w:sz="4" w:space="0" w:color="E2E8F0"/>
                </w:tblBorders>
                <w:tblCellMar>
                  <w:top w:w="90" w:type="dxa"/>
                  <w:left w:w="120" w:type="dxa"/>
                  <w:bottom w:w="90" w:type="dxa"/>
                  <w:right w:w="120" w:type="dxa"/>
                </w:tblCellMar>
              </w:tblPr>
        """.trimIndent())

        rows.forEachIndexed { index, pair ->
            val key = pair.getOrNull(0) ?: ""
            val value = pair.getOrNull(1) ?: ""
            val bg = if (index % 2 == 0) "FFFFFF" else "F8FAFC"

            sb.append("""
              <w:tr>
                <w:trPr><w:cantSplit/></w:trPr>
                <w:tc>
                  <w:tcPr><w:tcW w:w="3200" w:type="dxa"/><w:shd w:val="clear" w:color="auto" w:fill="F1F5F9"/></w:tcPr>
                  <w:p><w:r><w:rPr><w:b/><w:sz w:val="18"/><w:color w:val="334155"/></w:rPr><w:t xml:space="preserve">${escapeXml(key)}</w:t></w:r></w:p>
                </w:tc>
                <w:tc>
                  <w:tcPr><w:tcW w:w="6438" w:type="dxa"/><w:shd w:val="clear" w:color="auto" w:fill="$bg"/></w:tcPr>
                  <w:p><w:r><w:rPr><w:sz w:val="18"/><w:color w:val="0F172A"/></w:rPr><w:t xml:space="preserve">${escapeXml(value)}</w:t></w:r></w:p>
                </w:tc>
              </w:tr>
            """.trimIndent())
        }

        sb.append("</w:tbl>")
    }

    private fun appendSignatureBlock(sb: StringBuilder) {
        sb.append("""
            <w:p><w:pPr><w:spacing w:before="360" w:after="120"/></w:pPr></w:p>
            <w:tbl>
              <w:tblPr>
                <w:tblW w:w="9638" w:type="dxa"/>
                <w:jc w:val="center"/>
                <w:tblBorders>
                  <w:top w:val="single" w:sz="6" w:space="0" w:color="CBD5E1"/>
                  <w:left w:val="single" w:sz="6" w:space="0" w:color="CBD5E1"/>
                  <w:bottom w:val="single" w:sz="6" w:space="0" w:color="CBD5E1"/>
                  <w:right w:val="single" w:sz="6" w:space="0" w:color="CBD5E1"/>
                  <w:insideH w:val="none"/>
                  <w:insideV w:val="single" w:sz="6" w:space="0" w:color="CBD5E1"/>
                </w:tblBorders>
                <w:tblCellMar>
                  <w:top w:w="160" w:type="dxa"/>
                  <w:left w:w="160" w:type="dxa"/>
                  <w:bottom w:w="160" w:type="dxa"/>
                  <w:right w:w="160" w:type="dxa"/>
                </w:tblCellMar>
              </w:tblPr>
              <w:tr>
                <w:tc>
                  <w:tcPr><w:tcW w:w="4819" w:type="dxa"/></w:tcPr>
                  <w:p><w:r><w:rPr><w:b/><w:sz w:val="18"/><w:color w:val="1E293B"/></w:rPr><w:t>PREPARED / INSPECTED BY:</w:t></w:r></w:p>
                  <w:p><w:pPr><w:spacing w:before="360" w:after="40"/></w:pPr><w:r><w:t>___________________________________________</w:t></w:r></w:p>
                  <w:p><w:r><w:rPr><w:sz w:val="18"/><w:color w:val="475569"/></w:rPr><w:t>Field Inspecting Officer Signature</w:t></w:r></w:p>
                  <w:p><w:r><w:rPr><w:sz w:val="18"/><w:color w:val="475569"/></w:rPr><w:t>Date: ____________________________________</w:t></w:r></w:p>
                </w:tc>
                <w:tc>
                  <w:tcPr><w:tcW w:w="4819" w:type="dxa"/></w:tcPr>
                  <w:p><w:r><w:rPr><w:b/><w:sz w:val="18"/><w:color w:val="1E293B"/></w:rPr><w:t>CONTROLLING / DIVISIONAL AUTHORITY:</w:t></w:r></w:p>
                  <w:p><w:pPr><w:spacing w:before="360" w:after="40"/></w:pPr><w:r><w:t>___________________________________________</w:t></w:r></w:p>
                  <w:p><w:r><w:rPr><w:sz w:val="18"/><w:color w:val="475569"/></w:rPr><w:t>Divisional Railway Officer Signature</w:t></w:r></w:p>
                  <w:p><w:r><w:rPr><w:sz w:val="18"/><w:color w:val="475569"/></w:rPr><w:t>Date: ____________________________________</w:t></w:r></w:p>
                </w:tc>
              </w:tr>
            </w:tbl>
        """.trimIndent())
    }

    private fun parseChecklistResponses(responsesJson: String): List<List<String>> {
        val list = mutableListOf<List<String>>()
        if (responsesJson.isBlank() || responsesJson == "{}") return list

        try {
            val json = JSONObject(responsesJson)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key == "__custom_fields__" || key == "__attachments__") continue

                val value = json.optString(key, "")
                if (value.startsWith("content://") || value.startsWith("file://")) {
                    list.add(listOf(formatFieldName(key), "[Photo / Media Attached]"))
                } else if (value.isNotBlank()) {
                    list.add(listOf(formatFieldName(key), value))
                }
            }

            if (json.has("__custom_fields__")) {
                val customArr = json.getJSONArray("__custom_fields__")
                for (i in 0 until customArr.length()) {
                    val obj = customArr.getJSONObject(i)
                    val label = obj.optString("label", "Custom Point")
                    val fieldName = obj.optString("fieldName", "")
                    val value = json.optString(fieldName, "").ifBlank {
                        obj.optString("defaultValue", "")
                    }
                    if (value.isNotBlank()) {
                        list.add(listOf(label, value))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun formatFieldName(key: String): String {
        return key.replace("_", " ")
            .split(" ")
            .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } }
    }

    private fun getRelevantDate(insp: Inspection): Long {
        return when {
            insp.executionDate != null && insp.executionDate > 0 -> insp.executionDate
            insp.completedAt != null && insp.completedAt > 0 -> insp.completedAt
            insp.scheduledDate > 0 -> insp.scheduledDate
            else -> insp.createdAt
        }
    }

    private fun escapeXml(text: String?): String {
        if (text == null) return ""
        val sb = StringBuilder()
        for (ch in text) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> {
                    if (ch.code in 0x20..0xD7FF || ch == '\t' || ch == '\n' || ch == '\r') {
                        sb.append(ch)
                    }
                }
            }
        }
        return sb.toString()
    }
}
