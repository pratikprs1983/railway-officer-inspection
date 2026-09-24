package com.example.models

import org.json.JSONObject

enum class AttachmentSourceType {
    CAMERA,
    GALLERY,
    DOCUMENT
}

data class PointAttachment(
    val id: String = java.util.UUID.randomUUID().toString(),
    val fieldName: String = "",
    val fileName: String = "",
    val fileUri: String = "",
    val filePath: String = "",
    val sourceType: AttachmentSourceType = AttachmentSourceType.CAMERA,
    val mimeType: String = "",
    val fileSizeFormatted: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("fieldName", fieldName)
            put("fileName", fileName)
            put("fileUri", fileUri)
            put("filePath", filePath)
            put("sourceType", sourceType.name)
            put("mimeType", mimeType)
            put("fileSizeFormatted", fileSizeFormatted)
            put("timestamp", timestamp)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): PointAttachment {
            val srcTypeStr = json.optString("sourceType", AttachmentSourceType.CAMERA.name)
            val srcType = try {
                AttachmentSourceType.valueOf(srcTypeStr)
            } catch (e: Exception) {
                AttachmentSourceType.CAMERA
            }
            return PointAttachment(
                id = json.optString("id", java.util.UUID.randomUUID().toString()),
                fieldName = json.optString("fieldName", ""),
                fileName = json.optString("fileName", "Attachment"),
                fileUri = json.optString("fileUri", ""),
                filePath = json.optString("filePath", ""),
                sourceType = srcType,
                mimeType = json.optString("mimeType", ""),
                fileSizeFormatted = json.optString("fileSizeFormatted", ""),
                timestamp = json.optLong("timestamp", System.currentTimeMillis())
            )
        }
    }
}
