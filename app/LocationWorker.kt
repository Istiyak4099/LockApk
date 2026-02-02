package com.example.emilocker

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker.Result
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.Timestamp
import com.google.firebase.firestore.firestore
import com.google.firebase.Firebase
import kotlinx.coroutines.tasks.await

class LocationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val fusedLocationClient =
        LocationServices.getFusedLocationProviderClient(appContext)

    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        val fineLocationPermission =
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (!fineLocationPermission) {
            Log.e("LocationWorker", "Location permission not granted")
            return Result.failure()
        }

        AppPreferences.init(applicationContext)
        val deviceAndroidId = AppPreferences.deviceId

        if (deviceAndroidId.isNullOrEmpty()) {
            Log.e("LocationWorker", "Android ID not found")
            return Result.failure()
        }

        return try {
            val location = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                CancellationTokenSource().token
            ).await()

            if (location != null) {
                updateLocationInFirestore(
                    deviceAndroidId,
                    location.latitude,
                    location.longitude
                )
            } else {
                Log.w("LocationWorker", "Location is null")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("LocationWorker", "Failed to update location", e)
            Result.failure()
        }
    }

    private fun updateLocationInFirestore(
        androidId: String,
        lat: Double,
        lon: Double
    ) {
        val db = Firebase.firestore

        val locationData = mapOf(
            "latitude" to lat,
            "longitude" to lon,
            "location_timestamp" to Timestamp.now()
        )

        db.collection("customers")
            .whereEqualTo("android_id", androidId)
            .get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    db.collection("customers")
                        .document(doc.id)
                        .update(locationData)
                }
            }
            .addOnFailureListener { e ->
                Log.e("LocationWorker", "Firestore query failed", e)
            }
    }
}
