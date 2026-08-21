package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class SyncStatus {
    PENDING,
    SYNCING,
    SYNCED,
    FAILED
}

@Entity(tableName = "partners")
data class PartnerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val businessId: String = "",
    val name: String,
    val phone: String,
    val role: String = "Partner", // "Owner", "Partner"
    val avatarColorHex: String = "#1E4D2B",
    val photoUri: String = "",
    val isCurrentActive: Boolean = false,
    val createdBy: String = "",
    val deviceId: String = "",
    val version: Long = 1L,
    val isSynced: Boolean = false,
    val syncStatus: String = SyncStatus.PENDING.name,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "tractors")
data class TractorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val businessId: String = "",
    val label: String, // e.g. "Mahindra 575 DI"
    val chassisNo: String, // e.g. "MH-575-TN45-9871"
    val modelYear: String = "",
    val operatorName: String = "",
    val isActive: Boolean = true,
    val createdBy: String = "",
    val deviceId: String = "",
    val version: Long = 1L,
    val isSynced: Boolean = false,
    val syncStatus: String = SyncStatus.PENDING.name,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val businessId: String = "",
    val name: String,
    val phone: String,
    val location: String = "",
    val totalBilled: Double = 0.0,
    val totalPaid: Double = 0.0,
    val balanceDue: Double = 0.0,
    val createdBy: String = "",
    val deviceId: String = "",
    val version: Long = 1L,
    val isSynced: Boolean = false,
    val syncStatus: String = SyncStatus.PENDING.name,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "job_entries")
data class JobEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val businessId: String = "",
    val customerId: Long = 0,
    val customerUuid: String = "",
    val customerName: String,
    val customerPhone: String = "",
    val customerLocation: String = "",
    val operatorName: String,
    val tractorId: Long = 0,
    val tractorUuid: String = "",
    val tractorLabel: String,
    val workType: String, // "Ploughing", "Rotavator", "Cultivator", "Harvester", "Leveler", "Trailer", "Other"
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val durationMinutes: Long,
    val hourlyRate: Double = 1100.0,
    val totalAmount: Double,
    val amountReceived: Double,
    val pendingAmount: Double,
    val addedByPartner: String,
    val notes: String = "",
    val createdBy: String = addedByPartner,
    val deviceId: String = "",
    val version: Long = 1L,
    val isSynced: Boolean = false,
    val syncStatus: String = SyncStatus.PENDING.name,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val businessId: String = "",
    val expenseType: String, // "Diesel", "Petrol", "Repair", "Puncture", "Oil Change", "Driver Bata", "Spare Parts", "Other"
    val amount: Double,
    val tractorId: Long = 0,
    val tractorUuid: String = "",
    val tractorLabel: String,
    val operatorName: String = "",
    val description: String = "",
    val addedByPartner: String,
    val dateTimestamp: Long = System.currentTimeMillis(),
    val relatedJobId: Long? = null,
    val relatedJobUuid: String = "",
    val createdBy: String = addedByPartner,
    val deviceId: String = "",
    val version: Long = 1L,
    val isSynced: Boolean = false,
    val syncStatus: String = SyncStatus.PENDING.name,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "withdrawals")
data class WithdrawalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val businessId: String = "",
    val partnerId: Long = 0,
    val partnerUuid: String = "",
    val partnerName: String,
    val amount: Double,
    val category: String = "Personal Use", // "Personal Use", "Fuel Advance", "Salary", "Profit Share", "Emergency", "Other"
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val createdBy: String = partnerName,
    val deviceId: String = "",
    val version: Long = 1L,
    val isSynced: Boolean = false,
    val syncStatus: String = SyncStatus.PENDING.name,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val businessId: String = "AIDHUNT-TRAC-SHARED-01",
    val businessName: String = "AIDHUNT Agri & Tractor Services",
    val ownerName: String = "Muthu & Partners",
    val businessPhone: String = "+91 98421 54321",
    val businessAddress: String = "Tiruchirappalli, Tamil Nadu",
    val gstNumber: String = "Hzhsg",
    val defaultHourlyRate: Double = 1100.0,
    val currency: String = "₹",
    val language: String = "EN", // "EN", "TA" (Tamil)
    val sharedAccountId: String = "AIDHUNT-TRAC-SHARED-01",
    val isLoggedIn: Boolean = false,
    val activePartnerName: String = "Muthu (Owner)",
    val activePartnerPhone: String = "+91 98421 54321",
    val profilePhotoUri: String = "",
    val lockedTractorLabel: String = "",
    val deviceId: String = "",
    val lastSyncTime: Long = System.currentTimeMillis()
)
