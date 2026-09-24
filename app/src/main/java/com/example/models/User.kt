package com.example.models
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class Role {
    SUPER_ADMIN,
    ADMIN,
    OFFICER
}
@Entity(tableName = "users")
data class User(
    @PrimaryKey val uid: String = java.util.UUID.randomUUID().toString(),
    val hrmsId: String = "",
    val name: String = "",
    val designation: String = "",
    val departmentId: String = "",
    val departmentName: String = "",
    val mobile: String = "",
    val email: String = "",
    val role: Role = Role.OFFICER,
    val division: String = "",
    val office: String = "",
    val photoURL: String = "",
    val status: String = "ACTIVE",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
