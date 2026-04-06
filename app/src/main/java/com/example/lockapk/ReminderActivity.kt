package com.example.lockapk

import android.media.RingtoneManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ReminderActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reminder)

        val messageTextView = findViewById<TextView>(R.id.messageTextView)
        val dismissButton = findViewById<Button>(R.id.dismissButton)

        val message = intent.getStringExtra("REMINDER_MESSAGE")
        if (!message.isNullOrEmpty()) {
            messageTextView.text = message
        }

        // Play default notification sound when the reminder appears
        try {
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(applicationContext, notification)
            ringtone.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        dismissButton.setOnClickListener {
            finish()
        }
    }
}
