package com.example.lockapk

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

class DeviceDetailsActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_details)

        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        // Display Android ID
        val tvAndroidId = findViewById<TextView>(R.id.tvAndroidId)
        tvAndroidId.text = androidId

        // Generate and display QR Code
        val ivQrCode = findViewById<ImageView>(R.id.ivQrCode)
        val qrBitmap = generateQRCode(androidId, 500)
        ivQrCode.setImageBitmap(qrBitmap)


        // Fetch EMI details
        fetchEmiDetails(androidId)
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

                    val product = doc.getString("product_name") ?: "N/A"
                    val price = doc.get("price") ?: "0"
                    val procFee = doc.get("processing_fee") ?: "0"
                    val downPay = doc.get("down_payment") ?: "0"
                    val totalEmi = doc.get("total_emi") ?: "0"
                    val numEmi = doc.get("number_of_emi") ?: "0"
                    val monthlyAmt = doc.get("emi_monthly_amount") ?: "0"

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
