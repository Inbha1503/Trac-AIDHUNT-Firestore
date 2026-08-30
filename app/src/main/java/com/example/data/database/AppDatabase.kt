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
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
