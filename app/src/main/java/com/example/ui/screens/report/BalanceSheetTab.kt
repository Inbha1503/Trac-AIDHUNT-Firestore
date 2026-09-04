package com.example.ui.screens.report

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.pdf.BalanceSheetRow
import com.example.pdf.PdfGeneratorHelper
import com.example.ui.components.formatDate
import com.example.ui.components.formatInr
import com.example.ui.theme.AlertDueRed
import com.example.ui.theme.AppTheme
import com.example.ui.theme.DeepSageGreen
import com.example.ui.theme.ForestGreenHeader
import com.example.ui.theme.SageAccent
import com.example.ui.theme.SageCardBg
import com.example.ui.theme.SageOutline
import com.example.ui.theme.SoftSageGreen
import com.example.ui.theme.SuccessPaidGreen
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextSecondaryDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class BalanceSheetViewMode(val label: String) {
    DAILY("Daily"),
    MONTHLY("Monthly"),
    YEARLY("Yearly")
}

fun getLocalizedViewMode(mode: BalanceSheetViewMode, isTamil: Boolean): String {
    if (!isTamil) return mode.label
    return when (mode) {
        BalanceSheetViewMode.DAILY -> "தினசரி"
        BalanceSheetViewMode.MONTHLY -> "மாதாந்திர"
        BalanceSheetViewMode.YEARLY -> "வருடாந்திர"
    }
}

@Composable
fun BalanceSheetTab(
    settings: AppSettingsEntity,
    jobs: List<JobEntryEntity>,
    expenses: List<ExpenseEntity>,
    totalSales: Double,
    totalExpenses: Double,
    netBalance: Double
) {
    val isTamil = settings.language == "TA"
    val context = LocalContext.current
    val responsive = com.example.ui.theme.rememberResponsiveDimensions()
    var viewMode by remember { mutableStateOf(BalanceSheetViewMode.DAILY) }

    // Group jobs and expenses by date (Daily), month (Monthly), or year (Yearly)
    val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val dayDisplayFormat = SimpleDateFormat("dd MMM yyyy (EEE)", Locale.getDefault())
    val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    val monthDisplayFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())
    val yearDisplayFormat = SimpleDateFormat(if (isTamil) "'ஆண்டு 'yyyy" else "'Year 'yyyy", Locale.getDefault())

    val groupedPeriods = remember(jobs, expenses, viewMode) {
        val periods = mutableMapOf<String, MutableList<Any>>()
        val periodDisplayNames = mutableMapOf<String, String>()

        jobs.forEach { job ->
            val dateObj = Date(job.startTimeMillis)
            val key = when (viewMode) {
                BalanceSheetViewMode.DAILY -> dayFormat.format(dateObj)
                BalanceSheetViewMode.MONTHLY -> monthFormat.format(dateObj)
                BalanceSheetViewMode.YEARLY -> yearFormat.format(dateObj)
            }
            val displayName = when (viewMode) {
                BalanceSheetViewMode.DAILY -> dayDisplayFormat.format(dateObj)
                BalanceSheetViewMode.MONTHLY -> monthDisplayFormat.format(dateObj)
                BalanceSheetViewMode.YEARLY -> yearDisplayFormat.format(dateObj)
            }
            periodDisplayNames[key] = displayName
            periods.getOrPut(key) { mutableListOf() }.add(job)
        }

        expenses.forEach { exp ->
            val dateObj = Date(exp.dateTimestamp)
            val key = when (viewMode) {
                BalanceSheetViewMode.DAILY -> dayFormat.format(dateObj)
                BalanceSheetViewMode.MONTHLY -> monthFormat.format(dateObj)
                BalanceSheetViewMode.YEARLY -> yearFormat.format(dateObj)
            }
            val displayName = when (viewMode) {
                BalanceSheetViewMode.DAILY -> dayDisplayFormat.format(dateObj)
                BalanceSheetViewMode.MONTHLY -> monthDisplayFormat.format(dateObj)
                BalanceSheetViewMode.YEARLY -> yearDisplayFormat.format(dateObj)
            }
            periodDisplayNames[key] = displayName
            periods.getOrPut(key) { mutableListOf() }.add(exp)
        }

        periods.keys.sortedDescending().map { key ->
            val items = periods[key] ?: emptyList()
            val periodSales = items.filterIsInstance<JobEntryEntity>().sumOf { it.amountReceived }
            val periodExpenses = items.filterIsInstance<ExpenseEntity>().sumOf { it.amount }
            val periodNet = periodSales - periodExpenses
            val label = periodDisplayNames[key] ?: key

            BalanceSheetGroup(
                key = key,
                label = label,
                sales = periodSales,
                expenses = periodExpenses,
                net = periodNet,
                jobs = items.filterIsInstance<JobEntryEntity>(),
                expenseItems = items.filterIsInstance<ExpenseEntity>()
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = responsive.screenPaddingHorizontal,
            vertical = responsive.screenPaddingVertical
        ),
        verticalArrangement = Arrangement.spacedBy(if (responsive.isSmallPhone) 10.dp else 12.dp)
    ) {
        // 1. View Toggle & Export Button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Daily / Monthly / Yearly 3-Way Segmented Selector
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SoftSageGreen.copy(alpha = 0.6f),
                    modifier = Modifier.testTag("toggle_balance_view_mode")
                ) {
                    Row(modifier = Modifier.padding(3.dp)) {
                        BalanceSheetViewMode.values().forEach { mode ->
                            val isSelected = viewMode == mode
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) DeepSageGreen else Color.Transparent,
                                modifier = Modifier.clickable { viewMode = mode }
                            ) {
                                Text(
                                    text = getLocalizedViewMode(mode, isTamil),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else DeepSageGreen,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Export PDF Button
                OutlinedButton(
                    onClick = {
                        val reportRows = groupedPeriods.map {
                            BalanceSheetRow(
                                periodLabel = it.label,
                                sales = it.sales,
                                expenses = it.expenses,
                                balance = it.net
                            )
                        }
                        val file = PdfGeneratorHelper.generateBalanceSheetPdf(
                            context = context,
                            settings = settings,
                            totalSales = totalSales,
                            totalExpenses = totalExpenses,
                            netBalance = netBalance,
                            periodSummary = reportRows
                        )
                        file?.let {
                            val displayMode = getLocalizedViewMode(viewMode, isTamil)
                            PdfGeneratorHelper.sharePdf(context, it, "${if (isTamil) "இருப்புநிலை அறிக்கை" else "Balance Sheet Statement"} ($displayMode) - ${settings.businessName}")
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("btn_export_balance_pdf")
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = AlertDueRed, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isTamil) "பதிவிறக்கு" else "Export PDF", color = AppTheme.colors.textPrimary, fontSize = 12.sp)
                }
            }
        }

        // 2. Summary Master Card
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBg),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.cardBorder.copy(alpha = 0.6f))),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(if (responsive.isSmallPhone) 12.dp else 16.dp)) {
                    Text(
                        text = if (isTamil) "இருப்பு சுருக்கம்" else "Balance Summary",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeepSageGreen
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(if (isTamil) "பெறப்பட்ட விற்பனை" else "Sales Received", fontSize = 11.sp, color = AppTheme.colors.textMuted)
                            Text(formatInr(totalSales, settings.currency), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SuccessPaidGreen)
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(if (isTamil) "மொத்த செலவுகள்" else "Total Expenses", fontSize = 11.sp, color = AppTheme.colors.textMuted)
                            Text(formatInr(totalExpenses, settings.currency), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AlertDueRed)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Divider(color = AppTheme.colors.cardBorder.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (isTamil) "நிகர இலாபம் / வரம்பு:" else "Net Profit / Margin:", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.textPrimary)
                        Text(
                            text = formatInr(netBalance, settings.currency),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (netBalance >= 0) DeepSageGreen else AlertDueRed
                        )
                    }
                }
            }
        }

        // 3. Period Breakdown List
        item {
            Text(
                text = if (isTamil) "${getLocalizedViewMode(viewMode, isTamil)} விவரம்" else "${viewMode.label} Breakdown",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (groupedPeriods.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(if (isTamil) "பரிவர்த்தனை பதிவுகள் எதுவும் இல்லை" else "No transaction records found", color = AppTheme.colors.textMuted)
                    }
                }
            }
        } else {
            items(groupedPeriods, key = { it.key }) { group ->
                BalanceSheetPeriodCard(group = group, isTamil = isTamil)
            }
        }
    }
}

data class BalanceSheetGroup(
    val key: String,
    val label: String,
    val sales: Double,
    val expenses: Double,
    val net: Double,
    val jobs: List<JobEntryEntity>,
    val expenseItems: List<ExpenseEntity>
)

@Composable
fun BalanceSheetPeriodCard(group: BalanceSheetGroup, isTamil: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    val responsive = com.example.ui.theme.rememberResponsiveDimensions()

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBg),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.cardBorder.copy(alpha = 0.5f))),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (responsive.isSmallPhone) 10.dp else 14.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = DeepSageGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = group.label,
                        fontSize = if (responsive.isSmallPhone) 13.sp else 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary,
                        maxLines = 1
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = AppTheme.colors.textMuted
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3-Column Metrics: Sales | Expenses | Net
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(if (isTamil) "விற்பனை" else "Sales", fontSize = 10.sp, color = AppTheme.colors.textMuted)
                    Text(formatInr(group.sales), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = SuccessPaidGreen)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (isTamil) "செலவுகள்" else "Expenses", fontSize = 10.sp, color = AppTheme.colors.textMuted)
                    Text(formatInr(group.expenses), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = AlertDueRed)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(if (isTamil) "நிகர வரம்பு" else "Net Margin", fontSize = 10.sp, color = AppTheme.colors.textMuted)
                    Text(
                        formatInr(group.net),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (group.net >= 0) DeepSageGreen else AlertDueRed
                    )
                }
            }

            // Expandable breakdown rows
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Divider(color = AppTheme.colors.cardBorder.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(8.dp))

                    if (group.jobs.isNotEmpty()) {
                        Text(if (isTamil) "வேலைகள் & வசூல்கள்:" else "Jobs & Collections:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DeepSageGreen)
                        group.jobs.forEach { j ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val displayWorkType = if (isTamil && (j.workType == "Payment Received" || j.tractorLabel == "Payment")) {
                                    "கட்டணம் பெறப்பட்டது"
                                } else {
                                    j.workType
                                }
                                Text("• ${j.customerName} ($displayWorkType)", fontSize = 11.sp, color = AppTheme.colors.textSecondary, modifier = Modifier.weight(1f, fill = false), maxLines = 1)
                                Text("+${formatInr(j.amountReceived)}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SuccessPaidGreen)
                            }
                        }
                    }

                    if (group.expenseItems.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(if (isTamil) "ஏற்பட்ட செலவுகள்:" else "Expenses Incurred:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AlertDueRed)
                        group.expenseItems.forEach { e ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("• ${e.expenseType} (${e.tractorLabel})", fontSize = 11.sp, color = AppTheme.colors.textSecondary, modifier = Modifier.weight(1f, fill = false), maxLines = 1)
                                Text("-${formatInr(e.amount)}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AlertDueRed)
                            }
                        }
                    }
                }
            }
        }
    }
}

