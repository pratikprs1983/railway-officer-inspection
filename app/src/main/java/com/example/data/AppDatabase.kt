package com.example.data
import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.models.Inspection
import com.example.models.InspectionField
import com.example.models.InspectionType
import com.example.models.LCGate
import com.example.models.Section
import com.example.models.Station
import com.example.models.User
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY createdAt DESC")
    fun getAllUsers(): Flow<List<User>>
    @Query("SELECT * FROM users")
    suspend fun getAllUsersList(): List<User>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)
    @androidx.room.Delete
    suspend fun deleteUser(user: User)
    @Query("SELECT * FROM users WHERE hrmsId = :hrmsId OR mobile = :mobile LIMIT 1")
    suspend fun getUserByHrmsOrMobile(hrmsId: String, mobile: String): User?
}

@Dao
interface InspectionDao {
    @Query("SELECT * FROM inspection_types ORDER BY createdAt DESC")
    fun getInspectionTypes(): Flow<List<InspectionType>>
    @Query("SELECT * FROM inspection_types")
    suspend fun getAllInspectionTypesList(): List<InspectionType>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInspectionType(type: InspectionType)
    @androidx.room.Delete
    suspend fun deleteInspectionType(type: InspectionType)
    
    @Query("SELECT * FROM inspection_fields WHERE inspectionTypeId = :typeId ORDER BY displayOrder ASC")
    fun getInspectionFields(typeId: String): Flow<List<InspectionField>>
    @Query("SELECT * FROM inspection_fields")
    suspend fun getAllInspectionFieldsList(): List<InspectionField>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInspectionField(field: InspectionField)
    @androidx.room.Delete
    suspend fun deleteInspectionField(field: InspectionField)
    
    @Query("SELECT * FROM inspections ORDER BY createdAt DESC")
    fun getInspections(): Flow<List<Inspection>>
    @Query("SELECT * FROM inspections")
    suspend fun getAllInspectionsList(): List<Inspection>
    
    @Query("SELECT * FROM inspections WHERE id = :id LIMIT 1")
    fun getInspectionById(id: String): Flow<Inspection?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInspection(inspection: Inspection)
    @androidx.room.Delete
    suspend fun deleteInspection(inspection: Inspection)
}

@Dao
interface ComplianceDao {
    @Query("SELECT * FROM compliances ORDER BY createdAt DESC")
    fun getAllCompliances(): Flow<List<com.example.models.Compliance>>
    @Query("SELECT * FROM compliances")
    suspend fun getAllCompliancesList(): List<com.example.models.Compliance>

    @Query("SELECT * FROM compliances WHERE inspectionId = :inspectionId ORDER BY createdAt DESC")
    fun getCompliancesByInspection(inspectionId: String): Flow<List<com.example.models.Compliance>>

    @Query("SELECT * FROM compliances WHERE id = :id LIMIT 1")
    suspend fun getComplianceById(id: String): com.example.models.Compliance?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompliance(compliance: com.example.models.Compliance)

    @androidx.room.Delete
    suspend fun deleteCompliance(compliance: com.example.models.Compliance)
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY createdAt DESC")
    fun getAllNotifications(): Flow<List<com.example.models.AppNotification>>

    @Query("SELECT * FROM notifications WHERE recipientOfficerId = :officerId OR recipientOfficerId = 'ALL' OR recipientOfficerId = 'ADMIN' OR recipientOfficerId = '' ORDER BY createdAt DESC")
    fun getNotificationsForOfficer(officerId: String): Flow<List<com.example.models.AppNotification>>

    @Query("SELECT * FROM notifications WHERE id = :id LIMIT 1")
    suspend fun getNotificationById(id: String): com.example.models.AppNotification?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: com.example.models.AppNotification)

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: String)

    @Query("UPDATE notifications SET isRead = 1 WHERE recipientOfficerId = :officerId OR recipientOfficerId = 'ALL' OR recipientOfficerId = ''")
    suspend fun markAllAsRead(officerId: String)

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteNotification(id: String)

    @Query("DELETE FROM notifications")
    suspend fun clearAll()
}

@Database(
    entities = [
        User::class,
        Station::class,
        Section::class,
        LCGate::class,
        InspectionType::class,
        InspectionField::class,
        Inspection::class,
        com.example.models.Compliance::class,
        com.example.models.AppNotification::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun inspectionDao(): InspectionDao
    abstract fun masterDataDao(): MasterDataDao
    abstract fun complianceDao(): ComplianceDao
    abstract fun notificationDao(): NotificationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
