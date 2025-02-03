package com.example.tasknewcode

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class MessageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val message = intent?.getStringExtra("message")
        if (!message.isNullOrEmpty()) {
            Toast.makeText(context, "New message received: $message", Toast.LENGTH_SHORT).show()
        }
    }
}
