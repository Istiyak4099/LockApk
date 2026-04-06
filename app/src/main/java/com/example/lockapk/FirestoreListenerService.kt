package com.example.lockapk

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class FirestoreListenerService : Service() {

    private var firestoreListener: ListenerRegistration? = null
    private var notificationsListener: ListenerRegistration? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val TAG = "FirestoreListener"

    // Guard variables to prevent infinite loops and quota spikes
    private var lastKnownStatus: String? = null
    private var lastLocationUpdateTime: Long = 0
    private val THIRTY_MINUTES_MS = 30 * 60 * 1000

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channelId = "lock_monitor_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Security Monitor Service",
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

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    1001,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    1001,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(1001, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
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

                    if (currentStatus != lastKnownStatus) {
                        Log.d(TAG, "Status changed from $lastKnownStatus to $currentStatus")
                        lastKnownStatus = currentStatus
                        handleStatusChange(currentStatus)
                    }

                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastLocationUpdateTime > THIRTY_MINUTES_MS) {
                        lastLocationUpdateTime = currentTime
                        updateLocationInFirestore(document.id)
                    }
                }
            }

        notificationsListener?.remove()
        notificationsListener = FirebaseFirestore.getInstance()
            .collection("Notifications")
            .whereEqualTo("android_id", androidId)
            .whereEqualTo("status", "pending")
            .whereEqualTo("type", "payment_reminder")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Notifications Listen Error", error)
                    return@addSnapshotListener
                }

                snapshots?.documentChanges?.forEach { dc ->
                    if (dc.type == DocumentChange.Type.ADDED) {
                        val notification = dc.document.data
                        val message = notification["message"] as? String ?: "Please pay your EMI."

                        // Trigger the Reminder UI
                        Log.i(TAG, "REMINDER RECEIVED: $message")
                        val reminderIntent = Intent(this, ReminderActivity::class.java).apply {
                            putExtra("REMINDER_MESSAGE", message)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        startActivity(reminderIntent)

                        // Mark as delivered
                        dc.document.reference.update("status", "delivered")
                            .addOnSuccessListener {
                                Log.d(TAG, "Notification marked delivered")
                            }
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

                FirebaseFirestore.getInstance()
                    .collection("Customers")
                    .document(docId)
                    .update(updates)
                    .addOnSuccessListener {
                        Log.d(TAG, "Location updated successfully.")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Location update failed", e)
                    }
            } else {
                Log.w(TAG, "Location is null, skipping update")
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to get location", e)
        }
    }

    private fun handleStatusChange(status: String) {
        when (status) {
            "locked" -> {
                Log.d(TAG, "Locking device")
                val lockIntent = Intent(this, LockScreenActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(lockIntent)
            }

            "active", "unlocked" -> {
                Log.d(TAG, "Unlocking device")
                sendBroadcast(Intent("com.example.lockapk.UNLOCK_ACTION"))
            }

            "removed" -> {
                Log.d(TAG, "EMI fully paid - wiping device to release ownership")
                wipeDeviceAndRelease()
            }

            else -> {
                Log.w(TAG, "Unknown status: $status")
            }
        }
    }

    private fun wipeDeviceAndRelease() {
        try {
            showReleaseNotification()
            Thread.sleep(5000)

            // Use DeviceOwnerManager instead of doing it inline
            DeviceOwnerManager.wipeDevice(this, "EMI fully paid - device released")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to wipe device", e)
        }
    }

    private fun showReleaseNotification() {
        val channelId = "device_release_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Device Release",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("🎉 EMI Fully Paid!")
            .setContentText("Device will be released in 5 seconds...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(2001, notification)
    }

    // Android 15+ (API 34+): dataSync foreground services have a 6-hour cumulative
    // limit within a 24-hour window. This callback fires when the limit is reached.
    // We must stop gracefully to avoid a RemoteServiceException crash.
    // The service will be restarted by the system via START_STICKY.
    override fun onTimeout(startId: Int, fgsType: Int) {
        super.onTimeout(startId, fgsType)
        Log.w(TAG, "Foreground service timeout reached (type=$fgsType), stopping gracefully")
        stopSelf(startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        firestoreListener?.remove()
        notificationsListener?.remove()
        super.onDestroy()
    }
}