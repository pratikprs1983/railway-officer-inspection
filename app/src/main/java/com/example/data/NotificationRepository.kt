package com.example.data

import com.example.MyApplication
import com.example.models.AppNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotificationRepository {
    private val notificationDao = MyApplication.instance.database.notificationDao()

    fun getAllNotifications(): Flow<List<AppNotification>> =
        notificationDao.getAllNotifications()

    fun getNotificationsForOfficer(officerId: String): Flow<List<AppNotification>> =
        notificationDao.getNotificationsForOfficer(officerId)

    fun saveNotification(notification: AppNotification, onComplete: ((Boolean) -> Unit)? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                notificationDao.insertNotification(notification)
                FirebaseSyncManager.syncNotification(notification)
                withContext(Dispatchers.Main) { onComplete?.invoke(true) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onComplete?.invoke(false) }
            }
        }
    }

    fun markAsRead(notificationId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                notificationDao.markAsRead(notificationId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun markAllAsRead(officerId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                notificationDao.markAllAsRead(officerId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteNotification(notificationId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                notificationDao.deleteNotification(notificationId)
                FirebaseSyncManager.deleteNotification(notificationId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearAll() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                notificationDao.clearAll()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
