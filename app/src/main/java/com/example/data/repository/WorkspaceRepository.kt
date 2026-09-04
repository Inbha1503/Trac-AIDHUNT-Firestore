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
import com.example.data.sync.PendingDeleteManager
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

    private val _personalWorkspaceId = MutableStateFlow<String?>(null)
    val personalWorkspaceId: StateFlow<String?> = _personalWorkspaceId.asStateFlow()

    fun getPersonalWorkspaceId(): String? = _personalWorkspaceId.value ?: _activeWorkspaceId.value

    private val _visibleWorkspaceIds = MutableStateFlow<Set<String>>(emptySet())
    val visibleWorkspaceIds: StateFlow<Set<String>> = _visibleWorkspaceIds.asStateFlow()

    private val _activeWorkspaceId = MutableStateFlow<String?>(null)
    val activeWorkspaceId: StateFlow<String?> = _activeWorkspaceId.asStateFlow()

    private val _settingsSyncState = MutableStateFlow<SettingsSyncState>(SettingsSyncState.Uninitialized)
    val settingsSyncState: StateFlow<SettingsSyncState> = _settingsSyncState.asStateFlow()

    private val _pendingInvitations = MutableStateFlow<List<WorkspaceInvitation>>(emptyList())
    val pendingInvitations: StateFlow<List<WorkspaceInvitation>> = _pendingInvitations.asStateFlow()

    private val _workspaceMembers = MutableStateFlow<List<WorkspaceMember>>(emptyList())
    val workspaceMembers: StateFlow<List<WorkspaceMember>> = _workspaceMembers.asStateFlow()

    private val _isCollaborationOwner = MutableStateFlow(true)
    val isCollaborationOwner: StateFlow<Boolean> = _isCollaborationOwner.asStateFlow()

    private val _genuinePartnerWorkspaces = MutableStateFlow<List<Workspace>>(emptyList())
    val genuinePartnerWorkspaces: StateFlow<List<Workspace>> = _genuinePartnerWorkspaces.asStateFlow()

    fun getActiveUid(): String? = activeUid

    private var activeUid: String? = null
    private val pendingDeleteManager = PendingDeleteManager.getInstance(context)
    private var collaborationIndexListener: com.google.firebase.firestore.ListenerRegistration? = null
    private val groupMembersListeners = mutableMapOf<String, com.google.firebase.firestore.ListenerRegistration>()
    private val groupPendingListeners = mutableMapOf<String, com.google.firebase.firestore.ListenerRegistration>()
    private val groupWorkspacesMap = java.util.concurrent.ConcurrentHashMap<String, List<String>>()

    fun isCloudReady(): Boolean = _workspaceInitState.value is WorkspaceInitState.Ready

    fun normalizePhoneNumber(phone: String): String = com.example.data.firebase.normalizePhoneNumber(phone)

    /**
     * Initializes workspace, prioritizing immediate Room availability for offline access,
     * then resolves Firestore bootstrap & verification, attaches real-time snapshot listeners,
     * resolves settings safely, and performs safe initial migration.
     */
    suspend fun initializeForUser(userProfile: UserProfile): Result<String> {
        val uid = userProfile.uid
        if (uid.isBlank()) {
            val err = IllegalArgumentException("User profile UID is blank")
            _workspaceInitState.value = WorkspaceInitState.Error(err)
            return Result.failure(err)
        }

        // STRICT ACCOUNT BOUNDARY: Detect auth UID change or new session
        if (activeUid != null && activeUid != uid) {
            stopWorkspaceListeners()
        }
        activeUid = uid
        _workspaceInitState.value = WorkspaceInitState.Loading
        _settingsSyncState.value = SettingsSyncState.Loading
        _activeWorkspaceId.value = null
        _personalWorkspaceId.value = null
        _visibleWorkspaceIds.value = emptySet()
        _genuinePartnerWorkspaces.value = emptyList()
        _isCollaborationOwner.value = true

        // Deterministic fallback workspace ID based on UID
        val canonicalWsId = userProfile.defaultWorkspaceId?.ifBlank { null }
            ?: "ws_${uid.replace(Regex("[^a-zA-Z0-9]"), "").take(16).ifBlank { "main" }}"

        return try {
            Log.d(TAG, "Resolving deterministic cloud workspace for user UID: $uid")

            // 1-7. Resolve/create, verify and write users/{uid}.defaultWorkspaceId on Firestore
            val workspace = firestoreRepository.bootstrapWorkspaceForUser(userProfile)
            val personalWsId = workspace.workspaceId.ifBlank { canonicalWsId }
            _personalWorkspaceId.value = personalWsId

            // Auto-connect if this verified phone number was previously added by an owner while unregistered
            val rawPhone = userProfile.phoneNumber ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.phoneNumber ?: ""
            val verifiedPhone = normalizePhoneNumber(rawPhone)
            if (verifiedPhone.isNotBlank()) {
                try {
                    val connected = firestoreRepository.findAndConnectPendingPartnerLinks(
                        userUid = uid,
                        verifiedPhone = verifiedPhone,
                        userWorkspaceId = personalWsId,
                        userDisplayName = userProfile.displayName
                    )
                    if (connected.isNotEmpty()) {
                        Log.d("TRAC_PARTNER", "Auto-connected to groups on login: $connected")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "findAndConnectPendingPartnerLinks error: ${e.message}")
                }
            }

            // Discover genuine partner workspaces strictly
            val genuinePartners = getGenuinePartnerWorkspaces(userProfile, personalWsId)
            _genuinePartnerWorkspaces.value = genuinePartners

            // Strict Role and Workspace Activation Rules:
            // CASE 1: 0 genuine partner businesses -> personal workspace active, Role = Business Owner
            // CASE 2: 1+ genuine partner businesses -> partner workspace active automatically, Role = Partner, personal owner workspace hidden
            val (resolvedActiveWsId, isOwner, visibleSet) = when {
                genuinePartners.isEmpty() -> {
                    Triple(personalWsId, true, setOf(personalWsId))
                }
                else -> {
                    val partnerWs = genuinePartners.first().workspaceId
                    Triple(partnerWs, false, setOf(partnerWs))
                }
            }

            _isCollaborationOwner.value = isOwner
            _visibleWorkspaceIds.value = visibleSet
            _activeWorkspaceId.value = resolvedActiveWsId

            // Load or initialize cloud settings before enabling writes
            val currentLocal = appSettingsDao.getSettingsForWorkspaceOnce(resolvedActiveWsId)
                ?: AppSettingsEntity(
                    workspaceId = resolvedActiveWsId,
                    businessName = if (isOwner) (userProfile.displayName ?: "") else (genuinePartners.find { it.workspaceId == resolvedActiveWsId }?.name ?: "")
                )
            val resolvedSettings = firestoreRepository.fetchOrCreateWorkspaceSettings(
                workspaceId = resolvedActiveWsId,
                uid = uid,
                localSettings = currentLocal
            )

            // Merge user profile details with resolved settings
            val mergedSettings = resolvedSettings.copy(
                workspaceId = resolvedActiveWsId,
                isLoggedIn = true,
                activePartnerName = userProfile.displayName?.ifBlank { null }
                    ?: resolvedSettings.activePartnerName.ifBlank { "" },
                activePartnerPhone = userProfile.phoneNumber?.ifBlank { null }
                    ?: resolvedSettings.activePartnerPhone,
                profilePhotoUri = userProfile.photoUrl?.ifBlank { null }
                    ?: resolvedSettings.profilePhotoUri,
                lastSyncTime = System.currentTimeMillis()
            )
            appSettingsDao.insertOrUpdateSettings(mergedSettings)
            _settingsSyncState.value = SettingsSyncState.LoadedFromCloud(mergedSettings)

            // Safe migration on first login if personal workspace has no remote records yet
            if (isOwner) {
                firestoreRepository.migrateLocalDataIfRequired(personalWsId, uid, database)
            }

            _workspaceInitState.value = WorkspaceInitState.Ready(resolvedActiveWsId)

            // Start real-time snapshot listeners for all visible workspaces
            attachRealtimeListenersForVisibleWorkspaces()

            // Start listener for collaboration groups discovery
            listenToCollaborationGroupsDiscovery(uid, personalWsId)

            refreshWorkspaceMembers(resolvedActiveWsId)

            // Run pushUnsyncedToCloud()
            pushUnsyncedToCloud(resolvedActiveWsId)

            Result.success(resolvedActiveWsId)
        } catch (e: Throwable) {
            Log.w(TAG, "Cloud workspace bootstrap deferred (offline fallback): ${e.message}")
            val fallbackWsId = canonicalWsId
            _personalWorkspaceId.value = fallbackWsId
            _isCollaborationOwner.value = true
            _visibleWorkspaceIds.value = setOf(fallbackWsId)
            _activeWorkspaceId.value = fallbackWsId
            _genuinePartnerWorkspaces.value = emptyList()

            val fallbackSettings = appSettingsDao.getSettingsForWorkspaceOnce(fallbackWsId)
                ?: AppSettingsEntity(workspaceId = fallbackWsId, businessName = userProfile.displayName ?: "")
            appSettingsDao.insertOrUpdateSettings(fallbackSettings)

            _isInitialized.value = true
            _workspaceInitState.value = WorkspaceInitState.Ready(fallbackWsId)
            _settingsSyncState.value = SettingsSyncState.LoadedFromCloud(fallbackSettings)

            Result.success(fallbackWsId)
        }
    }

    private fun listenToCollaborationGroupsDiscovery(uid: String, personalWsId: String) {
        collaborationIndexListener?.remove()
        collaborationIndexListener = firestoreRepository.listenToUserCollaborationGroups(uid) { groupIds ->
            val allGroups = (groupIds + personalWsId).distinct()
            Log.d("TRAC_WORKSPACE", "Collaboration groups updated for $uid: $allGroups")

            // Re-evaluate partner status and active workspace
            scope.launch {
                val userProf = UserProfile(uid = uid)
                val refreshed = getGenuinePartnerWorkspaces(userProf, personalWsId)
                _genuinePartnerWorkspaces.value = refreshed

                val (targetWsId, isOwner, visibleSet) = when {
                    refreshed.isEmpty() -> {
                        Triple(personalWsId, true, setOf(personalWsId))
                    }
                    else -> {
                        val partnerWs = refreshed.first().workspaceId
                        Triple(partnerWs, false, setOf(partnerWs))
                    }
                }

                _isCollaborationOwner.value = isOwner
                _visibleWorkspaceIds.value = visibleSet

                if (_activeWorkspaceId.value != targetWsId) {
                    Log.d("TRAC_WORKSPACE", "Auto-switching active workspace to $targetWsId (isOwner=$isOwner)")
                    switchActiveWorkspace(targetWsId, userProf)
                } else {
                    attachRealtimeListenersForVisibleWorkspaces()
                    refreshWorkspaceMembers(targetWsId)
                }
            }

            // Listen to each group's members
            val currentListeningGroups = groupMembersListeners.keys.toSet()
            val groupsToRemove = currentListeningGroups - allGroups.toSet()
            for (gId in groupsToRemove) {
                groupMembersListeners.remove(gId)?.remove()
                groupWorkspacesMap.remove(gId)
            }

            if (groupsToRemove.isNotEmpty()) {
                scope.launch {
                    for (removedWsId in groupsToRemove) {
                        if (removedWsId != personalWsId) {
                            try {
                                jobEntryDao.deleteAllSyncedForWorkspace(removedWsId)
                                expenseDao.deleteAllSyncedForWorkspace(removedWsId)
                                customerDao.deleteAllSyncedForWorkspace(removedWsId)
                                tractorDao.deleteAllForWorkspace(removedWsId)
                                partnerDao.deleteAllForWorkspace(removedWsId)
                                withdrawalDao.deleteAllSyncedForWorkspace(removedWsId)
                            } catch (e: Exception) {
                                Log.w("TRAC_WORKSPACE", "Error cleaning up removed workspace $removedWsId: ${e.message}")
                            }
                        }
                    }

                    val newVisible = (groupWorkspacesMap.values.flatten() + personalWsId).toSet()
                    if (newVisible != _visibleWorkspaceIds.value) {
                        Log.d("TRAC_WORKSPACE", "Visible workspaces after group removal: $newVisible")
                        _visibleWorkspaceIds.value = newVisible
                        attachRealtimeListenersForVisibleWorkspaces()
                        refreshWorkspaceMembers()
                    }
                    
                    if (_activeWorkspaceId.value != null && groupsToRemove.contains(_activeWorkspaceId.value)) {
                        val available = getAvailableWorkspaces(UserProfile(uid = uid))
                        val fallbackId = available.firstOrNull()?.workspaceId ?: personalWsId
                        if (fallbackId != _activeWorkspaceId.value) {
                            switchActiveWorkspace(fallbackId, UserProfile(uid = uid))
                        }
                    }
                }
            }

            for (groupId in allGroups) {
                if (!groupMembersListeners.containsKey(groupId)) {
                    val listener = firestoreRepository.listenToCollaborationGroupMembers(groupId) { memberWorkspaces ->
                        scope.launch {
                            groupWorkspacesMap[groupId] = memberWorkspaces
                            val newVisible = (groupWorkspacesMap.values.flatten() + personalWsId).toSet()
                            if (newVisible != _visibleWorkspaceIds.value) {
                                Log.d("TRAC_WORKSPACE", "Visible workspaces updated: $newVisible")
                                _visibleWorkspaceIds.value = newVisible
                                attachRealtimeListenersForVisibleWorkspaces()
                            }
                            refreshWorkspaceMembers()
                        }
                    }
                    if (listener != null) {
                        groupMembersListeners[groupId] = listener
                    }
                }
                if (!groupPendingListeners.containsKey(groupId)) {
                    val pListener = firestoreRepository.listenToCollaborationGroupPendingPhones(groupId) {
                        scope.launch {
                            refreshWorkspaceMembers()
                        }
                    }
                    if (pListener != null) {
                        groupPendingListeners[groupId] = pListener
                    }
                }
            }
        }
    }

    private fun attachRealtimeListenersForVisibleWorkspaces() {
        val visibleIds = _visibleWorkspaceIds.value
        firestoreRepository.startRealtimeListenersForWorkspaces(
            workspaceIds = visibleIds,
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
                    val pWsId = _personalWorkspaceId.value
                    // Only update local app settings if this is from the personal workspace
                    if (listenerWsId == pWsId && !pWsId.isNullOrBlank()) {
                        val current = appSettingsDao.getSettingsForWorkspaceOnce(listenerWsId)
                            ?: AppSettingsEntity(workspaceId = listenerWsId)
                        val updated = appSettingsFromFirestoreMap(remoteSettingsMap, current, fallbackWorkspaceId = listenerWsId)
                        appSettingsDao.insertOrUpdateSettings(updated)
                    }
                }
            }
        )
    }

    /**
     * Cleanly detaches all cloud snapshot listeners on logout.
     */
    fun stopWorkspaceListeners() {
        firestoreRepository.stopRealtimeListeners()
        collaborationIndexListener?.remove()
        collaborationIndexListener = null
        groupMembersListeners.values.forEach { it.remove() }
        groupMembersListeners.clear()
        groupPendingListeners.values.forEach { it.remove() }
        groupPendingListeners.clear()
        groupWorkspacesMap.clear()
        _workspaceMembers.value = emptyList()
        _isInitialized.value = false
        _workspaceInitState.value = WorkspaceInitState.Uninitialized
        _settingsSyncState.value = SettingsSyncState.Uninitialized
        activeUid = null
        _personalWorkspaceId.value = null
        _visibleWorkspaceIds.value = emptySet()
        _activeWorkspaceId.value = null
        _genuinePartnerWorkspaces.value = emptyList()
        _isCollaborationOwner.value = true
        Log.d(TAG, "Workspace listeners stopped.")
    }

    // --- Remote to Local Synchronization Helpers (ID-based Reconciliation) ---

    private suspend fun syncRemoteJobsToLocal(remoteJobs: List<JobEntryEntity>, listenerWsId: String) {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (activeUid != currentUid || !_visibleWorkspaceIds.value.contains(listenerWsId)) {
            Log.w(TAG, "Ignoring jobs snapshot from $listenerWsId because it is not in visible workspaces")
            return
        }
        try {
            val pendingDeleteIds = pendingDeleteManager.getPendingDeleteIds("JOB", listenerWsId)
            val unsyncedLocalJobIds = jobEntryDao.getUnsyncedJobs().filter { it.workspaceId == listenerWsId }.map { it.id }.toSet()
            val validRemoteJobs = remoteJobs.filterNot { pendingDeleteIds.contains(it.id) }

            for (job in validRemoteJobs) {
                if (unsyncedLocalJobIds.contains(job.id)) {
                    // Do not overwrite pending local edits
                    continue
                }
                jobEntryDao.insertJob(job.copy(workspaceId = listenerWsId, isSynced = true))
            }

            val validRemoteIds = validRemoteJobs.map { it.id }
            if (validRemoteIds.isNotEmpty()) {
                jobEntryDao.deleteSyncedNotIn(listenerWsId, validRemoteIds)
            } else {
                jobEntryDao.deleteAllSyncedForWorkspace(listenerWsId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing remote jobs to local: ${e.message}")
        }
    }

    private suspend fun syncRemoteExpensesToLocal(remoteExpenses: List<ExpenseEntity>, listenerWsId: String) {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (activeUid != currentUid || !_visibleWorkspaceIds.value.contains(listenerWsId)) {
            Log.w(TAG, "Ignoring expenses snapshot from $listenerWsId because it is not in visible workspaces")
            return
        }
        try {
            val pendingDeleteIds = pendingDeleteManager.getPendingDeleteIds("EXPENSE", listenerWsId)
            val unsyncedLocalExpIds = expenseDao.getUnsyncedExpenses().filter { it.workspaceId == listenerWsId }.map { it.id }.toSet()
            val validRemoteExpenses = remoteExpenses.filterNot { pendingDeleteIds.contains(it.id) }

            for (expense in validRemoteExpenses) {
                if (unsyncedLocalExpIds.contains(expense.id)) {
                    // Do not overwrite pending local edits
                    continue
                }
                expenseDao.insertExpense(expense.copy(workspaceId = listenerWsId, isSynced = true))
            }

            val validRemoteIds = validRemoteExpenses.map { it.id }
            if (validRemoteIds.isNotEmpty()) {
                expenseDao.deleteSyncedNotIn(listenerWsId, validRemoteIds)
            } else {
                expenseDao.deleteAllSyncedForWorkspace(listenerWsId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing remote expenses to local: ${e.message}")
        }
    }

    private suspend fun syncRemoteCustomersToLocal(remoteCustomers: List<CustomerEntity>, listenerWsId: String) {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (activeUid != currentUid || !_visibleWorkspaceIds.value.contains(listenerWsId)) {
            Log.w(TAG, "Ignoring customers snapshot from $listenerWsId because it is not in visible workspaces")
            return
        }
        try {
            val pendingDeleteIds = pendingDeleteManager.getPendingDeleteIds("CUSTOMER", listenerWsId)
            val unsyncedLocalCustIds = customerDao.getUnsyncedCustomers().filter { it.workspaceId == listenerWsId }.map { it.id }.toSet()
            val validRemoteCustomers = remoteCustomers.filterNot { pendingDeleteIds.contains(it.id) }

            for (customer in validRemoteCustomers) {
                if (unsyncedLocalCustIds.contains(customer.id)) {
                    // Do not overwrite pending local edits
                    continue
                }
                customerDao.insertCustomer(customer.copy(workspaceId = listenerWsId, isSynced = true))
            }

            val validRemoteIds = validRemoteCustomers.map { it.id }
            if (validRemoteIds.isNotEmpty()) {
                customerDao.deleteSyncedNotIn(listenerWsId, validRemoteIds)
            } else {
                customerDao.deleteAllSyncedForWorkspace(listenerWsId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing remote customers to local: ${e.message}")
        }
    }

    private suspend fun syncRemoteTractorsToLocal(remoteTractors: List<TractorEntity>, listenerWsId: String) {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (activeUid != currentUid || !_visibleWorkspaceIds.value.contains(listenerWsId)) {
            Log.w(TAG, "Ignoring tractors snapshot from $listenerWsId because it is not in visible workspaces")
            return
        }
        try {
            val pendingDeleteIds = pendingDeleteManager.getPendingDeleteIds("TRACTOR", listenerWsId)
            val validRemoteTractors = remoteTractors.filterNot { pendingDeleteIds.contains(it.id) }

            for (tractor in validRemoteTractors) {
                tractorDao.insertTractor(tractor.copy(workspaceId = listenerWsId))
            }

            val validRemoteIds = validRemoteTractors.map { it.id }
            if (validRemoteIds.isNotEmpty()) {
                tractorDao.deleteNotIn(listenerWsId, validRemoteIds)
            } else {
                tractorDao.deleteAllForWorkspace(listenerWsId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing remote tractors to local: ${e.message}")
        }
    }

    private suspend fun syncRemotePartnersToLocal(remotePartners: List<PartnerEntity>, listenerWsId: String) {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (activeUid != currentUid || !_visibleWorkspaceIds.value.contains(listenerWsId)) {
            Log.w(TAG, "Ignoring partners snapshot from $listenerWsId because it is not in visible workspaces")
            return
        }
        try {
            val pendingDeleteIds = pendingDeleteManager.getPendingDeleteIds("PARTNER", listenerWsId)
            val validRemotePartners = remotePartners.filterNot { pendingDeleteIds.contains(it.id) }

            for (partner in validRemotePartners) {
                partnerDao.insertPartner(partner.copy(workspaceId = listenerWsId))
            }

            val validRemoteIds = validRemotePartners.map { it.id }
            if (validRemoteIds.isNotEmpty()) {
                partnerDao.deleteNotIn(listenerWsId, validRemoteIds)
            } else {
                partnerDao.deleteAllForWorkspace(listenerWsId)
            }
            refreshWorkspaceMembers()
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing remote partners to local: ${e.message}")
        }
    }

    private suspend fun syncRemoteWithdrawalsToLocal(remoteWithdrawals: List<WithdrawalEntity>, listenerWsId: String) {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (activeUid != currentUid || !_visibleWorkspaceIds.value.contains(listenerWsId)) {
            Log.w(TAG, "Ignoring withdrawals snapshot from $listenerWsId because it is not in visible workspaces")
            return
        }
        try {
            val pendingDeleteIds = pendingDeleteManager.getPendingDeleteIds("WITHDRAWAL", listenerWsId)
            val unsyncedLocalWthIds = withdrawalDao.getUnsyncedWithdrawals().filter { it.workspaceId == listenerWsId }.map { it.id }.toSet()
            val validRemoteWithdrawals = remoteWithdrawals.filterNot { pendingDeleteIds.contains(it.id) }

            for (withdrawal in validRemoteWithdrawals) {
                if (unsyncedLocalWthIds.contains(withdrawal.id)) {
                    // Do not overwrite pending local edits
                    continue
                }
                withdrawalDao.insertWithdrawal(withdrawal.copy(workspaceId = listenerWsId, isSynced = true))
            }

            val validRemoteIds = validRemoteWithdrawals.map { it.id }
            if (validRemoteIds.isNotEmpty()) {
                withdrawalDao.deleteSyncedNotIn(listenerWsId, validRemoteIds)
            } else {
                withdrawalDao.deleteAllSyncedForWorkspace(listenerWsId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing remote withdrawals to local: ${e.message}")
        }
    }

    private suspend fun getOrResolveWorkspaceId(): String? {
        val current = _activeWorkspaceId.value
        if (!current.isNullOrBlank()) return current
        val pWsId = _personalWorkspaceId.value
        if (!pWsId.isNullOrBlank()) return pWsId
        val readyWsId = (_workspaceInitState.value as? WorkspaceInitState.Ready)?.workspaceId
        if (!readyWsId.isNullOrBlank()) return readyWsId
        val wsCurrent = currentWorkspace.value?.workspaceId
        if (!wsCurrent.isNullOrBlank()) return wsCurrent
        return null
    }

    // --- CRUD Bridge (Local + Cloud with Collision-Resistant IDs) ---

    suspend fun saveJobEntry(job: JobEntryEntity, linkedExpense: ExpenseEntity? = null): Long {
        Log.d("TRAC_AUTH", "activeUid=$activeUid")
        val isReady = isCloudReady()
        val wsId = getOrResolveWorkspaceId() ?: ""
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

        // Push to Cloud Workspace asynchronously so local write returns immediately
        Log.d("TRAC_WORKSPACE", "workspaceId=$wsId isCloudReady=$isReady")
        if (isReady && wsId.isNotBlank()) {
            scope.launch {
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
            }
        } else {
            Log.d("TRAC_WORKSPACE", "workspace not ready for cloud writes, keeping local isSynced=false")
        }

        return safeJobId
    }

    suspend fun deleteJob(job: JobEntryEntity) {
        val wsId = job.workspaceId.ifBlank { getOrResolveWorkspaceId() ?: "" }
        jobEntryDao.deleteJob(job)
        recalculateCustomerStats(job.customerId)
        if (wsId.isNotBlank()) {
            pendingDeleteManager.recordPendingDelete("JOB", job.id, wsId)
        }

        val isReady = isCloudReady()
        if (isReady && wsId.isNotBlank()) {
            scope.launch {
                try {
                    firestoreRepository.deleteJob(wsId, job.id)
                    pendingDeleteManager.removePendingDelete("JOB", job.id, wsId)
                    val cust = customerDao.getCustomerById(job.customerId)
                    if (cust != null) {
                        firestoreRepository.saveCustomer(wsId, cust, activeUid)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Job delete cloud sync deferred: ${e.message}")
                }
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
            scope.launch {
                try {
                    firestoreRepository.saveExpense(wsId, savedExp, activeUid)
                    expenseDao.markExpensesSynced(listOf(safeExpId))
                } catch (e: Exception) {
                    Log.w("TRAC_FIRESTORE", "Expense cloud sync deferred: ${e.message}")
                }
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
            scope.launch {
                try {
                    firestoreRepository.saveExpense(wsId, updated, activeUid)
                    expenseDao.markExpensesSynced(listOf(updated.id))
                } catch (e: Exception) {
                    Log.w("TRAC_FIRESTORE", "Expense update cloud sync deferred: ${e.message}")
                }
            }
        }
    }

    suspend fun deleteExpense(expense: ExpenseEntity) {
        val wsId = expense.workspaceId.ifBlank { getOrResolveWorkspaceId() ?: "" }
        expenseDao.deleteExpense(expense)
        if (wsId.isNotBlank()) {
            pendingDeleteManager.recordPendingDelete("EXPENSE", expense.id, wsId)
        }

        val isReady = isCloudReady()
        if (isReady && wsId.isNotBlank()) {
            scope.launch {
                try {
                    firestoreRepository.deleteExpense(wsId, expense.id)
                    pendingDeleteManager.removePendingDelete("EXPENSE", expense.id, wsId)
                } catch (e: Exception) {
                    Log.w(TAG, "Expense delete cloud sync deferred: ${e.message}")
                }
            }
        }
    }

    suspend fun getAvailableBalance(): Double {
        val wsId = getOrResolveWorkspaceId() ?: return 0.0
        val totalRec = jobEntryDao.getTotalReceivedForWorkspace(wsId).firstOrNull() ?: 0.0
        val totalExp = expenseDao.getTotalExpensesForWorkspace(wsId).firstOrNull() ?: 0.0
        val totalWth = withdrawalDao.getTotalWithdrawnForWorkspace(wsId).firstOrNull() ?: 0.0
        return totalRec - totalExp - totalWth
    }

    suspend fun addWithdrawal(withdrawal: WithdrawalEntity): Long {
        if (withdrawal.amount <= 0) {
            throw IllegalArgumentException("Withdrawal amount must be greater than ₹0")
        }
        val currentAvailable = getAvailableBalance()
        if (withdrawal.amount > currentAvailable) {
            throw IllegalStateException("Insufficient available balance. Available: ₹$currentAvailable")
        }
        val isReady = isCloudReady()
        val wsId = getOrResolveWorkspaceId() ?: ""
        val safeWithId = if (withdrawal.id > 0) withdrawal.id else IdGenerator.generateId()
        val saved = withdrawal.copy(id = safeWithId, workspaceId = wsId, isSynced = false)
        withdrawalDao.insertWithdrawal(saved)

        if (isReady && wsId.isNotBlank()) {
            scope.launch {
                try {
                    firestoreRepository.saveWithdrawal(wsId, saved, activeUid)
                    withdrawalDao.markWithdrawalsSynced(listOf(safeWithId))
                } catch (e: Exception) {
                    Log.w("TRAC_FIRESTORE", "Withdrawal cloud sync deferred: ${e.message}")
                }
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
            scope.launch {
                try {
                    firestoreRepository.saveWithdrawal(wsId, updated, activeUid)
                    withdrawalDao.markWithdrawalsSynced(listOf(updated.id))
                } catch (e: Exception) {
                    Log.w("TRAC_FIRESTORE", "Withdrawal update cloud sync deferred: ${e.message}")
                }
            }
        }
    }

    suspend fun deleteWithdrawal(withdrawal: WithdrawalEntity) {
        val wsId = withdrawal.workspaceId.ifBlank { getOrResolveWorkspaceId() ?: "" }
        withdrawalDao.deleteWithdrawal(withdrawal)
        if (wsId.isNotBlank()) {
            pendingDeleteManager.recordPendingDelete("WITHDRAWAL", withdrawal.id, wsId)
        }

        val isReady = isCloudReady()
        if (isReady && wsId.isNotBlank()) {
            scope.launch {
                try {
                    firestoreRepository.deleteWithdrawal(wsId, withdrawal.id)
                    pendingDeleteManager.removePendingDelete("WITHDRAWAL", withdrawal.id, wsId)
                } catch (e: Exception) {
                    Log.w(TAG, "Withdrawal delete cloud sync deferred: ${e.message}")
                }
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
            scope.launch {
                try {
                    firestoreRepository.saveCustomer(wsId, sanitized, activeUid)
                    customerDao.markCustomersSynced(listOf(sanitized.id))
                } catch (e: Exception) {
                    Log.w("TRAC_FIRESTORE", "Customer update cloud sync deferred: ${e.message}")
                }
            }
        }
    }

    suspend fun deleteCustomer(customer: CustomerEntity) {
        val wsId = customer.workspaceId.ifBlank { getOrResolveWorkspaceId() ?: "" }
        customerDao.deleteCustomer(customer)
        if (wsId.isNotBlank()) {
            pendingDeleteManager.recordPendingDelete("CUSTOMER", customer.id, wsId)
        }

        val isReady = isCloudReady()
        if (isReady && wsId.isNotBlank()) {
            scope.launch {
                try {
                    firestoreRepository.deleteCustomer(wsId, customer.id)
                    pendingDeleteManager.removePendingDelete("CUSTOMER", customer.id, wsId)
                } catch (e: Exception) {
                    Log.w(TAG, "Customer delete cloud sync deferred: ${e.message}")
                }
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
            scope.launch {
                try {
                    firestoreRepository.saveCustomer(wsId, customerEntity, activeUid)
                    customerDao.markCustomersSynced(listOf(custId))
                } catch (e: Exception) {
                    Log.w(TAG, "Customer sync to cloud deferred: ${e.message}")
                }
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
            scope.launch {
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
            scope.launch {
                try {
                    firestoreRepository.saveTractor(wsId, saved, activeUid)
                } catch (e: Exception) {
                    Log.w(TAG, "Tractor cloud sync deferred: ${e.message}")
                }
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
            scope.launch {
                try {
                    firestoreRepository.saveTractor(wsId, scoped, activeUid)
                } catch (e: Exception) {
                    Log.w(TAG, "Tractor update cloud sync deferred: ${e.message}")
                }
            }
        }
    }

    suspend fun deleteTractor(tractor: TractorEntity) {
        val wsId = tractor.workspaceId.ifBlank { getOrResolveWorkspaceId() ?: "" }
        tractorDao.deleteTractor(tractor)
        if (wsId.isNotBlank()) {
            pendingDeleteManager.recordPendingDelete("TRACTOR", tractor.id, wsId)
        }

        val isReady = isCloudReady()
        if (isReady && wsId.isNotBlank()) {
            scope.launch {
                try {
                    firestoreRepository.deleteTractor(wsId, tractor.id)
                    pendingDeleteManager.removePendingDelete("TRACTOR", tractor.id, wsId)
                } catch (e: Exception) {
                    Log.w(TAG, "Tractor delete cloud sync deferred: ${e.message}")
                }
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
            scope.launch {
                try {
                    firestoreRepository.savePartner(wsId, saved, activeUid)
                } catch (e: Exception) {
                    Log.w(TAG, "Partner cloud sync deferred: ${e.message}")
                }
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
            scope.launch {
                try {
                    firestoreRepository.savePartner(wsId, scoped, activeUid)
                    if (partner.phone.isNotBlank()) {
                        firestoreRepository.savePendingPartnerPhone(
                            groupId = wsId,
                            normalizedPhone = partner.phone,
                            displayName = partner.name,
                            role = partner.role,
                            ownerUid = activeUid ?: ""
                        )
                    }
                    refreshWorkspaceMembers(wsId)
                } catch (e: Exception) {
                    Log.w(TAG, "Partner update cloud sync deferred: ${e.message}")
                }
            }
        }
    }

    suspend fun deletePartner(partner: PartnerEntity) {
        val wsId = partner.workspaceId.ifBlank { getOrResolveWorkspaceId() ?: "" }
        partnerDao.deletePartner(partner)
        if (wsId.isNotBlank()) {
            pendingDeleteManager.recordPendingDelete("PARTNER", partner.id, wsId)
        }

        val isReady = isCloudReady()
        if (isReady && wsId.isNotBlank()) {
            scope.launch {
                try {
                    firestoreRepository.deletePartner(wsId, partner.id)
                    if (partner.phone.isNotBlank()) {
                        firestoreRepository.deletePendingPartnerPhone(wsId, partner.phone)
                    }
                    pendingDeleteManager.removePendingDelete("PARTNER", partner.id, wsId)
                    refreshWorkspaceMembers(wsId)
                } catch (e: Exception) {
                    Log.w(TAG, "Partner delete cloud sync deferred: ${e.message}")
                }
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
        if (!isCloudReady()) return
        val targetWs = if (!workspaceId.isNullOrBlank()) setOf(workspaceId) else _visibleWorkspaceIds.value
        val visible = if (targetWs.isNotEmpty()) targetWs else setOfNotNull(_activeWorkspaceId.value, _personalWorkspaceId.value)
        val allMembers = mutableListOf<WorkspaceMember>()
        for (wsId in visible) {
            val members = firestoreRepository.getWorkspaceMembers(wsId)
            allMembers.addAll(members)
            val collabMembers = firestoreRepository.getCollaborationGroupMembersList(wsId)
            allMembers.addAll(collabMembers)
            val pendingPhones = firestoreRepository.getPendingPartnerPhones(wsId)
            for (pending in pendingPhones) {
                allMembers.add(
                    WorkspaceMember(
                        uid = "",
                        role = pending.role.ifBlank { "partner" },
                        status = "waiting_for_registration",
                        phoneNumber = pending.normalizedPhone,
                        displayName = pending.displayName.ifBlank { null },
                        addedByUid = pending.addedByUid,
                        invitedByUid = pending.addedByUid
                    )
                )
            }
        }
        _workspaceMembers.value = allMembers.distinctBy {
            val phoneDigits = it.phoneNumber?.filter { c -> c.isDigit() }?.takeLast(10) ?: ""
            if (it.uid.isNotBlank()) it.uid else "pending_$phoneDigits"
        }
    }

    suspend fun addPartnerDirectly(name: String, phone: String, role: String): DirectAddPartnerResult {
        val wsId = getOrResolveWorkspaceId() ?: ""
        if (wsId.isBlank()) {
            return DirectAddPartnerResult.Error("Workspace is not ready")
        }

        val normalizedPhone = normalizePhoneNumber(phone)
        val cleanDigits = normalizedPhone.filter { it.isDigit() }.takeLast(10)

        if (cleanDigits.length < 10) {
            return DirectAddPartnerResult.Error("Please enter a valid 10-digit phone number.")
        }

        // Perform Phone Directory Lookup first to validate single business per partner constraint
        val partnerUid = if (isCloudReady()) firestoreRepository.lookupPhoneInDirectory(normalizedPhone) else null

        if (partnerUid != null && partnerUid == activeUid) {
            return DirectAddPartnerResult.Error("Owner cannot add themselves as Partner.")
        }

        if (isCloudReady()) {
            val isAlreadyPartner = firestoreRepository.checkIsUserOrPhoneAlreadyPartner(partnerUid, normalizedPhone, wsId)
            if (isAlreadyPartner) {
                return DirectAddPartnerResult.Error("This partner is already added to this business.")
            }
        }

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

        if (partnerUid.isNullOrBlank()) {
            val pendingRes = firestoreRepository.savePendingPartnerPhone(
                groupId = wsId,
                normalizedPhone = normalizedPhone,
                displayName = name.trim(),
                role = role.trim().ifBlank { "partner" },
                ownerUid = activeUid ?: ""
            )
            try {
                firestoreRepository.savePartner(wsId, partnerEntity, activeUid)
            } catch (e: Exception) {
                Log.w(TAG, "Local partner save deferred: ${e.message}")
            }
            refreshWorkspaceMembers(wsId)
            return DirectAddPartnerResult.AccountNotRegistered(
                partnerEntity,
                "Partner account not found. It will automatically connect when the partner registers their phone number."
            )
        }

        try {
            firestoreRepository.deletePendingPartnerPhone(wsId, normalizedPhone)
        } catch (_: Exception) {}

        // 3. Directly create workspace membership and user discovery index
        val currentSettings = appSettingsDao.getSettingsForWorkspaceOnce(wsId)
            ?: AppSettingsEntity(workspaceId = wsId)
        val businessName = currentSettings.businessName.ifBlank { "" }

        val addResult = firestoreRepository.addPartnerMemberDirectly(
            workspaceId = wsId,
            partnerUid = partnerUid,
            partnerName = name.trim(),
            partnerPhone = normalizedPhone,
            role = role.trim().ifBlank { "Partner" },
            ownerUid = activeUid ?: "",
            businessName = businessName
        )

        // 4. Resolve partner's personal workspace ID and link to collaboration group
        val partnerUserDoc = firestoreRepository.getWorkspaceDetails(partnerUid) // or lookup in users
        val partnerPersonalWsId = "ws_${partnerUid.replace(Regex("[^a-zA-Z0-9]"), "").take(16).ifBlank { "main" }}"
        firestoreRepository.addPartnerToCollaborationGroup(
            ownerWorkspaceId = wsId,
            ownerUid = activeUid ?: "",
            partnerUid = partnerUid,
            partnerWorkspaceId = partnerPersonalWsId,
            partnerPhone = normalizedPhone,
            partnerDisplayName = name.trim()
        )

        // Update local visible workspaces
        val currentVisible = _visibleWorkspaceIds.value.toMutableSet()
        currentVisible.add(wsId)
        currentVisible.add(partnerPersonalWsId)
        _visibleWorkspaceIds.value = currentVisible
        attachRealtimeListenersForVisibleWorkspaces()

        return if (addResult.isSuccess) {
            refreshWorkspaceMembers(wsId)
            DirectAddPartnerResult.Success(partnerEntity, partnerUid)
        } else {
            val err = addResult.exceptionOrNull()?.message ?: "Could not connect partner"
            DirectAddPartnerResult.Error(err)
        }
    }

    suspend fun removePartner(partner: PartnerEntity, partnerUid: String? = null) {
        removePartnerFromWorkspace(partner, partnerUid)
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

            // Re-evaluate ownership for target workspace
            val isOwner = (targetWorkspaceId == _personalWorkspaceId.value) ||
                    (firestoreRepository.getUserCollaborationGroups(uid).firstOrNull { it.groupId == targetWorkspaceId }?.ownerUid == uid)
            _isCollaborationOwner.value = isOwner
            Log.d("TRAC_WORKSPACE", "ACTIVATE uid=$uid workspace=$targetWorkspaceId isOwner=$isOwner")

            // Reconnect real-time listeners for the new workspace
            if (isCloudReady()) {
                _visibleWorkspaceIds.value = setOf(targetWorkspaceId)
                attachRealtimeListenersForVisibleWorkspaces()
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

    suspend fun removePartnerFromWorkspace(partner: PartnerEntity, targetPartnerUid: String? = null) {
        val currentWsId = _personalWorkspaceId.value ?: _activeWorkspaceId.value ?: partner.workspaceId
        val isOwner = _isCollaborationOwner.value
        if (!isOwner) {
            Log.w(TAG, "Unauthorized: Non-owner cannot remove partner")
            return
        }
        try {
            partnerDao.deletePartner(partner)
            if (isCloudReady() && currentWsId.isNotBlank()) {
                scope.launch {
                    try {
                        val partnerUid = targetPartnerUid?.ifBlank { null }
                            ?: firestoreRepository.lookupPhoneInDirectory(partner.phone)
                        firestoreRepository.deletePartner(currentWsId, partner.id)
                        firestoreRepository.removePartnerFromWorkspace(
                            workspaceId = currentWsId,
                            partnerUid = partnerUid,
                            partnerPhone = partner.phone
                        )
                        val normPhone = normalizePhoneNumber(partner.phone)
                        if (normPhone.isNotBlank()) {
                            firestoreRepository.deletePendingPartnerPhone(currentWsId, normPhone)
                        }
                        if (!partnerUid.isNullOrBlank()) {
                            firestoreRepository.removePartnerFromCollaborationGroup(currentWsId, partnerUid)
                            val partnerPersonalWsId = "ws_${partnerUid.replace(Regex("[^a-zA-Z0-9]"), "").take(16).ifBlank { "main" }}"
                            val currentVisible = _visibleWorkspaceIds.value.toMutableSet()
                            currentVisible.remove(partnerPersonalWsId)
                            _visibleWorkspaceIds.value = currentVisible
                            attachRealtimeListenersForVisibleWorkspaces()
                        }
                        refreshWorkspaceMembers(currentWsId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error removing partner in cloud: ${e.message}", e)
                    }
                }
            }
            Log.d("TRAC_PARTNER", "Removed partner ${partner.name} from workspace $currentWsId")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing partner: ${e.message}", e)
        }
    }

    suspend fun leaveCollaborationGroup(ownerWorkspaceId: String) {
        val uid = activeUid ?: ""
        val currentSettings = appSettingsDao.getSettingsForWorkspaceOnce(getOrResolveWorkspaceId() ?: "")
        val currentPhone = currentSettings?.activePartnerPhone ?: ""

        if (isCloudReady() && ownerWorkspaceId.isNotBlank() && uid.isNotBlank()) {
            try {
                firestoreRepository.removePartnerFromWorkspace(
                    workspaceId = ownerWorkspaceId,
                    partnerUid = uid,
                    partnerPhone = currentPhone
                )
                firestoreRepository.removePartnerFromCollaborationGroup(ownerWorkspaceId, uid)
                val currentVisible = _visibleWorkspaceIds.value.toMutableSet()
                currentVisible.remove(ownerWorkspaceId)
                _visibleWorkspaceIds.value = currentVisible
                attachRealtimeListenersForVisibleWorkspaces()
                refreshWorkspaceMembers()
                Log.d("TRAC_PARTNER", "User $uid left collaboration group $ownerWorkspaceId")
                
                if (_activeWorkspaceId.value == ownerWorkspaceId) {
                    val available = getAvailableWorkspaces(UserProfile(uid = uid))
                    val fallbackId = available.firstOrNull()?.workspaceId ?: _personalWorkspaceId.value
                    if (fallbackId != null && fallbackId != ownerWorkspaceId) {
                        switchActiveWorkspace(fallbackId, UserProfile(uid = uid))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error leaving collaboration group: ${e.message}", e)
            }
        }
    }

    suspend fun getGenuinePartnerWorkspaces(userProfile: UserProfile, personalWsId: String): List<Workspace> {
        val uid = userProfile.uid
        if (uid.isBlank()) return emptyList()
        val genuinePartners = mutableMapOf<String, Workspace>()

        // 1. Discover partner workspaces from userCollaborationGroups
        if (isCloudReady()) {
            try {
                val groupIndices = firestoreRepository.getUserCollaborationGroups(uid)
                for (grp in groupIndices) {
                    val gId = grp.groupId.trim()
                    val ownerUid = grp.ownerUid.trim()
                    val role = grp.role.trim()
                    val status = grp.status.trim()

                    // Strict Genuine Partner Rules:
                    // - Owner's own workspace must NEVER count as a partner workspace
                    // - Owner's own collaborationGroups document must NEVER count as partner membership
                    if (gId.isBlank() || gId == personalWsId) continue
                    if (grp.ownerWorkspaceId.isNotBlank() && grp.ownerWorkspaceId == personalWsId) continue
                    if (role.equals("owner", ignoreCase = true)) continue
                    if (ownerUid.isBlank() || ownerUid == uid) continue
                    if (status.isNotBlank() && !status.equals("active", ignoreCase = true)) continue

                    val localWsSettings = appSettingsDao.getSettingsForWorkspaceOnce(gId)
                    val businessName = localWsSettings?.businessName?.ifBlank { null }
                        ?: try {
                            val details = firestoreRepository.getWorkspaceDetails(gId)
                            details?.name?.ifBlank { null }
                        } catch (_: Exception) { null }
                        ?: "Partner Business"

                    genuinePartners[gId] = Workspace(
                        workspaceId = gId,
                        name = businessName,
                        ownerUid = ownerUid,
                        createdAt = grp.joinedAt
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching userCollaborationGroups: ${e.message}")
            }

            // 2. Discover partner workspaces from userWorkspaceMemberships
            try {
                val memberships = firestoreRepository.getUserWorkspaceMemberships(uid)
                for (m in memberships) {
                    val wsId = (m["workspaceId"] as? String)?.trim() ?: continue
                    val ownerUid = (m["ownerUid"] as? String)?.trim() ?: ""
                    val role = (m["role"] as? String)?.trim() ?: ""
                    val status = (m["status"] as? String)?.trim() ?: "active"

                    if (wsId.isBlank() || wsId == personalWsId) continue
                    if (role.equals("owner", ignoreCase = true)) continue
                    if (ownerUid.isBlank() || ownerUid == uid) continue
                    if (status.isNotBlank() && !status.equals("active", ignoreCase = true)) continue
                    if (genuinePartners.containsKey(wsId)) continue

                    val details = firestoreRepository.getWorkspaceDetails(wsId)
                    val wsName = details?.name?.ifBlank { null }
                        ?: (m["workspaceName"] as? String)?.ifBlank { null }
                        ?: appSettingsDao.getSettingsForWorkspaceOnce(wsId)?.businessName?.ifBlank { null }
                        ?: "Partner Business"

                    genuinePartners[wsId] = Workspace(
                        workspaceId = wsId,
                        name = wsName,
                        ownerUid = ownerUid,
                        createdAt = (m["joinedAt"] as? Long) ?: System.currentTimeMillis()
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching userWorkspaceMemberships: ${e.message}")
            }
        }

        // 3. User profile listed workspaces (with strict ownerUid != uid verification)
        for (wsId in userProfile.workspaces) {
            val cleanId = wsId.trim()
            if (cleanId.isBlank() || cleanId == personalWsId || genuinePartners.containsKey(cleanId)) continue
            try {
                val ws = firestoreRepository.getWorkspaceDetails(cleanId)
                if (ws != null && ws.ownerUid.isNotBlank() && ws.ownerUid != uid) {
                    genuinePartners[cleanId] = ws
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error checking workspace $cleanId: ${e.message}")
            }
        }

        Log.d("TRAC_WORKSPACE", "uid=$uid personal=$personalWsId genuinePartnerWorkspaces=${genuinePartners.keys}")
        return genuinePartners.values.toList()
    }

    suspend fun getAvailableWorkspaces(userProfile: UserProfile): List<Workspace> {
        val uid = userProfile.uid
        val personalWsId = _personalWorkspaceId.value 
            ?: userProfile.defaultWorkspaceId?.ifBlank { null }
            ?: "ws_${uid.replace(Regex("[^a-zA-Z0-9]"), "").take(16).ifBlank { "main" }}"
        val list = getGenuinePartnerWorkspaces(userProfile, personalWsId)
        _genuinePartnerWorkspaces.value = list
        return list
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
            scope.launch {
                try {
                    firestoreRepository.saveSettings(wsId, scoped, activeUid)
                } catch (e: Exception) {
                    Log.w(TAG, "Settings update cloud sync deferred: ${e.message}")
                }
            }
        }
    }

    /**
     * Safely retries pushing all locally unsynced records and pending deletions to Cloud.
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
        if (activeUid == null) {
            activeUid = currentUid
        }
        if (activeUid == null || (currentUid != null && activeUid != currentUid)) {
            return SyncResult(
                isSuccess = false,
                syncedItemsCount = 0,
                message = "Workspace not ready or sign-in mismatch."
            )
        }

        val fallbackWsId = targetWorkspaceId
            ?: (_workspaceInitState.value as? WorkspaceInitState.Ready)?.workspaceId
            ?: _activeWorkspaceId.value
            ?: _personalWorkspaceId.value
            ?: ""

        var syncedCount = 0

        // 1. Process all pending deletions first so cloud copies are deleted
        val pendingDeletions = pendingDeleteManager.getAllPendingDeletions()
        for (del in pendingDeletions) {
            try {
                when (del.entityType) {
                    "JOB" -> firestoreRepository.deleteJob(del.workspaceId, del.recordId)
                    "EXPENSE" -> firestoreRepository.deleteExpense(del.workspaceId, del.recordId)
                    "CUSTOMER" -> firestoreRepository.deleteCustomer(del.workspaceId, del.recordId)
                    "WITHDRAWAL" -> firestoreRepository.deleteWithdrawal(del.workspaceId, del.recordId)
                    "TRACTOR" -> firestoreRepository.deleteTractor(del.workspaceId, del.recordId)
                    "PARTNER" -> firestoreRepository.deletePartner(del.workspaceId, del.recordId)
                }
                pendingDeleteManager.removePendingDelete(del.entityType, del.recordId, del.workspaceId)
                syncedCount++
            } catch (e: Exception) {
                Log.w(TAG, "Pending delete for ${del.entityType} ${del.recordId} deferred: ${e.message}")
            }
        }

        // 2. Fetch unsynced items sorted by creation time
        val unsyncedCustomers = customerDao.getUnsyncedCustomers().sortedBy { it.createdAt }
        val unsyncedJobs = jobEntryDao.getUnsyncedJobs().sortedBy { it.createdAt }
        val unsyncedExpenses = expenseDao.getUnsyncedExpenses().sortedBy { it.createdAt }
        val unsyncedWithdrawals = withdrawalDao.getUnsyncedWithdrawals().sortedBy { it.createdAt }

        val totalRecordsCount = pendingDeletions.size + unsyncedJobs.size + unsyncedExpenses.size + unsyncedWithdrawals.size + unsyncedCustomers.size
        if (totalRecordsCount == 0) {
            return SyncResult(
                isSuccess = true,
                syncedItemsCount = 0,
                message = "All records are already in sync with Cloud."
            )
        }

        // 3. Sync Customers first so FK relationships exist
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

        // 4. Sync Jobs
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

        // 5. Sync Expenses
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

        // 6. Sync Withdrawals
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

        // 7. Ensure settings for visible workspaces are synchronized
        val visible = _visibleWorkspaceIds.value.ifEmpty { setOf(fallbackWsId).filter { it.isNotBlank() } }
        for (ws in visible) {
            val localSettings = appSettingsDao.getSettingsForWorkspaceOnce(ws)
            if (localSettings != null) {
                try {
                    firestoreRepository.saveSettings(ws, localSettings, activeUid)
                } catch (e: Exception) {
                    Log.w(TAG, "Settings sync for $ws deferred: ${e.message}")
                }
            }
        }

        val success = (syncedCount >= totalRecordsCount)
        return SyncResult(
            isSuccess = success,
            syncedItemsCount = syncedCount,
            message = if (success) "Pushed $syncedCount offline records to Cloud successfully!"
                      else "Synced $syncedCount of $totalRecordsCount items. Cloud sync pending for remainder."
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
