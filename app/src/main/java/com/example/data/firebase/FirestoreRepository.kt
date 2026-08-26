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
import com.google.firebase.firestore.FirebaseFirestoreException
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
     * Completes strict deterministic workspace bootstrap for an authenticated user.
     * Sequences:
     * 1. Load users/{uid}
     * 2. Resolve workspaceId (stored defaultWorkspaceId or canonical)
     * 3. Ensure workspaces/{workspaceId} exists
     * 4. Ensure workspaces/{workspaceId}/members/{uid} exists
     * 5. Persist and AWAIT users/{uid}.defaultWorkspaceId = workspaceId
     * 6. Read users/{uid} again and verify defaultWorkspaceId == workspaceId
     * 7. Return validated Workspace
     */
    suspend fun bootstrapWorkspaceForUser(user: UserProfile): Workspace {
        val firestore = db ?: throw IllegalStateException("FirebaseFirestore instance is null")
        val uid = user.uid
        if (uid.isBlank()) throw IllegalArgumentException("User UID is blank")

        var currentOperation = "reading_users"
        try {
            // 1. Read users/{uid}
            Log.d("TRAC_WORKSPACE", "reading users/$uid")
            currentOperation = "reading_users/$uid"
            val userDoc = firestore.collection("users").document(uid).get().await()

            var workspaceId: String? = null
            var existingWorkspaces: List<String> = emptyList()
            var userData: Map<String, Any?> = emptyMap()

            if (userDoc.exists() && userDoc.data != null) {
                userData = userDoc.data!!
                val userFromCloud = UserProfile.fromMap(userData)
                val stored = userFromCloud.defaultWorkspaceId?.ifBlank { null }
                if (!stored.isNullOrBlank()) {
                    workspaceId = stored
                    Log.d("TRAC_WORKSPACE", "stored defaultWorkspaceId=$workspaceId")
                }
                existingWorkspaces = userFromCloud.workspaces.filter { it.isNotBlank() }
            }

            // 2. Fallback to canonical workspace ID if no valid default workspace found
            if (workspaceId.isNullOrBlank()) {
                val canonicalWsId = "ws_${uid.replace(Regex("[^a-zA-Z0-9]"), "").take(16).ifBlank { "main" }}"
                workspaceId = canonicalWsId
                Log.d("TRAC_WORKSPACE", "no default workspace, canonical=$canonicalWsId")
            }

            val now = System.currentTimeMillis()

            // 3. Ensure workspaces/{workspaceId} exists
            currentOperation = "creating_verifying_workspaces/$workspaceId"
            Log.d("TRAC_FIRESTORE", "creating/verifying workspaces/$workspaceId")
            val wsDocRef = firestore.collection("workspaces").document(workspaceId)
            val wsDoc = wsDocRef.get().await()
            val workspace: Workspace

            if (wsDoc.exists() && wsDoc.data != null) {
                val existing = Workspace.fromMap(wsDoc.data!!)
                workspace = existing.copy(
                    workspaceId = workspaceId,
                    ownerUid = existing.ownerUid.ifBlank { uid },
                    updatedAt = now
                )
            } else {
                val workspaceName = if (!user.displayName.isNullOrBlank()) {
                    "${user.displayName}'s Tractor Services"
                } else {
                    "AIDHUNT Agri & Tractor Services"
                }
                workspace = Workspace(
                    workspaceId = workspaceId,
                    name = workspaceName,
                    ownerUid = uid,
                    createdAt = now,
                    updatedAt = now
                )
            }
            wsDocRef.set(workspace.toMap(), SetOptions.merge()).await()

            // Verify workspaces/{workspaceId}
            val verifiedWs = wsDocRef.get().await()
            if (!verifiedWs.exists()) {
                val err = "Verification failed: workspaces/$workspaceId does not exist after write"
                Log.e("TRAC_FIRESTORE", "FAILED operation=$currentOperation message=$err")
                throw IllegalStateException(err)
            }
            Log.d("TRAC_FIRESTORE", "verified workspaces/$workspaceId exists")

            // 4. Ensure workspaces/{workspaceId}/members/{uid} exists
            currentOperation = "creating_verifying_workspaces/$workspaceId/members/$uid"
            Log.d("TRAC_FIRESTORE", "creating/verifying workspaces/$workspaceId/members/$uid")
            val memberDocRef = wsDocRef.collection("members").document(uid)
            val memberDoc = memberDocRef.get().await()
            val member = if (memberDoc.exists() && memberDoc.data != null) {
                WorkspaceMember.fromMap(memberDoc.data!!)
            } else {
                WorkspaceMember(
                    uid = uid,
                    role = "owner",
                    status = "active",
                    joinedAt = now,
                    displayName = user.displayName ?: (userData["displayName"] as? String),
                    email = user.email ?: (userData["email"] as? String),
                    phoneNumber = user.phoneNumber ?: (userData["phoneNumber"] as? String)
                )
            }
            memberDocRef.set(member.toMap(), SetOptions.merge()).await()

            // Verify member doc
            val verifiedMember = memberDocRef.get().await()
            if (!verifiedMember.exists()) {
                val err = "Verification failed: workspaces/$workspaceId/members/$uid does not exist after write"
                Log.e("TRAC_FIRESTORE", "FAILED operation=$currentOperation message=$err")
                throw IllegalStateException(err)
            }
            Log.d("TRAC_FIRESTORE", "verified workspaces/$workspaceId/members/$uid exists")

            // 5. Persist and AWAIT: users/{uid}.defaultWorkspaceId = workspaceId
            currentOperation = "updating_users/$uid.defaultWorkspaceId=$workspaceId"
            Log.d("TRAC_FIRESTORE", "updating users/$uid.defaultWorkspaceId=$workspaceId")
            val updatedWorkspaces = (existingWorkspaces + workspaceId).distinct()
            val userUpdateMap = mutableMapOf<String, Any?>(
                "uid" to uid,
                "defaultWorkspaceId" to workspaceId,
                "workspaces" to updatedWorkspaces,
                "updatedAt" to now
            )
            val displayName = user.displayName ?: (userData["displayName"] as? String)
            if (displayName != null) userUpdateMap["displayName"] = displayName
            val email = user.email ?: (userData["email"] as? String)
            if (email != null) userUpdateMap["email"] = email
            val phone = user.phoneNumber ?: (userData["phoneNumber"] as? String)
            if (phone != null) userUpdateMap["phoneNumber"] = phone
            val photo = user.photoUrl ?: (userData["photoUrl"] as? String)
            if (photo != null) userUpdateMap["photoUrl"] = photo

            firestore.collection("users").document(uid)
                .set(userUpdateMap, SetOptions.merge())
                .await()

            // 6. READ users/{uid} again from Firestore and verify defaultWorkspaceId == workspaceId
            currentOperation = "verifying_users/$uid.defaultWorkspaceId"
            val verifyDoc = firestore.collection("users").document(uid).get().await()
            val verifiedDefaultWs = verifyDoc.getString("defaultWorkspaceId")
            if (verifiedDefaultWs != workspaceId) {
                val err = "Verification failed: expected defaultWorkspaceId=$workspaceId, but got $verifiedDefaultWs"
                Log.e("TRAC_FIRESTORE", "FAILED operation=$currentOperation code=VERIFICATION_FAILED message=$err")
                throw IllegalStateException(err)
            }

            Log.d("TRAC_FIRESTORE", "user workspace update SUCCESS")
            _currentWorkspace.value = workspace
            return workspace
        } catch (e: Exception) {
            val code = (e as? FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
            Log.e("TRAC_FIRESTORE", "FAILED operation=$currentOperation code=$code message=${e.message}", e)
            throw e
        }
    }

    /**
     * Resolves an existing workspace or creates a canonical one for the user deterministically.
     */
    suspend fun resolveOrCreateWorkspace(user: UserProfile): Workspace? {
        return try {
            bootstrapWorkspaceForUser(user)
        } catch (e: Exception) {
            Log.e(TAG, "resolveOrCreateWorkspace failed: ${e.message}", e)
            null
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
        onJobsUpdated: (List<JobEntryEntity>, String) -> Unit,
        onExpensesUpdated: (List<ExpenseEntity>, String) -> Unit,
        onCustomersUpdated: (List<CustomerEntity>, String) -> Unit,
        onTractorsUpdated: (List<TractorEntity>, String) -> Unit,
        onPartnersUpdated: (List<PartnerEntity>, String) -> Unit,
        onWithdrawalsUpdated: (List<WithdrawalEntity>, String) -> Unit,
        onSettingsUpdated: (Map<String, Any?>, String) -> Unit
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
                        doc.data?.let { jobEntryFromFirestoreMap(it, fallbackId = parseLongId(doc.id), fallbackWorkspaceId = workspaceId) }
                    }
                    onJobsUpdated(jobs, workspaceId)
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
                        doc.data?.let { expenseFromFirestoreMap(it, fallbackId = parseLongId(doc.id), fallbackWorkspaceId = workspaceId) }
                    }
                    onExpensesUpdated(expenses, workspaceId)
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
                        doc.data?.let { customerFromFirestoreMap(it, fallbackId = parseLongId(doc.id), fallbackWorkspaceId = workspaceId) }
                    }
                    onCustomersUpdated(customers, workspaceId)
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
                        doc.data?.let { tractorFromFirestoreMap(it, fallbackId = parseLongId(doc.id), fallbackWorkspaceId = workspaceId) }
                    }
                    onTractorsUpdated(tractors, workspaceId)
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
                        doc.data?.let { partnerFromFirestoreMap(it, fallbackId = parseLongId(doc.id), fallbackWorkspaceId = workspaceId) }
                    }
                    onPartnersUpdated(partners, workspaceId)
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
                        doc.data?.let { withdrawalFromFirestoreMap(it, fallbackId = parseLongId(doc.id), fallbackWorkspaceId = workspaceId) }
                    }
                    onWithdrawalsUpdated(withdrawals, workspaceId)
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
                    snapshot.data?.let { onSettingsUpdated(it, workspaceId) }
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
        val firestore = db ?: throw IllegalStateException("FirebaseFirestore instance is null")
        val docId = if (job.id > 0) job.id.toString() else IdGenerator.generateId().toString()
        val path = "workspaces/$workspaceId/entries/$docId"
        Log.d("TRAC_FIRESTORE", "writing $path with uid=$uid")
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("entries").document(docId)
                .set(job.toFirestoreMap(uid), SetOptions.merge())
                .await()
            Log.d("TRAC_FIRESTORE", "SUCCESS entryId=$docId")
        } catch (e: Exception) {
            val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
            Log.e("TRAC_FIRESTORE", "FAILED code=$code message=${e.message} path=$path", e)
            throw e
        }
    }

    suspend fun deleteJob(workspaceId: String, jobId: Long) {
        val firestore = db ?: throw IllegalStateException("FirebaseFirestore instance is null")
        val path = "workspaces/$workspaceId/entries/$jobId"
        Log.d("TRAC_FIRESTORE", "deleting $path")
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("entries").document(jobId.toString())
                .delete()
                .await()
            Log.d("TRAC_FIRESTORE", "SUCCESS deleted entryId=$jobId")
        } catch (e: Exception) {
            val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
            Log.e("TRAC_FIRESTORE", "FAILED code=$code message=${e.message} path=$path", e)
            throw e
        }
    }

    suspend fun saveExpense(workspaceId: String, expense: ExpenseEntity, uid: String?) {
        val firestore = db ?: throw IllegalStateException("FirebaseFirestore instance is null")
        val docId = if (expense.id > 0) expense.id.toString() else IdGenerator.generateId().toString()
        val path = "workspaces/$workspaceId/expenses/$docId"
        Log.d("TRAC_FIRESTORE", "writing $path with uid=$uid")
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("expenses").document(docId)
                .set(expense.toFirestoreMap(uid), SetOptions.merge())
                .await()
            Log.d("TRAC_FIRESTORE", "SUCCESS expenseId=$docId")
        } catch (e: Exception) {
            val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
            Log.e("TRAC_FIRESTORE", "FAILED code=$code message=${e.message} path=$path", e)
            throw e
        }
    }

    suspend fun deleteExpense(workspaceId: String, expenseId: Long) {
        val firestore = db ?: throw IllegalStateException("FirebaseFirestore instance is null")
        val path = "workspaces/$workspaceId/expenses/$expenseId"
        Log.d("TRAC_FIRESTORE", "deleting $path")
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("expenses").document(expenseId.toString())
                .delete()
                .await()
            Log.d("TRAC_FIRESTORE", "SUCCESS deleted expenseId=$expenseId")
        } catch (e: Exception) {
            val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
            Log.e("TRAC_FIRESTORE", "FAILED code=$code message=${e.message} path=$path", e)
            throw e
        }
    }

    suspend fun saveCustomer(workspaceId: String, customer: CustomerEntity, uid: String?) {
        val firestore = db ?: throw IllegalStateException("FirebaseFirestore instance is null")
        val docId = if (customer.id > 0) customer.id.toString() else IdGenerator.generateId().toString()
        val path = "workspaces/$workspaceId/customers/$docId"
        Log.d("TRAC_FIRESTORE", "writing $path with uid=$uid")
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("customers").document(docId)
                .set(customer.toFirestoreMap(uid), SetOptions.merge())
                .await()
            Log.d("TRAC_FIRESTORE", "SUCCESS customerId=$docId")
        } catch (e: Exception) {
            val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
            Log.e("TRAC_FIRESTORE", "FAILED code=$code message=${e.message} path=$path", e)
            throw e
        }
    }

    suspend fun deleteCustomer(workspaceId: String, customerId: Long) {
        val firestore = db ?: throw IllegalStateException("FirebaseFirestore instance is null")
        val path = "workspaces/$workspaceId/customers/$customerId"
        Log.d("TRAC_FIRESTORE", "deleting $path")
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("customers").document(customerId.toString())
                .delete()
                .await()
            Log.d("TRAC_FIRESTORE", "SUCCESS deleted customerId=$customerId")
        } catch (e: Exception) {
            val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
            Log.e("TRAC_FIRESTORE", "FAILED code=$code message=${e.message} path=$path", e)
            throw e
        }
    }

    suspend fun saveTractor(workspaceId: String, tractor: TractorEntity, uid: String?) {
        val firestore = db ?: throw IllegalStateException("FirebaseFirestore instance is null")
        val docId = if (tractor.id > 0) tractor.id.toString() else IdGenerator.generateId().toString()
        val path = "workspaces/$workspaceId/tractors/$docId"
        Log.d("TRAC_FIRESTORE", "writing $path with uid=$uid")
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("tractors").document(docId)
                .set(tractor.toFirestoreMap(uid), SetOptions.merge())
                .await()
            Log.d("TRAC_FIRESTORE", "SUCCESS tractorId=$docId")
        } catch (e: Exception) {
            val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
            Log.e("TRAC_FIRESTORE", "FAILED code=$code message=${e.message} path=$path", e)
            throw e
        }
    }

    suspend fun deleteTractor(workspaceId: String, tractorId: Long) {
        val firestore = db ?: throw IllegalStateException("FirebaseFirestore instance is null")
        val path = "workspaces/$workspaceId/tractors/$tractorId"
        Log.d("TRAC_FIRESTORE", "deleting $path")
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("tractors").document(tractorId.toString())
                .delete()
                .await()
            Log.d("TRAC_FIRESTORE", "SUCCESS deleted tractorId=$tractorId")
        } catch (e: Exception) {
            val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
            Log.e("TRAC_FIRESTORE", "FAILED code=$code message=${e.message} path=$path", e)
            throw e
        }
    }

    suspend fun savePartner(workspaceId: String, partner: PartnerEntity, uid: String?) {
        val firestore = db ?: throw IllegalStateException("FirebaseFirestore instance is null")
        val docId = if (partner.id > 0) partner.id.toString() else IdGenerator.generateId().toString()
        val path = "workspaces/$workspaceId/attendees/$docId"
        Log.d("TRAC_FIRESTORE", "writing $path with uid=$uid")
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("attendees").document(docId)
                .set(partner.toFirestoreMap(uid), SetOptions.merge())
                .await()
            Log.d("TRAC_FIRESTORE", "SUCCESS partnerId=$docId")
        } catch (e: Exception) {
            val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
            Log.e("TRAC_FIRESTORE", "FAILED code=$code message=${e.message} path=$path", e)
            throw e
        }
    }

    suspend fun deletePartner(workspaceId: String, partnerId: Long) {
        val firestore = db ?: throw IllegalStateException("FirebaseFirestore instance is null")
        val path = "workspaces/$workspaceId/attendees/$partnerId"
        Log.d("TRAC_FIRESTORE", "deleting $path")
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("attendees").document(partnerId.toString())
                .delete()
                .await()
            Log.d("TRAC_FIRESTORE", "SUCCESS deleted partnerId=$partnerId")
        } catch (e: Exception) {
            val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
            Log.e("TRAC_FIRESTORE", "FAILED code=$code message=${e.message} path=$path", e)
            throw e
        }
    }

    suspend fun saveWithdrawal(workspaceId: String, withdrawal: WithdrawalEntity, uid: String?) {
        val firestore = db ?: throw IllegalStateException("FirebaseFirestore instance is null")
        val docId = if (withdrawal.id > 0) withdrawal.id.toString() else IdGenerator.generateId().toString()
        val path = "workspaces/$workspaceId/withdrawals/$docId"
        Log.d("TRAC_FIRESTORE", "writing $path with uid=$uid")
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("withdrawals").document(docId)
                .set(withdrawal.toFirestoreMap(uid), SetOptions.merge())
                .await()
            Log.d("TRAC_FIRESTORE", "SUCCESS withdrawalId=$docId")
        } catch (e: Exception) {
            val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
            Log.e("TRAC_FIRESTORE", "FAILED code=$code message=${e.message} path=$path", e)
            throw e
        }
    }

    suspend fun deleteWithdrawal(workspaceId: String, withdrawalId: Long) {
        val firestore = db ?: throw IllegalStateException("FirebaseFirestore instance is null")
        val path = "workspaces/$workspaceId/withdrawals/$withdrawalId"
        Log.d("TRAC_FIRESTORE", "deleting $path")
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("withdrawals").document(withdrawalId.toString())
                .delete()
                .await()
            Log.d("TRAC_FIRESTORE", "SUCCESS deleted withdrawalId=$withdrawalId")
        } catch (e: Exception) {
            val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
            Log.e("TRAC_FIRESTORE", "FAILED code=$code message=${e.message} path=$path", e)
            throw e
        }
    }

    suspend fun saveSettings(workspaceId: String, settings: AppSettingsEntity, uid: String?) {
        val firestore = db ?: throw IllegalStateException("FirebaseFirestore instance is null")
        val path = "workspaces/$workspaceId/settings/main"
        Log.d("TRAC_FIRESTORE", "writing $path with uid=$uid")
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("settings").document("main")
                .set(settings.toFirestoreMap(uid), SetOptions.merge())
                .await()
            Log.d("TRAC_FIRESTORE", "SUCCESS settings written to $path")
        } catch (e: Exception) {
            val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
            Log.e("TRAC_FIRESTORE", "FAILED code=$code message=${e.message} path=$path", e)
            throw e
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
        val migrationKey = "migration_${uid}_${workspaceId}"
        if (prefs.getBoolean(migrationKey, false)) {
            Log.d(TAG, "Workspace $workspaceId for user $uid already migrated.")
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

            // Only retrieve records for this workspaceId or unassigned seed records ("")
            val tractors = database.tractorDao().getTractorsForWorkspace(workspaceId).firstOrNull()?.ifEmpty { null }
                ?: database.tractorDao().getTractorsForWorkspace("").firstOrNull() ?: emptyList()
            val customers = database.customerDao().getCustomersForWorkspace(workspaceId).firstOrNull()?.ifEmpty { null }
                ?: database.customerDao().getCustomersForWorkspace("").firstOrNull() ?: emptyList()
            val jobs = database.jobEntryDao().getJobsForWorkspace(workspaceId).firstOrNull()?.ifEmpty { null }
                ?: database.jobEntryDao().getJobsForWorkspace("").firstOrNull() ?: emptyList()
            val expenses = database.expenseDao().getExpensesForWorkspace(workspaceId).firstOrNull()?.ifEmpty { null }
                ?: database.expenseDao().getExpensesForWorkspace("").firstOrNull() ?: emptyList()
            val withdrawals = database.withdrawalDao().getWithdrawalsForWorkspace(workspaceId).firstOrNull()?.ifEmpty { null }
                ?: database.withdrawalDao().getWithdrawalsForWorkspace("").firstOrNull() ?: emptyList()
            val partners = database.partnerDao().getPartnersForWorkspace(workspaceId).firstOrNull()?.ifEmpty { null }
                ?: database.partnerDao().getPartnersForWorkspace("").firstOrNull() ?: emptyList()
            val currentSettings = database.appSettingsDao().getSettingsForWorkspaceOnce(workspaceId)
                ?: database.appSettingsDao().getSettingsForWorkspaceOnce("")

            val batch = firestore.batch()

            // 1. Tractors
            for (tractor in tractors) {
                val docId = if (tractor.id > 0) tractor.id.toString() else IdGenerator.generateId().toString()
                val scoped = tractor.copy(workspaceId = workspaceId)
                batch.set(wsRef.collection("tractors").document(docId), scoped.toFirestoreMap(uid), SetOptions.merge())
                database.tractorDao().insertTractor(scoped)
            }

            // 2. Customers
            for (customer in customers) {
                val docId = if (customer.id > 0) customer.id.toString() else IdGenerator.generateId().toString()
                val scoped = customer.copy(workspaceId = workspaceId)
                batch.set(wsRef.collection("customers").document(docId), scoped.toFirestoreMap(uid), SetOptions.merge())
                database.customerDao().insertCustomer(scoped)
            }

            // 3. Jobs
            for (job in jobs) {
                val docId = if (job.id > 0) job.id.toString() else IdGenerator.generateId().toString()
                val scoped = job.copy(workspaceId = workspaceId)
                batch.set(wsRef.collection("entries").document(docId), scoped.toFirestoreMap(uid), SetOptions.merge())
                database.jobEntryDao().insertJob(scoped)
            }

            // 4. Expenses
            for (expense in expenses) {
                val docId = if (expense.id > 0) expense.id.toString() else IdGenerator.generateId().toString()
                val scoped = expense.copy(workspaceId = workspaceId)
                batch.set(wsRef.collection("expenses").document(docId), scoped.toFirestoreMap(uid), SetOptions.merge())
                database.expenseDao().insertExpense(scoped)
            }

            // 5. Withdrawals
            for (withdrawal in withdrawals) {
                val docId = if (withdrawal.id > 0) withdrawal.id.toString() else IdGenerator.generateId().toString()
                val scoped = withdrawal.copy(workspaceId = workspaceId)
                batch.set(wsRef.collection("withdrawals").document(docId), scoped.toFirestoreMap(uid), SetOptions.merge())
                database.withdrawalDao().insertWithdrawal(scoped)
            }

            // 6. Partners / Attendees
            for (partner in partners) {
                val docId = if (partner.id > 0) partner.id.toString() else IdGenerator.generateId().toString()
                val scoped = partner.copy(workspaceId = workspaceId)
                batch.set(wsRef.collection("attendees").document(docId), scoped.toFirestoreMap(uid), SetOptions.merge())
                database.partnerDao().insertPartner(scoped)
            }

            // 7. Settings
            if (currentSettings != null) {
                val scopedSettings = currentSettings.copy(workspaceId = workspaceId)
                batch.set(
                    wsRef.collection("settings").document("main"),
                    scopedSettings.toFirestoreMap(uid),
                    SetOptions.merge()
                )
                database.appSettingsDao().insertOrUpdateSettings(scopedSettings)
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
