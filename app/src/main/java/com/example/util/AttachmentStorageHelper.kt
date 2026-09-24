package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.models.AttachmentSourceType
import com.example.models.PointAttachment
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object AttachmentStorageHelper {

    fun getInspectionAttachmentDir(context: Context, inspectionId: String): File {
        val safeId = if (inspectionId.isBlank()) "general" else inspectionId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val dir = File(context.filesDir, "inspection_attachments/$safeId")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Prepares a destination file & content URI for the Camera capture intent.
     */
    fun createCameraDestination(context: Context, inspectionId: String, fieldName: String): Pair<File, Uri> {
        val dir = getInspectionAttachmentDir(context, inspectionId)
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val cleanFieldName = fieldName.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val file = File(dir, "CAM_${cleanFieldName}_$timeStamp.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Pair(file, uri)
    }

    /**
     * Resolves the actual display name of a Uri from content resolver.
     */
    fun queryFileName(context: Context, uri: Uri): String {
        var name = ""
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        name = cursor.getString(nameIndex) ?: ""
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (name.isBlank()) {
            name = uri.lastPathSegment ?: "file_${System.currentTimeMillis()}"
        }
        return name
    }

    /**
     * Formats bytes into human-readable B, KB, MB
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return ""
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        val size = bytes / Math.pow(1024.0, index.toDouble())
        return DecimalFormat("#,##0.#").format(size) + " " + units[index]
    }

    /**
     * Copies a chosen Gallery image or Document file into the internal inspection directory
     * so it remains accessible offline permanently.
     */
    fun copyUriToInspectionStorage(
        context: Context,
        sourceUri: Uri,
        inspectionId: String,
        fieldName: String,
        sourceType: AttachmentSourceType
    ): PointAttachment? {
        return try {
            val originalName = queryFileName(context, sourceUri)
            val extension = originalName.substringAfterLast('.', "")
            val extSuffix = if (extension.isNotBlank()) ".$extension" else ""
            val baseName = originalName.substringBeforeLast('.')

            val dir = getInspectionAttachmentDir(context, inspectionId)
            val prefix = when (sourceType) {
                AttachmentSourceType.CAMERA -> "CAM"
                AttachmentSourceType.GALLERY -> "GAL"
                AttachmentSourceType.DOCUMENT -> "DOC"
            }
            val destinationFile = File(dir, "${prefix}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}$extSuffix")

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            val fileSize = destinationFile.length()
            val mimeType = context.contentResolver.getType(sourceUri) ?: getMimeTypeFromExtension(extension)
            val fileProviderUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                destinationFile
            )

            PointAttachment(
                id = UUID.randomUUID().toString(),
                fieldName = fieldName,
                fileName = if (originalName.isNotBlank()) originalName else destinationFile.name,
                fileUri = fileProviderUri.toString(),
                filePath = destinationFile.absolutePath,
                sourceType = sourceType,
                mimeType = mimeType,
                fileSizeFormatted = formatFileSize(fileSize),
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Converts a camera captured file into a PointAttachment.
     */
    fun createAttachmentFromCameraFile(
        context: Context,
        cameraFile: File,
        contentUri: Uri,
        fieldName: String
    ): PointAttachment {
        val fileSize = cameraFile.length()
        return PointAttachment(
            id = UUID.randomUUID().toString(),
            fieldName = fieldName,
            fileName = cameraFile.name,
            fileUri = contentUri.toString(),
            filePath = cameraFile.absolutePath,
            sourceType = AttachmentSourceType.CAMERA,
            mimeType = "image/jpeg",
            fileSizeFormatted = formatFileSize(fileSize),
            timestamp = System.currentTimeMillis()
        )
    }

    fun getMimeTypeFromExtension(ext: String): String {
        return when (ext.lowercase()) {
            "pdf" -> "application/pdf"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "txt" -> "text/plain"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "*/*"
        }
    }

    /**
     * Attempts to open the attachment file using an external viewer or default system handler.
     */
    fun viewAttachment(context: Context, attachment: PointAttachment) {
        try {
            val uri = if (attachment.filePath.isNotBlank()) {
                val file = File(attachment.filePath)
                if (file.exists()) {
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                } else {
                    Uri.parse(attachment.fileUri)
                }
            } else {
                Uri.parse(attachment.fileUri)
            }

            val mime = if (attachment.mimeType.isNotBlank()) attachment.mimeType else "*/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open file directly: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
