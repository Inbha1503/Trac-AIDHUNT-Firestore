package com.example.ui.screens.report

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.PartnerEntity
import com.example.data.entity.WithdrawalEntity
import com.example.pdf.PdfGeneratorHelper
import com.example.ui.components.ChartPalette
import com.example.ui.components.CollapsibleFilterCard
import com.example.ui.components.FlatPdfIconButton
import com.example.ui.components.FlatShareIconButton
import com.example.ui.components.PartnerWithdrawalPieChart
import com.example.ui.components.PieChartSlice
import com.example.ui.components.buildWithdrawalWhatsAppMessage
import com.example.ui.components.formatDateTime
import com.example.ui.components.formatInr
import com.example.ui.components.sendWhatsAppMessage
import com.example.ui.components.shareGenericText
import com.example.ui.theme.AlertDueRed
import com.example.ui.theme.DeepSageGreen
import com.example.ui.theme.EarthGold
import com.example.ui.theme.EarthGoldSoft
import com.example.ui.theme.ForestGreenHeader
import com.example.ui.theme.SageAccent
import com.example.ui.theme.SageCardBg
import com.example.ui.theme.SageOutline
import com.example.ui.theme.SoftSageGreen
import com.example.ui.theme.SuccessPaidGreen
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextSecondaryDark

fun getLocalizedWithdrawalCategory(category: String, isTamil: Boolean): String {
    if (!isTamil) return category
    return when (category.trim().lowercase()) {
        "personal use" -> "தனிப்பட்ட பயன்பாடு"
        "fuel advance" -> "எரிபொருள் முன்பணம்"
        "salary" -> "சம்பளம்"
        "profit share" -> "லாபப் பங்கு"
        "emergency" -> "அவசர தேவை"
        "maintenance advance" -> "பராமரிப்பு முன்பணம்"
        "other" -> "இதர"
        else -> category
    }
}

val WithdrawalCategories = listOf(
    "Personal Use",
    "Fuel Advance",
    "Salary",
    "Profit Share",
    "Emergency",
    "Maintenance Advance",
    "Other"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WithdrawalTab(
    settings: AppSettingsEntity,
    withdrawals: List<WithdrawalEntity>,
    partners: List<PartnerEntity>,
    availableAmount: Double,
    totalWithdrawn: Double,
    onAddWithdrawal: (WithdrawalEntity) -> Unit,
    onDeleteWithdrawal: (WithdrawalEntity) -> Unit
) {
    val context = LocalContext.current
    val isTamil = settings.language.equals("TA", ignoreCase = true)
    var showTakeAmountDialog by remember { mutableStateOf(false) }
    var selectedPartnerFilter by remember { mutableStateOf("All") }
    var draftPartnerFilter by remember { mutableStateOf("All") }
    var isFilterExpanded by remember { mutableStateOf(false) }
    var withdrawalToDelete by remember { mutableStateOf<WithdrawalEntity?>(null) }

    val filteredWithdrawals = withdrawals.filter {
        selectedPartnerFilter == "All" || it.partnerName.contains(selectedPartnerFilter, ignoreCase = true)
    }

    val currentFilteredTotal = filteredWithdrawals.sumOf { it.amount }

    // Dynamic Pie Chart Slices matching active filters
    val pieSlices = remember(filteredWithdrawals, partners, selectedPartnerFilter) {
        val targetPartners = if (selectedPartnerFilter == "All") {
            partners
        } else {
            partners.filter { it.name.contains(selectedPartnerFilter, ignoreCase = true) }
        }

        targetPartners.mapIndexed { index, partner ->
            val pWithdrawals = filteredWithdrawals.filter { it.partnerName.contains(partner.name, ignoreCase = true) }
            val amount = pWithdrawals.sumOf { it.amount }
            val count = pWithdrawals.size
            val color = ChartPalette.getOrElse(index) { ChartPalette[0] }
            PieChartSlice(
                name = partner.name,
                value = amount,
                color = color,
                subText = if (isTamil) "$count பரிவர்த்தனைகள்" else "$count txns"
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Actions: Single Primary Action "+ Take Amount" + Secondary "PDF Report"
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { showTakeAmountDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_take_amount"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepSageGreen)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isTamil) "எடுப்புப் பதிவு செய்க" else "Take Amount",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                OutlinedButton(
                    onClick = {
                        val file = PdfGeneratorHelper.generateWithdrawalReportPdf(
                            context = context,
                            settings = settings,
                            availableAmount = availableAmount,
                            totalWithdrawn = totalWithdrawn,
                            withdrawals = filteredWithdrawals
                        )
                        file?.let {
                            PdfGeneratorHelper.sharePdf(context, it, "Partner Withdrawal Report - ${settings.businessName}")
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("btn_export_withdrawal_pdf")
                ) {
                    Icon(
                        Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = AlertDueRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isTamil) "PDF அறிக்கை" else "PDF Report",
                        color = ForestGreenHeader,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // 2. Available Liquidity & Withdrawn Summary Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SageCardBg),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SageOutline)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (isTamil) "கிடைக்கும் ரொக்க இருப்பு" else "Available Cash Balance",
                                fontSize = 12.sp,
                                color = SageAccent,
                                fontWeight = FontWeight.Medium
                            )
                            Text(formatInr(availableAmount), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DeepSageGreen)
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (isTamil) "மொத்த எடுப்புகள்" else "Total Drawings",
                                fontSize = 12.sp,
                                color = SageAccent,
                                fontWeight = FontWeight.Medium
                            )
                            Text(formatInr(totalWithdrawn), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = EarthGold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (isTamil) "கணக்கீடு: மொத்த வேலை வருவாய் – மொத்த செயல்பாட்டுச் செலவுகள் – மொத்த பங்குதாரர் எடுப்புகள்" else "Calculated as: Total Received Jobs – Total Operating Expenses – Total Partner Drawings",
                        fontSize = 11.sp,
                        color = TextSecondaryDark,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // 3. Collapsible Filter Section
        item {
            CollapsibleFilterCard(
                isExpanded = isFilterExpanded,
                onToggleExpand = {
                    if (!isFilterExpanded) {
                        draftPartnerFilter = selectedPartnerFilter
                    }
                    isFilterExpanded = !isFilterExpanded
                },
                activeFiltersCount = if (selectedPartnerFilter != "All") 1 else 0,
                onClearFilters = {
                    draftPartnerFilter = "All"
                    selectedPartnerFilter = "All"
                    isFilterExpanded = false
                },
                onApplyFilters = {
                    selectedPartnerFilter = draftPartnerFilter
                    isFilterExpanded = false
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (isTamil) "பங்குதாரர் மூலம் வடிகட்டு:" else "Filter by Partner:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SageAccent
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = draftPartnerFilter == "All",
                            onClick = { draftPartnerFilter = "All" },
                            label = { Text(if (isTamil) "அனைத்து பங்குதாரர்கள்" else "All Partners", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SoftSageGreen)
                        )
                        partners.forEach { partner ->
                            FilterChip(
                                selected = draftPartnerFilter == partner.name,
                                onClick = { draftPartnerFilter = if (draftPartnerFilter == partner.name) "All" else partner.name },
                                label = { Text(partner.name, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SoftSageGreen)
                            )
                        }
                    }
                }
            }
        }

        // 4. Dynamic Pie Chart Component
        item {
            PartnerWithdrawalPieChart(
                slices = pieSlices,
                totalAmount = currentFilteredTotal
            )
        }

        // 5. Partner-Wise Breakdown Cards
        item(key = "section_partner_breakdown_header") {
            Text(
                text = if (isTamil) "பங்குதாரர் இருப்பு & விவரம்" else "Partner Balances & Breakdown",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = ForestGreenHeader,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        items(partners, key = { "partner_balance_${it.id}_${it.name}" }) { partner ->
            val pWithdrawals = filteredWithdrawals.filter { it.partnerName.contains(partner.name, ignoreCase = true) }
            val pTotal = pWithdrawals.sumOf { it.amount }
            val pCount = pWithdrawals.size
            val pPercent = if (currentFilteredTotal > 0 && !pTotal.isNaN()) {
                ((pTotal / currentFilteredTotal).toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SageOutline.copy(alpha = 0.5f))),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(SoftSageGreen),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = partner.name.take(1).ifBlank { "P" },
                                    fontWeight = FontWeight.Bold,
                                    color = DeepSageGreen,
                                    fontSize = 13.sp
                                )
                            }
                            Column {
                                Text(
                                    text = "${partner.name} (${partner.role})",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ForestGreenHeader
                                )
                                Text(
                                    text = if (isTamil) "$pCount பரிவர்த்தனைகள்" else "$pCount transactions",
                                    fontSize = 11.sp,
                                    color = TextMutedDark
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = formatInr(pTotal),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = EarthGold
                            )
                            Text(
                                text = if (isTamil) String.format(java.util.Locale.US, "எடுப்பில் %.1f%%", (pPercent * 100).coerceAtLeast(0f))
                                else String.format(java.util.Locale.US, "%.1f%% of drawings", (pPercent * 100).coerceAtLeast(0f)),
                                fontSize = 11.sp,
                                color = SageAccent
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { if (pPercent.isNaN()) 0f else pPercent.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = DeepSageGreen,
                        trackColor = SoftSageGreen.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // 6. Past Withdrawals List Header
        item(key = "section_past_withdrawals_header") {
            Text(
                text = if (isTamil) "கடந்த எடுப்புப் பதிவேடு" else "Past Withdrawals Ledger",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = ForestGreenHeader,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (filteredWithdrawals.isEmpty()) {
            item(key = "section_empty_withdrawals") {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SageCardBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(28.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Payments,
                            contentDescription = null,
                            tint = SageAccent,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isTamil) "எடுப்புப் பதிவுகள் எதுவும் இல்லை" else "No withdrawal records found",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = ForestGreenHeader,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (isTamil) "பண எடுப்புகளைப் பதிவு செய்ய மேலே உள்ள '+ எடுப்புப் பதிவு செய்க' பொத்தானைத் தட்டவும்" else "Tap '+ Take Amount' above to record cash drawings",
                            fontSize = 11.sp,
                            color = TextMutedDark,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(filteredWithdrawals, key = { "withdrawal_entry_${it.id}_${it.timestamp}" }) { w ->
                WithdrawalItemCard(
                    withdrawal = w,
                    isTamil = isTamil,
                    onDelete = { withdrawalToDelete = w },
                    onShareWhatsApp = {
                        val shareText = buildWithdrawalWhatsAppMessage(w, settings.businessName)
                        sendWhatsAppMessage(context, null, shareText)
                    }
                )
            }
        }
    }

    // Take Amount Dialog
    if (showTakeAmountDialog) {
        TakeAmountDialog(
            partners = partners,
            currentPartnerName = settings.activePartnerName.split(" ").firstOrNull() ?: "",
            availableAmount = availableAmount,
            currency = settings.currency,
            isTamil = isTamil,
            onDismiss = { showTakeAmountDialog = false },
            onConfirm = { withdrawal ->
                onAddWithdrawal(withdrawal)
                showTakeAmountDialog = false
            }
        )
    }

    // Confirm Delete Dialog
    withdrawalToDelete?.let { w ->
        AlertDialog(
            onDismissRequest = { withdrawalToDelete = null },
            title = { Text(if (isTamil) "எடுப்புப் பதிவை நீக்கவா?" else "Delete Withdrawal Record?", fontWeight = FontWeight.Bold, color = ForestGreenHeader) },
            text = {
                Text(
                    text = if (isTamil) "${w.partnerName} என்பவரால் எடுக்கப்பட்ட ரூ. ${formatInr(w.amount)} எடுப்புப் பதிவை நிச்சயமாக நீக்க விரும்புகிறீர்களா?"
                    else "Are you sure you want to delete the withdrawal entry of ${formatInr(w.amount)} taken by ${w.partnerName}?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteWithdrawal(w)
                        withdrawalToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertDueRed)
                ) {
                    Text(if (isTamil) "நீக்கு" else "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { withdrawalToDelete = null }) {
                    Text(if (isTamil) "ரத்து" else "Cancel")
                }
            }
        )
    }
}

@Composable
fun WithdrawalItemCard(
    withdrawal: WithdrawalEntity,
    isTamil: Boolean = false,
    onDelete: () -> Unit,
    onShareWhatsApp: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SageOutline.copy(alpha = 0.5f))),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("withdrawal_card_${withdrawal.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(EarthGoldSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = EarthGold,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = withdrawal.partnerName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForestGreenHeader
                        )
                        Text(
                            text = formatInr(withdrawal.amount),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = EarthGold
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isTamil) "காரணம்/வகை: ${getLocalizedWithdrawalCategory(withdrawal.category, isTamil)}" else "Category: ${withdrawal.category}",
                        fontSize = 12.sp,
                        color = TextSecondaryDark
                    )
                    if (withdrawal.note.isNotBlank()) {
                        Text(
                            text = if (isTamil) "குறிப்பு: ${withdrawal.note}" else "Note: ${withdrawal.note}",
                            fontSize = 11.sp,
                            color = TextMutedDark,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = SageOutline.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDateTime(withdrawal.timestamp),
                    fontSize = 10.sp,
                    color = SageAccent,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    FlatShareIconButton(
                        onClick = onShareWhatsApp,
                        modifier = Modifier.testTag("btn_share_withdrawal_${withdrawal.id}"),
                        contentDescription = if (isTamil) "வாட்ஸ்அப் மூலம் பகிரவும்" else "Share Withdrawal via WhatsApp",
                        tint = SuccessPaidGreen,
                        iconSize = 20.dp
                    )

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("btn_delete_withdrawal_${withdrawal.id}")
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = TextMutedDark,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TakeAmountDialog(
    partners: List<PartnerEntity>,
    currentPartnerName: String,
    availableAmount: Double = 0.0,
    currency: String = "₹",
    isTamil: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (WithdrawalEntity) -> Unit
) {
    var selectedPartner by remember {
        mutableStateOf(
            if (currentPartnerName.isNotBlank()) currentPartnerName
            else partners.firstOrNull()?.name ?: ""
        )
    }
    var amountText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Personal Use") }
    var note by remember { mutableStateOf("") }
    var hasAttemptedSubmit by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    var partnerDropdownExpanded by remember { mutableStateOf(false) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    val enteredAmount = amountText.toDoubleOrNull() ?: 0.0
    val isAmountZeroOrNegative = enteredAmount <= 0
    val isAmountExceedsBalance = enteredAmount > availableAmount
    val isAmountInvalid = isAmountZeroOrNegative || isAmountExceedsBalance
    val isPartnerInvalid = selectedPartner.isBlank()

    AlertDialog(
        onDismissRequest = {
            if (!isSubmitting) onDismiss()
        },
        title = {
            Text(
                text = if (isTamil) "பங்குதாரர் எடுப்பு பதிவு" else "Take Amount (Partner Withdrawal)",
                fontWeight = FontWeight.Bold,
                color = ForestGreenHeader
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Available Balance Indicator Banner
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (availableAmount > 0) SoftSageGreen.copy(alpha = 0.5f) else Color(0xFFFFEBEE),
                    border = BorderStroke(0.5.dp, if (availableAmount > 0) SageOutline else AlertDueRed.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isTamil) "கிடைக்கும் இருப்பு:" else "Available Balance:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondaryDark
                        )
                        Text(
                            text = formatInr(availableAmount),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (availableAmount > 0) DeepSageGreen else AlertDueRed
                        )
                    }
                }

                // Select Partner
                ExposedDropdownMenuBox(
                    expanded = partnerDropdownExpanded,
                    onExpandedChange = { if (!isSubmitting) partnerDropdownExpanded = !partnerDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedPartner,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(if (isTamil) "பங்குதாரர் பெயர் *" else "Partner Name *") },
                        isError = hasAttemptedSubmit && isPartnerInvalid,
                        supportingText = {
                            if (hasAttemptedSubmit && isPartnerInvalid) {
                                Text(
                                    text = if (isTamil) "பங்குதாரரை தேர்ந்தெடுக்கவும்" else "Partner selection is required",
                                    color = AlertDueRed,
                                    fontSize = 11.sp
                                )
                            }
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = partnerDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = partnerDropdownExpanded,
                        onDismissRequest = { partnerDropdownExpanded = false }
                    ) {
                        partners.forEach { partner ->
                            DropdownMenuItem(
                                text = { Text("${partner.name} (${partner.role})") },
                                onClick = {
                                    selectedPartner = partner.name
                                    partnerDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Amount
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(if (isTamil) "எடுப்பு தொகை (₹) *" else "Withdrawal Amount (₹) *") },
                    isError = hasAttemptedSubmit && isAmountInvalid,
                    supportingText = {
                        if (hasAttemptedSubmit && isAmountZeroOrNegative) {
                            Text(
                                text = if (isTamil) "தொகை தேவை (₹0 ஐ விட அதிகமாக இருக்க வேண்டும்)" else "Amount is required (greater than ₹0)",
                                color = AlertDueRed,
                                fontSize = 11.sp
                            )
                        } else if (isAmountExceedsBalance && enteredAmount > 0) {
                            Text(
                                text = if (isTamil) "தொகை இருக்கும் இருப்பை விட (${formatInr(availableAmount)}) அதிகமாக உள்ளது" else "Amount exceeds available balance (${formatInr(availableAmount)})",
                                color = AlertDueRed,
                                fontSize = 11.sp
                            )
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("withdrawal_amount_input"),
                    shape = RoundedCornerShape(10.dp)
                )

                // Category
                ExposedDropdownMenuBox(
                    expanded = categoryDropdownExpanded,
                    onExpandedChange = { if (!isSubmitting) categoryDropdownExpanded = !categoryDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = getLocalizedWithdrawalCategory(selectedCategory, isTamil),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(if (isTamil) "காரணம் / வகை" else "Category / Purpose") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = categoryDropdownExpanded,
                        onDismissRequest = { categoryDropdownExpanded = false }
                    ) {
                        WithdrawalCategories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(getLocalizedWithdrawalCategory(cat, isTamil)) },
                                onClick = {
                                    selectedCategory = cat
                                    categoryDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Note
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(if (isTamil) "குறிப்பு (விருப்பத்தேர்வு)" else "Note (Optional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    hasAttemptedSubmit = true
                    val amt = amountText.toDoubleOrNull() ?: 0.0
                    if (amt > 0 && amt <= availableAmount && selectedPartner.isNotBlank() && !isSubmitting) {
                        isSubmitting = true
                        val matchedPartner = partners.find { it.name == selectedPartner }
                        val withdrawal = WithdrawalEntity(
                            partnerId = matchedPartner?.id ?: 0,
                            partnerName = selectedPartner,
                            amount = amt,
                            category = selectedCategory,
                            note = note,
                            timestamp = System.currentTimeMillis()
                        )
                        onConfirm(withdrawal)
                    }
                },
                enabled = !isSubmitting && (!hasAttemptedSubmit || (!isAmountInvalid && !isPartnerInvalid)),
                colors = ButtonDefaults.buttonColors(containerColor = DeepSageGreen)
            ) {
                Text(if (isTamil) "எடுப்பை சேமி" else "Record Withdrawal")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSubmitting
            ) {
                Text(if (isTamil) "ரத்து" else "Cancel")
            }
        }
    )
}
