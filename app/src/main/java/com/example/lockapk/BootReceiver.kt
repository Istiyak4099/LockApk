package com.example.lockapk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i(TAG, "Device booted - checking if setup completed")

            // Check if app setup is complete
            val prefs = context.getSharedPreferences("app_setup", Context.MODE_PRIVATE)
            val isSetupComplete = prefs.getBoolean("setup_completed", false)

            if (isSetupComplete) {
                Log.i(TAG, "Setup complete - starting FirestoreListenerService")

                try {
                    val serviceIntent = Intent(context, FirestoreListenerService::class.java)
                    ContextCompat.startForegroundService(context, serviceIntent)
                    Log.i(TAG, "✅ FirestoreListenerService started successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to start service on boot", e)
                }
            } else {
                Log.i(TAG, "Setup not complete - service not started")
            }
        }
    }
}