package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp

class TracApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
                Log.d("TracApplication", "FirebaseApp initialized successfully in Application subclass")
            }
        } catch (e: Exception) {
            Log.e("TracApplication", "Error initializing FirebaseApp: ${e.message}", e)
        }
    }
}
