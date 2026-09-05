package com.example.ui.screens.entry

import com.example.ui.utils.trackFocusedField
import android.app.TimePickerDialog
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.CustomerEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.TractorEntity
import com.example.ui.components.DetailRow
import com.example.ui.components.PartnerAvatarImage
import com.example.ui.components.formatDateTime
import com.example.ui.components.formatInr
import com.example.ui.components.sanitizePhoneNumberForStorage
import com.example.ui.screens.report.ExpenseTypes
import com.example.ui.theme.AlertDueRed
import com.example.ui.theme.AppTheme
import com.example.ui.theme.DeepSageGreen
import com.example.ui.theme.EarthGold
import com.example.ui.theme.ForestGreenHeader
import com.example.ui.theme.SageAccent
import com.example.ui.theme.SageCardBg
import com.example.ui.theme.SageOutline
import com.example.ui.theme.SoftSageGreen
import com.example.ui.theme.SuccessPaidGreen
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.viewmodel.FIXED_WORK_TYPES
import com.example.ui.viewmodel.NewEntryDraft
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

private enum class FocusedField {
    NONE,
    NAME,
    PHONE,
    LOCATION,
    HOURS,
    MINUTES,
    HOURLY_RATE,
    WORK_AMOUNT,
    EXTRA_CHARGES,
    AMOUNT_RECEIVED,
    EXPENSE_AMOUNT,
    EXPENSE_DESC,
    NOTES
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NewEntryScreen(
    settings: AppSettingsEntity,
    tractors: List<TractorEntity>,
    customers: List<CustomerEntity>,
    draft: NewEntryDraft,
    isSaving: Boolean = false,
    onUpdateDraft: (NewEntryDraft) -> Unit,
    onClearDraft: () -> Unit,
    onSaveJob: (JobEntryEntity, ExpenseEntity?) -> Unit,
    onUpdateLockedTractor: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val isTamil = settings.language.equals("TA", ignoreCase = true)

    androidx.compose.runtime.LaunchedEffect(Unit) {
        Log.d("TRAC_ENTRY", "NewEntryScreen OPEN wsId=${settings.workspaceId}")
    }

    var localIsSaving by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(isSaving) {
        if (!isSaving) {
            localIsSaving = false
        }
    }
    val effectiveIsSaving = isSaving || localIsSaving

    // Handle Back Press when on Review Screen or Dialogs to prevent accidental screen close
    BackHandler(enabled = draft.isReviewScreenVisible) {
        Log.d("TRAC_ENTRY", "BackHandler: exiting review screen back to edit form")
        if (!effectiveIsSaving) {
            onUpdateDraft(draft.copy(isReviewScreenVisible = false))
        }
    }

    // 1. Partner Name: Auto-filled from logged-in account, read-only
    val currentPartnerName = settings.activePartnerName

    // 2. Tractor Selection & Lock State
    val ownTractors = remember(tractors, settings.workspaceId) {
        tractors.filter { it.workspaceId == settings.workspaceId }
    }
    val selectedTractor = if (draft.selectedTractor.isNotBlank() && tractors.any { it.label == draft.selectedTractor }) {
        draft.selectedTractor
    } else if (settings.lockedTractorLabel.isNotBlank() && tractors.any { it.label == settings.lockedTractorLabel }) {
        settings.lockedTractorLabel
    } else {
        ownTractors.firstOrNull()?.label ?: ""
    }
    val isTractorLocked = draft.isTractorLocked
    val selectedWorkType = draft.selectedWorkType

    var tractorDropdownExpanded by remember { mutableStateOf(false) }
    var workTypeDropdownExpanded by remember { mutableStateOf(false) }

    // 3. Customer Details
    val customerNameInput = draft.customerNameInput
    val customerPhoneInput = draft.customerPhoneInput
    val customerLocationInput = draft.customerLocationInput
    val matchedCustomerId = draft.matchedCustomerId
    var showSuggestions by remember { mutableStateOf(false) }

    // 4. Time Selection: Manual Duration vs Clock Time Option
    val isDirectDurationMode = draft.isDirectDurationMode
    val manualHoursInput = draft.manualHoursInput
    val manualMinutesInput = draft.manualMinutesInput

    val startHour = draft.startHour
    val startMinute = draft.startMinute
    val endHour = draft.endHour
    val endMinute = draft.endMinute

    val computedDurationMinutes: Long = if (isDirectDurationMode) {
        com.example.ui.util.WorkBillingCalculator.calculateDurationFromManual(manualHoursInput, manualMinutesInput)
    } else {
        com.example.ui.util.WorkBillingCalculator.calculateDurationFromClock(startHour, startMinute, endHour, endMinute)
    }

    // 5. Payment Details
    val hourlyRateInput = draft.hourlyRateInput
    val hourlyRate = hourlyRateInput.toDoubleOrNull() ?: 0.0

    val calculatedWorkAmount = com.example.ui.util.WorkBillingCalculator.calculateAmount(computedDurationMinutes, hourlyRate)
    val customWorkAmountInput = draft.customWorkAmountInput
    val baseWorkAmount = customWorkAmountInput.toDoubleOrNull() ?: calculatedWorkAmount

    val extraChargesInput = draft.extraChargesInput
    val extraCharges = extraChargesInput.toDoubleOrNull() ?: 0.0

    val finalTotalAmount = baseWorkAmount + extraCharges

    val amountReceivedInput = draft.amountReceivedInput
    val amountReceived = amountReceivedInput.toDoubleOrNull() ?: 0.0
    val pendingAmount = (finalTotalAmount - amountReceived).coerceAtLeast(0.0)

    // 6. Optional Linked Expense
    val includeLinkedExpense = draft.includeLinkedExpense
    val linkedExpenseType = draft.linkedExpenseType
    val linkedExpenseAmountInput = draft.linkedExpenseAmountInput
    val linkedExpenseDesc = draft.linkedExpenseDesc
    var expenseTypeDropdownExpanded by remember { mutableStateOf(false) }

    // Notes
    val notes = draft.notes

    // Validation & Clear State
    val hasAttemptedReview = draft.hasAttemptedReview
    var showClearConfirmationDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val tractorRequester = remember { BringIntoViewRequester() }
    val workTypeRequester = remember { BringIntoViewRequester() }
    val expenseTypeRequester = remember { BringIntoViewRequester() }
    val customerNameRequester = remember { BringIntoViewRequester() }
    val customerPhoneRequester = remember { BringIntoViewRequester() }
    val customerLocationRequester = remember { BringIntoViewRequester() }
    val hoursRequester = remember { BringIntoViewRequester() }
    val minutesRequester = remember { BringIntoViewRequester() }
    val hourlyRateRequester = remember { BringIntoViewRequester() }
    val workAmountRequester = remember { BringIntoViewRequester() }
    val extraChargesRequester = remember { BringIntoViewRequester() }
    val amountReceivedRequester = remember { BringIntoViewRequester() }
    val expenseAmountRequester = remember { BringIntoViewRequester() }
    val expenseDescRequester = remember { BringIntoViewRequester() }
    val notesRequester = remember { BringIntoViewRequester() }

    BackHandler(enabled = showClearConfirmationDialog) {
        Log.d("TRAC_ENTRY", "BackHandler: dismissing clear confirmation dialog")
        showClearConfirmationDialog = false
    }

    val isTractorInvalid = selectedTractor.isBlank() || (tractors.isNotEmpty() && tractors.none { it.label == selectedTractor })
    val isCustomerNameInvalid = customerNameInput.isBlank()
    val isCustomerPhoneInvalid = customerPhoneInput.length != 10 || !customerPhoneInput.all { it.isDigit() }
    val isTimeInvalid = computedDurationMinutes <= 0L
    val isWorkAmountInvalid = (customWorkAmountInput.isNotBlank() && (customWorkAmountInput.toDoubleOrNull() ?: 0.0) <= 0.0) ||
            (customWorkAmountInput.isBlank() && calculatedWorkAmount <= 0.0)
    val isAmountReceivedInvalid = amountReceivedInput.isBlank() || amountReceivedInput.toDoubleOrNull() == null
    val isLinkedExpenseInvalid = includeLinkedExpense && ((linkedExpenseAmountInput.toDoubleOrNull() ?: 0.0) <= 0.0 || linkedExpenseType.isBlank())

    val isSystem24Hour = android.text.format.DateFormat.is24HourFormat(context)

    fun formatTimeDisplay(hour: Int?, minute: Int?): String {
        if (hour == null || minute == null) return "--:--"
        return if (isSystem24Hour) {
            String.format(Locale.US, "%02d:%02d", hour, minute)
        } else {
            val amPm = if (hour < 12) "AM" else "PM"
            val h = when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }
            String.format(Locale.US, "%02d:%02d %s", h, minute, amPm)
        }
    }

    val customerSuggestions = if (customerPhoneInput.trim().isNotEmpty()) {
        val queryDigits = customerPhoneInput.filter { it.isDigit() }
        customers.filter { c ->
            val cDigits = c.phone.filter { it.isDigit() }
            cDigits.contains(queryDigits)
        }
    } else {
        emptyList()
    }

    val handleClearAction = {
        if (draft.isModified()) {
            showClearConfirmationDialog = true
        } else {
            onClearDraft()
        }
    }

    val responsive = com.example.ui.theme.rememberResponsiveDimensions()

    if (draft.isReviewScreenVisible) {
        // Full Dedicated Review Page
        val now = System.currentTimeMillis()
        val startMillis: Long
        val endMillis: Long
        if (isDirectDurationMode || startHour == null || startMinute == null || endHour == null || endMinute == null) {
            startMillis = now - (computedDurationMinutes * 60 * 1000L)
            endMillis = now
        } else {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, startHour)
                set(Calendar.MINUTE, startMinute)
                set(Calendar.SECOND, 0)
            }
            startMillis = calendar.timeInMillis
            val endCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, endHour)
                set(Calendar.MINUTE, endMinute)
                set(Calendar.SECOND, 0)
            }
            endMillis = if (endCal.timeInMillis >= startMillis) endCal.timeInMillis else endCal.timeInMillis + (24 * 3600 * 1000L)
        }

        FullReviewAndSaveScreen(
            settings = settings,
            partnerName = currentPartnerName,
            tractorLabel = selectedTractor,
            workType = if (isTamil) (FIXED_WORK_TYPES.find { it.en == selectedWorkType }?.ta ?: selectedWorkType) else selectedWorkType,
            customerName = customerNameInput,
            customerPhone = customerPhoneInput,
            customerLocation = customerLocationInput,
            durationMinutes = computedDurationMinutes,
            hourlyRate = hourlyRate,
            workAmount = baseWorkAmount,
            extraCharges = extraCharges,
            totalAmount = finalTotalAmount,
            amountReceived = amountReceived,
            pendingAmount = pendingAmount,
            notes = notes,
            linkedExpense = if (includeLinkedExpense && (linkedExpenseAmountInput.toDoubleOrNull() ?: 0.0) > 0) {
                ExpenseEntity(
                    id = if (draft.linkedExpenseId > 0L) draft.linkedExpenseId else 0L,
                    expenseType = linkedExpenseType,
                    amount = linkedExpenseAmountInput.toDoubleOrNull() ?: 0.0,
                    tractorLabel = selectedTractor,
                    operatorName = currentPartnerName,
                    description = linkedExpenseDesc.ifBlank { "Expense for ${customerNameInput.trim()}'s job" },
                    addedByPartner = currentPartnerName
                )
            } else null,
            isSaving = effectiveIsSaving,
            onBackToEdit = {
                if (!effectiveIsSaving) {
                    onUpdateDraft(draft.copy(isReviewScreenVisible = false))
                }
            },
            onConfirmSave = {
                if (effectiveIsSaving) return@FullReviewAndSaveScreen
                localIsSaving = true

                val entryId = if (draft.entryId > 0L) draft.entryId else com.example.data.util.IdGenerator.generateId()
                val expenseId = if (draft.linkedExpenseId > 0L) draft.linkedExpenseId else com.example.data.util.IdGenerator.generateId()
                if (draft.entryId <= 0L || draft.linkedExpenseId <= 0L) {
                    onUpdateDraft(draft.copy(entryId = entryId, linkedExpenseId = expenseId))
                }

                val matchedTractor = tractors.find { it.label == selectedTractor }
                val job = JobEntryEntity(
                    id = entryId,
                    customerId = matchedCustomerId,
                    customerName = customerNameInput.trim(),
                    customerPhone = customerPhoneInput.trim(),
                    customerLocation = customerLocationInput.trim(),
                    tractorId = matchedTractor?.id ?: 0L,
                    tractorLabel = selectedTractor,
                    operatorName = currentPartnerName,
                    workType = selectedWorkType,
                    startTimeMillis = startMillis,
                    endTimeMillis = endMillis,
                    hourlyRate = hourlyRate,
                    durationMinutes = computedDurationMinutes,
                    totalAmount = finalTotalAmount,
                    amountReceived = amountReceived,
                    pendingAmount = pendingAmount,
                    notes = notes,
                    addedByPartner = currentPartnerName
                )

                val linkedExpense = if (includeLinkedExpense && (linkedExpenseAmountInput.toDoubleOrNull() ?: 0.0) > 0) {
                    ExpenseEntity(
                        id = expenseId,
                        expenseType = linkedExpenseType,
                        amount = linkedExpenseAmountInput.toDoubleOrNull() ?: 0.0,
                        tractorId = matchedTractor?.id ?: 0L,
                        tractorLabel = selectedTractor,
                        operatorName = currentPartnerName,
                        description = linkedExpenseDesc.ifBlank { "Linked expense with ${customerNameInput.trim()}'s job" },
                        addedByPartner = currentPartnerName
                    )
                } else null

                onSaveJob(job, linkedExpense)
            }
        )
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(AppTheme.colors.background),
            contentPadding = PaddingValues(
                start = responsive.screenPaddingHorizontal,
                end = responsive.screenPaddingHorizontal,
                top = responsive.screenPaddingVertical,
                bottom = 120.dp
            ),
            verticalArrangement = Arrangement.spacedBy(if (responsive.isSmallPhone) 10.dp else 14.dp)
        ) {
            // Action Bar: Clear/Reset Draft Button & Unsaved Draft Indicator
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (draft.isModified()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = EarthGold.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = if (isTamil) "சேமிக்கப்படாத வரைவு (Draft)" else "Unsaved Draft",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = EarthGold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    OutlinedButton(
                        onClick = handleClearAction,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertDueRed),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AlertDueRed.copy(alpha = 0.4f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("btn_clear_header")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Clear",
                            tint = AlertDueRed,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isTamil) "அழி" else "Clear Draft",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AlertDueRed
                        )
                    }
                }
            }

            // 1. Partner Name (Auto-filled from logged-in account, read-only display)
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SoftSageGreen),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SageOutline)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(if (responsive.isSmallPhone) 10.dp else 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PartnerAvatarImage(
                            photoUri = settings.profilePhotoUri,
                            name = currentPartnerName,
                            size = if (responsive.isSmallPhone) 38.dp else 44.dp,
                            avatarColorHex = "#1E4D2B"
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isTamil) "பதிவு செய்யும் பங்குதாரர்" else "Partner",
                                fontSize = if (responsive.isSmallPhone) 10.sp else 11.sp,
                                color = AppTheme.colors.textMuted,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = currentPartnerName,
                                fontSize = if (responsive.isSmallPhone) 14.sp else 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.textPrimary,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SoftSageGreen
                        ) {
                            Text(
                                text = if (isTamil) "உள்நுழைவு" else "Active",
                                fontSize = if (responsive.isSmallPhone) 10.sp else 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = DeepSageGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // 2. Tractor Selection & Lock Button
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBg),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.cardBorder)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isTamil) "1. டிராக்டர் & பணி தேர்வு" else "1. Tractor & Attachment",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = DeepSageGreen
                            )

                            // Tractor Lock Indicator / Button
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isTractorLocked) SoftSageGreen else Color.Transparent,
                                modifier = Modifier.clickable {
                                    val newLocked = !isTractorLocked
                                    onUpdateDraft(draft.copy(isTractorLocked = newLocked))
                                    val lockedLabel = if (newLocked) selectedTractor else ""
                                    onUpdateLockedTractor?.invoke(lockedLabel)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isTractorLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                        contentDescription = if (isTractorLocked) "Locked" else "Unlocked",
                                        tint = if (isTractorLocked) DeepSageGreen else TextMutedDark,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (isTractorLocked) (if (isTamil) "இயல்புநிலை பூட்டப்பட்டது" else "Default Locked") else (if (isTamil) "பூட்டு" else "Lock Default"),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isTractorLocked) DeepSageGreen else TextMutedDark
                                    )
                                }
                            }
                        }

                        // Tractor Dropdown with Lock Icon
                        val tractorDisplayText = if (tractors.isEmpty()) {
                            if (isTamil) "டிராக்டர் எதுவும் சேர்க்கப்படவில்லை" else "No tractor added"
                        } else if (selectedTractor.isBlank()) {
                            if (isTamil) "டிராக்டரைத் தேர்ந்தெடுக்கவும்" else "Select tractor"
                        } else {
                            selectedTractor
                        }

                        ExposedDropdownMenuBox(
                            expanded = tractorDropdownExpanded,
                            onExpandedChange = {
                                if (tractors.isNotEmpty()) {
                                    tractorDropdownExpanded = !tractorDropdownExpanded
                                }
                            }
                        ) {
                            OutlinedTextField(
                                value = tractorDisplayText,
                                onValueChange = {},
                                readOnly = true,
                                isError = hasAttemptedReview && (isTractorInvalid || tractors.isEmpty()),
                                supportingText = {
                                    if (tractors.isEmpty()) {
                                        Text(
                                            text = if (isTamil) "முதலில் ஒரு டிராக்டரைச் சேர்க்கவும்" else "Please add a tractor before creating an entry.",
                                            color = AlertDueRed,
                                            fontSize = 11.sp
                                        )
                                    } else if (hasAttemptedReview && isTractorInvalid) {
                                        Text(
                                            text = if (isTamil) "டிராக்டர் தேர்வு செய்க" else "Please select a tractor",
                                            color = AlertDueRed,
                                            fontSize = 11.sp
                                        )
                                    }
                                },
                                label = { Text(if (isTamil) "டிராக்டர் தேர்வு செய்க *" else "Select Tractor *") },
                                trailingIcon = {
                                    if (tractors.isNotEmpty()) {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = tractorDropdownExpanded)
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Agriculture,
                                        contentDescription = null,
                                        tint = if (hasAttemptedReview && (isTractorInvalid || tractors.isEmpty())) AlertDueRed else DeepSageGreen
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                                    .trackFocusedField(tractorRequester, coroutineScope)
                                    .testTag("entry_tractor_dropdown"),
                                shape = RoundedCornerShape(12.dp)
                            )
                            if (tractors.isNotEmpty()) {
                                ExposedDropdownMenu(
                                    expanded = tractorDropdownExpanded,
                                    onDismissRequest = { tractorDropdownExpanded = false }
                                ) {
                                    tractors.forEach { t ->
                                        DropdownMenuItem(
                                            text = { Text("${t.label} • ${t.modelYear}") },
                                            onClick = {
                                                onUpdateDraft(draft.copy(selectedTractor = t.label))
                                                tractorDropdownExpanded = false
                                                if (isTractorLocked) {
                                                    onUpdateLockedTractor?.invoke(t.label)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Work / Extension Type Dropdown (4 Fixed Options with Tamil support)
                        ExposedDropdownMenuBox(
                            expanded = workTypeDropdownExpanded,
                            onExpandedChange = { workTypeDropdownExpanded = !workTypeDropdownExpanded }
                        ) {
                            val displayWorkType = if (isTamil) {
                                FIXED_WORK_TYPES.find { it.en == selectedWorkType }?.ta ?: selectedWorkType
                            } else {
                                selectedWorkType
                            }

                            OutlinedTextField(
                                value = displayWorkType,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(if (isTamil) "பணி / கருவி வகை" else "Work / Extension Type") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = workTypeDropdownExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                                    .trackFocusedField(workTypeRequester, coroutineScope)
                                    .testTag("entry_work_type_dropdown"),
                                shape = RoundedCornerShape(12.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = workTypeDropdownExpanded,
                                onDismissRequest = { workTypeDropdownExpanded = false }
                            ) {
                                FIXED_WORK_TYPES.forEach { item ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(if (isTamil) "${item.ta} (${item.en})" else item.en)
                                        },
                                        onClick = {
                                            onUpdateDraft(draft.copy(selectedWorkType = item.en))
                                            workTypeDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Customer Details (Fixed Non-disruptive Autocomplete)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SageOutline)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (isTamil) "2. வாடிக்கையாளர் விவரங்கள்" else "2. Customer Details",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = DeepSageGreen
                        )

                        // Customer Name Field
                        OutlinedTextField(
                            value = customerNameInput,
                            onValueChange = {
                                onUpdateDraft(draft.copy(customerNameInput = it, matchedCustomerId = 0L))
                            },
                            label = { Text(if (isTamil) "விவசாயி / வாடிக்கையாளர் பெயர் *" else "Customer Name *") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = if (hasAttemptedReview && isCustomerNameInvalid) AlertDueRed else SageAccent
                                )
                            },
                            singleLine = true,
                            isError = hasAttemptedReview && isCustomerNameInvalid,
                            supportingText = {
                                if (hasAttemptedReview && isCustomerNameInvalid) {
                                    Text(if (isTamil) "வாடிக்கையாளர் பெயர் தேவை" else "This field is required", color = AlertDueRed, fontSize = 11.sp)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .trackFocusedField(customerNameRequester, coroutineScope)
                                .testTag("entry_customer_name_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Customer Phone & Location
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customerPhoneInput,
                                onValueChange = { input ->
                                    val digitsOnly = input.filter { it.isDigit() }.take(10)
                                    onUpdateDraft(draft.copy(customerPhoneInput = digitsOnly))
                                    showSuggestions = true
                                },
                                label = { Text(if (isTamil) "மொபைல் எண் *" else "Mobile Number *") },
                                placeholder = { Text(if (isTamil) "10 இலக்க எண்" else "10-digit mobile number", fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Phone,
                                        contentDescription = null,
                                        tint = if (hasAttemptedReview && isCustomerPhoneInvalid) AlertDueRed else SageAccent
                                    )
                                },
                                singleLine = true,
                                isError = hasAttemptedReview && isCustomerPhoneInvalid,
                                supportingText = {
                                    if (hasAttemptedReview && isCustomerPhoneInvalid) {
                                        Text(
                                            text = if (isTamil) "சரியான 10 இலக்க மொபைல் எண் தேவை" else "Please enter a valid 10-digit mobile number",
                                            color = AlertDueRed,
                                            fontSize = 10.sp
                                        )
                                    } else if (customerPhoneInput.length == 10) {
                                        Text("WhatsApp: +91 $customerPhoneInput", color = SuccessPaidGreen, fontSize = 10.sp)
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .trackFocusedField(customerPhoneRequester, coroutineScope)
                                    .testTag("entry_customer_phone_input"),
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = customerLocationInput,
                                onValueChange = { onUpdateDraft(draft.copy(customerLocationInput = it)) },
                                label = { Text(if (isTamil) "ஊர் / தோட்டம்" else "Village / Location") },
                                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = SageAccent) },
                                singleLine = true,
                                supportingText = {
                                    if (hasAttemptedReview && isCustomerPhoneInvalid) {
                                        Text("", fontSize = 10.sp)
                                    } else if (customerPhoneInput.length == 10) {
                                        Text("", fontSize = 10.sp)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .trackFocusedField(customerLocationRequester, coroutineScope),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        // Smooth Non-intrusive Suggestion Cards below phone input
                        AnimatedVisibility(
                            visible = showSuggestions && customerSuggestions.isNotEmpty(),
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = SageCardBg),
                                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SageOutline)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(6.dp)) {
                                    Text(
                                        text = if (isTamil) "பரிந்துரைக்கப்பட்ட வாடிக்கையாளர்கள்:" else "Existing Customers (Tap to auto-fill):",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SageAccent,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                    customerSuggestions.take(4).forEach { c ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color.White,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 3.dp)
                                                .clickable {
                                                    onUpdateDraft(
                                                        draft.copy(
                                                            customerNameInput = c.name,
                                                            customerPhoneInput = c.phone,
                                                            customerLocationInput = c.location,
                                                            matchedCustomerId = c.id
                                                        )
                                                    )
                                                    showSuggestions = false
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(c.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ForestGreenHeader)
                                                    Text(
                                                        "${c.phone} ${if (c.location.isNotBlank()) "• ${c.location}" else ""}",
                                                        fontSize = 11.sp,
                                                        color = TextMutedDark
                                                    )
                                                }
                                                if (c.balanceDue > 0) {
                                                    Text(
                                                        "Due: ${formatInr(c.balanceDue, settings.currency)}",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = AlertDueRed
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
            }

            // 4. Time Selection (Direct Duration vs Clock Time)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SageOutline)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isTamil) "3. வேலை நேரம்" else "3. Work Time",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = DeepSageGreen
                            )

                            // Direct Duration vs Clock Time Toggle
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SoftSageGreen.copy(alpha = 0.6f)
                            ) {
                                Row(modifier = Modifier.padding(2.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (!isDirectDurationMode) DeepSageGreen else Color.Transparent,
                                        modifier = Modifier.clickable { onUpdateDraft(draft.copy(isDirectDurationMode = false)) }
                                    ) {
                                        Text(
                                            text = if (isTamil) "நேரம்" else "Clock Time",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (!isDirectDurationMode) Color.White else DeepSageGreen,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isDirectDurationMode) DeepSageGreen else Color.Transparent,
                                        modifier = Modifier.clickable { onUpdateDraft(draft.copy(isDirectDurationMode = true)) }
                                    ) {
                                        Text(
                                            text = if (isTamil) "மணி / நிமிடம்" else "Hour / Min",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDirectDurationMode) Color.White else DeepSageGreen,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (!isDirectDurationMode) {
                            // Clock Start / End pickers
                            val curCal = Calendar.getInstance()
                            val isStartSet = startHour != null && startMinute != null
                            val isEndSet = endHour != null && endMinute != null

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val initH = startHour ?: curCal.get(Calendar.HOUR_OF_DAY)
                                        val initM = startMinute ?: curCal.get(Calendar.MINUTE)
                                        val dialog = TimePickerDialog(
                                            context,
                                            { _, h, m ->
                                                onUpdateDraft(draft.copy(startHour = h, startMinute = m))
                                            },
                                            initH,
                                            initM,
                                            isSystem24Hour
                                        )
                                        dialog.show()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("entry_start_time_btn")
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(if (isTamil) "தொடக்க நேரம்" else "Start Time", fontSize = 10.sp, color = TextMutedDark)
                                        Text(
                                            formatTimeDisplay(startHour, startMinute),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isStartSet) ForestGreenHeader else TextMutedDark
                                        )
                                    }
                                }

                                Text(if (isTamil) "முதல்" else "to", fontSize = 12.sp, color = TextMutedDark, fontWeight = FontWeight.Bold)

                                OutlinedButton(
                                    onClick = {
                                        val initH = endHour ?: curCal.get(Calendar.HOUR_OF_DAY)
                                        val initM = endMinute ?: curCal.get(Calendar.MINUTE)
                                        val dialog = TimePickerDialog(
                                            context,
                                            { _, h, m ->
                                                onUpdateDraft(draft.copy(endHour = h, endMinute = m))
                                            },
                                            initH,
                                            initM,
                                            isSystem24Hour
                                        )
                                        dialog.show()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("entry_end_time_btn")
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(if (isTamil) "முடிவு நேரம்" else "End Time", fontSize = 10.sp, color = TextMutedDark)
                                        Text(
                                            formatTimeDisplay(endHour, endMinute),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isEndSet) ForestGreenHeader else TextMutedDark
                                        )
                                    }
                                }
                            }
                        } else {
                            // Direct Hours & Minutes Input
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = manualHoursInput,
                                    onValueChange = { onUpdateDraft(draft.copy(manualHoursInput = it)) },
                                    label = { Text(if (isTamil) "மணிகள் (Hours) *" else "Hours *") },
                                    placeholder = { Text("0") },
                                    singleLine = true,
                                    isError = hasAttemptedReview && isTimeInvalid,
                                    supportingText = {
                                        if (hasAttemptedReview && isTimeInvalid) {
                                            Text(if (isTamil) "நேரம் தேவை" else "Required", color = AlertDueRed, fontSize = 10.sp)
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier
                                        .weight(1f)
                                        .trackFocusedField(hoursRequester, coroutineScope)
                                        .testTag("entry_manual_hours_input"),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                OutlinedTextField(
                                    value = manualMinutesInput,
                                    onValueChange = { onUpdateDraft(draft.copy(manualMinutesInput = it)) },
                                    label = { Text(if (isTamil) "நிமிடங்கள் (Mins) *" else "Minutes *") },
                                    placeholder = { Text("0") },
                                    singleLine = true,
                                    isError = hasAttemptedReview && isTimeInvalid,
                                    supportingText = {
                                        if (hasAttemptedReview && isTimeInvalid) {
                                            Text(if (isTamil) "நேரம் தேவை" else "Required", color = AlertDueRed, fontSize = 10.sp)
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier
                                        .weight(1f)
                                        .trackFocusedField(minutesRequester, coroutineScope)
                                        .testTag("entry_manual_minutes_input"),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }

                        // Duration Summary Banner
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SageCardBg,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (isTamil) "மொத்த கால அளவு:" else "Total Work Duration:", fontSize = 12.sp, color = ForestGreenHeader)
                                Text(
                                    if (computedDurationMinutes > 0) "${com.example.ui.util.WorkBillingCalculator.formatDuration(computedDurationMinutes)} ($computedDurationMinutes mins)" else "0 mins",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DeepSageGreen
                                )
                            }
                        }
                    }
                }
            }

            // 5. Payment Details (Work Amount, Amount Received, Extra Charges)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SageOutline)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (isTamil) "4. கட்டணக் கணக்கீடு" else "4. Payment & Billing Details",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = DeepSageGreen
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = hourlyRateInput,
                                onValueChange = { onUpdateDraft(draft.copy(hourlyRateInput = it)) },
                                label = { Text(if (isTamil) "மணிநேர விகிதம் (${settings.currency}/hr)" else "Rate (${settings.currency}/hr)") },
                                placeholder = { Text("0") },
                                singleLine = true,
                                supportingText = {
                                    if (hasAttemptedReview && isWorkAmountInvalid) {
                                        Text("", fontSize = 10.sp)
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .trackFocusedField(hourlyRateRequester, coroutineScope)
                                    .testTag("entry_hourly_rate_input"),
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = if (customWorkAmountInput.isNotBlank()) customWorkAmountInput else (if (calculatedWorkAmount > 0.0) String.format(Locale.US, "%.0f", calculatedWorkAmount) else ""),
                                onValueChange = { onUpdateDraft(draft.copy(customWorkAmountInput = it)) },
                                label = { Text(if (isTamil) "வேலைத் தொகை (${settings.currency}) *" else "Work Amount (${settings.currency}) *") },
                                placeholder = { Text("0") },
                                singleLine = true,
                                isError = hasAttemptedReview && isWorkAmountInvalid,
                                supportingText = {
                                    if (hasAttemptedReview && isWorkAmountInvalid) {
                                        Text(if (isTamil) "வேலைத் தொகை தேவை" else "This field is required", color = AlertDueRed, fontSize = 10.sp)
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .trackFocusedField(workAmountRequester, coroutineScope)
                                    .testTag("entry_work_amount_input"),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        // Single Optional Extra Charges (₹) Field
                        OutlinedTextField(
                            value = extraChargesInput,
                            onValueChange = { onUpdateDraft(draft.copy(extraChargesInput = it)) },
                            label = { Text(if (isTamil) "கூடுதல் கட்டணம் (${settings.currency}) - விருப்பத்தேர்வு" else "Extra Charges (${settings.currency}) - Optional") },
                            placeholder = { Text("e.g. 200 (diesel haul / transport)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .trackFocusedField(extraChargesRequester, coroutineScope)
                                .testTag("entry_extra_charges_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Amount Received & Pending Due
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = amountReceivedInput,
                                onValueChange = { onUpdateDraft(draft.copy(amountReceivedInput = it)) },
                                label = { Text(if (isTamil) "பெற்ற தொகை (${settings.currency}) *" else "Amount Received (${settings.currency}) *") },
                                singleLine = true,
                                isError = hasAttemptedReview && isAmountReceivedInvalid,
                                supportingText = {
                                    if (hasAttemptedReview && isAmountReceivedInvalid) {
                                        Text(if (isTamil) "பெற்ற தொகையை உள்ளிடவும் (கடன் எனில் 0)" else "Required (enter 0 if unpaid credit)", color = AlertDueRed, fontSize = 10.sp)
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .trackFocusedField(amountReceivedRequester, coroutineScope)
                                    .testTag("entry_amount_received_input"),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (pendingAmount > 0) AlertDueRed.copy(alpha = 0.1f) else SuccessPaidGreen.copy(alpha = 0.1f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 56.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                        .fillMaxWidth(),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = if (isTamil) "நிலுவைத் தொகை" else "Pending Due",
                                        fontSize = 11.sp,
                                        color = TextMutedDark,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = formatInr(pendingAmount, settings.currency),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (pendingAmount > 0) AlertDueRed else SuccessPaidGreen,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        // Total Bill Summary
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SoftSageGreen.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (isTamil) "மொத்த கட்டணம் (வேலை + கூடுதல்):" else "Total Payment (Work + Extra):", fontSize = 12.sp, color = ForestGreenHeader)
                                Text(
                                    formatInr(finalTotalAmount, settings.currency),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ForestGreenHeader
                                )
                            }
                        }
                    }
                }
            }

            // 6. Optional Expenses Toggle
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SageOutline)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onUpdateDraft(draft.copy(includeLinkedExpense = !includeLinkedExpense)) },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = DeepSageGreen)
                                Text(
                                    text = if (isTamil) "செலவு சேர்க்க (விருப்பத்தேர்வு)" else "Add Expense (Optional)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ForestGreenHeader
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (includeLinkedExpense) DeepSageGreen else SoftSageGreen
                            ) {
                                Text(
                                    text = if (includeLinkedExpense) "ON" else "OFF",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (includeLinkedExpense) Color.White else ForestGreenHeader,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        AnimatedVisibility(visible = includeLinkedExpense) {
                            Column(
                                modifier = Modifier.padding(top = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        ExposedDropdownMenuBox(
                                            expanded = expenseTypeDropdownExpanded,
                                            onExpandedChange = { expenseTypeDropdownExpanded = !expenseTypeDropdownExpanded }
                                        ) {
                                            OutlinedTextField(
                                                value = linkedExpenseType,
                                                onValueChange = {},
                                                readOnly = true,
                                                label = { Text(if (isTamil) "செலவு வகை" else "Expense Type") },
                                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expenseTypeDropdownExpanded) },
                                                supportingText = {
                                                    if (hasAttemptedReview && isLinkedExpenseInvalid) {
                                                        Text("", fontSize = 10.sp)
                                                    }
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .menuAnchor()
                                                    .trackFocusedField(expenseTypeRequester, coroutineScope),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            ExposedDropdownMenu(
                                                expanded = expenseTypeDropdownExpanded,
                                                onDismissRequest = { expenseTypeDropdownExpanded = false }
                                            ) {
                                                ExpenseTypes.forEach { type ->
                                                    DropdownMenuItem(
                                                        text = { Text(type) },
                                                        onClick = {
                                                            onUpdateDraft(draft.copy(linkedExpenseType = type))
                                                            expenseTypeDropdownExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    OutlinedTextField(
                                        value = linkedExpenseAmountInput,
                                        onValueChange = { onUpdateDraft(draft.copy(linkedExpenseAmountInput = it)) },
                                        label = { Text(if (isTamil) "தொகை (${settings.currency}) *" else "Amount (${settings.currency}) *") },
                                        singleLine = true,
                                        isError = hasAttemptedReview && isLinkedExpenseInvalid,
                                        supportingText = {
                                            if (hasAttemptedReview && isLinkedExpenseInvalid) {
                                                Text(if (isTamil) "செலவுத் தொகை தேவை" else "Amount is required", color = AlertDueRed, fontSize = 10.sp)
                                            }
                                        },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier
                                            .weight(1f)
                                            .trackFocusedField(expenseAmountRequester, coroutineScope),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }

                                OutlinedTextField(
                                    value = linkedExpenseDesc,
                                    onValueChange = { onUpdateDraft(draft.copy(linkedExpenseDesc = it)) },
                                    label = { Text(if (isTamil) "செலவு விவரம் (எ.கா. 20 லிட்டர் டீசல்)" else "Expense Description") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .trackFocusedField(expenseDescRequester, coroutineScope),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Notes
            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { onUpdateDraft(draft.copy(notes = it)) },
                    label = { Text(if (isTamil) "குறிப்புகள் (விருப்பத்தேர்வு)" else "Work Notes (Optional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .trackFocusedField(notesRequester, coroutineScope),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DeepSageGreen,
                        unfocusedBorderColor = SageOutline
                    )
                )
            }

            // 7. Review & Save Button
            item {
                Button(
                    onClick = {
                        if (effectiveIsSaving) return@Button
                        onUpdateDraft(draft.copy(hasAttemptedReview = true))
                        if (tractors.isEmpty()) {
                            Toast.makeText(
                                context,
                                if (isTamil) "முதலில் ஒரு டிராக்டரைச் சேர்க்கவும்" else "Please add a tractor before creating an entry.",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@Button
                        }
                        val isFormValid = !isTractorInvalid &&
                                !isCustomerNameInvalid &&
                                !isCustomerPhoneInvalid &&
                                !isTimeInvalid &&
                                !isWorkAmountInvalid &&
                                !isAmountReceivedInvalid &&
                                (!includeLinkedExpense || !isLinkedExpenseInvalid)

                        if (isFormValid) {
                            val entryId = if (draft.entryId > 0L) draft.entryId else com.example.data.util.IdGenerator.generateId()
                            val expenseId = if (draft.linkedExpenseId > 0L) draft.linkedExpenseId else com.example.data.util.IdGenerator.generateId()
                            onUpdateDraft(draft.copy(entryId = entryId, linkedExpenseId = expenseId, isReviewScreenVisible = true, hasAttemptedReview = true))
                        } else {
                            val errorMsg = when {
                                isTractorInvalid -> if (isTamil) "டிராக்டரைத் தேர்வுசெய்க" else "Please select a tractor"
                                isCustomerNameInvalid -> if (isTamil) "வாடிக்கையாளர் பெயர் தேவை" else "Customer name is required"
                                isCustomerPhoneInvalid -> if (isTamil) "சரியான 10 இலக்க தொலைபேசி எண்ணை உள்ளிடவும்" else "Enter a valid 10-digit mobile number"
                                isTimeInvalid -> if (isTamil) "வேலை நேரம் பூஜ்ஜியத்திற்கு மேல் இருக்க வேண்டும்" else "Work duration must be greater than 0"
                                isWorkAmountInvalid -> if (isTamil) "வேலைத் தொகை பூஜ்ஜியத்திற்கு மேல் இருக்க வேண்டும்" else "Work amount must be greater than 0"
                                isAmountReceivedInvalid -> if (isTamil) "பெறப்பட்ட தொகையை உள்ளிடவும்" else "Please enter amount received"
                                else -> if (isTamil) "தேவையான தகவல்களை நிரப்பவும்" else "Please fill all required highlighted fields"
                            }
                            Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !effectiveIsSaving,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepSageGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("btn_review_and_save")
                ) {
                    Text(
                        text = if (isTamil) "சரிபார்த்து சேமிக்கவும்" else "Review & Save",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            // 8. Clear Draft Action Button at bottom
            item {
                OutlinedButton(
                    onClick = handleClearAction,
                    enabled = !effectiveIsSaving,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertDueRed),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AlertDueRed.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("btn_clear_draft")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = AlertDueRed
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isTamil) "படிவத்தை அழிக்கவும் (Clear Form)" else "Clear Form / Reset",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = AlertDueRed
                    )
                }
            }
        }
    }

    if (showClearConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmationDialog = false },
            title = {
                Text(
                    text = if (isTamil) "புதிய பதிவை அழிக்கவா?" else "Clear New Entry?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isTamil)
                        "சேமிக்கப்படாத அனைத்து விவரங்களும் நீக்கப்படும்."
                    else
                        "All unsaved information will be removed."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmationDialog = false
                        onClearDraft()
                        Toast.makeText(
                            context,
                            if (isTamil) "படிவம் அழிக்கப்பட்டது" else "Draft cleared",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.testTag("dialog_confirm_clear")
                ) {
                    Text(
                        text = if (isTamil) "அழி" else "Clear",
                        color = AlertDueRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirmationDialog = false },
                    modifier = Modifier.testTag("dialog_cancel_clear")
                ) {
                    Text(text = if (isTamil) "ரத்து செய்" else "Cancel")
                }
            }
        )
    }
}

@Composable
fun FullReviewAndSaveScreen(
    settings: AppSettingsEntity,
    partnerName: String,
    tractorLabel: String,
    workType: String,
    customerName: String,
    customerPhone: String,
    customerLocation: String,
    durationMinutes: Long,
    hourlyRate: Double,
    workAmount: Double,
    extraCharges: Double,
    totalAmount: Double,
    amountReceived: Double,
    pendingAmount: Double,
    notes: String,
    linkedExpense: ExpenseEntity?,
    isSaving: Boolean = false,
    onBackToEdit: () -> Unit,
    onConfirmSave: () -> Unit
) {
    val isTamil = settings.language.equals("TA", ignoreCase = true)
    val responsive = com.example.ui.theme.rememberResponsiveDimensions()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background),
        contentPadding = PaddingValues(
            horizontal = responsive.screenPaddingHorizontal,
            vertical = responsive.screenPaddingVertical
        ),
        verticalArrangement = Arrangement.spacedBy(if (responsive.isSmallPhone) 10.dp else 14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = { if (!isSaving) onBackToEdit() },
                    enabled = !isSaving,
                    modifier = Modifier.testTag("btn_review_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = if (isSaving) DeepSageGreen.copy(alpha = 0.4f) else DeepSageGreen
                    )
                }

                Text(
                    text = if (isTamil) "வேலை விவரங்களை சரிபார்க்கவும்" else "Review Entry",
                    fontSize = if (responsive.isSmallPhone) 16.sp else 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBg),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.cardBorder)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(if (responsive.isSmallPhone) 12.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(if (responsive.isSmallPhone) 8.dp else 10.dp)
                ) {
                    Text(
                        text = if (isTamil) "முக்கிய விவரங்கள்" else "Entry Summary",
                        fontSize = if (responsive.isSmallPhone) 13.sp else 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeepSageGreen
                    )

                    DetailRow(
                        label = if (isTamil) "பங்குதாரர்" else "Attributed Partner",
                        value = partnerName
                    )
                    DetailRow(
                        label = if (isTamil) "டிராக்டர்" else "Tractor",
                        value = tractorLabel
                    )
                    DetailRow(
                        label = if (isTamil) "பணி / கருவி" else "Work Type",
                        value = workType
                    )

                    Divider(color = SageOutline.copy(alpha = 0.5f))

                    DetailRow(
                        label = if (isTamil) "வாடிக்கையாளர் பெயர்" else "Customer Name",
                        value = customerName
                    )
                    if (customerPhone.isNotBlank()) {
                        DetailRow(
                            label = if (isTamil) "தொலைபேசி" else "Phone Number",
                            value = customerPhone
                        )
                    }
                    if (customerLocation.isNotBlank()) {
                        DetailRow(
                            label = if (isTamil) "ஊர்" else "Location",
                            value = customerLocation
                        )
                    }

                    Divider(color = SageOutline.copy(alpha = 0.5f))

                    DetailRow(
                        label = if (isTamil) "வேலை நேரம்" else "Duration",
                        value = if (durationMinutes > 0) "${com.example.ui.util.WorkBillingCalculator.formatDuration(durationMinutes)} ($durationMinutes mins)" else "0 mins"
                    )
                    DetailRow(
                        label = if (isTamil) "மணிநேர கட்டணம்" else "Hourly Rate",
                        value = "${formatInr(hourlyRate, settings.currency)} / hr"
                    )
                    DetailRow(
                        label = if (isTamil) "வேலைத் தொகை" else "Work Amount",
                        value = formatInr(workAmount, settings.currency)
                    )
                    if (extraCharges > 0) {
                        DetailRow(
                            label = if (isTamil) "கூடுதல் கட்டணம்" else "Extra Charges",
                            value = "+ ${formatInr(extraCharges, settings.currency)}"
                        )
                    }

                    Divider(color = SageOutline.copy(alpha = 0.5f))

                    DetailRow(
                        label = if (isTamil) "மொத்த பில் தொகை" else "Total Amount",
                        value = formatInr(totalAmount, settings.currency),
                        valueColor = ForestGreenHeader,
                        isBoldValue = true
                    )
                    DetailRow(
                        label = if (isTamil) "பெற்ற தொகை" else "Amount Received",
                        value = formatInr(amountReceived, settings.currency),
                        valueColor = SuccessPaidGreen
                    )
                    DetailRow(
                        label = if (isTamil) "நிலுவைத் தொகை" else "Pending Due",
                        value = formatInr(pendingAmount, settings.currency),
                        valueColor = if (pendingAmount > 0) AlertDueRed else SuccessPaidGreen
                    )

                    if (linkedExpense != null) {
                        Divider(color = SageOutline.copy(alpha = 0.5f))
                        DetailRow(
                            label = if (isTamil) "இணைக்கப்பட்ட செலவு" else "Linked Expense",
                            value = "${linkedExpense.expenseType}: ${formatInr(linkedExpense.amount, settings.currency)}"
                        )
                    }

                    if (notes.isNotBlank()) {
                        Divider(color = SageOutline.copy(alpha = 0.5f))
                        DetailRow(
                            label = if (isTamil) "குறிப்புகள்" else "Notes",
                            value = notes,
                            isBoldValue = false
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(if (responsive.isSmallPhone) 8.dp else 12.dp)
            ) {
                OutlinedButton(
                    onClick = onBackToEdit,
                    enabled = !isSaving,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(if (responsive.isSmallPhone) 46.dp else 50.dp)
                        .testTag("btn_review_edit")
                ) {
                    Text(if (isTamil) "திருத்துக" else "Edit", fontSize = if (responsive.isSmallPhone) 13.sp else 14.sp)
                }

                Button(
                    onClick = {
                        if (isSaving) return@Button
                        onConfirmSave()
                    },
                    enabled = !isSaving,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DeepSageGreen,
                        disabledContainerColor = DeepSageGreen.copy(alpha = 0.6f),
                        disabledContentColor = Color.White.copy(alpha = 0.8f)
                    ),
                    modifier = Modifier
                        .weight(1.5f)
                        .height(if (responsive.isSmallPhone) 46.dp else 50.dp)
                        .testTag("btn_confirm_final_save")
                ) {
                    if (isSaving) {
                        androidx.compose.material3.CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isTamil) "சேமிக்கப்படுகிறது..." else "Saving...",
                            fontWeight = FontWeight.Bold,
                            fontSize = if (responsive.isSmallPhone) 13.sp else 14.sp,
                            maxLines = 1
                        )
                    } else {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isTamil) "உறுதிசெய்து சேமிக்கவும்" else "Confirm & Save",
                            fontWeight = FontWeight.Bold,
                            fontSize = if (responsive.isSmallPhone) 13.sp else 14.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
