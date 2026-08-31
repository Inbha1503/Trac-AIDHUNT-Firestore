package com.example.ui.screens.report

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.PartnerEntity
import com.example.data.entity.TractorEntity
import com.example.pdf.PdfGeneratorHelper
import com.example.ui.components.buildExpenseWhatsAppMessage
import com.example.ui.components.formatInr
import com.example.ui.components.sendWhatsAppMessage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

val ExpenseTypes = listOf(
    "Diesel",
    "Petrol",
    "Repair",
    "Puncture",
    "Oil Change",
    "Driver Bata",
    "Spare Parts",
    "Toll / Parking",
    "Other"
)

val PaymentModes = listOf(
    "Cash",
    "UPI / GPay / PhonePe",
    "Bank Transfer",
    "Credit / Due",
    "Cheque"
)

data class ExpenseVisualConfig(
    val icon: ImageVector,
    val bgColor: Color,
    val tintColor: Color
)

fun getExpenseVisualConfig(type: String): ExpenseVisualConfig {
    return when (type.lowercase().trim()) {
        "diesel" -> ExpenseVisualConfig(
            icon = Icons.Default.LocalGasStation,
            bgColor = Color(0xFFDCFCE7), // Light Emerald Green
            tintColor = Color(0xFF16A34A)
        )
        "petrol" -> ExpenseVisualConfig(
            icon = Icons.Default.Opacity,
            bgColor = Color(0xFFFEF3C7), // Light Amber
            tintColor = Color(0xFFD97706)
        )
        "repair", "spare parts" -> ExpenseVisualConfig(
            icon = Icons.Default.Build,
            bgColor = Color(0xFFF3E8FF), // Light Purple
            tintColor = Color(0xFF9333EA)
        )
        "puncture" -> ExpenseVisualConfig(
            icon = Icons.Default.DirectionsCar,
            bgColor = Color(0xFFFFEDD5), // Light Orange
            tintColor = Color(0xFFEA580C)
        )
        "oil change" -> ExpenseVisualConfig(
            icon = Icons.Default.Opacity,
            bgColor = Color(0xFFE0F2FE), // Light Sky Blue
            tintColor = Color(0xFF0284C7)
        )
        "driver bata" -> ExpenseVisualConfig(
            icon = Icons.Default.Person,
            bgColor = Color(0xFFEDE9FE), // Light Violet
            tintColor = Color(0xFF6366F1)
        )
        else -> ExpenseVisualConfig(
            icon = Icons.Default.MonetizationOn,
            bgColor = Color(0xFFF1F5F9), // Light Slate
            tintColor = Color(0xFF475569)
        )
    }
}

enum class ExpenseScreenView {
    REPORT_LIST,
    ADD_EXPENSE,
    EDIT_EXPENSE,
    EXPENSE_DETAILS
}

enum class DatePreset(val label: String) {
    TODAY("Today"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    ALL_TIME("All Time")
}

@Composable
fun ExpensesTab(
    settings: AppSettingsEntity,
    expenses: List<ExpenseEntity>,
    tractors: List<TractorEntity>,
    partners: List<PartnerEntity>,
    onAddExpense: (ExpenseEntity) -> Unit,
    onUpdateExpense: (ExpenseEntity) -> Unit,
    onDeleteExpense: (ExpenseEntity) -> Unit
) {
    var currentView by remember { mutableStateOf(ExpenseScreenView.REPORT_LIST) }
    var activeExpense by remember { mutableStateOf<ExpenseEntity?>(null) }

    // Intercept back button when in subview
    BackHandler(enabled = currentView != ExpenseScreenView.REPORT_LIST) {
        if (currentView == ExpenseScreenView.EDIT_EXPENSE && activeExpense != null) {
            currentView = ExpenseScreenView.EXPENSE_DETAILS
        } else {
            currentView = ExpenseScreenView.REPORT_LIST
            activeExpense = null
        }
    }

    Crossfade(targetState = currentView, label = "expense_view_transition") { viewState ->
        when (viewState) {
            ExpenseScreenView.REPORT_LIST -> {
                ExpenseReportListScreen(
                    settings = settings,
                    expenses = expenses,
                    tractors = tractors,
                    partners = partners,
                    onOpenAddExpense = {
                        activeExpense = null
                        currentView = ExpenseScreenView.ADD_EXPENSE
                    },
                    onOpenExpenseDetails = { expense ->
                        activeExpense = expense
                        currentView = ExpenseScreenView.EXPENSE_DETAILS
                    },
                    onQuickEdit = { expense ->
                        activeExpense = expense
                        currentView = ExpenseScreenView.EDIT_EXPENSE
                    },
                    onQuickDelete = { expense ->
                        onDeleteExpense(expense)
                    }
                )
            }
            ExpenseScreenView.ADD_EXPENSE -> {
                AddOrEditExpenseScreen(
                    isEditMode = false,
                    initialExpense = null,
                    tractors = tractors,
                    partners = partners,
                    settings = settings,
                    onBack = {
                        currentView = ExpenseScreenView.REPORT_LIST
                        activeExpense = null
                    },
                    onSaveExpense = { newExpense ->
                        onAddExpense(newExpense)
                        currentView = ExpenseScreenView.REPORT_LIST
                        activeExpense = null
                    }
                )
            }
            ExpenseScreenView.EDIT_EXPENSE -> {
                AddOrEditExpenseScreen(
                    isEditMode = true,
                    initialExpense = activeExpense,
                    tractors = tractors,
                    partners = partners,
                    settings = settings,
                    onBack = {
                        currentView = if (activeExpense != null) ExpenseScreenView.EXPENSE_DETAILS else ExpenseScreenView.REPORT_LIST
                    },
                    onSaveExpense = { updatedExpense ->
                        onUpdateExpense(updatedExpense)
                        activeExpense = updatedExpense
                        currentView = ExpenseScreenView.EXPENSE_DETAILS
                    }
                )
            }
            ExpenseScreenView.EXPENSE_DETAILS -> {
                activeExpense?.let { expense ->
                    ExpenseDetailsScreen(
                        expense = expense,
                        settings = settings,
                        onBack = {
                            currentView = ExpenseScreenView.REPORT_LIST
                            activeExpense = null
                        },
                        onEdit = {
                            currentView = ExpenseScreenView.EDIT_EXPENSE
                        },
                        onDelete = {
                            onDeleteExpense(expense)
                            currentView = ExpenseScreenView.REPORT_LIST
                            activeExpense = null
                        }
                    )
                } ?: run {
                    currentView = ExpenseScreenView.REPORT_LIST
                }
            }
        }
    }
}

/* =========================================================================================
   1. EXPENSE REPORT LIST SCREEN (Main Screen)
   ========================================================================================= */
@Composable
fun ExpenseReportListScreen(
    settings: AppSettingsEntity,
    expenses: List<ExpenseEntity>,
    tractors: List<TractorEntity>,
    partners: List<PartnerEntity>,
    onOpenAddExpense: () -> Unit,
    onOpenExpenseDetails: (ExpenseEntity) -> Unit,
    onQuickEdit: (ExpenseEntity) -> Unit,
    onQuickDelete: (ExpenseEntity) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedDatePreset by remember { mutableStateOf(DatePreset.ALL_TIME) }
    var customStartDate by remember { mutableLongStateOf(getStartOfDayMillis()) }
    var customEndDate by remember { mutableLongStateOf(getEndOfDayMillis()) }

    var selectedOperatorFilter by remember { mutableStateOf("All") }
    var selectedTractorFilter by remember { mutableStateOf("All") }
    var selectedTypeFilter by remember { mutableStateOf("All") }

    var isPresetMenuOpen by remember { mutableStateOf(false) }
    var isOperatorMenuOpen by remember { mutableStateOf(false) }
    var isTractorMenuOpen by remember { mutableStateOf(false) }
    var isTypeMenuOpen by remember { mutableStateOf(false) }

    // Filter Logic
    val filteredExpenses = expenses.filter { exp ->
        // Search
        val matchesSearch = searchQuery.isBlank() ||
                exp.description.contains(searchQuery, ignoreCase = true) ||
                exp.expenseType.contains(searchQuery, ignoreCase = true) ||
                exp.operatorName.contains(searchQuery, ignoreCase = true) ||
                exp.tractorLabel.contains(searchQuery, ignoreCase = true)

        // Dropdowns
        val matchesOperator = selectedOperatorFilter == "All" ||
                exp.operatorName.contains(selectedOperatorFilter, ignoreCase = true) ||
                exp.addedByPartner.contains(selectedOperatorFilter, ignoreCase = true)
        val matchesTractor = selectedTractorFilter == "All" ||
                exp.tractorLabel.contains(selectedTractorFilter, ignoreCase = true)
        val matchesType = selectedTypeFilter == "All" ||
                exp.expenseType.equals(selectedTypeFilter, ignoreCase = true)

        // Date filter
        val matchesDate = when (selectedDatePreset) {
            DatePreset.TODAY -> exp.dateTimestamp in getStartOfDayMillis()..getEndOfDayMillis()
            DatePreset.THIS_WEEK -> exp.dateTimestamp in getStartOfWeekMillis()..getEndOfDayMillis()
            DatePreset.THIS_MONTH -> exp.dateTimestamp in getStartOfMonthMillis()..getEndOfDayMillis()
            DatePreset.ALL_TIME -> true
        }

        matchesSearch && matchesOperator && matchesTractor && matchesType && matchesDate
    }

    val totalAmount = filteredExpenses.sumOf { it.amount }
    val totalCount = filteredExpenses.size
    val avgAmount = if (totalCount > 0) totalAmount / totalCount else 0.0
    val lowestAmount = filteredExpenses.minOfOrNull { it.amount } ?: 0.0
    val highestAmount = filteredExpenses.maxOfOrNull { it.amount } ?: 0.0

    val dateFormat = remember { SimpleDateFormat("dd/MM/yy\nhh:mm a", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9FAFB)),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Action Header (Title + Export PDF + Add Expense Green Button)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Expense Report",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )

                // + Add Expense Button
                Button(
                    onClick = onOpenAddExpense,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF166534)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("btn_add_expense_top")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "+ Add Expense",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Date Range Selector Row (Preset + From Date + - + To Date + Calendar Icon)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Preset Dropdown Chip
                Box {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF166534),
                        modifier = Modifier
                            .clickable { isPresetMenuOpen = true }
                            .height(46.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        ) {
                            Text(
                                text = selectedDatePreset.label,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = isPresetMenuOpen,
                        onDismissRequest = { isPresetMenuOpen = false }
                    ) {
                        DatePreset.values().forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(preset.label) },
                                onClick = {
                                    selectedDatePreset = preset
                                    isPresetMenuOpen = false
                                }
                            )
                        }
                    }
                }

                // From Date Box (Clickable to pick start date)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White,
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(Color(0xFFE5E7EB))),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clickable {
                            showDatePicker(context, customStartDate) { picked ->
                                customStartDate = picked
                            }
                        }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = dateFormat.format(Date(customStartDate)),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF374151),
                            textAlign = TextAlign.Center,
                            lineHeight = 13.sp
                        )
                    }
                }

                Text(
                    text = "-",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6B7280)
                )

                // To Date Box (Clickable to pick end date)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White,
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(Color(0xFFE5E7EB))),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clickable {
                            showDatePicker(context, customEndDate) { picked ->
                                customEndDate = picked + 86399000L
                            }
                        }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = dateFormat.format(Date(customEndDate)),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF374151),
                            textAlign = TextAlign.Center,
                            lineHeight = 13.sp
                        )
                    }
                }

                // Calendar Picker Icon
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White,
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(Color(0xFFE5E7EB))),
                    modifier = Modifier
                        .size(46.dp)
                        .clickable {
                            showDatePicker(context, customStartDate) { picked ->
                                customStartDate = picked
                                customEndDate = picked + 86399000L
                            }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Pick Date",
                            tint = Color(0xFF4B5563),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // 3 Filter Dropdowns Row: Operator, Tractor, Expense Type
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 1. Operator Filter
                FilterDropdownButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Person,
                    iconTint = Color(0xFF16A34A),
                    label = "Operator",
                    selectedValue = selectedOperatorFilter,
                    isOpen = isOperatorMenuOpen,
                    onToggle = { isOperatorMenuOpen = !isOperatorMenuOpen },
                    onDismiss = { isOperatorMenuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All Operators", fontWeight = if (selectedOperatorFilter == "All") FontWeight.Bold else FontWeight.Normal) },
                        onClick = {
                            selectedOperatorFilter = "All"
                            isOperatorMenuOpen = false
                        }
                    )
                    partners.forEach { partner ->
                        DropdownMenuItem(
                            text = { Text(partner.name) },
                            onClick = {
                                selectedOperatorFilter = partner.name
                                isOperatorMenuOpen = false
                            }
                        )
                    }
                }

                // 2. Tractor Filter
                FilterDropdownButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.DirectionsCar,
                    iconTint = Color(0xFF16A34A),
                    label = "Tractor",
                    selectedValue = selectedTractorFilter,
                    isOpen = isTractorMenuOpen,
                    onToggle = { isTractorMenuOpen = !isTractorMenuOpen },
                    onDismiss = { isTractorMenuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All Fleet", fontWeight = if (selectedTractorFilter == "All") FontWeight.Bold else FontWeight.Normal) },
                        onClick = {
                            selectedTractorFilter = "All"
                            isTractorMenuOpen = false
                        }
                    )
                    tractors.forEach { tractor ->
                        DropdownMenuItem(
                            text = { Text(tractor.label) },
                            onClick = {
                                selectedTractorFilter = tractor.label
                                isTractorMenuOpen = false
                            }
                        )
                    }
                }

                // 3. Expense Type Filter
                FilterDropdownButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Tag,
                    iconTint = Color(0xFF16A34A),
                    label = "Type",
                    selectedValue = selectedTypeFilter,
                    isOpen = isTypeMenuOpen,
                    onToggle = { isTypeMenuOpen = !isTypeMenuOpen },
                    onDismiss = { isTypeMenuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All Types", fontWeight = if (selectedTypeFilter == "All") FontWeight.Bold else FontWeight.Normal) },
                        onClick = {
                            selectedTypeFilter = "All"
                            isTypeMenuOpen = false
                        }
                    )
                    ExpenseTypes.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type) },
                            onClick = {
                                selectedTypeFilter = type
                                isTypeMenuOpen = false
                            }
                        )
                    }
                }
            }
        }

        // Search Description Field
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search description, type, operator...", fontSize = 13.sp, color = Color(0xFF9CA3AF)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFF9CA3AF),
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = Color(0xFF9CA3AF),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF166534),
                    unfocusedBorderColor = Color(0xFFE5E7EB),
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("expense_search_bar")
            )
        }

        // 3 Stat Cards: Total Expenses, Total Entries, Avg. per Entry
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Card 1: Total Expenses
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.ArrowDownward,
                    iconBg = Color(0xFFDCFCE7),
                    iconColor = Color(0xFF16A34A),
                    title = "Total Expenses",
                    value = formatInr(totalAmount, settings.currency)
                )

                // Card 2: Total Entries
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                    iconBg = Color(0xFFE0E7FF),
                    iconColor = Color(0xFF4F46E5),
                    title = "Total Entries",
                    value = "$totalCount"
                )

                // Card 3: Avg. per Entry
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.MonetizationOn,
                    iconBg = Color(0xFFFEF3C7),
                    iconColor = Color(0xFFD97706),
                    title = "Avg. per Entry",
                    value = formatInr(avgAmount, settings.currency)
                )
            }
        }

        // Expense List Items or Empty State
        if (filteredExpenses.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(Color(0xFFE5E7EB))),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = Color(0xFF9CA3AF),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No expenses found",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF374151)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap '+ Add Expense' above to record a new expense",
                            fontSize = 12.sp,
                            color = Color(0xFF6B7280),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(filteredExpenses, key = { it.id }) { expense ->
                ExpenseListItemRow(
                    expense = expense,
                    currency = settings.currency,
                    onClick = { onOpenExpenseDetails(expense) },
                    onEdit = { onQuickEdit(expense) },
                    onDelete = { onQuickDelete(expense) },
                    onShare = {
                        val shareText = buildExpenseWhatsAppMessage(expense, settings.businessName)
                        sendWhatsAppMessage(context, null, shareText)
                    }
                )
            }
        }

        // Footer Summary: Lowest Expense & Highest Expense
        if (filteredExpenses.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(Color(0xFFE5E7EB))),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Lowest Expense",
                                fontSize = 11.5.sp,
                                color = Color(0xFF4B5563),
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formatInr(lowestAmount, settings.currency),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF16A34A)
                            )
                        }

                        Divider(
                            modifier = Modifier
                                .height(32.dp)
                                .width(1.dp),
                            color = Color(0xFFE5E7EB)
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Highest Expense",
                                fontSize = 11.5.sp,
                                color = Color(0xFF4B5563),
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formatInr(highestAmount, settings.currency),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFDC2626)
                            )
                        }
                    }
                }
            }
        }
    }
}

/* =========================================================================================
   Filter Dropdown Button Component
   ========================================================================================= */
@Composable
fun FilterDropdownButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconTint: Color,
    label: String,
    selectedValue: String,
    isOpen: Boolean,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val isFiltered = selectedValue != "All" && selectedValue.isNotBlank()

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isFiltered) Color(0xFFF0FDF4) else Color.White,
            border = CardDefaults.outlinedCardBorder().copy(
                brush = SolidColor(if (isFiltered) Color(0xFF16A34A) else Color(0xFFE5E7EB))
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isFiltered) Color(0xFF16A34A) else iconTint,
                    modifier = Modifier.size(15.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        fontSize = 9.5.sp,
                        color = Color(0xFF6B7280),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = selectedValue,
                            fontSize = 10.5.sp,
                            fontWeight = if (isFiltered) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (isFiltered) Color(0xFF166534) else Color(0xFF111827),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color(0xFF9CA3AF),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }

        DropdownMenu(
            expanded = isOpen,
            onDismissRequest = onDismiss
        ) {
            content()
        }
    }
}

/* =========================================================================================
   Stat Card Component
   ========================================================================================= */
@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconBg: Color,
    iconColor: Color,
    title: String,
    value: String
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(Color(0xFFE5E7EB))),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(11.dp)
                    )
                }
                Text(
                    text = title,
                    fontSize = 10.sp,
                    color = Color(0xFF4B5563),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = value,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/* =========================================================================================
   Expense List Item Row
   ========================================================================================= */
@Composable
fun ExpenseListItemRow(
    expense: ExpenseEntity,
    currency: String,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    val visualConfig = getExpenseVisualConfig(expense.expenseType)
    val itemDateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(Color(0xFFE5E7EB))),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("expense_item_${expense.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Left Category Rounded Square Icon Box
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(visualConfig.bgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = visualConfig.icon,
                    contentDescription = expense.expenseType,
                    tint = visualConfig.tintColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Middle Info Column (Expense Type, Tractor, Operator, Description)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.expenseType,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (expense.tractorLabel.isNotBlank()) {
                    Text(
                        text = expense.tractorLabel,
                        fontSize = 11.5.sp,
                        color = Color(0xFF4B5563),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val opName = expense.operatorName.ifBlank { expense.addedByPartner }
                if (opName.isNotBlank()) {
                    Text(
                        text = opName,
                        fontSize = 11.sp,
                        color = Color(0xFF6B7280),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (expense.description.isNotBlank()) {
                    Text(
                        text = expense.description,
                        fontSize = 10.5.sp,
                        color = Color(0xFF9CA3AF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right Column: Amount + 3-dots Menu + Date
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = formatInr(expense.amount, currency),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFDC2626)
                    )

                    Box {
                        IconButton(
                            onClick = { isMenuExpanded = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = Color(0xFF9CA3AF),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = isMenuExpanded,
                            onDismissRequest = { isMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = Color(0xFF166534)) },
                                text = { Text("View Details") },
                                onClick = {
                                    isMenuExpanded = false
                                    onClick()
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF2563EB)) },
                                text = { Text("Edit Expense") },
                                onClick = {
                                    isMenuExpanded = false
                                    onEdit()
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF16A34A)) },
                                text = { Text("Share WhatsApp") },
                                onClick = {
                                    isMenuExpanded = false
                                    onShare()
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFDC2626)) },
                                text = { Text("Delete", color = Color(0xFFDC2626)) },
                                onClick = {
                                    isMenuExpanded = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = itemDateFormat.format(Date(expense.dateTimestamp)),
                    fontSize = 10.sp,
                    color = Color(0xFF6B7280)
                )
            }
        }
    }
}

/* =========================================================================================
   2. ADD / EDIT EXPENSE SCREEN
   ========================================================================================= */
@Composable
fun AddOrEditExpenseScreen(
    isEditMode: Boolean,
    initialExpense: ExpenseEntity?,
    tractors: List<TractorEntity>,
    partners: List<PartnerEntity>,
    settings: AppSettingsEntity,
    onBack: () -> Unit,
    onSaveExpense: (ExpenseEntity) -> Unit
) {
    val context = LocalContext.current

    // Operator Default: If creating a new expense, default to whoever is logged in (settings.activePartnerName)
    val defaultOperatorName = remember(initialExpense, settings.activePartnerName, partners) {
        if (initialExpense != null) {
            initialExpense.operatorName
        } else {
            // Find matched partner from partners list or use active partner name
            val matchedPartner = partners.find {
                it.name.equals(settings.activePartnerName, ignoreCase = true) ||
                        settings.activePartnerName.startsWith(it.name, ignoreCase = true) ||
                        it.name.startsWith(settings.activePartnerName, ignoreCase = true)
            }
            matchedPartner?.let { "${it.name} (${it.role})" }
                ?: settings.activePartnerName.ifBlank {
                    partners.firstOrNull()?.let { "${it.name} (${it.role})" } ?: ""
                }
        }
    }

    var selectedTimestamp by remember { mutableLongStateOf(initialExpense?.dateTimestamp ?: System.currentTimeMillis()) }
    var selectedType by remember { mutableStateOf(initialExpense?.expenseType ?: "Diesel") }
    var selectedOperator by remember { mutableStateOf(defaultOperatorName) }
    var selectedTractor by remember {
        mutableStateOf(initialExpense?.tractorLabel ?: tractors.firstOrNull()?.label ?: "")
    }
    var amountText by remember { mutableStateOf(if (initialExpense != null) initialExpense.amount.toInt().toString() else "") }
    var descriptionText by remember { mutableStateOf(initialExpense?.description ?: "") }
    var paymentMode by remember { mutableStateOf("Cash") }

    var isTypeDropdownOpen by remember { mutableStateOf(false) }
    var isOperatorDropdownOpen by remember { mutableStateOf(false) }
    var isTractorDropdownOpen by remember { mutableStateOf(false) }
    var isPaymentModeDropdownOpen by remember { mutableStateOf(false) }

    var hasValidated by remember { mutableStateOf(false) }
    val isAmountValid = (amountText.toDoubleOrNull() ?: 0.0) > 0

    val formDateFormat = remember { SimpleDateFormat("dd/MM/yyyy  hh:mm a", java.util.Locale.getDefault()) }

    fun doSave() {
        hasValidated = true
        val amt = amountText.toDoubleOrNull() ?: 0.0
        if (amt > 0) {
            val matchedTractor = tractors.find { it.label == selectedTractor }
            val expense = ExpenseEntity(
                id = initialExpense?.id ?: 0,
                expenseType = selectedType,
                amount = amt,
                tractorId = matchedTractor?.id ?: 0,
                tractorLabel = selectedTractor,
                operatorName = selectedOperator,
                description = descriptionText,
                addedByPartner = initialExpense?.addedByPartner ?: settings.activePartnerName.ifBlank { "" },
                dateTimestamp = selectedTimestamp,
                createdAt = initialExpense?.createdAt ?: System.currentTimeMillis()
            )
            onSaveExpense(expense)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9FAFB))
    ) {
        // Top Navigation Bar
        Surface(
            color = Color(0xFF166534), // Dark Emerald Header
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isEditMode) "Edit Expense" else "Add Expense",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Top Right Save Text + Icon Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { doSave() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Save",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Save",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Form Body
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Row 1: Date & Time* (Left) and Expense Type* (Right)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Date & Time Field (Full visible datetime)
                    FormFieldCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.CalendarToday,
                        label = "Date & Time*",
                        value = formDateFormat.format(Date(selectedTimestamp)),
                        isDropdown = false,
                        onClick = {
                            showDateTimePicker(context, selectedTimestamp) { pickedTime ->
                                selectedTimestamp = pickedTime
                            }
                        }
                    )

                    // Expense Type Field
                    Box(modifier = Modifier.weight(1f)) {
                        FormFieldCard(
                            icon = Icons.Default.Tag,
                            label = "Expense Type*",
                            value = selectedType,
                            isDropdown = true,
                            onClick = { isTypeDropdownOpen = true }
                        )

                        DropdownMenu(
                            expanded = isTypeDropdownOpen,
                            onDismissRequest = { isTypeDropdownOpen = false }
                        ) {
                            ExpenseTypes.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type) },
                                    onClick = {
                                        selectedType = type
                                        isTypeDropdownOpen = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Row 2: Operator* (Left) and Tractor (Chassis No.)* (Right)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Operator Dropdown (Default to logged in user)
                    Box(modifier = Modifier.weight(1f)) {
                        FormFieldCard(
                            icon = Icons.Default.Person,
                            label = "Operator*",
                            value = selectedOperator,
                            isDropdown = true,
                            onClick = { isOperatorDropdownOpen = true }
                        )

                        DropdownMenu(
                            expanded = isOperatorDropdownOpen,
                            onDismissRequest = { isOperatorDropdownOpen = false }
                        ) {
                            partners.forEach { partner ->
                                val operatorLabel = "${partner.name} (${partner.role})"
                                DropdownMenuItem(
                                    text = { Text(operatorLabel) },
                                    onClick = {
                                        selectedOperator = operatorLabel
                                        isOperatorDropdownOpen = false
                                    }
                                )
                            }
                        }
                    }

                    // Tractor Dropdown
                    Box(modifier = Modifier.weight(1f)) {
                        FormFieldCard(
                            icon = Icons.Default.DirectionsCar,
                            label = "Tractor (Chassis No.)*",
                            value = selectedTractor,
                            isDropdown = true,
                            onClick = { isTractorDropdownOpen = true }
                        )

                        DropdownMenu(
                            expanded = isTractorDropdownOpen,
                            onDismissRequest = { isTractorDropdownOpen = false }
                        ) {
                            tractors.forEach { tractor ->
                                DropdownMenuItem(
                                    text = { Text(tractor.label) },
                                    onClick = {
                                        selectedTractor = tractor.label
                                        isTractorDropdownOpen = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Row 3: Amount (₹)* Input
            item {
                FormInputCard(
                    iconText = "₹",
                    label = "Amount (₹)*",
                    value = amountText,
                    onValueChange = { amountText = it },
                    keyboardType = KeyboardType.Number,
                    placeholder = "e.g. 2500",
                    isError = hasValidated && !isAmountValid,
                    errorMessage = "Please enter a valid amount greater than 0",
                    testTag = "input_expense_amount"
                )
            }

            // Row 4: Description / Purpose (Optional)
            item {
                FormInputCard(
                    icon = Icons.Default.Description,
                    label = "Description / Purpose (Optional)",
                    value = descriptionText,
                    onValueChange = { descriptionText = it },
                    keyboardType = KeyboardType.Text,
                    placeholder = "e.g. Diesel for field work / Tyre puncture repair",
                    testTag = "input_expense_description"
                )
            }

            // Row 5: Payment Mode
            item {
                Box(modifier = Modifier.fillMaxWidth()) {
                    FormFieldCard(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Default.Payments,
                        label = "Payment Mode",
                        value = paymentMode,
                        isDropdown = true,
                        onClick = { isPaymentModeDropdownOpen = true }
                    )

                    DropdownMenu(
                        expanded = isPaymentModeDropdownOpen,
                        onDismissRequest = { isPaymentModeDropdownOpen = false }
                    ) {
                        PaymentModes.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode) },
                                onClick = {
                                    paymentMode = mode
                                    isPaymentModeDropdownOpen = false
                                }
                            )
                        }
                    }
                }
            }

            // Required fields note
            item {
                Text(
                    text = "* Required fields",
                    fontSize = 11.5.sp,
                    color = Color(0xFFDC2626),
                    fontWeight = FontWeight.Medium
                )
            }

            // Big Green Save Button
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { doSave() },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF166534)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("btn_save_expense_submit")
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEditMode) "Update Expense" else "Save Expense",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

/* =========================================================================================
   3. EXPENSE DETAILS SCREEN
   ========================================================================================= */
@Composable
fun ExpenseDetailsScreen(
    expense: ExpenseEntity,
    settings: AppSettingsEntity,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    val visualConfig = getExpenseVisualConfig(expense.expenseType)
    val detailsDateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9FAFB))
    ) {
        // Top Green Header
        Surface(
            color = Color(0xFF166534),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Expense Details",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Top Right Edit & Share Icons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            val shareText = buildExpenseWhatsAppMessage(expense, settings.businessName)
                            sendWhatsAppMessage(context, null, shareText)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag("btn_edit_expense_top")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Expense",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // Details Body
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Card with Category, Description, and Amount
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(Color(0xFFE5E7EB))),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(visualConfig.bgColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = visualConfig.icon,
                                contentDescription = null,
                                tint = visualConfig.tintColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = expense.expenseType,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF111827)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (expense.description.isNotBlank()) expense.description else "No description provided",
                                fontSize = 12.5.sp,
                                color = Color(0xFF6B7280),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = formatInr(expense.amount, settings.currency),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFDC2626)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Cash",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF16A34A)
                        )
                    }
                }
            }

            // 6 Grid Details Cards
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(Color(0xFFE5E7EB))),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Row 1: Date & Time & Operator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DetailInfoItem(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.CalendarToday,
                            label = "Date & Time",
                            value = detailsDateFormat.format(Date(expense.dateTimestamp))
                        )
                        DetailInfoItem(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Person,
                            label = "Operator",
                            value = expense.operatorName.ifBlank { settings.activePartnerName }
                        )
                    }

                    Divider(color = Color(0xFFF3F4F6), thickness = 1.dp)

                    // Row 2: Tractor & Expense Type
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DetailInfoItem(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.DirectionsCar,
                            label = "Tractor (Chassis No.)",
                            value = expense.tractorLabel
                        )
                        DetailInfoItem(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Tag,
                            label = "Expense Type",
                            value = expense.expenseType
                        )
                    }

                    Divider(color = Color(0xFFF3F4F6), thickness = 1.dp)

                    // Row 3: Added By & Created At
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DetailInfoItem(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Person,
                            label = "Added By",
                            value = expense.addedByPartner.ifBlank { settings.activePartnerName }
                        )
                        DetailInfoItem(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Schedule,
                            label = "Created At",
                            value = detailsDateFormat.format(Date(expense.createdAt))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Delete Expense Button
            OutlinedButton(
                onClick = { showDeleteConfirmDialog = true },
                shape = RoundedCornerShape(10.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(Color(0xFFDC2626))),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_delete_expense_details")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Delete Expense",
                    color = Color(0xFFDC2626),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }

    // Confirmation Alert
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Expense?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete this ${expense.expenseType} record of ${formatInr(expense.amount, settings.currency)}? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/* =========================================================================================
   Helper Form Components
   ========================================================================================= */
@Composable
fun FormFieldCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    isDropdown: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(Color(0xFFE5E7EB))),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF16A34A),
                modifier = Modifier.size(18.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 10.sp,
                    color = Color(0xFF6B7280),
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF111827),
                    maxLines = 2,
                    lineHeight = 15.sp,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isDropdown) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color(0xFF9CA3AF),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun FormInputCard(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconText: String? = null,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    placeholder: String = "",
    isError: Boolean = false,
    errorMessage: String = "",
    testTag: String = ""
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(if (isError) Color(0xFFDC2626) else Color(0xFFE5E7EB))
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color(0xFF16A34A),
                        modifier = Modifier.size(18.dp)
                    )
                } else if (iconText != null) {
                    Text(
                        text = iconText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF16A34A)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        color = Color(0xFF6B7280),
                        fontWeight = FontWeight.Medium
                    )

                    OutlinedTextField(
                        value = value,
                        onValueChange = onValueChange,
                        placeholder = { Text(placeholder, fontSize = 13.sp, color = Color(0xFF9CA3AF)) },
                        singleLine = keyboardType == KeyboardType.Number,
                        maxLines = if (keyboardType == KeyboardType.Number) 1 else 3,
                        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(testTag)
                    )
                }
            }

            if (isError && errorMessage.isNotBlank()) {
                Text(
                    text = errorMessage,
                    color = Color(0xFFDC2626),
                    fontSize = 10.5.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
fun DetailInfoItem(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF16A34A),
            modifier = Modifier.size(18.dp)
        )
        Column {
            Text(
                text = label,
                fontSize = 10.sp,
                color = Color(0xFF6B7280),
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF111827),
                maxLines = 2,
                lineHeight = 15.sp,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/* =========================================================================================
   Date & Time Helper Utilities
   ========================================================================================= */
fun showDatePicker(context: Context, initialMillis: Long, onPicked: (Long) -> Unit) {
    val cal = Calendar.getInstance().apply { timeInMillis = initialMillis }
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val resultCal = Calendar.getInstance().apply {
                timeInMillis = initialMillis
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, dayOfMonth)
            }
            onPicked(resultCal.timeInMillis)
        },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH)
    ).show()
}

fun showDateTimePicker(context: Context, initialMillis: Long, onPicked: (Long) -> Unit) {
    val cal = Calendar.getInstance().apply { timeInMillis = initialMillis }
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    val resultCal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, month)
                        set(Calendar.DAY_OF_MONTH, dayOfMonth)
                        set(Calendar.HOUR_OF_DAY, hourOfDay)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onPicked(resultCal.timeInMillis)
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                false
            ).show()
        },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH)
    ).show()
}

fun getStartOfDayMillis(): Long {
    return Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

fun getEndOfDayMillis(): Long {
    return Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis
}

fun getStartOfWeekMillis(): Long {
    return Calendar.getInstance().apply {
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

fun getStartOfMonthMillis(): Long {
    return Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
