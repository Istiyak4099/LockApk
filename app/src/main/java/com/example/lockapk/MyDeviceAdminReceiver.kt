package com.example.lockapk

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.widget.Toast

class MyDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, MyDeviceAdminReceiver::class.java)

        Toast.makeText(context, "Admin Status Granted", Toast.LENGTH_SHORT).show()

        // Device Owner Security Configuration
        try {
            // 1. Prevent Uninstallation
            dpm.setUninstallBlocked(adminComponent, context.packageName, true)

            // 2. Prevent User from disabling permissions or force-stopping
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_APPS_CONTROL)

            // 3. ENABLE KIOSK MODE (CRITICAL FIX)
            // This allows the app to pin itself to the screen automatically.
            dpm.setLockTaskPackages(adminComponent, arrayOf(context.packageName))

        } catch (e: Exception) {
            // This block will trigger if the app is not set as "Device Owner" via ADB/QR Code.
            // If just "Device Admin", some of these might fail silently.
        }

        // Start the background service immediately
        val serviceIntent = Intent(context, FirestoreListenerService::class.java)
        context.startForegroundService(serviceIntent)

        // Bring user back to MainActivity to finish other permissions
        val launch = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        context.startActivity(launch)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "Admin Status Removed", Toast.LENGTH_SHORT).show()
    }

    // Ensures service starts after device reboot
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, FirestoreListenerService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}