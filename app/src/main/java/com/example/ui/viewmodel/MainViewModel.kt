package com.example.ui.viewmodel

import android.app.Application
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
import com.example.data.network.NetworkMonitor
import com.example.data.repository.TractorRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

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
    private val networkMonitor = NetworkMonitor(application)
    private val syncManager = com.example.data.sync.FirestoreSyncManager(application, database)

    private val firebaseAuth: FirebaseAuth? by lazy {
        try {
            if (com.google.firebase.FirebaseApp.getApps(application).isNotEmpty()) {
                FirebaseAuth.getInstance()
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.w("MainViewModel", "Firebase Auth not available: ${e.message}")
            null
        }
    }

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
        // Schedule periodic background synchronization with WorkManager
        com.example.data.sync.SyncWorker.schedulePeriodicSync(application)

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
                val isCurrentlyOnline = isEffectiveOnline.value
                val jobToSave = job.copy(isSynced = false, syncStatus = com.example.data.entity.SyncStatus.PENDING.name)
                val expenseToSave = linkedExpense?.copy(isSynced = false, syncStatus = com.example.data.entity.SyncStatus.PENDING.name)
                repository.saveJobEntry(jobToSave, expenseToSave)

                // Clear the draft only upon successful persistence
                clearNewEntryDraft()

                if (isCurrentlyOnline) {
                    pushUnsyncedToCloud()
                } else {
                    _syncMessage.value = "Job saved offline to Room SQLite. Will push to Cloud when online."
                }
                onSuccess()
            } catch (e: Exception) {
                // If saving fails, do not clear the draft!
                e.printStackTrace()
            }
        }
    }

    fun deleteJob(job: JobEntryEntity) {
        viewModelScope.launch {
            repository.deleteJob(job)
            if (isEffectiveOnline.value) {
                pushUnsyncedToCloud()
            }
        }
    }

    fun addExpense(expense: ExpenseEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val isCurrentlyOnline = isEffectiveOnline.value
            val expToSave = expense.copy(isSynced = false, syncStatus = com.example.data.entity.SyncStatus.PENDING.name)
            repository.addExpense(expToSave)

            if (isCurrentlyOnline) {
                pushUnsyncedToCloud()
            } else {
                _syncMessage.value = "Expense saved offline to Room SQLite."
            }
            onSuccess()
        }
    }

    fun updateExpense(expense: ExpenseEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val isCurrentlyOnline = isEffectiveOnline.value
            val expToSave = expense.copy(isSynced = false, syncStatus = com.example.data.entity.SyncStatus.PENDING.name)
            repository.updateExpense(expToSave)

            if (isCurrentlyOnline) {
                pushUnsyncedToCloud()
            }
            onSuccess()
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
            if (isEffectiveOnline.value) {
                pushUnsyncedToCloud()
            }
        }
    }

    fun addWithdrawal(withdrawal: WithdrawalEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val isCurrentlyOnline = isEffectiveOnline.value
            val withToSave = withdrawal.copy(isSynced = false, syncStatus = com.example.data.entity.SyncStatus.PENDING.name)
            repository.addWithdrawal(withToSave)

            if (isCurrentlyOnline) {
                pushUnsyncedToCloud()
            } else {
                _syncMessage.value = "Withdrawal saved offline to Room SQLite."
            }
            onSuccess()
        }
    }

    fun deleteWithdrawal(withdrawal: WithdrawalEntity) {
        viewModelScope.launch {
            repository.deleteWithdrawal(withdrawal)
            if (isEffectiveOnline.value) {
                pushUnsyncedToCloud()
            }
        }
    }

    fun updateCustomer(customer: CustomerEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val isCurrentlyOnline = isEffectiveOnline.value
            repository.updateCustomer(customer.copy(isSynced = false, syncStatus = com.example.data.entity.SyncStatus.PENDING.name))

            if (isCurrentlyOnline) {
                pushUnsyncedToCloud()
            }
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
            val isCurrentlyOnline = isEffectiveOnline.value
            repository.recordCustomerPayment(
                customer = customer.copy(isSynced = false),
                amount = amount,
                dateTimestamp = dateTimestamp,
                paymentMethod = paymentMethod,
                note = note,
                operatorName = settings.value.activePartnerName
            )

            if (isCurrentlyOnline) {
                pushUnsyncedToCloud()
            } else {
                _syncMessage.value = "Payment recorded offline to Room SQLite."
            }
            onSuccess()
        }
    }

    fun deleteCustomer(customer: CustomerEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteCustomer(customer)
            if (isEffectiveOnline.value) {
                pushUnsyncedToCloud()
            }
            onSuccess()
        }
    }

    fun addTractor(tractor: TractorEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.addTractor(tractor)
            if (isEffectiveOnline.value) {
                pushUnsyncedToCloud()
            }
            onSuccess()
        }
    }

    fun updateTractor(tractor: TractorEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.updateTractor(tractor)
            if (isEffectiveOnline.value) {
                pushUnsyncedToCloud()
            }
            onSuccess()
        }
    }

    fun deleteTractor(tractor: TractorEntity) {
        viewModelScope.launch {
            repository.deleteTractor(tractor)
            if (isEffectiveOnline.value) {
                pushUnsyncedToCloud()
            }
        }
    }

    fun addPartner(partner: PartnerEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.addPartner(partner)
            if (isEffectiveOnline.value) {
                pushUnsyncedToCloud()
            }
            onSuccess()
        }
    }

    fun updatePartner(partner: PartnerEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.updatePartner(partner)
            if (isEffectiveOnline.value) {
                pushUnsyncedToCloud()
            }
            onSuccess()
        }
    }

    fun deletePartner(partner: PartnerEntity) {
        viewModelScope.launch {
            repository.deletePartner(partner)
            if (isEffectiveOnline.value) {
                pushUnsyncedToCloud()
            }
        }
    }

    fun setActivePartner(partner: PartnerEntity) {
        viewModelScope.launch {
            repository.setActivePartner(
                partnerName = "${partner.name} (${partner.role})",
                partnerPhone = partner.phone
            )
            if (isEffectiveOnline.value) {
                pushUnsyncedToCloud()
            }
        }
    }

    fun updateSettings(updated: AppSettingsEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.updateSettings(updated)
            if (isEffectiveOnline.value) {
                pushUnsyncedToCloud()
            }
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
            _syncMessage.value = "Synchronizing with Cloud Firestore..."

            val result = repository.pushUnsyncedToCloud(isOnline = true, syncManager = syncManager)
            _isSyncing.value = false
            _syncMessage.value = result.message
            onComplete(result.isSuccess)
        }
    }

    fun triggerSync() {
        pushUnsyncedToCloud()
    }

    fun getJobsForCustomer(customerId: Long) = repository.getJobsForCustomer(customerId)

    fun loginWithOtp(phone: String, otp: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            _isSyncing.value = true
            delay(800)
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

    fun sendVerificationCode(
        phone: String,
        activity: android.app.Activity,
        onCodeSent: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val auth = firebaseAuth
        if (auth == null) {
            // Local fallback logic
            onCodeSent("mock_verification_id")
            return
        }

        // Clean phone number format for Firebase Auth (needs country code, e.g. +91)
        val formattedPhone = if (!phone.startsWith("+")) {
            "+91$phone"
        } else {
            phone
        }

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                viewModelScope.launch {
                    try {
                        auth.signInWithCredential(credential).await()
                        val current = settings.value
                        repository.updateSettings(
                            current.copy(
                                isLoggedIn = true,
                                activePartnerPhone = phone
                            )
                        )
                        pushUnsyncedToCloud()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                onError(e.message ?: "Verification failed")
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                onCodeSent(verificationId)
            }
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(formattedPhone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()
        
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun verifyPhoneOtp(
        verificationId: String,
        otp: String,
        phone: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val auth = firebaseAuth
        if (auth == null || verificationId == "mock_verification_id") {
            // Local fallback logic
            viewModelScope.launch {
                _isSyncing.value = true
                delay(800)
                val current = settings.value
                repository.updateSettings(
                    current.copy(
                        isLoggedIn = true,
                        activePartnerPhone = phone
                    )
                )
                _isSyncing.value = false
                onComplete(true, null)
            }
            return
        }

        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val credential = PhoneAuthProvider.getCredential(verificationId, otp)
                auth.signInWithCredential(credential).await()
                
                // Update settings
                val current = settings.value
                repository.updateSettings(
                    current.copy(
                        isLoggedIn = true,
                        activePartnerPhone = phone
                    )
                )
                pushUnsyncedToCloud()
                _isSyncing.value = false
                onComplete(true, null)
            } catch (e: Exception) {
                _isSyncing.value = false
                onComplete(false, e.message ?: "Authentication failed")
            }
        }
    }

    fun loginAnonymously(onComplete: (Boolean, String?) -> Unit) {
        val auth = firebaseAuth
        if (auth == null) {
            viewModelScope.launch {
                _isSyncing.value = true
                delay(800)
                val current = settings.value
                repository.updateSettings(
                    current.copy(
                        isLoggedIn = true,
                        activePartnerPhone = "gmail@partner.com"
                    )
                )
                _isSyncing.value = false
                onComplete(true, null)
            }
            return
        }

        viewModelScope.launch {
            _isSyncing.value = true
            try {
                auth.signInAnonymously().await()
                val current = settings.value
                repository.updateSettings(
                    current.copy(
                        isLoggedIn = true,
                        activePartnerPhone = "gmail@partner.com"
                    )
                )
                pushUnsyncedToCloud()
                _isSyncing.value = false
                onComplete(true, null)
            } catch (e: Exception) {
                _isSyncing.value = false
                onComplete(false, e.message ?: "Anonymous sign-in failed")
            }
        }
    }

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            val current = settings.value
            repository.updateSettings(current.copy(isLoggedIn = false))
            onComplete()
        }
    }
}

