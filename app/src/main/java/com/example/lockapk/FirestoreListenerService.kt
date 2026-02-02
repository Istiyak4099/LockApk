package com.example.lockapk

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class FirestoreListenerService : Service() {

    private var firestoreListener: ListenerRegistration? = null
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val TAG = "FirestoreListener"

    // Guard variables to prevent infinite loops and quota spikes
    private var lastKnownStatus: String? = null
    private var lastLocationUpdateTime: Long = 0
    private val THIRTY_MINUTES_MS = 30 * 60 * 1000

    override fun onCreate() {
        super.onCreate()
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, MyDeviceAdminReceiver::class.java)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channelId = "lock_monitor_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Security Monitor Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("EMI Protection Active")
            .setContentText("Monitoring security status...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                1001,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(1001, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        firestoreListener?.remove()
        firestoreListener = FirebaseFirestore.getInstance()
            .collection("Customers")
            .whereEqualTo("android_id", androidId)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Firestore Listen Error", error)
                    return@addSnapshotListener
                }

                if (snapshots != null && !snapshots.isEmpty) {
                    val document = snapshots.documents[0]
                    val currentStatus = document.getString("status") ?: "active"

                    // Safeguard 1: Only trigger UI changes if the status actually changed.
                    // This prevents re-launching the lock screen on every location update.
                    if (currentStatus != lastKnownStatus) {
                        Log.d(TAG, "Status changed from $lastKnownStatus to $currentStatus")
                        lastKnownStatus = currentStatus
                        handleStatusChange(currentStatus)
                    }

                    // Safeguard 2: Only update location if 30 minutes have passed.
                    // This breaks the infinite loop and saves your Firebase quota.
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastLocationUpdateTime > THIRTY_MINUTES_MS) {
                        lastLocationUpdateTime = currentTime
                        updateLocationInFirestore(document.id)
                    }
                }
            }

        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun updateLocationInFirestore(docId: String) {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val updates = hashMapOf<String, Any>(
                    "latitude" to location.latitude,
                    "longitude" to location.longitude,
                    "last_location_update" to com.google.firebase.Timestamp.now()
                )

                // Using .update() is more quota-friendly than .set() for specific fields.
                FirebaseFirestore.getInstance().collection("Customers").document(docId)
                    .update(updates)
                    .addOnSuccessListener { Log.d(TAG, "Location updated successfully.") }
                    .addOnFailureListener { e -> Log.e(TAG, "Location update failed", e) }
            }
        }
    }

    private fun handleStatusChange(status: String) {
        when (status) {
            "locked" -> {
                val lockIntent = Intent(this, LockScreenActivity::class.java)
                lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(lockIntent)
            }
            "active", "unlocked" -> {
                sendBroadcast(Intent("com.example.lockapk.UNLOCK_ACTION"))
            }
            "removed" -> {
                if (devicePolicyManager.isDeviceOwnerApp(packageName)) {
                    devicePolicyManager.wipeData(0)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        firestoreListener?.remove()
        super.onDestroy()
    }
}