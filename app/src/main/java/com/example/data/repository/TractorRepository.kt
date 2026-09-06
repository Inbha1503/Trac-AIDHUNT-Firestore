package com.example.data.repository

import com.example.data.database.AppDatabase
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.CustomerEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PartnerEntity
import com.example.data.entity.TractorEntity
import com.example.data.entity.WithdrawalEntity
import com.example.data.util.IdGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class TractorRepository(private val database: AppDatabase) {

    private val partnerDao = database.partnerDao()
    private val tractorDao = database.tractorDao()
    private val customerDao = database.customerDao()
    private val jobEntryDao = database.jobEntryDao()
    private val expenseDao = database.expenseDao()
    private val withdrawalDao = database.withdrawalDao()
    private val appSettingsDao = database.appSettingsDao()

    // 1. App Settings
    val settingsFlow: Flow<AppSettingsEntity?> = appSettingsDao.getSettings()

    suspend fun getSettings(): AppSettingsEntity {
        return appSettingsDao.getSettingsOnce() ?: AppSettingsEntity()
    }

    suspend fun updateSettings(settings: AppSettingsEntity) {
        appSettingsDao.insertOrUpdateSettings(settings)
    }

    suspend fun setActivePartner(partnerName: String, partnerPhone: String) {
        val current = getSettings()
        appSettingsDao.insertOrUpdateSettings(
            current.copy(
                activePartnerName = partnerName,
                activePartnerPhone = partnerPhone
            )
        )
    }

    // 2. Partners
    val allPartners: Flow<List<PartnerEntity>> = partnerDao.getAllPartners()

    suspend fun addPartner(partner: PartnerEntity): Long {
        val safeId = if (partner.id > 0) partner.id else IdGenerator.generateId()
        val toInsert = partner.copy(id = safeId)
        partnerDao.insertPartner(toInsert)
        return safeId
    }

    suspend fun updatePartner(partner: PartnerEntity) = partnerDao.updatePartner(partner)

    suspend fun deletePartner(partner: PartnerEntity) = partnerDao.deletePartner(partner)

    // 3. Tractors
    val allTractors: Flow<List<TractorEntity>> = tractorDao.getAllTractors()

    suspend fun addTractor(tractor: TractorEntity): Long {
        val safeId = if (tractor.id > 0) tractor.id else IdGenerator.generateId()
        val toInsert = tractor.copy(id = safeId)
        tractorDao.insertTractor(toInsert)
        return safeId
    }

    suspend fun updateTractor(tractor: TractorEntity) = tractorDao.updateTractor(tractor)

    suspend fun deleteTractor(tractor: TractorEntity) = tractorDao.deleteTractor(tractor)

    // 4. Customers
    val allCustomers: Flow<List<CustomerEntity>> = customerDao.getAllCustomers()
    val customersWithDue: Flow<List<CustomerEntity>> = customerDao.getCustomersWithDue()

    fun searchCustomers(query: String): Flow<List<CustomerEntity>> = customerDao.searchCustomers(query)

    suspend fun getCustomerById(id: Long): CustomerEntity? = customerDao.getCustomerById(id)

    suspend fun updateCustomer(customer: CustomerEntity) {
        val sanitized = customer.copy(
            phone = com.example.ui.components.sanitizePhoneNumberForStorage(customer.phone)
        )
        customerDao.updateCustomer(sanitized)
    }

    suspend fun deleteCustomer(customer: CustomerEntity) = customerDao.deleteCustomer(customer)

    suspend fun addOrFindCustomer(name: String, phone: String, location: String): Long {
        val customers = customerDao.getAllCustomers().firstOrNull() ?: emptyList()
        val existing = customers.find { it.name.trim().equals(name.trim(), ignoreCase = true) }
        val cleanPhone = com.example.ui.components.sanitizePhoneNumberForStorage(phone)
        val cleanLocation = location.trim()

        return if (existing != null) {
            // If existing customer didn't have a phone or location, update with new one
            if (cleanPhone.isNotBlank() && (existing.phone.isBlank() || existing.phone != cleanPhone)) {
                customerDao.updateCustomer(
                    existing.copy(
                        phone = cleanPhone,
                        location = if (cleanLocation.isNotBlank()) cleanLocation else existing.location,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            existing.id
        } else {
            val safeId = IdGenerator.generateId()
            customerDao.insertCustomer(
                CustomerEntity(
                    id = safeId,
                    name = name.trim(),
                    phone = cleanPhone,
                    location = cleanLocation,
                    totalBilled = 0.0,
                    totalPaid = 0.0,
                    balanceDue = 0.0
                )
            )
            safeId
        }
    }

    // 5. Job Entries
    val allJobs: Flow<List<JobEntryEntity>> = jobEntryDao.getAllJobs()
    val totalReceived: Flow<Double?> = jobEntryDao.getTotalReceived()
    val totalPending: Flow<Double?> = jobEntryDao.getTotalPending()

    fun getJobsForCustomer(customerId: Long): Flow<List<JobEntryEntity>> =
        jobEntryDao.getJobsForCustomer(customerId)

    suspend fun saveJobEntry(
        job: JobEntryEntity,
        linkedExpense: ExpenseEntity? = null
    ): Long {
        var customerId = job.customerId
        if (customerId <= 0) {
            customerId = addOrFindCustomer(job.customerName, job.customerPhone, job.customerLocation)
        }

        val safeJobId = if (job.id > 0) job.id else IdGenerator.generateId()
        val localJob = job.copy(id = safeJobId, customerId = customerId, createdAt = System.currentTimeMillis())
        jobEntryDao.insertJob(localJob)

        // If optional linked expense was provided, add it
        if (linkedExpense != null && linkedExpense.amount > 0) {
            val safeExpId = if (linkedExpense.id > 0) linkedExpense.id else IdGenerator.generateId()
            expenseDao.insertExpense(
                linkedExpense.copy(id = safeExpId, relatedJobId = safeJobId)
            )
        }

        // Recalculate customer statistics
        recalculateCustomerStats(customerId)

        return safeJobId
    }

    suspend fun deleteJob(job: JobEntryEntity) {
        jobEntryDao.deleteJob(job)
        recalculateCustomerStats(job.customerId)
    }

    suspend fun recordCustomerPayment(
        customer: CustomerEntity,
        amount: Double,
        dateTimestamp: Long,
        paymentMethod: String,
        note: String,
        operatorName: String
    ): Long {
        // 1. Validate amount against outstanding due
        val currentBalanceDue = customer.balanceDue
        val displayDue = String.format(java.util.Locale.US, "%.0f", currentBalanceDue).toDoubleOrNull() ?: currentBalanceDue
        if (amount > displayDue) {
            throw IllegalArgumentException("Payment amount $amount cannot be greater than outstanding due $displayDue")
        }

        // 2. Fetch all jobs for the customer
        val jobs = jobEntryDao.getJobsForCustomer(customer.id).firstOrNull() ?: emptyList()

        // 3. Find job entries with pending dues and sort by startTimeMillis (oldest first)
        val unpaidJobs = jobs.filter { it.tractorLabel != "Payment" && it.pendingAmount > 0.0 }
                            .sortedBy { it.startTimeMillis }

        var remainingPayment = amount
        val updatedJobsList = mutableListOf<JobEntryEntity>()

        for (job in unpaidJobs) {
            if (remainingPayment <= 0.0) break

            val currentPending = job.pendingAmount
            val allocation = minOf(remainingPayment, currentPending)

            val newAmountReceived = job.amountReceived + allocation
            val newPendingAmount = job.totalAmount - newAmountReceived

            // Combine note with existing notes
            val notePart = if (note.isNotBlank()) "Payment Note: $note" else ""
            val methodPart = if (paymentMethod.isNotBlank()) "Payment Method: $paymentMethod" else ""
            val paymentNotes = listOf(methodPart, notePart).filter { it.isNotBlank() }.joinToString(" • ")

            val updatedNotes = if (paymentNotes.isNotBlank()) {
                if (job.notes.isNotBlank()) "${job.notes} • $paymentNotes" else paymentNotes
            } else {
                job.notes
            }

            val updatedJob = job.copy(
                amountReceived = newAmountReceived,
                pendingAmount = newPendingAmount,
                notes = updatedNotes,
                createdAt = System.currentTimeMillis()
            )
            updatedJobsList.add(updatedJob)
            remainingPayment -= allocation
        }

        // 4. Update the jobs in database
        for (updatedJob in updatedJobsList) {
            jobEntryDao.insertJob(updatedJob)
        }

        // 5. If there is still remainingPayment, create a payment record (fallback)
        var fallbackPaymentId: Long? = null
        if (remainingPayment > 0.0) {
            val methodDesc = if (paymentMethod.isNotBlank()) "Payment Method: $paymentMethod" else ""
            val noteDesc = if (note.isNotBlank()) "Note: $note" else ""
            val combinedNotes = listOf(methodDesc, noteDesc).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { "Direct Payment Received" }

            val safeEntryId = IdGenerator.generateId()
            val paymentEntry = JobEntryEntity(
                id = safeEntryId,
                customerId = customer.id,
                customerName = customer.name,
                customerPhone = customer.phone,
                customerLocation = customer.location,
                operatorName = operatorName.ifBlank { "Partner" },
                tractorId = 0,
                tractorLabel = "Payment",
                workType = "Payment Received",
                startTimeMillis = dateTimestamp,
                endTimeMillis = dateTimestamp,
                durationMinutes = 0,
                hourlyRate = 0.0,
                totalAmount = 0.0,
                amountReceived = remainingPayment,
                pendingAmount = -remainingPayment,
                addedByPartner = operatorName.ifBlank { "Partner" },
                notes = combinedNotes
            )
            jobEntryDao.insertJob(paymentEntry)
            fallbackPaymentId = safeEntryId
        }

        recalculateCustomerStats(customer.id)
        return fallbackPaymentId ?: (updatedJobsList.lastOrNull()?.id ?: 0L)
    }

    private suspend fun recalculateCustomerStats(customerId: Long) {
        val customer = customerDao.getCustomerById(customerId) ?: return
        val jobs = jobEntryDao.getJobsForCustomer(customerId).firstOrNull() ?: emptyList()

        val totalBilled = jobs.sumOf { it.totalAmount }
        val totalPaid = jobs.sumOf { it.amountReceived }
        val balanceDue = jobs.sumOf { it.pendingAmount }

        customerDao.updateCustomer(
            customer.copy(
                totalBilled = totalBilled,
                totalPaid = totalPaid,
                balanceDue = balanceDue,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    // 6. Expenses
    val allExpenses: Flow<List<ExpenseEntity>> = expenseDao.getAllExpenses()
    val totalExpenses: Flow<Double?> = expenseDao.getTotalExpenses()

    suspend fun addExpense(expense: ExpenseEntity): Long {
        val safeId = if (expense.id > 0) expense.id else IdGenerator.generateId()
        val toInsert = expense.copy(id = safeId)
        expenseDao.insertExpense(toInsert)
        return safeId
    }

    suspend fun updateExpense(expense: ExpenseEntity) = expenseDao.updateExpense(expense)

    suspend fun deleteExpense(expense: ExpenseEntity) = expenseDao.deleteExpense(expense)

    // 7. Withdrawals
    val allWithdrawals: Flow<List<WithdrawalEntity>> = withdrawalDao.getAllWithdrawals()
    val totalWithdrawn: Flow<Double?> = withdrawalDao.getTotalWithdrawn()

    suspend fun addWithdrawal(withdrawal: WithdrawalEntity): Long {
        val safeId = if (withdrawal.id > 0) withdrawal.id else IdGenerator.generateId()
        val toInsert = withdrawal.copy(id = safeId)
        withdrawalDao.insertWithdrawal(toInsert)
        return safeId
    }

    suspend fun updateWithdrawal(withdrawal: WithdrawalEntity) =
        withdrawalDao.updateWithdrawal(withdrawal)

    suspend fun deleteWithdrawal(withdrawal: WithdrawalEntity) =
        withdrawalDao.deleteWithdrawal(withdrawal)

    // 8. Offline & Cloud Sync
    val unsyncedJobsCount: Flow<Int> = jobEntryDao.getUnsyncedCount()
    val unsyncedExpensesCount: Flow<Int> = expenseDao.getUnsyncedCount()
    val unsyncedWithdrawalsCount: Flow<Int> = withdrawalDao.getUnsyncedCount()
    val unsyncedCustomersCount: Flow<Int> = customerDao.getUnsyncedCount()

    val totalUnsyncedCount: Flow<Int> = kotlinx.coroutines.flow.combine(
        unsyncedJobsCount,
        unsyncedExpensesCount,
        unsyncedWithdrawalsCount,
        unsyncedCustomersCount
    ) { jobs, exp, wth, cust ->
        jobs + exp + wth + cust
    }

    suspend fun pushUnsyncedToCloud(isOnline: Boolean): SyncResult {
        if (!isOnline) {
            return SyncResult(
                isSuccess = false,
                syncedItemsCount = 0,
                message = "Device offline. Stored safely in local Room SQLite database."
            )
        }

        val unsyncedJobs = jobEntryDao.getUnsyncedJobs()
        val unsyncedExpenses = expenseDao.getUnsyncedExpenses()
        val unsyncedWithdrawals = withdrawalDao.getUnsyncedWithdrawals()
        val unsyncedCustomers = customerDao.getUnsyncedCustomers()

        val totalCount = unsyncedJobs.size + unsyncedExpenses.size + unsyncedWithdrawals.size + unsyncedCustomers.size

        if (totalCount == 0) {
            return SyncResult(
                isSuccess = true,
                syncedItemsCount = 0,
                message = "All records are already in sync with Cloud."
            )
        }

        return SyncResult(
            isSuccess = false,
            syncedItemsCount = 0,
            message = "$totalCount local records pending Cloud upload."
        )
    }

    suspend fun triggerSync() {
        val current = getSettings()
        appSettingsDao.insertOrUpdateSettings(
            current.copy(lastSyncTime = System.currentTimeMillis())
        )
    }
}

data class SyncResult(
    val isSuccess: Boolean,
    val syncedItemsCount: Int,
    val message: String
)
