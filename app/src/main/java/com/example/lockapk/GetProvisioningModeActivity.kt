package com.example.lockapk

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log

/**
 * CRITICAL: This activity is required for Android 10+ QR code provisioning.
 * It tells the system this app supports fully managed device mode.
 */
class GetProvisioningModeActivity : Activity() {

    private val TAG = "GetProvisioningMode"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "GetProvisioningModeActivity triggered")

        // Specify that this app wants to be a fully managed device owner
        val adminComponent = ComponentName(this, MyDeviceAdminReceiver::class.java)

        val resultIntent = Intent().apply {
            // Tell the system we want FULLY_MANAGED_DEVICE mode (Device Owner)
            putExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_MODE,
                DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE
            )

            // Optional: Pass any extra configuration data
            putExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE,
                PersistableBundle().apply {
                    putString("custom_key", "custom_value")
                }
            )
        }

        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}