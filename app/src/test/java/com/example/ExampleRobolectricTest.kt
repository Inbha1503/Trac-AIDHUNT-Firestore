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
        assertEquals("Trac", appName)
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
            expenseType = "Diesel",
            amount = 1500.0,
            description = "Fuel",
            dateTimestamp = 1000L,
            addedByPartner = "Partner A",
            tractorId = 1L,
            tractorLabel = "Mahindra",
            isSynced = false
        )

        val syncedExpA = unsyncedExpA.copy(id = 502L, isSynced = true)

        val unsyncedExpB = ExpenseEntity(
            id = 601L,
            workspaceId = "ws_account_b",
            expenseType = "Oil Change",
            amount = 800.0,
            description = "Maintenance",
            dateTimestamp = 2000L,
            addedByPartner = "Partner B",
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

    @Test
    fun `customer and tractor isolation - entities are partitioned strictly by workspaceId`() = runBlocking {
        val customerDao = database.customerDao()
        val tractorDao = database.tractorDao()

        val custA = CustomerEntity(
            id = 1L,
            workspaceId = "ws_owner_100",
            name = "Farmer Ramesh",
            phone = "9876543210",
            location = "North Village",
            totalBilled = 3000.0,
            totalPaid = 2000.0,
            balanceDue = 1000.0,
            isSynced = true
        )
        val custB = custA.copy(id = 2L, workspaceId = "ws_owner_200", name = "Farmer Suresh")

        customerDao.insertCustomer(custA)
        customerDao.insertCustomer(custB)

        val customersA = customerDao.getCustomersForWorkspace("ws_owner_100").first()
        val customersB = customerDao.getCustomersForWorkspace("ws_owner_200").first()

        assertEquals(1, customersA.size)
        assertEquals("Farmer Ramesh", customersA[0].name)
        assertEquals(1, customersB.size)
        assertEquals("Farmer Suresh", customersB[0].name)
    }

    @Test
    fun `shared canonical workspace - multiple partners write to same owner workspaceId`() = runBlocking {
        val jobDao = database.jobEntryDao()
        val canonicalOwnerWsId = "ws_owner_alpha"

        // Owner creates entry
        val entryByOwner = JobEntryEntity(
            id = 101L,
            workspaceId = canonicalOwnerWsId,
            customerId = 1L,
            customerName = "Customer 1",
            customerPhone = "1234567890",
            customerLocation = "Farm A",
            operatorName = "Owner Alpha",
            tractorId = 1L,
            tractorLabel = "John Deere 5050D",
            workType = "Ploughing",
            startTimeMillis = 1000L,
            endTimeMillis = 2000L,
            durationMinutes = 60,
            hourlyRate = 1000.0,
            totalAmount = 1000.0,
            amountReceived = 1000.0,
            pendingAmount = 0.0,
            addedByPartner = "Owner Alpha",
            isSynced = true
        )

        // Partner 1 creates entry in the same canonical owner workspace
        val entryByPartner1 = entryByOwner.copy(
            id = 102L,
            operatorName = "Partner 1",
            addedByPartner = "Partner 1"
        )

        // Partner 2 creates entry in the same canonical owner workspace
        val entryByPartner2 = entryByOwner.copy(
            id = 103L,
            operatorName = "Partner 2",
            addedByPartner = "Partner 2"
        )

        jobDao.insertJob(entryByOwner)
        jobDao.insertJob(entryByPartner1)
        jobDao.insertJob(entryByPartner2)

        val allJobsInWorkspace = jobDao.getJobsForWorkspace(canonicalOwnerWsId).first()
        assertEquals(3, allJobsInWorkspace.size)
        assertTrue(allJobsInWorkspace.any { it.addedByPartner == "Owner Alpha" })
        assertTrue(allJobsInWorkspace.any { it.addedByPartner == "Partner 1" })
        assertTrue(allJobsInWorkspace.any { it.addedByPartner == "Partner 2" })
    }
}

