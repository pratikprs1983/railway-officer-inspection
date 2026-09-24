package com.example.data

import com.example.MyApplication
import com.example.models.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class InspectionRepository {
    private val inspectionDao = MyApplication.instance.database.inspectionDao()

    fun getInspectionTypes(): Flow<List<InspectionType>> = inspectionDao.getInspectionTypes()

    fun addInspectionType(type: InspectionType, onComplete: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                inspectionDao.insertInspectionType(type)
                FirebaseSyncManager.syncInspectionType(type)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onComplete(true) }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onComplete(false) }
            }
        }
    }
    
    fun deleteInspectionType(type: InspectionType, onComplete: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                inspectionDao.deleteInspectionType(type)
                FirebaseSyncManager.deleteInspectionType(type.id)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onComplete(true) }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onComplete(false) }
            }
        }
    }

    fun getInspectionFields(typeId: String): Flow<List<InspectionField>> = inspectionDao.getInspectionFields(typeId)

    fun addInspectionField(field: InspectionField, onComplete: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                inspectionDao.insertInspectionField(field)
                FirebaseSyncManager.syncInspectionField(field)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onComplete(true) }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onComplete(false) }
            }
        }
    }
    
    fun deleteInspectionField(field: InspectionField, onComplete: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                inspectionDao.deleteInspectionField(field)
                FirebaseSyncManager.deleteInspectionField(field.id)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onComplete(true) }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onComplete(false) }
            }
        }
    }

    fun getInspections(): Flow<List<Inspection>> = inspectionDao.getInspections()

    fun getInspectionById(id: String): Flow<Inspection?> = inspectionDao.getInspectionById(id)

    fun assignInspection(inspection: Inspection, onComplete: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val newInspection = if (inspection.inspectionNumber.isEmpty()) {
                    val formattedId = "INSP-2026-${java.util.UUID.randomUUID().toString().take(6).uppercase()}"
                    inspection.copy(inspectionNumber = formattedId)
                } else {
                    inspection
                }
                inspectionDao.insertInspection(newInspection)
                FirebaseSyncManager.syncInspection(newInspection)
                if (newInspection.assignedOfficerId.isNotBlank()) {
                    val adminName = SessionManager.currentUser.value?.name ?: "Admin"
                    com.example.util.NotificationService.notifyInspectionAssigned(newInspection, adminName)
                }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onComplete(true) }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onComplete(false) }
            }
        }
    }

    fun deleteInspection(inspection: Inspection, onComplete: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                inspectionDao.deleteInspection(inspection)
                FirebaseSyncManager.deleteInspection(inspection.id)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onComplete(true) }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onComplete(false) }
            }
        }
    }
}
