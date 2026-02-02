package com.example.lockapk

import android.app.Activity
import android.os.Bundle

class PolicyComplianceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Just close and let the system handle the rest, or redirect to MainActivity
        finish()
    }
}