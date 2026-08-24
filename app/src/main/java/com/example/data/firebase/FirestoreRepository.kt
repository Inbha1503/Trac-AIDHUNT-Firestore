package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.CustomerEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PartnerEntity
import com.example.data.entity.TractorEntity
import com.example.data.entity.WithdrawalEntity
import com.example.data.util.IdGenerator
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FirestoreRepository(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val TAG = "FirestoreRepository"

    private val db: FirebaseFirestore? by lazy {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firestore: ${e.message}")
            null
        }
    }

    private val _currentWorkspace = MutableStateFlow<Workspace?>(null)
    val currentWorkspace: StateFlow<Workspace?> = _currentWorkspace.asStateFlow()

    // Real-time listener registrations
    private var entriesListener: ListenerRegistration? = null
    private var expensesListener: ListenerRegistration? = null
    private var customersListener: ListenerRegistration? = null
    private var tractorsListener: ListenerRegistration? = null
    private var attendeesListener: ListenerRegistration? = null
    private var withdrawalsListener: ListenerRegistration? = null
    private var settingsListener: ListenerRegistration? = null

    /**
     * Resolves an existing workspace or creates a canonical one for the user deterministically.
     * Uses server-backed Firestore lookups to prevent multi-device race conditions.
     */
    suspend fun resolveOrCreateWorkspace(user: UserProfile): Workspace? {
        val firestore = db ?: return null
        val uid = user.uid
        if (uid.isBlank()) return null

        try {
            // 1. Fetch user document directly from Firestore server
            var workspaceId: String? = null
            try {
                val userDoc = firestore.collection("users").document(uid).get().await()
                if (userDoc.exists()) {
                    val userFromCloud = UserProfile.fromMap(userDoc.data ?: emptyMap())
                    workspaceId = userFromCloud.defaultWorkspaceId?.ifBlank { null }
                        ?: userFromCloud.workspaces.firstOrNull { it.isNotBlank() }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed reading user profile from cloud: ${e.message}")
            }

            // Fallback to in-memory profile if cloud fetch didn't return a workspace
            if (workspaceId.isNullOrBlank()) {
                workspaceId = user.defaultWorkspaceId?.ifBlank { null }
                    ?: user.workspaces.firstOrNull { it.isNotBlank() }
            }

            // 2. If a workspaceId is found, check if the workspace document exists in Firestore
            if (!workspaceId.isNullOrBlank()) {
                try {
                    val wsDoc = firestore.collection("workspaces").document(workspaceId).get().await()
                    if (wsDoc.exists()) {
                        val ws = Workspace.fromMap(wsDoc.data ?: emptyMap())
                        _currentWorkspace.value = ws
                        return ws
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed reading workspace document $workspaceId: ${e.message}")
                }
            }

            // 3. Compute deterministic canonical workspace ID based on UID
            val canonicalWsId = "ws_${uid.replace(Regex("[^a-zA-Z0-9]"), "").take(16).ifBlank { "main" }}"

            // Check if canonical workspace exists
            val canonicalWsDoc = firestore.collection("workspaces").document(canonicalWsId).get().await()
            if (canonicalWsDoc.exists()) {
                val ws = Workspace.fromMap(canonicalWsDoc.data ?: emptyMap())
                // Ensure user profile points to it
                firestore.collection("users").document(uid)
                    .set(
                        mapOf(
                            "defaultWorkspaceId" to canonicalWsId,
                            "workspaces" to listOf(canonicalWsId),
                            "updatedAt" to System.currentTimeMillis()
                        ),
                        SetOptions.merge()
                    ).await()
                _currentWorkspace.value = ws
                return ws
            }

            // 4. Create new canonical workspace for this user
            val workspaceName = if (!user.displayName.isNullOrBlank()) {
                "${user.displayName}'s Tractor Services"
            } else {
                "AIDHUNT Agri & Tractor Services"
            }

            val newWorkspace = Workspace(
                workspaceId = canonicalWsId,
                name = workspaceName,
                ownerUid = uid,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            // Create workspace document
            firestore.collection("workspaces").document(canonicalWsId)
                .set(newWorkspace.toMap(), SetOptions.merge())
                .await()

            // Create owner member record
            val member = WorkspaceMember(
                uid = uid,
                role = "owner",
                status = "active",
                joinedAt = System.currentTimeMillis(),
                displayName = user.displayName,
                email = user.email,
                phoneNumber = user.phoneNumber
            )
            firestore.collection("workspaces").document(canonicalWsId)
                .collection("members").document(uid)
                .set(member.toMap(), SetOptions.merge())
                .await()

            // Link workspace in user's profile
            val updatedUserMap = mapOf(
                "uid" to uid,
                "displayName" to user.displayName,
                "email" to user.email,
                "phoneNumber" to user.phoneNumber,
                "photoUrl" to user.photoUrl,
                "defaultWorkspaceId" to canonicalWsId,
                "workspaces" to listOf(canonicalWsId),
                "updatedAt" to System.currentTimeMillis()
            )
            firestore.collection("users").document(uid)
                .set(updatedUserMap, SetOptions.merge())
                .await()

            _currentWorkspace.value = newWorkspace
            return newWorkspace
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving workspace: ${e.message}", e)
            val fallbackWsId = "ws_${uid.replace(Regex("[^a-zA-Z0-9]"), "").take(16).ifBlank { "main" }}"
            val fallbackWs = Workspace(
                workspaceId = fallbackWsId,
                name = "AIDHUNT Agri & Tractor Services",
                ownerUid = uid
            )
            _currentWorkspace.value = fallbackWs
            return fallbackWs
        }
    }

    /**
     * Fetches settings from Firestore `workspaces/{workspaceId}/settings/main`.
     * If existing cloud settings are found, returns them so local Room cache can be updated.
     * If no settings exist in cloud yet, writes initial settings to cloud and returns them.
     */
    suspend fun fetchOrCreateWorkspaceSettings(
        workspaceId: String,
        uid: String,
        localSettings: AppSettingsEntity
    ): AppSettingsEntity {
        val firestore = db ?: return localSettings
        val settingsDocRef = firestore.collection("workspaces").document(workspaceId)
            .collection("settings").document("main")

        return try {
            val snapshot = settingsDocRef.get().await()
            if (snapshot.exists() && snapshot.data != null) {
                // Cloud settings exist: Merge with local entity
                val remoteData = snapshot.data!!
                val merged = appSettingsFromFirestoreMap(remoteData, localSettings)
                Log.d(TAG, "Loaded existing cloud settings for workspace: $workspaceId")
                merged
            } else {
                // No cloud settings exist yet: initialize with local settings
                Log.d(TAG, "Initializing first-time cloud settings for workspace: $workspaceId")
                settingsDocRef.set(localSettings.toFirestoreMap(uid), SetOptions.merge()).await()
                localSettings
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching/creating cloud settings: ${e.message}", e)
            localSettings
        }
    }

    /**
     * Registers real-time Firestore listeners on all subcollections for the workspace.
     * When remote additions/edits/deletions happen, callbacks update local Room storage.
     */
    fun startRealtimeListeners(
        workspaceId: String,
        onJobsUpdated: (List<JobEntryEntity>) -> Unit,
        onExpensesUpdated: (List<ExpenseEntity>) -> Unit,
        onCustomersUpdated: (List<CustomerEntity>) -> Unit,
        onTractorsUpdated: (List<TractorEntity>) -> Unit,
        onPartnersUpdated: (List<PartnerEntity>) -> Unit,
        onWithdrawalsUpdated: (List<WithdrawalEntity>) -> Unit,
        onSettingsUpdated: (Map<String, Any?>) -> Unit
    ) {
        val firestore = db ?: return
        if (workspaceId.isBlank()) return

        // Prevent duplicate listeners
        stopRealtimeListeners()

        val wsRef = firestore.collection("workspaces").document(workspaceId)

        // 1. Entries Listener
        entriesListener = wsRef.collection("entries")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Entries listener error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val jobs = snapshot.documents.mapNotNull { doc ->
                        doc.data?.let { jobEntryFromFirestoreMap(it, fallbackId = parseLongId(doc.id)) }
                    }
                    onJobsUpdated(jobs)
                }
            }

        // 2. Expenses Listener
        expensesListener = wsRef.collection("expenses")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Expenses listener error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val expenses = snapshot.documents.mapNotNull { doc ->
                        doc.data?.let { expenseFromFirestoreMap(it, fallbackId = parseLongId(doc.id)) }
                    }
                    onExpensesUpdated(expenses)
                }
            }

        // 3. Customers Listener
        customersListener = wsRef.collection("customers")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Customers listener error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val customers = snapshot.documents.mapNotNull { doc ->
                        doc.data?.let { customerFromFirestoreMap(it, fallbackId = parseLongId(doc.id)) }
                    }
                    onCustomersUpdated(customers)
                }
            }

        // 4. Tractors Listener
        tractorsListener = wsRef.collection("tractors")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Tractors listener error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val tractors = snapshot.documents.mapNotNull { doc ->
                        doc.data?.let { tractorFromFirestoreMap(it, fallbackId = parseLongId(doc.id)) }
                    }
                    onTractorsUpdated(tractors)
                }
            }

        // 5. Attendees (Partners / Operators) Listener
        attendeesListener = wsRef.collection("attendees")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Attendees listener error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val partners = snapshot.documents.mapNotNull { doc ->
                        doc.data?.let { partnerFromFirestoreMap(it, fallbackId = parseLongId(doc.id)) }
                    }
                    onPartnersUpdated(partners)
                }
            }

        // 6. Withdrawals Listener
        withdrawalsListener = wsRef.collection("withdrawals")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Withdrawals listener error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val withdrawals = snapshot.documents.mapNotNull { doc ->
                        doc.data?.let { withdrawalFromFirestoreMap(it, fallbackId = parseLongId(doc.id)) }
                    }
                    onWithdrawalsUpdated(withdrawals)
                }
            }

        // 7. Settings Listener
        settingsListener = wsRef.collection("settings").document("main")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Settings listener error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    snapshot.data?.let { onSettingsUpdated(it) }
                }
            }

        Log.d(TAG, "All Firestore snapshot listeners started for workspace: $workspaceId")
    }

    /**
     * Cleanly stops and unregisters all real-time listeners.
     */
    fun stopRealtimeListeners() {
        entriesListener?.remove()
        entriesListener = null

        expensesListener?.remove()
        expensesListener = null

        customersListener?.remove()
        customersListener = null

        tractorsListener?.remove()
        tractorsListener = null

        attendeesListener?.remove()
        attendeesListener = null

        withdrawalsListener?.remove()
        withdrawalsListener = null

        settingsListener?.remove()
        settingsListener = null

        Log.d(TAG, "All Firestore snapshot listeners stopped.")
    }

    // --- Direct Cloud Operations ---

    suspend fun saveJobEntry(workspaceId: String, job: JobEntryEntity, uid: String?) {
        val firestore = db ?: return
        try {
            val docId = if (job.id > 0) job.id.toString() else IdGenerator.generateId().toString()
            firestore.collection("workspaces").document(workspaceId)
                .collection("entries").document(docId)
                .set(job.toFirestoreMap(uid), SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving job entry to Firestore: ${e.message}", e)
        }
    }

    suspend fun deleteJob(workspaceId: String, jobId: Long) {
        val firestore = db ?: return
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("entries").document(jobId.toString())
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting job entry from Firestore: ${e.message}", e)
        }
    }

    suspend fun saveExpense(workspaceId: String, expense: ExpenseEntity, uid: String?) {
        val firestore = db ?: return
        try {
            val docId = if (expense.id > 0) expense.id.toString() else IdGenerator.generateId().toString()
            firestore.collection("workspaces").document(workspaceId)
                .collection("expenses").document(docId)
                .set(expense.toFirestoreMap(uid), SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving expense to Firestore: ${e.message}", e)
        }
    }

    suspend fun deleteExpense(workspaceId: String, expenseId: Long) {
        val firestore = db ?: return
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("expenses").document(expenseId.toString())
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting expense from Firestore: ${e.message}", e)
        }
    }

    suspend fun saveCustomer(workspaceId: String, customer: CustomerEntity, uid: String?) {
        val firestore = db ?: return
        try {
            val docId = if (customer.id > 0) customer.id.toString() else IdGenerator.generateId().toString()
            firestore.collection("workspaces").document(workspaceId)
                .collection("customers").document(docId)
                .set(customer.toFirestoreMap(uid), SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving customer to Firestore: ${e.message}", e)
        }
    }

    suspend fun deleteCustomer(workspaceId: String, customerId: Long) {
        val firestore = db ?: return
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("customers").document(customerId.toString())
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting customer from Firestore: ${e.message}", e)
        }
    }

    suspend fun saveTractor(workspaceId: String, tractor: TractorEntity, uid: String?) {
        val firestore = db ?: return
        try {
            val docId = if (tractor.id > 0) tractor.id.toString() else IdGenerator.generateId().toString()
            firestore.collection("workspaces").document(workspaceId)
                .collection("tractors").document(docId)
                .set(tractor.toFirestoreMap(uid), SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving tractor to Firestore: ${e.message}", e)
        }
    }

    suspend fun deleteTractor(workspaceId: String, tractorId: Long) {
        val firestore = db ?: return
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("tractors").document(tractorId.toString())
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting tractor from Firestore: ${e.message}", e)
        }
    }

    suspend fun savePartner(workspaceId: String, partner: PartnerEntity, uid: String?) {
        val firestore = db ?: return
        try {
            val docId = if (partner.id > 0) partner.id.toString() else IdGenerator.generateId().toString()
            firestore.collection("workspaces").document(workspaceId)
                .collection("attendees").document(docId)
                .set(partner.toFirestoreMap(uid), SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving partner to Firestore: ${e.message}", e)
        }
    }

    suspend fun deletePartner(workspaceId: String, partnerId: Long) {
        val firestore = db ?: return
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("attendees").document(partnerId.toString())
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting partner from Firestore: ${e.message}", e)
        }
    }

    suspend fun saveWithdrawal(workspaceId: String, withdrawal: WithdrawalEntity, uid: String?) {
        val firestore = db ?: return
        try {
            val docId = if (withdrawal.id > 0) withdrawal.id.toString() else IdGenerator.generateId().toString()
            firestore.collection("workspaces").document(workspaceId)
                .collection("withdrawals").document(docId)
                .set(withdrawal.toFirestoreMap(uid), SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving withdrawal to Firestore: ${e.message}", e)
        }
    }

    suspend fun deleteWithdrawal(workspaceId: String, withdrawalId: Long) {
        val firestore = db ?: return
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("withdrawals").document(withdrawalId.toString())
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting withdrawal from Firestore: ${e.message}", e)
        }
    }

    suspend fun saveSettings(workspaceId: String, settings: AppSettingsEntity, uid: String?) {
        val firestore = db ?: return
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("settings").document("main")
                .set(settings.toFirestoreMap(uid), SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving settings to Firestore: ${e.message}", e)
        }
    }

    /**
     * Safe Migration: On first login with a new workspace, if local Room database has existing records,
     * upload them to the workspace without duplicating or overwriting newer cloud data.
     */
    suspend fun migrateLocalDataIfRequired(
        workspaceId: String,
        uid: String,
        database: AppDatabase
    ) {
        val firestore = db ?: return
        val prefs = context.getSharedPreferences("firestore_migration_prefs", Context.MODE_PRIVATE)
        val migrationKey = "migrated_ws_$workspaceId"
        if (prefs.getBoolean(migrationKey, false)) {
            Log.d(TAG, "Workspace $workspaceId already migrated.")
            return
        }

        try {
            val wsRef = firestore.collection("workspaces").document(workspaceId)
            val entriesSnap = wsRef.collection("entries").limit(1).get().await()
            val customersSnap = wsRef.collection("customers").limit(1).get().await()

            // If workspace already has entries or customers in cloud, do not upload local default records
            if (!entriesSnap.isEmpty || !customersSnap.isEmpty) {
                prefs.edit().putBoolean(migrationKey, true).apply()
                Log.d(TAG, "Remote workspace already has cloud data. Local seed migration skipped.")
                return
            }

            Log.d(TAG, "Migrating local Room data to new workspace: $workspaceId")

            val tractors = database.tractorDao().getAllTractors().firstOrNull() ?: emptyList()
            val customers = database.customerDao().getAllCustomers().firstOrNull() ?: emptyList()
            val jobs = database.jobEntryDao().getAllJobs().firstOrNull() ?: emptyList()
            val expenses = database.expenseDao().getAllExpenses().firstOrNull() ?: emptyList()
            val withdrawals = database.withdrawalDao().getAllWithdrawals().firstOrNull() ?: emptyList()
            val partners = database.partnerDao().getAllPartners().firstOrNull() ?: emptyList()
            val currentSettings = database.appSettingsDao().getSettingsOnce()

            val batch = firestore.batch()

            // 1. Tractors
            for (tractor in tractors) {
                val docId = if (tractor.id > 0) tractor.id.toString() else IdGenerator.generateId().toString()
                batch.set(wsRef.collection("tractors").document(docId), tractor.toFirestoreMap(uid), SetOptions.merge())
            }

            // 2. Customers
            for (customer in customers) {
                val docId = if (customer.id > 0) customer.id.toString() else IdGenerator.generateId().toString()
                batch.set(wsRef.collection("customers").document(docId), customer.toFirestoreMap(uid), SetOptions.merge())
            }

            // 3. Jobs
            for (job in jobs) {
                val docId = if (job.id > 0) job.id.toString() else IdGenerator.generateId().toString()
                batch.set(wsRef.collection("entries").document(docId), job.toFirestoreMap(uid), SetOptions.merge())
            }

            // 4. Expenses
            for (expense in expenses) {
                val docId = if (expense.id > 0) expense.id.toString() else IdGenerator.generateId().toString()
                batch.set(wsRef.collection("expenses").document(docId), expense.toFirestoreMap(uid), SetOptions.merge())
            }

            // 5. Withdrawals
            for (withdrawal in withdrawals) {
                val docId = if (withdrawal.id > 0) withdrawal.id.toString() else IdGenerator.generateId().toString()
                batch.set(wsRef.collection("withdrawals").document(docId), withdrawal.toFirestoreMap(uid), SetOptions.merge())
            }

            // 6. Partners / Attendees
            for (partner in partners) {
                val docId = if (partner.id > 0) partner.id.toString() else IdGenerator.generateId().toString()
                batch.set(wsRef.collection("attendees").document(docId), partner.toFirestoreMap(uid), SetOptions.merge())
            }

            // 7. Settings
            if (currentSettings != null) {
                batch.set(
                    wsRef.collection("settings").document("main"),
                    currentSettings.toFirestoreMap(uid),
                    SetOptions.merge()
                )
            }

            batch.commit().await()
            prefs.edit().putBoolean(migrationKey, true).apply()
            Log.d(TAG, "Migration completed successfully for workspace: $workspaceId")
        } catch (e: Exception) {
            Log.e(TAG, "Error during migration: ${e.message}", e)
        }
    }

    private fun parseLongId(idStr: String): Long {
        return idStr.toLongOrNull() ?: IdGenerator.generateId()
    }
}
