package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.models.Compliance
import com.example.models.ComplianceSeverity
import com.example.models.ComplianceStatus
import com.example.models.Inspection
import com.example.ui.dashboard.InspectionComplianceGroup
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
 * for Inspection-Wise Compliances in rich detail.
 *
 * Uses standard Office Open XML (ISO/IEC 29500) packaged in a ZIP archive,
 * fully compatible with Microsoft Word, Google Docs, WPS Office, and LibreOffice.
 */
object ComplianceWordExporter {

    private val displayDateFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("dd-MMM-yyyy HH:mm", Locale.getDefault())
    private val fileTimestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    /**
     * Exports a detailed Word (.docx) document for a single inspection's compliance group.
     */
    fun exportInspectionComplianceToWord(
        context: Context,
        group: InspectionComplianceGroup
    ): File? {
        return try {
            val exportDir = File(context.cacheDir, "exports")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            val sanitizedNumber = group.inspectionNumber.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
            val fileName = "Compliance_Report_${sanitizedNumber}_${fileTimestampFormat.format(Date())}.docx"
            val file = File(exportDir, fileName)

            val documentXml = buildSingleInspectionDocXml(group)
            writeDocxPackage(file, documentXml)

            file
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to generate Word document: ${e.message}", Toast.LENGTH_LONG).show()
            null
        }
    }

    /**
     * Exports a comprehensive Word (.docx) document containing ALL inspection-wise compliance groups.
     */
    fun exportAllCompliancesToWord(
        context: Context,
        groups: List<InspectionComplianceGroup>,
        officerName: String = "Compliance Officer"
    ): File? {
        if (groups.isEmpty()) {
            Toast.makeText(context, "No compliances to export", Toast.LENGTH_SHORT).show()
            return null
        }

        return try {
            val exportDir = File(context.cacheDir, "exports")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            val fileName = "All_Compliances_Dossier_${fileTimestampFormat.format(Date())}.docx"
            val file = File(exportDir, fileName)

            val documentXml = buildAllCompliancesDocXml(groups, officerName)
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
        title: String = "Inspection Compliance Report (Word Document)"
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
                putExtra(Intent.EXTRA_TEXT, "Please find attached the detailed Railway Inspection Compliance Dossier in Microsoft Word (.docx) format.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share or Save Word Document")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Could not open share dialog: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Attempts to open the Word document directly in Microsoft Word / Google Docs,
     * falling back to the share chooser.
     */
    fun openWordFile(context: Context, file: File) {
        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(viewIntent, "Open with Word / Docs")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            shareWordFile(context, file)
        }
    }

    /**
     * Open or share Word file (convenience wrapper).
     */
    fun openOrShareWordFile(context: Context, file: File, title: String = "Compliance Detailed Report") {
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
    // Word Document Body Builder (Single Inspection)
    // =========================================================================

    private fun buildSingleInspectionDocXml(group: InspectionComplianceGroup): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""")
        sb.append("<w:body>")

        // 1. Official Header Banner
        appendHeaderBanner(sb, "INSPECTION COMPLIANCE DOSSIER", "OFFICIAL RAILWAY SAFETY & COMPLIANCE REPORT")

        // 2. Inspection Executive Overview Table
        appendInspectionOverviewSection(sb, group)

        // 3. Compliance Statistics Summary Box
        appendComplianceSummaryBox(sb, group.compliances)

        // 4. Detailed Compliance Points Table
        appendCompliancesTableSection(sb, group.compliances)

        // 5. Individual Compliance Action Narrative Cards
        appendIndividualComplianceNarrativeCards(sb, group.compliances)

        // 6. Parent Inspection Checklist & Field Observations (if available)
        group.parentInspection?.let { parent ->
            appendChecklistResponsesSection(sb, parent)
        }

        // 7. Official Sign-off & Verification Block
        appendSignOffBlock(sb, group.inspectingOfficerName)

        // Section Properties (A4 Portrait, 20mm margins)
        sb.append("""
            <w:sectPr>
              <w:pgSz w:w="11906" w:h="16838"/>
              <w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134" w:header="708" w:footer="708" w:gutter="0"/>
              <w:cols w:space="708"/>
              <w:docGrid w:linePitch="360"/>
            </w:sectPr>
        """.trimIndent())

        sb.append("</w:body></w:document>")
        return sb.toString()
    }

    // =========================================================================
    // Word Document Body Builder (All Compliances)
    // =========================================================================

    private fun buildAllCompliancesDocXml(
        groups: List<InspectionComplianceGroup>,
        officerName: String
    ): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""")
        sb.append("<w:body>")

        val totalPoints = groups.sumOf { it.compliances.size }
        val openPoints = groups.sumOf { g -> g.compliances.count { it.status == ComplianceStatus.OPEN } }
        val underActionPoints = groups.sumOf { g -> g.compliances.count { it.status == ComplianceStatus.UNDER_ACTION } }
        val resolvedPoints = groups.sumOf { g -> g.compliances.count { it.status == ComplianceStatus.RESOLVED || it.status == ComplianceStatus.CLOSED } }

        // Header Banner
        appendHeaderBanner(
            sb,
            "MASTER COMPLIANCE DOSSIER (ALL INSPECTIONS)",
            "CONSOLIDATED ACTION REGISTER FOR: ${officerName.uppercase()}"
        )

        // Overall Master Statistics Table
        sb.append("<w:p><w:pPr><w:spacing w:before=\"200\" w:after=\"100\"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val=\"26\"/><w:color w:val=\"0B3C5D\"/></w:rPr><w:t>MASTER COMPLIANCE SUMMARY</w:t></w:r></w:p>")
        
        val statsRows = listOf(
            listOf("Officer Name", officerName),
            listOf("Total Inspections with Compliances", groups.size.toString()),
            listOf("Total Action Items Assigned", totalPoints.toString()),
            listOf("Items Currently OPEN (Pending Action)", openPoints.toString()),
            listOf("Items UNDER ACTION", underActionPoints.toString()),
            listOf("Items RESOLVED / CLOSED", resolvedPoints.toString()),
            listOf("Report Generated On", dateTimeFormat.format(Date()))
        )
        appendKeyValueTable(sb, statsRows, headerBg = "1E3A8A")

        sb.append("<w:p><w:pPr><w:spacing w:before=\"300\" w:after=\"150\"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val=\"28\"/><w:color w:val=\"1E3A8A\"/></w:rPr><w:t>INSPECTION-WISE DETAILED COMPLIANCE BREAKDOWN</w:t></w:r></w:p>")

        // Iterate through each inspection group
        groups.forEachIndexed { index, group ->
            sb.append("<w:p><w:pPr><w:spacing w:before=\"240\" w:after=\"80\"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val=\"24\"/><w:color w:val=\"0B3C5D\"/></w:rPr><w:t>Section ${index + 1}: Inspection #${escapeXml(group.inspectionNumber)} (${escapeXml(group.inspectionTypeName)})</w:t></w:r></w:p>")

            appendInspectionOverviewSection(sb, group)
            appendComplianceSummaryBox(sb, group.compliances)
            appendCompliancesTableSection(sb, group.compliances)
            appendIndividualComplianceNarrativeCards(sb, group.compliances)

            group.parentInspection?.let { parent ->
                appendChecklistResponsesSection(sb, parent)
            }

            // Divider between inspections
            if (index < groups.size - 1) {
                sb.append("<w:p><w:pPr><w:pBdr><w:bottom w:val=\"single\" w:sz=\"12\" w:space=\"8\" w:color=\"CBD5E1\"/></w:pPr><w:spacing w:before=\"200\" w:after=\"200\"/></w:p><w:r><w:t></w:t></w:r></w:p>")
            }
        }

        // Final Master Sign-off
        appendSignOffBlock(sb, "Divisional Safety Officer")

        // Section Properties
        sb.append("""
            <w:sectPr>
              <w:pgSz w:w="11906" w:h="16838"/>
              <w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134" w:header="708" w:footer="708" w:gutter="0"/>
              <w:cols w:space="708"/>
              <w:docGrid w:linePitch="360"/>
            </w:sectPr>
        """.trimIndent())

        sb.append("</w:body></w:document>")
        return sb.toString()
    }

    // =========================================================================
    // Document Formatting Sub-sections
    // =========================================================================

    private fun appendHeaderBanner(sb: StringBuilder, title: String, subtitle: String) {
        sb.append("""
            <w:tbl>
              <w:tblPr>
                <w:tblW w:w="9638" w:type="dxa"/>
                <w:jc w:val="center"/>
                <w:tblBorders>
                  <w:top w:val="single" w:sz="12" w:space="0" w:color="1E3A8A"/>
                  <w:left w:val="single" w:sz="12" w:space="0" w:color="1E3A8A"/>
                  <w:bottom w:val="single" w:sz="12" w:space="0" w:color="1E3A8A"/>
                  <w:right w:val="single" w:sz="12" w:space="0" w:color="1E3A8A"/>
                </w:tblBorders>
                <w:tblCellMar>
                  <w:top w:w="180" w:type="dxa"/>
                  <w:left w:w="200" w:type="dxa"/>
                  <w:bottom w:w="180" w:type="dxa"/>
                  <w:right w:w="200" w:type="dxa"/>
                </w:tblCellMar>
              </w:tblPr>
              <w:tr>
                <w:tc>
                  <w:tcPr>
                    <w:tcW w:w="9638" w:type="dxa"/>
                    <w:shd w:val="clear" w:color="auto" w:fill="1E3A8A"/>
                  </w:tcPr>
                  <w:p>
                    <w:pPr><w:jc w:val="center"/><w:spacing w:before="60" w:after="40"/></w:pPr>
                    <w:r><w:rPr><w:b/><w:sz w:val="28"/><w:color w:val="FFFFFF"/></w:rPr><w:t xml:space="preserve">INDIAN RAILWAYS — FIELD INSPECTION SYSTEM</w:t></w:r>
                  </w:p>
                  <w:p>
                    <w:pPr><w:jc w:val="center"/><w:spacing w:before="0" w:after="40"/></w:pPr>
                    <w:r><w:rPr><w:b/><w:sz w:val="24"/><w:color w:val="FDE047"/></w:rPr><w:t xml:space="preserve">${escapeXml(title)}</w:t></w:r>
                  </w:p>
                  <w:p>
                    <w:pPr><w:jc w:val="center"/><w:spacing w:before="0" w:after="60"/></w:pPr>
                    <w:r><w:rPr><w:sz w:val="18"/><w:color w:val="E2E8F0"/></w:rPr><w:t xml:space="preserve">${escapeXml(subtitle)}</w:t></w:r>
                  </w:p>
                </w:tc>
              </w:tr>
            </w:tbl>
            <w:p><w:pPr><w:spacing w:before="120" w:after="80"/></w:pPr><w:r><w:t></w:t></w:r></w:p>
        """.trimIndent())
    }

    private fun appendInspectionOverviewSection(sb: StringBuilder, group: InspectionComplianceGroup) {
        sb.append("<w:p><w:pPr><w:spacing w:before=\"160\" w:after=\"80\"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val=\"22\"/><w:color w:val=\"0F172A\"/></w:rPr><w:t>1. INSPECTION IDENTIFICATION &amp; LOCATION DETAILS</w:t></w:r></w:p>")

        val dateStr = if (group.inspectionDateMillis > 0)
            dateTimeFormat.format(Date(group.inspectionDateMillis)) else "Not recorded"

        val categoryStr = if (group.isSpotInspection) "Spot / Self Inspection" else "Assigned Inspection"

        val parent = group.parentInspection
        val scheduledDateStr = if (parent != null && parent.scheduledDate > 0)
            displayDateFormat.format(Date(parent.scheduledDate)) else dateStr

        val gpsStr = if (parent?.startLatitude != null) {
            "${String.format(Locale.getDefault(), "%.5f", parent.startLatitude)}, ${String.format(Locale.getDefault(), "%.5f", parent.startLongitude)} (Acc: ${parent.startAccuracy ?: 0f}m)"
        } else "Not captured"

        val statusStr = parent?.status?.name?.replace("_", " ") ?: "COMPLETED"

        val rows = listOf(
            listOf("Inspection Number", group.inspectionNumber),
            listOf("Category", categoryStr),
            listOf("Inspection Type", group.inspectionTypeName),
            listOf("Location / Station / Section", group.locationName.ifBlank { "Unspecified" }),
            listOf("Inspecting Officer", group.inspectingOfficerName.ifBlank { "Unassigned" }),
            listOf("Scheduled Date", scheduledDateStr),
            listOf("Execution / Conducted Date", dateStr),
            listOf("Inspection Status", statusStr),
            listOf("GPS Coordinates", gpsStr)
        )

        appendKeyValueTable(sb, rows, headerBg = "0F2C59")

        if (!parent?.remarks.isNullOrBlank()) {
            sb.append("<w:p><w:pPr><w:spacing w:before=\"80\" w:after=\"60\"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val=\"18\"/><w:color w:val=\"475569\"/></w:rPr><w:t>Inspecting Officer Remarks: </w:t></w:r><w:r><w:rPr><w:sz w:val=\"18\"/><w:i/></w:rPr><w:t xml:space=\"preserve\">${escapeXml(parent?.remarks)}</w:t></w:r></w:p>")
        }
    }

    private fun appendComplianceSummaryBox(sb: StringBuilder, compliances: List<Compliance>) {
        val total = compliances.size
        val open = compliances.count { it.status == ComplianceStatus.OPEN }
        val underAction = compliances.count { it.status == ComplianceStatus.UNDER_ACTION }
        val resolved = compliances.count { it.status == ComplianceStatus.RESOLVED || it.status == ComplianceStatus.CLOSED }

        sb.append("<w:p><w:pPr><w:spacing w:before=\"160\" w:after=\"80\"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val=\"22\"/><w:color w:val=\"0F172A\"/></w:rPr><w:t>2. COMPLIANCE ACTION ITEMS SUMMARY</w:t></w:r></w:p>")

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
                  <w:top w:w="120" w:type="dxa"/>
                  <w:left w:w="140" w:type="dxa"/>
                  <w:bottom w:w="120" w:type="dxa"/>
                  <w:right w:w="140" w:type="dxa"/>
                </w:tblCellMar>
              </w:tblPr>
              <w:tr>
                <w:tc>
                  <w:tcPr><w:tcW w:w="2409" w:type="dxa"/><w:shd w:val="clear" w:color="auto" w:fill="F1F5F9"/></w:tcPr>
                  <w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val="18"/><w:color w:val="475569"/></w:rPr><w:t>TOTAL POINTS</w:t></w:r></w:p>
                  <w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val="28"/><w:color w:val="1E293B"/></w:rPr><w:t>$total</w:t></w:r></w:p>
                </w:tc>
                <w:tc>
                  <w:tcPr><w:tcW w:w="2409" w:type="dxa"/><w:shd w:val="clear" w:color="auto" w:fill="FEE2E2"/></w:tcPr>
                  <w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val="18"/><w:color w:val="991B1B"/></w:rPr><w:t>OPEN (PENDING)</w:t></w:r></w:p>
                  <w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val="28"/><w:color w:val="DC2626"/></w:rPr><w:t>$open</w:t></w:r></w:p>
                </w:tc>
                <w:tc>
                  <w:tcPr><w:tcW w:w="2409" w:type="dxa"/><w:shd w:val="clear" w:color="auto" w:fill="FEF3C7"/></w:tcPr>
                  <w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val="18"/><w:color w:val="92400E"/></w:rPr><w:t>UNDER ACTION</w:t></w:r></w:p>
                  <w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val="28"/><w:color w:val="D97706"/></w:rPr><w:t>$underAction</w:t></w:r></w:p>
                </w:tc>
                <w:tc>
                  <w:tcPr><w:tcW w:w="2411" w:type="dxa"/><w:shd w:val="clear" w:color="auto" w:fill="DCFCE7"/></w:tcPr>
                  <w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val="18"/><w:color w:val="166534"/></w:rPr><w:t>RESOLVED / CLOSED</w:t></w:r></w:p>
                  <w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val="28"/><w:color w:val="16A34A"/></w:rPr><w:t>$resolved</w:t></w:r></w:p>
                </w:tc>
              </w:tr>
            </w:tbl>
            <w:p><w:pPr><w:spacing w:before="100" w:after="80"/></w:pPr><w:r><w:t></w:t></w:r></w:p>
        """.trimIndent())
    }

    private fun appendCompliancesTableSection(sb: StringBuilder, compliances: List<Compliance>) {
        sb.append("<w:p><w:pPr><w:spacing w:before=\"160\" w:after=\"80\"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val=\"22\"/><w:color w:val=\"0F172A\"/></w:rPr><w:t>3. COMPLIANCE ACTION REGISTER (TABULAR OVERVIEW)</w:t></w:r></w:p>")

        sb.append("""
            <w:tbl>
              <w:tblPr>
                <w:tblW w:w="9638" w:type="dxa"/>
                <w:jc w:val="center"/>
                <w:tblBorders>
                  <w:top w:val="single" w:sz="6" w:space="0" w:color="94A3B8"/>
                  <w:left w:val="single" w:sz="6" w:space="0" w:color="94A3B8"/>
                  <w:bottom w:val="single" w:sz="6" w:space="0" w:color="94A3B8"/>
                  <w:right w:val="single" w:sz="6" w:space="0" w:color="94A3B8"/>
                  <w:insideH w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/>
                  <w:insideV w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/>
                </w:tblBorders>
                <w:tblCellMar>
                  <w:top w:w="100" w:type="dxa"/>
                  <w:left w:w="120" w:type="dxa"/>
                  <w:bottom w:w="100" w:type="dxa"/>
                  <w:right w:w="120" w:type="dxa"/>
                </w:tblCellMar>
              </w:tblPr>
              <w:tr>
                <w:trPr><w:tblHeader/><w:cantSplit/></w:trPr>
                <w:tc><w:tcPr><w:tcW w:w="600" w:type="dxa"/><w:shd w:val="clear" w:color="auto" w:fill="1E3A8A"/></w:tcPr><w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val="18"/><w:color w:val="FFFFFF"/></w:rPr><w:t>#</w:t></w:r></w:p></w:tc>
                <w:tc><w:tcPr><w:tcW w:w="1800" w:type="dxa"/><w:shd w:val="clear" w:color="auto" w:fill="1E3A8A"/></w:tcPr><w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val="18"/><w:color w:val="FFFFFF"/></w:rPr><w:t>Checklist Point</w:t></w:r></w:p></w:tc>
                <w:tc><w:tcPr><w:tcW w:w="2438" w:type="dxa"/><w:shd w:val="clear" w:color="auto" w:fill="1E3A8A"/></w:tcPr><w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val="18"/><w:color w:val="FFFFFF"/></w:rPr><w:t>Observation / Deficiency</w:t></w:r></w:p></w:tc>
                <w:tc><w:tcPr><w:tcW w:w="1000" w:type="dxa"/><w:shd w:val="clear" w:color="auto" w:fill="1E3A8A"/></w:tcPr><w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val="18"/><w:color w:val="FFFFFF"/></w:rPr><w:t>Severity</w:t></w:r></w:p></w:tc>
                <w:tc><w:tcPr><w:tcW w:w="1100" w:type="dxa"/><w:shd w:val="clear" w:color="auto" w:fill="1E3A8A"/></w:tcPr><w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val="18"/><w:color w:val="FFFFFF"/></w:rPr><w:t>Target Date</w:t></w:r></w:p></w:tc>
                <w:tc><w:tcPr><w:tcW w:w="1100" w:type="dxa"/><w:shd w:val="clear" w:color="auto" w:fill="1E3A8A"/></w:tcPr><w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val="18"/><w:color w:val="FFFFFF"/></w:rPr><w:t>Status</w:t></w:r></w:p></w:tc>
                <w:tc><w:tcPr><w:tcW w:w="1600" w:type="dxa"/><w:shd w:val="clear" w:color="auto" w:fill="1E3A8A"/></w:tcPr><w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val="18"/><w:color w:val="FFFFFF"/></w:rPr><w:t>Action Taken / Remark</w:t></w:r></w:p></w:tc>
              </w:tr>
        """.trimIndent())

        compliances.forEachIndexed { idx, comp ->
            val rowBg = if (idx % 2 == 0) "FFFFFF" else "F8FAFC"
            val targetStr = if (comp.targetDate != null && comp.targetDate!! > 0)
                displayDateFormat.format(Date(comp.targetDate!!)) else "No deadline"

            val statusColor = when (comp.status) {
                ComplianceStatus.RESOLVED, ComplianceStatus.CLOSED -> "15803D"
                ComplianceStatus.UNDER_ACTION -> "B45309"
                else -> "B91C1C"
            }

            val severityColor = when (comp.severity) {
                ComplianceSeverity.CRITICAL -> "DC2626"
                ComplianceSeverity.MAJOR -> "EA580C"
                ComplianceSeverity.MINOR -> "0284C7"
            }

            sb.append("""
              <w:tr>
                <w:trPr><w:cantSplit/></w:trPr>
                <w:tc><w:tcPr><w:tcW w:w="600" w:type="dxa"/><w:shd w:val="clear" w:color="auto" w:fill="$rowBg"/></w:tcPr><w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val="18"/></w:rPr><w:t>${idx + 1}</w:t></w:r></w:p></w:tc>
                <w:tc><w:tcPr><w:tcW w:w="1800" w:type="dxa"/><w:shd w:val="clear" w:color="auto" w:fill="$rowBg"/></w:tcPr><w:p><w:r><w:rPr><w:b/><w:sz w:val="18"/><w:color w:val="0F172A"/></w:rPr><w:t xml:space="preserve">${escapeXml(comp.inspectionPointTitle.ifBlank { "Checklist Point" })}</w:t></w:r></w:p></w:tc>
                <w:tc><w:tcPr><w:tcW w:w="2438" w:type="dxa"/><w:shd w:val="clear" w:color="auto" w:fill="$rowBg"/></w:tcPr><w:p><w:r><w:rPr><w:sz w:val="18"/></w:rPr><w:t xml:space="preserve">${escapeXml(comp.description.ifBlank { "Deficiency recorded during field inspection." })}</w:t></w:r></w:p></w:tc>
                <w:tc><w:tcPr><w:tcW w:w="1000" w:type="dxa"/><w:shd w:val="clear" w:color="auto" w:fill="$rowBg"/></w:tcPr><w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val="18"/><w:color w:val="$severityColor"/></w:rPr><w:t>${comp.severity.name}</w:t></w:r></w:p></w:tc>
                <w:tc><w:tcPr><w:tcW w:w="1100" w:type="dxa"/><w:shd w:val="clear" w:color="auto" w:fill="$rowBg"/></w:tcPr><w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:sz w:val="18"/></w:rPr><w:t>$targetStr</w:t></w:r></w:p></w:tc>
                <w:tc><w:tcPr><w:tcW w:w="1100" w:type="dxa"/><w:shd w:val="clear" w:color="auto" w:fill="$rowBg"/></w:tcPr><w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val="18"/><w:color w:val="$statusColor"/></w:rPr><w:t>${comp.status.name.replace("_", " ")}</w:t></w:r></w:p></w:tc>
                <w:tc><w:tcPr><w:tcW w:w="1600" w:type="dxa"/><w:shd w:val="clear" w:color="auto" w:fill="$rowBg"/></w:tcPr><w:p><w:r><w:rPr><w:sz w:val="18"/><w:color w:val="334155"/></w:rPr><w:t xml:space="preserve">${escapeXml(comp.complianceRemarks.ifBlank { "Awaiting action" })}</w:t></w:r></w:p></w:tc>
              </w:tr>
            """.trimIndent())
        }

        sb.append("</w:tbl>")
        sb.append("<w:p><w:pPr><w:spacing w:before=\"100\" w:after=\"80\"/></w:pPr><w:r><w:t></w:t></w:r></w:p>")
    }

    private fun appendIndividualComplianceNarrativeCards(sb: StringBuilder, compliances: List<Compliance>) {
        sb.append("<w:p><w:pPr><w:spacing w:before=\"180\" w:after=\"80\"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val=\"22\"/><w:color w:val=\"0F172A\"/></w:rPr><w:t>4. POINT-BY-POINT COMPLIANCE DETAILS &amp; ACTION PLAN</w:t></w:r></w:p>")

        compliances.forEachIndexed { idx, comp ->
            val targetStr = if (comp.targetDate != null && comp.targetDate!! > 0)
                displayDateFormat.format(Date(comp.targetDate!!)) else "Not specified"

            val statusBg = when (comp.status) {
                ComplianceStatus.RESOLVED, ComplianceStatus.CLOSED -> "DCFCE7"
                ComplianceStatus.UNDER_ACTION -> "FEF3C7"
                else -> "FEE2E2"
            }
            val statusColor = when (comp.status) {
                ComplianceStatus.RESOLVED, ComplianceStatus.CLOSED -> "166534"
                ComplianceStatus.UNDER_ACTION -> "92400E"
                else -> "991B1B"
            }

            val cardRows = listOf(
                listOf("Action Point Title", comp.inspectionPointTitle.ifBlank { "Point #${idx + 1}" }),
                listOf("Assigned Officer", comp.assignedOfficerName.ifBlank { "Assigned Officer" }),
                listOf("Assigned By (Inspecting Officer)", comp.inspectingOfficerName.ifBlank { "Inspection Authority" }),
                listOf("Severity Level", "${comp.severity.name} Severity"),
                listOf("Target Compliance Date", targetStr),
                listOf("Current Compliance Status", comp.status.name.replace("_", " ")),
                listOf("Recorded Non-Compliance Observation", comp.description.ifBlank { "Observation recorded during field inspection." }),
                listOf("Action Taken / Rectification Details", comp.complianceRemarks.ifBlank { "Action in progress / Pending rectification." })
            )

            sb.append("<w:p><w:pPr><w:spacing w:before=\"140\" w:after=\"60\"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val=\"20\"/><w:color w:val=\"1E3A8A\"/></w:rPr><w:t>Item ${idx + 1}: ${escapeXml(comp.inspectionPointTitle)}</w:t></w:r></w:p>")
            appendKeyValueTable(sb, cardRows, headerBg = "1E3A8A")
            sb.append("<w:p><w:pPr><w:spacing w:before=\"60\" w:after=\"60\"/></w:pPr><w:r><w:t></w:t></w:r></w:p>")
        }
    }

    private fun appendChecklistResponsesSection(sb: StringBuilder, parent: Inspection) {
        if (parent.responses.isBlank() || parent.responses == "{}") return

        val parsedData = parseResponsesAndCustomFields(parent.responses)
        val checklistResponses = parsedData.first
        val customObservations = parsedData.second

        if (checklistResponses.isNotEmpty() || customObservations.isNotEmpty()) {
            sb.append("<w:p><w:pPr><w:spacing w:before=\"180\" w:after=\"80\"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val=\"22\"/><w:color w:val=\"0F172A\"/></w:rPr><w:t>5. FULL FIELD INSPECTION CHECKLIST &amp; RESPONSES CONTEXT</w:t></w:r></w:p>")

            if (checklistResponses.isNotEmpty()) {
                sb.append("<w:p><w:pPr><w:spacing w:before=\"100\" w:after=\"60\"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val=\"20\"/><w:color w:val=\"0F2C59\"/></w:rPr><w:t>Standard Checklist Points:</w:t></w:r></w:p>")
                appendKeyValueTable(sb, checklistResponses, headerBg = "0F2C59")
            }

            if (customObservations.isNotEmpty()) {
                sb.append("<w:p><w:pPr><w:spacing w:before=\"120\" w:after=\"60\"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val=\"20\"/><w:color w:val=\"0F2C59\"/></w:rPr><w:t>Additional Observations &amp; Notes (Added in Field):</w:t></w:r></w:p>")
                appendKeyValueTable(sb, customObservations, headerBg = "0F2C59")
            }
        }
    }

    private fun appendSignOffBlock(sb: StringBuilder, inspectingOfficerName: String) {
        sb.append("""
            <w:p><w:pPr><w:spacing w:before="240" w:after="100"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val="22"/><w:color w:val="0F172A"/></w:rPr><w:t>CERTIFICATION &amp; OFFICIAL SIGN-OFF</w:t></w:r></w:p>
            <w:p><w:r><w:rPr><w:i/><w:sz w:val="18"/><w:color w:val="475569"/></w:rPr><w:t>I hereby certify that the compliance action items specified in this dossier have been reviewed, addressed, and updated in accordance with the prescribed Railway Safety Regulations and Divisional Operating Procedures.</w:t></w:r></w:p>
            <w:p><w:pPr><w:spacing w:before="120" w:after="80"/></w:pPr><w:r><w:t></w:t></w:r></w:p>
            
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
                  <w:p><w:r><w:rPr><w:b/><w:sz w:val="18"/><w:color w:val="1E293B"/></w:rPr><w:t>COMPLIANCE OFFICER SIGNATURE:</w:t></w:r></w:p>
                  <w:p><w:pPr><w:spacing w:before="360" w:after="40"/></w:pPr><w:r><w:t>___________________________________________</w:t></w:r></w:p>
                  <w:p><w:r><w:rPr><w:sz w:val="18"/><w:color w:val="475569"/></w:rPr><w:t>Name: ____________________________________</w:t></w:r></w:p>
                  <w:p><w:r><w:rPr><w:sz w:val="18"/><w:color w:val="475569"/></w:rPr><w:t>Designation / Dept: ________________________</w:t></w:r></w:p>
                  <w:p><w:r><w:rPr><w:sz w:val="18"/><w:color w:val="475569"/></w:rPr><w:t>Date: ____________________________________</w:t></w:r></w:p>
                </w:tc>
                <w:tc>
                  <w:tcPr><w:tcW w:w="4819" w:type="dxa"/></w:tcPr>
                  <w:p><w:r><w:rPr><w:b/><w:sz w:val="18"/><w:color w:val="1E293B"/></w:rPr><w:t>INSPECTING / CONTROLLING OFFICER:</w:t></w:r></w:p>
                  <w:p><w:pPr><w:spacing w:before="360" w:after="40"/></w:pPr><w:r><w:t>___________________________________________</w:t></w:r></w:p>
                  <w:p><w:r><w:rPr><w:sz w:val="18"/><w:color w:val="475569"/></w:rPr><w:t xml:space="preserve">Name: ${escapeXml(inspectingOfficerName.ifBlank { "Inspecting Authority" })}</w:t></w:r></w:p>
                  <w:p><w:r><w:rPr><w:sz w:val="18"/><w:color w:val="475569"/></w:rPr><w:t>Designation: Divisional Safety / Tech Officer</w:t></w:r></w:p>
                  <w:p><w:r><w:rPr><w:sz w:val="18"/><w:color w:val="475569"/></w:rPr><w:t>Date: ____________________________________</w:t></w:r></w:p>
                </w:tc>
              </w:tr>
            </w:tbl>
        """.trimIndent())
    }

    // =========================================================================
    // XML & Table Helpers
    // =========================================================================

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

    private fun parseResponsesAndCustomFields(responsesJson: String): Pair<List<List<String>>, List<List<String>>> {
        val standardList = mutableListOf<List<String>>()
        val customList = mutableListOf<List<String>>()

        try {
            val json = JSONObject(responsesJson)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key == "__custom_fields__" || key == "__attachments__") continue

                val value = json.optString(key, "")
                if (value.startsWith("content://") || value.startsWith("file://")) {
                    standardList.add(listOf(key, "[Photo / Attachment Captured]"))
                } else if (value.isNotBlank()) {
                    standardList.add(listOf(key, value))
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
                        customList.add(listOf(label, value))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return Pair(standardList, customList)
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
                    // Retain valid XML characters only
                    if (ch.code in 0x20..0xD7FF || ch == '\t' || ch == '\n' || ch == '\r') {
                        sb.append(ch)
                    }
                }
            }
        }
        return sb.toString()
    }
}
