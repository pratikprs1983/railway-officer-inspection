package com.example.data
import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.models.FormCategory
import com.example.models.FormSubcategory
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
    
    // Categories
    @Query("SELECT * FROM form_categories WHERE inspectionTypeId = :typeId ORDER BY displayOrder ASC, createdAt ASC")
    fun getCategoriesForType(typeId: String): Flow<List<FormCategory>>
    @Query("SELECT * FROM form_categories")
    suspend fun getAllCategoriesList(): List<FormCategory>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: FormCategory)
    @androidx.room.Delete
    suspend fun deleteCategory(category: FormCategory)
    @Query("DELETE FROM form_categories WHERE id = :categoryId")
    suspend fun deleteCategoryById(categoryId: String)

    // Subcategories
    @Query("SELECT * FROM form_subcategories WHERE inspectionTypeId = :typeId ORDER BY displayOrder ASC, createdAt ASC")
    fun getSubcategoriesForType(typeId: String): Flow<List<FormSubcategory>>
    @Query("SELECT * FROM form_subcategories WHERE categoryId = :categoryId ORDER BY displayOrder ASC, createdAt ASC")
    fun getSubcategoriesForCategory(categoryId: String): Flow<List<FormSubcategory>>
    @Query("SELECT * FROM form_subcategories")
    suspend fun getAllSubcategoriesList(): List<FormSubcategory>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubcategory(subcategory: FormSubcategory)
    @androidx.room.Delete
    suspend fun deleteSubcategory(subcategory: FormSubcategory)
    @Query("DELETE FROM form_subcategories WHERE id = :subcategoryId")
    suspend fun deleteSubcategoryById(subcategoryId: String)
    @Query("DELETE FROM form_subcategories WHERE categoryId = :categoryId")
    suspend fun deleteSubcategoriesByCategoryId(categoryId: String)
    @Query("DELETE FROM form_subcategories WHERE inspectionTypeId = :typeId AND categoryId != '' AND categoryId NOT IN (SELECT id FROM form_categories WHERE inspectionTypeId = :typeId)")
    suspend fun purgeOrphanedSubcategories(typeId: String)

    // Fields
    @Query("SELECT * FROM inspection_fields WHERE inspectionTypeId = :typeId ORDER BY displayOrder ASC")
    fun getInspectionFields(typeId: String): Flow<List<InspectionField>>
    @Query("SELECT * FROM inspection_fields WHERE inspectionTypeId = :typeId ORDER BY displayOrder ASC")
    fun getInspectionFieldsListSync(typeId: String): List<InspectionField>
    @Query("SELECT * FROM inspection_fields")
    suspend fun getAllInspectionFieldsList(): List<InspectionField>
    @Query("SELECT * FROM inspection_fields ORDER BY displayOrder ASC")
    fun getAllInspectionFieldsListSync(): List<InspectionField>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInspectionField(field: InspectionField)
    @androidx.room.Delete
    suspend fun deleteInspectionField(field: InspectionField)
    @Query("DELETE FROM inspection_fields WHERE id = :fieldId")
    suspend fun deleteFieldById(fieldId: String)
    @Query("DELETE FROM inspection_fields WHERE categoryId = :categoryId")
    suspend fun deleteFieldsByCategoryId(categoryId: String)
    @Query("DELETE FROM inspection_fields WHERE subcategoryId = :subcategoryId")
    suspend fun deleteFieldsBySubcategoryId(subcategoryId: String)
    @Query("DELETE FROM inspection_fields WHERE subcategoryId = :subcategoryId OR (categoryId = :categoryId AND LOWER(subcategoryName) = LOWER(:subcategoryName))")
    suspend fun deleteFieldsBySubcategory(subcategoryId: String, categoryId: String, subcategoryName: String)
    @Query("DELETE FROM inspection_fields WHERE inspectionTypeId = :typeId AND (categoryId != '' AND categoryId NOT IN (SELECT id FROM form_categories WHERE inspectionTypeId = :typeId))")
    suspend fun purgeOrphanedCategoryFields(typeId: String)
    @Query("DELETE FROM inspection_fields WHERE inspectionTypeId = :typeId AND (subcategoryId != '' AND subcategoryId NOT IN (SELECT id FROM form_subcategories WHERE inspectionTypeId = :typeId))")
    suspend fun purgeOrphanedSubcategoryFields(typeId: String)
    
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

    @Query("DELETE FROM inspections WHERE id = :id")
    suspend fun deleteInspectionById(id: String)
}

@Dao
interface ComplianceDao {
    @Query("SELECT * FROM compliances ORDER BY createdAt DESC")
    fun getAllCompliances(): Flow<List<com.example.models.Compliance>>
    @Query("SELECT * FROM compliances")
    suspend fun getAllCompliancesList(): List<com.example.models.Compliance>

    @Query("SELECT * FROM compliances WHERE (:inspectionId != '' AND (inspectionId = :inspectionId OR inspectionNumber = :inspectionId)) ORDER BY createdAt DESC")
    fun getCompliancesByInspection(inspectionId: String): Flow<List<com.example.models.Compliance>>

    @Query("SELECT * FROM compliances WHERE id = :id LIMIT 1")
    suspend fun getComplianceById(id: String): com.example.models.Compliance?

    @Query("SELECT * FROM compliances WHERE (:inspectionId != '' AND inspectionId = :inspectionId) OR (:inspectionNumber != '' AND inspectionNumber = :inspectionNumber)")
    suspend fun getCompliancesForInspectionList(inspectionId: String, inspectionNumber: String): List<com.example.models.Compliance>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompliance(compliance: com.example.models.Compliance)

    @androidx.room.Delete
    suspend fun deleteCompliance(compliance: com.example.models.Compliance)

    @Query("DELETE FROM compliances WHERE id = :id")
    suspend fun deleteComplianceById(id: String)

    @Query("DELETE FROM compliances WHERE (:inspectionId != '' AND inspectionId = :inspectionId) OR (:inspectionNumber != '' AND inspectionNumber = :inspectionNumber)")
    suspend fun deleteCompliancesForInspection(inspectionId: String, inspectionNumber: String)

    @Query("DELETE FROM compliances")
    suspend fun clearAllCompliances()
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY createdAt DESC")
    fun getAllNotifications(): Flow<List<com.example.models.AppNotification>>

    @Query("SELECT * FROM notifications")
    suspend fun getAllNotificationsList(): List<com.example.models.AppNotification>

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

    @Query("DELETE FROM notifications WHERE (:inspectionId != '' AND inspectionId = :inspectionId) OR (:inspectionNumber != '' AND inspectionNumber = :inspectionNumber)")
    suspend fun deleteNotificationsForInspection(inspectionId: String, inspectionNumber: String)

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
        FormCategory::class,
        FormSubcategory::class,
        InspectionField::class,
        Inspection::class,
        com.example.models.Compliance::class,
        com.example.models.AppNotification::class
    ],
    version = 10,
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

        private val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE inspections ADD COLUMN verifiedLocationName TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                .addMigrations(MIGRATION_9_10)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
