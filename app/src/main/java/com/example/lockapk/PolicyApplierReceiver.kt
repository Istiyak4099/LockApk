package com.example.lockapk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log

class PolicyApplierReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PolicyApplierReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.example.lockapk.APPLY_POLICIES") {
            Log.i(TAG, "Transfer complete - applying policies after delay")

            // CRITICAL: Add delay to ensure transfer is fully complete
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    DeviceOwnerManager.applyPolicies(context)
                    DeviceOwnerManager.setFrpPolicy(context)

                    // Launch MainActivity
                    val mainIntent = Intent(context, MainActivity::class.java)
                    mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    context.startActivity(mainIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to apply policies", e)
                }
            }, 2000) // 2 second delay
        }
    }
}