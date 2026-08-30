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
import com.example.data.firebase.WorkspaceInvitation
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

    private val _pendingInvitations = MutableStateFlow<List<WorkspaceInvitation>>(emptyList())
    val pendingInvitations: StateFlow<List<WorkspaceInvitation>> = _pendingInvitations.asStateFlow()

    private val _workspaceMembers = MutableStateFlow<List<WorkspaceMember>>(emptyList())
    val workspaceMembers: StateFlow<List<WorkspaceMember>> = _workspaceMembers.asStateFlow()

    private var activeUid: String? = null
    private var membershipListener: com.google.firebase.firestore.ListenerRegistration? = null

    fun isCloudReady(): Boolean = _workspaceInitState.value is WorkspaceInitState.Ready

    fun normalizePhoneNumber(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return if (phone.startsWith("+")) phone else "+91${digits.takeLast(10)}"
    }

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
            val personalWsId = workspace.workspaceId

            // Check if there are shared workspace memberships
            val sharedMemberships = firestoreRepository.getUserWorkspaceMemberships(uid)
            val sharedWsIds = sharedMemberships.mapNotNull { it["workspaceId"] as? String }.filter { it.isNotBlank() }
            Log.d("TRAC_WORKSPACE", "DISCOVER uid=$uid personal=$personalWsId shared=$sharedWsIds")

            val wsId = if (sharedWsIds.isNotEmpty()) {
                sharedWsIds.first()
            } else {
                personalWsId
            }
            Log.d("TRAC_WORKSPACE", "ACTIVATE uid=$uid workspace=$wsId")

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

            // Safe migration on first login if personal workspace has no remote records yet
            if (wsId == personalWsId) {
                firestoreRepository.migrateLocalDataIfRequired(personalWsId, uid, database)
            }

            // 8. Set activeWorkspaceId & Ready state ONLY AFTER bootstrap succeeds
            _activeWorkspaceId.value = wsId
            _isInitialized.value = true
            _workspaceInitState.value = WorkspaceInitState.Ready(wsId)

            // 9. Start real-time snapshot listeners
            attachRealtimeListeners(wsId)
            membershipListener?.remove()
            membershipListener = firestoreRepository.listenToUserMemberships(uid) { updatedSharedIds ->
                Log.d("TRAC_WORKSPACE", "DISCOVER uid=$uid personal=$personalWsId shared=$updatedSharedIds")
                if (updatedSharedIds.isNotEmpty()) {
                    val currentWs = _activeWorkspaceId.value
                    if (currentWs == personalWsId || currentWs.isNullOrBlank()) {
                        val targetShared = updatedSharedIds.first()
                        scope.launch {
                            Log.d("TRAC_WORKSPACE", "Auto-activating shared workspace: $targetShared")
                            switchActiveWorkspace(targetShared, userProfile)
                        }
                    }
                }
            }
            refreshWorkspaceMembers(wsId)

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

    private fun attachRealtimeListeners(wsId: String) {
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
    }

    /**
     * Cleanly detaches all cloud snapshot listeners on logout.
     */
    fun stopWorkspaceListeners() {
        firestoreRepository.stopRealtimeListeners()
        membershipListener?.remove()
        membershipListener = null
        _workspaceMembers.value = emptyList()
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

    // --- Direct Partner Management & Shared Workspace Discovery ---

    sealed class DirectAddPartnerResult {
        data class Success(val partner: PartnerEntity, val partnerUid: String) : DirectAddPartnerResult()
        data class AccountNotRegistered(val partner: PartnerEntity, val message: String) : DirectAddPartnerResult()
        data class Error(val message: String) : DirectAddPartnerResult()
    }

    suspend fun refreshWorkspaceMembers(workspaceId: String? = null) {
        val wsId = workspaceId ?: _activeWorkspaceId.value ?: return
        if (isCloudReady()) {
            val members = firestoreRepository.getWorkspaceMembers(wsId)
            _workspaceMembers.value = members
        }
    }

    suspend fun addPartnerDirectly(name: String, phone: String, role: String): DirectAddPartnerResult {
        val wsId = getOrResolveWorkspaceId() ?: ""
        if (wsId.isBlank()) {
            return DirectAddPartnerResult.Error("Workspace is not ready")
        }

        val normalizedPhone = normalizePhoneNumber(phone)
        val cleanDigits = normalizedPhone.filter { it.isDigit() }.takeLast(10)

        // 1. Create or update local PartnerEntity (used for local driver/operator functionality)
        val existingPartners = partnerDao.getPartnersForWorkspace(wsId).firstOrNull() ?: emptyList()
        val existing = existingPartners.firstOrNull { it.phone.filter { ch -> ch.isDigit() }.takeLast(10) == cleanDigits }
        val partnerEntity = if (existing != null) {
            val updated = existing.copy(name = name.trim(), phone = normalizedPhone, role = role.trim().ifBlank { "Partner" })
            partnerDao.updatePartner(updated)
            updated
        } else {
            val newPartner = PartnerEntity(
                id = IdGenerator.generateId(),
                workspaceId = wsId,
                name = name.trim(),
                phone = normalizedPhone,
                role = role.trim().ifBlank { "Partner" },
                avatarColorHex = "#1E4D2B",
                isCurrentActive = false
            )
            partnerDao.insertPartner(newPartner)
            newPartner
        }

        if (!isCloudReady()) {
            return DirectAddPartnerResult.AccountNotRegistered(
                partnerEntity,
                "Partner account not found. Ask this partner to create/login to their Phone account first."
            )
        }

        // 2. Perform Phone Directory Lookup
        val partnerUid = firestoreRepository.lookupPhoneInDirectory(normalizedPhone)
        if (partnerUid.isNullOrBlank()) {
            try {
                firestoreRepository.savePartner(wsId, partnerEntity, activeUid)
            } catch (e: Exception) {
                Log.w(TAG, "Local partner save deferred: ${e.message}")
            }
            return DirectAddPartnerResult.AccountNotRegistered(
                partnerEntity,
                "Partner account not found. Ask this partner to create/login to their Phone account first."
            )
        }

        if (partnerUid == activeUid) {
            return DirectAddPartnerResult.Error("Owner cannot add themselves as Partner.")
        }

        // 3. Directly create workspace membership and user discovery index
        val currentSettings = appSettingsDao.getSettingsForWorkspaceOnce(wsId)
            ?: AppSettingsEntity(workspaceId = wsId)
        val businessName = currentSettings.businessName.ifBlank { "AIDHUNT Tractor Fleet" }

        val addResult = firestoreRepository.addPartnerMemberDirectly(
            workspaceId = wsId,
            partnerUid = partnerUid,
            partnerName = name.trim(),
            partnerPhone = normalizedPhone,
            role = role.trim().ifBlank { "Partner" },
            ownerUid = activeUid ?: "",
            businessName = businessName
        )

        return if (addResult.isSuccess) {
            refreshWorkspaceMembers(wsId)
            DirectAddPartnerResult.Success(partnerEntity, partnerUid)
        } else {
            val err = addResult.exceptionOrNull()?.message ?: "Could not connect partner"
            DirectAddPartnerResult.Error(err)
        }
    }

    suspend fun checkForInvitations(phoneNumber: String): List<WorkspaceInvitation> {
        _pendingInvitations.value = emptyList()
        return emptyList()
    }

    suspend fun switchActiveWorkspace(targetWorkspaceId: String, userProfile: UserProfile): Result<String> {
        val uid = userProfile.uid
        if (targetWorkspaceId.isBlank()) {
            return Result.failure(IllegalArgumentException("Target workspace ID cannot be blank"))
        }
        if (targetWorkspaceId == _activeWorkspaceId.value && _isInitialized.value) {
            return Result.success(targetWorkspaceId)
        }

        return try {
            _workspaceInitState.value = WorkspaceInitState.Loading
            _settingsSyncState.value = SettingsSyncState.Loading

            // Load settings for target workspace
            val localSettings = appSettingsDao.getSettingsForWorkspaceOnce(targetWorkspaceId)
                ?: AppSettingsEntity(workspaceId = targetWorkspaceId)
            val resolvedSettings = if (isCloudReady() && uid.isNotBlank()) {
                firestoreRepository.fetchOrCreateWorkspaceSettings(targetWorkspaceId, uid, localSettings)
            } else localSettings

            val mergedSettings = resolvedSettings.copy(
                workspaceId = targetWorkspaceId,
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

            // Switch active workspace ID
            _activeWorkspaceId.value = targetWorkspaceId
            _isInitialized.value = true
            _workspaceInitState.value = WorkspaceInitState.Ready(targetWorkspaceId)
            Log.d("TRAC_WORKSPACE", "ACTIVATE uid=$uid workspace=$targetWorkspaceId")

            // Reconnect real-time listeners for the new workspace
            if (isCloudReady()) {
                attachRealtimeListeners(targetWorkspaceId)
                refreshWorkspaceMembers(targetWorkspaceId)
                pushUnsyncedToCloud(targetWorkspaceId)
            }

            Result.success(targetWorkspaceId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to switch active workspace: ${e.message}", e)
            _workspaceInitState.value = WorkspaceInitState.Error(e)
            _settingsSyncState.value = SettingsSyncState.Error(e.message ?: "Failed to switch workspace")
            Result.failure(e)
        }
    }

    suspend fun removePartnerFromWorkspace(partner: PartnerEntity) {
        val currentWsId = _activeWorkspaceId.value ?: partner.workspaceId
        try {
            partnerDao.deletePartner(partner)
            if (isCloudReady() && currentWsId.isNotBlank()) {
                firestoreRepository.deletePartner(currentWsId, partner.id)
                firestoreRepository.removePartnerFromWorkspace(
                    workspaceId = currentWsId,
                    partnerUid = null,
                    partnerPhone = partner.phone
                )
            }
            Log.d("TRAC_PARTNER", "Removed partner ${partner.name} from workspace $currentWsId")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing partner: ${e.message}", e)
        }
    }

    suspend fun getAvailableWorkspaces(userProfile: UserProfile): List<Workspace> {
        val uid = userProfile.uid
        val personalWsId = userProfile.defaultWorkspaceId?.ifBlank { null }
            ?: "ws_${uid.replace(Regex("[^a-zA-Z0-9]"), "").take(16).ifBlank { "main" }}"

        val wsMap = mutableMapOf<String, Workspace>()

        // 1. Personal Workspace
        val personalDetails = firestoreRepository.getWorkspaceDetails(personalWsId)
        val personalWs = personalDetails ?: Workspace(
            workspaceId = personalWsId,
            name = "Personal Workspace",
            ownerUid = uid,
            createdAt = System.currentTimeMillis()
        )
        wsMap[personalWsId] = personalWs.copy(name = "Personal Workspace")

        // 2. Discover shared workspaces from userWorkspaceMemberships
        if (uid.isNotBlank() && isCloudReady()) {
            try {
                val memberships = firestoreRepository.getUserWorkspaceMemberships(uid)
                for (m in memberships) {
                    val wsId = m["workspaceId"] as? String ?: continue
                    if (wsId.isBlank() || wsId == personalWsId) continue
                    val wsName = m["workspaceName"] as? String ?: "Tractor Fleet"
                    val ownerUid = m["ownerUid"] as? String ?: ""
                    val details = firestoreRepository.getWorkspaceDetails(wsId)
                    val resolvedName = details?.name ?: wsName
                    wsMap[wsId] = Workspace(
                        workspaceId = wsId,
                        name = "Shared - $resolvedName",
                        ownerUid = ownerUid,
                        createdAt = (m["joinedAt"] as? Long) ?: System.currentTimeMillis()
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching userWorkspaceMemberships: ${e.message}")
            }
        }

        // 3. Any additional listed workspaces in user profile
        for (wsId in userProfile.workspaces) {
            if (wsId.isNotBlank() && !wsMap.containsKey(wsId)) {
                val ws = firestoreRepository.getWorkspaceDetails(wsId)
                val isOwn = wsId == personalWsId || wsId.contains(uid.take(8))
                val name = if (isOwn) "Personal Workspace" else (ws?.name?.let { "Shared - $it" } ?: "Shared Workspace")
                wsMap[wsId] = ws?.copy(name = name) ?: Workspace(
                    workspaceId = wsId,
                    name = name,
                    ownerUid = if (isOwn) uid else "",
                    createdAt = System.currentTimeMillis()
                )
            }
        }

        val sharedIds = wsMap.keys.filter { it != personalWsId }
        Log.d("TRAC_WORKSPACE", "DISCOVER uid=$uid personal=$personalWsId shared=$sharedIds")

        return wsMap.values.toList()
    }

    suspend fun getWorkspaceMembers(workspaceId: String? = null): List<WorkspaceMember> {
        val targetWsId = workspaceId ?: getOrResolveWorkspaceId() ?: return emptyList()
        return firestoreRepository.getWorkspaceMembers(targetWsId)
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
     * Safely retries pushing all locally unsynced records to Cloud.
     * Records are pushed according to their own record.workspaceId.
     */
    suspend fun pushUnsyncedToCloud(targetWorkspaceId: String? = null, isOnline: Boolean = true): SyncResult {
        if (!isOnline) {
            return SyncResult(
                isSuccess = false,
                syncedItemsCount = 0,
                message = "Device offline. Records stored safely in local Room database."
            )
        }

        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (activeUid == null || activeUid != currentUid) {
            return SyncResult(
                isSuccess = false,
                syncedItemsCount = 0,
                message = "Workspace not ready or sign-in mismatch."
            )
        }

        val fallbackWsId = targetWorkspaceId ?: (_workspaceInitState.value as? WorkspaceInitState.Ready)?.workspaceId ?: _activeWorkspaceId.value ?: ""

        val unsyncedJobs = jobEntryDao.getUnsyncedJobs()
        val unsyncedExpenses = expenseDao.getUnsyncedExpenses()
        val unsyncedWithdrawals = withdrawalDao.getUnsyncedWithdrawals()
        val unsyncedCustomers = customerDao.getUnsyncedCustomers()

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
            val recordWsId = cust.workspaceId.ifBlank { fallbackWsId }
            if (recordWsId.isNotBlank()) {
                try {
                    firestoreRepository.saveCustomer(recordWsId, cust.copy(workspaceId = recordWsId), activeUid)
                    customerDao.markCustomersSynced(listOf(cust.id))
                    syncedCount++
                } catch (e: Exception) {
                    Log.w(TAG, "Retry sync for customer ${cust.id} failed: ${e.message}")
                }
            }
        }

        // 2. Sync Jobs
        for (job in unsyncedJobs) {
            val recordWsId = job.workspaceId.ifBlank { fallbackWsId }
            if (recordWsId.isNotBlank()) {
                try {
                    firestoreRepository.saveJobEntry(recordWsId, job.copy(workspaceId = recordWsId), activeUid)
                    jobEntryDao.markJobsSynced(listOf(job.id))
                    syncedCount++
                } catch (e: Exception) {
                    Log.w(TAG, "Retry sync for job ${job.id} failed: ${e.message}")
                }
            }
        }

        // 3. Sync Expenses
        for (exp in unsyncedExpenses) {
            val recordWsId = exp.workspaceId.ifBlank { fallbackWsId }
            if (recordWsId.isNotBlank()) {
                try {
                    firestoreRepository.saveExpense(recordWsId, exp.copy(workspaceId = recordWsId), activeUid)
                    expenseDao.markExpensesSynced(listOf(exp.id))
                    syncedCount++
                } catch (e: Exception) {
                    Log.w(TAG, "Retry sync for expense ${exp.id} failed: ${e.message}")
                }
            }
        }

        // 4. Sync Withdrawals
        for (wth in unsyncedWithdrawals) {
            val recordWsId = wth.workspaceId.ifBlank { fallbackWsId }
            if (recordWsId.isNotBlank()) {
                try {
                    firestoreRepository.saveWithdrawal(recordWsId, wth.copy(workspaceId = recordWsId), activeUid)
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
