package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.CustomerEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PartnerEntity
import com.example.data.entity.TractorEntity
import com.example.data.entity.WithdrawalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PartnerDao {
    @Query("SELECT * FROM partners WHERE workspaceId = :workspaceId ORDER BY id ASC")
    fun getPartnersForWorkspace(workspaceId: String): Flow<List<PartnerEntity>>

    @Query("SELECT * FROM partners WHERE workspaceId IN (:workspaceIds) ORDER BY id ASC")
    fun getPartnersForWorkspaces(workspaceIds: List<String>): Flow<List<PartnerEntity>>

    @Query("SELECT * FROM partners WHERE workspaceId = :workspaceId ORDER BY id ASC")
    suspend fun getPartnersForWorkspaceOnce(workspaceId: String): List<PartnerEntity>

    @Query("SELECT * FROM partners WHERE workspaceId IN (:workspaceIds) ORDER BY id ASC")
    suspend fun getPartnersForWorkspacesOnce(workspaceIds: List<String>): List<PartnerEntity>

    @Query("SELECT * FROM partners ORDER BY id ASC")
    fun getAllPartners(): Flow<List<PartnerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPartner(partner: PartnerEntity): Long

    @Update
    suspend fun updatePartner(partner: PartnerEntity)

    @Delete
    suspend fun deletePartner(partner: PartnerEntity)

    @Query("DELETE FROM partners WHERE workspaceId = :workspaceId AND id NOT IN (:validIds)")
    suspend fun deleteNotIn(workspaceId: String, validIds: List<Long>)

    @Query("DELETE FROM partners WHERE id NOT IN (:validIds)")
    suspend fun deleteNotIn(validIds: List<Long>)

    @Query("DELETE FROM partners WHERE workspaceId = :workspaceId")
    suspend fun deleteAllForWorkspace(workspaceId: String)

    @Query("DELETE FROM partners WHERE workspaceId IN (:workspaceIds)")
    suspend fun deleteAllForWorkspaces(workspaceIds: List<String>)

    @Query("DELETE FROM partners")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM partners WHERE workspaceId = :workspaceId")
    suspend fun getCount(workspaceId: String): Int

    @Query("SELECT COUNT(*) FROM partners WHERE workspaceId IN (:workspaceIds)")
    suspend fun getCount(workspaceIds: List<String>): Int

    @Query("SELECT COUNT(*) FROM partners")
    suspend fun getCount(): Int
}

@Dao
interface TractorDao {
    @Query("SELECT * FROM tractors WHERE workspaceId = :workspaceId ORDER BY id ASC")
    fun getTractorsForWorkspace(workspaceId: String): Flow<List<TractorEntity>>

    @Query("SELECT * FROM tractors WHERE workspaceId IN (:workspaceIds) ORDER BY id ASC")
    fun getTractorsForWorkspaces(workspaceIds: List<String>): Flow<List<TractorEntity>>

    @Query("SELECT * FROM tractors WHERE workspaceId = :workspaceId ORDER BY id ASC")
    suspend fun getTractorsForWorkspaceOnce(workspaceId: String): List<TractorEntity>

    @Query("SELECT * FROM tractors WHERE workspaceId IN (:workspaceIds) ORDER BY id ASC")
    suspend fun getTractorsForWorkspacesOnce(workspaceIds: List<String>): List<TractorEntity>

    @Query("SELECT * FROM tractors ORDER BY id ASC")
    fun getAllTractors(): Flow<List<TractorEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTractor(tractor: TractorEntity): Long

    @Update
    suspend fun updateTractor(tractor: TractorEntity)

    @Delete
    suspend fun deleteTractor(tractor: TractorEntity)

    @Query("DELETE FROM tractors WHERE workspaceId = :workspaceId AND id NOT IN (:validIds)")
    suspend fun deleteNotIn(workspaceId: String, validIds: List<Long>)

    @Query("DELETE FROM tractors WHERE id NOT IN (:validIds)")
    suspend fun deleteNotIn(validIds: List<Long>)

    @Query("DELETE FROM tractors WHERE workspaceId = :workspaceId")
    suspend fun deleteAllForWorkspace(workspaceId: String)

    @Query("DELETE FROM tractors WHERE workspaceId IN (:workspaceIds)")
    suspend fun deleteAllForWorkspaces(workspaceIds: List<String>)

    @Query("DELETE FROM tractors")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM tractors WHERE workspaceId = :workspaceId")
    suspend fun getCount(workspaceId: String): Int

    @Query("SELECT COUNT(*) FROM tractors WHERE workspaceId IN (:workspaceIds)")
    suspend fun getCount(workspaceIds: List<String>): Int

    @Query("SELECT COUNT(*) FROM tractors")
    suspend fun getCount(): Int
}

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers WHERE workspaceId = :workspaceId ORDER BY updatedAt DESC")
    fun getCustomersForWorkspace(workspaceId: String): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE workspaceId IN (:workspaceIds) ORDER BY updatedAt DESC")
    fun getCustomersForWorkspaces(workspaceIds: List<String>): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE workspaceId = :workspaceId ORDER BY updatedAt DESC")
    suspend fun getCustomersForWorkspaceOnce(workspaceId: String): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE workspaceId IN (:workspaceIds) ORDER BY updatedAt DESC")
    suspend fun getCustomersForWorkspacesOnce(workspaceIds: List<String>): List<CustomerEntity>

    @Query("SELECT * FROM customers ORDER BY updatedAt DESC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE workspaceId = :workspaceId AND balanceDue > 0 ORDER BY balanceDue DESC")
    fun getCustomersWithDueForWorkspace(workspaceId: String): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE workspaceId IN (:workspaceIds) AND balanceDue > 0 ORDER BY balanceDue DESC")
    fun getCustomersWithDueForWorkspaces(workspaceIds: List<String>): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE balanceDue > 0 ORDER BY balanceDue DESC")
    fun getCustomersWithDue(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE workspaceId = :workspaceId AND id = :id LIMIT 1")
    suspend fun getCustomerById(workspaceId: String, id: Long): CustomerEntity?

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getCustomerById(id: Long): CustomerEntity?

    @Query("SELECT * FROM customers WHERE workspaceId = :workspaceId AND (LOWER(name) LIKE '%' || LOWER(:query) || '%' OR phone LIKE '%' || :query || '%')")
    fun searchCustomers(workspaceId: String, query: String): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE workspaceId IN (:workspaceIds) AND (LOWER(name) LIKE '%' || LOWER(:query) || '%' OR phone LIKE '%' || :query || '%')")
    fun searchCustomers(workspaceIds: List<String>, query: String): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE LOWER(name) LIKE '%' || LOWER(:query) || '%' OR phone LIKE '%' || :query || '%'")
    fun searchCustomers(query: String): Flow<List<CustomerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity): Long

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    @Delete
    suspend fun deleteCustomer(customer: CustomerEntity)

    @Query("DELETE FROM customers WHERE workspaceId = :workspaceId AND isSynced = 1 AND id NOT IN (:validIds)")
    suspend fun deleteSyncedNotIn(workspaceId: String, validIds: List<Long>)

    @Query("DELETE FROM customers WHERE isSynced = 1 AND id NOT IN (:validIds)")
    suspend fun deleteSyncedNotIn(validIds: List<Long>)

    @Query("DELETE FROM customers WHERE workspaceId = :workspaceId AND isSynced = 1")
    suspend fun deleteAllSyncedForWorkspace(workspaceId: String)

    @Query("DELETE FROM customers WHERE isSynced = 1")
    suspend fun deleteAllSynced()

    @Query("SELECT * FROM customers WHERE workspaceId = :workspaceId AND isSynced = 0")
    suspend fun getUnsyncedCustomersForWorkspace(workspaceId: String): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE isSynced = 0")
    suspend fun getUnsyncedCustomers(): List<CustomerEntity>

    @Query("UPDATE customers SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markCustomersSynced(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM customers WHERE workspaceId = :workspaceId AND isSynced = 0")
    fun getUnsyncedCountForWorkspace(workspaceId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM customers WHERE isSynced = 0")
    fun getUnsyncedCount(): Flow<Int>
}

@Dao
interface JobEntryDao {
    @Query("SELECT * FROM job_entries WHERE workspaceId = :workspaceId ORDER BY startTimeMillis DESC")
    fun getJobsForWorkspace(workspaceId: String): Flow<List<JobEntryEntity>>

    @Query("SELECT * FROM job_entries WHERE workspaceId IN (:workspaceIds) ORDER BY startTimeMillis DESC")
    fun getJobsForWorkspaces(workspaceIds: List<String>): Flow<List<JobEntryEntity>>

    @Query("SELECT * FROM job_entries WHERE workspaceId = :workspaceId ORDER BY startTimeMillis DESC")
    suspend fun getJobsForWorkspaceOnce(workspaceId: String): List<JobEntryEntity>

    @Query("SELECT * FROM job_entries WHERE workspaceId IN (:workspaceIds) ORDER BY startTimeMillis DESC")
    suspend fun getJobsForWorkspacesOnce(workspaceIds: List<String>): List<JobEntryEntity>

    @Query("SELECT * FROM job_entries ORDER BY startTimeMillis DESC")
    fun getAllJobs(): Flow<List<JobEntryEntity>>

    @Query("SELECT * FROM job_entries WHERE workspaceId = :workspaceId AND customerId = :customerId ORDER BY startTimeMillis DESC")
    fun getJobsForCustomer(workspaceId: String, customerId: Long): Flow<List<JobEntryEntity>>

    @Query("SELECT * FROM job_entries WHERE workspaceId IN (:workspaceIds) AND customerId = :customerId ORDER BY startTimeMillis DESC")
    fun getJobsForCustomer(workspaceIds: List<String>, customerId: Long): Flow<List<JobEntryEntity>>

    @Query("SELECT * FROM job_entries WHERE customerId = :customerId ORDER BY startTimeMillis DESC")
    fun getJobsForCustomer(customerId: Long): Flow<List<JobEntryEntity>>

    @Query("SELECT * FROM job_entries WHERE workspaceId = :workspaceId AND (LOWER(customerName) LIKE '%' || LOWER(:query) || '%' OR LOWER(operatorName) LIKE '%' || LOWER(:query) || '%' OR LOWER(tractorLabel) LIKE '%' || LOWER(:query) || '%')")
    fun searchJobs(workspaceId: String, query: String): Flow<List<JobEntryEntity>>

    @Query("SELECT * FROM job_entries WHERE workspaceId IN (:workspaceIds) AND (LOWER(customerName) LIKE '%' || LOWER(:query) || '%' OR LOWER(operatorName) LIKE '%' || LOWER(:query) || '%' OR LOWER(tractorLabel) LIKE '%' || LOWER(:query) || '%')")
    fun searchJobsForWorkspaces(workspaceIds: List<String>, query: String): Flow<List<JobEntryEntity>>

    @Query("SELECT * FROM job_entries WHERE (LOWER(customerName) LIKE '%' || LOWER(:query) || '%' OR LOWER(operatorName) LIKE '%' || LOWER(:query) || '%' OR LOWER(tractorLabel) LIKE '%' || LOWER(:query) || '%')")
    fun searchJobs(query: String): Flow<List<JobEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: JobEntryEntity): Long

    @Update
    suspend fun updateJob(job: JobEntryEntity)

    @Delete
    suspend fun deleteJob(job: JobEntryEntity)

    @Query("DELETE FROM job_entries WHERE workspaceId = :workspaceId AND isSynced = 1 AND id NOT IN (:validIds)")
    suspend fun deleteSyncedNotIn(workspaceId: String, validIds: List<Long>)

    @Query("DELETE FROM job_entries WHERE isSynced = 1 AND id NOT IN (:validIds)")
    suspend fun deleteSyncedNotIn(validIds: List<Long>)

    @Query("DELETE FROM job_entries WHERE workspaceId = :workspaceId AND isSynced = 1")
    suspend fun deleteAllSyncedForWorkspace(workspaceId: String)

    @Query("DELETE FROM job_entries WHERE workspaceId IN (:workspaceIds) AND isSynced = 1")
    suspend fun deleteAllSyncedForWorkspaces(workspaceIds: List<String>)

    @Query("DELETE FROM job_entries WHERE isSynced = 1")
    suspend fun deleteAllSynced()

    @Query("SELECT SUM(amountReceived) FROM job_entries WHERE workspaceId = :workspaceId")
    fun getTotalReceivedForWorkspace(workspaceId: String): Flow<Double?>

    @Query("SELECT SUM(amountReceived) FROM job_entries WHERE workspaceId IN (:workspaceIds)")
    fun getTotalReceivedForWorkspaces(workspaceIds: List<String>): Flow<Double?>

    @Query("SELECT SUM(amountReceived) FROM job_entries")
    fun getTotalReceived(): Flow<Double?>

    @Query("SELECT SUM(pendingAmount) FROM job_entries WHERE workspaceId = :workspaceId")
    fun getTotalPendingForWorkspace(workspaceId: String): Flow<Double?>

    @Query("SELECT SUM(pendingAmount) FROM job_entries WHERE workspaceId IN (:workspaceIds)")
    fun getTotalPendingForWorkspaces(workspaceIds: List<String>): Flow<Double?>

    @Query("SELECT SUM(pendingAmount) FROM job_entries")
    fun getTotalPending(): Flow<Double?>

    @Query("SELECT * FROM job_entries WHERE workspaceId = :workspaceId AND isSynced = 0")
    suspend fun getUnsyncedJobsForWorkspace(workspaceId: String): List<JobEntryEntity>

    @Query("SELECT * FROM job_entries WHERE isSynced = 0")
    suspend fun getUnsyncedJobs(): List<JobEntryEntity>

    @Query("UPDATE job_entries SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markJobsSynced(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM job_entries WHERE workspaceId = :workspaceId AND isSynced = 0")
    fun getUnsyncedCountForWorkspace(workspaceId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM job_entries WHERE workspaceId IN (:workspaceIds) AND isSynced = 0")
    fun getUnsyncedCountForWorkspaces(workspaceIds: List<String>): Flow<Int>

    @Query("SELECT COUNT(*) FROM job_entries WHERE isSynced = 0")
    fun getUnsyncedCount(): Flow<Int>
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE workspaceId = :workspaceId ORDER BY dateTimestamp DESC")
    fun getExpensesForWorkspace(workspaceId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE workspaceId IN (:workspaceIds) ORDER BY dateTimestamp DESC")
    fun getExpensesForWorkspaces(workspaceIds: List<String>): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE workspaceId = :workspaceId ORDER BY dateTimestamp DESC")
    suspend fun getExpensesForWorkspaceOnce(workspaceId: String): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE workspaceId IN (:workspaceIds) ORDER BY dateTimestamp DESC")
    suspend fun getExpensesForWorkspacesOnce(workspaceIds: List<String>): List<ExpenseEntity>

    @Query("SELECT * FROM expenses ORDER BY dateTimestamp DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE workspaceId = :workspaceId AND isSynced = 1 AND id NOT IN (:validIds)")
    suspend fun deleteSyncedNotIn(workspaceId: String, validIds: List<Long>)

    @Query("DELETE FROM expenses WHERE isSynced = 1 AND id NOT IN (:validIds)")
    suspend fun deleteSyncedNotIn(validIds: List<Long>)

    @Query("DELETE FROM expenses WHERE workspaceId = :workspaceId AND isSynced = 1")
    suspend fun deleteAllSyncedForWorkspace(workspaceId: String)

    @Query("DELETE FROM expenses WHERE workspaceId IN (:workspaceIds) AND isSynced = 1")
    suspend fun deleteAllSyncedForWorkspaces(workspaceIds: List<String>)

    @Query("DELETE FROM expenses WHERE isSynced = 1")
    suspend fun deleteAllSynced()

    @Query("SELECT SUM(amount) FROM expenses WHERE workspaceId = :workspaceId")
    fun getTotalExpensesForWorkspace(workspaceId: String): Flow<Double?>

    @Query("SELECT SUM(amount) FROM expenses WHERE workspaceId IN (:workspaceIds)")
    fun getTotalExpensesForWorkspaces(workspaceIds: List<String>): Flow<Double?>

    @Query("SELECT SUM(amount) FROM expenses")
    fun getTotalExpenses(): Flow<Double?>

    @Query("SELECT * FROM expenses WHERE workspaceId = :workspaceId AND isSynced = 0")
    suspend fun getUnsyncedExpensesForWorkspace(workspaceId: String): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE isSynced = 0")
    suspend fun getUnsyncedExpenses(): List<ExpenseEntity>

    @Query("UPDATE expenses SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markExpensesSynced(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM expenses WHERE workspaceId = :workspaceId AND isSynced = 0")
    fun getUnsyncedCountForWorkspace(workspaceId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM expenses WHERE workspaceId IN (:workspaceIds) AND isSynced = 0")
    fun getUnsyncedCountForWorkspaces(workspaceIds: List<String>): Flow<Int>

    @Query("SELECT COUNT(*) FROM expenses WHERE isSynced = 0")
    fun getUnsyncedCount(): Flow<Int>
}

@Dao
interface WithdrawalDao {
    @Query("SELECT * FROM withdrawals WHERE workspaceId = :workspaceId ORDER BY timestamp DESC")
    fun getWithdrawalsForWorkspace(workspaceId: String): Flow<List<WithdrawalEntity>>

    @Query("SELECT * FROM withdrawals WHERE workspaceId IN (:workspaceIds) ORDER BY timestamp DESC")
    fun getWithdrawalsForWorkspaces(workspaceIds: List<String>): Flow<List<WithdrawalEntity>>

    @Query("SELECT * FROM withdrawals WHERE workspaceId = :workspaceId ORDER BY timestamp DESC")
    suspend fun getWithdrawalsForWorkspaceOnce(workspaceId: String): List<WithdrawalEntity>

    @Query("SELECT * FROM withdrawals WHERE workspaceId IN (:workspaceIds) ORDER BY timestamp DESC")
    suspend fun getWithdrawalsForWorkspacesOnce(workspaceIds: List<String>): List<WithdrawalEntity>

    @Query("SELECT * FROM withdrawals ORDER BY timestamp DESC")
    fun getAllWithdrawals(): Flow<List<WithdrawalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWithdrawal(withdrawal: WithdrawalEntity): Long

    @Update
    suspend fun updateWithdrawal(withdrawal: WithdrawalEntity)

    @Delete
    suspend fun deleteWithdrawal(withdrawal: WithdrawalEntity)

    @Query("DELETE FROM withdrawals WHERE workspaceId = :workspaceId AND isSynced = 1 AND id NOT IN (:validIds)")
    suspend fun deleteSyncedNotIn(workspaceId: String, validIds: List<Long>)

    @Query("DELETE FROM withdrawals WHERE isSynced = 1 AND id NOT IN (:validIds)")
    suspend fun deleteSyncedNotIn(validIds: List<Long>)

    @Query("DELETE FROM withdrawals WHERE workspaceId = :workspaceId AND isSynced = 1")
    suspend fun deleteAllSyncedForWorkspace(workspaceId: String)

    @Query("DELETE FROM withdrawals WHERE workspaceId IN (:workspaceIds) AND isSynced = 1")
    suspend fun deleteAllSyncedForWorkspaces(workspaceIds: List<String>)

    @Query("DELETE FROM withdrawals WHERE isSynced = 1")
    suspend fun deleteAllSynced()

    @Query("SELECT SUM(amount) FROM withdrawals WHERE workspaceId = :workspaceId")
    fun getTotalWithdrawnForWorkspace(workspaceId: String): Flow<Double?>

    @Query("SELECT SUM(amount) FROM withdrawals WHERE workspaceId IN (:workspaceIds)")
    fun getTotalWithdrawnForWorkspaces(workspaceIds: List<String>): Flow<Double?>

    @Query("SELECT SUM(amount) FROM withdrawals")
    fun getTotalWithdrawn(): Flow<Double?>

    @Query("SELECT * FROM withdrawals WHERE workspaceId = :workspaceId AND isSynced = 0")
    suspend fun getUnsyncedWithdrawalsForWorkspace(workspaceId: String): List<WithdrawalEntity>

    @Query("SELECT * FROM withdrawals WHERE isSynced = 0")
    suspend fun getUnsyncedWithdrawals(): List<WithdrawalEntity>

    @Query("UPDATE withdrawals SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markWithdrawalsSynced(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM withdrawals WHERE workspaceId = :workspaceId AND isSynced = 0")
    fun getUnsyncedCountForWorkspace(workspaceId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM withdrawals WHERE workspaceId IN (:workspaceIds) AND isSynced = 0")
    fun getUnsyncedCountForWorkspaces(workspaceIds: List<String>): Flow<Int>

    @Query("SELECT COUNT(*) FROM withdrawals WHERE isSynced = 0")
    fun getUnsyncedCount(): Flow<Int>
}

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE workspaceId = :workspaceId LIMIT 1")
    fun getSettingsForWorkspace(workspaceId: String): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE workspaceId = :workspaceId LIMIT 1")
    suspend fun getSettingsForWorkspaceOnce(workspaceId: String): AppSettingsEntity?

    @Query("SELECT * FROM app_settings LIMIT 1")
    fun getSettings(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings LIMIT 1")
    suspend fun getSettingsOnce(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSettings(settings: AppSettingsEntity)

    @Query("DELETE FROM app_settings WHERE workspaceId = :workspaceId")
    suspend fun deleteSettingsForWorkspace(workspaceId: String)
}
