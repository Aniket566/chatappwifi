package com.example.tasknewcode

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    private lateinit var scanner: NetworkScanner
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DeviceListAdapter
    private val deviceList = mutableListOf<String>()

    private val messageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                println("Received broadcast: ${intent?.action}")
                val message = intent?.getStringExtra("message")
                if (!message.isNullOrEmpty()) {
                    println("Message content: $message")
                    Toast.makeText(context, "New message received!", Toast.LENGTH_SHORT).show()

                    val chatIntent = Intent("com.example.tasknewcode.NEW_MESSAGE")
                    chatIntent.putExtra("message", message)
                    sendBroadcast(chatIntent)
                    println("Forwarded message to chat activity")
                } else {
                    println("Received empty message")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                println("Error in message receiver: ${e.message}")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        println("MainActivity onCreate started")
        checkAndRequestPermissions()

        scanner = NetworkScanner(this)
        recyclerView = findViewById(R.id.recyclerViewDevices)
        val btnScan: Button = findViewById(R.id.btnScan)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = DeviceListAdapter(deviceList) { selectedIp ->
            navigateToChat(selectedIp, null)
        }
        recyclerView.adapter = adapter

        btnScan.setOnClickListener {
            scanDevices()
        }

        try {
            registerReceiver(
                messageReceiver,
                IntentFilter("com.example.tasknewcode.NEW_MESSAGE"),
                RECEIVER_NOT_EXPORTED
            )
            println("Initial message receiver registration successful")
        } catch (e: Exception) {
            e.printStackTrace()
            println("Error in initial receiver registration: ${e.message}")
        }
    }

    private fun checkAndRequestPermissions() {
        println("Checking permissions...")
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.INTERNET)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(android.Manifest.permission.INTERNET)
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_NETWORK_STATE)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(android.Manifest.permission.ACCESS_NETWORK_STATE)
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_WIFI_STATE)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(android.Manifest.permission.ACCESS_WIFI_STATE)
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_VIDEO)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.READ_MEDIA_VIDEO)
            }
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.READ_MEDIA_AUDIO)
            }
        }

        println("Permissions to request: $permissions")
        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        } else {
            println("All permissions already granted")
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.all { it.value }
            if (allGranted) {
                println("All permissions granted successfully")
                Toast.makeText(this, "All permissions granted!", Toast.LENGTH_SHORT).show()
            } else {
                println("Some permissions were denied: $permissions")
                Toast.makeText(this, "Some permissions denied. App functionality may be limited.", Toast.LENGTH_LONG).show()
            }
        }

    private fun scanDevices() {
        println("Starting network scan...")
        deviceList.clear()
        scanner.scanNetwork { devices ->
            println("Found ${devices.size} devices: $devices")
            deviceList.addAll(devices)
            runOnUiThread {
                adapter.notifyDataSetChanged()
                Toast.makeText(this, "Found ${devices.size} devices", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateToChat(ipAddress: String, message: String?) {
        println("Navigating to chat with IP: $ipAddress")
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("ip_address", ipAddress)
        intent.putExtra("message", message)
        startActivity(intent)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onResume() {
        super.onResume()
        try {
            registerReceiver(
                messageReceiver,
                IntentFilter("com.example.tasknewcode.NEW_MESSAGE"),
                RECEIVER_NOT_EXPORTED
            )
            println("Registered message receiver in onResume")
        } catch (e: Exception) {
            e.printStackTrace()
            println("Error registering receiver in onResume: ${e.message}")
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(messageReceiver)
            println("Unregistered message receiver in onPause")
        } catch (e: Exception) {
            e.printStackTrace()
            println("Error unregistering receiver in onPause: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(messageReceiver)
            println("Cleaned up resources in onDestroy")
        } catch (e: Exception) {
            println("Receiver already unregistered in onDestroy")
        }
    }
}