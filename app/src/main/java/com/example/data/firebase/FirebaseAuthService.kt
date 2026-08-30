package com.example.data.firebase

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FirebaseAuthService(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : AuthService {

    private val TAG = "FirebaseAuthService"

    private val auth: FirebaseAuth? by lazy {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            val instance = FirebaseAuth.getInstance()
            if (com.example.BuildConfig.DEBUG) {
                instance.firebaseAuthSettings.setAppVerificationDisabledForTesting(true)
                Log.d("TRAC_AUTH", "DEBUG build: setAppVerificationDisabledForTesting(true)")
            }
            instance
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing FirebaseAuth: ${e.message}")
            null
        }
    }

    private val firestore: FirebaseFirestore? by lazy {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing FirebaseFirestore: ${e.message}")
            null
        }
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _currentUserProfile = MutableStateFlow<UserProfile?>(null)
    override val currentUserProfile: StateFlow<UserProfile?> = _currentUserProfile.asStateFlow()

    override val currentUid: String?
        get() = auth?.currentUser?.uid

    private var authStateListener: FirebaseAuth.AuthStateListener? = null

    override fun startAuthStateListener() {
        val currentAuth = auth ?: run {
            _authState.value = AuthState.Unauthenticated("Firebase not initialized")
            return
        }

        if (authStateListener != null) return

        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                Log.d("TRAC_AUTH", "authenticated uid=${user.uid}")
                scope.launch {
                    try {
                        val profile = fetchOrCreateUserProfile(user)
                        _currentUserProfile.value = profile
                        _authState.value = AuthState.Authenticated(profile)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching user profile: ${e.message}", e)
                        val basicProfile = UserProfile(
                            uid = user.uid,
                            displayName = user.displayName,
                            email = user.email,
                            phoneNumber = user.phoneNumber,
                            photoUrl = user.photoUrl?.toString()
                        )
                        _currentUserProfile.value = basicProfile
                        _authState.value = AuthState.Authenticated(basicProfile)
                    }
                }
            } else {
                _currentUserProfile.value = null
                _authState.value = AuthState.Unauthenticated()
            }
        }

        currentAuth.addAuthStateListener(authStateListener!!)
    }

    override suspend fun signInWithGoogle(context: Context, webClientId: String?): Result<UserProfile> {
        return try {
            val currentAuth = auth ?: return Result.failure(IllegalStateException("Firebase is not initialized."))

            _authState.value = AuthState.Loading
            val credentialManager = CredentialManager.create(context)

            // Resolve Web Client ID from resources or parameter
            val serverClientId = webClientId?.ifBlank { null }
                ?: getResourceString(context, "default_web_client_id")
                ?: "1043385667408-mock-client-id.apps.googleusercontent.com"

            val googleIdOption = GetSignInWithGoogleOption.Builder(serverClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(context, request)
            val credential = result.credential

            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val authCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                val authResult = currentAuth.signInWithCredential(authCredential).awaitTask()
                val user = authResult.user ?: throw IllegalStateException("Firebase user is null after sign in")

                val profile = fetchOrCreateUserProfile(user)
                Log.d("TRAC_AUTH", "authenticated uid=${user.uid}")
                _currentUserProfile.value = profile
                _authState.value = AuthState.Authenticated(profile)
                Result.success(profile)
            } else {
                val err = "Unrecognized credential type received from CredentialManager"
                _authState.value = AuthState.Error(err)
                Result.failure(IllegalStateException(err))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed: ${e.message}", e)
            _authState.value = AuthState.Error(e.message ?: "Google Sign-In failed")
            Result.failure(e)
        }
    }

    override fun sendPhoneOtp(
        activity: Activity,
        phoneNumber: String,
        onCodeSent: (verificationId: String) -> Unit,
        onError: (message: String) -> Unit,
        onAutoVerified: (userProfile: UserProfile) -> Unit
    ) {
        val currentAuth = auth
        if (currentAuth == null) {
            onError("Firebase Auth is not initialized.")
            return
        }

        // Format phone number to E.164 if necessary
        val formattedNumber = if (!phoneNumber.startsWith("+")) {
            "+91${phoneNumber.filter { it.isDigit() }.takeLast(10)}"
        } else {
            phoneNumber
        }

        _authState.value = AuthState.Loading

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                scope.launch {
                    try {
                        val authResult = currentAuth.signInWithCredential(credential).awaitTask()
                        val user = authResult.user ?: throw IllegalStateException("User null after verification")
                        val profile = fetchOrCreateUserProfile(user)
                        Log.d("TRAC_AUTH", "authenticated uid=${user.uid}")
                        _currentUserProfile.value = profile
                        _authState.value = AuthState.Authenticated(profile)
                        onAutoVerified(profile)
                    } catch (e: Exception) {
                        Log.e(TAG, "Auto verification sign in failed: ${e.message}", e)
                        onError(e.message ?: "Verification failed")
                    }
                }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                Log.e(TAG, "Phone verification failed: ${e.message}", e)
                _authState.value = AuthState.Error(e.message ?: "Phone verification failed")
                onError(e.message ?: "Phone verification failed")
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                Log.d(TAG, "Phone verification code sent. ID: $verificationId")
                _authState.value = AuthState.CodeSent(verificationId, formattedNumber)
                onCodeSent(verificationId)
            }
        }

        val options = PhoneAuthOptions.newBuilder(currentAuth)
            .setPhoneNumber(formattedNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    override suspend fun verifyPhoneOtp(verificationId: String, otpCode: String): Result<UserProfile> {
        return try {
            val currentAuth = auth ?: return Result.failure(IllegalStateException("Firebase not initialized."))

            _authState.value = AuthState.Loading
            val credential = PhoneAuthProvider.getCredential(verificationId, otpCode)
            val authResult = currentAuth.signInWithCredential(credential).awaitTask()
            val user = authResult.user ?: throw IllegalStateException("User is null after OTP verification")

            val profile = fetchOrCreateUserProfile(user)
            Log.d("TRAC_AUTH", "authenticated uid=${user.uid}")
            _currentUserProfile.value = profile
            _authState.value = AuthState.Authenticated(profile)
            Result.success(profile)
        } catch (e: Exception) {
            Log.e(TAG, "OTP verification failed: ${e.message}", e)
            _authState.value = AuthState.Error(e.message ?: "Invalid OTP Code")
            Result.failure(e)
        }
    }

    override suspend fun signOut() {
        try {
            auth?.signOut()
            _currentUserProfile.value = null
            _authState.value = AuthState.Unauthenticated()
        } catch (e: Exception) {
            Log.e(TAG, "Error signing out: ${e.message}", e)
        }
    }

    /**
     * Checks if `users/{uid}` exists in Firestore.
     * If not, creates basic profile document without nulling defaultWorkspaceId.
     * If exists, updates fields safely using SetOptions.merge().
     */
    private suspend fun fetchOrCreateUserProfile(user: FirebaseUser): UserProfile {
        val uid = user.uid
        val db = firestore

        if (db == null) {
            return UserProfile(
                uid = uid,
                displayName = user.displayName,
                email = user.email,
                phoneNumber = user.phoneNumber,
                photoUrl = user.photoUrl?.toString()
            )
        }

        val userDocRef = db.collection("users").document(uid)

        return try {
            val snapshot = userDocRef.get().awaitTask()
            if (snapshot.exists()) {
                val data = snapshot.data ?: emptyMap()
                val existing = UserProfile.fromMap(data)
                val updated = existing.copy(
                    updatedAt = System.currentTimeMillis(),
                    displayName = existing.displayName ?: user.displayName,
                    email = existing.email ?: user.email,
                    phoneNumber = existing.phoneNumber ?: user.phoneNumber,
                    photoUrl = existing.photoUrl ?: user.photoUrl?.toString()
                )
                val updateMap = mutableMapOf<String, Any?>(
                    "uid" to uid,
                    "updatedAt" to System.currentTimeMillis()
                )
                if (user.displayName != null) updateMap["displayName"] = user.displayName
                if (user.email != null) updateMap["email"] = user.email
                if (user.phoneNumber != null) updateMap["phoneNumber"] = user.phoneNumber
                if (user.photoUrl != null) updateMap["photoUrl"] = user.photoUrl.toString()
                userDocRef.set(updateMap, SetOptions.merge()).awaitTask()
                registerPhoneDirectoryEntry(user)
                updated
            } else {
                val canonicalWsId = "ws_${uid.replace(Regex("[^a-zA-Z0-9]"), "").take(16).ifBlank { "main" }}"
                val newProfile = UserProfile(
                    uid = uid,
                    displayName = user.displayName ?: "Partner",
                    email = user.email,
                    phoneNumber = user.phoneNumber,
                    photoUrl = user.photoUrl?.toString(),
                    defaultWorkspaceId = canonicalWsId,
                    workspaces = listOf(canonicalWsId),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                userDocRef.set(newProfile.toMap(), SetOptions.merge()).awaitTask()
                registerPhoneDirectoryEntry(user)
                newProfile
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firestore users/{uid} fetch/create skipped: ${e.message}")
            UserProfile(
                uid = uid,
                displayName = user.displayName,
                email = user.email,
                phoneNumber = user.phoneNumber,
                photoUrl = user.photoUrl?.toString()
            )
        }
    }

    private suspend fun registerPhoneDirectoryEntry(user: FirebaseUser) {
        val phone = user.phoneNumber
        if (phone.isNullOrBlank()) return
        val db = firestore ?: return
        val normalizedPhone = normalizePhoneNumber(phone)
        if (normalizedPhone.isBlank()) return
        val docData = mapOf(
            "uid" to user.uid,
            "phoneNumber" to normalizedPhone,
            "updatedAt" to System.currentTimeMillis()
        )
        try {
            db.collection("phoneDirectory").document(normalizedPhone)
                .set(docData, SetOptions.merge())
                .awaitTask()
            Log.d("TRAC_PHONE_DIRECTORY", "REGISTER phone=$normalizedPhone uid=${user.uid} SUCCESS")
        } catch (e: Exception) {
            Log.w("TRAC_PHONE_DIRECTORY", "REGISTER phone=$normalizedPhone uid=${user.uid} FAILED: ${e.message}")
        }
    }

    private fun getResourceString(context: Context, name: String): String? {
        val id = context.resources.getIdentifier(name, "string", context.packageName)
        return if (id != 0) context.getString(id) else null
    }
}

/**
 * Extension helper to await Task results safely with coroutine cancellation.
 */
suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) {
            continuation.resume(task.result)
        } else {
            continuation.resumeWithException(task.exception ?: RuntimeException("Task failed with unknown error"))
        }
    }
}
