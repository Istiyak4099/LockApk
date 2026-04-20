package com.example.lockapk

import android.Manifest
import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

class MainActivity : ComponentActivity() {

    private lateinit var dpm: DevicePolicyManager
    private lateinit var componentName: ComponentName
    private val TAG = "MainActivity"

    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            grantAndStart()
        }
    }

    private val deviceAdminLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (dpm.isAdminActive(componentName)) {
            grantAndStart()
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            grantAndStart()
        }
    }

    private val batteryOptimizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            grantAndStart()
        }
    }

    override fun onResume() {
        super.onResume()
        if (isSetupCompleted()) return

        val isDeviceOwner = DeviceOwnerManager.isDeviceOwner(this)
        if (isDeviceOwner) {
            if (isAllRuntimePermissionsGranted()) {
                markPermissionsGranted()
                startRequiredService()
                navigateToDetails()
            }
        } else if (isAllPermissionGranted()) {
            markPermissionsGranted()
            startRequiredService()
            navigateToDetails()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        componentName = ComponentName(this, MyDeviceAdminReceiver::class.java)

        val isDeviceOwner = DeviceOwnerManager.isDeviceOwner(this)
        Log.d(TAG, "Is Device Owner: $isDeviceOwner")

        if (isSetupCompleted()) {
            Log.d(TAG, "Setup already completed - going to details")
            startRequiredService()
            navigateToDetails()
            return
        }

        if (isDeviceOwner) {
            if (isAllRuntimePermissionsGranted()) {
                markPermissionsGranted()
                startRequiredService()
                navigateToDetails()
                return
            }
        } else if (isAllPermissionGranted()) {
            markPermissionsGranted()
            startRequiredService()
            navigateToDetails()
            return
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(isDeviceOwner = isDeviceOwner)
                }
            }
        }
    }

    private fun isSetupCompleted(): Boolean {
        val prefs = getSharedPreferences("app_setup", Context.MODE_PRIVATE)
        return prefs.getBoolean("setup_completed", false)
    }

    private fun markPermissionsGranted() {
        val prefs = getSharedPreferences("app_setup", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("setup_completed", true).apply()
        Log.d(TAG, "Setup marked as completed")
    }

    private fun generateQRCode(text: String, size: Int = 512): Bitmap {
        val qrCodeWriter = QRCodeWriter()
        val bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(
                    x, y,
                    if (bitMatrix[x, y]) android.graphics.Color.BLACK
                    else android.graphics.Color.WHITE
                )
            }
        }
        return bitmap
    }

    @Composable
    private fun MainScreen(isDeviceOwner: Boolean) {
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val deviceOwnerCommand = "adb shell dpm set-device-owner com.example.lockapk/.MyDeviceAdminReceiver"

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Device Owner Status Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDeviceOwner) Color(0xFF4CAF50) else Color(0xFFFF9800)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isDeviceOwner) "✓ Device Owner Active" else "⚠ Device Admin Required",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (isDeviceOwner) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "All policies applied automatically",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
            }


            // Android ID Card with QR Code
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Device Android ID:", fontSize = 14.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(androidId, fontSize = 18.sp, fontWeight = FontWeight.Bold)

                    Spacer(modifier = Modifier.height(16.dp))

                    // QR Code
                    val qrBitmap = remember { generateQRCode(androidId, 300) }
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "Android ID QR Code",
                        modifier = Modifier.size(200.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Scan this QR code when creating customer profile",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            // Grant Permissions Button
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                onClick = { grantAndStart() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                )
            ) {
                Text(
                    text = "Grant Permissions",
                    fontSize = 16.sp
                )
            }

            // Info text
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (isDeviceOwner)
                    "Tap to grant location and battery permissions"
                else
                    "Set Device Owner first to continue",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }

    private fun isAllPermissionGranted(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val hasLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return dpm.isAdminActive(componentName) &&
                Settings.canDrawOverlays(this) &&
                pm.isIgnoringBatteryOptimizations(packageName) &&
                hasLocation
    }

    private fun isAllRuntimePermissionsGranted(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val hasLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return Settings.canDrawOverlays(this) &&
                pm.isIgnoringBatteryOptimizations(packageName) &&
                hasLocation
    }

    @SuppressLint("BatteryLife")
    private fun grantAndStart() {
        val isDeviceOwner = DeviceOwnerManager.isDeviceOwner(this)

        if (!isDeviceOwner && !dpm.isAdminActive(componentName)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Required for EMI security."
                )
            }
            deviceAdminLauncher.launch(intent)
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            overlayPermissionLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestLocationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            batteryOptimizationLauncher.launch(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }

        markPermissionsGranted()
        startRequiredService()
        navigateToDetails()
    }

    private fun startRequiredService() {
        val serviceIntent = Intent(this, FirestoreListenerService::class.java)
        startForegroundService(serviceIntent)
        Log.d(TAG, "FirestoreListenerService started")
    }

    private fun navigateToDetails() {
        startActivity(Intent(this, DeviceDetailsActivity::class.java))
        finish()
    }
}