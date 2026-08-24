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
    @Query("SELECT * FROM partners ORDER BY id ASC")
    fun getAllPartners(): Flow<List<PartnerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPartner(partner: PartnerEntity): Long

    @Update
    suspend fun updatePartner(partner: PartnerEntity)

    @Delete
    suspend fun deletePartner(partner: PartnerEntity)

    @Query("SELECT COUNT(*) FROM partners")
    suspend fun getCount(): Int
}

@Dao
interface TractorDao {
    @Query("SELECT * FROM tractors ORDER BY id ASC")
    fun getAllTractors(): Flow<List<TractorEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTractor(tractor: TractorEntity): Long

    @Update
    suspend fun updateTractor(tractor: TractorEntity)

    @Delete
    suspend fun deleteTractor(tractor: TractorEntity)

    @Query("SELECT COUNT(*) FROM tractors")
    suspend fun getCount(): Int
}

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY updatedAt DESC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE balanceDue > 0 ORDER BY balanceDue DESC")
    fun getCustomersWithDue(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getCustomerById(id: Long): CustomerEntity?

    @Query("SELECT * FROM customers WHERE LOWER(name) LIKE '%' || LOWER(:query) || '%' OR phone LIKE '%' || :query || '%'")
    fun searchCustomers(query: String): Flow<List<CustomerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity): Long

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    @Delete
    suspend fun deleteCustomer(customer: CustomerEntity)

    @Query("SELECT * FROM customers WHERE isSynced = 0")
    suspend fun getUnsyncedCustomers(): List<CustomerEntity>

    @Query("UPDATE customers SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markCustomersSynced(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM customers WHERE isSynced = 0")
    fun getUnsyncedCount(): Flow<Int>
}

@Dao
interface JobEntryDao {
    @Query("SELECT * FROM job_entries ORDER BY startTimeMillis DESC")
    fun getAllJobs(): Flow<List<JobEntryEntity>>

    @Query("SELECT * FROM job_entries WHERE customerId = :customerId ORDER BY startTimeMillis DESC")
    fun getJobsForCustomer(customerId: Long): Flow<List<JobEntryEntity>>

    @Query("SELECT * FROM job_entries WHERE LOWER(customerName) LIKE '%' || LOWER(:query) || '%' OR LOWER(operatorName) LIKE '%' || LOWER(:query) || '%' OR LOWER(tractorLabel) LIKE '%' || LOWER(:query) || '%'")
    fun searchJobs(query: String): Flow<List<JobEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: JobEntryEntity): Long

    @Update
    suspend fun updateJob(job: JobEntryEntity)

    @Delete
    suspend fun deleteJob(job: JobEntryEntity)

    @Query("SELECT SUM(amountReceived) FROM job_entries")
    fun getTotalReceived(): Flow<Double?>

    @Query("SELECT SUM(pendingAmount) FROM job_entries")
    fun getTotalPending(): Flow<Double?>

    @Query("SELECT * FROM job_entries WHERE isSynced = 0")
    suspend fun getUnsyncedJobs(): List<JobEntryEntity>

    @Query("UPDATE job_entries SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markJobsSynced(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM job_entries WHERE isSynced = 0")
    fun getUnsyncedCount(): Flow<Int>
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY dateTimestamp DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity)

    @Query("SELECT SUM(amount) FROM expenses")
    fun getTotalExpenses(): Flow<Double?>

    @Query("SELECT * FROM expenses WHERE isSynced = 0")
    suspend fun getUnsyncedExpenses(): List<ExpenseEntity>

    @Query("UPDATE expenses SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markExpensesSynced(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM expenses WHERE isSynced = 0")
    fun getUnsyncedCount(): Flow<Int>
}

@Dao
interface WithdrawalDao {
    @Query("SELECT * FROM withdrawals ORDER BY timestamp DESC")
    fun getAllWithdrawals(): Flow<List<WithdrawalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWithdrawal(withdrawal: WithdrawalEntity): Long

    @Update
    suspend fun updateWithdrawal(withdrawal: WithdrawalEntity)

    @Delete
    suspend fun deleteWithdrawal(withdrawal: WithdrawalEntity)

    @Query("SELECT SUM(amount) FROM withdrawals")
    fun getTotalWithdrawn(): Flow<Double?>

    @Query("SELECT * FROM withdrawals WHERE isSynced = 0")
    suspend fun getUnsyncedWithdrawals(): List<WithdrawalEntity>

    @Query("UPDATE withdrawals SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markWithdrawalsSynced(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM withdrawals WHERE isSynced = 0")
    fun getUnsyncedCount(): Flow<Int>
}

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    fun getSettings(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsOnce(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSettings(settings: AppSettingsEntity)
}
