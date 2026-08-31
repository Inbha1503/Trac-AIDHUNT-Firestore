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

    // Real-time listener registrations per workspace (workspaceId -> Map of listener registrations)
    private val activeWorkspaceListeners = mutableMapOf<String, MutableList<ListenerRegistration>>()

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
                    ""
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

            // Ensure owner's collaboration group exists: collaborationGroups/{workspaceId}
            ensureCollaborationGroup(workspaceId, uid)

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

    private suspend fun ensureCollaborationGroup(workspaceId: String, ownerUid: String) {
        val firestore = db ?: return
        try {
            val groupRef = firestore.collection("collaborationGroups").document(workspaceId)
            val groupSnap = groupRef.get().await()
            val now = System.currentTimeMillis()
            if (!groupSnap.exists()) {
                val group = CollaborationGroup(
                    groupId = workspaceId,
                    ownerUid = ownerUid,
                    ownerWorkspaceId = workspaceId,
                    createdAt = now,
                    updatedAt = now
                )
                groupRef.set(group.toMap(), SetOptions.merge()).await()
            }

            // Also ensure owner is registered as active member in the group
            val ownerMemberRef = groupRef.collection("members").document(ownerUid)
            val ownerMemberSnap = ownerMemberRef.get().await()
            if (!ownerMemberSnap.exists()) {
                val member = CollaborationGroupMember(
                    uid = ownerUid,
                    workspaceId = workspaceId,
                    role = "owner",
                    status = "active",
                    joinedAt = now
                )
                ownerMemberRef.set(member.toMap(), SetOptions.merge()).await()
            }

            // Also ensure user's discovery index exists
            val userGroupIndexRef = firestore.collection("userCollaborationGroups").document(ownerUid)
                .collection("groups").document(workspaceId)
            userGroupIndexRef.set(
                UserCollaborationGroupIndex(
                    groupId = workspaceId,
                    ownerUid = ownerUid,
                    ownerWorkspaceId = workspaceId,
                    role = "owner",
                    status = "active",
                    joinedAt = now
                ).toMap(),
                SetOptions.merge()
            ).await()
        } catch (e: Exception) {
            Log.w(TAG, "ensureCollaborationGroup deferred: ${e.message}")
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
     * Registers real-time Firestore listeners for a set of visible workspaces.
     * Listens to entries, expenses, customers, tractors, attendees, withdrawals, settings for all given workspaces.
     */
    fun startRealtimeListenersForWorkspaces(
        workspaceIds: Set<String>,
        onJobsUpdated: (List<JobEntryEntity>, String) -> Unit,
        onExpensesUpdated: (List<ExpenseEntity>, String) -> Unit,
        onCustomersUpdated: (List<CustomerEntity>, String) -> Unit,
        onTractorsUpdated: (List<TractorEntity>, String) -> Unit,
        onPartnersUpdated: (List<PartnerEntity>, String) -> Unit,
        onWithdrawalsUpdated: (List<WithdrawalEntity>, String) -> Unit,
        onSettingsUpdated: (Map<String, Any?>, String) -> Unit
    ) {
        val firestore = db ?: return
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""

        // Stop listeners for workspaces no longer in the set
        val existingWorkspaces = activeWorkspaceListeners.keys.toSet()
        val toRemove = existingWorkspaces - workspaceIds
        for (wsId in toRemove) {
            activeWorkspaceListeners.remove(wsId)?.forEach { it.remove() }
            Log.d("TRAC_FIRESTORE", "STOP_LISTEN workspace=$wsId")
        }

        // Add listeners for new workspaces
        val toAdd = workspaceIds - existingWorkspaces
        for (workspaceId in toAdd) {
            if (workspaceId.isBlank()) continue
            val wsRef = firestore.collection("workspaces").document(workspaceId)
            val regList = mutableListOf<ListenerRegistration>()

            Log.d("TRAC_FIRESTORE", "LISTEN uid=$currentUid workspace=$workspaceId collection=entries")
            Log.d("TRAC_FIRESTORE", "LISTEN uid=$currentUid workspace=$workspaceId collection=expenses")
            Log.d("TRAC_FIRESTORE", "LISTEN uid=$currentUid workspace=$workspaceId collection=customers")
            Log.d("TRAC_FIRESTORE", "LISTEN uid=$currentUid workspace=$workspaceId collection=tractors")
            Log.d("TRAC_FIRESTORE", "LISTEN uid=$currentUid workspace=$workspaceId collection=attendees")
            Log.d("TRAC_FIRESTORE", "LISTEN uid=$currentUid workspace=$workspaceId collection=withdrawals")
            Log.d("TRAC_FIRESTORE", "LISTEN uid=$currentUid workspace=$workspaceId collection=settings")

            // 1. Entries Listener
            val regEntries = wsRef.collection("entries")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Entries listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        Log.d("TRAC_FIRESTORE", "SNAPSHOT workspace=$workspaceId collection=entries count=${snapshot.documents.size}")
                        val jobs = snapshot.documents.mapNotNull { doc ->
                            doc.data?.let { jobEntryFromFirestoreMap(it, fallbackId = parseLongId(doc.id), fallbackWorkspaceId = workspaceId) }
                        }
                        onJobsUpdated(jobs, workspaceId)
                    }
                }
            regList.add(regEntries)

            // 2. Expenses Listener
            val regExpenses = wsRef.collection("expenses")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Expenses listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        Log.d("TRAC_FIRESTORE", "SNAPSHOT workspace=$workspaceId collection=expenses count=${snapshot.documents.size}")
                        val expenses = snapshot.documents.mapNotNull { doc ->
                            doc.data?.let { expenseFromFirestoreMap(it, fallbackId = parseLongId(doc.id), fallbackWorkspaceId = workspaceId) }
                        }
                        onExpensesUpdated(expenses, workspaceId)
                    }
                }
            regList.add(regExpenses)

            // 3. Customers Listener
            val regCustomers = wsRef.collection("customers")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Customers listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        Log.d("TRAC_FIRESTORE", "SNAPSHOT workspace=$workspaceId collection=customers count=${snapshot.documents.size}")
                        val customers = snapshot.documents.mapNotNull { doc ->
                            doc.data?.let { customerFromFirestoreMap(it, fallbackId = parseLongId(doc.id), fallbackWorkspaceId = workspaceId) }
                        }
                        onCustomersUpdated(customers, workspaceId)
                    }
                }
            regList.add(regCustomers)

            // 4. Tractors Listener
            val regTractors = wsRef.collection("tractors")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Tractors listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        Log.d("TRAC_FIRESTORE", "SNAPSHOT workspace=$workspaceId collection=tractors count=${snapshot.documents.size}")
                        val tractors = snapshot.documents.mapNotNull { doc ->
                            doc.data?.let { tractorFromFirestoreMap(it, fallbackId = parseLongId(doc.id), fallbackWorkspaceId = workspaceId) }
                        }
                        onTractorsUpdated(tractors, workspaceId)
                    }
                }
            regList.add(regTractors)

            // 5. Attendees (Partners / Operators) Listener
            val regAttendees = wsRef.collection("attendees")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Attendees listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        Log.d("TRAC_FIRESTORE", "SNAPSHOT workspace=$workspaceId collection=attendees count=${snapshot.documents.size}")
                        val partners = snapshot.documents.mapNotNull { doc ->
                            doc.data?.let { partnerFromFirestoreMap(it, fallbackId = parseLongId(doc.id), fallbackWorkspaceId = workspaceId) }
                        }
                        onPartnersUpdated(partners, workspaceId)
                    }
                }
            regList.add(regAttendees)

            // 6. Withdrawals Listener
            val regWithdrawals = wsRef.collection("withdrawals")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Withdrawals listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        Log.d("TRAC_FIRESTORE", "SNAPSHOT workspace=$workspaceId collection=withdrawals count=${snapshot.documents.size}")
                        val withdrawals = snapshot.documents.mapNotNull { doc ->
                            doc.data?.let { withdrawalFromFirestoreMap(it, fallbackId = parseLongId(doc.id), fallbackWorkspaceId = workspaceId) }
                        }
                        onWithdrawalsUpdated(withdrawals, workspaceId)
                    }
                }
            regList.add(regWithdrawals)

            // 7. Settings Listener
            val regSettings = wsRef.collection("settings").document("main")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Settings listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        Log.d("TRAC_FIRESTORE", "SNAPSHOT workspace=$workspaceId collection=settings exists=true")
                        snapshot.data?.let { onSettingsUpdated(it, workspaceId) }
                    }
                }
            regList.add(regSettings)

            activeWorkspaceListeners[workspaceId] = regList
            Log.d(TAG, "All Firestore snapshot listeners started for workspace: $workspaceId")
        }
    }

    /**
     * Backward compatibility wrapper for single workspace listener.
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
        startRealtimeListenersForWorkspaces(
            workspaceIds = if (workspaceId.isBlank()) emptySet() else setOf(workspaceId),
            onJobsUpdated = onJobsUpdated,
            onExpensesUpdated = onExpensesUpdated,
            onCustomersUpdated = onCustomersUpdated,
            onTractorsUpdated = onTractorsUpdated,
            onPartnersUpdated = onPartnersUpdated,
            onWithdrawalsUpdated = onWithdrawalsUpdated,
            onSettingsUpdated = onSettingsUpdated
        )
    }

    /**
     * Cleanly stops and unregisters all real-time listeners across all workspaces.
     */
    fun stopRealtimeListeners() {
        activeWorkspaceListeners.values.forEach { list ->
            list.forEach { it.remove() }
        }
        activeWorkspaceListeners.clear()
        Log.d(TAG, "All Firestore snapshot listeners stopped.")
    }

    // --- Direct Cloud Operations ---

    suspend fun saveJobEntry(workspaceId: String, job: JobEntryEntity, uid: String?) {
        val firestore = db ?: throw IllegalStateException("FirebaseFirestore instance is null")
        val docId = if (job.id > 0) job.id.toString() else IdGenerator.generateId().toString()
        val path = "workspaces/$workspaceId/entries/$docId"
        val currentAuthUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: uid
        Log.d("TRAC_PARTNER", "SHARED_WRITE authenticatedUid=$currentAuthUid workspace=$workspaceId entryId=$docId")
        Log.d("TRAC_FIRESTORE", "writing $path with uid=$currentAuthUid")
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("entries").document(docId)
                .set(job.toFirestoreMap(currentAuthUid), SetOptions.merge())
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
        val currentAuthUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: uid
        Log.d("TRAC_PARTNER", "SHARED_WRITE authenticatedUid=$currentAuthUid workspace=$workspaceId expenseId=$docId")
        Log.d("TRAC_FIRESTORE", "writing $path with uid=$currentAuthUid")
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("expenses").document(docId)
                .set(expense.toFirestoreMap(currentAuthUid), SetOptions.merge())
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
        val currentAuthUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: uid
        Log.d("TRAC_PARTNER", "SHARED_WRITE authenticatedUid=$currentAuthUid workspace=$workspaceId customerId=$docId")
        Log.d("TRAC_FIRESTORE", "writing $path with uid=$currentAuthUid")
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("customers").document(docId)
                .set(customer.toFirestoreMap(currentAuthUid), SetOptions.merge())
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
        val currentAuthUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: uid
        Log.d("TRAC_PARTNER", "SHARED_WRITE authenticatedUid=$currentAuthUid workspace=$workspaceId tractorId=$docId")
        Log.d("TRAC_FIRESTORE", "writing $path with uid=$currentAuthUid")
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("tractors").document(docId)
                .set(tractor.toFirestoreMap(currentAuthUid), SetOptions.merge())
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
        val currentAuthUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: uid
        Log.d("TRAC_FIRESTORE", "writing $path with uid=$currentAuthUid")
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("attendees").document(docId)
                .set(partner.toFirestoreMap(currentAuthUid), SetOptions.merge())
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
        val currentAuthUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: uid
        Log.d("TRAC_PARTNER", "SHARED_WRITE authenticatedUid=$currentAuthUid workspace=$workspaceId withdrawalId=$docId")
        Log.d("TRAC_FIRESTORE", "writing $path with uid=$currentAuthUid")
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("withdrawals").document(docId)
                .set(withdrawal.toFirestoreMap(currentAuthUid), SetOptions.merge())
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

            // Only retrieve records for this workspaceId
            val tractors = database.tractorDao().getTractorsForWorkspace(workspaceId).firstOrNull() ?: emptyList()
            val customers = database.customerDao().getCustomersForWorkspace(workspaceId).firstOrNull() ?: emptyList()
            val jobs = database.jobEntryDao().getJobsForWorkspace(workspaceId).firstOrNull() ?: emptyList()
            val expenses = database.expenseDao().getExpensesForWorkspace(workspaceId).firstOrNull() ?: emptyList()
            val withdrawals = database.withdrawalDao().getWithdrawalsForWorkspace(workspaceId).firstOrNull() ?: emptyList()
            val partners = database.partnerDao().getPartnersForWorkspace(workspaceId).firstOrNull() ?: emptyList()
            val currentSettings = database.appSettingsDao().getSettingsForWorkspaceOnce(workspaceId)

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

    // --- Direct Phone Account Directory & Direct Partner Membership ---

    suspend fun lookupPhoneInDirectory(rawPhone: String): String? {
        val firestore = db ?: return null
        val formatted = normalizePhoneNumber(rawPhone)
        if (formatted.isBlank()) return null
        val docPath = "phoneDirectory/$formatted"
        return try {
            val doc = firestore.collection("phoneDirectory").document(formatted).get().await()
            if (doc.exists() && doc.data != null) {
                val partnerUid = doc.getString("uid")
                if (!partnerUid.isNullOrBlank()) {
                    Log.d("TRAC_PARTNER", "PHONE_LOOKUP phone=$formatted uid=$partnerUid")
                    partnerUid
                } else {
                    Log.w("TRAC_PARTNER", "PHONE_LOOKUP phone=$formatted NOT_FOUND")
                    null
                }
            } else {
                Log.w("TRAC_PARTNER", "PHONE_LOOKUP phone=$formatted NOT_FOUND")
                null
            }
        } catch (e: Exception) {
            Log.e("TRAC_PARTNER", "PHONE_LOOKUP phone=$formatted FAILED: ${e.message}", e)
            null
        }
    }

    suspend fun addPartnerMemberDirectly(
        workspaceId: String,
        partnerUid: String,
        partnerName: String,
        partnerPhone: String,
        role: String,
        ownerUid: String,
        businessName: String
    ): Result<Unit> {
        val firestore = db ?: return Result.failure(IllegalStateException("Firestore is not initialized"))
        val now = System.currentTimeMillis()
        val formattedPhone = normalizePhoneNumber(partnerPhone)

        return try {
            val batch = firestore.batch()

            // 1. Create workspace member document: workspaces/{workspaceId}/members/{partnerUid}
            val memberRef = firestore.collection("workspaces").document(workspaceId)
                .collection("members").document(partnerUid)
            val member = WorkspaceMember(
                uid = partnerUid,
                role = role.ifBlank { "partner" },
                status = "active",
                phoneNumber = formattedPhone,
                joinedAt = now,
                addedByUid = ownerUid,
                invitedByUid = ownerUid,
                displayName = partnerName.ifBlank { "Partner" }
            )
            batch.set(memberRef, member.toMap(), SetOptions.merge())

            // 2. Create user workspace membership discovery index: userWorkspaceMemberships/{partnerUid}/workspaces/{workspaceId}
            val membershipIndexRef = firestore.collection("userWorkspaceMemberships").document(partnerUid)
                .collection("workspaces").document(workspaceId)
            val membershipData = mapOf(
                "workspaceId" to workspaceId,
                "ownerUid" to ownerUid,
                "role" to role.ifBlank { "partner" },
                "status" to "active",
                "joinedAt" to now,
                "workspaceName" to businessName.ifBlank { "AIDHUNT Tractor Fleet" }
            )
            batch.set(membershipIndexRef, membershipData, SetOptions.merge())

            // 3. Ensure PartnerEntity is created under workspace attendees for business/operator management
            val partnerAttendeeRef = firestore.collection("workspaces").document(workspaceId)
                .collection("attendees").document(partnerUid)
            val partnerAttendeeId = parseLongId(partnerUid)
            val partnerAttendee = PartnerEntity(
                id = partnerAttendeeId,
                workspaceId = workspaceId,
                name = partnerName.ifBlank { "Partner" },
                phone = formattedPhone,
                role = role.ifBlank { "Partner" },
                avatarColorHex = "#1E4D2B",
                isCurrentActive = false
            )
            batch.set(partnerAttendeeRef, partnerAttendee.toFirestoreMap(ownerUid), SetOptions.merge())

            batch.commit().await()
            Log.d("TRAC_PARTNER", "MEMBER_WRITE workspace=$workspaceId partnerUid=$partnerUid SUCCESS")
            Log.d("TRAC_PARTNER", "INDEX_WRITE workspace=$workspaceId partnerUid=$partnerUid SUCCESS")
            Result.success(Unit)
        } catch (e: Exception) {
            val code = (e as? FirebaseFirestoreException)?.code?.name ?: "ERROR"
            Log.e("TRAC_PARTNER", "MEMBER_WRITE workspace=$workspaceId partnerUid=$partnerUid FAILED code=$code message=${e.message}", e)
            Log.e("TRAC_PARTNER", "INDEX_WRITE workspace=$workspaceId partnerUid=$partnerUid FAILED code=$code message=${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getUserWorkspaceMemberships(uid: String): List<Map<String, Any?>> {
        val firestore = db ?: return emptyList()
        if (uid.isBlank()) return emptyList()

        return try {
            val snapshot = firestore.collection("userWorkspaceMemberships").document(uid)
                .collection("workspaces").get().await()
            val list = snapshot.documents.mapNotNull { it.data }
            for (item in list) {
                val wsId = item["workspaceId"] as? String ?: ""
                Log.d("TRAC_WORKSPACE", "MEMBERSHIP uid=$uid sharedWorkspace=$wsId")
            }
            list
        } catch (e: Exception) {
            Log.w("TRAC_WORKSPACE", "Error fetching userWorkspaceMemberships for $uid: ${e.message}")
            emptyList()
        }
    }

    fun listenToUserMemberships(uid: String, onWorkspacesChanged: (List<String>) -> Unit): ListenerRegistration? {
        val firestore = db ?: return null
        if (uid.isBlank()) return null

        return try {
            firestore.collection("userWorkspaceMemberships").document(uid)
                .collection("workspaces")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w("TRAC_WORKSPACE", "Listener error on userWorkspaceMemberships: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val workspaceIds = snapshot.documents.map { it.id }.filter { it.isNotBlank() }
                        Log.d("TRAC_WORKSPACE", "Membership listener update for $uid: $workspaceIds")
                        onWorkspacesChanged(workspaceIds)
                    }
                }
        } catch (e: Exception) {
            Log.w("TRAC_WORKSPACE", "Failed to register membership listener: ${e.message}")
            null
        }
    }

    suspend fun removePartnerFromWorkspace(
        workspaceId: String,
        partnerUid: String?,
        partnerPhone: String
    ) {
        val firestore = db ?: return
        try {
            // 1. Remove member from workspace
            if (!partnerUid.isNullOrBlank()) {
                val memberRef = firestore.collection("workspaces").document(workspaceId)
                    .collection("members").document(partnerUid)
                memberRef.delete().await()

                // Delete userWorkspaceMemberships discovery index
                try {
                    firestore.collection("userWorkspaceMemberships").document(partnerUid)
                        .collection("workspaces").document(workspaceId).delete().await()
                } catch (e: Exception) {
                    Log.w("TRAC_PARTNER", "Error deleting membership index: ${e.message}")
                }
            }

            // 2. Remove partner entity from attendees collection
            val attendeesSnap = firestore.collection("workspaces").document(workspaceId)
                .collection("attendees").get().await()
            val cleanPhone = partnerPhone.filter { it.isDigit() }.takeLast(10)
            for (doc in attendeesSnap.documents) {
                val phone = doc.getString("phone") ?: ""
                val clean = phone.filter { it.isDigit() }.takeLast(10)
                if (doc.id == partnerUid || (cleanPhone.isNotBlank() && clean == cleanPhone)) {
                    doc.reference.delete().await()
                }
            }
            Log.d("TRAC_PARTNER", "Partner removed safely from $workspaceId")
        } catch (e: Exception) {
            Log.e("TRAC_PARTNER", "Error removing partner from workspace: ${e.message}", e)
        }
    }

    suspend fun getWorkspaceDetails(workspaceId: String): Workspace? {
        val firestore = db ?: return null
        return try {
            val doc = firestore.collection("workspaces").document(workspaceId).get().await()
            if (doc.exists() && doc.data != null) {
                Workspace.fromMap(doc.data!!)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateUserDefaultWorkspace(uid: String, workspaceId: String) {
        val firestore = db ?: return
        try {
            firestore.collection("users").document(uid).set(
                mapOf(
                    "defaultWorkspaceId" to workspaceId,
                    "updatedAt" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            ).await()
        } catch (e: Exception) {
            Log.w(TAG, "Error updating defaultWorkspaceId: ${e.message}")
        }
    }

    suspend fun getWorkspaceMembers(workspaceId: String): List<WorkspaceMember> {
        val firestore = db ?: return emptyList()
        return try {
            val snapshot = firestore.collection("workspaces").document(workspaceId)
                .collection("members")
                .get()
                .await()
            val list = mutableListOf<WorkspaceMember>()
            for (doc in snapshot.documents) {
                val data = doc.data ?: continue
                var member = WorkspaceMember.fromMap(data)
                if (member.displayName.isNullOrBlank() && member.uid.isNotBlank()) {
                    try {
                        val userDoc = firestore.collection("users").document(member.uid).get().await()
                        val uName = userDoc.getString("displayName")
                        if (!uName.isNullOrBlank()) {
                            member = member.copy(displayName = uName)
                        }
                    } catch (_: Exception) {}
                }
                list.add(member)
            }
            list
        } catch (e: Exception) {
            Log.w("TRAC_PARTNER", "Error getting members for $workspaceId: ${e.message}")
            emptyList()
        }
    }

    // --- Collaboration Groups & Multi-Workspace Discovery ---

    suspend fun getUserCollaborationGroupIds(uid: String): List<String> {
        val firestore = db ?: return emptyList()
        if (uid.isBlank()) return emptyList()

        return try {
            val snap = firestore.collection("userCollaborationGroups").document(uid)
                .collection("groups").get().await()
            snap.documents.map { it.id }.filter { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w(TAG, "Error getting userCollaborationGroupIds for $uid: ${e.message}")
            emptyList()
        }
    }

    fun listenToUserCollaborationGroups(uid: String, onGroupsChanged: (List<String>) -> Unit): ListenerRegistration? {
        val firestore = db ?: return null
        if (uid.isBlank()) return null

        return try {
            firestore.collection("userCollaborationGroups").document(uid)
                .collection("groups")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Error in userCollaborationGroups listener: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val groupIds = snapshot.documents.map { it.id }.filter { it.isNotBlank() }
                        onGroupsChanged(groupIds)
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register userCollaborationGroups listener: ${e.message}")
            null
        }
    }

    suspend fun getWorkspacesInCollaborationGroup(groupId: String): List<String> {
        val firestore = db ?: return listOf(groupId)
        if (groupId.isBlank()) return emptyList()

        return try {
            val snap = firestore.collection("collaborationGroups").document(groupId)
                .collection("members").get().await()
            val workspaceIds = snap.documents.mapNotNull { doc ->
                doc.getString("workspaceId")?.ifBlank { null }
            }.filter { it.isNotBlank() }
            if (workspaceIds.isNotEmpty()) workspaceIds.distinct() else listOf(groupId)
        } catch (e: Exception) {
            Log.w(TAG, "Error getting workspaces for group $groupId: ${e.message}")
            listOf(groupId)
        }
    }

    fun listenToCollaborationGroupMembers(groupId: String, onWorkspacesChanged: (List<String>) -> Unit): ListenerRegistration? {
        val firestore = db ?: return null
        if (groupId.isBlank()) return null

        return try {
            firestore.collection("collaborationGroups").document(groupId)
                .collection("members")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Error in collaboration group members listener for $groupId: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val workspaceIds = snapshot.documents.mapNotNull { doc ->
                            doc.getString("workspaceId")?.ifBlank { null }
                        }.filter { it.isNotBlank() }
                        onWorkspacesChanged(if (workspaceIds.isNotEmpty()) workspaceIds.distinct() else listOf(groupId))
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register collaboration group members listener: ${e.message}")
            null
        }
    }

    suspend fun addPartnerToCollaborationGroup(
        ownerWorkspaceId: String,
        ownerUid: String,
        partnerUid: String,
        partnerWorkspaceId: String,
        partnerPhone: String? = null,
        partnerDisplayName: String? = null
    ): Result<Unit> {
        val firestore = db ?: return Result.failure(IllegalStateException("Firestore is not initialized"))
        val now = System.currentTimeMillis()

        return try {
            val batch = firestore.batch()

            // 1. collaborationGroups/{ownerWorkspaceId}/members/{partnerUid}
            val memberRef = firestore.collection("collaborationGroups").document(ownerWorkspaceId)
                .collection("members").document(partnerUid)
            val groupMember = CollaborationGroupMember(
                uid = partnerUid,
                workspaceId = partnerWorkspaceId,
                role = "partner",
                status = "active",
                joinedAt = now,
                phoneNumber = partnerPhone,
                displayName = partnerDisplayName
            )
            batch.set(memberRef, groupMember.toMap(), SetOptions.merge())

            // 2. userCollaborationGroups/{partnerUid}/groups/{ownerWorkspaceId}
            val partnerIndexRef = firestore.collection("userCollaborationGroups").document(partnerUid)
                .collection("groups").document(ownerWorkspaceId)
            val partnerIndex = UserCollaborationGroupIndex(
                groupId = ownerWorkspaceId,
                ownerUid = ownerUid,
                ownerWorkspaceId = ownerWorkspaceId,
                role = "partner",
                status = "active",
                joinedAt = now
            )
            batch.set(partnerIndexRef, partnerIndex.toMap(), SetOptions.merge())

            batch.commit().await()
            Log.d("TRAC_PARTNER", "COLLABORATION_GROUP_LINK ownerWs=$ownerWorkspaceId partnerUid=$partnerUid partnerWs=$partnerWorkspaceId SUCCESS")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("TRAC_PARTNER", "COLLABORATION_GROUP_LINK failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun removePartnerFromCollaborationGroup(
        ownerWorkspaceId: String,
        partnerUid: String
    ) {
        val firestore = db ?: return
        try {
            val batch = firestore.batch()
            val memberRef = firestore.collection("collaborationGroups").document(ownerWorkspaceId)
                .collection("members").document(partnerUid)
            batch.delete(memberRef)

            val partnerIndexRef = firestore.collection("userCollaborationGroups").document(partnerUid)
                .collection("groups").document(ownerWorkspaceId)
            batch.delete(partnerIndexRef)

            batch.commit().await()
            Log.d("TRAC_PARTNER", "COLLABORATION_GROUP_UNLINK ownerWs=$ownerWorkspaceId partnerUid=$partnerUid SUCCESS")
        } catch (e: Exception) {
            Log.w("TRAC_PARTNER", "Error unlinking from collaboration group: ${e.message}")
        }
    }

    suspend fun savePendingPartnerPhone(
        groupId: String,
        normalizedPhone: String,
        displayName: String,
        role: String,
        ownerUid: String
    ): Result<Unit> {
        val firestore = db ?: return Result.failure(IllegalStateException("Firestore is not initialized"))
        val formattedPhone = normalizePhoneNumber(normalizedPhone)
        if (formattedPhone.isBlank() || groupId.isBlank()) {
            return Result.failure(IllegalArgumentException("Invalid phone or groupId"))
        }

        return try {
            val pendingRef = firestore.collection("collaborationGroups").document(groupId)
                .collection("pendingPhones").document(formattedPhone)
            val pendingData = PendingPartnerPhone(
                normalizedPhone = formattedPhone,
                displayName = displayName.trim(),
                role = role.trim().ifBlank { "partner" },
                addedByUid = ownerUid,
                groupId = groupId,
                createdAt = System.currentTimeMillis(),
                status = "waiting_for_registration"
            )
            pendingRef.set(pendingData.toMap(), SetOptions.merge()).await()
            Log.d("TRAC_PARTNER", "PENDING_PHONE_SAVED group=$groupId phone=$formattedPhone SUCCESS")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("TRAC_PARTNER", "PENDING_PHONE_SAVED failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun deletePendingPartnerPhone(
        groupId: String,
        normalizedPhone: String
    ) {
        val firestore = db ?: return
        val formattedPhone = normalizePhoneNumber(normalizedPhone)
        if (formattedPhone.isBlank() || groupId.isBlank()) return
        try {
            firestore.collection("collaborationGroups").document(groupId)
                .collection("pendingPhones").document(formattedPhone)
                .delete().await()
            Log.d("TRAC_PARTNER", "PENDING_PHONE_DELETED group=$groupId phone=$formattedPhone")
        } catch (e: Exception) {
            Log.w("TRAC_PARTNER", "Failed to delete pending phone: ${e.message}")
        }
    }

    suspend fun getPendingPartnerPhones(groupId: String): List<PendingPartnerPhone> {
        val firestore = db ?: return emptyList()
        if (groupId.isBlank()) return emptyList()
        return try {
            val snap = firestore.collection("collaborationGroups").document(groupId)
                .collection("pendingPhones")
                .whereEqualTo("status", "waiting_for_registration")
                .get().await()
            snap.documents.mapNotNull { doc ->
                doc.data?.let { PendingPartnerPhone.fromMap(it) }
            }
        } catch (e: Exception) {
            Log.w("TRAC_PARTNER", "Failed to get pending phones for $groupId: ${e.message}")
            emptyList()
        }
    }

    suspend fun findAndConnectPendingPartnerLinks(
        userUid: String,
        verifiedPhone: String,
        userWorkspaceId: String,
        userDisplayName: String?
    ): List<String> {
        val firestore = db ?: return emptyList()
        val formattedPhone = normalizePhoneNumber(verifiedPhone)
        if (formattedPhone.isBlank() || userUid.isBlank()) return emptyList()

        val connectedGroupIds = mutableListOf<String>()

        try {
            val pendingQuery = firestore.collectionGroup("pendingPhones")
                .whereEqualTo("normalizedPhone", formattedPhone)
                .whereEqualTo("status", "waiting_for_registration")
                .get().await()

            Log.d("TRAC_PARTNER", "AUTO_CONNECT query phone=$formattedPhone matches=${pendingQuery.documents.size}")

            for (doc in pendingQuery.documents) {
                val data = doc.data ?: continue
                val pending = PendingPartnerPhone.fromMap(data)
                val groupId = pending.groupId.ifBlank { doc.reference.parent.parent?.id ?: "" }
                val addedByUid = pending.addedByUid

                if (groupId.isBlank() || addedByUid.isBlank()) continue

                // Check that collaboration group exists and addedByUid is the owner
                val groupSnap = firestore.collection("collaborationGroups").document(groupId).get().await()
                if (!groupSnap.exists()) continue
                val groupOwnerUid = groupSnap.getString("ownerUid")
                if (groupOwnerUid != null && groupOwnerUid != addedByUid) {
                    Log.w("TRAC_PARTNER", "Security mismatch for pending phone: ownerUid=$groupOwnerUid addedBy=$addedByUid")
                    continue
                }

                val now = System.currentTimeMillis()
                val partnerName = userDisplayName?.ifBlank { null }
                    ?: pending.displayName.ifBlank { "Partner" }

                val batch = firestore.batch()

                // 1. Add active member in collaboration group
                val memberRef = firestore.collection("collaborationGroups").document(groupId)
                    .collection("members").document(userUid)
                val groupMember = CollaborationGroupMember(
                    uid = userUid,
                    workspaceId = userWorkspaceId,
                    role = "partner",
                    status = "active",
                    joinedAt = now,
                    phoneNumber = formattedPhone,
                    displayName = partnerName
                )
                batch.set(memberRef, groupMember.toMap(), SetOptions.merge())

                // 2. Add user discovery index
                val partnerIndexRef = firestore.collection("userCollaborationGroups").document(userUid)
                    .collection("groups").document(groupId)
                val partnerIndex = UserCollaborationGroupIndex(
                    groupId = groupId,
                    ownerUid = addedByUid,
                    ownerWorkspaceId = groupId,
                    role = "partner",
                    status = "active",
                    joinedAt = now
                )
                batch.set(partnerIndexRef, partnerIndex.toMap(), SetOptions.merge())

                // 3. Workspace member doc for backward compatibility
                val wsMemberRef = firestore.collection("workspaces").document(groupId)
                    .collection("members").document(userUid)
                val wsMember = WorkspaceMember(
                    uid = userUid,
                    role = "partner",
                    status = "active",
                    phoneNumber = formattedPhone,
                    joinedAt = now,
                    addedByUid = addedByUid,
                    invitedByUid = addedByUid,
                    displayName = partnerName
                )
                batch.set(wsMemberRef, wsMember.toMap(), SetOptions.merge())

                // 4. User Workspace Membership discovery doc
                val wsIndexRef = firestore.collection("userWorkspaceMemberships").document(userUid)
                    .collection("workspaces").document(groupId)
                batch.set(wsIndexRef, mapOf(
                    "workspaceId" to groupId,
                    "ownerUid" to addedByUid,
                    "role" to "partner",
                    "status" to "active",
                    "joinedAt" to now,
                    "workspaceName" to "Shared Business"
                ), SetOptions.merge())

                // 5. Delete pending record
                batch.delete(doc.reference)

                batch.commit().await()
                connectedGroupIds.add(groupId)
                Log.d("TRAC_PARTNER", "AUTO_CONNECT SUCCESS group=$groupId phone=$formattedPhone uid=$userUid")
            }
        } catch (e: Exception) {
            Log.e("TRAC_PARTNER", "AUTO_CONNECT failed for phone=$formattedPhone: ${e.message}", e)
        }

        return connectedGroupIds
    }

    suspend fun getCollaborationGroupMembersList(groupId: String): List<WorkspaceMember> {
        val firestore = db ?: return emptyList()
        if (groupId.isBlank()) return emptyList()
        return try {
            val snapshot = firestore.collection("collaborationGroups").document(groupId)
                .collection("members").get().await()
            val list = mutableListOf<WorkspaceMember>()
            for (doc in snapshot.documents) {
                val data = doc.data ?: continue
                var member = WorkspaceMember(
                    uid = doc.getString("uid") ?: doc.id,
                    role = doc.getString("role") ?: "partner",
                    status = doc.getString("status") ?: "active",
                    phoneNumber = doc.getString("phoneNumber"),
                    displayName = doc.getString("displayName"),
                    joinedAt = doc.getLong("joinedAt") ?: System.currentTimeMillis()
                )
                if (member.displayName.isNullOrBlank() && member.uid.isNotBlank()) {
                    try {
                        val userDoc = firestore.collection("users").document(member.uid).get().await()
                        val uName = userDoc.getString("displayName")
                        val uPhone = userDoc.getString("phoneNumber")
                        if (!uName.isNullOrBlank()) {
                            member = member.copy(displayName = uName)
                        }
                        if (member.phoneNumber.isNullOrBlank() && !uPhone.isNullOrBlank()) {
                            member = member.copy(phoneNumber = uPhone)
                        }
                    } catch (_: Exception) {}
                }
                list.add(member)
            }
            list
        } catch (e: Exception) {
            Log.w("TRAC_PARTNER", "Error getting collaboration members for $groupId: ${e.message}")
            emptyList()
        }
    }

    suspend fun getUserCollaborationGroups(uid: String): List<UserCollaborationGroupIndex> {
        val firestore = db ?: return emptyList()
        if (uid.isBlank()) return emptyList()
        return try {
            val snap = firestore.collection("userCollaborationGroups").document(uid)
                .collection("groups").get().await()
            snap.documents.mapNotNull { doc ->
                doc.data?.let { UserCollaborationGroupIndex.fromMap(it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error getting userCollaborationGroups: ${e.message}")
            emptyList()
        }
    }

    private fun parseLongId(idStr: String): Long {
        return idStr.toLongOrNull() ?: (idStr.hashCode().toLong().let { if (it <= 0) Math.abs(it) + 1L else it })
    }
}
