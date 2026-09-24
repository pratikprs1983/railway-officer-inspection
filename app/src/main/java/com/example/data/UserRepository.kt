package com.example.data
import com.example.MyApplication
import com.example.models.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class UserRepository {
    private val userDao = MyApplication.instance.database.userDao()

    fun getUsers(): Flow<List<User>> {
        return userDao.getAllUsers()
    }

    fun addUser(user: User, onComplete: (Boolean, Exception?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                userDao.insertUser(user)
                FirebaseSyncManager.syncUser(user)
                onComplete(true, null)
            } catch (e: Exception) {
                onComplete(false, e)
            }
        }
    }

    fun deleteUser(user: User, onComplete: (Boolean, Exception?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                userDao.deleteUser(user)
                FirebaseSyncManager.deleteUser(user.uid)
                onComplete(true, null)
            } catch (e: Exception) {
                onComplete(false, e)
            }
        }
    }

    suspend fun getUserByHrmsOrMobile(hrmsIdOrMobile: String): User? {
        return userDao.getUserByHrmsOrMobile(hrmsIdOrMobile, hrmsIdOrMobile)
    }
}
