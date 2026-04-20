package com.example.lockapk

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore

class LockScreenActivity : AppCompatActivity() {

    private val TAG = "LockScreenActivity"
    private var retailerPhoneNumber: String? = null
    private var isCalling = false

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.lockapk.UNLOCK_ACTION") {
                // Stop Kiosk Mode before closing
                try {
                    stopLockTask()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock_screen)

        // Show over system lock screen (API 27+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            // For older versions, use window flags
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        // Prevent screenshots (Optional security measure)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        // Keep screen awake
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Register the unlock receiver with proper export flag
        val filter = IntentFilter("com.example.lockapk.UNLOCK_ACTION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(unlockReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(unlockReceiver, filter)
        }

        // Handle back button press using new API
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing - prevent back button from working
            }
        })

        // Setup Call button
        val btnCall = findViewById<MaterialButton>(R.id.btnCallRetailer)
        btnCall.setOnClickListener {
            val number = retailerPhoneNumber
            if (!number.isNullOrBlank()) {
                isCalling = true
                whitelistDialerPackages()

                val uri = Uri.parse("tel:$number")
                try {
                    val telecomManager = getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
                    if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        telecomManager.placeCall(uri, Bundle())
                    } else {
                        val callIntent = Intent(Intent.ACTION_CALL, uri)
                        callIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(callIntent)
                    }
                } catch (e: SecurityException) {
                    // Fallback to dialer if CALL_PHONE not granted
                    val dialIntent = Intent(Intent.ACTION_DIAL, uri)
                    dialIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(dialIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initiate call", e)
                    Toast.makeText(this, "Cannot place call", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Fetch retailer phone number from Firestore
        fetchRetailerPhoneNumber()
    }

    /**
     * Queries the Customers collection by this device's android_id,
     * reads the "created_by_uid" field, then looks up that retailer's
     * mobile_number from the Retailers collection.
     */
    private fun fetchRetailerPhoneNumber() {
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val db = FirebaseFirestore.getInstance()

        val tvPhone = findViewById<TextView>(R.id.tvRetailerPhone)
        val btnCall = findViewById<MaterialButton>(R.id.btnCallRetailer)

        db.collection("Customers")
            .whereEqualTo("android_id", androidId)
            .limit(1)
            .get()
            .addOnSuccessListener { customerSnapshots ->
                if (customerSnapshots.isEmpty) {
                    Log.w(TAG, "No customer document found for android_id: $androidId")
                    showContactUnavailable(tvPhone, btnCall)
                    return@addOnSuccessListener
                }

                val customerDoc = customerSnapshots.documents[0]
                val retailerUid = customerDoc.getString("created_by_uid")

                if (retailerUid.isNullOrBlank()) {
                    Log.w(TAG, "created_by_uid is missing in customer document")
                    showContactUnavailable(tvPhone, btnCall)
                    return@addOnSuccessListener
                }

                // Now query the Retailers collection for this uid
                db.collection("Retailers")
                    .document(retailerUid)
                    .get()
                    .addOnSuccessListener { retailerDoc ->
                        if (retailerDoc.exists()) {
                            val mobileNumber = retailerDoc.getString("mobile_number")

                            if (!mobileNumber.isNullOrBlank()) {
                                retailerPhoneNumber = mobileNumber
                                tvPhone.text = mobileNumber
                                btnCall.isEnabled = true
                                Log.d(TAG, "Retailer phone loaded: $mobileNumber")
                            } else {
                                Log.w(TAG, "mobile_number is empty in retailer doc")
                                showContactUnavailable(tvPhone, btnCall)
                            }
                        } else {
                            Log.w(TAG, "Retailer document not found for uid: $retailerUid")
                            showContactUnavailable(tvPhone, btnCall)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to fetch retailer document", e)
                        showContactUnavailable(tvPhone, btnCall)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to fetch customer document", e)
                showContactUnavailable(tvPhone, btnCall)
            }
    }

    private fun showContactUnavailable(
        tvPhone: TextView,
        btnCall: MaterialButton
    ) {
        tvPhone.text = getString(R.string.contact_unavailable)
        btnCall.isEnabled = false
    }

    private fun whitelistDialerPackages() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, MyDeviceAdminReceiver::class.java)

        if (dpm.isDeviceOwnerApp(packageName)) {
            try {
                val currentPackages = dpm.getLockTaskPackages(adminComponent).toMutableList()

                val dialers = mutableListOf(
                    "com.android.server.telecom",
                    "com.google.android.dialer",
                    "com.samsung.android.dialer",
                    "com.samsung.android.incallui",
                    "com.android.incallui",
                    "com.android.phone"
                )

                val dialIntent = Intent(Intent.ACTION_DIAL)
                val resolveInfo = packageManager.resolveActivity(dialIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                resolveInfo?.activityInfo?.packageName?.let {
                    if (!dialers.contains(it)) dialers.add(it)
                }

                var updated = false
                for (dialer in dialers) {
                    if (!currentPackages.contains(dialer)) {
                        currentPackages.add(dialer)
                        updated = true
                    }
                }

                if (updated) {
                    dpm.setLockTaskPackages(adminComponent, currentPackages.toTypedArray())
                    Log.d(TAG, "Added dialer packages to LockTask whitelist")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update LockTaskPackages", e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isCalling = false
        // Enable Kiosk Mode (Screen Pinning)
        try {
            startLockTask()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(unlockReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Prevent User from pressing Home/Switching Apps
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isCalling) return

        try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            activityManager.moveTaskToFront(taskId, ActivityManager.MOVE_TASK_NO_USER_ACTION)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}