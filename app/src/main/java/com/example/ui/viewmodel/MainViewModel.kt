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
import com.example.data.network.NetworkMonitor
import com.example.data.repository.AuthRepository
import com.example.data.repository.TractorRepository
import com.example.data.repository.WorkspaceRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

    // Unsynced entity counters
    val totalUnsyncedCount: StateFlow<Int> = repository.totalUnsyncedCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val unsyncedJobsCount: StateFlow<Int> = repository.unsyncedJobsCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val unsyncedExpensesCount: StateFlow<Int> = repository.unsyncedExpensesCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val unsyncedWithdrawalsCount: StateFlow<Int> = repository.unsyncedWithdrawalsCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val unsyncedCustomersCount: StateFlow<Int> = repository.unsyncedCustomersCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // 1. Settings & Profile
    val settings: StateFlow<AppSettingsEntity> = repository.settingsFlow
        .combine(MutableStateFlow(Unit)) { set, _ -> set ?: AppSettingsEntity() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettingsEntity())

    // 2. Partners
    val partners: StateFlow<List<PartnerEntity>> = repository.allPartners
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 3. Tractors
    val tractors: StateFlow<List<TractorEntity>> = repository.allTractors
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 4. Customers
    val customers: StateFlow<List<CustomerEntity>> = repository.allCustomers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customersWithDue: StateFlow<List<CustomerEntity>> = repository.customersWithDue
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 5. Jobs
    val jobs: StateFlow<List<JobEntryEntity>> = repository.allJobs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 6. Expenses
    val expenses: StateFlow<List<ExpenseEntity>> = repository.allExpenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 7. Withdrawals
    val withdrawals: StateFlow<List<WithdrawalEntity>> = repository.allWithdrawals
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Aggregates & Financial calculations
    val totalReceived: StateFlow<Double> = repository.totalReceived
        .combine(MutableStateFlow(Unit)) { total, _ -> total ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalPending: StateFlow<Double> = repository.totalPending
        .combine(MutableStateFlow(Unit)) { total, _ -> total ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalExpenses: StateFlow<Double> = repository.totalExpenses
        .combine(MutableStateFlow(Unit)) { total, _ -> total ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalWithdrawn: StateFlow<Double> = repository.totalWithdrawn
        .combine(MutableStateFlow(Unit)) { total, _ -> total ?: 0.0 }
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
                        val current = settings.value
                        val prof = state.profile
                        // Initialize Firestore Workspace & real-time snapshot listeners
                        workspaceRepository.initializeForUser(prof)

                        repository.updateSettings(
                            current.copy(
                                isLoggedIn = true,
                                activePartnerName = prof.displayName ?: current.activePartnerName.ifBlank { "Partner" },
                                activePartnerPhone = prof.phoneNumber ?: current.activePartnerPhone,
                                profilePhotoUri = prof.photoUrl ?: current.profilePhotoUri
                            )
                        )
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

                // Clear the draft only upon successful persistence
                clearNewEntryDraft()

                _syncMessage.value = "Saved successfully and synced to Cloud Workspace"
                onSuccess()
            } catch (e: Exception) {
                // If saving fails, do not clear the draft!
                e.printStackTrace()
            }
        }
    }

    fun deleteJob(job: JobEntryEntity) {
        viewModelScope.launch {
            workspaceRepository.deleteJob(job)
        }
    }

    fun addExpense(expense: ExpenseEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            workspaceRepository.addExpense(expense)
            _syncMessage.value = "Expense saved and synced with Cloud"
            onSuccess()
        }
    }

    fun updateExpense(expense: ExpenseEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            workspaceRepository.updateExpense(expense)
            onSuccess()
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            workspaceRepository.deleteExpense(expense)
        }
    }

    fun addWithdrawal(withdrawal: WithdrawalEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            workspaceRepository.addWithdrawal(withdrawal)
            _syncMessage.value = "Withdrawal saved and synced with Cloud"
            onSuccess()
        }
    }

    fun deleteWithdrawal(withdrawal: WithdrawalEntity) {
        viewModelScope.launch {
            workspaceRepository.deleteWithdrawal(withdrawal)
        }
    }

    fun updateCustomer(customer: CustomerEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            workspaceRepository.updateCustomer(customer)
            onSuccess()
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
        }
    }

    fun deleteCustomer(customer: CustomerEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            workspaceRepository.deleteCustomer(customer)
            onSuccess()
        }
    }

    fun addTractor(tractor: TractorEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            workspaceRepository.addTractor(tractor)
            onSuccess()
        }
    }

    fun updateTractor(tractor: TractorEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            workspaceRepository.updateTractor(tractor)
            onSuccess()
        }
    }

    fun deleteTractor(tractor: TractorEntity) {
        viewModelScope.launch {
            workspaceRepository.deleteTractor(tractor)
        }
    }

    fun addPartner(partner: PartnerEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            workspaceRepository.addPartner(partner)
            onSuccess()
        }
    }

    fun updatePartner(partner: PartnerEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            workspaceRepository.updatePartner(partner)
            onSuccess()
        }
    }

    fun deletePartner(partner: PartnerEntity) {
        viewModelScope.launch {
            workspaceRepository.deletePartner(partner)
        }
    }

    fun setActivePartner(partner: PartnerEntity) {
        viewModelScope.launch {
            repository.setActivePartner(
                partnerName = "${partner.name} (${partner.role})",
                partnerPhone = partner.phone
            )
        }
    }

    fun updateSettings(updated: AppSettingsEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            workspaceRepository.updateSettings(updated)
            onSuccess()
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

            val result = repository.pushUnsyncedToCloud(isOnline = true)
            _isSyncing.value = false
            _syncMessage.value = result.message
            onComplete(result.isSuccess)
        }
    }

    fun triggerSync() {
        pushUnsyncedToCloud()
    }

    fun getJobsForCustomer(customerId: Long) = repository.getJobsForCustomer(customerId)

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
            _isSyncing.value = false
            result.onSuccess { profile ->
                onSuccess(profile)
            }.onFailure { e ->
                onError(e.message ?: "Google Sign-In failed")
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
                _isSyncing.value = false
                onAutoVerified(profile)
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
            _isSyncing.value = false
            result.onSuccess { profile ->
                onSuccess(profile)
            }.onFailure { e ->
                onError(e.message ?: "Invalid OTP Code")
            }
        }
    }

    /**
     * Fallback / Direct Login for offline testing or pre-configured local partner
     */
    fun loginWithOtp(phone: String, otp: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            _isSyncing.value = true
            delay(400)
            val current = settings.value
            repository.updateSettings(
                current.copy(
                    isLoggedIn = true,
                    activePartnerPhone = phone
                )
            )
            _isSyncing.value = false
            onComplete()
        }
    }

    fun loginWithPartner(partner: PartnerEntity, onComplete: () -> Unit) {
        viewModelScope.launch {
            _isSyncing.value = true
            val current = settings.value
            repository.updateSettings(
                current.copy(
                    isLoggedIn = true,
                    activePartnerName = "${partner.name} (${partner.role})",
                    activePartnerPhone = partner.phone,
                    profilePhotoUri = partner.photoUri ?: current.profilePhotoUri
                )
            )
            _isSyncing.value = false
            onComplete()
        }
    }

    fun logout(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                authRepository.signOut()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            val current = settings.value
            repository.updateSettings(current.copy(isLoggedIn = false))
            onComplete()
        }
    }
}

