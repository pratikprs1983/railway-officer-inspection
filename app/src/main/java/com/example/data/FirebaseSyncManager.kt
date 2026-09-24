package com.example.data

import android.content.Context
import android.util.Log
import com.example.MyApplication
import com.example.models.*
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

object FirebaseSyncManager {
    private const val TAG = "FirebaseSyncManager"
    const val EXPECTED_PROJECT_ID = "roicms-de75c"
    const val APP_PACKAGE_NAME = "com.aistudio.roicms.amgtaf"
    const val DEBUG_SHA1 = "38:A6:47:8A:2F:72:88:A5:5C:25:47:D7:EF:CF:00:98:55:BC:FF:16"
    const val DEBUG_SHA256 = "14:6D:F1:FA:CA:1E:E3:BC:B9:F6:21:22:7A:8C:06:1E:F6:5B:4A:2E:94:DB:73:BB:45:E4:E1:F1:3B:9A:A2:25"

    val isFirebaseAvailable: Boolean
        get() = try {
            FirebaseApp.getApps(MyApplication.instance).isNotEmpty()
        } catch (_: Exception) {
            false
        }

    val currentProjectId: String
        get() = try {
            if (!isFirebaseAvailable) "Not Initialized"
            else FirebaseApp.getInstance().options.projectId ?: "Unknown"
        } catch (_: Exception) {
            "Unknown"
        }

    val isPlaceholderConfig: Boolean
        get() = try {
            if (!isFirebaseAvailable) true
            else {
                val pid = currentProjectId
                pid.isBlank() || pid == "remixed-project-id" || pid == "Not Initialized" || pid == "Unknown"
            }
        } catch (_: Exception) {
            true
        }

    private val db: FirebaseFirestore?
        get() = try {
            if (isFirebaseAvailable) FirebaseFirestore.getInstance() else null
        } catch (_: Exception) {
            null
        }

    private val activeListeners = mutableListOf<ListenerRegistration>()

    private val _syncStatus = MutableStateFlow("Idle")
    val syncStatus = _syncStatus.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    fun initialize() {
        checkAndApplySavedConfig(MyApplication.instance)

        if (!isFirebaseAvailable) {
            Log.w(TAG, "FirebaseApp is not configured in this process. Skipping Firestore sync.")
            _syncStatus.value = "Local storage mode (Firebase unconfigured)"
            return
        }

        if (isPlaceholderConfig) {
            Log.w(TAG, "FirebaseApp is using placeholder project_id ($currentProjectId). Cloud sync requires google-services.json from $EXPECTED_PROJECT_ID.")
            _syncStatus.value = "Local storage mode (Placeholder config: $currentProjectId)"
            return
        }

        _syncStatus.value = "Connected to $currentProjectId"
        startRealtimeListeners()
        // Proactively push existing local data so Firestore collections are immediately populated
        syncAllToCloud()
    }

    private fun checkAndApplySavedConfig(context: Context) {
        try {
            val prefs = context.getSharedPreferences("firebase_runtime_config", Context.MODE_PRIVATE)
            var savedProjectId = prefs.getString("project_id", null)
            var savedApiKey = prefs.getString("api_key", null)
            var savedAppId = prefs.getString("app_id", null)

            // Default to configured project roicms-de75c
            if (savedProjectId.isNullOrBlank() || savedApiKey.isNullOrBlank() || savedAppId.isNullOrBlank()) {
                savedProjectId = "roicms-de75c"
                savedApiKey = "AIzaSyBkJNQlw6yP0jLDUo3N-BU9ESMlj2C32k0"
                savedAppId = "1:1006127721674:android:c707b7014fbf96f25b5833"
                prefs.edit()
                    .putString("project_id", savedProjectId)
                    .putString("api_key", savedApiKey)
                    .putString("app_id", savedAppId)
                    .apply()
            }

            if (currentProjectId != savedProjectId) {
                reinitializeFirebase(context, savedProjectId, savedApiKey, savedAppId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply saved runtime Firebase config: ${e.message}")
        }
    }

    fun applyGoogleServicesJson(context: Context, jsonString: String): Result<String> {
        return try {
            val root = JSONObject(jsonString)
            val projectInfo = root.getJSONObject("project_info")
            val projectId = projectInfo.getString("project_id")
            val clientArray = root.getJSONArray("client")
            if (clientArray.length() == 0) return Result.failure(Exception("No client found in google-services.json"))
            val client = clientArray.getJSONObject(0)
            val appId = client.getJSONObject("client_info").getString("mobilesdk_app_id")
            val apiKey = client.getJSONArray("api_key").getJSONObject(0).getString("current_key")

            // Save to SharedPreferences
            val prefs = context.getSharedPreferences("firebase_runtime_config", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("project_id", projectId)
                .putString("app_id", appId)
                .putString("api_key", apiKey)
                .putString("raw_json", jsonString)
                .apply()

            reinitializeFirebase(context, projectId, apiKey, appId)
            Result.success("Connected to Firebase project '$projectId'!")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing google-services.json: ${e.message}", e)
            Result.failure(Exception("Invalid google-services.json: ${e.localizedMessage}"))
        }
    }

    fun reinitializeFirebase(context: Context, projectId: String, apiKey: String, appId: String) {
        try {
            // Remove previous listeners
            activeListeners.forEach { it.remove() }
            activeListeners.clear()

            val builder = FirebaseOptions.Builder()
                .setProjectId(projectId)
                .setApiKey(apiKey)
                .setApplicationId(appId)

            val existingApps = FirebaseApp.getApps(context)
            val defaultApp = existingApps.find { it.name == FirebaseApp.DEFAULT_APP_NAME }
            defaultApp?.delete()

            FirebaseApp.initializeApp(context, builder.build())
            Log.i(TAG, "Successfully reinitialized Firebase with project: $projectId")

            _syncStatus.value = "Connected to $projectId"
            startRealtimeListeners()
            syncAllToCloud()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reinitialize Firebase: ${e.message}", e)
        }
    }

    // --- Push single items to Firestore ---

    fun syncUser(user: User, onComplete: ((Boolean, String?) -> Unit)? = null) {
        if (isPlaceholderConfig) {
            val msg = "Cloud sync pending: google-services.json has placeholder '$currentProjectId'. Saved locally in device database."
            Log.w(TAG, msg)
            onComplete?.invoke(false, msg)
            return
        }
        val currentDb = db
        if (currentDb == null) {
            onComplete?.invoke(false, "Firestore instance not available")
            return
        }

        val data = hashMapOf(
            "uid" to user.uid,
            "hrmsId" to user.hrmsId,
            "name" to user.name,
            "designation" to user.designation,
            "departmentId" to user.departmentId,
            "departmentName" to user.departmentName,
            "mobile" to user.mobile,
            "email" to user.email,
            "role" to user.role.name,
            "division" to user.division,
            "office" to user.office,
            "photoURL" to user.photoURL,
            "status" to user.status,
            "createdAt" to user.createdAt,
            "updatedAt" to user.updatedAt
        )
        currentDb.collection("users").document(user.uid)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                Log.d(TAG, "User ${user.uid} synced to Firestore")
                onComplete?.invoke(true, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error syncing user: ${e.message}")
                onComplete?.invoke(false, e.localizedMessage ?: "Firestore write error")
            }
    }

    fun syncAllUsers(onComplete: (Boolean, String) -> Unit) {
        if (isPlaceholderConfig) {
            onComplete(false, "Cannot sync to Cloud: App is using placeholder '$currentProjectId'. Connect to '$EXPECTED_PROJECT_ID' first.")
            return
        }
        val currentDb = db
        if (currentDb == null) {
            onComplete(false, "Firestore is unavailable.")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val users = MyApplication.instance.database.userDao().getAllUsersList()
                if (users.isEmpty()) {
                    onComplete(true, "No users in local database to sync.")
                    return@launch
                }
                users.forEach { user ->
                    val data = hashMapOf(
                        "uid" to user.uid,
                        "hrmsId" to user.hrmsId,
                        "name" to user.name,
                        "designation" to user.designation,
                        "departmentId" to user.departmentId,
                        "departmentName" to user.departmentName,
                        "mobile" to user.mobile,
                        "email" to user.email,
                        "role" to user.role.name,
                        "division" to user.division,
                        "office" to user.office,
                        "photoURL" to user.photoURL,
                        "status" to user.status,
                        "createdAt" to user.createdAt,
                        "updatedAt" to user.updatedAt
                    )
                    currentDb.collection("users").document(user.uid).set(data, SetOptions.merge())
                }
                onComplete(true, "Successfully pushed ${users.size} user(s) to Cloud Firestore ($currentProjectId)!")
            } catch (e: Exception) {
                onComplete(false, "Sync failed: ${e.localizedMessage ?: e.message}")
            }
        }
    }

    fun deleteUser(userId: String) {
        db?.collection("users")?.document(userId)?.delete()
    }

    fun syncStation(station: Station) {
        val data = hashMapOf(
            "id" to station.id,
            "stationName" to station.stationName,
            "stationCode" to station.stationCode,
            "section" to station.section,
            "division" to station.division,
            "active" to station.active
        )
        db?.collection("stations")?.document(station.id)
            ?.set(data, SetOptions.merge())
    }

    fun deleteStation(stationId: String) {
        db?.collection("stations")?.document(stationId)?.delete()
    }

    fun syncSection(section: Section) {
        val data = hashMapOf(
            "id" to section.id,
            "sectionName" to section.sectionName,
            "fromLocation" to section.fromLocation,
            "toLocation" to section.toLocation,
            "active" to section.active
        )
        db?.collection("sections")?.document(section.id)
            ?.set(data, SetOptions.merge())
    }

    fun deleteSection(sectionId: String) {
        db?.collection("sections")?.document(sectionId)?.delete()
    }

    fun syncLCGate(gate: LCGate) {
        val data = hashMapOf(
            "id" to gate.id,
            "lcNumber" to gate.lcNumber,
            "location" to gate.location,
            "section" to gate.section,
            "gateType" to gate.gateType,
            "active" to gate.active
        )
        db?.collection("lc_gates")?.document(gate.id)
            ?.set(data, SetOptions.merge())
    }

    fun deleteLCGate(gateId: String) {
        db?.collection("lc_gates")?.document(gateId)?.delete()
    }

    fun syncInspectionType(type: InspectionType) {
        val data = hashMapOf(
            "id" to type.id,
            "name" to type.name,
            "category" to type.category.name,
            "description" to type.description,
            "active" to type.active,
            "createdAt" to type.createdAt,
            "createdBy" to type.createdBy
        )
        db?.collection("inspection_types")?.document(type.id)
            ?.set(data, SetOptions.merge())
            ?.addOnSuccessListener { Log.d(TAG, "InspectionType ${type.id} synced to Firestore") }
            ?.addOnFailureListener { e -> Log.e(TAG, "Error syncing inspection type: ${e.message}") }
    }

    fun deleteInspectionType(typeId: String) {
        db?.collection("inspection_types")?.document(typeId)?.delete()
    }

    fun syncInspectionField(field: InspectionField) {
        val data = hashMapOf(
            "id" to field.id,
            "inspectionTypeId" to field.inspectionTypeId,
            "label" to field.label,
            "fieldName" to field.fieldName,
            "fieldType" to field.fieldType.name,
            "required" to field.required,
            "options" to field.options,
            "placeholder" to field.placeholder,
            "helpText" to field.helpText,
            "displayOrder" to field.displayOrder,
            "active" to field.active,
            "createdAt" to field.createdAt,
            "updatedAt" to field.updatedAt
        )
        db?.collection("inspection_fields")?.document(field.id)
            ?.set(data, SetOptions.merge())
    }

    fun deleteInspectionField(fieldId: String) {
        db?.collection("inspection_fields")?.document(fieldId)?.delete()
    }

    fun syncInspection(inspection: Inspection) {
        val data = hashMapOf(
            "id" to inspection.id,
            "inspectionNumber" to inspection.inspectionNumber,
            "inspectionTypeId" to inspection.inspectionTypeId,
            "inspectionTypeName" to inspection.inspectionTypeName,
            "assignedOfficerId" to inspection.assignedOfficerId,
            "assignedOfficerName" to inspection.assignedOfficerName,
            "locationId" to inspection.locationId,
            "locationName" to inspection.locationName,
            "status" to inspection.status.name,
            "isSpotInspection" to inspection.isSpotInspection,
            "inspectionCategory" to inspection.inspectionCategory,
            "scheduledDate" to inspection.scheduledDate,
            "executionDate" to inspection.executionDate,
            "startLatitude" to inspection.startLatitude,
            "startLongitude" to inspection.startLongitude,
            "startAccuracy" to inspection.startAccuracy,
            "selfieURL" to inspection.selfieURL,
            "remarks" to inspection.remarks,
            "responses" to inspection.responses,
            "createdAt" to inspection.createdAt,
            "updatedAt" to inspection.updatedAt,
            "startedAt" to inspection.startedAt,
            "completedAt" to inspection.completedAt
        )
        db?.collection("inspections")?.document(inspection.id)
            ?.set(data, SetOptions.merge())
            ?.addOnSuccessListener { Log.d(TAG, "Inspection ${inspection.id} synced to Firestore") }
            ?.addOnFailureListener { e -> Log.e(TAG, "Error syncing inspection: ${e.message}") }
    }

    fun deleteInspection(inspectionId: String) {
        db?.collection("inspections")?.document(inspectionId)?.delete()
    }

    fun syncCompliance(compliance: Compliance) {
        val data = hashMapOf(
            "id" to compliance.id,
            "inspectionId" to compliance.inspectionId,
            "inspectionNumber" to compliance.inspectionNumber,
            "inspectionPointTitle" to compliance.inspectionPointTitle,
            "title" to compliance.title,
            "description" to compliance.description,
            "locationName" to compliance.locationName,
            "inspectingOfficerId" to compliance.inspectingOfficerId,
            "inspectingOfficerName" to compliance.inspectingOfficerName,
            "assignedOfficerId" to compliance.assignedOfficerId,
            "assignedOfficerName" to compliance.assignedOfficerName,
            "severity" to compliance.severity.name,
            "status" to compliance.status.name,
            "targetDate" to compliance.targetDate,
            "actionTaken" to compliance.actionTaken,
            "complianceRemarks" to compliance.complianceRemarks,
            "compliancePhotoUri" to compliance.compliancePhotoUri,
            "closedAt" to compliance.closedAt,
            "createdAt" to compliance.createdAt,
            "updatedAt" to compliance.updatedAt
        )
        db?.collection("compliances")?.document(compliance.id)
            ?.set(data, SetOptions.merge())
    }

    fun deleteCompliance(complianceId: String) {
        db?.collection("compliances")?.document(complianceId)?.delete()
    }

    fun syncNotification(notification: AppNotification) {
        val data = hashMapOf(
            "id" to notification.id,
            "recipientOfficerId" to notification.recipientOfficerId,
            "recipientOfficerName" to notification.recipientOfficerName,
            "senderName" to notification.senderName,
            "title" to notification.title,
            "message" to notification.message,
            "type" to notification.type.name,
            "inspectionId" to notification.inspectionId,
            "inspectionNumber" to notification.inspectionNumber,
            "complianceId" to notification.complianceId,
            "deepLinkRoute" to notification.deepLinkRoute,
            "isRead" to notification.isRead,
            "createdAt" to notification.createdAt
        )
        db?.collection("notifications")?.document(notification.id)
            ?.set(data, SetOptions.merge())
    }

    fun deleteNotification(notificationId: String) {
        db?.collection("notifications")?.document(notificationId)?.delete()
    }

    // --- Bulk Sync from Room to Cloud Firestore ---

    fun syncAllToCloud(onComplete: ((Boolean, String) -> Unit)? = null) {
        if (!isFirebaseAvailable) {
            _syncStatus.value = "Local storage mode (Cloud sync unconfigured)"
            onComplete?.invoke(false, "Firebase not configured")
            return
        }
        _isSyncing.value = true
        _syncStatus.value = "Syncing with Cloud Firestore..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dbInstance = MyApplication.instance.database

                // 1. Users
                val users = dbInstance.userDao().getAllUsersList()
                users.forEach { syncUser(it) }

                // 2. Master Data
                val stations = dbInstance.masterDataDao().getAllStationsList()
                stations.forEach { syncStation(it) }

                val sections = dbInstance.masterDataDao().getAllSectionsList()
                sections.forEach { syncSection(it) }

                val gates = dbInstance.masterDataDao().getAllLCGatesList()
                gates.forEach { syncLCGate(it) }

                // 3. Inspection Types & Fields
                val inspectionTypes = dbInstance.inspectionDao().getAllInspectionTypesList()
                inspectionTypes.forEach { syncInspectionType(it) }

                val fields = dbInstance.inspectionDao().getAllInspectionFieldsList()
                fields.forEach { syncInspectionField(it) }

                // 4. Inspections
                val inspections = dbInstance.inspectionDao().getAllInspectionsList()
                inspections.forEach { syncInspection(it) }

                // 5. Compliances
                val compliances = dbInstance.complianceDao().getAllCompliancesList()
                compliances.forEach { syncCompliance(it) }

                _syncStatus.value = "Synced with Cloud Firestore (${users.size} users, ${inspectionTypes.size} forms, ${inspections.size} inspections)"
                _isSyncing.value = false
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onComplete?.invoke(true, "Successfully synced ${users.size} officers, ${inspectionTypes.size} inspection types, and ${inspections.size} inspections to Firebase.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error performing full sync: ${e.message}", e)
                _syncStatus.value = "Sync error: ${e.localizedMessage ?: "Unknown error"}"
                _isSyncing.value = false
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onComplete?.invoke(false, e.localizedMessage ?: "Sync error")
                }
            }
        }
    }

    // --- Realtime Listeners from Cloud Firestore to Room ---

    private fun startRealtimeListeners() {
        val currentDb = db ?: return
        val dbInstance = MyApplication.instance.database

        // Listen for users
        currentDb.collection("users").addSnapshotListener { snapshots, error ->
            if (error != null) {
                Log.e(TAG, "Users listener error: ${error.message}")
                return@addSnapshotListener
            }
            snapshots?.let { querySnapshot ->
                CoroutineScope(Dispatchers.IO).launch {
                    for (doc in querySnapshot.documents) {
                        try {
                            val roleStr = doc.getString("role") ?: Role.OFFICER.name
                            val user = User(
                                uid = doc.getString("uid") ?: doc.id,
                                hrmsId = doc.getString("hrmsId") ?: "",
                                name = doc.getString("name") ?: "",
                                designation = doc.getString("designation") ?: "",
                                departmentId = doc.getString("departmentId") ?: "",
                                departmentName = doc.getString("departmentName") ?: "",
                                mobile = doc.getString("mobile") ?: "",
                                email = doc.getString("email") ?: "",
                                role = try { Role.valueOf(roleStr) } catch (e: Exception) { Role.OFFICER },
                                division = doc.getString("division") ?: "",
                                office = doc.getString("office") ?: "",
                                photoURL = doc.getString("photoURL") ?: "",
                                status = doc.getString("status") ?: "ACTIVE",
                                createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                                updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                            )
                            dbInstance.userDao().insertUser(user)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing user doc ${doc.id}: ${e.message}")
                        }
                    }
                }
            }
        }

        // Listen for inspection types
        currentDb.collection("inspection_types").addSnapshotListener { snapshots, error ->
            if (error != null) {
                Log.e(TAG, "Inspection types listener error: ${error.message}")
                return@addSnapshotListener
            }
            snapshots?.let { querySnapshot ->
                CoroutineScope(Dispatchers.IO).launch {
                    for (doc in querySnapshot.documents) {
                        try {
                            val catStr = doc.getString("category") ?: InspectionCategory.SCHEDULED.name
                            val type = InspectionType(
                                id = doc.getString("id") ?: doc.id,
                                name = doc.getString("name") ?: "",
                                category = try { InspectionCategory.valueOf(catStr) } catch (e: Exception) { InspectionCategory.SCHEDULED },
                                description = doc.getString("description") ?: "",
                                active = doc.getBoolean("active") ?: true,
                                createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                                createdBy = doc.getString("createdBy") ?: ""
                            )
                            dbInstance.inspectionDao().insertInspectionType(type)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing inspection type doc ${doc.id}: ${e.message}")
                        }
                    }
                }
            }
        }

        // Listen for inspection fields
        currentDb.collection("inspection_fields").addSnapshotListener { snapshots, error ->
            if (error != null) return@addSnapshotListener
            snapshots?.let { querySnapshot ->
                CoroutineScope(Dispatchers.IO).launch {
                    for (doc in querySnapshot.documents) {
                        try {
                            val fieldTypeStr = doc.getString("fieldType") ?: FieldType.TEXT.name
                            val field = InspectionField(
                                id = doc.getString("id") ?: doc.id,
                                inspectionTypeId = doc.getString("inspectionTypeId") ?: "",
                                label = doc.getString("label") ?: "",
                                fieldName = doc.getString("fieldName") ?: "",
                                fieldType = try { FieldType.valueOf(fieldTypeStr) } catch (e: Exception) { FieldType.TEXT },
                                required = doc.getBoolean("required") ?: false,
                                options = doc.getString("options") ?: "",
                                placeholder = doc.getString("placeholder") ?: "",
                                helpText = doc.getString("helpText") ?: "",
                                displayOrder = doc.getLong("displayOrder")?.toInt() ?: 0,
                                active = doc.getBoolean("active") ?: true,
                                createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                                updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                            )
                            dbInstance.inspectionDao().insertInspectionField(field)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing inspection field doc ${doc.id}: ${e.message}")
                        }
                    }
                }
            }
        }

        // Listen for inspections
        currentDb.collection("inspections").addSnapshotListener { snapshots, error ->
            if (error != null) return@addSnapshotListener
            snapshots?.let { querySnapshot ->
                CoroutineScope(Dispatchers.IO).launch {
                    for (doc in querySnapshot.documents) {
                        try {
                            val statusStr = doc.getString("status") ?: InspectionStatus.ASSIGNED.name
                            val inspection = Inspection(
                                id = doc.getString("id") ?: doc.id,
                                inspectionNumber = doc.getString("inspectionNumber") ?: "",
                                inspectionTypeId = doc.getString("inspectionTypeId") ?: "",
                                inspectionTypeName = doc.getString("inspectionTypeName") ?: "",
                                assignedOfficerId = doc.getString("assignedOfficerId") ?: "",
                                assignedOfficerName = doc.getString("assignedOfficerName") ?: "",
                                locationId = doc.getString("locationId") ?: "",
                                locationName = doc.getString("locationName") ?: "",
                                status = try { InspectionStatus.valueOf(statusStr) } catch (e: Exception) { InspectionStatus.ASSIGNED },
                                isSpotInspection = doc.getBoolean("isSpotInspection") ?: false,
                                inspectionCategory = doc.getString("inspectionCategory") ?: "ASSIGNED",
                                scheduledDate = doc.getLong("scheduledDate") ?: 0L,
                                executionDate = doc.getLong("executionDate"),
                                startLatitude = doc.getDouble("startLatitude"),
                                startLongitude = doc.getDouble("startLongitude"),
                                startAccuracy = doc.getDouble("startAccuracy")?.toFloat(),
                                selfieURL = doc.getString("selfieURL") ?: "",
                                remarks = doc.getString("remarks") ?: "",
                                responses = doc.getString("responses") ?: "{}",
                                createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                                updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
                                startedAt = doc.getLong("startedAt"),
                                completedAt = doc.getLong("completedAt")
                            )
                            dbInstance.inspectionDao().insertInspection(inspection)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing inspection doc ${doc.id}: ${e.message}")
                        }
                    }
                }
            }
        }

        // Listen for compliances
        currentDb.collection("compliances").addSnapshotListener { snapshots, error ->
            if (error != null) return@addSnapshotListener
            snapshots?.let { querySnapshot ->
                CoroutineScope(Dispatchers.IO).launch {
                    for (doc in querySnapshot.documents) {
                        try {
                            val severityStr = doc.getString("severity") ?: ComplianceSeverity.MAJOR.name
                            val statusStr = doc.getString("status") ?: ComplianceStatus.OPEN.name
                            val compliance = Compliance(
                                id = doc.getString("id") ?: doc.id,
                                inspectionId = doc.getString("inspectionId") ?: "",
                                inspectionNumber = doc.getString("inspectionNumber") ?: "",
                                inspectionPointTitle = doc.getString("inspectionPointTitle") ?: "",
                                title = doc.getString("title") ?: "",
                                description = doc.getString("description") ?: "",
                                locationName = doc.getString("locationName") ?: "",
                                inspectingOfficerId = doc.getString("inspectingOfficerId") ?: "",
                                inspectingOfficerName = doc.getString("inspectingOfficerName") ?: "",
                                assignedOfficerId = doc.getString("assignedOfficerId") ?: "",
                                assignedOfficerName = doc.getString("assignedOfficerName") ?: "",
                                severity = try { ComplianceSeverity.valueOf(severityStr) } catch (e: Exception) { ComplianceSeverity.MAJOR },
                                status = try { ComplianceStatus.valueOf(statusStr) } catch (e: Exception) { ComplianceStatus.OPEN },
                                targetDate = doc.getLong("targetDate") ?: System.currentTimeMillis(),
                                actionTaken = doc.getString("actionTaken") ?: "",
                                complianceRemarks = doc.getString("complianceRemarks") ?: "",
                                compliancePhotoUri = doc.getString("compliancePhotoUri") ?: "",
                                closedAt = doc.getLong("closedAt"),
                                createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                                updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                            )
                            dbInstance.complianceDao().insertCompliance(compliance)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing compliance doc ${doc.id}: ${e.message}")
                        }
                    }
                }
            }
        }
    }
}
