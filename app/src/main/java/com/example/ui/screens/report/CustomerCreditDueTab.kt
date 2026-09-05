
package com.example.ui.screens.report
import androidx.compose.foundation.ExperimentalFoundationApi
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.ui.components.shareGenericText
import com.example.ui.theme.AppTheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import com.example.ui.utils.trackFocusedField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.foundation.text.KeyboardOptions
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.CustomerEntity
import com.example.data.entity.JobEntryEntity
import com.example.pdf.PdfGeneratorHelper
import com.example.ui.components.DetailRow
import com.example.ui.components.FlatPdfIconButton
import com.example.ui.components.FlatShareIconButton
import com.example.ui.components.StatusBadge
import com.example.ui.components.buildCustomerDueWhatsAppMessage
import com.example.ui.components.formatDate
import com.example.ui.components.formatInr
import com.example.ui.components.formatWhatsAppPhone
import com.example.ui.components.isValidPhoneNumber
import com.example.ui.components.openDialer
import com.example.ui.components.openWhatsApp
import com.example.ui.components.sanitizePhoneNumberForStorage
import com.example.ui.components.sendWhatsAppMessage
import com.example.ui.theme.AlertDueRed
import com.example.ui.theme.AlertDueRedBg
import com.example.ui.theme.DeepSageGreen
import com.example.ui.theme.ForestGreenHeader
import com.example.ui.theme.SageAccent
import com.example.ui.theme.SageCardBg
import com.example.ui.theme.SageOutline
import com.example.ui.theme.SoftSageGreen
import com.example.ui.theme.SuccessPaidGreen
import com.example.ui.theme.SuccessPaidGreenBg
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextSecondaryDark
import java.util.Calendar
import java.util.Locale
enum class CreditDueSortOrder(val label: String) {
    HIGH_TO_LOW("High to Low"),
    LOW_TO_HIGH("Low to High"),
    RECENT("Recent")
}
enum class CreditDueStatusFilter(val label: String) {
    ALL("All"),
    PENDING_DUE("Pending Due"),
    PAID("Fully Paid")
}
enum class CreditDueDateFilter(val label: String) {
    ALL("All Time"),
    TODAY("Today"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    LAST_30_DAYS("Last 30 Days"),
    THIS_YEAR("This Year"),
    CUSTOM("📅 Pick Dates")
}
fun getLocalizedSortOrder(order: CreditDueSortOrder, isTamil: Boolean): String {
    if (!isTamil) return order.label
    return when (order) {
        CreditDueSortOrder.HIGH_TO_LOW -> "அதிகம் முதல் குறைவு"
        CreditDueSortOrder.LOW_TO_HIGH -> "குறைவு முதல் அதிகம்"
        CreditDueSortOrder.RECENT -> "சமீபத்திய"
    }
}
fun getLocalizedStatusFilter(filter: CreditDueStatusFilter, isTamil: Boolean): String {
    if (!isTamil) return filter.label
    return when (filter) {
        CreditDueStatusFilter.ALL -> "அனைத்தும்"
        CreditDueStatusFilter.PENDING_DUE -> "நிலுவை உள்ளவை"
        CreditDueStatusFilter.PAID -> "முழுமையாக செலுத்தியவை"
    }
}
fun getLocalizedDateFilter(filter: CreditDueDateFilter, isTamil: Boolean): String {
    if (!isTamil) return filter.label
    return when (filter) {
        CreditDueDateFilter.ALL -> "அனைத்து காலம்"
        CreditDueDateFilter.TODAY -> "இன்று"
        CreditDueDateFilter.THIS_WEEK -> "இந்த வாரம்"
        CreditDueDateFilter.THIS_MONTH -> "இந்த மாதம்"
        CreditDueDateFilter.LAST_30_DAYS -> "கடந்த 30 நாட்கள்"
        CreditDueDateFilter.THIS_YEAR -> "இந்த ஆண்டு"
        CreditDueDateFilter.CUSTOM -> "📅 தேதியைத் தேர்ந்தெடு"
    }
}
fun getLocalizedPaymentMethod(method: String, isTamil: Boolean): String {
    if (!isTamil) return method
    return when (method) {
        "Cash" -> "ரொக்கம்"
        "UPI / GPay" -> "யுபிஐ / ஜிபே"
        "Bank Transfer" -> "வங்கி கணக்கு"
        "Cheque" -> "காசோலை"
        else -> method
    }
}
@Composable
fun CustomerCreditDueTab(
    settings: AppSettingsEntity,
    customers: List<CustomerEntity>,
    jobs: List<JobEntryEntity>,
    onUpdateCustomer: ((CustomerEntity) -> Unit)? = null,
    onRecordPayment: ((CustomerEntity, Double, Long, String, String) -> Unit)? = null
) {
    val context = LocalContext.current
    val isTamil = settings.language == "TA"
    val coroutineScope = rememberCoroutineScope()
    val searchRequester = remember { BringIntoViewRequester() }
    val amountRequester = remember { BringIntoViewRequester() }
    val dialogAmountRequester = remember { BringIntoViewRequester() }
    val dialogNotesRequester = remember { BringIntoViewRequester() }
    var searchQuery by remember { mutableStateOf("") }
    var selectedSortOrder by remember { mutableStateOf(CreditDueSortOrder.HIGH_TO_LOW) }
    var selectedStatusFilter by remember { mutableStateOf(CreditDueStatusFilter.ALL) }
    var selectedDateFilter by remember { mutableStateOf(CreditDueDateFilter.ALL) }
    var isFilterExpanded by remember { mutableStateOf(false) }
    var customStartDateMillis by remember {
        mutableLongStateOf(Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -30) }.timeInMillis)
    }
    var customEndDateMillis by remember {
        mutableLongStateOf(Calendar.getInstance().timeInMillis)
    }
    var selectedCustomer by remember { mutableStateOf<CustomerEntity?>(null) }
    var customerToEditPhone by remember { mutableStateOf<CustomerEntity?>(null) }
    var customerForPayment by remember { mutableStateOf<CustomerEntity?>(null) }
    // Map each customer to their most recent job timestamp
    val customerRecentJobTime = remember(jobs) {
        val map = mutableMapOf<Long, Long>()
        val nameMap = mutableMapOf<String, Long>()
        jobs.forEach { job ->
            val prevTime = map[job.customerId] ?: 0L
            if (job.startTimeMillis > prevTime) {
                map[job.customerId] = job.startTimeMillis
            }
            val prevNameTime = nameMap[job.customerName.trim().lowercase()] ?: 0L
            if (job.startTimeMillis > prevNameTime) {
                nameMap[job.customerName.trim().lowercase()] = job.startTimeMillis
            }
        }
        Pair(map, nameMap)
    }
    // Determine active date range
    val (dateFilterStart, dateFilterEnd) = remember(selectedDateFilter, customStartDateMillis, customEndDateMillis) {
        val cal = Calendar.getInstance()
        when (selectedDateFilter) {
            CreditDueDateFilter.ALL -> Pair(0L, Long.MAX_VALUE)
            CreditDueDateFilter.TODAY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, Long.MAX_VALUE)
            }
            CreditDueDateFilter.THIS_WEEK -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, Long.MAX_VALUE)
            }
            CreditDueDateFilter.THIS_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, Long.MAX_VALUE)
            }
            CreditDueDateFilter.LAST_30_DAYS -> {
                cal.add(Calendar.DAY_OF_YEAR, -30)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, Long.MAX_VALUE)
            }
            CreditDueDateFilter.THIS_YEAR -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, Long.MAX_VALUE)
            }
            CreditDueDateFilter.CUSTOM -> {
                Pair(customStartDateMillis, customEndDateMillis)
            }
        }
    }
    val filteredCustomers = remember(
        customers,
        jobs,
        searchQuery,
        selectedSortOrder,
        selectedStatusFilter,
        selectedDateFilter,
        dateFilterStart,
        dateFilterEnd
    ) {
        customers.filter { c ->
            val matchesSearch = searchQuery.isBlank() ||
                    c.name.contains(searchQuery, ignoreCase = true) ||
                    c.phone.contains(searchQuery) ||
                    c.location.contains(searchQuery, ignoreCase = true)
            
            val matchesStatus = when (selectedStatusFilter) {
                CreditDueStatusFilter.ALL -> true
                CreditDueStatusFilter.PENDING_DUE -> c.balanceDue > 0
                CreditDueStatusFilter.PAID -> c.balanceDue <= 0
            }
            val matchesDate = if (selectedDateFilter == CreditDueDateFilter.ALL) {
                true
            } else {
                val hasJobInDate = jobs.any { j ->
                    (j.customerId == c.id || j.customerName.equals(c.name, ignoreCase = true)) &&
                            j.startTimeMillis in dateFilterStart..dateFilterEnd
                }
                val hasCustomerUpdatedInDate = c.updatedAt in dateFilterStart..dateFilterEnd || c.createdAt in dateFilterStart..dateFilterEnd
                hasJobInDate || hasCustomerUpdatedInDate
            }
            matchesSearch && matchesStatus && matchesDate
        }.let { list ->
            when (selectedSortOrder) {
                CreditDueSortOrder.HIGH_TO_LOW -> list.sortedByDescending { it.balanceDue }
                CreditDueSortOrder.LOW_TO_HIGH -> list.sortedBy { it.balanceDue }
                CreditDueSortOrder.RECENT -> list.sortedByDescending {
                    customerRecentJobTime.first[it.id]
                        ?: customerRecentJobTime.second[it.name.trim().lowercase()]
                        ?: 0L
                }
            }
        }
    }
    val totalOutstanding = filteredCustomers.filter { it.balanceDue > 0 }.sumOf { it.balanceDue }
    fun showCustomDatePickers() {
        val cal = Calendar.getInstance()
        cal.timeInMillis = customStartDateMillis
        DatePickerDialog(
            context,
            { _, sYear, sMonth, sDay ->
                val startCal = Calendar.getInstance().apply {
                    set(sYear, sMonth, sDay, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                customStartDateMillis = startCal.timeInMillis
                // Open End Date picker
                val endCalInit = Calendar.getInstance()
                endCalInit.timeInMillis = customEndDateMillis
                DatePickerDialog(
                    context,
                    { _, eYear, eMonth, eDay ->
                        val endCal = Calendar.getInstance().apply {
                            set(eYear, eMonth, eDay, 23, 59, 59)
                            set(Calendar.MILLISECOND, 999)
                        }
                        customEndDateMillis = endCal.timeInMillis
                        selectedDateFilter = CreditDueDateFilter.CUSTOM
                    },
                    endCalInit.get(Calendar.YEAR),
                    endCalInit.get(Calendar.MONTH),
                    endCalInit.get(Calendar.DAY_OF_MONTH)
                ).apply {
                    setTitle("Select End Date")
                }.show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).apply {
            setTitle("Select Start Date")
        }.show()
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 100.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Bulk Export & Summary Banner
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = AlertDueRedBg),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AlertDueRed.copy(alpha = 0.3f))),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(if (isTamil) "மொத்த நிலுவைத் தொகை" else "Total Outstanding Dues", fontSize = 11.sp, color = AlertDueRed, fontWeight = FontWeight.SemiBold)
                        Text(formatInr(totalOutstanding, settings.currency), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AlertDueRed)
                    }
                }
                OutlinedButton(
                    onClick = {
                        val file = PdfGeneratorHelper.generateBulkCustomerDuesPdf(
                            context = context,
                            settings = settings,
                            customers = filteredCustomers
                        )
                        file?.let {
                            PdfGeneratorHelper.sharePdf(context, it, "Customer Credit Dues Report - ${settings.businessName}")
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.CenterVertically).testTag("btn_bulk_export_dues")
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = AlertDueRed, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isTamil) "பதிவிறக்கு" else "Bulk Export", color = ForestGreenHeader)
                }
            }
        }
        // 2. Search Field
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(if (isTamil) "வாடிக்கையாளர் பெயர், மொபைல், ஊர்..." else "Search customer name, phone, village...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SageAccent) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .trackFocusedField(searchRequester, coroutineScope)
                    .testTag("customer_due_search_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DeepSageGreen,
                    unfocusedBorderColor = SageOutline
                )
            )
        }
        // 3. Sorting & Filtering Controls (Collapsible by default)
        item {
            val activeFilterCount = (if (selectedStatusFilter != CreditDueStatusFilter.ALL) 1 else 0) +
                (if (selectedSortOrder != CreditDueSortOrder.HIGH_TO_LOW) 1 else 0) +
                (if (selectedDateFilter != CreditDueDateFilter.ALL) 1 else 0)
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SageCardBg),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SageOutline.copy(alpha = 0.6f))),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Header Toggle Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isFilterExpanded = !isFilterExpanded }
                            .testTag("btn_toggle_customer_due_filters"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = if (isTamil) "வடிகட்டிகள்" else "Filters",
                                tint = ForestGreenHeader,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = if (isTamil) "வடிகட்டுதல் & வரிசைப்படுத்துதல்" else "Filters & Sorting",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ForestGreenHeader
                            )
                            if (activeFilterCount > 0) {
                                Surface(
                                    shape = CircleShape,
                                    color = AlertDueRed,
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = activeFilterCount.toString(),
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (activeFilterCount > 0 && isFilterExpanded) {
                                TextButton(
                                    onClick = {
                                        selectedStatusFilter = CreditDueStatusFilter.ALL
                                        selectedSortOrder = CreditDueSortOrder.HIGH_TO_LOW
                                        selectedDateFilter = CreditDueDateFilter.ALL
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(14.dp), tint = SageAccent)
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(if (isTamil) "மீட்டமை" else "Reset", fontSize = 11.sp, color = SageAccent, fontWeight = FontWeight.Bold)
                                }
                            }
                            IconButton(
                                onClick = { isFilterExpanded = !isFilterExpanded },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFilterExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (isFilterExpanded) {
                                        if (isTamil) "வடிகட்டிகளை சுருக்குக" else "Collapse Filters"
                                    } else {
                                        if (isTamil) "வடிகட்டிகளை விரிக்கவும்" else "Expand Filters"
                                    },
                                    tint = ForestGreenHeader
                                )
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = isFilterExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Divider(color = SageOutline.copy(alpha = 0.4f), thickness = 0.8.dp)
                            // Status Filter Row (All / Pending Due / Fully Paid)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.FilterList, contentDescription = null, tint = ForestGreenHeader, modifier = Modifier.size(16.dp))
                                    Text(if (isTamil) "நிலை:" else "Status:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ForestGreenHeader)
                                }
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    items(CreditDueStatusFilter.values().toList()) { statusFilter ->
                                        val isSelected = selectedStatusFilter == statusFilter
                                        val count = when (statusFilter) {
                                            CreditDueStatusFilter.ALL -> customers.size
                                            CreditDueStatusFilter.PENDING_DUE -> customers.count { it.balanceDue > 0 }
                                            CreditDueStatusFilter.PAID -> customers.count { it.balanceDue <= 0 }
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) DeepSageGreen else Color.White,
                                            border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, SageOutline.copy(alpha = 0.6f)),
                                            modifier = Modifier.clickable { selectedStatusFilter = statusFilter }
                                        ) {
                                            Text(
                                                text = "${getLocalizedStatusFilter(statusFilter, isTamil)} ($count)",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isSelected) Color.White else ForestGreenHeader,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Divider(color = SageOutline.copy(alpha = 0.4f), thickness = 0.8.dp)
                            // Sort Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Sort, contentDescription = null, tint = ForestGreenHeader, modifier = Modifier.size(16.dp))
                                    Text(if (isTamil) "வரிசைப்படுத்து:" else "Sort:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ForestGreenHeader)
                                }
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    items(CreditDueSortOrder.values().toList()) { sortOrder ->
                                        val isSelected = selectedSortOrder == sortOrder
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) DeepSageGreen else Color.White,
                                            border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, SageOutline.copy(alpha = 0.6f)),
                                            modifier = Modifier.clickable { selectedSortOrder = sortOrder }
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                            ) {
                                                val icon = when (sortOrder) {
                                                    CreditDueSortOrder.HIGH_TO_LOW -> Icons.Default.ArrowDownward
                                                    CreditDueSortOrder.LOW_TO_HIGH -> Icons.Default.ArrowUpward
                                                    CreditDueSortOrder.RECENT -> Icons.Default.Schedule
                                                }
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = null,
                                                    tint = if (isSelected) Color.White else DeepSageGreen,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = getLocalizedSortOrder(sortOrder, isTamil),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (isSelected) Color.White else ForestGreenHeader
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Divider(color = SageOutline.copy(alpha = 0.4f), thickness = 0.8.dp)
                            // Date Filter Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.clickable { showCustomDatePickers() }
                                ) {
                                    Icon(Icons.Default.CalendarMonth, contentDescription = "Calendar Picker", tint = ForestGreenHeader, modifier = Modifier.size(16.dp))
                                    Text(if (isTamil) "காலம்:" else "Calendar:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ForestGreenHeader)
                                }
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    items(CreditDueDateFilter.values().toList()) { filter ->
                                        val isSelected = selectedDateFilter == filter
                                        val label = if (filter == CreditDueDateFilter.CUSTOM && selectedDateFilter == CreditDueDateFilter.CUSTOM) {
                                            "${formatDate(customStartDateMillis)} - ${formatDate(customEndDateMillis)}"
                                        } else {
                                            getLocalizedDateFilter(filter, isTamil)
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) DeepSageGreen else Color.White,
                                            border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, SageOutline.copy(alpha = 0.6f)),
                                            modifier = Modifier.clickable {
                                                if (filter == CreditDueDateFilter.CUSTOM) {
                                                    showCustomDatePickers()
                                                } else {
                                                    selectedDateFilter = filter
                                                }
                                            }
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isSelected) Color.White else ForestGreenHeader,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // Active Calendar Date Filter Banner
        if (selectedDateFilter != CreditDueDateFilter.ALL) {
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SoftSageGreen.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = DeepSageGreen, modifier = Modifier.size(16.dp))
                            Text(
                                text = if (selectedDateFilter == CreditDueDateFilter.CUSTOM) {
                                    if (isTamil) "நாட்காட்டி: ${formatDate(customStartDateMillis)} – ${formatDate(customEndDateMillis)}" else "Calendar: ${formatDate(customStartDateMillis)} – ${formatDate(customEndDateMillis)}"
                                } else {
                                    if (isTamil) "வடிகட்டி: ${getLocalizedDateFilter(selectedDateFilter, isTamil)}" else "Calendar Filter: ${selectedDateFilter.label}"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DeepSageGreen
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TextButton(
                                onClick = { showCustomDatePickers() },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(if (isTamil) "மாற்று" else "Change", fontSize = 11.sp, color = DeepSageGreen, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = { selectedDateFilter = CreditDueDateFilter.ALL },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = if (isTamil) "அழி" else "Clear", tint = ForestGreenHeader, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
        // 4. Customers Due List Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val headerTitle = when (selectedStatusFilter) {
                    CreditDueStatusFilter.ALL -> if (isTamil) "அனைத்து வாடிக்கையாளர்கள் (${filteredCustomers.size})" else "All Customer Accounts (${filteredCustomers.size})"
                    CreditDueStatusFilter.PENDING_DUE -> if (isTamil) "நிலுவைத் தொகை உள்ளவர்கள் (${filteredCustomers.size})" else "Customers with Outstanding Due (${filteredCustomers.size})"
                    CreditDueStatusFilter.PAID -> if (isTamil) "முழுமையாக செலுத்தியவர்கள் (${filteredCustomers.size})" else "Fully Paid Customers (${filteredCustomers.size})"
                }
                Text(
                    text = headerTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = ForestGreenHeader
                )
                if (selectedDateFilter != CreditDueDateFilter.ALL || selectedSortOrder != CreditDueSortOrder.HIGH_TO_LOW || selectedStatusFilter != CreditDueStatusFilter.ALL) {
                    Text(
                        text = if (isTamil) "வடிகட்டப்பட்டது" else "Filtered",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SageAccent
                    )
                }
            }
        }
        if (filteredCustomers.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SageCardBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (selectedStatusFilter == CreditDueStatusFilter.PAID) {
                            Text(if (isTamil) "வடிகட்டலில் முழுமையாக செலுத்திய வாடிக்கையாளர்கள் யாரும் இல்லை" else "No fully paid customers found in this filter", fontWeight = FontWeight.Bold, color = DeepSageGreen, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Text(if (isTamil) "₹0 இருப்பு உள்ள வாடிக்கையாளர்கள் இங்கு தோன்றுவர்." else "Customers with ₹0 balance will appear here.", fontSize = 12.sp, color = SageAccent, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        } else {
                            Text(if (isTamil) "பொருந்தும் பதிவுகள் எதுவும் இல்லை! 🎉" else "No matching records found! 🎉", fontWeight = FontWeight.Bold, color = DeepSageGreen, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Text(if (isTamil) "அனைத்து கணக்குகளும் தீர்க்கப்பட்டுள்ளன அல்லது வடித்தலுடன் பொருந்துகின்றன." else "All accounts are settled or match filter.", fontSize = 12.sp, color = SageAccent, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
        } else {
            items(filteredCustomers, key = { it.id }) { customer ->
                CustomerCreditDueCard(
                    customer = customer,
                    isTamil = isTamil,
                    onClick = { selectedCustomer = customer },
                    onAddPayment = { customerForPayment = customer },
                    onShareWhatsApp = { shareCustomerDueWhatsApp(context, customer, settings.businessName) },
                    onSharePdf = {
                        val custJobs = jobs.filter {
                            it.customerId == customer.id || it.customerName.equals(customer.name, ignoreCase = true)
                        }
                        val file = PdfGeneratorHelper.generateCustomerStatementPdf(
                            context = context,
                            settings = settings,
                            customer = customer,
                            jobs = custJobs
                        )
                        file?.let {
                            PdfGeneratorHelper.sharePdfToWhatsAppOrGeneral(
                                context = context,
                                file = it,
                                phoneNumber = customer.phone,
                                subject = "Customer Statement - ${customer.name}"
                            )
                        }
                    }
                )
            }
        }
    }
    // Customer Statement & Details Bottom Sheet
    selectedCustomer?.let { customer ->
        // Keep updated customer data if changed in list
        val currentCustomer = customers.find { it.id == customer.id } ?: customer
        val customerJobs = jobs.filter {
            it.customerId == currentCustomer.id || it.customerName.equals(currentCustomer.name, ignoreCase = true)
        }
        CustomerDetailSheet(
            customer = currentCustomer,
            jobs = customerJobs,
            settings = settings,
            onDismiss = { selectedCustomer = null },
            onAddPayment = {
                customerForPayment = currentCustomer
            },
            onCall = {
                if (currentCustomer.phone.isNotBlank()) {
                    openDialer(context, currentCustomer.phone)
                } else {
                    Toast.makeText(context, if (isTamil) "${currentCustomer.name} கைபேசி எண் சேமிக்கப்படவில்லை" else "No phone number saved for ${currentCustomer.name}", Toast.LENGTH_SHORT).show()
                    customerToEditPhone = currentCustomer
                }
            },
            onWhatsApp = {
                val msg = buildCustomerDueWhatsAppMessage(currentCustomer, settings.businessName)
                sendWhatsAppMessage(context, currentCustomer.phone, msg)
            },
            onSharePdf = {
                val file = PdfGeneratorHelper.generateCustomerStatementPdf(
                    context = context,
                    settings = settings,
                    customer = currentCustomer,
                    jobs = customerJobs
                )
                file?.let {
                    val pdfMsg = "Namaste ${currentCustomer.name},\nPlease find attached your tractor work statement from ${settings.businessName}.\nOutstanding Due: ${formatInr(currentCustomer.balanceDue)}"
                    PdfGeneratorHelper.sharePdfToWhatsAppOrGeneral(
                        context = context,
                        file = it,
                        phoneNumber = currentCustomer.phone,
                        subject = "Tractor Statement - ${currentCustomer.name}",
                        message = pdfMsg
                    )
                }
            },
            onEditPhone = {
                customerToEditPhone = currentCustomer
            }
        )
    }
    // Dialog to Add Payment for Customer
    customerForPayment?.let { customer ->
        RecordCustomerPaymentDialog(
            customer = customer,
            settings = settings,
            onDismiss = { customerForPayment = null },
            onConfirm = { amount, dateMillis, method, note ->
                onRecordPayment?.invoke(customer, amount, dateMillis, method, note)
                customerForPayment = null
                // Also update selectedCustomer if open
                if (selectedCustomer?.id == customer.id) {
                    val updated = customers.find { it.id == customer.id }
                    if (updated != null) {
                        selectedCustomer = updated
                    }
                }
            }
        )
    }
    // Dialog to Add or Edit Customer Phone Number
    customerToEditPhone?.let { customer ->
        var phoneInput by remember { mutableStateOf(customer.phone) }
        var phoneError by remember { mutableStateOf(false) }
        val phoneRequester = remember { BringIntoViewRequester() }
        val coroutineScope = rememberCoroutineScope()
        AlertDialog(
            onDismissRequest = { customerToEditPhone = null },
            title = {
                Text(
                    text = if (isTamil) (if (customer.phone.isBlank()) "கைபேசி எண் சேர்க்க" else "கைபேசி எண் திருத்த") else (if (customer.phone.isBlank()) "Add Phone Number" else "Edit Phone Number"),
                    fontWeight = FontWeight.Bold,
                    color = ForestGreenHeader
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isTamil) "வாடிக்கையாளர்: ${customer.name}" else "Customer: ${customer.name}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DeepSageGreen
                    )
                    Text(
                        text = if (isTamil) "வாட்ஸ்அப் மற்றும் அறிக்கை விவரங்களை அனுப்ப செல்லுபடியாகும் 10 இலக்க மொபைல் எண்ணை உள்ளிடவும்." else "Enter a valid 10-digit mobile number for WhatsApp messaging and statements.",
                        fontSize = 12.sp,
                        color = TextSecondaryDark
                    )
                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = {
                            phoneInput = it
                            phoneError = false
                        },
                        label = { Text(if (isTamil) "10-இலக்க மொபைல் எண்" else "10-Digit Mobile Number") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = DeepSageGreen) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        isError = phoneError,
                        supportingText = {
                            if (phoneError) {
                                Text(if (isTamil) "செல்லுபடியாகும் 10 இலக்க எண்ணை உள்ளிடவும்" else "Please enter a valid 10-digit number", color = AlertDueRed)
                            } else {
                                val clean = sanitizePhoneNumberForStorage(phoneInput)
                                if (clean.length == 10) {
                                    Text(if (isTamil) "சரியான மொபைல் எண்: +91 $clean" else "Valid mobile number: +91 $clean", color = SuccessPaidGreen)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().trackFocusedField(phoneRequester, coroutineScope),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sanitized = sanitizePhoneNumberForStorage(phoneInput)
                        if (sanitized.length != 10 && sanitized.length !in 10..12) {
                            phoneError = true
                        } else {
                            val updatedCustomer = customer.copy(
                                phone = sanitized,
                                updatedAt = System.currentTimeMillis()
                            )
                            onUpdateCustomer?.invoke(updatedCustomer)
                            if (selectedCustomer?.id == customer.id) {
                                selectedCustomer = updatedCustomer
                            }
                            customerToEditPhone = null
                            Toast.makeText(context, if (isTamil) "கைபேசி எண் $sanitized ஆக மாற்றப்பட்டது" else "Phone number updated to $sanitized", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DeepSageGreen)
                ) {
                    Text(if (isTamil) "சேமி" else "Save Phone")
                }
            },
            dismissButton = {
                TextButton(onClick = { customerToEditPhone = null }) {
                    Text(if (isTamil) "ரத்து" else "Cancel")
                }
            }
        )
    }
}
fun shareCustomerDueWhatsApp(context: Context, customer: CustomerEntity, businessName: String = "AIDHUNT Trac Services") {
    val msg = buildCustomerDueWhatsAppMessage(customer, businessName)
    sendWhatsAppMessage(context, customer.phone, msg)
}
@Composable
fun CustomerCreditDueCard(
    customer: CustomerEntity,
    isTamil: Boolean = false,
    onClick: () -> Unit,
    onAddPayment: () -> Unit,
    onShareWhatsApp: () -> Unit,
    onSharePdf: () -> Unit
) {
    val responsive = com.example.ui.theme.rememberResponsiveDimensions()
    val isPaid = customer.balanceDue <= 0.0
    val initials = customer.name.trim().take(2).uppercase().ifBlank { "C" }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBg),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (isPaid) SuccessPaidGreen.copy(alpha = 0.35f) else AppTheme.colors.cardBorder.copy(alpha = 0.7f)
            )
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("customer_due_card_${customer.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (responsive.isSmallPhone) 12.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: Avatar + Name & Location + Status / Due Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Customer Avatar Circle with Initials
                Box(
                    modifier = Modifier
                        .size(if (responsive.isSmallPhone) 42.dp else 46.dp)
                        .clip(CircleShape)
                        .background(if (isPaid) SuccessPaidGreenBg else AlertDueRedBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        color = if (isPaid) SuccessPaidGreen else AlertDueRed,
                        fontSize = if (responsive.isSmallPhone) 14.sp else 15.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = customer.name,
                            fontSize = if (responsive.isSmallPhone) 15.sp else 16.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isPaid) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SuccessPaidGreenBg,
                                border = androidx.compose.foundation.BorderStroke(1.dp, SuccessPaidGreen.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = SuccessPaidGreen,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = if (isTamil) "செலுத்தப்பட்டது" else "PAID",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SuccessPaidGreen
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = formatInr(customer.balanceDue),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = AlertDueRed
                            )
                        }
                    }
                    if (customer.location.isNotBlank() || customer.phone.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (customer.location.isNotBlank()) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = DeepSageGreen,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = customer.location,
                                    fontSize = 12.sp,
                                    color = AppTheme.colors.textSecondary,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                            if (customer.phone.isNotBlank()) {
                                if (customer.location.isNotBlank()) {
                                    Text("•", fontSize = 10.sp, color = AppTheme.colors.textMuted)
                                }
                                Text(
                                    text = customer.phone,
                                    fontSize = 12.sp,
                                    color = AppTheme.colors.textSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = AppTheme.colors.textMuted
                )
            }
            // Middle Stats Row: Billed & Paid Summary
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (AppTheme.colors.isDark) Color(0xFF1E2320) else Color(0xFFF3F7F4),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isTamil) "மொத்த பில்: ${formatInr(customer.totalBilled)}" else "Total Billed: ${formatInr(customer.totalBilled)}",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppTheme.colors.textSecondary
                    )
                    Text(
                        text = if (isTamil) "செலுத்தியது: ${formatInr(customer.totalPaid)}" else "Paid: ${formatInr(customer.totalPaid)}",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SuccessPaidGreen
                    )
                }
            }
            Divider(color = AppTheme.colors.cardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
            // Bottom Actions: Share buttons + Payment / Statement
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    FlatShareIconButton(
                        onClick = onShareWhatsApp,
                        modifier = Modifier.testTag("btn_share_customer_card_${customer.id}"),
                        contentDescription = if (isTamil) "வாட்ஸ்அப் மூலம் பகிரவும்" else "Share via WhatsApp",
                        tint = SuccessPaidGreen,
                        iconSize = 20.dp
                    )
                    FlatPdfIconButton(
                        onClick = onSharePdf,
                        modifier = Modifier.testTag("btn_pdf_customer_card_${customer.id}"),
                        contentDescription = if (isTamil) "அறிக்கை PDF ஆக பகிரவும்" else "Share Statement PDF",
                        tint = DeepSageGreen,
                        iconSize = 20.dp
                    )
                }
                if (isPaid) {
                    TextButton(
                        onClick = onClick,
                        modifier = Modifier.testTag("btn_view_statement_card_${customer.id}")
                    ) {
                        Text(if (isTamil) "அறிக்கை விவரம்" else "Statement", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = DeepSageGreen)
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(14.dp), tint = DeepSageGreen)
                    }
                } else {
                    Button(
                        onClick = onAddPayment,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessPaidGreen),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("btn_add_payment_card_${customer.id}")
                    ) {
                        Icon(Icons.Default.Paid, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isTamil) "பணம் சேர்க்க" else "Add Payment", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailSheet(
    customer: CustomerEntity,
    jobs: List<JobEntryEntity>,
    settings: AppSettingsEntity,
    onDismiss: () -> Unit,
    onAddPayment: () -> Unit,
    onCall: () -> Unit,
    onWhatsApp: () -> Unit,
    onSharePdf: () -> Unit,
    onEditPhone: () -> Unit = {}
) {
    val isTamil = settings.language == "TA"
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val workJobs = jobs.filter { it.tractorLabel != "Payment" }
    val totalMinutes = workJobs.sumOf { it.durationMinutes }
    val tractorsUsed = workJobs.map { it.tractorLabel }.distinct().joinToString(", ").ifBlank { "N/A" }
    val operators = workJobs.map { it.operatorName }.distinct().joinToString(", ").ifBlank { "N/A" }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = customer.name,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForestGreenHeader
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (customer.phone.isNotBlank()) {
                                    "${if (isTamil) "கைபேசி" else "Phone"}: ${customer.phone} ${if (customer.location.isNotBlank()) "• " + customer.location else ""}"
                                } else {
                                    if (isTamil) "கைபேசி எண் சேமிக்கப்படவில்லை" else "No phone number saved"
                                },
                                fontSize = 12.sp,
                                color = if (customer.phone.isNotBlank()) TextSecondaryDark else AlertDueRed
                            )
                            IconButton(
                                onClick = onEditPhone,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = if (isTamil) "கைபேசி எண்ணைத் திருத்து" else "Edit Phone",
                                    tint = DeepSageGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                    StatusBadge(isPaid = customer.balanceDue <= 0, pendingAmount = customer.balanceDue)
                }
            }
            // Quick Actions: Add Payment, Call, WhatsApp, Share PDF Statement
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (customer.balanceDue > 0) {
                        Button(
                            onClick = onAddPayment,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("btn_sheet_add_payment"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessPaidGreen)
                        ) {
                            Icon(Icons.Default.Paid, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isTamil) "பணம் சேர்க்க (நிலுவை வசூல்)" else "Add Payment (Collect Due)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onCall,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isTamil) "அழை" else "Call", fontSize = 12.sp, maxLines = 1)
                        }
                        Button(
                            onClick = onWhatsApp,
                            modifier = Modifier
                                .weight(1.1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DeepSageGreen),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isTamil) "வாட்ஸ்அப்" else "WhatsApp", fontSize = 12.sp, maxLines = 1)
                        }
                        Button(
                            onClick = onSharePdf,
                            modifier = Modifier
                                .weight(1.2f)
                                .height(44.dp)
                                .testTag("btn_share_customer_statement_pdf"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreenHeader),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isTamil) "பகிர்க (PDF)" else "Share PDF", fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            }
            // Summary Card
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SageCardBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DetailRow(label = if (isTamil) "மொத்த பில் தொகை" else "Total Work Billed", value = formatInr(customer.totalBilled))
                        DetailRow(label = if (isTamil) "மொத்த செலுத்திய தொகை" else "Total Amount Paid", value = formatInr(customer.totalPaid))
                        DetailRow(label = if (isTamil) "வேலை செய்த மொத்த நேரம்" else "Total Hours Worked", value = com.example.ui.util.WorkBillingCalculator.formatDuration(totalMinutes))
                        DetailRow(label = if (isTamil) "பயன்படுத்தப்பட்ட டிராக்டர்கள்" else "Tractors Used", value = tractorsUsed)
                        DetailRow(label = if (isTamil) "இயக்குநர்கள்" else "Operators", value = operators)
                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(if (isTamil) "நிலுவைத் தொகை:" else "Outstanding Balance Due:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (customer.balanceDue > 0) AlertDueRed else SuccessPaidGreen)
                            Text(formatInr(customer.balanceDue), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (customer.balanceDue > 0) AlertDueRed else SuccessPaidGreen)
                        }
                    }
                }
            }
            // Transaction / Job History
            item {
                Text(
                    text = if (isTamil) "பரிவர்த்தனை & கட்டண வரலாறு (${jobs.size})" else "Transaction & Payment History (${jobs.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = ForestGreenHeader
                )
            }
            if (jobs.isEmpty()) {
                item {
                    Text(if (isTamil) "இந்த வாடிக்கையாளருக்கு எந்த வேலை பதிவும் இல்லை." else "No job records recorded under this customer.", fontSize = 12.sp, color = TextMutedDark)
                }
            } else {
                items(jobs) { job ->
                    val isPayment = job.tractorLabel == "Payment" || job.workType == "Payment Received"
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isPayment) SuccessPaidGreenBg else Color.White
                        ),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(
                                if (isPayment) SuccessPaidGreen.copy(alpha = 0.4f) else SageOutline.copy(alpha = 0.5f)
                            )
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (isPayment) {
                                        Icon(
                                            imageVector = Icons.Default.Paid,
                                            contentDescription = null,
                                            tint = SuccessPaidGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Text(
                                        text = if (isTamil && (job.workType == "Payment Received" || job.tractorLabel == "Payment")) {
                                            "கட்டணம் பெறப்பட்டது"
                                        } else {
                                            job.workType
                                        },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPayment) SuccessPaidGreen else ForestGreenHeader
                                    )
                                }
                                Text(
                                    text = if (isPayment) "+ ${formatInr(job.amountReceived)}" else formatInr(job.totalAmount),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPayment) SuccessPaidGreen else Color.Black
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isPayment) {
                                    "${formatDate(job.startTimeMillis)} • ${job.notes.ifBlank { if (isTamil) "நேரடி பணம் பெறப்பட்டது" else "Direct Payment Received" }}"
                                } else {
                                    "${formatDate(job.startTimeMillis)} • ${com.example.ui.util.WorkBillingCalculator.formatDuration(job.durationMinutes)} • ${job.tractorLabel}"
                                },
                                fontSize = 11.sp,
                                color = TextMutedDark
                             )
                            if (!isPayment) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("${if (isTamil) "செலுத்தியது" else "Paid"}: ${formatInr(job.amountReceived)}", fontSize = 11.sp, color = SuccessPaidGreen)
                                    Text("${if (isTamil) "நிலுவை" else "Due"}: ${formatInr(job.pendingAmount)}", fontSize = 11.sp, color = if (job.pendingAmount > 0) AlertDueRed else SuccessPaidGreen, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
@Composable
fun RecordCustomerPaymentDialog(
    customer: CustomerEntity,
    settings: AppSettingsEntity,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, dateMillis: Long, method: String, note: String) -> Unit
) {
    val context = LocalContext.current
    val isTamil = settings.language == "TA"
    val coroutineScope = rememberCoroutineScope()
    val dialogAmountRequester = remember { BringIntoViewRequester() }
    val dialogNotesRequester = remember { BringIntoViewRequester() }
    var amountInput by remember { mutableStateOf(if (customer.balanceDue > 0) String.format(Locale.US, "%.0f", customer.balanceDue) else "") }
    var paymentDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selectedPaymentMethod by remember { mutableStateOf("Cash") }
    var noteInput by remember { mutableStateOf("") }
    var hasAttemptedSubmit by remember { mutableStateOf(false) }
    val amountValue = amountInput.toDoubleOrNull() ?: 0.0
    val displayDue = String.format(Locale.US, "%.0f", customer.balanceDue).toDoubleOrNull() ?: customer.balanceDue
    val isAmountInvalid = amountValue <= 0.0 || amountValue > displayDue
    val paymentMethods = listOf("Cash", "UPI / GPay", "Bank Transfer", "Cheque")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SuccessPaidGreenBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Paid,
                        contentDescription = null,
                        tint = SuccessPaidGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = if (isTamil) "கட்டணம் சேர்க்க" else "Add Payment",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = ForestGreenHeader
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Customer Summary Info
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SageCardBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = if (isTamil) "வாடிக்கையாளர்: ${customer.name}" else "Customer: ${customer.name}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = ForestGreenHeader
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(if (isTamil) "நிலுவைத் தொகை:" else "Pending Due:", fontSize = 12.sp, color = TextSecondaryDark)
                            Text(
                                formatInr(customer.balanceDue),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (customer.balanceDue > 0) AlertDueRed else SuccessPaidGreen
                            )
                        }
                    }
                }
                // 1. Amount Received (₹) - Mandatory with red validation outline and helper text
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text(if (isTamil) "பெறப்பட்ட தொகை (₹) *" else "Amount Received (₹) *") },
                    leadingIcon = { Icon(Icons.Default.Paid, contentDescription = null, tint = DeepSageGreen) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = hasAttemptedSubmit && isAmountInvalid,
                    supportingText = {
                        if (hasAttemptedSubmit && isAmountInvalid) {
                            Text(
                                text = if (amountInput.isBlank()) {
                                    if (isTamil) "இந்தத் துறை கட்டாயமாகும்" else "This field is required"
                                } else if (amountValue <= 0.0) {
                                    if (isTamil) "பூஜ்ஜியத்தை விட அதிகமான தொகையை உள்ளிடவும்" else "Please enter an amount greater than 0"
                                } else {
                                    if (isTamil) "தொகை நிலுவைத் தொகையை விட அதிகமாக இருக்க முடியாது" else "Amount cannot exceed outstanding due"
                                },
                                color = AlertDueRed,
                                fontSize = 11.sp
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .trackFocusedField(dialogAmountRequester, coroutineScope)
                        .testTag("input_payment_amount"),
                    shape = RoundedCornerShape(10.dp)
                )
                // 2. Date Selection (Defaults to Today, Editable)
                val cal = Calendar.getInstance().apply { timeInMillis = paymentDateMillis }
                OutlinedButton(
                    onClick = {
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                val newCal = Calendar.getInstance().apply {
                                    set(year, month, dayOfMonth)
                                }
                                paymentDateMillis = newCal.timeInMillis
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_payment_date"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = DeepSageGreen, modifier = Modifier.size(18.dp))
                            Text(if (isTamil) "கட்டணம் செலுத்திய தேதி:" else "Payment Date:", fontSize = 12.sp, color = TextSecondaryDark)
                        }
                        Text(
                            formatDate(paymentDateMillis),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = ForestGreenHeader
                        )
                    }
                }
                // 3. Payment Method (Optional)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (isTamil) "பணம் செலுத்தும் முறை (விருப்பத்தேர்வு)" else "Payment Method (Optional)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondaryDark
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        paymentMethods.take(2).forEach { method ->
                            FilterChip(
                                selected = selectedPaymentMethod == method,
                                onClick = { selectedPaymentMethod = method },
                                label = { Text(getLocalizedPaymentMethod(method, isTamil), fontSize = 11.sp) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = DeepSageGreen,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        paymentMethods.drop(2).forEach { method ->
                            FilterChip(
                                selected = selectedPaymentMethod == method,
                                onClick = { selectedPaymentMethod = method },
                                label = { Text(getLocalizedPaymentMethod(method, isTamil), fontSize = 11.sp) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = DeepSageGreen,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
                // 4. Note (Optional)
                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text(if (isTamil) "குறிப்பு / விபரம் (விருப்பத்தேர்வு)" else "Note / Remarks (Optional)") },
                    placeholder = { Text(if (isTamil) "எ.கா. அறுவடை இருப்பு கணக்கு தீர்க்கப்பட்டது" else "e.g. Cleared harvest balance") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .trackFocusedField(dialogNotesRequester, coroutineScope),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    hasAttemptedSubmit = true
                    if (!isAmountInvalid) {
                        onConfirm(
                            amountValue,
                            paymentDateMillis,
                            selectedPaymentMethod,
                            noteInput.trim()
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SuccessPaidGreen),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("btn_confirm_record_payment")
            ) {
                Text(if (isTamil) "கட்டணத்தைச் சேமி" else "Save Payment", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isTamil) "ரத்து" else "Cancel", color = TextMutedDark)
            }
        }
    )
}
