package com.example.ui.inspection

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.models.AttachmentSourceType
import com.example.models.PointAttachment
import com.example.util.AttachmentStorageHelper
import java.io.File

@Composable
fun PointAttachmentSection(
    inspectionId: String,
    fieldName: String,
    fieldLabel: String,
    attachments: List<PointAttachment>,
    onAttachmentAdded: (PointAttachment) -> Unit,
    onAttachmentRemoved: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showSourceDialog by remember { mutableStateOf(false) }
    var previewAttachment by remember { mutableStateOf<PointAttachment?>(null) }

    // State for Camera Capture
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCameraFile
        val uri = pendingCameraUri
        if (success && file != null && uri != null && file.exists() && file.length() > 0) {
            val attachment = AttachmentStorageHelper.createAttachmentFromCameraFile(
                context = context,
                cameraFile = file,
                contentUri = uri,
                fieldName = fieldName
            )
            onAttachmentAdded(attachment)
            Toast.makeText(context, "Photo attached successfully", Toast.LENGTH_SHORT).show()
        }
        pendingCameraFile = null
        pendingCameraUri = null
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                val (file, uri) = AttachmentStorageHelper.createCameraDestination(
                    context = context,
                    inspectionId = inspectionId,
                    fieldName = fieldName
                )
                pendingCameraFile = file
                pendingCameraUri = uri
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(context, "Error starting camera: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Camera permission required to capture photos", Toast.LENGTH_SHORT).show()
        }
    }

    // Gallery Picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { sourceUri: Uri? ->
        if (sourceUri != null) {
            val attachment = AttachmentStorageHelper.copyUriToInspectionStorage(
                context = context,
                sourceUri = sourceUri,
                inspectionId = inspectionId,
                fieldName = fieldName,
                sourceType = AttachmentSourceType.GALLERY
            )
            if (attachment != null) {
                onAttachmentAdded(attachment)
                Toast.makeText(context, "Gallery photo attached", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Could not attach image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Document Picker (PDF, DOC, TXT, sheets, etc.)
    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { sourceUri: Uri? ->
        if (sourceUri != null) {
            val attachment = AttachmentStorageHelper.copyUriToInspectionStorage(
                context = context,
                sourceUri = sourceUri,
                inspectionId = inspectionId,
                fieldName = fieldName,
                sourceType = AttachmentSourceType.DOCUMENT
            )
            if (attachment != null) {
                onAttachmentAdded(attachment)
                Toast.makeText(context, "Document attached", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Could not attach document", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Attachment Control Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Attachment Action Button with Paperclip icon
            OutlinedButton(
                onClick = { showSourceDialog = true },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(
                    1.dp,
                    if (attachments.isNotEmpty()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.testTag("attach_btn_$fieldName")
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = "Attach File",
                    modifier = Modifier.size(16.dp),
                    tint = if (attachments.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (attachments.isEmpty()) "Attach File" else "Add File (${attachments.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (attachments.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (attachments.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${attachments.size} file${if (attachments.size == 1) "" else "s"} attached",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Horizontal List of Attached Files (Thumbnails / Chips)
        if (attachments.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(attachments, key = { it.id }) { item ->
                    AttachmentChipCard(
                        attachment = item,
                        onClick = {
                            if (item.sourceType == AttachmentSourceType.DOCUMENT) {
                                AttachmentStorageHelper.viewAttachment(context, item)
                            } else {
                                previewAttachment = item
                            }
                        },
                        onDelete = { onAttachmentRemoved(item.id) }
                    )
                }
            }
        }
    }

    // Modal Dialog: Choose Attachment Option (Camera, Gallery, Document)
    if (showSourceDialog) {
        AttachmentOptionDialog(
            fieldLabel = fieldLabel,
            onDismiss = { showSourceDialog = false },
            onSelectCamera = {
                showSourceDialog = false
                val permissionGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED

                if (permissionGranted) {
                    val (file, uri) = AttachmentStorageHelper.createCameraDestination(
                        context = context,
                        inspectionId = inspectionId,
                        fieldName = fieldName
                    )
                    pendingCameraFile = file
                    pendingCameraUri = uri
                    cameraLauncher.launch(uri)
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            onSelectGallery = {
                showSourceDialog = false
                galleryLauncher.launch("image/*")
            },
            onSelectDocument = {
                showSourceDialog = false
                documentLauncher.launch("*/*")
            }
        )
    }

    // Full-screen image preview dialog for photos
    if (previewAttachment != null) {
        AttachmentPreviewDialog(
            attachment = previewAttachment!!,
            onDismiss = { previewAttachment = null },
            onOpenExternally = {
                AttachmentStorageHelper.viewAttachment(context, previewAttachment!!)
            }
        )
    }
}

/**
 * Bottom Sheet / Dialog showing the 3 Attachment Options: Camera, Gallery, Document.
 */
@Composable
fun AttachmentOptionDialog(
    fieldLabel: String,
    onDismiss: () -> Unit,
    onSelectCamera: () -> Unit,
    onSelectGallery: () -> Unit,
    onSelectDocument: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Add Attachment",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (fieldLabel.isNotBlank()) {
                    Text(
                        text = "For: $fieldLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Option 1: CAMERA
                Surface(
                    onClick = onSelectCamera,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().testTag("option_camera")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.PhotoCamera,
                                    contentDescription = "Camera",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                "Camera",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Capture live photo with device camera",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Option 2: GALLERY
                Surface(
                    onClick = onSelectGallery,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().testTag("option_gallery")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.PhotoLibrary,
                                    contentDescription = "Gallery",
                                    tint = MaterialTheme.colorScheme.onSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                "Gallery",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Pick image or screenshot from photos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Option 3: DOCUMENT
                Surface(
                    onClick = onSelectDocument,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().testTag("option_document")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Description,
                                    contentDescription = "Document",
                                    tint = MaterialTheme.colorScheme.onTertiary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                "Document",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Upload PDF, DOC, TXT or report files",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Thumbnail card displaying an individual attached item.
 */
@Composable
fun AttachmentChipCard(
    attachment: PointAttachment,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .width(180.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Image Thumbnail or Document Icon
            if (attachment.sourceType == AttachmentSourceType.DOCUMENT) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    val uriOrPath = if (attachment.filePath.isNotBlank() && File(attachment.filePath).exists()) {
                        File(attachment.filePath)
                    } else {
                        attachment.fileUri
                    }
                    AsyncImage(
                        model = uriOrPath,
                        contentDescription = attachment.fileName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Center: Name & Size
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.fileName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (attachment.fileSizeFormatted.isNotBlank())
                        "${attachment.sourceType.name.lowercase().replaceFirstChar { it.uppercase() }} • ${attachment.fileSizeFormatted}"
                    else attachment.sourceType.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1
                )
            }

            // Right: Delete button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Delete Attachment",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Full Screen Image Preview Dialog
 */
@Composable
fun AttachmentPreviewDialog(
    attachment: PointAttachment,
    onDismiss: () -> Unit,
    onOpenExternally: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = attachment.fileName,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (attachment.fileSizeFormatted.isNotBlank()) {
                            Text(
                                text = attachment.fileSizeFormatted,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }

                    IconButton(onClick = onOpenExternally) {
                        Icon(Icons.Default.OpenInNew, contentDescription = "Open Externally", tint = Color.White)
                    }
                }

                // Photo Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val uriOrPath = if (attachment.filePath.isNotBlank() && File(attachment.filePath).exists()) {
                        File(attachment.filePath)
                    } else {
                        attachment.fileUri
                    }
                    AsyncImage(
                        model = uriOrPath,
                        contentDescription = attachment.fileName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
