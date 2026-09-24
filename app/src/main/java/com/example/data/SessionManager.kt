package com.example.data

import android.content.Context
import com.example.MyApplication
import com.example.models.Role
import com.example.models.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

object SessionManager {
    private const val PREFS_NAME = "roicms_session_prefs"
    private const val KEY_USER_JSON = "key_user_json"
    private const val KEY_AUTH_USER_JSON = "key_auth_user_json"

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _authenticatedUser = MutableStateFlow<User?>(null)
    val authenticatedUser: StateFlow<User?> = _authenticatedUser.asStateFlow()

    init {
        loadSession()
    }

    private fun getPrefs() = MyApplication.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun login(user: User) {
        _authenticatedUser.value = user
        _currentUser.value = user
        saveUserToPrefs(KEY_AUTH_USER_JSON, user)
        saveUserToPrefs(KEY_USER_JSON, user)
    }

    fun logout() {
        _authenticatedUser.value = null
        _currentUser.value = null
        try {
            getPrefs().edit().clear().apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setCurrentUser(user: User) {
        _currentUser.value = user
        saveUserToPrefs(KEY_USER_JSON, user)
    }

    /**
     * Checks if the currently authenticated session or active user has Admin privileges.
     * Switch option and admin dashboard access are strictly reserved for ADMIN or SUPER_ADMIN.
     */
    fun isAdmin(): Boolean {
        val authRole = _authenticatedUser.value?.role
        val currRole = _currentUser.value?.role
        return authRole == Role.ADMIN || authRole == Role.SUPER_ADMIN ||
               currRole == Role.ADMIN || currRole == Role.SUPER_ADMIN
    }

    private fun saveUserToPrefs(key: String, user: User) {
        try {
            val json = JSONObject().apply {
                put("uid", user.uid)
                put("hrmsId", user.hrmsId)
                put("name", user.name)
                put("designation", user.designation)
                put("departmentId", user.departmentId)
                put("departmentName", user.departmentName)
                put("mobile", user.mobile)
                put("email", user.email)
                put("role", user.role.name)
                put("division", user.division)
                put("office", user.office)
                put("photoURL", user.photoURL)
                put("status", user.status)
            }
            getPrefs().edit().putString(key, json.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadSession() {
        try {
            val authJsonStr = getPrefs().getString(KEY_AUTH_USER_JSON, null)
            if (authJsonStr != null) {
                _authenticatedUser.value = parseUserJson(authJsonStr)
            }

            val userJsonStr = getPrefs().getString(KEY_USER_JSON, null)
            if (userJsonStr != null) {
                val loadedUser = parseUserJson(userJsonStr)
                _currentUser.value = loadedUser
                if (_authenticatedUser.value == null) {
                    _authenticatedUser.value = loadedUser
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseUserJson(jsonStr: String): User {
        val json = JSONObject(jsonStr)
        return User(
            uid = json.optString("uid"),
            hrmsId = json.optString("hrmsId"),
            name = json.optString("name"),
            designation = json.optString("designation"),
            departmentId = json.optString("departmentId"),
            departmentName = json.optString("departmentName"),
            mobile = json.optString("mobile"),
            email = json.optString("email"),
            role = try { Role.valueOf(json.optString("role", Role.OFFICER.name)) } catch (e: Exception) { Role.OFFICER },
            division = json.optString("division"),
            office = json.optString("office"),
            photoURL = json.optString("photoURL"),
            status = json.optString("status", "ACTIVE")
        )
    }

    /**
     * Checks if a given inspection is assigned to the specified user.
     * Matches strictly by assignedOfficerId or by exact assignedOfficerName (case-insensitive).
     */
    fun isInspectionAssignedToUser(inspection: com.example.models.Inspection, user: User?): Boolean {
        if (user == null) return false
        if (inspection.assignedOfficerId.isNotBlank() && user.uid.isNotBlank()) {
            return inspection.assignedOfficerId == user.uid
        }
        if (inspection.assignedOfficerName.isNotBlank() && user.name.isNotBlank()) {
            return inspection.assignedOfficerName.trim().equals(user.name.trim(), ignoreCase = true)
        }
        return false
    }
}
