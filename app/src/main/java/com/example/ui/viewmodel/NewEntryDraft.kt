package com.example.ui.viewmodel

import java.util.Calendar

data class FixedWorkType(val en: String, val ta: String)

val FIXED_WORK_TYPES = listOf(
    FixedWorkType("Rotavator", "ரோட்டாவேட்டர்"),
    FixedWorkType("5 Claws", "5 கலப்பை"),
    FixedWorkType("9 Claws", "9 கலப்பை"),
    FixedWorkType("Loader", "லோடர்")
)

/**
 * Authoritative single source of truth for the New Work Entry draft.
 * Preserves user inputs across in-app navigation (Home, Reports, Account, etc.)
 * until explicitly cleared by the user or after a successful save.
 */
data class NewEntryDraft(
    val isReviewScreenVisible: Boolean = false,
    val selectedTractor: String = "",
    val isTractorLocked: Boolean = false,
    val selectedWorkType: String = "Rotavator",
    val customerNameInput: String = "",
    val customerPhoneInput: String = "",
    val customerLocationInput: String = "",
    val matchedCustomerId: Long = 0L,
    val isDirectDurationMode: Boolean = false,
    val manualHoursInput: String = "",
    val manualMinutesInput: String = "",
    val startHour: Int? = null,
    val startMinute: Int? = null,
    val endHour: Int? = null,
    val endMinute: Int? = null,
    val hourlyRateInput: String = "",
    val customWorkAmountInput: String = "",
    val extraChargesInput: String = "",
    val amountReceivedInput: String = "",
    val includeLinkedExpense: Boolean = false,
    val linkedExpenseType: String = "Diesel",
    val linkedExpenseAmountInput: String = "",
    val linkedExpenseDesc: String = "",
    val notes: String = "",
    val hasAttemptedReview: Boolean = false
) {
    /**
     * Determines whether the user has entered any non-default data into the form.
     * Used to decide whether to prompt the user with a confirmation dialog before clearing.
     */
    fun isModified(): Boolean {
        return customerNameInput.isNotBlank() ||
                customerPhoneInput.isNotBlank() ||
                customerLocationInput.isNotBlank() ||
                manualHoursInput.isNotBlank() ||
                manualMinutesInput.isNotBlank() ||
                endHour != null ||
                endMinute != null ||
                customWorkAmountInput.isNotBlank() ||
                extraChargesInput.isNotBlank() ||
                amountReceivedInput.isNotBlank() ||
                includeLinkedExpense ||
                linkedExpenseAmountInput.isNotBlank() ||
                linkedExpenseDesc.isNotBlank() ||
                notes.isNotBlank() ||
                isReviewScreenVisible
    }

    companion object {
        fun createDefault(
            defaultTractor: String = "Mahindra 575 DI",
            lockedTractor: String = "",
            defaultHourlyRate: Double = 0.0
        ): NewEntryDraft {
            val now = Calendar.getInstance()
            val tractor = if (lockedTractor.isNotBlank()) lockedTractor else defaultTractor
            val rateStr = if (defaultHourlyRate > 0.0) defaultHourlyRate.toInt().toString() else ""
            return NewEntryDraft(
                selectedTractor = tractor,
                isTractorLocked = lockedTractor.isNotBlank(),
                startHour = now.get(Calendar.HOUR_OF_DAY),
                startMinute = now.get(Calendar.MINUTE),
                hourlyRateInput = rateStr
            )
        }
    }
}
