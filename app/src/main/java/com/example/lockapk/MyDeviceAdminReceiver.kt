package com.example.lockapk

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import android.util.Log

class MyDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "DeviceAdminReceiver"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device admin enabled")

        // Check if we became device owner and apply policies
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (dpm.isDeviceOwnerApp(context.packageName)) {
            Log.i(TAG, "We became device owner - applying policies")
            val policyIntent = Intent("com.example.lockapk.APPLY_POLICIES")
            policyIntent.setPackage(context.packageName)
            context.sendBroadcast(policyIntent)
        }
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        Log.i(TAG, "Profile provisioning complete")
    }

    override fun onTransferOwnershipComplete(context: Context, bundle: PersistableBundle?) {
        super.onTransferOwnershipComplete(context, bundle)
        Log.i(TAG, "onTransferOwnershipComplete")

        // Send broadcast to apply policies AFTER transfer
        val policyIntent = Intent("com.example.lockapk.APPLY_POLICIES")
        policyIntent.setPackage(context.packageName)
        context.sendBroadcast(policyIntent)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.i(TAG, "Device admin disabled")
    }
}