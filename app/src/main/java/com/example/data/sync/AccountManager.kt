package com.example.data.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest

data class UserAccountProfile(
    val email: String = "",
    val phone: String = "",
    val businessId: String = "",
    val businessName: String = "",
    val ownerName: String = "",
    val role: String = "OWNER",
    val authProvider: String = "EMAIL", // EMAIL, GOOGLE, PHONE
    val passwordHash: String = "",
    val profilePhotoUri: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "email" to email,
            "phone" to phone,
            "businessId" to businessId,
            "businessName" to businessName,
            "ownerName" to ownerName,
            "role" to role,
            "authProvider" to authProvider,
            "passwordHash" to passwordHash,
            "profilePhotoUri" to profilePhotoUri,
            "createdAt" to createdAt
        )
    }

    fun toJsonString(): String {
        val json = JSONObject()
        json.put("email", email)
        json.put("phone", phone)
        json.put("businessId", businessId)
        json.put("businessName", businessName)
        json.put("ownerName", ownerName)
        json.put("role", role)
        json.put("authProvider", authProvider)
        json.put("passwordHash", passwordHash)
        json.put("profilePhotoUri", profilePhotoUri)
        json.put("createdAt", createdAt)
        return json.toString()
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): UserAccountProfile {
            return UserAccountProfile(
                email = map["email"] as? String ?: "",
                phone = map["phone"] as? String ?: "",
                businessId = map["businessId"] as? String ?: "",
                businessName = map["businessName"] as? String ?: "",
                ownerName = map["ownerName"] as? String ?: "",
                role = map["role"] as? String ?: "OWNER",
                authProvider = map["authProvider"] as? String ?: "EMAIL",
                passwordHash = map["passwordHash"] as? String ?: "",
                profilePhotoUri = map["profilePhotoUri"] as? String ?: "",
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }

        fun fromJsonString(jsonStr: String): UserAccountProfile? {
            return try {
                val json = JSONObject(jsonStr)
                UserAccountProfile(
                    email = json.optString("email", ""),
                    phone = json.optString("phone", ""),
                    businessId = json.optString("businessId", ""),
                    businessName = json.optString("businessName", ""),
                    ownerName = json.optString("ownerName", ""),
                    role = json.optString("role", "OWNER"),
                    authProvider = json.optString("authProvider", "EMAIL"),
                    passwordHash = json.optString("passwordHash", ""),
                    profilePhotoUri = json.optString("profilePhotoUri", ""),
                    createdAt = json.optLong("createdAt", System.currentTimeMillis())
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

class AccountManager(private val context: Context) {
    private val TAG = "AccountManager"
    private val PREFS_NAME = "aidhunt_user_accounts"
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val firestore: FirebaseFirestore? by lazy {
        try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseFirestore.getInstance()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase Firestore not available: ${e.message}")
            null
        }
    }

    fun hashPassword(password: String): String {
        if (password.isBlank()) return ""
        val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun sanitizeKey(key: String): String {
        return key.trim().lowercase()
            .replace("@", "_at_")
            .replace(".", "_dot_")
            .replace("+", "_plus_")
            .replace(" ", "_")
    }

    suspend fun saveAccountProfile(profile: UserAccountProfile): Boolean = withContext(Dispatchers.IO) {
        val sanitizedEmail = if (profile.email.isNotBlank()) sanitizeKey(profile.email) else ""
        val sanitizedPhone = if (profile.phone.isNotBlank()) sanitizeKey(profile.phone) else ""
        val jsonStr = profile.toJsonString()

        // 1. Save to local SharedPreferences
        val editor = prefs.edit()
        if (sanitizedEmail.isNotBlank()) {
            editor.putString("user_$sanitizedEmail", jsonStr)
        }
        if (sanitizedPhone.isNotBlank()) {
            editor.putString("user_$sanitizedPhone", jsonStr)
        }
        editor.putString("last_active_user", jsonStr)
        editor.apply()

        // 2. Save to Firestore `users` collection and `businesses` collection
        val db = firestore
        if (db != null) {
            try {
                if (sanitizedEmail.isNotBlank()) {
                    db.collection("users").document(sanitizedEmail)
                        .set(profile.toMap(), SetOptions.merge()).await()
                }
                if (sanitizedPhone.isNotBlank() && sanitizedPhone != sanitizedEmail) {
                    db.collection("users").document(sanitizedPhone)
                        .set(profile.toMap(), SetOptions.merge()).await()
                }

                // Also save/update the business document
                if (profile.businessId.isNotBlank()) {
                    val businessDoc = mapOf(
                        "businessId" to profile.businessId,
                        "businessName" to profile.businessName,
                        "ownerName" to profile.ownerName,
                        "ownerEmail" to profile.email,
                        "ownerPhone" to profile.phone,
                        "updatedAt" to System.currentTimeMillis()
                    )
                    db.collection("businesses").document(profile.businessId)
                        .set(businessDoc, SetOptions.merge()).await()
                }
                Log.d(TAG, "Successfully saved account profile to Firestore for ${profile.email}")
                true
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save profile to Firestore (saved locally): ${e.message}")
                true
            }
        } else {
            true
        }
    }

    suspend fun findAccountProfile(identifier: String): UserAccountProfile? = withContext(Dispatchers.IO) {
        val sanitized = sanitizeKey(identifier)
        if (sanitized.isBlank()) return@withContext null

        // Try Firestore first
        val db = firestore
        if (db != null) {
            try {
                val doc = db.collection("users").document(sanitized).get().await()
                if (doc.exists() && doc.data != null) {
                    val profile = UserAccountProfile.fromMap(doc.data!!)
                    // Update local cache
                    prefs.edit().putString("user_$sanitized", profile.toJsonString()).apply()
                    return@withContext profile
                }
            } catch (e: Exception) {
                Log.w(TAG, "Firestore findAccountProfile failed: ${e.message}")
            }
        }

        // Fallback to local cache
        val localJson = prefs.getString("user_$sanitized", null)
        if (!localJson.isNullOrBlank()) {
            return@withContext UserAccountProfile.fromJsonString(localJson)
        }

        null
    }
}
