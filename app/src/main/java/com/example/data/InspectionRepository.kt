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

    // Categories
    fun getCategoriesForType(typeId: String): Flow<List<FormCategory>> = inspectionDao.getCategoriesForType(typeId)

    fun addCategory(category: FormCategory, onComplete: (Boolean) -> Unit = {}) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                inspectionDao.insertCategory(category)
                FirebaseSyncManager.syncCategory(category)
                kotlinx.coroutines.withContext(Dispatchers.Main) { onComplete(true) }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(Dispatchers.Main) { onComplete(false) }
            }
        }
    }

    fun deleteCategory(category: FormCategory, onComplete: (Boolean) -> Unit = {}) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                inspectionDao.deleteCategory(category)
                inspectionDao.deleteCategoryById(category.id)
                inspectionDao.deleteSubcategoriesByCategoryId(category.id)
                inspectionDao.deleteFieldsByCategoryId(category.id)
                FirebaseSyncManager.deleteCategory(category.id)
                FirebaseSyncManager.deleteSubcategoriesByCategoryId(category.id)
                FirebaseSyncManager.deleteFieldsByCategoryId(category.id)
                kotlinx.coroutines.withContext(Dispatchers.Main) { onComplete(true) }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(Dispatchers.Main) { onComplete(false) }
            }
        }
    }

    // Subcategories
    fun getSubcategoriesForType(typeId: String): Flow<List<FormSubcategory>> = inspectionDao.getSubcategoriesForType(typeId)

    fun getSubcategoriesForCategory(categoryId: String): Flow<List<FormSubcategory>> = inspectionDao.getSubcategoriesForCategory(categoryId)

    fun addSubcategory(subcategory: FormSubcategory, onComplete: (Boolean) -> Unit = {}) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                inspectionDao.insertSubcategory(subcategory)
                FirebaseSyncManager.syncSubcategory(subcategory)
                kotlinx.coroutines.withContext(Dispatchers.Main) { onComplete(true) }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(Dispatchers.Main) { onComplete(false) }
            }
        }
    }

    fun deleteSubcategory(subcategory: FormSubcategory, onComplete: (Boolean) -> Unit = {}) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                inspectionDao.deleteSubcategory(subcategory)
                inspectionDao.deleteSubcategoryById(subcategory.id)
                inspectionDao.deleteFieldsBySubcategory(subcategory.id, subcategory.categoryId, subcategory.name)
                FirebaseSyncManager.deleteSubcategory(subcategory.id)
                FirebaseSyncManager.deleteFieldsBySubcategory(subcategory.id, subcategory.categoryId, subcategory.name)
                kotlinx.coroutines.withContext(Dispatchers.Main) { onComplete(true) }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(Dispatchers.Main) { onComplete(false) }
            }
        }
    }

    // Fields
    fun getInspectionFields(typeId: String): Flow<List<InspectionField>> = inspectionDao.getInspectionFields(typeId)

    fun addInspectionField(field: InspectionField, onComplete: (Boolean) -> Unit = {}) {
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
    
    fun deleteInspectionField(field: InspectionField, onComplete: (Boolean) -> Unit = {}) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                inspectionDao.deleteInspectionField(field)
                inspectionDao.deleteFieldById(field.id)
                FirebaseSyncManager.deleteInspectionField(field.id)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onComplete(true) }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onComplete(false) }
            }
        }
    }

    fun purgeOrphanedFormData(typeId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                inspectionDao.purgeOrphanedSubcategories(typeId)
                inspectionDao.purgeOrphanedCategoryFields(typeId)
                inspectionDao.purgeOrphanedSubcategoryFields(typeId)
                FirebaseSyncManager.purgeOrphanedFormDataInFirestore(typeId)
            } catch (e: Exception) {
                android.util.Log.e("InspectionRepository", "Error purging orphaned form data: ${e.message}")
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
                val db = MyApplication.instance.database
                // 1. Delete all compliances associated with this inspection from Room
                db.complianceDao().deleteCompliancesForInspection(inspection.id, inspection.inspectionNumber)

                // 2. Delete all notifications associated with this inspection from Room
                db.notificationDao().deleteNotificationsForInspection(inspection.id, inspection.inspectionNumber)

                // 3. Delete the inspection itself from Room
                inspectionDao.deleteInspection(inspection)
                inspectionDao.deleteInspectionById(inspection.id)

                // 4. Cascade delete from Cloud Firestore (inspection + compliances + notifications)
                FirebaseSyncManager.deleteInspection(inspection.id, inspection.inspectionNumber)

                // 5. Purge any stray orphaned compliances
                ComplianceRepository().purgeOrphanedCompliances()

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onComplete(true) }
            } catch (e: Exception) {
                android.util.Log.e("InspectionRepository", "Error cascade deleting inspection: ${e.message}", e)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onComplete(false) }
            }
        }
    }
}
