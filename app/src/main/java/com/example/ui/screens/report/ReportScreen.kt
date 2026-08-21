package com.example.ui.screens.report

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.CustomerEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PartnerEntity
import com.example.data.entity.TractorEntity
import com.example.data.entity.WithdrawalEntity
import com.example.ui.components.formatInr
import com.example.ui.theme.AlertDueRed
import com.example.ui.theme.AlertDueRedBg
import com.example.ui.theme.AppTheme
import com.example.ui.theme.DeepSageGreen
import com.example.ui.theme.EarthGold
import com.example.ui.theme.EarthGoldSoft
import com.example.ui.theme.ForestGreenHeader
import com.example.ui.theme.SageAccent
import com.example.ui.theme.SageCardBg
import com.example.ui.theme.SoftSageGreen
import com.example.ui.theme.SuccessPaidGreen
import com.example.ui.theme.SuccessPaidGreenBg
import com.example.ui.viewmodel.ReportSubPage

@Composable
fun ReportScreen(
    currentSubPage: ReportSubPage,
    onSubPageSelected: (ReportSubPage) -> Unit,
    settings: AppSettingsEntity,
    expenses: List<ExpenseEntity>,
    jobs: List<JobEntryEntity>,
    customers: List<CustomerEntity>,
    withdrawals: List<WithdrawalEntity>,
    partners: List<PartnerEntity>,
    tractors: List<TractorEntity>,
    totalSales: Double,
    totalExpenses: Double,
    netBalance: Double,
    availableAmount: Double,
    totalWithdrawn: Double,
    onAddExpense: (ExpenseEntity) -> Unit,
    onUpdateExpense: (ExpenseEntity) -> Unit,
    onDeleteExpense: (ExpenseEntity) -> Unit,
    onAddWithdrawal: (WithdrawalEntity) -> Unit,
    onDeleteWithdrawal: (WithdrawalEntity) -> Unit,
    onUpdateCustomer: (CustomerEntity) -> Unit = {},
    onRecordPayment: ((CustomerEntity, Double, Long, String, String) -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background)
    ) {
        when (currentSubPage) {
            ReportSubPage.MENU -> {
                ReportMenuDashboard(
                    expensesCount = expenses.size,
                    totalExpenses = totalExpenses,
                    netBalance = netBalance,
                    totalWithdrawn = totalWithdrawn,
                    totalPendingDue = customers.filter { it.balanceDue > 0 }.sumOf { it.balanceDue },
                    pendingCustomersCount = customers.count { it.balanceDue > 0 },
                    onNavigate = onSubPageSelected
                )
            }
            ReportSubPage.EXPENSES -> {
                ExpensesTab(
                    settings = settings,
                    expenses = expenses,
                    tractors = tractors,
                    partners = partners,
                    onAddExpense = onAddExpense,
                    onUpdateExpense = onUpdateExpense,
                    onDeleteExpense = onDeleteExpense
                )
            }
            ReportSubPage.BALANCE_SHEET -> {
                BalanceSheetTab(
                    settings = settings,
                    jobs = jobs,
                    expenses = expenses,
                    totalSales = totalSales,
                    totalExpenses = totalExpenses,
                    netBalance = netBalance
                )
            }
            ReportSubPage.WITHDRAWAL -> {
                WithdrawalTab(
                    settings = settings,
                    withdrawals = withdrawals,
                    partners = partners,
                    availableAmount = availableAmount,
                    totalWithdrawn = totalWithdrawn,
                    onAddWithdrawal = onAddWithdrawal,
                    onDeleteWithdrawal = onDeleteWithdrawal
                )
            }
            ReportSubPage.CUSTOMER_CREDIT_DUE -> {
                CustomerCreditDueTab(
                    settings = settings,
                    customers = customers,
                    jobs = jobs,
                    onUpdateCustomer = onUpdateCustomer,
                    onRecordPayment = onRecordPayment
                )
            }
        }
    }
}

@Composable
fun ReportMenuDashboard(
    expensesCount: Int,
    totalExpenses: Double,
    netBalance: Double,
    totalWithdrawn: Double,
    totalPendingDue: Double,
    pendingCustomersCount: Int,
    onNavigate: (ReportSubPage) -> Unit
) {
    val responsive = com.example.ui.theme.rememberResponsiveDimensions()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = responsive.screenPaddingHorizontal,
            vertical = responsive.screenPaddingVertical
        ),
        verticalArrangement = Arrangement.spacedBy(if (responsive.isSmallPhone) 10.dp else 12.dp)
    ) {
        // 1. Expenses Card
        item {
            ReportMenuCard(
                title = "Expenses",
                subtitle = "Diesel, repairs & costs",
                icon = Icons.Default.LocalGasStation,
                iconBgColor = SoftSageGreen,
                iconTint = DeepSageGreen,
                statLabel = "Total Expenses",
                statValue = formatInr(totalExpenses),
                badgeText = "$expensesCount entries",
                badgeBgColor = SoftSageGreen,
                testTag = "report_menu_card_expenses",
                onClick = { onNavigate(ReportSubPage.EXPENSES) }
            )
        }

        // 2. Balance Sheet Card
        item {
            ReportMenuCard(
                title = "Balance Sheet",
                subtitle = "Profit & loss statement",
                icon = Icons.Default.Assessment,
                iconBgColor = SuccessPaidGreenBg,
                iconTint = SuccessPaidGreen,
                statLabel = "Net Profit",
                statValue = formatInr(netBalance),
                badgeText = if (netBalance >= 0) "Profitable" else "Loss",
                badgeBgColor = if (netBalance >= 0) SuccessPaidGreenBg else AlertDueRedBg,
                testTag = "report_menu_card_balance_sheet",
                onClick = { onNavigate(ReportSubPage.BALANCE_SHEET) }
            )
        }

        // 3. Withdrawal Card
        item {
            ReportMenuCard(
                title = "Withdrawal",
                subtitle = "Partner share distribution",
                icon = Icons.Default.AccountBalanceWallet,
                iconBgColor = EarthGoldSoft,
                iconTint = EarthGold,
                statLabel = "Total Withdrawn",
                statValue = formatInr(totalWithdrawn),
                badgeText = "Partner Split",
                badgeBgColor = EarthGoldSoft,
                testTag = "report_menu_card_withdrawal",
                onClick = { onNavigate(ReportSubPage.WITHDRAWAL) }
            )
        }

        // 4. Customer Credit Due Card
        item {
            ReportMenuCard(
                title = "Customer Dues",
                subtitle = "Outstanding balances",
                icon = Icons.Default.PendingActions,
                iconBgColor = AlertDueRedBg,
                iconTint = AlertDueRed,
                statLabel = "Total Outstanding",
                statValue = formatInr(totalPendingDue),
                badgeText = "$pendingCustomersCount dues",
                badgeBgColor = AlertDueRedBg,
                testTag = "report_menu_card_customer_dues",
                onClick = { onNavigate(ReportSubPage.CUSTOMER_CREDIT_DUE) }
            )
        }
    }
}

@Composable
fun ReportMenuCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBgColor: Color,
    iconTint: Color,
    statLabel: String,
    statValue: String,
    badgeText: String,
    badgeBgColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    val responsive = com.example.ui.theme.rememberResponsiveDimensions()

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBg),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.cardBorder.copy(alpha = 0.6f))),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (responsive.isSmallPhone) 12.dp else 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(if (responsive.isSmallPhone) 40.dp else 46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconTint,
                        modifier = Modifier.size(if (responsive.isSmallPhone) 22.dp else 24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = title,
                            fontSize = if (responsive.isSmallPhone) 14.5.sp else 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = badgeBgColor
                        ) {
                            Text(
                                text = badgeText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = iconTint,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 11.5.sp,
                        color = AppTheme.colors.textMuted,
                        lineHeight = 15.sp
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Open",
                    tint = AppTheme.colors.textMuted,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppTheme.colors.cardBg)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = statLabel,
                    fontSize = 11.5.sp,
                    color = AppTheme.colors.textSecondary
                )
                Text(
                    text = statValue,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textPrimary
                )
            }
        }
    }
}

