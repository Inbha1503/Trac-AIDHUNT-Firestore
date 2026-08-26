package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.AppSettingsDao
import com.example.data.dao.CustomerDao
import com.example.data.dao.ExpenseDao
import com.example.data.dao.JobEntryDao
import com.example.data.dao.PartnerDao
import com.example.data.dao.TractorDao
import com.example.data.dao.WithdrawalDao
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.CustomerEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PartnerEntity
import com.example.data.entity.TractorEntity
import com.example.data.entity.WithdrawalEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        PartnerEntity::class,
        TractorEntity::class,
        CustomerEntity::class,
        JobEntryEntity::class,
        ExpenseEntity::class,
        WithdrawalEntity::class,
        AppSettingsEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun partnerDao(): PartnerDao
    abstract fun tractorDao(): TractorDao
    abstract fun customerDao(): CustomerDao
    abstract fun jobEntryDao(): JobEntryDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun withdrawalDao(): WithdrawalDao
    abstract fun appSettingsDao(): AppSettingsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE partners ADD COLUMN workspaceId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tractors ADD COLUMN workspaceId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE customers ADD COLUMN workspaceId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE job_entries ADD COLUMN workspaceId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE expenses ADD COLUMN workspaceId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE withdrawals ADD COLUMN workspaceId TEXT NOT NULL DEFAULT ''")

                // Recreate app_settings table with workspaceId as primary key
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS app_settings_new (
                        workspaceId TEXT NOT NULL PRIMARY KEY,
                        businessName TEXT NOT NULL,
                        ownerName TEXT NOT NULL,
                        businessPhone TEXT NOT NULL,
                        businessAddress TEXT NOT NULL,
                        gstNumber TEXT NOT NULL,
                        defaultHourlyRate REAL NOT NULL,
                        currency TEXT NOT NULL,
                        language TEXT NOT NULL,
                        sharedAccountId TEXT NOT NULL,
                        isLoggedIn INTEGER NOT NULL,
                        activePartnerName TEXT NOT NULL,
                        activePartnerPhone TEXT NOT NULL,
                        profilePhotoUri TEXT NOT NULL,
                        lockedTractorLabel TEXT NOT NULL,
                        lastSyncTime INTEGER NOT NULL
                    )
                """.trimIndent())

                db.execSQL("""
                    INSERT OR REPLACE INTO app_settings_new (
                        workspaceId, businessName, ownerName, businessPhone, businessAddress, gstNumber,
                        defaultHourlyRate, currency, language, sharedAccountId, isLoggedIn,
                        activePartnerName, activePartnerPhone, profilePhotoUri, lockedTractorLabel, lastSyncTime
                    )
                    SELECT '', businessName, ownerName, businessPhone, businessAddress, gstNumber,
                           defaultHourlyRate, currency, language, sharedAccountId, isLoggedIn,
                           activePartnerName, activePartnerPhone, profilePhotoUri, lockedTractorLabel, lastSyncTime
                    FROM app_settings
                """.trimIndent())

                db.execSQL("DROP TABLE app_settings")
                db.execSQL("ALTER TABLE app_settings_new RENAME TO app_settings")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "aidhunt_trac_v4.db"
                ).addMigrations(MIGRATION_4_5)
                .fallbackToDestructiveMigration(true)
                .addCallback(DatabaseCallback(context.applicationContext))
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(private val appContext: Context) : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Seed initial partners, tractors, default settings
                CoroutineScope(Dispatchers.IO).launch {
                    val database = getInstance(appContext)
                    seedDatabase(database)
                }
            }
        }

        private suspend fun seedDatabase(db: AppDatabase) {
            val partnerDao = db.partnerDao()
            val tractorDao = db.tractorDao()
            val customerDao = db.customerDao()
            val jobEntryDao = db.jobEntryDao()
            val expenseDao = db.expenseDao()
            val withdrawalDao = db.withdrawalDao()
            val settingsDao = db.appSettingsDao()

            // 1. Settings
            settingsDao.insertOrUpdateSettings(
                AppSettingsEntity(
                    workspaceId = "",
                    businessName = "AIDHUNT Agri & Tractor Services",
                    ownerName = "Muthu (Owner)",
                    businessPhone = "+91 98421 54321",
                    businessAddress = "Kaveri Road, Tiruchirappalli, Tamil Nadu",
                    defaultHourlyRate = 1100.0,
                    language = "EN",
                    sharedAccountId = "AIDHUNT-TRAC-SHARED-01",
                    isLoggedIn = true,
                    activePartnerName = "Muthu (Owner)",
                    activePartnerPhone = "+91 98421 54321",
                    lastSyncTime = System.currentTimeMillis()
                )
            )

            // 2. 3 Shared Business Partners
            val muthuId = partnerDao.insertPartner(
                PartnerEntity(name = "Muthu", phone = "+91 98421 54321", role = "Owner", avatarColorHex = "#1E4D2B", isCurrentActive = true)
            )
            val sureshId = partnerDao.insertPartner(
                PartnerEntity(name = "Suresh", phone = "+91 97890 12345", role = "Partner", avatarColorHex = "#2E6B3F", isCurrentActive = false)
            )
            val rameshId = partnerDao.insertPartner(
                PartnerEntity(name = "Ramesh", phone = "+91 94432 67890", role = "Partner", avatarColorHex = "#3F7D52", isCurrentActive = false)
            )

            // 3. Tractors
            val t1 = tractorDao.insertTractor(
                TractorEntity(label = "Mahindra 575 DI (Red)", chassisNo = "MH-575-TN45-9871", modelYear = "2023", operatorName = "Karthik")
            )
            val t2 = tractorDao.insertTractor(
                TractorEntity(label = "John Deere 5310 4WD (Green)", chassisNo = "JD-5310-TN48-4421", modelYear = "2024", operatorName = "Velu")
            )
            val t3 = tractorDao.insertTractor(
                TractorEntity(label = "Swaraj 744 FE", chassisNo = "SW-744-TN45-3120", modelYear = "2022", operatorName = "Saravanan")
            )

            // 4. Initial Customers
            val now = System.currentTimeMillis()
            val c1Id = customerDao.insertCustomer(
                CustomerEntity(
                    name = "Ramasamy Gounder",
                    phone = "+91 94431 87654",
                    location = "Manapparai West",
                    totalBilled = 14300.0,
                    totalPaid = 9000.0,
                    balanceDue = 5300.0,
                    createdAt = now - 86400000L * 4
                )
            )
            val c2Id = customerDao.insertCustomer(
                CustomerEntity(
                    name = "Kaliannan Farmer",
                    phone = "+91 98940 22334",
                    location = "Viralimalai Fields",
                    totalBilled = 8800.0,
                    totalPaid = 8800.0,
                    balanceDue = 0.0,
                    createdAt = now - 86400000L * 3
                )
            )
            val c3Id = customerDao.insertCustomer(
                CustomerEntity(
                    name = "Shanmugam Chettiar",
                    phone = "+91 97500 11223",
                    location = "Kulithalai Canal",
                    totalBilled = 19800.0,
                    totalPaid = 12000.0,
                    balanceDue = 7800.0,
                    createdAt = now - 86400000L * 2
                )
            )
            val c4Id = customerDao.insertCustomer(
                CustomerEntity(
                    name = "Palanisamy Thottam",
                    phone = "+91 96290 88990",
                    location = "Musiri East",
                    totalBilled = 6600.0,
                    totalPaid = 3000.0,
                    balanceDue = 3600.0,
                    createdAt = now - 86400000L * 1
                )
            )

            // 5. Initial Job Entries
            jobEntryDao.insertJob(
                JobEntryEntity(
                    customerId = c1Id,
                    customerName = "Ramasamy Gounder",
                    customerPhone = "+91 94431 87654",
                    customerLocation = "Manapparai West",
                    operatorName = "Karthik",
                    tractorId = t1,
                    tractorLabel = "Mahindra 575 DI (Red)",
                    workType = "Ploughing (3-Blade MB)",
                    startTimeMillis = now - 86400000L * 3 - 3600000L * 5,
                    endTimeMillis = now - 86400000L * 3 - 3600000L * 1,
                    durationMinutes = 240, // 4 hours
                    hourlyRate = 1100.0,
                    totalAmount = 4400.0,
                    amountReceived = 4400.0,
                    pendingAmount = 0.0,
                    addedByPartner = "Muthu",
                    notes = "Completed sugarcane field ploughing first pass",
                    createdAt = now - 86400000L * 3
                )
            )
            jobEntryDao.insertJob(
                JobEntryEntity(
                    customerId = c1Id,
                    customerName = "Ramasamy Gounder",
                    customerPhone = "+91 94431 87654",
                    customerLocation = "Manapparai West",
                    operatorName = "Velu",
                    tractorId = t2,
                    tractorLabel = "John Deere 5310 4WD (Green)",
                    workType = "Rotavator (42 Blades)",
                    startTimeMillis = now - 86400000L * 2 - 3600000L * 9,
                    endTimeMillis = now - 86400000L * 2,
                    durationMinutes = 540, // 9 hours
                    hourlyRate = 1100.0,
                    totalAmount = 9900.0,
                    amountReceived = 4600.0,
                    pendingAmount = 5300.0,
                    addedByPartner = "Suresh",
                    notes = "Second pass rotavator, promised balance next Monday",
                    createdAt = now - 86400000L * 2
                )
            )
            jobEntryDao.insertJob(
                JobEntryEntity(
                    customerId = c2Id,
                    customerName = "Kaliannan Farmer",
                    customerPhone = "+91 98940 22334",
                    customerLocation = "Viralimalai Fields",
                    operatorName = "Saravanan",
                    tractorId = t3,
                    tractorLabel = "Swaraj 744 FE",
                    workType = "Cultivator (9-Tyne)",
                    startTimeMillis = now - 86400000L * 1 - 3600000L * 8,
                    endTimeMillis = now - 86400000L * 1,
                    durationMinutes = 480, // 8 hours
                    hourlyRate = 1100.0,
                    totalAmount = 8800.0,
                    amountReceived = 8800.0,
                    pendingAmount = 0.0,
                    addedByPartner = "Ramesh",
                    notes = "Cash received on spot after work",
                    createdAt = now - 86400000L * 1
                )
            )
            jobEntryDao.insertJob(
                JobEntryEntity(
                    customerId = c3Id,
                    customerName = "Shanmugam Chettiar",
                    customerPhone = "+91 97500 11223",
                    customerLocation = "Kulithalai Canal",
                    operatorName = "Karthik",
                    tractorId = t1,
                    tractorLabel = "Mahindra 575 DI (Red)",
                    workType = "Harvester Attachment / Bundle",
                    startTimeMillis = now - 3600000L * 18,
                    endTimeMillis = now - 3600000L * 2,
                    durationMinutes = 1080, // 18 hours (2 days)
                    hourlyRate = 1100.0,
                    totalAmount = 19800.0,
                    amountReceived = 12000.0,
                    pendingAmount = 7800.0,
                    addedByPartner = "Muthu",
                    notes = "Paddy harvest work. Sent statement on WhatsApp.",
                    createdAt = now - 3600000L * 2
                )
            )

            // 6. Initial Expenses
            expenseDao.insertExpense(
                ExpenseEntity(
                    expenseType = "Diesel",
                    amount = 4500.0,
                    tractorId = t1,
                    tractorLabel = "Mahindra 575 DI (Red)",
                    operatorName = "Karthik",
                    description = "50 Litres HPCL Bunk Diesel",
                    addedByPartner = "Muthu",
                    dateTimestamp = now - 86400000L * 3,
                    createdAt = now - 86400000L * 3
                )
            )
            expenseDao.insertExpense(
                ExpenseEntity(
                    expenseType = "Repair",
                    amount = 1200.0,
                    tractorId = t2,
                    tractorLabel = "John Deere 5310 4WD (Green)",
                    operatorName = "Velu",
                    description = "Hydraulic pipe clamp welding & grease",
                    addedByPartner = "Suresh",
                    dateTimestamp = now - 86400000L * 2,
                    createdAt = now - 86400000L * 2
                )
            )
            expenseDao.insertExpense(
                ExpenseEntity(
                    expenseType = "Puncture",
                    amount = 350.0,
                    tractorId = t3,
                    tractorLabel = "Swaraj 744 FE",
                    operatorName = "Saravanan",
                    description = "Rear tube puncture patch at Manapparai workshop",
                    addedByPartner = "Ramesh",
                    dateTimestamp = now - 86400000L * 1,
                    createdAt = now - 86400000L * 1
                )
            )
            expenseDao.insertExpense(
                ExpenseEntity(
                    expenseType = "Driver Bata",
                    amount = 1500.0,
                    tractorId = t1,
                    tractorLabel = "Mahindra 575 DI (Red)",
                    operatorName = "Karthik",
                    description = "Night work allowance for Kulithalai harvest job",
                    addedByPartner = "Muthu",
                    dateTimestamp = now - 3600000L * 4,
                    createdAt = now - 3600000L * 4
                )
            )

            // 7. Initial Withdrawals
            withdrawalDao.insertWithdrawal(
                WithdrawalEntity(
                    partnerId = muthuId,
                    partnerName = "Muthu",
                    amount = 5000.0,
                    category = "Personal Use",
                    note = "Weekly profit withdrawal",
                    timestamp = now - 86400000L * 2,
                    createdAt = now - 86400000L * 2
                )
            )
            withdrawalDao.insertWithdrawal(
                WithdrawalEntity(
                    partnerId = sureshId,
                    partnerName = "Suresh",
                    amount = 4000.0,
                    category = "Fuel Advance",
                    note = "Advance taken for upcoming diesel bulk barrel",
                    timestamp = now - 86400000L * 1,
                    createdAt = now - 86400000L * 1
                )
            )
        }
    }
}
