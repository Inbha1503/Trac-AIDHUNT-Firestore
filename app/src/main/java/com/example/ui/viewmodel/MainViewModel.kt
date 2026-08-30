package com.example.ui.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.CustomerEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PartnerEntity
import com.example.data.entity.TractorEntity
import com.example.data.entity.WithdrawalEntity
import com.example.data.firebase.AuthState
import com.example.data.firebase.UserProfile
import com.example.data.firebase.Workspace
import com.example.data.firebase.WorkspaceInvitation
import com.example.data.firebase.WorkspaceMember
import com.example.data.network.NetworkMonitor
import com.example.data.repository.AuthRepository
import com.example.data.repository.SettingsSyncState
import com.example.data.repository.TractorRepository
import com.example.data.repository.WorkspaceInitState
import com.example.data.repository.WorkspaceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class BottomTab {
    HOME,
    REPORT,
    NEW_ENTRY,
    ACCOUNT
}

enum class ReportSubPage {
    MENU,
    EXPENSES,
    BALANCE_SHEET,
    WITHDRAWAL,
    CUSTOMER_CREDIT_DUE
}

enum class AccountSubPage {
    MAIN,
    MANAGE_TRACTORS,
    MANAGE_PARTNERS,
    SETTINGS,
    EDIT_PROFILE,
    SQLITE_SYNC_STATUS
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val repository = TractorRepository(database)
    private val authRepository = AuthRepository.getInstance(application)
    private val workspaceRepository = WorkspaceRepository.getInstance(application, database)
    private val networkMonitor = NetworkMonitor(application)

    // Workspace & Authentication State
    val currentWorkspace: StateFlow<Workspace?> = workspaceRepository.currentWorkspace
    val isWorkspaceInitialized: StateFlow<Boolean> = workspaceRepository.isInitialized
    val settingsSyncState: StateFlow<SettingsSyncState> = workspaceRepository.settingsSyncState
    val authState: StateFlow<AuthState> = authRepository.authState
    val currentUserProfile: StateFlow<UserProfile?> = authRepository.currentUserProfile
    val currentUid: String?
        get() = authRepository.currentUid

    // Phone verification ID tracker for OTP flow
    private val _phoneVerificationId = MutableStateFlow<String?>(null)
    val phoneVerificationId: StateFlow<String?> = _phoneVerificationId.asStateFlow()

    // Sync State
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow("All partner data in sync with Cloud (Offline Ready)")
    val syncMessage: StateFlow<String> = _syncMessage.asStateFlow()

    // Live Network State
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline

    // Force Simulated Offline Toggle for user demonstration/testing
    private val _simulatedOffline = MutableStateFlow(false)
    val simulatedOffline: StateFlow<Boolean> = _simulatedOffline.asStateFlow()

    // Effective online state (considers real network + simulation toggle)
    val isEffectiveOnline: StateFlow<Boolean> = combine(isOnline, _simulatedOffline) { online, simOff ->
        online && !simOff
    }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // App Navigation & Tab State
    private val _currentTab = MutableStateFlow(BottomTab.HOME)
    val currentTab: StateFlow<BottomTab> = _currentTab.asStateFlow()

    private val _currentReportSubPage = MutableStateFlow(ReportSubPage.MENU)
    val currentReportSubPage: StateFlow<ReportSubPage> = _currentReportSubPage.asStateFlow()

    private val _currentAccountSubPage = MutableStateFlow(AccountSubPage.MAIN)
    val currentAccountSubPage: StateFlow<AccountSubPage> = _currentAccountSubPage.asStateFlow()

    // Persistent in-memory draft state for New Work Entry
    private val _newEntryDraft = MutableStateFlow<NewEntryDraft?>(null)
    val newEntryDraft: StateFlow<NewEntryDraft?> = _newEntryDraft.asStateFlow()

    val workspaceInitState: StateFlow<WorkspaceInitState> = workspaceRepository.workspaceInitState
    val activeWorkspaceId: StateFlow<String?> = workspaceRepository.activeWorkspaceId
    val pendingInvitations: StateFlow<List<WorkspaceInvitation>> = workspaceRepository.pendingInvitations
    val workspaceMembers: StateFlow<List<WorkspaceMember>> = workspaceRepository.workspaceMembers

    private val _availableWorkspaces = MutableStateFlow<List<Workspace>>(emptyList())
    val availableWorkspaces: StateFlow<List<Workspace>> = _availableWorkspaces.asStateFlow()

    fun loadAvailableWorkspaces() {
        val profile = currentUserProfile.value ?: return
        viewModelScope.launch {
            val list = workspaceRepository.getAvailableWorkspaces(profile)
            _availableWorkspaces.value = list
        }
    }

    fun switchWorkspace(
        targetWorkspaceId: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val profile = currentUserProfile.value
        if (profile == null) {
            onError("User profile not loaded")
            return
        }
        viewModelScope.launch {
            _isSyncing.value = true
            val result = workspaceRepository.switchActiveWorkspace(targetWorkspaceId, profile)
            _isSyncing.value = false
            result.onSuccess {
                loadAvailableWorkspaces()
                onSuccess()
            }.onFailure { e ->
                onError(e.message ?: "Failed to switch workspace")
            }
        }
    }

    // Unsynced entity counters scoped by active workspace
    @OptIn(ExperimentalCoroutinesApi::class)
    val unsyncedJobsCount: StateFlow<Int> = activeWorkspaceId.flatMapLatest { wsId ->
        if (wsId.isNullOrBlank()) MutableStateFlow(0)
        else database.jobEntryDao().getUnsyncedCountForWorkspace(wsId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val unsyncedExpensesCount: StateFlow<Int> = activeWorkspaceId.flatMapLatest { wsId ->
        if (wsId.isNullOrBlank()) MutableStateFlow(0)
        else database.expenseDao().getUnsyncedCountForWorkspace(wsId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val unsyncedWithdrawalsCount: StateFlow<Int> = activeWorkspaceId.flatMapLatest { wsId ->
        if (wsId.isNullOrBlank()) MutableStateFlow(0)
        else database.withdrawalDao().getUnsyncedCountForWorkspace(wsId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val unsyncedCustomersCount: StateFlow<Int> = activeWorkspaceId.flatMapLatest { wsId ->
        if (wsId.isNullOrBlank()) MutableStateFlow(0)
        else database.customerDao().getUnsyncedCountForWorkspace(wsId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalUnsyncedCount: StateFlow<Int> = combine(
        unsyncedJobsCount,
        unsyncedExpensesCount,
        unsyncedWithdrawalsCount,
        unsyncedCustomersCount
    ) { jobs, exp, wth, cust ->
        jobs + exp + wth + cust
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // 1. Settings & Profile scoped to active workspace
    @OptIn(ExperimentalCoroutinesApi::class)
    val settings: StateFlow<AppSettingsEntity> = activeWorkspaceId.flatMapLatest { wsId ->
        if (wsId.isNullOrBlank()) database.appSettingsDao().getSettings()
        else database.appSettingsDao().getSettingsForWorkspace(wsId)
    }.combine(MutableStateFlow(Unit)) { set, _ -> set ?: AppSettingsEntity(workspaceId = activeWorkspaceId.value ?: "") }
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettingsEntity())

    // 2. Partners scoped to active workspace
    @OptIn(ExperimentalCoroutinesApi::class)
    val partners: StateFlow<List<PartnerEntity>> = activeWorkspaceId.flatMapLatest { wsId ->
        if (wsId.isNullOrBlank()) MutableStateFlow(emptyList())
        else database.partnerDao().getPartnersForWorkspace(wsId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 3. Tractors scoped to active workspace
    @OptIn(ExperimentalCoroutinesApi::class)
    val tractors: StateFlow<List<TractorEntity>> = activeWorkspaceId.flatMapLatest { wsId ->
        if (wsId.isNullOrBlank()) MutableStateFlow(emptyList())
        else database.tractorDao().getTractorsForWorkspace(wsId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 4. Customers scoped to active workspace
    @OptIn(ExperimentalCoroutinesApi::class)
    val customers: StateFlow<List<CustomerEntity>> = activeWorkspaceId.flatMapLatest { wsId ->
        if (wsId.isNullOrBlank()) MutableStateFlow(emptyList())
        else database.customerDao().getCustomersForWorkspace(wsId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val customersWithDue: StateFlow<List<CustomerEntity>> = activeWorkspaceId.flatMapLatest { wsId ->
        if (wsId.isNullOrBlank()) MutableStateFlow(emptyList())
        else database.customerDao().getCustomersWithDueForWorkspace(wsId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 5. Jobs scoped to active workspace
    @OptIn(ExperimentalCoroutinesApi::class)
    val jobs: StateFlow<List<JobEntryEntity>> = activeWorkspaceId.flatMapLatest { wsId ->
        if (wsId.isNullOrBlank()) MutableStateFlow(emptyList())
        else database.jobEntryDao().getJobsForWorkspace(wsId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 6. Expenses scoped to active workspace
    @OptIn(ExperimentalCoroutinesApi::class)
    val expenses: StateFlow<List<ExpenseEntity>> = activeWorkspaceId.flatMapLatest { wsId ->
        if (wsId.isNullOrBlank()) MutableStateFlow(emptyList())
        else database.expenseDao().getExpensesForWorkspace(wsId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 7. Withdrawals scoped to active workspace
    @OptIn(ExperimentalCoroutinesApi::class)
    val withdrawals: StateFlow<List<WithdrawalEntity>> = activeWorkspaceId.flatMapLatest { wsId ->
        if (wsId.isNullOrBlank()) MutableStateFlow(emptyList())
        else database.withdrawalDao().getWithdrawalsForWorkspace(wsId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Aggregates & Financial calculations scoped to active workspace
    @OptIn(ExperimentalCoroutinesApi::class)
    val totalReceived: StateFlow<Double> = activeWorkspaceId.flatMapLatest { wsId ->
        if (wsId.isNullOrBlank()) MutableStateFlow(0.0)
        else database.jobEntryDao().getTotalReceivedForWorkspace(wsId)
    }.combine(MutableStateFlow(Unit)) { total, _ -> total ?: 0.0 }
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val totalPending: StateFlow<Double> = activeWorkspaceId.flatMapLatest { wsId ->
        if (wsId.isNullOrBlank()) MutableStateFlow(0.0)
        else database.jobEntryDao().getTotalPendingForWorkspace(wsId)
    }.combine(MutableStateFlow(Unit)) { total, _ -> total ?: 0.0 }
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val totalExpenses: StateFlow<Double> = activeWorkspaceId.flatMapLatest { wsId ->
        if (wsId.isNullOrBlank()) MutableStateFlow(0.0)
        else database.expenseDao().getTotalExpensesForWorkspace(wsId)
    }.combine(MutableStateFlow(Unit)) { total, _ -> total ?: 0.0 }
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val totalWithdrawn: StateFlow<Double> = activeWorkspaceId.flatMapLatest { wsId ->
        if (wsId.isNullOrBlank()) MutableStateFlow(0.0)
        else database.withdrawalDao().getTotalWithdrawnForWorkspace(wsId)
    }.combine(MutableStateFlow(Unit)) { total, _ -> total ?: 0.0 }
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Available Amount = Total Received - Total Expenses - Total Withdrawn
    val availableAmount: StateFlow<Double> = combine(
        totalReceived,
        totalExpenses,
        totalWithdrawn
    ) { rec, exp, wth ->
        rec - exp - wth
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Net Balance = Total Received - Total Expenses
    val netBalance: StateFlow<Double> = combine(
        totalReceived,
        totalExpenses
    ) { rec, exp ->
        rec - exp
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    init {
        // Start listening to Firebase Auth state
        authRepository.startListening()

        viewModelScope.launch {
            authState.collect { state ->
                when (state) {
                    is AuthState.Authenticated -> {
                        val prof = state.profile
                        if (workspaceRepository.workspaceInitState.value !is WorkspaceInitState.Ready) {
                            workspaceRepository.initializeForUser(prof)
                        }
                    }
                    is AuthState.Unauthenticated -> {
                        // Stop real-time listeners on logout
                        workspaceRepository.stopWorkspaceListeners()
                        val current = settings.value
                        if (current.isLoggedIn) {
                            repository.updateSettings(current.copy(isLoggedIn = false))
                        }
                    }
                    else -> Unit
                }
            }
        }

        // Automatically push to cloud whenever network comes back online
        viewModelScope.launch {
            isEffectiveOnline.collect { online ->
                if (online) {
                    pushUnsyncedToCloud()
                }
            }
        }
    }

    fun setBottomTab(tab: BottomTab) {
        _currentTab.value = tab
        _currentReportSubPage.value = ReportSubPage.MENU
        _currentAccountSubPage.value = AccountSubPage.MAIN
    }

    fun setReportSubPage(subPage: ReportSubPage) {
        _currentReportSubPage.value = subPage
    }

    fun setAccountSubPage(subPage: AccountSubPage) {
        _currentAccountSubPage.value = subPage
    }

    fun toggleSimulatedOffline(forceOffline: Boolean) {
        _simulatedOffline.value = forceOffline
        if (!forceOffline) {
            pushUnsyncedToCloud()
        }
    }

    // --- Draft Management ---

    fun updateNewEntryDraft(draft: NewEntryDraft) {
        _newEntryDraft.value = draft
    }

    fun clearNewEntryDraft() {
        val set = settings.value
        val defaultTrac = if (set.lockedTractorLabel.isNotBlank()) set.lockedTractorLabel else (tractors.value.firstOrNull()?.label ?: "Mahindra 575 DI")
        _newEntryDraft.value = NewEntryDraft.createDefault(
            defaultTractor = defaultTrac,
            lockedTractor = set.lockedTractorLabel,
            defaultHourlyRate = set.defaultHourlyRate
        )
    }

    // --- Actions ---

    fun saveJobEntry(
        job: JobEntryEntity,
        linkedExpense: ExpenseEntity? = null,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                workspaceRepository.saveJobEntry(job, linkedExpense)
                clearNewEntryDraft()
                _syncMessage.value = "Saved successfully and synced to Cloud Workspace"
                onSuccess()
            } catch (e: Exception) {
                clearNewEntryDraft()
                val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "SYNC_FAILED"
                _syncMessage.value = "Saved locally. Cloud sync pending ($code)"
                android.util.Log.e("TRAC_ENTRY", "Cloud sync failed while saving entry: $code ${e.message}", e)
                onSuccess()
            }
        }
    }

    fun deleteJob(job: JobEntryEntity) {
        viewModelScope.launch {
            try {
                workspaceRepository.deleteJob(job)
            } catch (e: Exception) {
                android.util.Log.w("TRAC_ENTRY", "Cloud delete job failed: ${e.message}")
            }
        }
    }

    fun addExpense(expense: ExpenseEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                workspaceRepository.addExpense(expense)
                _syncMessage.value = "Expense saved and synced with Cloud"
                onSuccess()
            } catch (e: Exception) {
                val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "SYNC_FAILED"
                _syncMessage.value = "Expense saved locally. Cloud sync pending ($code)"
                android.util.Log.e("TRAC_FIRESTORE", "Cloud sync failed for expense: $code ${e.message}", e)
                onSuccess()
            }
        }
    }

    fun updateExpense(expense: ExpenseEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                workspaceRepository.updateExpense(expense)
                onSuccess()
            } catch (e: Exception) {
                val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "SYNC_FAILED"
                _syncMessage.value = "Expense updated locally. Cloud sync pending ($code)"
                android.util.Log.e("TRAC_FIRESTORE", "Cloud update failed for expense: $code ${e.message}", e)
                onSuccess()
            }
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            try {
                workspaceRepository.deleteExpense(expense)
            } catch (e: Exception) {
                android.util.Log.w("TRAC_FIRESTORE", "Cloud delete expense failed: ${e.message}")
            }
        }
    }

    fun addWithdrawal(withdrawal: WithdrawalEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                workspaceRepository.addWithdrawal(withdrawal)
                _syncMessage.value = "Withdrawal saved and synced with Cloud"
                onSuccess()
            } catch (e: Exception) {
                val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "SYNC_FAILED"
                _syncMessage.value = "Withdrawal saved locally. Cloud sync pending ($code)"
                android.util.Log.e("TRAC_FIRESTORE", "Cloud sync failed for withdrawal: $code ${e.message}", e)
                onSuccess()
            }
        }
    }

    fun deleteWithdrawal(withdrawal: WithdrawalEntity) {
        viewModelScope.launch {
            try {
                workspaceRepository.deleteWithdrawal(withdrawal)
            } catch (e: Exception) {
                android.util.Log.w("TRAC_FIRESTORE", "Cloud delete withdrawal failed: ${e.message}")
            }
        }
    }

    fun updateCustomer(customer: CustomerEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                workspaceRepository.updateCustomer(customer)
                onSuccess()
            } catch (e: Exception) {
                val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "SYNC_FAILED"
                _syncMessage.value = "Customer updated locally. Cloud sync pending ($code)"
                android.util.Log.e("TRAC_FIRESTORE", "Cloud update customer failed: $code ${e.message}", e)
                onSuccess()
            }
        }
    }

    fun recordCustomerPayment(
        customer: CustomerEntity,
        amount: Double,
        dateTimestamp: Long,
        paymentMethod: String,
        note: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                workspaceRepository.recordCustomerPayment(
                    customer = customer,
                    amount = amount,
                    dateTimestamp = dateTimestamp,
                    paymentMethod = paymentMethod,
                    note = note,
                    operatorName = settings.value.activePartnerName
                )
                _syncMessage.value = "Payment recorded and synced with Cloud"
                onSuccess()
            } catch (e: Exception) {
                val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "SYNC_FAILED"
                _syncMessage.value = "Payment recorded locally. Cloud sync pending ($code)"
                android.util.Log.e("TRAC_FIRESTORE", "Cloud payment recording failed: $code ${e.message}", e)
                onSuccess()
            }
        }
    }

    fun deleteCustomer(customer: CustomerEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                workspaceRepository.deleteCustomer(customer)
                onSuccess()
            } catch (e: Exception) {
                android.util.Log.w("TRAC_FIRESTORE", "Cloud delete customer failed: ${e.message}")
                onSuccess()
            }
        }
    }

    fun addTractor(tractor: TractorEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                workspaceRepository.addTractor(tractor)
                onSuccess()
            } catch (e: Exception) {
                android.util.Log.w("TRAC_FIRESTORE", "Cloud add tractor failed: ${e.message}")
                onSuccess()
            }
        }
    }

    fun updateTractor(tractor: TractorEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                workspaceRepository.updateTractor(tractor)
                onSuccess()
            } catch (e: Exception) {
                android.util.Log.w("TRAC_FIRESTORE", "Cloud update tractor failed: ${e.message}")
                onSuccess()
            }
        }
    }

    fun deleteTractor(tractor: TractorEntity) {
        viewModelScope.launch {
            try {
                workspaceRepository.deleteTractor(tractor)
            } catch (e: Exception) {
                android.util.Log.w("TRAC_FIRESTORE", "Cloud delete tractor failed: ${e.message}")
            }
        }
    }

    fun addPartner(
        partner: PartnerEntity,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSyncing.value = true
            val res = workspaceRepository.addPartnerDirectly(partner.name, partner.phone, partner.role)
            _isSyncing.value = false
            when (res) {
                is WorkspaceRepository.DirectAddPartnerResult.Success -> {
                    onSuccess()
                }
                is WorkspaceRepository.DirectAddPartnerResult.AccountNotRegistered -> {
                    onError(res.message)
                }
                is WorkspaceRepository.DirectAddPartnerResult.Error -> {
                    onError(res.message)
                }
            }
        }
    }

    fun addPartnerDirectly(
        name: String,
        phone: String,
        role: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            _isSyncing.value = true
            val res = workspaceRepository.addPartnerDirectly(name, phone, role)
            _isSyncing.value = false
            when (res) {
                is WorkspaceRepository.DirectAddPartnerResult.Success -> {
                    onResult(true, "Partner connected successfully!")
                }
                is WorkspaceRepository.DirectAddPartnerResult.AccountNotRegistered -> {
                    onResult(false, res.message)
                }
                is WorkspaceRepository.DirectAddPartnerResult.Error -> {
                    onResult(false, res.message)
                }
            }
        }
    }

    fun updatePartner(partner: PartnerEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                workspaceRepository.updatePartner(partner)
                onSuccess()
            } catch (e: Exception) {
                android.util.Log.w("TRAC_FIRESTORE", "Cloud update partner failed: ${e.message}")
                onSuccess()
            }
        }
    }

    fun deletePartner(partner: PartnerEntity) {
        viewModelScope.launch {
            try {
                workspaceRepository.removePartnerFromWorkspace(partner)
            } catch (e: Exception) {
                android.util.Log.w("TRAC_FIRESTORE", "Delete partner failed: ${e.message}")
            }
        }
    }

    fun setActivePartner(partner: PartnerEntity) {
        viewModelScope.launch {
            val current = settings.value
            val updated = current.copy(
                activePartnerName = "${partner.name} (${partner.role})",
                activePartnerPhone = partner.phone
            )
            workspaceRepository.updateSettings(updated)
        }
    }

    fun updateSettings(updated: AppSettingsEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                workspaceRepository.updateSettings(updated)
                onSuccess()
            } catch (e: Exception) {
                android.util.Log.w("TRAC_FIRESTORE", "Cloud update settings failed: ${e.message}")
                onSuccess()
            }
        }
    }

    fun pushUnsyncedToCloud(onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val online = isEffectiveOnline.value
            if (!online) {
                _syncMessage.value = "Device is offline. Local SQLite records safe."
                onComplete(false)
                return@launch
            }

            _isSyncing.value = true
            _syncMessage.value = "Pushing local SQLite entries to Cloud..."
            delay(800) // Smooth sync visual feedback

            val result = workspaceRepository.pushUnsyncedToCloud(isOnline = true)
            _isSyncing.value = false
            _syncMessage.value = result.message
            onComplete(result.isSuccess)
        }
    }

    fun triggerSync() {
        pushUnsyncedToCloud()
    }

    fun getJobsForCustomer(customerId: Long) = database.jobEntryDao().getJobsForCustomer(customerId)

    // --- Firebase Authentication ---

    fun signInWithGoogle(
        context: Context,
        webClientId: String? = null,
        onSuccess: (UserProfile) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSyncing.value = true
            val result = authRepository.signInWithGoogle(context, webClientId)
            if (result.isSuccess) {
                val profile = result.getOrThrow()
                // AWAIT workspace initialization before triggering onSuccess or marking auth complete
                val initResult = workspaceRepository.initializeForUser(profile)
                _isSyncing.value = false
                initResult.onSuccess {
                    loadAvailableWorkspaces()
                    onSuccess(profile)
                }.onFailure { e ->
                    onError(e.message ?: "Workspace initialization failed")
                }
            } else {
                _isSyncing.value = false
                val e = result.exceptionOrNull()
                onError(e?.message ?: "Google Sign-In failed")
            }
        }
    }

    fun sendPhoneOtp(
        activity: Activity,
        phone: String,
        onCodeSent: () -> Unit = {},
        onError: (String) -> Unit = {},
        onAutoVerified: (UserProfile) -> Unit = {}
    ) {
        _isSyncing.value = true
        authRepository.sendPhoneOtp(
            activity = activity,
            phoneNumber = phone,
            onCodeSent = { verificationId ->
                _phoneVerificationId.value = verificationId
                _isSyncing.value = false
                onCodeSent()
            },
            onError = { msg ->
                _isSyncing.value = false
                onError(msg)
            },
            onAutoVerified = { profile ->
                viewModelScope.launch {
                    val initResult = workspaceRepository.initializeForUser(profile)
                    _isSyncing.value = false
                    initResult.onSuccess {
                        loadAvailableWorkspaces()
                        onAutoVerified(profile)
                    }.onFailure { e ->
                        onError(e.message ?: "Workspace initialization failed")
                    }
                }
            }
        )
    }

    fun verifyPhoneOtp(
        otpCode: String,
        onSuccess: (UserProfile) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val verificationId = _phoneVerificationId.value
        if (verificationId == null) {
            onError("Verification ID missing. Please request a new OTP.")
            return
        }

        viewModelScope.launch {
            _isSyncing.value = true
            val result = authRepository.verifyPhoneOtp(verificationId, otpCode)
            if (result.isSuccess) {
                val profile = result.getOrThrow()
                // AWAIT workspace initialization before triggering onSuccess or marking auth complete
                val initResult = workspaceRepository.initializeForUser(profile)
                _isSyncing.value = false
                initResult.onSuccess {
                    loadAvailableWorkspaces()
                    onSuccess(profile)
                }.onFailure { e ->
                    onError(e.message ?: "Workspace initialization failed")
                }
            } else {
                _isSyncing.value = false
                val e = result.exceptionOrNull()
                onError(e?.message ?: "Invalid OTP Code")
            }
        }
    }

    fun logout(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                authRepository.signOut()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            workspaceRepository.stopWorkspaceListeners()
            val current = settings.value
            if (current.isLoggedIn) {
                workspaceRepository.updateSettings(current.copy(isLoggedIn = false))
            }
            onComplete()
        }
    }
}

