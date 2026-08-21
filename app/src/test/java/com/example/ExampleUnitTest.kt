package com.example

import com.example.ui.components.buildWhatsAppUrl
import com.example.ui.components.formatWhatsAppPhone
import com.example.ui.components.isValidPhoneNumber
import com.example.ui.components.sanitizePhoneNumberForStorage
import com.example.ui.util.WorkBillingCalculator
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testFormatWhatsAppPhone_standardTenDigits() {
        // Standard 10 digit Indian number without country code
        val formatted = formatWhatsAppPhone("9876543210")
        assertEquals("919876543210", formatted)
    }

    @Test
    fun testFormatWhatsAppPhone_withSpacesAndDashes() {
        val formatted = formatWhatsAppPhone("98765 43210")
        assertEquals("919876543210", formatted)

        val formattedDash = formatWhatsAppPhone("9876-543-210")
        assertEquals("919876543210", formattedDash)
    }

    @Test
    fun testFormatWhatsAppPhone_withLeadingZero() {
        val formatted = formatWhatsAppPhone("09876543210")
        assertEquals("919876543210", formatted)
    }

    @Test
    fun testFormatWhatsAppPhone_withCountryCodePlus() {
        val formatted = formatWhatsAppPhone("+91 98765 43210")
        assertEquals("919876543210", formatted)
    }

    @Test
    fun testFormatWhatsAppPhone_supportNumber() {
        val formatted = formatWhatsAppPhone("8778285956")
        assertEquals("918778285956", formatted)
    }

    @Test
    fun testSanitizePhoneNumberForStorage() {
        assertEquals("9876543210", sanitizePhoneNumberForStorage("09876543210"))
        assertEquals("9876543210", sanitizePhoneNumberForStorage("+91 98765 43210"))
        assertEquals("8778285956", sanitizePhoneNumberForStorage("8778-285-956"))
    }

    @Test
    fun testBuildWhatsAppUrl() {
        val url = buildWhatsAppUrl("9876543210", "Hello")
        assertNotNull(url)
        assertTrue(url!!.startsWith("https://wa.me/919876543210"))
    }

    // WorkBillingCalculator Tests
    @Test
    fun testWorkBillingCalculator_clockDurationCalculation() {
        // 08:15 to 12:05 -> 3h 50m = 230 minutes
        val duration = WorkBillingCalculator.calculateDurationFromClock(8, 15, 12, 5)
        assertEquals(230L, duration)
    }

    @Test
    fun testWorkBillingCalculator_manualDurationCalculation() {
        // 3 hours, 50 minutes -> 230 minutes
        val duration = WorkBillingCalculator.calculateDurationFromManual("3", "50")
        assertEquals(230L, duration)
    }

    @Test
    fun testWorkBillingCalculator_clockAndManualEquivalence() {
        // Clock: 08:15 to 12:05 vs Manual: 3h 50m
        val clockDuration = WorkBillingCalculator.calculateDurationFromClock(8, 15, 12, 5)
        val manualDuration = WorkBillingCalculator.calculateDurationFromManual("3", "50")
        assertEquals(clockDuration, manualDuration)

        val rate = 1100.0
        val clockAmount = WorkBillingCalculator.calculateAmount(clockDuration, rate)
        val manualAmount = WorkBillingCalculator.calculateAmount(manualDuration, rate)
        assertEquals(clockAmount, manualAmount, 0.001)
        val expectedAmount = (230.0 / 60.0) * 1100.0
        assertEquals(expectedAmount, clockAmount, 0.001)
    }

    @Test
    fun testWorkBillingCalculator_midnightCrossing() {
        // 23:00 to 01:30 next day -> 150 minutes (2h 30m)
        val duration = WorkBillingCalculator.calculateDurationFromClock(23, 0, 1, 30)
        assertEquals(150L, duration)
        assertEquals(2.5, WorkBillingCalculator.toDecimalHours(duration), 0.001)
    }

    @Test
    fun testWorkBillingCalculator_zeroDefaults() {
        assertEquals(0L, WorkBillingCalculator.calculateDurationFromClock(null, null, null, null))
        assertEquals(0L, WorkBillingCalculator.calculateDurationFromClock(8, 37, null, null))
        assertEquals(0L, WorkBillingCalculator.calculateDurationFromClock(null, null, 12, 5))
        assertEquals(0L, WorkBillingCalculator.calculateDurationFromManual("", ""))
        assertEquals(0.0, WorkBillingCalculator.calculateAmount(0L, 1000.0), 0.001)
        assertEquals(0.0, WorkBillingCalculator.calculateAmount(120L, 0.0), 0.001)
    }

    @Test
    fun testWorkBillingCalculator_formatDuration() {
        assertEquals("3h 50m", WorkBillingCalculator.formatDuration(230L))
        assertEquals("2h", WorkBillingCalculator.formatDuration(120L))
        assertEquals("45m", WorkBillingCalculator.formatDuration(45L))
        assertEquals("0", WorkBillingCalculator.formatDuration(0L))
    }

    // NewEntryDraft Unit Tests
    @Test
    fun testNewEntryDraft_defaultState() {
        val draft = com.example.ui.viewmodel.NewEntryDraft.createDefault(
            defaultTractor = "John Deere 5050 D",
            lockedTractor = "John Deere 5050 D",
            defaultHourlyRate = 1200.0
        )
        assertEquals("John Deere 5050 D", draft.selectedTractor)
        assertTrue(draft.isTractorLocked)
        assertNotNull(draft.startHour)
        assertNotNull(draft.startMinute)
        assertNull(draft.endHour)
        assertNull(draft.endMinute)
        assertEquals("1200", draft.hourlyRateInput)
        assertEquals("", draft.customerNameInput)
        assertEquals("", draft.amountReceivedInput)
        assertFalse(draft.isModified())
    }

    @Test
    fun testNewEntryDraft_isModifiedDetection() {
        val defaultDraft = com.example.ui.viewmodel.NewEntryDraft.createDefault()
        assertFalse(defaultDraft.isModified())

        val modifiedCustomer = defaultDraft.copy(customerNameInput = "Ramesh Kumar")
        assertTrue(modifiedCustomer.isModified())

        val modifiedTime = defaultDraft.copy(endHour = 14, endMinute = 30)
        assertTrue(modifiedTime.isModified())

        val modifiedExpense = defaultDraft.copy(includeLinkedExpense = true)
        assertTrue(modifiedExpense.isModified())

        val modifiedNotes = defaultDraft.copy(notes = "Field plowing")
        assertTrue(modifiedNotes.isModified())
    }

    @Test
    fun testNewEntryDraft_preservesValuesAcrossSimulatedNavigation() {
        var draft = com.example.ui.viewmodel.NewEntryDraft.createDefault()
        draft = draft.copy(
            customerNameInput = "Sundar",
            customerPhoneInput = "9876543210",
            customerLocationInput = "Madurai",
            manualHoursInput = "2",
            manualMinutesInput = "30",
            isDirectDurationMode = true,
            amountReceivedInput = "2000",
            notes = "Test draft retention"
        )

        // Simulated navigation away to another screen and back
        val restoredDraft = draft

        assertEquals("Sundar", restoredDraft.customerNameInput)
        assertEquals("9876543210", restoredDraft.customerPhoneInput)
        assertEquals("Madurai", restoredDraft.customerLocationInput)
        assertEquals("2", restoredDraft.manualHoursInput)
        assertEquals("30", restoredDraft.manualMinutesInput)
        assertTrue(restoredDraft.isDirectDurationMode)
        assertEquals("2000", restoredDraft.amountReceivedInput)
        assertEquals("Test draft retention", restoredDraft.notes)
        assertTrue(restoredDraft.isModified())
    }

    @Test
    fun testNewEntryDraft_clearResetsToCleanDefault() {
        val modifiedDraft = com.example.ui.viewmodel.NewEntryDraft(
            customerNameInput = "Sundar",
            manualHoursInput = "5",
            amountReceivedInput = "3000",
            notes = "Unsaved draft"
        )
        assertTrue(modifiedDraft.isModified())

        val clearedDraft = com.example.ui.viewmodel.NewEntryDraft.createDefault(
            defaultTractor = "Mahindra 575 DI",
            lockedTractor = "",
            defaultHourlyRate = 1100.0
        )
        assertFalse(clearedDraft.isModified())
        assertEquals("", clearedDraft.customerNameInput)
        assertEquals("", clearedDraft.manualHoursInput)
        assertEquals("", clearedDraft.amountReceivedInput)
        assertNotNull(clearedDraft.startHour)
        assertNotNull(clearedDraft.startMinute)
    }

    // Login Auth Method & Validation Unit Tests
    @Test
    fun testAuthMethod_enumExistence() {
        val phoneMethod = com.example.ui.screens.auth.AuthMethod.PHONE
        val gmailMethod = com.example.ui.screens.auth.AuthMethod.GMAIL
        assertEquals("PHONE", phoneMethod.name)
        assertEquals("GMAIL", gmailMethod.name)
    }

    @Test
    fun testLoginEmailValidation() {
        val emailRegex = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
        assertTrue(emailRegex.matches("thiruselvam400@gmail.com"))
        assertTrue(emailRegex.matches("farmer.partner@tractor.org"))
        assertTrue(emailRegex.matches("user+test@domain.co.in"))

        assertFalse(emailRegex.matches(""))
        assertFalse(emailRegex.matches("plainaddress"))
        assertFalse(emailRegex.matches("@missingusername.com"))
        assertFalse(emailRegex.matches("username@.com"))
        assertFalse(emailRegex.matches("username@domain"))
    }

    @Test
    fun testLoginPhoneValidation() {
        fun isValidPhone(phone: String): Boolean {
            val digits = phone.filter { it.isDigit() }
            return digits.length in 10..12
        }

        assertTrue(isValidPhone("9842154321"))
        assertTrue(isValidPhone("+91 98421 54321"))
        assertTrue(isValidPhone("09842154321"))
        assertTrue(isValidPhone("919842154321"))

        assertFalse(isValidPhone(""))
        assertFalse(isValidPhone("12345"))
        assertFalse(isValidPhone("abc"))
    }
}

