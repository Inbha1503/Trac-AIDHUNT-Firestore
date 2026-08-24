package com.example.data.firebase

import androidx.annotation.Keep
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.CustomerEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PartnerEntity
import com.example.data.entity.TractorEntity
import com.example.data.entity.WithdrawalEntity

@Keep
data class Workspace(
    val workspaceId: String = "",
    val name: String = "AIDHUNT Agri & Tractor Services",
    val ownerUid: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "workspaceId" to workspaceId,
        "name" to name,
        "ownerUid" to ownerUid,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): Workspace {
            return Workspace(
                workspaceId = map["workspaceId"] as? String ?: "",
                name = map["name"] as? String ?: "AIDHUNT Agri & Tractor Services",
                ownerUid = map["ownerUid"] as? String ?: "",
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }
    }
}

@Keep
data class WorkspaceMember(
    val uid: String = "",
    val role: String = "owner", // "owner", "partner"
    val status: String = "active", // "active", "invited"
    val joinedAt: Long = System.currentTimeMillis(),
    val displayName: String? = null,
    val email: String? = null,
    val phoneNumber: String? = null
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "uid" to uid,
        "role" to role,
        "status" to status,
        "joinedAt" to joinedAt,
        "displayName" to displayName,
        "email" to email,
        "phoneNumber" to phoneNumber
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): WorkspaceMember {
            return WorkspaceMember(
                uid = map["uid"] as? String ?: "",
                role = map["role"] as? String ?: "owner",
                status = map["status"] as? String ?: "active",
                joinedAt = (map["joinedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                displayName = map["displayName"] as? String,
                email = map["email"] as? String,
                phoneNumber = map["phoneNumber"] as? String
            )
        }
    }
}

// Extension functions for Document / Map mapping

fun JobEntryEntity.toFirestoreMap(createdByUid: String? = null): Map<String, Any?> = mapOf(
    "id" to id,
    "customerId" to customerId,
    "customerName" to customerName,
    "customerPhone" to customerPhone,
    "customerLocation" to customerLocation,
    "operatorName" to operatorName,
    "tractorId" to tractorId,
    "tractorLabel" to tractorLabel,
    "workType" to workType,
    "startTimeMillis" to startTimeMillis,
    "endTimeMillis" to endTimeMillis,
    "durationMinutes" to durationMinutes,
    "hourlyRate" to hourlyRate,
    "totalAmount" to totalAmount,
    "amountReceived" to amountReceived,
    "pendingAmount" to pendingAmount,
    "addedByPartner" to addedByPartner,
    "notes" to notes,
    "createdBy" to (createdByUid ?: addedByPartner),
    "createdAt" to (if (createdAt > 0) createdAt else System.currentTimeMillis()),
    "updatedAt" to System.currentTimeMillis()
)

fun jobEntryFromFirestoreMap(map: Map<String, Any?>, fallbackId: Long = 0): JobEntryEntity {
    val id = (map["id"] as? Number)?.toLong() ?: fallbackId
    return JobEntryEntity(
        id = id,
        customerId = (map["customerId"] as? Number)?.toLong() ?: 0L,
        customerName = map["customerName"] as? String ?: "",
        customerPhone = map["customerPhone"] as? String ?: "",
        customerLocation = map["customerLocation"] as? String ?: "",
        operatorName = map["operatorName"] as? String ?: "Partner",
        tractorId = (map["tractorId"] as? Number)?.toLong() ?: 0L,
        tractorLabel = map["tractorLabel"] as? String ?: "",
        workType = map["workType"] as? String ?: "Ploughing",
        startTimeMillis = (map["startTimeMillis"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        endTimeMillis = (map["endTimeMillis"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        durationMinutes = (map["durationMinutes"] as? Number)?.toLong() ?: 0L,
        hourlyRate = (map["hourlyRate"] as? Number)?.toDouble() ?: 1100.0,
        totalAmount = (map["totalAmount"] as? Number)?.toDouble() ?: 0.0,
        amountReceived = (map["amountReceived"] as? Number)?.toDouble() ?: 0.0,
        pendingAmount = (map["pendingAmount"] as? Number)?.toDouble() ?: 0.0,
        addedByPartner = map["addedByPartner"] as? String ?: (map["createdBy"] as? String ?: "Partner"),
        notes = map["notes"] as? String ?: "",
        isSynced = true,
        createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
    )
}

fun ExpenseEntity.toFirestoreMap(createdByUid: String? = null): Map<String, Any?> = mapOf(
    "id" to id,
    "expenseType" to expenseType,
    "amount" to amount,
    "tractorId" to tractorId,
    "tractorLabel" to tractorLabel,
    "operatorName" to operatorName,
    "description" to description,
    "addedByPartner" to addedByPartner,
    "dateTimestamp" to dateTimestamp,
    "relatedJobId" to relatedJobId,
    "createdBy" to (createdByUid ?: addedByPartner),
    "createdAt" to (if (createdAt > 0) createdAt else System.currentTimeMillis()),
    "updatedAt" to System.currentTimeMillis()
)

fun expenseFromFirestoreMap(map: Map<String, Any?>, fallbackId: Long = 0): ExpenseEntity {
    val id = (map["id"] as? Number)?.toLong() ?: fallbackId
    return ExpenseEntity(
        id = id,
        expenseType = map["expenseType"] as? String ?: "Diesel",
        amount = (map["amount"] as? Number)?.toDouble() ?: 0.0,
        tractorId = (map["tractorId"] as? Number)?.toLong() ?: 0L,
        tractorLabel = map["tractorLabel"] as? String ?: "",
        operatorName = map["operatorName"] as? String ?: "",
        description = map["description"] as? String ?: "",
        addedByPartner = map["addedByPartner"] as? String ?: "Partner",
        dateTimestamp = (map["dateTimestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        relatedJobId = (map["relatedJobId"] as? Number)?.toLong(),
        isSynced = true,
        createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
    )
}

fun CustomerEntity.toFirestoreMap(createdByUid: String? = null): Map<String, Any?> = mapOf(
    "id" to id,
    "name" to name,
    "phone" to phone,
    "location" to location,
    "totalBilled" to totalBilled,
    "totalPaid" to totalPaid,
    "balanceDue" to balanceDue,
    "createdBy" to createdByUid,
    "createdAt" to (if (createdAt > 0) createdAt else System.currentTimeMillis()),
    "updatedAt" to updatedAt
)

fun customerFromFirestoreMap(map: Map<String, Any?>, fallbackId: Long = 0): CustomerEntity {
    val id = (map["id"] as? Number)?.toLong() ?: fallbackId
    return CustomerEntity(
        id = id,
        name = map["name"] as? String ?: "",
        phone = map["phone"] as? String ?: "",
        location = map["location"] as? String ?: "",
        totalBilled = (map["totalBilled"] as? Number)?.toDouble() ?: 0.0,
        totalPaid = (map["totalPaid"] as? Number)?.toDouble() ?: 0.0,
        balanceDue = (map["balanceDue"] as? Number)?.toDouble() ?: 0.0,
        isSynced = true,
        createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
    )
}

fun TractorEntity.toFirestoreMap(createdByUid: String? = null): Map<String, Any?> = mapOf(
    "id" to id,
    "label" to label,
    "chassisNo" to chassisNo,
    "modelYear" to modelYear,
    "operatorName" to operatorName,
    "isActive" to isActive,
    "createdBy" to createdByUid,
    "createdAt" to (if (createdAt > 0) createdAt else System.currentTimeMillis()),
    "updatedAt" to System.currentTimeMillis()
)

fun tractorFromFirestoreMap(map: Map<String, Any?>, fallbackId: Long = 0): TractorEntity {
    val id = (map["id"] as? Number)?.toLong() ?: fallbackId
    return TractorEntity(
        id = id,
        label = map["label"] as? String ?: "Tractor",
        chassisNo = map["chassisNo"] as? String ?: "",
        modelYear = map["modelYear"] as? String ?: "",
        operatorName = map["operatorName"] as? String ?: "",
        isActive = map["isActive"] as? Boolean ?: true,
        createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
    )
}

fun PartnerEntity.toFirestoreMap(createdByUid: String? = null): Map<String, Any?> = mapOf(
    "id" to id,
    "name" to name,
    "phone" to phone,
    "role" to role,
    "avatarColorHex" to avatarColorHex,
    "photoUri" to photoUri,
    "isCurrentActive" to isCurrentActive,
    "createdBy" to createdByUid,
    "createdAt" to (if (createdAt > 0) createdAt else System.currentTimeMillis()),
    "updatedAt" to System.currentTimeMillis()
)

fun partnerFromFirestoreMap(map: Map<String, Any?>, fallbackId: Long = 0): PartnerEntity {
    val id = (map["id"] as? Number)?.toLong() ?: fallbackId
    return PartnerEntity(
        id = id,
        name = map["name"] as? String ?: "Partner",
        phone = map["phone"] as? String ?: "",
        role = map["role"] as? String ?: "Partner",
        avatarColorHex = map["avatarColorHex"] as? String ?: "#1E4D2B",
        photoUri = map["photoUri"] as? String ?: "",
        isCurrentActive = map["isCurrentActive"] as? Boolean ?: false,
        createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
    )
}

fun WithdrawalEntity.toFirestoreMap(createdByUid: String? = null): Map<String, Any?> = mapOf(
    "id" to id,
    "partnerId" to partnerId,
    "partnerName" to partnerName,
    "amount" to amount,
    "category" to category,
    "note" to note,
    "timestamp" to timestamp,
    "createdBy" to createdByUid,
    "createdAt" to (if (createdAt > 0) createdAt else System.currentTimeMillis()),
    "updatedAt" to System.currentTimeMillis()
)

fun withdrawalFromFirestoreMap(map: Map<String, Any?>, fallbackId: Long = 0): WithdrawalEntity {
    val id = (map["id"] as? Number)?.toLong() ?: fallbackId
    return WithdrawalEntity(
        id = id,
        partnerId = (map["partnerId"] as? Number)?.toLong() ?: 0L,
        partnerName = map["partnerName"] as? String ?: "Partner",
        amount = (map["amount"] as? Number)?.toDouble() ?: 0.0,
        category = map["category"] as? String ?: "Personal Use",
        note = map["note"] as? String ?: "",
        timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        isSynced = true,
        createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
    )
}

fun AppSettingsEntity.toFirestoreMap(updatedByUid: String? = null): Map<String, Any?> = mapOf(
    "businessName" to businessName,
    "ownerName" to ownerName,
    "businessPhone" to businessPhone,
    "businessAddress" to businessAddress,
    "gstNumber" to gstNumber,
    "defaultHourlyRate" to defaultHourlyRate,
    "currency" to currency,
    "language" to language,
    "sharedAccountId" to sharedAccountId,
    "lockedTractorLabel" to lockedTractorLabel,
    "updatedBy" to updatedByUid,
    "updatedAt" to System.currentTimeMillis()
)

fun appSettingsFromFirestoreMap(map: Map<String, Any?>, currentSettings: AppSettingsEntity): AppSettingsEntity {
    return currentSettings.copy(
        businessName = map["businessName"] as? String ?: currentSettings.businessName,
        ownerName = map["ownerName"] as? String ?: currentSettings.ownerName,
        businessPhone = map["businessPhone"] as? String ?: currentSettings.businessPhone,
        businessAddress = map["businessAddress"] as? String ?: currentSettings.businessAddress,
        gstNumber = map["gstNumber"] as? String ?: currentSettings.gstNumber,
        defaultHourlyRate = (map["defaultHourlyRate"] as? Number)?.toDouble() ?: currentSettings.defaultHourlyRate,
        currency = map["currency"] as? String ?: currentSettings.currency,
        language = map["language"] as? String ?: currentSettings.language,
        sharedAccountId = map["sharedAccountId"] as? String ?: currentSettings.sharedAccountId,
        lockedTractorLabel = map["lockedTractorLabel"] as? String ?: currentSettings.lockedTractorLabel,
        lastSyncTime = (map["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
    )
}
