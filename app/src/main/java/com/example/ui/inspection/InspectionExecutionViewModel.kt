package com.example.ui.inspection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ComplianceRepository
import com.example.data.InspectionRepository
import com.example.data.UserRepository
import com.example.models.Compliance
import com.example.models.ComplianceSeverity
import com.example.models.ComplianceStatus
import com.example.models.FieldType
import com.example.models.Inspection
import com.example.models.InspectionField
import com.example.models.InspectionStatus
import com.example.models.Role
import com.example.models.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class InspectionExecutionViewModel : ViewModel() {
    private val repo = InspectionRepository()
    private val complianceRepo = ComplianceRepository()
    private val userRepo = UserRepository()

    private val _inspection = MutableStateFlow<Inspection?>(null)
    val inspection: StateFlow<Inspection?> = _inspection.asStateFlow()

    private val _fields = MutableStateFlow<List<InspectionField>>(emptyList())
    val fields: StateFlow<List<InspectionField>> = _fields.asStateFlow()

    // Additional custom fields added on-the-fly by the inspecting officer
    private val _customFields = MutableStateFlow<List<InspectionField>>(emptyList())
    val customFields: StateFlow<List<InspectionField>> = _customFields.asStateFlow()

    // Map of fieldName to response value
    private val _responses = MutableStateFlow<Map<String, String>>(emptyMap())
    val responses: StateFlow<Map<String, String>> = _responses.asStateFlow()

    // Map of fieldName to list of attachments
    private val _attachments = MutableStateFlow<Map<String, List<com.example.models.PointAttachment>>>(emptyMap())
    val attachments: StateFlow<Map<String, List<com.example.models.PointAttachment>>> = _attachments.asStateFlow()

    // Compliances linked to this inspection
    private val _compliances = MutableStateFlow<List<Compliance>>(emptyList())
    val compliances: StateFlow<List<Compliance>> = _compliances.asStateFlow()

    // Officers available for compliance assignment
    private val _officers = MutableStateFlow<List<User>>(emptyList())
    val officers: StateFlow<List<User>> = _officers.asStateFlow()

    init {
        // Load officers
        viewModelScope.launch {
            userRepo.getUsers()
                .catch { e -> android.util.Log.e("ExecVM", "Error loading officers", e) }
                .collect { list ->
                    _officers.value = list.filter { it.role != Role.SUPER_ADMIN }
                }
        }
    }

    fun loadInspection(inspectionId: String) {
        viewModelScope.launch {
            repo.getInspectionById(inspectionId)
                .catch { e -> android.util.Log.e("ExecVM", "Error fetching inspection", e) }
                .collect { insp ->
                    _inspection.value = insp
                    if (insp != null) {
                        // Load saved responses and custom fields if any
                        val savedResponses = mutableMapOf<String, String>()
                        val loadedCustomFields = mutableListOf<InspectionField>()
                        val loadedAttachments = mutableMapOf<String, List<com.example.models.PointAttachment>>()

                        try {
                            val json = JSONObject(insp.responses)
                            if (json.has("__custom_fields__")) {
                                val customStr = json.getString("__custom_fields__")
                                val customArray = JSONArray(customStr)
                                for (i in 0 until customArray.length()) {
                                    val obj = customArray.getJSONObject(i)
                                    val id = obj.optString("id", UUID.randomUUID().toString())
                                    val label = obj.optString("label", "Additional Field")
                                    val fieldName = obj.optString("fieldName", "custom_$i")
                                    loadedCustomFields.add(
                                        InspectionField(
                                            id = id,
                                            inspectionTypeId = insp.inspectionTypeId,
                                            label = label,
                                            fieldName = fieldName,
                                            fieldType = FieldType.TEXT,
                                            required = false,
                                            placeholder = "Enter observation / note..."
                                        )
                                    )
                                }
                            }
                            if (json.has("__attachments__")) {
                                try {
                                    val attJson = json.getJSONObject("__attachments__")
                                    val keys = attJson.keys()
                                    while (keys.hasNext()) {
                                        val fKey = keys.next()
                                        val arr = attJson.getJSONArray(fKey)
                                        val list = mutableListOf<com.example.models.PointAttachment>()
                                        for (idx in 0 until arr.length()) {
                                            list.add(com.example.models.PointAttachment.fromJson(arr.getJSONObject(idx)))
                                        }
                                        loadedAttachments[fKey] = list
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("ExecVM", "Error parsing attachments", e)
                                }
                            }
                            val keys = json.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                if (key != "__custom_fields__" && key != "__attachments__") {
                                    savedResponses[key] = json.getString(key)
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ExecVM", "Error parsing responses", e)
                        }

                        _responses.value = savedResponses
                        _customFields.value = loadedCustomFields
                        _attachments.value = loadedAttachments

                        // Fetch template fields
                        fetchFields(insp.inspectionTypeId)

                        // Fetch compliances for this inspection
                        fetchCompliances(insp.id)
                    }
                }
        }
    }

    private fun fetchCompliances(inspectionId: String) {
        viewModelScope.launch {
            complianceRepo.getCompliancesByInspection(inspectionId)
                .catch { e -> android.util.Log.e("ExecVM", "Error fetching compliances", e) }
                .collect { list ->
                    _compliances.value = list
                }
        }
    }

    private fun fetchFields(typeId: String) {
        viewModelScope.launch {
            repo.getInspectionFields(typeId)
                .catch { e -> android.util.Log.e("ExecVM", "Error fetching fields", e) }
                .collect { fieldList ->
                    _fields.value = fieldList
                }
        }
    }

    fun addCustomField(label: String = ""): InspectionField {
        val existingCount = _customFields.value.size
        val cleanInput = label.trim()
        val finalLabel = when {
            cleanInput.isBlank() -> "Additional Observation ${existingCount + 1}"
            cleanInput.equals("Additional Observation", ignoreCase = true) -> "Additional Observation ${existingCount + 1}"
            _customFields.value.any { it.label.equals(cleanInput, ignoreCase = true) } -> "$cleanInput (${existingCount + 1})"
            else -> cleanInput
        }
        val fieldName = "custom_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(5)}"
        val newField = InspectionField(
            id = UUID.randomUUID().toString(),
            inspectionTypeId = _inspection.value?.inspectionTypeId ?: "",
            label = finalLabel,
            fieldName = fieldName,
            fieldType = FieldType.TEXT,
            required = false,
            placeholder = "Enter observation details..."
        )
        _customFields.value = _customFields.value + newField
        return newField
    }

    fun updateCustomFieldLabel(fieldId: String, newLabel: String) {
        val trimmed = newLabel.trim()
        if (trimmed.isEmpty()) return
        _customFields.value = _customFields.value.map {
            if (it.id == fieldId) it.copy(label = trimmed) else it
        }
    }

    fun removeCustomField(fieldId: String) {
        val target = _customFields.value.find { it.id == fieldId }
        _customFields.value = _customFields.value.filter { it.id != fieldId }
        if (target != null) {
            val current = _responses.value.toMutableMap()
            current.remove(target.fieldName)
            _responses.value = current
        }
    }

    fun updateResponse(fieldName: String, value: String) {
        val current = _responses.value.toMutableMap()
        current[fieldName] = value
        _responses.value = current
    }

    fun startInspection(
        lat: Double?,
        lng: Double?,
        acc: Float?,
        selfieUri: String,
        executionDate: Long? = null,
        remarks: String? = null
    ) {
        val currentInspection = _inspection.value ?: return
        val chosenExecutionDate = executionDate ?: System.currentTimeMillis()
        
        val updatedInspection = currentInspection.copy(
            status = InspectionStatus.IN_PROGRESS,
            startLatitude = lat,
            startLongitude = lng,
            startAccuracy = acc,
            selfieURL = selfieUri,
            executionDate = chosenExecutionDate,
            remarks = if (!remarks.isNullOrBlank()) remarks else currentInspection.remarks,
            startedAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        repo.assignInspection(updatedInspection) { success ->
            if (success) {
                _inspection.value = updatedInspection
            }
        }
    }

    fun updateExecutionDate(newDate: Long) {
        val currentInspection = _inspection.value ?: return
        val updatedInspection = currentInspection.copy(
            executionDate = newDate,
            updatedAt = System.currentTimeMillis()
        )
        repo.assignInspection(updatedInspection) { success ->
            if (success) {
                _inspection.value = updatedInspection
            }
        }
    }

    fun updateLocation(lat: Double?, lng: Double?, acc: Float?) {
        val currentInspection = _inspection.value ?: return
        val updatedInspection = currentInspection.copy(
            startLatitude = lat,
            startLongitude = lng,
            startAccuracy = acc,
            updatedAt = System.currentTimeMillis()
        )
        repo.assignInspection(updatedInspection) { success ->
            if (success) {
                _inspection.value = updatedInspection
            }
        }
    }

    fun updateSelfie(selfieUri: String) {
        val currentInspection = _inspection.value ?: return
        val updatedInspection = currentInspection.copy(
            selfieURL = selfieUri,
            updatedAt = System.currentTimeMillis()
        )
        repo.assignInspection(updatedInspection) { success ->
            if (success) {
                _inspection.value = updatedInspection
            }
        }
    }

    fun saveInspection(isComplete: Boolean, onResult: (Boolean) -> Unit) {
        val currentInspection = _inspection.value ?: return
        
        // Convert map to JSON
        val json = JSONObject()
        _responses.value.forEach { (key, value) ->
            if (key != "__custom_fields__") {
                json.put(key, value)
            }
        }

        // Save custom fields metadata
        val customArray = JSONArray()
        _customFields.value.forEach { cf ->
            val obj = JSONObject()
            obj.put("id", cf.id)
            obj.put("label", cf.label)
            obj.put("fieldName", cf.fieldName)
            customArray.put(obj)
        }
        json.put("__custom_fields__", customArray.toString())

        // Save attachments per field
        val attObj = JSONObject()
        _attachments.value.forEach { (fieldName, list) ->
            if (list.isNotEmpty()) {
                val arr = JSONArray()
                list.forEach { att ->
                    arr.put(att.toJson())
                }
                attObj.put(fieldName, arr)
            }
        }
        json.put("__attachments__", attObj)
        
        val updatedInspection = currentInspection.copy(
            responses = json.toString(),
            status = if (isComplete) InspectionStatus.COMPLETED else InspectionStatus.IN_PROGRESS,
            completedAt = if (isComplete) System.currentTimeMillis() else currentInspection.completedAt,
            updatedAt = System.currentTimeMillis()
        )

        repo.assignInspection(updatedInspection) { success ->
            onResult(success)
        }
    }

    fun addAttachment(fieldName: String, attachment: com.example.models.PointAttachment) {
        val current = _attachments.value.toMutableMap()
        val list = current[fieldName]?.toMutableList() ?: mutableListOf()
        list.add(attachment)
        current[fieldName] = list
        _attachments.value = current

        // Auto-save changes so attachment is never lost
        saveInspection(isComplete = false) { /* auto-saved */ }
    }

    fun removeAttachment(fieldName: String, attachmentId: String) {
        val current = _attachments.value.toMutableMap()
        val list = current[fieldName]?.toMutableList() ?: mutableListOf()
        list.removeAll { it.id == attachmentId }
        if (list.isEmpty()) {
            current.remove(fieldName)
        } else {
            current[fieldName] = list
        }
        _attachments.value = current

        // Auto-save changes
        saveInspection(isComplete = false) { /* auto-saved */ }
    }

    fun sendPointForCompliance(
        pointId: String = "",
        pointTitle: String,
        observationDescription: String,
        assignedOfficer: User,
        severity: ComplianceSeverity,
        targetDate: Long,
        onResult: (Boolean) -> Unit
    ) {
        val currentInspection = _inspection.value ?: return
        val currentOfficer = com.example.data.SessionManager.currentUser.value

        val compliance = Compliance(
            id = UUID.randomUUID().toString(),
            inspectionId = currentInspection.id,
            inspectionNumber = currentInspection.inspectionNumber,
            inspectionPointId = pointId.trim(),
            inspectionPointTitle = pointTitle.trim(),
            title = if (pointTitle.isNotBlank()) "Compliance: $pointTitle" else "Inspection Observation (${currentInspection.inspectionNumber})",
            description = observationDescription.trim(),
            locationName = currentInspection.locationName,
            inspectingOfficerId = currentOfficer?.uid ?: currentInspection.assignedOfficerId,
            inspectingOfficerName = currentOfficer?.name ?: currentInspection.assignedOfficerName,
            assignedOfficerId = assignedOfficer.uid,
            assignedOfficerName = assignedOfficer.name,
            severity = severity,
            status = ComplianceStatus.OPEN,
            targetDate = targetDate,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        complianceRepo.saveCompliance(compliance) { success ->
            if (success) {
                fetchCompliances(currentInspection.id)
            }
            onResult(success)
        }
    }

    fun updateComplianceStatus(
        compliance: Compliance,
        newStatus: ComplianceStatus,
        remarks: String = "",
        onResult: (Boolean) -> Unit = {}
    ) {
        val updated = compliance.copy(
            status = newStatus,
            complianceRemarks = if (remarks.isNotBlank()) remarks else compliance.complianceRemarks,
            actionTaken = if (remarks.isNotBlank()) remarks else compliance.actionTaken,
            closedAt = if (newStatus == ComplianceStatus.RESOLVED || newStatus == ComplianceStatus.CLOSED) System.currentTimeMillis() else compliance.closedAt,
            updatedAt = System.currentTimeMillis()
        )
        complianceRepo.saveCompliance(updated) { success ->
            if (success && _inspection.value != null) {
                fetchCompliances(_inspection.value!!.id)
            }
            onResult(success)
        }
    }
}
