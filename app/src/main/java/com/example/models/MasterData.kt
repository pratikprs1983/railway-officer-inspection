package com.example.models
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "stations")
data class Station(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val stationName: String = "",
    val stationCode: String = "",
    val section: String = "",
    val division: String = "",
    val active: Boolean = true
)
@Entity(tableName = "sections")
data class Section(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val sectionName: String = "",
    val fromLocation: String = "",
    val toLocation: String = "",
    val active: Boolean = true
)
@Entity(tableName = "lc_gates")
data class LCGate(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val lcNumber: String = "",
    val location: String = "",
    val section: String = "",
    val gateType: String = "",
    val active: Boolean = true
)
enum class InspectionCategory { SCHEDULED, SPOT }
@Entity(tableName = "inspection_types")
data class InspectionType(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val category: InspectionCategory = InspectionCategory.SCHEDULED,
    val description: String = "",
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val createdBy: String = ""
)
enum class FieldType {
    TEXT, TEXTAREA, NUMBER, DATE, TIME, YES_NO, DROPDOWN, MULTI_SELECT, PHOTO, VIDEO, PDF, DOCUMENT, GPS, SELFIE, SIGNATURE
}
@Entity(tableName = "inspection_fields")
data class InspectionField(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val inspectionTypeId: String = "",
    val label: String = "",
    val fieldName: String = "",
    val fieldType: FieldType = FieldType.TEXT,
    val required: Boolean = false,
    val options: String = "", // Changed to String to store comma separated options for Room
    val placeholder: String = "",
    val helpText: String = "",
    val displayOrder: Int = 0,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun getOptionsList(): List<String> = if (options.isEmpty()) emptyList() else options.split(",")
    fun setOptionsList(list: List<String>) {
        // Not a direct setter for data class but we can just use options in constructor
    }
}
