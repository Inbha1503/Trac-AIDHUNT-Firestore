package com.example.data.firebase

import androidx.annotation.Keep
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.CustomerEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PartnerEntity
import com.example.data.entity.TractorEntity
import com.example.data.entity.WithdrawalEntity

/**
 * Standard phone normalization function used across Phone Directory registration,
 * Direct Partner Lookup, and Account management.
 * Examples:
 *   "8925624885" -> "+918925624885"
 *   "+918925624885" -> "+918925624885"
 *   "+91 89256-24885" -> "+918925624885"
 */
fun normalizePhoneNumber(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return ""
    val digits = trimmed.filter { it.isDigit() }
    if (trimmed.startsWith("+")) {
        return "+$digits"
    }
    val clean10 = digits.takeLast(10)
    return "+91$clean10"
}

@Keep
data class CollaborationGroup(
    val groupId: String = "",
    val ownerUid: String = "",
    val ownerWorkspaceId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "groupId" to groupId,
        "ownerUid" to ownerUid,
        "ownerWorkspaceId" to ownerWorkspaceId,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): CollaborationGroup {
            return CollaborationGroup(
                groupId = map["groupId"] as? String ?: "",
                ownerUid = map["ownerUid"] as? String ?: "",
                ownerWorkspaceId = map["ownerWorkspaceId"] as? String ?: "",
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }
    }
}

@Keep
data class CollaborationGroupMember(
    val uid: String = "",
    val workspaceId: String = "",
    val role: String = "partner", // "owner", "partner"
    val status: String = "active", // "active", "removed"
    val joinedAt: Long = System.currentTimeMillis(),
    val phoneNumber: String? = null,
    val displayName: String? = null
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "uid" to uid,
        "workspaceId" to workspaceId,
        "role" to role,
        "status" to status,
        "joinedAt" to joinedAt,
        "phoneNumber" to phoneNumber,
        "displayName" to displayName
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): CollaborationGroupMember {
            return CollaborationGroupMember(
                uid = map["uid"] as? String ?: "",
                workspaceId = map["workspaceId"] as? String ?: "",
                role = map["role"] as? String ?: "partner",
                status = map["status"] as? String ?: "active",
                joinedAt = (map["joinedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                phoneNumber = map["phoneNumber"] as? String,
                displayName = map["displayName"] as? String
            )
        }
    }
}

@Keep
data class UserCollaborationGroupIndex(
    val groupId: String = "",
    val ownerUid: String = "",
    val ownerWorkspaceId: String = "",
    val role: String = "partner",
    val status: String = "active",
    val joinedAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "groupId" to groupId,
        "ownerUid" to ownerUid,
        "ownerWorkspaceId" to ownerWorkspaceId,
        "role" to role,
        "status" to status,
        "joinedAt" to joinedAt
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): UserCollaborationGroupIndex {
            return UserCollaborationGroupIndex(
                groupId = map["groupId"] as? String ?: "",
                ownerUid = map["ownerUid"] as? String ?: "",
                ownerWorkspaceId = map["ownerWorkspaceId"] as? String ?: "",
                role = map["role"] as? String ?: "partner",
                status = map["status"] as? String ?: "active",
                joinedAt = (map["joinedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }
    }
}

@Keep
data class PendingPartnerPhone(
    val normalizedPhone: String = "",
    val displayName: String = "",
    val role: String = "partner",
    val addedByUid: String = "",
    val groupId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "waiting_for_registration"
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "normalizedPhone" to normalizedPhone,
        "displayName" to displayName,
        "role" to role,
        "addedByUid" to addedByUid,
        "groupId" to groupId,
        "createdAt" to createdAt,
        "status" to status
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): PendingPartnerPhone {
            return PendingPartnerPhone(
                normalizedPhone = map["normalizedPhone"] as? String ?: "",
                displayName = map["displayName"] as? String ?: "",
                role = map["role"] as? String ?: "partner",
                addedByUid = map["addedByUid"] as? String ?: "",
                groupId = map["groupId"] as? String ?: "",
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                status = map["status"] as? String ?: "waiting_for_registration"
            )
        }
    }
}

@Keep
data class Workspace(
    val workspaceId: String = "",
    val name: String = "",
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
                name = map["name"] as? String ?: "",
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
    val role: String = "partner", // "owner", "partner"
    val status: String = "active", // "active"
    val phoneNumber: String? = null,
    val joinedAt: Long = System.currentTimeMillis(),
    val addedByUid: String? = null,
    val invitedByUid: String? = null,
    val displayName: String? = null,
    val email: String? = null
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "uid" to uid,
        "role" to role,
        "status" to status,
        "phoneNumber" to phoneNumber,
        "joinedAt" to joinedAt,
        "addedByUid" to (addedByUid ?: invitedByUid),
        "invitedByUid" to (invitedByUid ?: addedByUid),
        "displayName" to displayName,
        "email" to email
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): WorkspaceMember {
            return WorkspaceMember(
                uid = map["uid"] as? String ?: "",
                role = map["role"] as? String ?: "partner",
                status = map["status"] as? String ?: "active",
                phoneNumber = map["phoneNumber"] as? String,
                joinedAt = (map["joinedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                addedByUid = map["addedByUid"] as? String ?: map["invitedByUid"] as? String,
                invitedByUid = map["invitedByUid"] as? String ?: map["addedByUid"] as? String,
                displayName = map["displayName"] as? String,
                email = map["email"] as? String
            )
        }
    }
}

@Keep
data class WorkspaceInvitation(
    val invitationId: String = "",
    val workspaceId: String = "",
    val workspaceName: String = "",
    val invitedByUid: String = "",
    val ownerUid: String = "",
    val ownerName: String = "",
    val ownerPhone: String = "",
    val invitedPhoneNumber: String = "",
    val inviteePhone: String = "",
    val inviteeName: String = "",
    val role: String = "partner",
    val status: String = "pending", // "pending", "accepted", "declined"
    val acceptedByUid: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "invitationId" to invitationId,
        "workspaceId" to workspaceId,
        "workspaceName" to workspaceName,
        "invitedByUid" to (invitedByUid.ifBlank { ownerUid }),
        "ownerUid" to (ownerUid.ifBlank { invitedByUid }),
        "ownerName" to ownerName,
        "ownerPhone" to ownerPhone,
        "invitedPhoneNumber" to (invitedPhoneNumber.ifBlank { inviteePhone }),
        "inviteePhone" to (inviteePhone.ifBlank { invitedPhoneNumber }),
        "inviteeName" to inviteeName,
        "role" to role,
        "status" to status,
        "acceptedByUid" to acceptedByUid,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): WorkspaceInvitation {
            val invBy = map["invitedByUid"] as? String ?: map["ownerUid"] as? String ?: ""
            val invPhone = map["invitedPhoneNumber"] as? String ?: map["inviteePhone"] as? String ?: ""
            return WorkspaceInvitation(
                invitationId = map["invitationId"] as? String ?: "",
                workspaceId = map["workspaceId"] as? String ?: "",
                workspaceName = map["workspaceName"] as? String ?: "",
                invitedByUid = invBy,
                ownerUid = map["ownerUid"] as? String ?: invBy,
                ownerName = map["ownerName"] as? String ?: "",
                ownerPhone = map["ownerPhone"] as? String ?: "",
                invitedPhoneNumber = invPhone,
                inviteePhone = map["inviteePhone"] as? String ?: invPhone,
                inviteeName = map["inviteeName"] as? String ?: "",
                role = map["role"] as? String ?: "partner",
                status = map["status"] as? String ?: "pending",
                acceptedByUid = map["acceptedByUid"] as? String,
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }
    }
}

@Keep
data class MemberMigration(
    val partnerUid: String = "",
    val sourceWorkspaceId: String = "",
    val destinationWorkspaceId: String = "",
    val status: String = "pending", // "pending", "in_progress", "completed", "failed"
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val recordsMigrated: Int = 0
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "partnerUid" to partnerUid,
        "sourceWorkspaceId" to sourceWorkspaceId,
        "destinationWorkspaceId" to destinationWorkspaceId,
        "status" to status,
        "startedAt" to startedAt,
        "completedAt" to completedAt,
        "recordsMigrated" to recordsMigrated
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): MemberMigration {
            return MemberMigration(
                partnerUid = map["partnerUid"] as? String ?: "",
                sourceWorkspaceId = map["sourceWorkspaceId"] as? String ?: "",
                destinationWorkspaceId = map["destinationWorkspaceId"] as? String ?: "",
                status = map["status"] as? String ?: "pending",
                startedAt = (map["startedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                completedAt = (map["completedAt"] as? Number)?.toLong(),
                recordsMigrated = (map["recordsMigrated"] as? Number)?.toInt() ?: 0
            )
        }
    }
}

// Extension functions for Document / Map mapping

fun JobEntryEntity.toFirestoreMap(createdByUid: String? = null): Map<String, Any?> = mapOf(
    "id" to id,
    "workspaceId" to workspaceId,
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

fun jobEntryFromFirestoreMap(map: Map<String, Any?>, fallbackId: Long = 0, fallbackWorkspaceId: String = ""): JobEntryEntity {
    val id = (map["id"] as? Number)?.toLong() ?: fallbackId
    val wsId = map["workspaceId"] as? String ?: fallbackWorkspaceId
    return JobEntryEntity(
        id = id,
        workspaceId = wsId,
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
    "workspaceId" to workspaceId,
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

fun expenseFromFirestoreMap(map: Map<String, Any?>, fallbackId: Long = 0, fallbackWorkspaceId: String = ""): ExpenseEntity {
    val id = (map["id"] as? Number)?.toLong() ?: fallbackId
    val wsId = map["workspaceId"] as? String ?: fallbackWorkspaceId
    return ExpenseEntity(
        id = id,
        workspaceId = wsId,
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
    "workspaceId" to workspaceId,
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

fun customerFromFirestoreMap(map: Map<String, Any?>, fallbackId: Long = 0, fallbackWorkspaceId: String = ""): CustomerEntity {
    val id = (map["id"] as? Number)?.toLong() ?: fallbackId
    val wsId = map["workspaceId"] as? String ?: fallbackWorkspaceId
    return CustomerEntity(
        id = id,
        workspaceId = wsId,
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
    "workspaceId" to workspaceId,
    "label" to label,
    "chassisNo" to chassisNo,
    "modelYear" to modelYear,
    "operatorName" to operatorName,
    "isActive" to isActive,
    "createdBy" to createdByUid,
    "createdAt" to (if (createdAt > 0) createdAt else System.currentTimeMillis()),
    "updatedAt" to System.currentTimeMillis()
)

fun tractorFromFirestoreMap(map: Map<String, Any?>, fallbackId: Long = 0, fallbackWorkspaceId: String = ""): TractorEntity {
    val id = (map["id"] as? Number)?.toLong() ?: fallbackId
    val wsId = map["workspaceId"] as? String ?: fallbackWorkspaceId
    return TractorEntity(
        id = id,
        workspaceId = wsId,
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
    "workspaceId" to workspaceId,
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

fun partnerFromFirestoreMap(map: Map<String, Any?>, fallbackId: Long = 0, fallbackWorkspaceId: String = ""): PartnerEntity {
    val id = (map["id"] as? Number)?.toLong() ?: fallbackId
    val wsId = map["workspaceId"] as? String ?: fallbackWorkspaceId
    return PartnerEntity(
        id = id,
        workspaceId = wsId,
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
    "workspaceId" to workspaceId,
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

fun withdrawalFromFirestoreMap(map: Map<String, Any?>, fallbackId: Long = 0, fallbackWorkspaceId: String = ""): WithdrawalEntity {
    val id = (map["id"] as? Number)?.toLong() ?: fallbackId
    val wsId = map["workspaceId"] as? String ?: fallbackWorkspaceId
    return WithdrawalEntity(
        id = id,
        workspaceId = wsId,
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
    "workspaceId" to workspaceId,
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

fun appSettingsFromFirestoreMap(map: Map<String, Any?>, currentSettings: AppSettingsEntity, fallbackWorkspaceId: String = ""): AppSettingsEntity {
    val wsId = map["workspaceId"] as? String ?: fallbackWorkspaceId.ifBlank { currentSettings.workspaceId }
    return currentSettings.copy(
        workspaceId = wsId,
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
