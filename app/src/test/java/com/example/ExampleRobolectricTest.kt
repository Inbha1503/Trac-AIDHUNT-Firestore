package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.entity.CustomerEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var database: AppDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("AIDHUNT Trac", appName)
    }

    @Test
    fun `account isolation - queries for workspace A do not return workspace B records`() = runBlocking {
        val jobDao = database.jobEntryDao()

        val jobA = JobEntryEntity(
            id = 1001L,
            workspaceId = "ws_account_a",
            customerId = 1L,
            customerName = "Ramesh Kumar",
            customerPhone = "9876543210",
            customerLocation = "Farm North",
            operatorName = "Operator A",
            tractorId = 1L,
            tractorLabel = "Mahindra 575",
            workType = "Ploughing",
            startTimeMillis = 1000L,
            endTimeMillis = 5000L,
            durationMinutes = 60,
            hourlyRate = 800.0,
            totalAmount = 800.0,
            amountReceived = 500.0,
            pendingAmount = 300.0,
            addedByPartner = "Operator A",
            isSynced = true
        )

        val jobB = JobEntryEntity(
            id = 2002L,
            workspaceId = "ws_account_b",
            customerId = 2L,
            customerName = "Suresh Patel",
            customerPhone = "9123456780",
            customerLocation = "Farm South",
            operatorName = "Operator B",
            tractorId = 2L,
            tractorLabel = "John Deere",
            workType = "Rotavator",
            startTimeMillis = 2000L,
            endTimeMillis = 6000L,
            durationMinutes = 90,
            hourlyRate = 900.0,
            totalAmount = 1350.0,
            amountReceived = 1350.0,
            pendingAmount = 0.0,
            addedByPartner = "Operator B",
            isSynced = true
        )

        jobDao.insertJob(jobA)
        jobDao.insertJob(jobB)

        val jobsForA = jobDao.getJobsForWorkspace("ws_account_a").first()
        val jobsForB = jobDao.getJobsForWorkspace("ws_account_b").first()
        val jobsForEmpty = jobDao.getJobsForWorkspace("").first()

        assertEquals(1, jobsForA.size)
        assertEquals(1001L, jobsForA[0].id)
        assertEquals("ws_account_a", jobsForA[0].workspaceId)

        assertEquals(1, jobsForB.size)
        assertEquals(2002L, jobsForB[0].id)
        assertEquals("ws_account_b", jobsForB[0].workspaceId)

        assertEquals(0, jobsForEmpty.size)
    }

    @Test
    fun `remote deletion reconciliation - deletes obsolete records scoped only to that workspace`() = runBlocking {
        val jobDao = database.jobEntryDao()

        val jobA1 = JobEntryEntity(
            id = 101L,
            workspaceId = "ws_account_a",
            customerId = 1L,
            customerName = "Ramesh",
            customerPhone = "123",
            customerLocation = "Loc",
            operatorName = "Op",
            tractorId = 1L,
            tractorLabel = "T1",
            workType = "Ploughing",
            startTimeMillis = 1000L,
            endTimeMillis = 2000L,
            durationMinutes = 60,
            hourlyRate = 500.0,
            totalAmount = 500.0,
            amountReceived = 500.0,
            pendingAmount = 0.0,
            addedByPartner = "Op",
            isSynced = true
        )

        val jobA2 = jobA1.copy(id = 102L)
        val jobB1 = jobA1.copy(id = 201L, workspaceId = "ws_account_b")

        jobDao.insertJob(jobA1)
        jobDao.insertJob(jobA2)
        jobDao.insertJob(jobB1)

        // Simulate remote deletion of jobA2 on Device B: remote snapshot now only has [101L]
        val remoteIdsPresent = listOf(101L)
        jobDao.deleteSyncedNotIn("ws_account_a", remoteIdsPresent)

        val remainingA = jobDao.getJobsForWorkspace("ws_account_a").first()
        val remainingB = jobDao.getJobsForWorkspace("ws_account_b").first()

        assertEquals(1, remainingA.size)
        assertEquals(101L, remainingA[0].id)

        // Workspace B's records MUST remain intact
        assertEquals(1, remainingB.size)
        assertEquals(201L, remainingB[0].id)
    }

    @Test
    fun `unsynced counter isolation - unsynced counts are workspace isolated`() = runBlocking {
        val expenseDao = database.expenseDao()

        val unsyncedExpA = ExpenseEntity(
            id = 501L,
            workspaceId = "ws_account_a",
            title = "Diesel A",
            amount = 1500.0,
            category = "Fuel",
            dateTimestamp = 1000L,
            paidByPartner = "Partner A",
            tractorId = 1L,
            tractorLabel = "Mahindra",
            isSynced = false
        )

        val syncedExpA = unsyncedExpA.copy(id = 502L, isSynced = true)

        val unsyncedExpB = ExpenseEntity(
            id = 601L,
            workspaceId = "ws_account_b",
            title = "Oil B",
            amount = 800.0,
            category = "Maintenance",
            dateTimestamp = 2000L,
            paidByPartner = "Partner B",
            tractorId = 2L,
            tractorLabel = "John Deere",
            isSynced = false
        )

        expenseDao.insertExpense(unsyncedExpA)
        expenseDao.insertExpense(syncedExpA)
        expenseDao.insertExpense(unsyncedExpB)

        val unsyncedCountA = expenseDao.getUnsyncedCountForWorkspace("ws_account_a").first()
        val unsyncedCountB = expenseDao.getUnsyncedCountForWorkspace("ws_account_b").first()

        assertEquals(1, unsyncedCountA)
        assertEquals(1, unsyncedCountB)
    }
}

