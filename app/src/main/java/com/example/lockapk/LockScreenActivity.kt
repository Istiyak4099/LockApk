package com.example.lockapk

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

class LockScreenActivity : AppCompatActivity() {

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

        // Show over system lock screen
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        // Prevent screenshots (Optional security measure)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        // Keep screen awake
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Register the unlock receiver
        val filter = IntentFilter("com.example.lockapk.UNLOCK_ACTION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(unlockReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(unlockReceiver, filter)
        }
    }

    override fun onResume() {
        super.onResume()
        // CRITICAL FIX: Enable Kiosk Mode (Screen Pinning)
        // This disables the Home and Recent Apps buttons.
        try {
            startLockTask()
        } catch (e: Exception) {
            // Fallback if not Device Owner: User will be prompted to pin
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(unlockReceiver)
    }

    // 1. Disable Back Button
    override fun onBackPressed() {
        // Do nothing, preventing the user from going back
    }

    // 2. Prevent User from pressing Home/Switching Apps
    // This immediately brings the activity back if they manage to minimize it
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.moveTaskToFront(taskId, ActivityManager.MOVE_TASK_NO_USER_ACTION)
    }
}