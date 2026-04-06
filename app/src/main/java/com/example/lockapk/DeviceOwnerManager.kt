package com.example.lockapk

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import android.util.Log
import androidx.core.content.ContextCompat

object DeviceOwnerManager {

    private const val TAG = "DeviceOwnerManager"

    fun getComponentName(context: Context): ComponentName {
        return ComponentName(context, MyDeviceAdminReceiver::class.java)
    }

    fun isDeviceOwner(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isDeviceOwnerApp(context.packageName)
    }

    fun applyPolicies(context: Context) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = getComponentName(context)

        if (!isDeviceOwner(context)) {
            Log.e(TAG, "❌ Not Device Owner - cannot apply policies")
            return
        }

        try {
            Log.i(TAG, "Applying Device Owner policies...")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // 1. Prevent factory reset
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)

                // 2. Prevent ONLY THIS APP from being uninstalled
                dpm.setUninstallBlocked(adminComponent, context.packageName, true)

                // 3. DO NOT USE DISALLOW_APPS_CONTROL - It blocks ALL app uninstalls
                // Removed: dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_APPS_CONTROL)

                // 4. Enable lock task mode
                dpm.setLockTaskPackages(adminComponent, arrayOf(context.packageName))

                // 5. Prevent safe boot
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)

                // 6. Prevent adding users
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_ADD_USER)

                // 7. Disable USB debugging to prevent ADB removal
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_DEBUGGING_FEATURES)

                // 8. Prevent users from modifying app permissions
                dpm.setPermissionPolicy(adminComponent, DevicePolicyManager.PERMISSION_POLICY_AUTO_GRANT)

                // 9. Auto-grant all runtime permissions to prevent disabling
                // Note: On Android 15+ (API 35), setPermissionGrantState() for sensor
                // permissions may throw SecurityException if the provisioning included
                // the sensors permission grant opt-out flag. Each call is wrapped
                // individually so one failure doesn't block the others.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val sensorPermissions = listOf(
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION,
                        android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    )
                    for (permission in sensorPermissions) {
                        try {
                            dpm.setPermissionGrantState(
                                adminComponent,
                                context.packageName,
                                permission,
                                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                            )
                        } catch (e: SecurityException) {
                            Log.w(TAG, "Cannot auto-grant $permission (Android 15+ restriction)", e)
                        }
                    }
                }


                // 10. Prevent USB file transfer
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_USB_FILE_TRANSFER)

                // 11. Prevent mounting physical media
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA)
            }

            Log.i(TAG, "✅ All policies applied successfully")

            // Start monitoring service
            startMonitoringService(context)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply policies", e)
        }
    }

    private fun startMonitoringService(context: Context) {
        try {
            val serviceIntent = Intent(context, FirestoreListenerService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
            Log.i(TAG, "✅ Firestore service started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service", e)
        }
    }

    fun wipeDevice(context: Context, reason: String = "EMI paid - device released") {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        if (!isDeviceOwner(context)) {
            Log.e(TAG, "Not Device Owner - cannot wipe")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.wipeData(
                    DevicePolicyManager.WIPE_EXTERNAL_STORAGE or
                            DevicePolicyManager.WIPE_RESET_PROTECTION_DATA,
                    reason
                )
            } else {
                dpm.wipeData(0)
            }
            Log.i(TAG, "Device wipe initiated")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to wipe device", e)
        }
    }
}