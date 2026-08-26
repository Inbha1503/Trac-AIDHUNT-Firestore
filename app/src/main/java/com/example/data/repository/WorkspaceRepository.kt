package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.CustomerEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PartnerEntity
import com.example.data.entity.TractorEntity
import com.example.data.entity.WithdrawalEntity
import com.example.data.firebase.FirestoreRepository
import com.example.data.firebase.UserProfile
import com.example.data.firebase.Workspace
import com.example.data.firebase.WorkspaceMember
import com.example.data.firebase.appSettingsFromFirestoreMap
import com.example.data.util.IdGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

sealed class WorkspaceInitState {
    object Uninitialized : WorkspaceInitState()
    object Loading : WorkspaceInitState()
    data class Ready(val workspaceId: String) : WorkspaceInitState()
    data class Error(val exception: Throwable) : WorkspaceInitState()
}

sealed interface SettingsSyncState {
    object Uninitialized : SettingsSyncState
    object Loading : SettingsSyncState
    data class LoadedFromCloud(val settings: AppSettingsEntity) : SettingsSyncState
    data class CreatedInCloud(val settings: AppSettingsEntity) : SettingsSyncState
    data class Error(val message: String) : SettingsSyncState
}

class WorkspaceRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val firestoreRepository: FirestoreRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val TAG = "WorkspaceRepository"

    private val partnerDao = database.partnerDao()
    private val tractorDao = database.tractorDao()
    private val customerDao = database.customerDao()
    private val jobEntryDao = database.jobEntryDao()
    private val expenseDao = database.expenseDao()
    private val withdrawalDao = database.withdrawalDao()
    private val appSettingsDao = database.appSettingsDao()

    val currentWorkspace: StateFlow<Workspace?> = firestoreRepository.currentWorkspace

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _workspaceInitState = MutableStateFlow<WorkspaceInitState>(WorkspaceInitState.Uninitialized)
    val workspaceInitState: StateFlow<WorkspaceInitState> = _workspaceInitState.asStateFlow()

    private val _activeWorkspaceId = MutableStateFlow<String?>(null)
    val activeWorkspaceId: StateFlow<String?> = _activeWorkspaceId.asStateFlow()

    private val _settingsSyncState = MutableStateFlow<SettingsSyncState>(SettingsSyncState.Uninitialized)
    val settingsSyncState: StateFlow<SettingsSyncState> = _settingsSyncState.asStateFlow()

    private var activeUid: String? = null

    fun isCloudReady(): Boolean = _workspaceInitState.value is WorkspaceInitState.Ready

    /**
     * Initializes workspace, awaits Firestore bootstrap & verification, attaches real-time snapshot listeners,
     * resolves settings safely, and performs safe initial migration.
     */
    suspend fun initializeForUser(userProfile: UserProfile): Result<String> {
        val uid = userProfile.uid
        if (uid.isBlank()) {
            val err = IllegalArgumentException("User profile UID is blank")
            _workspaceInitState.value = WorkspaceInitState.Error(err)
            return Result.failure(err)
        }
        activeUid = uid
        _workspaceInitState.value = WorkspaceInitState.Loading
        _settingsSyncState.value = SettingsSyncState.Loading

        return try {
            Log.d(TAG, "Resolving deterministic workspace for user UID: $uid")

            // 1-7. Resolve/create, verify and write users/{uid}.defaultWorkspaceId on Firestore
            val workspace = firestoreRepository.bootstrapWorkspaceForUser(userProfile)
            val wsId = workspace.workspaceId

            // Load or initialize cloud settings before enabling writes
            val localSettings = appSettingsDao.getSettingsForWorkspaceOnce(wsId)
                ?: AppSettingsEntity(workspaceId = wsId)
            val resolvedSettings = firestoreRepository.fetchOrCreateWorkspaceSettings(
                workspaceId = wsId,
                uid = uid,
                localSettings = localSettings
            )

            // Merge user profile details with resolved settings
            val mergedSettings = resolvedSettings.copy(
                workspaceId = wsId,
                isLoggedIn = true,
                activePartnerName = userProfile.displayName?.ifBlank { null }
                    ?: resolvedSettings.activePartnerName.ifBlank { "Partner" },
                activePartnerPhone = userProfile.phoneNumber?.ifBlank { null }
                    ?: resolvedSettings.activePartnerPhone,
                profilePhotoUri = userProfile.photoUrl?.ifBlank { null }
                    ?: resolvedSettings.profilePhotoUri,
                lastSyncTime = System.currentTimeMillis()
            )
            appSettingsDao.insertOrUpdateSettings(mergedSettings)
            _settingsSyncState.value = SettingsSyncState.LoadedFromCloud(mergedSettings)

            // Safe migration on first login if workspace has no remote records yet
            firestoreRepository.migrateLocalDataIfRequired(wsId, uid, database)

            // 8. Set activeWorkspaceId & Ready state ONLY AFTER bootstrap succeeds
            _activeWorkspaceId.value = wsId
            _isInitialized.value = true
            _workspaceInitState.value = WorkspaceInitState.Ready(wsId)
            Log.d("TRAC_WORKSPACE", "READY workspace=$wsId")

            // 9. Start real-time snapshot listeners
            firestoreRepository.startRealtimeListeners(
                workspaceId = wsId,
                onJobsUpdated = { remoteJobs, listenerWsId ->
                    scope.launch {
                        syncRemoteJobsToLocal(remoteJobs, listenerWsId)
                    }
                },
                onExpensesUpdated = { remoteExpenses, listenerWsId ->
                    scope.launch {
                        syncRemoteExpensesToLocal(remoteExpenses, listenerWsId)
                    }
                },
                onCustomersUpdated = { remoteCustomers, listenerWsId ->
                    scope.launch {
                        syncRemoteCustomersToLocal(remoteCustomers, listenerWsId)
                    }
                },
                onTractorsUpdated = { remoteTractors, listenerWsId ->
                    scope.launch {
                        syncRemoteTractorsToLocal(remoteTractors, listenerWsId)
                    }
                },
                onPartnersUpdated = { remotePartners, listenerWsId ->
                    scope.launch {
                        syncRemotePartnersToLocal(remotePartners, listenerWsId)
                    }
                },
                onWithdrawalsUpdated = { remoteWithdrawals, listenerWsId ->
                    scope.launch {
                        syncRemoteWithdrawalsToLocal(remoteWithdrawals, listenerWsId)
                    }
                },
                onSettingsUpdated = { remoteSettingsMap, listenerWsId ->
                    scope.launch {
                        val currentWsId = _activeWorkspaceId.value
                        if (listenerWsId != currentWsId || currentWsId.isNullOrBlank()) return@launch
                        val current = appSettingsDao.getSettingsForWorkspaceOnce(listenerWsId)
                            ?: AppSettingsEntity(workspaceId = listenerWsId)
                        val updated = appSettingsFromFirestoreMap(remoteSettingsMap, current, fallbackWorkspaceId = listenerWsId)
                        appSettingsDao.insertOrUpdateSettings(updated)
                    }
                }
            )

            // 10. Run pushUnsyncedToCloud() for that workspace
            pushUnsyncedToCloud(wsId)

            Result.success(wsId)
        } catch (e: Throwable) {
            Log.e(TAG, "Workspace bootstrap failed: ${e.message}", e)
            _workspaceInitState.value = WorkspaceInitState.Error(e)
            _settingsSyncState.value = SettingsSyncState.Error(e.message ?: "Workspace bootstrap failed")
            Result.failure(e)
        }
    }

    /**
     * Cleanly detaches all cloud snapshot listeners on logout.
     */
    fun stopWorkspaceListeners() {
        firestoreRepository.stopRealtimeListeners()
        _isInitialized.value = false
        _workspaceInitState.value = WorkspaceInitState.Uninitialized
        _settingsSyncState.value = SettingsSyncState.Uninitialized
        activeUid = null
        _activeWorkspaceId.value = null
        Log.d(TAG, "Workspace listeners stopped.")
    }

    // --- Remote to Local Synchronization Helpers (ID-based Reconciliation) ---

    private suspend fun syncRemoteJobsToLocal(remoteJobs: List<JobEntryEntity>, listenerWsId: String) {
        val currentWsId = _activeWorkspaceId.value
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (listenerWsId != currentWsId || activeUid != currentUid || currentWsId.isNullOrBlank()) {
            Log.w(TAG, "Ignoring jobs snapshot from $listenerWsId because active workspace is $currentWsId")
            return
        }
        try {
            val remoteIds = remoteJobs.map { it.id }
            for (job in remoteJobs) {
                jobEntryDao.insertJob(job.copy(workspaceId = listenerWsId, isSynced = true))
            }
            if (remoteIds.isNotEmpty()) {
                jobEntryDao.deleteSyncedNotIn(listenerWsId, remoteIds)
            } else {
                jobEntryDao.deleteAllSyncedForWorkspace(listenerWsId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing remote jobs to local: ${e.message}")
        }
    }

    private suspend fun syncRemoteExpensesToLocal(remoteExpenses: List<ExpenseEntity>, listenerWsId: String) {
        val currentWsId = _activeWorkspaceId.value
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (listenerWsId != currentWsId || activeUid != currentUid || currentWsId.isNullOrBlank()) {
            Log.w(TAG, "Ignoring expenses snapshot from $listenerWsId because active workspace is $currentWsId")
            return
        }
        try {
            val remoteIds = remoteExpenses.map { it.id }
            for (expense in remoteExpenses) {
                expenseDao.insertExpense(expense.copy(workspaceId = listenerWsId, isSynced = true))
            }
            if (remoteIds.isNotEmpty()) {
                expenseDao.deleteSyncedNotIn(listenerWsId, remoteIds)
            } else {
                expenseDao.deleteAllSyncedForWorkspace(listenerWsId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing remote expenses to local: ${e.message}")
        }
    }

    private suspend fun syncRemoteCustomersToLocal(remoteCustomers: List<CustomerEntity>, listenerWsId: String) {
        val currentWsId = _activeWorkspaceId.value
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (listenerWsId != currentWsId || activeUid != currentUid || currentWsId.isNullOrBlank()) {
            Log.w(TAG, "Ignoring customers snapshot from $listenerWsId because active workspace is $currentWsId")
            return
        }
        try {
            val remoteIds = remoteCustomers.map { it.id }
            for (customer in remoteCustomers) {
                customerDao.insertCustomer(customer.copy(workspaceId = listenerWsId, isSynced = true))
            }
            if (remoteIds.isNotEmpty()) {
                customerDao.deleteSyncedNotIn(listenerWsId, remoteIds)
            } else {
                customerDao.deleteAllSyncedForWorkspace(listenerWsId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing remote customers to local: ${e.message}")
        }
    }

    private suspend fun syncRemoteTractorsToLocal(remoteTractors: List<TractorEntity>, listenerWsId: String) {
        val currentWsId = _activeWorkspaceId.value
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (listenerWsId != currentWsId || activeUid != currentUid || currentWsId.isNullOrBlank()) {
            Log.w(TAG, "Ignoring tractors snapshot from $listenerWsId because active workspace is $currentWsId")
            return
        }
        try {
            val remoteIds = remoteTractors.map { it.id }
            for (tractor in remoteTractors) {
                tractorDao.insertTractor(tractor.copy(workspaceId = listenerWsId))
            }
            if (remoteIds.isNotEmpty()) {
                tractorDao.deleteNotIn(listenerWsId, remoteIds)
            } else {
                tractorDao.deleteAllForWorkspace(listenerWsId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing remote tractors to local: ${e.message}")
        }
    }

    private suspend fun syncRemotePartnersToLocal(remotePartners: List<PartnerEntity>, listenerWsId: String) {
        val currentWsId = _activeWorkspaceId.value
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (listenerWsId != currentWsId || activeUid != currentUid || currentWsId.isNullOrBlank()) {
            Log.w(TAG, "Ignoring partners snapshot from $listenerWsId because active workspace is $currentWsId")
            return
        }
        try {
            val remoteIds = remotePartners.map { it.id }
            for (partner in remotePartners) {
                partnerDao.insertPartner(partner.copy(workspaceId = listenerWsId))
            }
            if (remoteIds.isNotEmpty()) {
                partnerDao.deleteNotIn(listenerWsId, remoteIds)
            } else {
                partnerDao.deleteAllForWorkspace(listenerWsId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing remote partners to local: ${e.message}")
        }
    }

    private suspend fun syncRemoteWithdrawalsToLocal(remoteWithdrawals: List<WithdrawalEntity>, listenerWsId: String) {
        val currentWsId = _activeWorkspaceId.value
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (listenerWsId != currentWsId || activeUid != currentUid || currentWsId.isNullOrBlank()) {
            Log.w(TAG, "Ignoring withdrawals snapshot from $listenerWsId because active workspace is $currentWsId")
            return
        }
        try {
            val remoteIds = remoteWithdrawals.map { it.id }
            for (withdrawal in remoteWithdrawals) {
                withdrawalDao.insertWithdrawal(withdrawal.copy(workspaceId = listenerWsId, isSynced = true))
            }
            if (remoteIds.isNotEmpty()) {
                withdrawalDao.deleteSyncedNotIn(listenerWsId, remoteIds)
            } else {
                withdrawalDao.deleteAllSyncedForWorkspace(listenerWsId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing remote withdrawals to local: ${e.message}")
        }
    }

    private suspend fun getOrResolveWorkspaceId(): String? {
        val readyWsId = (_workspaceInitState.value as? WorkspaceInitState.Ready)?.workspaceId
        if (!readyWsId.isNullOrBlank()) return readyWsId
        val current = _activeWorkspaceId.value ?: currentWorkspace.value?.workspaceId
        if (!current.isNullOrBlank()) return current
        return null
    }

    // --- CRUD Bridge (Local + Cloud with Collision-Resistant IDs) ---

    suspend fun saveJobEntry(job: JobEntryEntity, linkedExpense: ExpenseEntity? = null): Long {
        Log.d("TRAC_AUTH", "activeUid=$activeUid")
        val isReady = isCloudReady()
        val wsId = (_workspaceInitState.value as? WorkspaceInitState.Ready)?.workspaceId ?: _activeWorkspaceId.value ?: ""
        var customerId = job.customerId
        if (customerId <= 0) {
            customerId = addOrFindCustomer(job.customerName, job.customerPhone, job.customerLocation)
        }

        // Generate globally unique collision-resistant ID before insert if new record
        val safeJobId = if (job.id > 0) job.id else IdGenerator.generateId()
        Log.d("TRAC_ENTRY", "local record saved id=$safeJobId isSynced=false jobTitle=${job.workType} wsId=$wsId")
        val localJob = job.copy(id = safeJobId, workspaceId = wsId, customerId = customerId, isSynced = false)
        jobEntryDao.insertJob(localJob)

        var savedExpense: ExpenseEntity? = null
        if (linkedExpense != null && linkedExpense.amount > 0) {
            val safeExpId = if (linkedExpense.id > 0) linkedExpense.id else IdGenerator.generateId()
            val localExp = linkedExpense.copy(id = safeExpId, workspaceId = wsId, relatedJobId = safeJobId, isSynced = false)
            expenseDao.insertExpense(localExp)
            savedExpense = localExp
        }

        recalculateCustomerStats(customerId)

        // Push to Cloud Workspace ONLY when workspace initialization state is Ready
        Log.d("TRAC_WORKSPACE", "workspaceId=$wsId isCloudReady=$isReady")
        if (isReady && wsId.isNotBlank()) {
            try {
                firestoreRepository.saveJobEntry(wsId, localJob, activeUid)
                jobEntryDao.markJobsSynced(listOf(safeJobId))
                Log.d("TRAC_ENTRY", "marked synced id=$safeJobId")
            } catch (e: Exception) {
                Log.w("TRAC_FIRESTORE", "Job cloud sync deferred: ${e.message}")
            }

            if (savedExpense != null) {
                try {
                    firestoreRepository.saveExpense(wsId, savedExpense, activeUid)
                    expenseDao.markExpensesSynced(listOf(savedExpense.id))
                } catch (e: Exception) {
                    Log.w("TRAC_FIRESTORE", "Linked expense cloud sync deferred: ${e.message}")
                }
            }
            val cust = customerDao.getCustomerById(customerId)
            if (cust != null) {
                try {
                    firestoreRepository.saveCustomer(wsId, cust, activeUid)
                    customerDao.markCustomersSynced(listOf(cust.id))
                } catch (e: Exception) {
                    Log.w("TRAC_FIRESTORE", "Customer cloud sync deferred: ${e.message}")
                }
            }
        } else {
            Log.d("TRAC_WORKSPACE", "workspace not ready for cloud writes, keeping local isSynced=false")
        }

        return safeJobId
    }

    suspend fun deleteJob(job: JobEntryEntity) {
        jobEntryDao.deleteJob(job)
        recalculateCustomerStats(job.customerId)

        val isReady = isCloudReady()
        val wsId = getOrResolveWorkspaceId()
        if (isReady && !wsId.isNullOrBlank()) {
            try {
                firestoreRepository.deleteJob(wsId, job.id)
                val cust = customerDao.getCustomerById(job.customerId)
                if (cust != null) {
                    firestoreRepository.saveCustomer(wsId, cust, activeUid)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Job delete cloud sync deferred: ${e.message}")
            }
        }
    }

    suspend fun addExpense(expense: ExpenseEntity): Long {
        val isReady = isCloudReady()
        val wsId = getOrResolveWorkspaceId() ?: ""
        val safeExpId = if (expense.id > 0) expense.id else IdGenerator.generateId()
        val savedExp = expense.copy(id = safeExpId, workspaceId = wsId, isSynced = false)
        expenseDao.insertExpense(savedExp)

        if (isReady && wsId.isNotBlank()) {
            try {
                firestoreRepository.saveExpense(wsId, savedExp, activeUid)
                expenseDao.markExpensesSynced(listOf(safeExpId))
            } catch (e: Exception) {
                Log.w("TRAC_FIRESTORE", "Expense cloud sync deferred: ${e.message}")
            }
        }
        return safeExpId
    }

    suspend fun updateExpense(expense: ExpenseEntity) {
        val isReady = isCloudReady()
        val wsId = getOrResolveWorkspaceId() ?: expense.workspaceId
        val updated = expense.copy(workspaceId = wsId, isSynced = false)
        expenseDao.updateExpense(updated)

        if (isReady && wsId.isNotBlank()) {
            try {
                firestoreRepository.saveExpense(wsId, updated, activeUid)
                expenseDao.markExpensesSynced(listOf(updated.id))
            } catch (e: Exception) {
                Log.w("TRAC_FIRESTORE", "Expense update cloud sync deferred: ${e.message}")
            }
        }
    }

    suspend fun deleteExpense(expense: ExpenseEntity) {
        expenseDao.deleteExpense(expense)

        val isReady = isCloudReady()
        val wsId = getOrResolveWorkspaceId()
        if (isReady && !wsId.isNullOrBlank()) {
            try {
                firestoreRepository.deleteExpense(wsId, expense.id)
            } catch (e: Exception) {
                Log.w(TAG, "Expense delete cloud sync deferred: ${e.message}")
            }
        }
    }

    suspend fun addWithdrawal(withdrawal: WithdrawalEntity): Long {
        val isReady = isCloudReady()
        val wsId = getOrResolveWorkspaceId() ?: ""
        val safeWithId = if (withdrawal.id > 0) withdrawal.id else IdGenerator.generateId()
        val saved = withdrawal.copy(id = safeWithId, workspaceId = wsId, isSynced = false)
        withdrawalDao.insertWithdrawal(saved)

        if (isReady && wsId.isNotBlank()) {
            try {
                firestoreRepository.saveWithdrawal(wsId, saved, activeUid)
                withdrawalDao.markWithdrawalsSynced(listOf(safeWithId))
            } catch (e: Exception) {
                Log.w("TRAC_FIRESTORE", "Withdrawal cloud sync deferred: ${e.message}")
            }
        }
        return safeWithId
    }

    suspend fun updateWithdrawal(withdrawal: WithdrawalEntity) {
        val isReady = isCloudReady()
        val wsId = getOrResolveWorkspaceId() ?: withdrawal.workspaceId
        val updated = withdrawal.copy(workspaceId = wsId, isSynced = false)
        withdrawalDao.updateWithdrawal(updated)

        if (isReady && wsId.isNotBlank()) {
            try {
                firestoreRepository.saveWithdrawal(wsId, updated, activeUid)
                withdrawalDao.markWithdrawalsSynced(listOf(updated.id))
            } catch (e: Exception) {
                Log.w("TRAC_FIRESTORE", "Withdrawal update cloud sync deferred: ${e.message}")
            }
        }
    }

    suspend fun deleteWithdrawal(withdrawal: WithdrawalEntity) {
        withdrawalDao.deleteWithdrawal(withdrawal)

        val isReady = isCloudReady()
        val wsId = getOrResolveWorkspaceId()
        if (isReady && !wsId.isNullOrBlank()) {
            try {
                firestoreRepository.deleteWithdrawal(wsId, withdrawal.id)
            } catch (e: Exception) {
                Log.w(TAG, "Withdrawal delete cloud sync deferred: ${e.message}")
            }
        }
    }

    suspend fun updateCustomer(customer: CustomerEntity) {
        val isReady = isCloudReady()
        val wsId = getOrResolveWorkspaceId() ?: customer.workspaceId
        val sanitized = customer.copy(
            workspaceId = wsId,
            phone = com.example.ui.components.sanitizePhoneNumberForStorage(customer.phone),
            isSynced = false
        )
        customerDao.updateCustomer(sanitized)

        if (isReady && wsId.isNotBlank()) {
            try {
                firestoreRepository.saveCustomer(wsId, sanitized, activeUid)
                customerDao.markCustomersSynced(listOf(sanitized.id))
            } catch (e: Exception) {
                Log.w("TRAC_FIRESTORE", "Customer update cloud sync deferred: ${e.message}")
            }
        }
    }

    suspend fun deleteCustomer(customer: CustomerEntity) {
        customerDao.deleteCustomer(customer)

        val isReady = isCloudReady()
        val wsId = getOrResolveWorkspaceId()
        if (isReady && !wsId.isNullOrBlank()) {
            try {
                firestoreRepository.deleteCustomer(wsId, customer.id)
            } catch (e: Exception) {
                Log.w(TAG, "Customer delete cloud sync deferred: ${e.message}")
            }
        }
    }

    suspend fun addOrFindCustomer(name: String, phone: String, location: String): Long {
        val isReady = isCloudReady()
        val wsId = getOrResolveWorkspaceId() ?: ""
        val customers = customerDao.getCustomersForWorkspace(wsId).firstOrNull() ?: emptyList()
        val existing = customers.find { it.name.trim().equals(name.trim(), ignoreCase = true) }
        val cleanPhone = com.example.ui.components.sanitizePhoneNumberForStorage(phone)
        val cleanLocation = location.trim()

        val custId: Long
        val customerEntity: CustomerEntity

        if (existing != null) {
            val updated = existing.copy(
                phone = if (cleanPhone.isNotBlank()) cleanPhone else existing.phone,
                location = if (cleanLocation.isNotBlank()) cleanLocation else existing.location,
                updatedAt = System.currentTimeMillis(),
                isSynced = false
            )
            customerDao.updateCustomer(updated)
            custId = existing.id
            customerEntity = updated
        } else {
            val safeCustId = IdGenerator.generateId()
            val newCust = CustomerEntity(
                id = safeCustId,
                workspaceId = wsId,
                name = name.trim(),
                phone = cleanPhone,
                location = cleanLocation,
                totalBilled = 0.0,
                totalPaid = 0.0,
                balanceDue = 0.0,
                isSynced = false
            )
            customerDao.insertCustomer(newCust)
            custId = safeCustId
            customerEntity = newCust
        }

        if (isReady && wsId.isNotBlank()) {
            try {
                firestoreRepository.saveCustomer(wsId, customerEntity, activeUid)
                customerDao.markCustomersSynced(listOf(custId))
            } catch (e: Exception) {
                Log.w(TAG, "Customer sync to cloud deferred: ${e.message}")
            }
        }

        return custId
    }

    suspend fun recordCustomerPayment(
        customer: CustomerEntity,
        amount: Double,
        dateTimestamp: Long,
        paymentMethod: String,
        note: String,
        operatorName: String
    ): Long {
        val isReady = isCloudReady()
        val wsId = getOrResolveWorkspaceId() ?: customer.workspaceId
        val methodDesc = if (paymentMethod.isNotBlank()) "Payment Method: $paymentMethod" else ""
        val noteDesc = if (note.isNotBlank()) "Note: $note" else ""
        val combinedNotes = listOf(methodDesc, noteDesc).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { "Direct Payment Received" }

        val safeEntryId = IdGenerator.generateId()
        val paymentEntry = JobEntryEntity(
            id = safeEntryId,
            workspaceId = wsId,
            customerId = customer.id,
            customerName = customer.name,
            customerPhone = customer.phone,
            customerLocation = customer.location,
            operatorName = operatorName.ifBlank { "Partner" },
            tractorId = 0,
            tractorLabel = "Payment",
            workType = "Payment Received",
            startTimeMillis = dateTimestamp,
            endTimeMillis = dateTimestamp,
            durationMinutes = 0,
            hourlyRate = 0.0,
            totalAmount = 0.0,
            amountReceived = amount,
            pendingAmount = -amount,
            addedByPartner = operatorName.ifBlank { "Partner" },
            notes = combinedNotes,
            isSynced = false
        )

        jobEntryDao.insertJob(paymentEntry)
        recalculateCustomerStats(customer.id)

        if (isReady && wsId.isNotBlank()) {
            try {
                firestoreRepository.saveJobEntry(wsId, paymentEntry, activeUid)
                jobEntryDao.markJobsSynced(listOf(safeEntryId))
                val updatedCust = customerDao.getCustomerById(customer.id)
                if (updatedCust != null) {
                    firestoreRepository.saveCustomer(wsId, updatedCust, activeUid)
                    customerDao.markCustomersSynced(listOf(customer.id))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Customer update after payment deferred: ${e.message}")
            }
        }

        return safeEntryId
    }

    private suspend fun recalculateCustomerStats(customerId: Long) {
        val customer = customerDao.getCustomerById(customerId) ?: return
        val jobs = jobEntryDao.getJobsForCustomer(customerId).firstOrNull() ?: emptyList()

        val totalBilled = jobs.sumOf { it.totalAmount }
        val totalPaid = jobs.sumOf { it.amountReceived }
        val balanceDue = jobs.sumOf { it.pendingAmount }

        val updated = customer.copy(
            totalBilled = totalBilled,
            totalPaid = totalPaid,
            balanceDue = balanceDue,
            updatedAt = System.currentTimeMillis()
        )
        customerDao.updateCustomer(updated)
    }

    suspend fun addTractor(tractor: TractorEntity): Long {
        val isReady = isCloudReady()
        val wsId = getOrResolveWorkspaceId() ?: ""
        val safeTracId = if (tractor.id > 0) tractor.id else IdGenerator.generateId()
        val saved = tractor.copy(id = safeTracId, workspaceId = wsId)
        tractorDao.insertTractor(saved)

        if (isReady && wsId.isNotBlank()) {
            try {
                firestoreRepository.saveTractor(wsId, saved, activeUid)
            } catch (e: Exception) {
                Log.w(TAG, "Tractor cloud sync deferred: ${e.message}")
            }
        }
        return safeTracId
    }

    suspend fun updateTractor(tractor: TractorEntity) {
        val isReady = isCloudReady()
        val wsId = getOrResolveWorkspaceId() ?: tractor.workspaceId
        val scoped = tractor.copy(workspaceId = wsId)
        tractorDao.updateTractor(scoped)

        if (isReady && wsId.isNotBlank()) {
            try {
                firestoreRepository.saveTractor(wsId, scoped, activeUid)
            } catch (e: Exception) {
                Log.w(TAG, "Tractor update cloud sync deferred: ${e.message}")
            }
        }
    }

    suspend fun deleteTractor(tractor: TractorEntity) {
        tractorDao.deleteTractor(tractor)

        val isReady = isCloudReady()
        val wsId = getOrResolveWorkspaceId()
        if (isReady && !wsId.isNullOrBlank()) {
            try {
                firestoreRepository.deleteTractor(wsId, tractor.id)
            } catch (e: Exception) {
                Log.w(TAG, "Tractor delete cloud sync deferred: ${e.message}")
            }
        }
    }

    suspend fun addPartner(partner: PartnerEntity): Long {
        val isReady = isCloudReady()
        val wsId = getOrResolveWorkspaceId() ?: ""
        val safePartId = if (partner.id > 0) partner.id else IdGenerator.generateId()
        val saved = partner.copy(id = safePartId, workspaceId = wsId)
        partnerDao.insertPartner(saved)

        if (isReady && wsId.isNotBlank()) {
            try {
                firestoreRepository.savePartner(wsId, saved, activeUid)
            } catch (e: Exception) {
                Log.w(TAG, "Partner cloud sync deferred: ${e.message}")
            }
        }
        return safePartId
    }

    suspend fun updatePartner(partner: PartnerEntity) {
        val isReady = isCloudReady()
        val wsId = getOrResolveWorkspaceId() ?: partner.workspaceId
        val scoped = partner.copy(workspaceId = wsId)
        partnerDao.updatePartner(scoped)

        if (isReady && wsId.isNotBlank()) {
            try {
                firestoreRepository.savePartner(wsId, scoped, activeUid)
            } catch (e: Exception) {
                Log.w(TAG, "Partner update cloud sync deferred: ${e.message}")
            }
        }
    }

    suspend fun deletePartner(partner: PartnerEntity) {
        partnerDao.deletePartner(partner)

        val isReady = isCloudReady()
        val wsId = getOrResolveWorkspaceId()
        if (isReady && !wsId.isNullOrBlank()) {
            try {
                firestoreRepository.deletePartner(wsId, partner.id)
            } catch (e: Exception) {
                Log.w(TAG, "Partner delete cloud sync deferred: ${e.message}")
            }
        }
    }

    suspend fun updateSettings(settings: AppSettingsEntity) {
        val isReady = isCloudReady()
        val wsId = getOrResolveWorkspaceId() ?: settings.workspaceId
        val scoped = settings.copy(workspaceId = wsId)
        appSettingsDao.insertOrUpdateSettings(scoped)

        // Only push to cloud if settings have been loaded/initialized from cloud
        val syncState = _settingsSyncState.value
        if (isReady && wsId.isNotBlank() && (syncState is SettingsSyncState.LoadedFromCloud || syncState is SettingsSyncState.CreatedInCloud)) {
            try {
                firestoreRepository.saveSettings(wsId, scoped, activeUid)
            } catch (e: Exception) {
                Log.w(TAG, "Settings update cloud sync deferred: ${e.message}")
            }
        }
    }

    /**
     * Safely retries pushing all locally unsynced records to Cloud for the ready workspace.
     */
    suspend fun pushUnsyncedToCloud(targetWorkspaceId: String? = null, isOnline: Boolean = true): SyncResult {
        if (!isOnline) {
            return SyncResult(
                isSuccess = false,
                syncedItemsCount = 0,
                message = "Device offline. Records stored safely in local Room database."
            )
        }

        val readyWsId = (_workspaceInitState.value as? WorkspaceInitState.Ready)?.workspaceId
        val wsId = targetWorkspaceId ?: readyWsId ?: _activeWorkspaceId.value
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (wsId.isNullOrBlank() || activeUid == null || activeUid != currentUid) {
            return SyncResult(
                isSuccess = false,
                syncedItemsCount = 0,
                message = "Workspace not ready or sign-in mismatch."
            )
        }

        val unsyncedJobs = jobEntryDao.getUnsyncedJobsForWorkspace(wsId)
        val unsyncedExpenses = expenseDao.getUnsyncedExpensesForWorkspace(wsId)
        val unsyncedWithdrawals = withdrawalDao.getUnsyncedWithdrawalsForWorkspace(wsId)
        val unsyncedCustomers = customerDao.getUnsyncedCustomersForWorkspace(wsId)

        val totalCount = unsyncedJobs.size + unsyncedExpenses.size + unsyncedWithdrawals.size + unsyncedCustomers.size
        if (totalCount == 0) {
            return SyncResult(
                isSuccess = true,
                syncedItemsCount = 0,
                message = "All records are already in sync with Cloud."
            )
        }

        var syncedCount = 0

        // 1. Sync Customers first so FK relationships exist
        for (cust in unsyncedCustomers) {
            if (cust.workspaceId == wsId) {
                try {
                    firestoreRepository.saveCustomer(wsId, cust, activeUid)
                    customerDao.markCustomersSynced(listOf(cust.id))
                    syncedCount++
                } catch (e: Exception) {
                    Log.w(TAG, "Retry sync for customer ${cust.id} failed: ${e.message}")
                }
            }
        }

        // 2. Sync Jobs
        for (job in unsyncedJobs) {
            if (job.workspaceId == wsId) {
                try {
                    firestoreRepository.saveJobEntry(wsId, job, activeUid)
                    jobEntryDao.markJobsSynced(listOf(job.id))
                    syncedCount++
                } catch (e: Exception) {
                    Log.w(TAG, "Retry sync for job ${job.id} failed: ${e.message}")
                }
            }
        }

        // 3. Sync Expenses
        for (exp in unsyncedExpenses) {
            if (exp.workspaceId == wsId) {
                try {
                    firestoreRepository.saveExpense(wsId, exp, activeUid)
                    expenseDao.markExpensesSynced(listOf(exp.id))
                    syncedCount++
                } catch (e: Exception) {
                    Log.w(TAG, "Retry sync for expense ${exp.id} failed: ${e.message}")
                }
            }
        }

        // 4. Sync Withdrawals
        for (wth in unsyncedWithdrawals) {
            if (wth.workspaceId == wsId) {
                try {
                    firestoreRepository.saveWithdrawal(wsId, wth, activeUid)
                    withdrawalDao.markWithdrawalsSynced(listOf(wth.id))
                    syncedCount++
                } catch (e: Exception) {
                    Log.w(TAG, "Retry sync for withdrawal ${wth.id} failed: ${e.message}")
                }
            }
        }

        val success = (syncedCount == totalCount)
        return SyncResult(
            isSuccess = success,
            syncedItemsCount = syncedCount,
            message = if (success) "Pushed $syncedCount offline records to Cloud successfully!"
                      else "Synced $syncedCount of $totalCount items. Cloud sync pending for remainder."
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: WorkspaceRepository? = null

        fun getInstance(context: Context, database: AppDatabase): WorkspaceRepository {
            return INSTANCE ?: synchronized(this) {
                val firestoreRepo = FirestoreRepository(context.applicationContext)
                val instance = WorkspaceRepository(context.applicationContext, database, firestoreRepo)
                INSTANCE = instance
                instance
            }
        }
    }
}
