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
import com.example.data.firebase.appSettingsFromFirestoreMap
import com.example.data.util.IdGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

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

    private val _settingsSyncState = MutableStateFlow<SettingsSyncState>(SettingsSyncState.Uninitialized)
    val settingsSyncState: StateFlow<SettingsSyncState> = _settingsSyncState.asStateFlow()

    private var activeUid: String? = null

    /**
     * Initializes workspace, attaches real-time snapshot listeners, resolves settings safely,
     * and performs safe initial migration without overwriting remote data.
     */
    fun initializeForUser(userProfile: UserProfile) {
        val uid = userProfile.uid
        if (uid.isBlank()) return
        activeUid = uid

        scope.launch {
            try {
                _settingsSyncState.value = SettingsSyncState.Loading
                Log.d(TAG, "Resolving deterministic workspace for user UID: $uid")

                // Step 1: Resolve or retrieve canonical workspace
                val workspace = firestoreRepository.resolveOrCreateWorkspace(userProfile)
                if (workspace != null) {
                    val wsId = workspace.workspaceId

                    // Step 2: Load or initialize cloud settings before enabling writes
                    val localSettings = appSettingsDao.getSettingsOnce() ?: AppSettingsEntity()
                    val resolvedSettings = firestoreRepository.fetchOrCreateWorkspaceSettings(
                        workspaceId = wsId,
                        uid = uid,
                        localSettings = localSettings
                    )

                    // Merge user profile details with resolved settings
                    val mergedSettings = resolvedSettings.copy(
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

                    // Step 3: Safe migration on first login if workspace has no remote records yet
                    firestoreRepository.migrateLocalDataIfRequired(wsId, uid, database)

                    // Step 4: Start real-time snapshot listeners
                    firestoreRepository.startRealtimeListeners(
                        workspaceId = wsId,
                        onJobsUpdated = { remoteJobs ->
                            scope.launch {
                                syncRemoteJobsToLocal(remoteJobs)
                            }
                        },
                        onExpensesUpdated = { remoteExpenses ->
                            scope.launch {
                                syncRemoteExpensesToLocal(remoteExpenses)
                            }
                        },
                        onCustomersUpdated = { remoteCustomers ->
                            scope.launch {
                                syncRemoteCustomersToLocal(remoteCustomers)
                            }
                        },
                        onTractorsUpdated = { remoteTractors ->
                            scope.launch {
                                syncRemoteTractorsToLocal(remoteTractors)
                            }
                        },
                        onPartnersUpdated = { remotePartners ->
                            scope.launch {
                                syncRemotePartnersToLocal(remotePartners)
                            }
                        },
                        onWithdrawalsUpdated = { remoteWithdrawals ->
                            scope.launch {
                                syncRemoteWithdrawalsToLocal(remoteWithdrawals)
                            }
                        },
                        onSettingsUpdated = { remoteSettingsMap ->
                            scope.launch {
                                val current = appSettingsDao.getSettingsOnce() ?: AppSettingsEntity()
                                val updated = appSettingsFromFirestoreMap(remoteSettingsMap, current)
                                appSettingsDao.insertOrUpdateSettings(updated)
                            }
                        }
                    )

                    _isInitialized.value = true
                    Log.d(TAG, "Workspace initialized successfully: $wsId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing workspace: ${e.message}", e)
                _settingsSyncState.value = SettingsSyncState.Error(e.message ?: "Unknown initialization error")
            }
        }
    }

    /**
     * Cleanly detaches all cloud snapshot listeners on logout.
     */
    fun stopWorkspaceListeners() {
        firestoreRepository.stopRealtimeListeners()
        _isInitialized.value = false
        _settingsSyncState.value = SettingsSyncState.Uninitialized
        activeUid = null
        Log.d(TAG, "Workspace listeners stopped.")
    }

    // --- Remote to Local Synchronization Helpers ---

    private suspend fun syncRemoteJobsToLocal(remoteJobs: List<JobEntryEntity>) {
        try {
            for (job in remoteJobs) {
                jobEntryDao.insertJob(job.copy(isSynced = true))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing remote jobs to local: ${e.message}")
        }
    }

    private suspend fun syncRemoteExpensesToLocal(remoteExpenses: List<ExpenseEntity>) {
        try {
            for (expense in remoteExpenses) {
                expenseDao.insertExpense(expense.copy(isSynced = true))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing remote expenses to local: ${e.message}")
        }
    }

    private suspend fun syncRemoteCustomersToLocal(remoteCustomers: List<CustomerEntity>) {
        try {
            for (customer in remoteCustomers) {
                customerDao.insertCustomer(customer.copy(isSynced = true))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing remote customers to local: ${e.message}")
        }
    }

    private suspend fun syncRemoteTractorsToLocal(remoteTractors: List<TractorEntity>) {
        try {
            for (tractor in remoteTractors) {
                tractorDao.insertTractor(tractor)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing remote tractors to local: ${e.message}")
        }
    }

    private suspend fun syncRemotePartnersToLocal(remotePartners: List<PartnerEntity>) {
        try {
            for (partner in remotePartners) {
                partnerDao.insertPartner(partner)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing remote partners to local: ${e.message}")
        }
    }

    private suspend fun syncRemoteWithdrawalsToLocal(remoteWithdrawals: List<WithdrawalEntity>) {
        try {
            for (withdrawal in remoteWithdrawals) {
                withdrawalDao.insertWithdrawal(withdrawal.copy(isSynced = true))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing remote withdrawals to local: ${e.message}")
        }
    }

    // --- CRUD Bridge (Local + Cloud with Collision-Resistant IDs) ---

    suspend fun saveJobEntry(job: JobEntryEntity, linkedExpense: ExpenseEntity? = null): Long {
        var customerId = job.customerId
        if (customerId <= 0) {
            customerId = addOrFindCustomer(job.customerName, job.customerPhone, job.customerLocation)
        }

        // Generate globally unique collision-resistant ID before insert if new record
        val safeJobId = if (job.id > 0) job.id else IdGenerator.generateId()
        val localJob = job.copy(id = safeJobId, customerId = customerId, isSynced = true)
        jobEntryDao.insertJob(localJob)

        var savedExpense: ExpenseEntity? = null
        if (linkedExpense != null && linkedExpense.amount > 0) {
            val safeExpId = if (linkedExpense.id > 0) linkedExpense.id else IdGenerator.generateId()
            val localExp = linkedExpense.copy(id = safeExpId, relatedJobId = safeJobId, isSynced = true)
            expenseDao.insertExpense(localExp)
            savedExpense = localExp
        }

        recalculateCustomerStats(customerId)

        // Push to Cloud Workspace
        val wsId = currentWorkspace.value?.workspaceId
        if (!wsId.isNullOrBlank()) {
            scope.launch {
                firestoreRepository.saveJobEntry(wsId, localJob, activeUid)
                if (savedExpense != null) {
                    firestoreRepository.saveExpense(wsId, savedExpense, activeUid)
                }
                val cust = customerDao.getCustomerById(customerId)
                if (cust != null) {
                    firestoreRepository.saveCustomer(wsId, cust, activeUid)
                }
            }
        }

        return safeJobId
    }

    suspend fun deleteJob(job: JobEntryEntity) {
        jobEntryDao.deleteJob(job)
        recalculateCustomerStats(job.customerId)

        val wsId = currentWorkspace.value?.workspaceId
        if (!wsId.isNullOrBlank()) {
            scope.launch {
                firestoreRepository.deleteJob(wsId, job.id)
                val cust = customerDao.getCustomerById(job.customerId)
                if (cust != null) {
                    firestoreRepository.saveCustomer(wsId, cust, activeUid)
                }
            }
        }
    }

    suspend fun addExpense(expense: ExpenseEntity): Long {
        val safeExpId = if (expense.id > 0) expense.id else IdGenerator.generateId()
        val savedExp = expense.copy(id = safeExpId, isSynced = true)
        expenseDao.insertExpense(savedExp)

        val wsId = currentWorkspace.value?.workspaceId
        if (!wsId.isNullOrBlank()) {
            scope.launch {
                firestoreRepository.saveExpense(wsId, savedExp, activeUid)
            }
        }
        return safeExpId
    }

    suspend fun updateExpense(expense: ExpenseEntity) {
        val updated = expense.copy(isSynced = true)
        expenseDao.updateExpense(updated)

        val wsId = currentWorkspace.value?.workspaceId
        if (!wsId.isNullOrBlank()) {
            scope.launch {
                firestoreRepository.saveExpense(wsId, updated, activeUid)
            }
        }
    }

    suspend fun deleteExpense(expense: ExpenseEntity) {
        expenseDao.deleteExpense(expense)

        val wsId = currentWorkspace.value?.workspaceId
        if (!wsId.isNullOrBlank()) {
            scope.launch {
                firestoreRepository.deleteExpense(wsId, expense.id)
            }
        }
    }

    suspend fun addWithdrawal(withdrawal: WithdrawalEntity): Long {
        val safeWithId = if (withdrawal.id > 0) withdrawal.id else IdGenerator.generateId()
        val saved = withdrawal.copy(id = safeWithId, isSynced = true)
        withdrawalDao.insertWithdrawal(saved)

        val wsId = currentWorkspace.value?.workspaceId
        if (!wsId.isNullOrBlank()) {
            scope.launch {
                firestoreRepository.saveWithdrawal(wsId, saved, activeUid)
            }
        }
        return safeWithId
    }

    suspend fun updateWithdrawal(withdrawal: WithdrawalEntity) {
        val updated = withdrawal.copy(isSynced = true)
        withdrawalDao.updateWithdrawal(updated)

        val wsId = currentWorkspace.value?.workspaceId
        if (!wsId.isNullOrBlank()) {
            scope.launch {
                firestoreRepository.saveWithdrawal(wsId, updated, activeUid)
            }
        }
    }

    suspend fun deleteWithdrawal(withdrawal: WithdrawalEntity) {
        withdrawalDao.deleteWithdrawal(withdrawal)

        val wsId = currentWorkspace.value?.workspaceId
        if (!wsId.isNullOrBlank()) {
            scope.launch {
                firestoreRepository.deleteWithdrawal(wsId, withdrawal.id)
            }
        }
    }

    suspend fun updateCustomer(customer: CustomerEntity) {
        val sanitized = customer.copy(
            phone = com.example.ui.components.sanitizePhoneNumberForStorage(customer.phone),
            isSynced = true
        )
        customerDao.updateCustomer(sanitized)

        val wsId = currentWorkspace.value?.workspaceId
        if (!wsId.isNullOrBlank()) {
            scope.launch {
                firestoreRepository.saveCustomer(wsId, sanitized, activeUid)
            }
        }
    }

    suspend fun deleteCustomer(customer: CustomerEntity) {
        customerDao.deleteCustomer(customer)

        val wsId = currentWorkspace.value?.workspaceId
        if (!wsId.isNullOrBlank()) {
            scope.launch {
                firestoreRepository.deleteCustomer(wsId, customer.id)
            }
        }
    }

    suspend fun addOrFindCustomer(name: String, phone: String, location: String): Long {
        val customers = customerDao.getAllCustomers().firstOrNull() ?: emptyList()
        val existing = customers.find { it.name.trim().equals(name.trim(), ignoreCase = true) }
        val cleanPhone = com.example.ui.components.sanitizePhoneNumberForStorage(phone)
        val cleanLocation = location.trim()

        val custId: Long
        val customerEntity: CustomerEntity

        if (existing != null) {
            val updated = existing.copy(
                phone = if (cleanPhone.isNotBlank()) cleanPhone else existing.phone,
                location = if (cleanLocation.isNotBlank()) cleanLocation else existing.location,
                updatedAt = System.currentTimeMillis()
            )
            customerDao.updateCustomer(updated)
            custId = existing.id
            customerEntity = updated
        } else {
            val safeCustId = IdGenerator.generateId()
            val newCust = CustomerEntity(
                id = safeCustId,
                name = name.trim(),
                phone = cleanPhone,
                location = cleanLocation,
                totalBilled = 0.0,
                totalPaid = 0.0,
                balanceDue = 0.0,
                isSynced = true
            )
            customerDao.insertCustomer(newCust)
            custId = safeCustId
            customerEntity = newCust
        }

        val wsId = currentWorkspace.value?.workspaceId
        if (!wsId.isNullOrBlank()) {
            scope.launch {
                firestoreRepository.saveCustomer(wsId, customerEntity, activeUid)
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
        val methodDesc = if (paymentMethod.isNotBlank()) "Payment Method: $paymentMethod" else ""
        val noteDesc = if (note.isNotBlank()) "Note: $note" else ""
        val combinedNotes = listOf(methodDesc, noteDesc).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { "Direct Payment Received" }

        val safeEntryId = IdGenerator.generateId()
        val paymentEntry = JobEntryEntity(
            id = safeEntryId,
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
            isSynced = true
        )

        jobEntryDao.insertJob(paymentEntry)
        recalculateCustomerStats(customer.id)

        val wsId = currentWorkspace.value?.workspaceId
        if (!wsId.isNullOrBlank()) {
            scope.launch {
                firestoreRepository.saveJobEntry(wsId, paymentEntry, activeUid)
                val updatedCust = customerDao.getCustomerById(customer.id)
                if (updatedCust != null) {
                    firestoreRepository.saveCustomer(wsId, updatedCust, activeUid)
                }
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
        val safeTracId = if (tractor.id > 0) tractor.id else IdGenerator.generateId()
        val saved = tractor.copy(id = safeTracId)
        tractorDao.insertTractor(saved)

        val wsId = currentWorkspace.value?.workspaceId
        if (!wsId.isNullOrBlank()) {
            scope.launch {
                firestoreRepository.saveTractor(wsId, saved, activeUid)
            }
        }
        return safeTracId
    }

    suspend fun updateTractor(tractor: TractorEntity) {
        tractorDao.updateTractor(tractor)

        val wsId = currentWorkspace.value?.workspaceId
        if (!wsId.isNullOrBlank()) {
            scope.launch {
                firestoreRepository.saveTractor(wsId, tractor, activeUid)
            }
        }
    }

    suspend fun deleteTractor(tractor: TractorEntity) {
        tractorDao.deleteTractor(tractor)

        val wsId = currentWorkspace.value?.workspaceId
        if (!wsId.isNullOrBlank()) {
            scope.launch {
                firestoreRepository.deleteTractor(wsId, tractor.id)
            }
        }
    }

    suspend fun addPartner(partner: PartnerEntity): Long {
        val safePartId = if (partner.id > 0) partner.id else IdGenerator.generateId()
        val saved = partner.copy(id = safePartId)
        partnerDao.insertPartner(saved)

        val wsId = currentWorkspace.value?.workspaceId
        if (!wsId.isNullOrBlank()) {
            scope.launch {
                firestoreRepository.savePartner(wsId, saved, activeUid)
            }
        }
        return safePartId
    }

    suspend fun updatePartner(partner: PartnerEntity) {
        partnerDao.updatePartner(partner)

        val wsId = currentWorkspace.value?.workspaceId
        if (!wsId.isNullOrBlank()) {
            scope.launch {
                firestoreRepository.savePartner(wsId, partner, activeUid)
            }
        }
    }

    suspend fun deletePartner(partner: PartnerEntity) {
        partnerDao.deletePartner(partner)

        val wsId = currentWorkspace.value?.workspaceId
        if (!wsId.isNullOrBlank()) {
            scope.launch {
                firestoreRepository.deletePartner(wsId, partner.id)
            }
        }
    }

    suspend fun updateSettings(settings: AppSettingsEntity) {
        appSettingsDao.insertOrUpdateSettings(settings)

        // Only push to cloud if settings have been loaded/initialized from cloud
        val syncState = _settingsSyncState.value
        val wsId = currentWorkspace.value?.workspaceId
        if (!wsId.isNullOrBlank() && (syncState is SettingsSyncState.LoadedFromCloud || syncState is SettingsSyncState.CreatedInCloud)) {
            scope.launch {
                firestoreRepository.saveSettings(wsId, settings, activeUid)
            }
        }
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
