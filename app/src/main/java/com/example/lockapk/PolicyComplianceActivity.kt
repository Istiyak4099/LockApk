package com.example.lockapk

import android.app.Activity
import android.os.Bundle
import android.util.Log

class PolicyComplianceActivity : Activity() {

    companion object {
        private const val TAG = "PolicyCompliance"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "Policy compliance check")
        setResult(RESULT_OK)
        finish()
    }
}