package com.example.lockapk

import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class DeviceDetailsActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_details)

        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        fetchEmiDetails(androidId)
    }

    private fun fetchEmiDetails(androidId: String) {
        val tvData = findViewById<TextView>(R.id.tvEmiData)

        db.collection("EmiDetails")
            .whereEqualTo("android_id", androidId)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    tvData.text = "Error loading data."
                    return@addSnapshotListener
                }

                if (snapshots != null && !snapshots.isEmpty) {
                    val doc = snapshots.documents[0]

                    // Manually retrieving fields to maintain YOUR exact sequence
                    val product = doc.getString("product_name") ?: "N/A"
                    val price = doc.get("price") ?: "0"
                    val procFee = doc.get("processing_fee") ?: "0"
                    val downPay = doc.get("down_payment") ?: "0"
                    val totalEmi = doc.get("total_emi") ?: "0"
                    val numEmi = doc.get("number_of_emi") ?: "0"
                    val monthlyAmt = doc.get("emi_monthly_amount") ?: "0"

                    // Building the display string
                    val displayText = """
                        Product: $product
                        Price: $price
                        Processing Fee: $procFee
                        Down Payment: $downPay
                        Total EMI: $totalEmi
                        No. of EMI: $numEmi
                        Monthly Amount: $monthlyAmt
                    """.trimIndent()

                    tvData.text = displayText
                } else {
                    tvData.text = "No EMI details found for this device."
                }
            }
    }
}