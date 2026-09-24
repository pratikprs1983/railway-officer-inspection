package com.example.ui.admin

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FirebaseSyncManager
import com.example.data.UserRepository
import com.example.models.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class UserViewModel : ViewModel() {
    private val repository = UserRepository()

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val syncStatus = FirebaseSyncManager.syncStatus
    val isSyncing = FirebaseSyncManager.isSyncing
    val isPlaceholderConfig: Boolean get() = FirebaseSyncManager.isPlaceholderConfig
    val currentProjectId: String get() = FirebaseSyncManager.currentProjectId

    init {
        fetchUsers()
    }

    private fun fetchUsers() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getUsers()
                .catch { e ->
                    _error.value = e.message
                    _isLoading.value = false
                }
                .collect { userList ->
                    _users.value = userList
                    _isLoading.value = false
                }
        }
    }

    fun addUser(user: User, onResult: ((Boolean, String) -> Unit)? = null) {
        _isLoading.value = true
        repository.addUser(user) { success, exception ->
            _isLoading.value = false
            if (!success) {
                _error.value = exception?.message ?: "Failed to save user"
                onResult?.invoke(false, exception?.message ?: "Failed to save user")
            } else {
                if (FirebaseSyncManager.isPlaceholderConfig) {
                    onResult?.invoke(true, "Saved to local device storage. (Firebase disconnected: placeholder config)")
                } else {
                    onResult?.invoke(true, "User saved and synced to Firebase Firestore!")
                }
            }
        }
    }
    
    fun deleteUser(user: User, onResult: ((Boolean, String) -> Unit)? = null) {
        _isLoading.value = true
        repository.deleteUser(user) { success, exception ->
            _isLoading.value = false
            if (!success) {
                _error.value = exception?.message ?: "Failed to delete user"
                onResult?.invoke(false, exception?.message ?: "Failed to delete user")
            } else {
                onResult?.invoke(true, "User deleted.")
            }
        }
    }

    fun syncAllUsers(onComplete: (Boolean, String) -> Unit) {
        FirebaseSyncManager.syncAllUsers(onComplete)
    }

    fun applyGoogleServicesJson(context: Context, json: String): Result<String> {
        return FirebaseSyncManager.applyGoogleServicesJson(context, json)
    }
}
