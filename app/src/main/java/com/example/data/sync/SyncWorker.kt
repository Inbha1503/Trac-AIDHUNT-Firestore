package com.example.data.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.database.AppDatabase
import com.example.data.network.NetworkMonitor
import java.util.concurrent.TimeUnit

/**
 * Background WorkManager Worker for robust, non-blocking cloud synchronization.
 */
class SyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val TAG = "SyncWorker"

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting background sync job...")
        return try {
            val database = AppDatabase.getInstance(context)
            val syncManager = FirestoreSyncManager(context, database)
            val networkMonitor = NetworkMonitor(context)
            val isOnline = networkMonitor.isCurrentlyConnected()

            if (!isOnline) {
                Log.d(TAG, "Device is offline. Will retry when connected.")
                return Result.retry()
            }

            val success = syncManager.synchronize(isOnline = true)
            if (success) {
                Log.d(TAG, "SyncWorker finished successfully.")
                Result.success()
            } else {
                Log.d(TAG, "SyncWorker encountered temporary failure. Retrying.")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "SyncWorker execution exception: ${e.message}", e)
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_PERIODIC_SYNC_WORK = "aidhunt_tractor_periodic_sync"
        private const val UNIQUE_ONE_TIME_SYNC_WORK = "aidhunt_tractor_one_time_sync"

        /**
         * Schedules periodic background sync (every 15-30 minutes) whenever network is connected
         */
        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_SYNC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
        }

        /**
         * Enqueues an immediate one-time sync task when network is connected
         */
        fun enqueueOneTimeSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val oneTimeRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_ONE_TIME_SYNC_WORK,
                ExistingWorkPolicy.REPLACE,
                oneTimeRequest
            )
        }
    }
}
