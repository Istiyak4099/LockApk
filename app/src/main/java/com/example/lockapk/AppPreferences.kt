package com.example.lockapk

import android.content.Context

object AppPreferences {
    private const val PREF_NAME = "emi_locker_prefs"
    private const val KEY_STATUS = "device_status"
    private const val KEY_CUSTOMER_ID = "customer_id" // Ideally saved during setup

    fun setStatus(context: Context, status: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_STATUS, status).apply()
    }

    fun getStatus(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_STATUS, "unlocked") ?: "unlocked"
    }

    // In a real app, you'd save the specific Firestore Document ID here
    fun setCustomerId(context: Context, id: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CUSTOMER_ID, id).apply()
    }

    fun getCustomerId(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CUSTOMER_ID, null)
    }
}