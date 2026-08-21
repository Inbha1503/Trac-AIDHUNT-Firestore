package com.example

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.ui.components.AppBottomNav
import com.example.ui.components.AppTopHeader
import com.example.ui.screens.account.AccountScreen
import com.example.ui.screens.auth.LoginScreen
import com.example.ui.screens.entry.NewEntryScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.report.ReportScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AccountSubPage
import com.example.ui.viewmodel.BottomTab
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.ReportSubPage

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppContent(activity = this, viewModel = viewModel, onShowToast = { msg ->
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                })
            }
        }
    }
}

@Composable
fun MainAppContent(
    activity: Activity,
    viewModel: MainViewModel,
    onShowToast: (String) -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val partners by viewModel.partners.collectAsState()
    val tractors by viewModel.tractors.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val jobs by viewModel.jobs.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val withdrawals by viewModel.withdrawals.collectAsState()

    val totalReceived by viewModel.totalReceived.collectAsState()
    val totalPending by viewModel.totalPending.collectAsState()
    val totalExpenses by viewModel.totalExpenses.collectAsState()
    val totalWithdrawn by viewModel.totalWithdrawn.collectAsState()
    val availableAmount by viewModel.availableAmount.collectAsState()
    val netBalance by viewModel.netBalance.collectAsState()

    val currentTab by viewModel.currentTab.collectAsState()
    val newEntryDraft by viewModel.newEntryDraft.collectAsState()
    val currentReportSubPage by viewModel.currentReportSubPage.collectAsState()
    val currentAccountSubPage by viewModel.currentAccountSubPage.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val isOnline by viewModel.isEffectiveOnline.collectAsState()
    val totalUnsyncedCount by viewModel.totalUnsyncedCount.collectAsState()
    val unsyncedJobsCount by viewModel.unsyncedJobsCount.collectAsState()
    val unsyncedExpensesCount by viewModel.unsyncedExpensesCount.collectAsState()
    val unsyncedWithdrawalsCount by viewModel.unsyncedWithdrawalsCount.collectAsState()
    val unsyncedCustomersCount by viewModel.unsyncedCustomersCount.collectAsState()
    val simulatedOffline by viewModel.simulatedOffline.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()

    // Handle Hardware Back Button
    BackHandler(enabled = currentTab == BottomTab.REPORT && currentReportSubPage != ReportSubPage.MENU) {
        viewModel.setReportSubPage(ReportSubPage.MENU)
    }
    BackHandler(enabled = currentTab == BottomTab.ACCOUNT && currentAccountSubPage != AccountSubPage.MAIN) {
        viewModel.setAccountSubPage(AccountSubPage.MAIN)
    }
    BackHandler(enabled = currentTab != BottomTab.HOME && currentReportSubPage == ReportSubPage.MENU && currentAccountSubPage == AccountSubPage.MAIN) {
        viewModel.setBottomTab(BottomTab.HOME)
    }

    val isTamil = settings.language.equals("TA", ignoreCase = true)

    val topBarTitle: String
    val showBackButton: Boolean
    val onBackAction: (() -> Unit)?
    val rightActionIcon: androidx.compose.ui.graphics.vector.ImageVector?
    val onRightActionClick: (() -> Unit)?

    when (currentTab) {
        BottomTab.HOME -> {
            topBarTitle = if (isTamil) "AIDHUNT டிராக்" else "AIDHUNT Trac"
            showBackButton = false
            onBackAction = null
            rightActionIcon = null
            onRightActionClick = null
        }
        BottomTab.NEW_ENTRY -> {
            topBarTitle = if (isTamil) "புதிய வேலை" else "New Job"
            showBackButton = false
            onBackAction = null
            rightActionIcon = null
            onRightActionClick = null
        }
        BottomTab.REPORT -> {
            rightActionIcon = null
            onRightActionClick = null
            when (currentReportSubPage) {
                ReportSubPage.MENU -> {
                    topBarTitle = if (isTamil) "அறிக்கைகள் & பகுப்பாய்வு" else "Reports & Analytics"
                    showBackButton = false
                    onBackAction = null
                }
                ReportSubPage.EXPENSES -> {
                    topBarTitle = if (isTamil) "டிராக்டர் செலவுகள்" else "Fleet Expenses"
                    showBackButton = false
                    onBackAction = null
                }
                ReportSubPage.BALANCE_SHEET -> {
                    topBarTitle = if (isTamil) "இருப்புநிலை அறிக்கை" else "Balance Sheet"
                    showBackButton = true
                    onBackAction = { viewModel.setReportSubPage(ReportSubPage.MENU) }
                }
                ReportSubPage.WITHDRAWAL -> {
                    topBarTitle = if (isTamil) "பங்குதாரர் எடுப்புகள்" else "Partner Withdrawals"
                    showBackButton = true
                    onBackAction = { viewModel.setReportSubPage(ReportSubPage.MENU) }
                }
                ReportSubPage.CUSTOMER_CREDIT_DUE -> {
                    topBarTitle = if (isTamil) "வாடிக்கையாளர் கடன் நிலுவை" else "Customer Credit Due"
                    showBackButton = true
                    onBackAction = { viewModel.setReportSubPage(ReportSubPage.MENU) }
                }
            }
        }
        BottomTab.ACCOUNT -> {
            when (currentAccountSubPage) {
                AccountSubPage.MAIN -> {
                    topBarTitle = if (isTamil) "கணக்கு" else "Account"
                    showBackButton = false
                    onBackAction = null
                    rightActionIcon = null
                    onRightActionClick = null
                }
                AccountSubPage.MANAGE_TRACTORS -> {
                    topBarTitle = if (isTamil) "டிராக்டர்களை நிர்வகி" else "Manage Fleet Tractors"
                    showBackButton = true
                    onBackAction = { viewModel.setAccountSubPage(AccountSubPage.MAIN) }
                    rightActionIcon = null
                    onRightActionClick = null
                }
                AccountSubPage.MANAGE_PARTNERS -> {
                    topBarTitle = if (isTamil) "பங்குதாரர்களை நிர்வகி" else "Manage Partners"
                    showBackButton = true
                    onBackAction = { viewModel.setAccountSubPage(AccountSubPage.MAIN) }
                    rightActionIcon = null
                    onRightActionClick = null
                }
                AccountSubPage.SETTINGS -> {
                    topBarTitle = if (isTamil) "வணிக அமைப்புகள்" else "Business Preferences"
                    showBackButton = true
                    onBackAction = { viewModel.setAccountSubPage(AccountSubPage.MAIN) }
                    rightActionIcon = null
                    onRightActionClick = null
                }
                AccountSubPage.EDIT_PROFILE -> {
                    topBarTitle = if (isTamil) "சுயவிவரத்தைத் திருத்து" else "Edit Partner Profile"
                    showBackButton = true
                    onBackAction = { viewModel.setAccountSubPage(AccountSubPage.MAIN) }
                    rightActionIcon = null
                    onRightActionClick = null
                }
                AccountSubPage.SQLITE_SYNC_STATUS -> {
                    topBarTitle = if (isTamil) "SQLite & கிளவுட் ஒத்திசைவு" else "SQLite & Cloud Sync"
                    showBackButton = true
                    onBackAction = { viewModel.setAccountSubPage(AccountSubPage.MAIN) }
                    rightActionIcon = null
                    onRightActionClick = null
                }
            }
        }
    }

    if (!settings.isLoggedIn) {
        LoginScreen(
            partners = partners,
            onGoogleSignIn = { isCreatingAccount, bName, oName, onError ->
                viewModel.signInWithGoogle(activity, bName, oName, isCreatingAccount) { success, error ->
                    if (success) {
                        onShowToast(if (isTamil) "Google மூலம் வெற்றிகரமாக உள்நுழைந்தது!" else "Signed in with Google successfully!")
                    } else if (error != null) {
                        onError(error)
                        onShowToast(error)
                    }
                }
            },
            onGoogleSignInDirect = { email, name, isCreatingAccount, bName, oName, onError ->
                viewModel.signInWithGoogleDirect(email, name, bName, oName, isCreatingAccount) { success, error ->
                    if (success) {
                        onShowToast(if (isTamil) "Google மூலம் வெற்றிகரமாக உள்நுழைந்தது!" else "Signed in with Google successfully!")
                    } else if (error != null) {
                        onError(error)
                        onShowToast(error)
                    }
                }
            },
            onSendOtp = { phone, onCodeSent, onError ->
                viewModel.sendVerificationCode(phone, activity, onCodeSent, onError)
            },
            onVerifyOtp = { phone, verificationId, otp, onError ->
                viewModel.verifyPhoneOtp(verificationId, otp, phone) { success, error ->
                    if (success) {
                        onShowToast(if (isTamil) "பகிரப்பட்ட கணக்கில் உள்நுழைந்தது" else "Logged in to Shared Account")
                    } else {
                        val msg = error ?: "Login failed"
                        onError(msg)
                        onShowToast(msg)
                    }
                }
            },
            onEmailLogin = { email, pass, onError ->
                viewModel.loginWithEmail(email, pass) { success, error ->
                    if (success) {
                        onShowToast(if (isTamil) "கணக்கில் உள்நுழைந்தது" else "Signed in successfully!")
                    } else {
                        val msg = error ?: "Email login failed"
                        onError(msg)
                        onShowToast(msg)
                    }
                }
            },
            onCreateAccountEmail = { email, pass, bName, oName, phone, onError ->
                viewModel.createAccountWithEmail(email, pass, bName, oName, phone) { success, error ->
                    if (success) {
                        onShowToast(if (isTamil) "புதிய கணக்கு உருவாக்கப்பட்டது!" else "New Business Account created successfully!")
                    } else {
                        val msg = error ?: "Account creation failed"
                        onError(msg)
                        onShowToast(msg)
                    }
                }
            },
            onCreateAccountPhone = { verificationId, otp, phone, bName, oName, onError ->
                viewModel.createAccountWithPhone(verificationId, otp, phone, bName, oName) { success, error ->
                    if (success) {
                        onShowToast(if (isTamil) "புதிய கணக்கு உருவாக்கப்பட்டது!" else "New Business Account created successfully!")
                    } else {
                        val msg = error ?: "Phone registration failed"
                        onError(msg)
                        onShowToast(msg)
                    }
                }
            },
            onDemoLogin = { partner, onError ->
                viewModel.loginWithDemoAccount(partner) { success, error ->
                    if (success) {
                        onShowToast(if (isTamil) "${partner.name} ஆக உள்நுழைந்தது (Demo)" else "Logged in as ${partner.name} (Demo)")
                    } else {
                        val msg = error ?: "Demo login failed"
                        onError(msg)
                        onShowToast(msg)
                    }
                }
            },
            isLoggingIn = isSyncing
        )
    } else {
        Scaffold(
            topBar = {
                AppTopHeader(
                    title = topBarTitle,
                    showBack = showBackButton,
                    onBackClick = onBackAction,
                    settings = settings,
                    partners = partners,
                    isSyncing = isSyncing,
                    isOnline = isOnline,
                    totalUnsyncedCount = totalUnsyncedCount,
                    onSyncClick = {
                        viewModel.pushUnsyncedToCloud()
                    },
                    onPartnerSelected = { partner ->
                        viewModel.setActivePartner(partner)
                        onShowToast(if (isTamil) "பங்குதாரர் மாற்றப்பட்டது: ${partner.name}" else "Switched to ${partner.name}")
                    },
                    rightActionIcon = rightActionIcon,
                    onRightActionClick = onRightActionClick,
                    isDarkGreenStyle = (currentTab == BottomTab.ACCOUNT)
                )
            },
            bottomBar = {
                AppBottomNav(
                    currentTab = currentTab,
                    onTabSelected = { viewModel.setBottomTab(it) },
                    isTamil = isTamil
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Crossfade(targetState = currentTab, label = "tab_crossfade") { tab ->
                    when (tab) {
                        BottomTab.HOME -> {
                            HomeScreen(
                                settings = settings,
                                totalReceived = totalReceived,
                                totalPending = totalPending,
                                availableBalance = availableAmount,
                                recentJobs = jobs,
                                isOnline = isOnline,
                                isSyncing = isSyncing,
                                unsyncedCount = totalUnsyncedCount,
                                syncMessage = syncMessage,
                                onTriggerSync = {
                                    viewModel.pushUnsyncedToCloud()
                                },
                                onFabClick = { viewModel.setBottomTab(BottomTab.NEW_ENTRY) },
                                onDeleteJob = { job ->
                                    viewModel.deleteJob(job)
                                    onShowToast(if (isTamil) "வேலைப் பதிவு நீக்கப்பட்டது" else "Job entry deleted")
                                }
                            )
                        }

                        BottomTab.REPORT -> {
                            ReportScreen(
                                currentSubPage = currentReportSubPage,
                                onSubPageSelected = { viewModel.setReportSubPage(it) },
                                settings = settings,
                                expenses = expenses,
                                jobs = jobs,
                                customers = customers,
                                withdrawals = withdrawals,
                                partners = partners,
                                tractors = tractors,
                                totalSales = totalReceived,
                                totalExpenses = totalExpenses,
                                netBalance = netBalance,
                                availableAmount = availableAmount,
                                totalWithdrawn = totalWithdrawn,
                                onAddExpense = { expense ->
                                    viewModel.addExpense(expense) {
                                        onShowToast(if (isTamil) "செலவு சேர்க்கப்பட்டது" else "Expense added")
                                    }
                                },
                                onUpdateExpense = { expense ->
                                    viewModel.updateExpense(expense) {
                                        onShowToast(if (isTamil) "செலவு புதுப்பிக்கப்பட்டது" else "Expense updated")
                                    }
                                },
                                onDeleteExpense = { expense ->
                                    viewModel.deleteExpense(expense)
                                    onShowToast(if (isTamil) "செலவு நீக்கப்பட்டது" else "Expense deleted")
                                },
                                onAddWithdrawal = { withdrawal ->
                                    viewModel.addWithdrawal(withdrawal) {
                                        onShowToast(if (isTamil) "எடுப்பு பதிவு செய்யப்பட்டது" else "Withdrawal recorded")
                                    }
                                },
                                onDeleteWithdrawal = { withdrawal ->
                                    viewModel.deleteWithdrawal(withdrawal)
                                    onShowToast(if (isTamil) "எடுப்பு நீக்கப்பட்டது" else "Withdrawal deleted")
                                },
                                onUpdateCustomer = { customer ->
                                    viewModel.updateCustomer(customer) {
                                        onShowToast(if (isTamil) "வாடிக்கையாளர் புதுப்பிக்கப்பட்டார்" else "Customer updated")
                                    }
                                },
                                onRecordPayment = { customer, amount, dateMillis, method, note ->
                                    viewModel.recordCustomerPayment(customer, amount, dateMillis, method, note) {
                                        onShowToast(if (isTamil) "${customer.name} - ₹${amount.toInt()} கட்டணம் பதிவு செய்யப்பட்டது" else "Payment of ₹${amount.toInt()} recorded for ${customer.name}")
                                    }
                                }
                            )
                        }

                        BottomTab.NEW_ENTRY -> {
                            val draft = newEntryDraft ?: com.example.ui.viewmodel.NewEntryDraft.createDefault(
                                defaultTractor = if (settings.lockedTractorLabel.isNotBlank()) settings.lockedTractorLabel else (tractors.firstOrNull()?.label ?: "Mahindra 575 DI"),
                                lockedTractor = settings.lockedTractorLabel,
                                defaultHourlyRate = settings.defaultHourlyRate
                            )
                            NewEntryScreen(
                                settings = settings,
                                tractors = tractors,
                                customers = customers,
                                draft = draft,
                                onUpdateDraft = { viewModel.updateNewEntryDraft(it) },
                                onClearDraft = { viewModel.clearNewEntryDraft() },
                                onSaveJob = { job, linkedExpense ->
                                    viewModel.saveJobEntry(job, linkedExpense) {
                                        onShowToast(if (isTamil) "வேலைப் பதிவு வெற்றிகரமாகச் சேமிக்கப்பட்டது!" else "Job Entry saved successfully!")
                                        viewModel.setBottomTab(BottomTab.HOME)
                                    }
                                },
                                onUpdateLockedTractor = { locked ->
                                    viewModel.updateSettings(settings.copy(lockedTractorLabel = locked))
                                }
                            )
                        }

                        BottomTab.ACCOUNT -> {
                            AccountScreen(
                                currentSubPage = currentAccountSubPage,
                                onSubPageSelected = { viewModel.setAccountSubPage(it) },
                                settings = settings,
                                partners = partners,
                                tractors = tractors,
                                isSyncing = isSyncing,
                                isOnline = isOnline,
                                unsyncedJobsCount = unsyncedJobsCount,
                                unsyncedExpensesCount = unsyncedExpensesCount,
                                unsyncedWithdrawalsCount = unsyncedWithdrawalsCount,
                                unsyncedCustomersCount = unsyncedCustomersCount,
                                totalUnsyncedCount = totalUnsyncedCount,
                                isSimulatedOffline = simulatedOffline,
                                onToggleSimulatedOffline = { viewModel.toggleSimulatedOffline(it) },
                                onTriggerSync = {
                                    viewModel.pushUnsyncedToCloud()
                                },
                                onAddTractor = {
                                    viewModel.addTractor(it) { onShowToast(if (isTamil) "டிராக்டர் சேர்க்கப்பட்டது" else "Tractor added") }
                                },
                                onUpdateTractor = {
                                    viewModel.updateTractor(it) { onShowToast(if (isTamil) "டிராக்டர் புதுப்பிக்கப்பட்டது" else "Tractor updated") }
                                },
                                onDeleteTractor = {
                                    viewModel.deleteTractor(it)
                                    onShowToast(if (isTamil) "டிராக்டர் நீக்கப்பட்டது" else "Tractor deleted")
                                },
                                onAddPartner = {
                                    viewModel.addPartner(it) { onShowToast(if (isTamil) "பங்குதாரர் சேர்க்கப்பட்டார்" else "Partner added") }
                                },
                                onUpdatePartner = {
                                    viewModel.updatePartner(it) { onShowToast(if (isTamil) "பங்குதாரர் புதுப்பிக்கப்பட்டார்" else "Partner updated") }
                                },
                                onDeletePartner = {
                                    viewModel.deletePartner(it)
                                    onShowToast(if (isTamil) "பங்குதாரர் நீக்கப்பட்டார்" else "Partner removed")
                                },
                                onUpdateSettings = {
                                    viewModel.updateSettings(it) { onShowToast(if (isTamil) "அமைப்புகள் புதுப்பிக்கப்பட்டன" else "Settings updated") }
                                },
                                onLogout = {
                                    viewModel.logout { onShowToast(if (isTamil) "வெளியேறியது" else "Logged out") }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
