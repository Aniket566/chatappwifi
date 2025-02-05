package com.example.tasknewcode

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.wifi.p2p.*
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    private lateinit var wifiP2pManager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel
    private lateinit var receiver: BroadcastReceiver
    private lateinit var intentFilter: IntentFilter

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DeviceListAdapter
    private val deviceList = mutableListOf<WifiP2pDevice>()
    private var connectedDeviceIp: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        checkPermissions()

        wifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = wifiP2pManager.initialize(this, mainLooper, null)

        receiver = WiFiDirectBroadcastReceiver(wifiP2pManager, channel, this)
        intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }

        recyclerView = findViewById(R.id.recyclerViewDevices)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = DeviceListAdapter(deviceList, connectedDeviceIp,
            { device -> connectToDevice(device) },
            { ip -> navigateToNextActivity(ip) }
        )
        recyclerView.adapter = adapter

        findViewById<Button>(R.id.btnScan).setOnClickListener { discoverPeers() }
    }
    private fun checkPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                android.Manifest.permission.NEARBY_WIFI_DEVICES,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        if (!hasPermissions(permissions)) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSIONS_REQUEST_CODE)
        }
    }

    private fun hasPermissions(permissions: Array<String>): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSIONS_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    // Permissions granted, start discovery
                    discoverPeers()
                } else {
                    Toast.makeText(this, "Required permissions not granted", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 1001
    }

    @SuppressLint("MissingPermission")
    private fun discoverPeers() {
        wifiP2pManager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Toast.makeText(this@MainActivity, "Discovery started", Toast.LENGTH_SHORT).show()
            }
            override fun onFailure(reasonCode: Int) {
                Toast.makeText(this@MainActivity, "Discovery failed: $reasonCode", Toast.LENGTH_SHORT).show()
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: WifiP2pDevice) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            groupOwnerIntent = 0  // This device prefers not to be the owner
        }

        wifiP2pManager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Toast.makeText(this@MainActivity, "Connecting to ${device.deviceName}", Toast.LENGTH_SHORT).show()
                // Don't update IP here, wait for connection info
            }
            override fun onFailure(reason: Int) {
                Toast.makeText(this@MainActivity, "Connection failed: $reason", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun requestConnectionInfo() {
        wifiP2pManager.requestConnectionInfo(channel) { info ->
            // Check if this device is the group owner
            if (info.groupFormed) {
                val targetIp = if (info.isGroupOwner) {
                    // I am the group owner, use my address
                    "192.168.49.1"  // Default IP for group owner
                } else {
                    // Other device is group owner, use their address
                    info.groupOwnerAddress?.hostAddress
                }

                targetIp?.let { ip ->
                    println("WiFiP2P: Connection formed - isGroupOwner: ${info.isGroupOwner}, IP: $ip")
                    updateConnectedDeviceIp(ip)
                }
            }
        }
    }

//    private fun requestConnectionInfo() {
//        wifiP2pManager.requestConnectionInfo(channel) { info ->
//            info.groupOwnerAddress?.hostAddress?.let { ip ->
//                updateConnectedDeviceIp(ip)
//            }
//        }
//    }

    fun updateConnectedDeviceIp(ip: String?) {
        connectedDeviceIp = ip
        adapter.updateConnectedDeviceIp(ip ?: "")
        if (!ip.isNullOrEmpty() && ip != "Unknown") {
            Handler(Looper.getMainLooper()).postDelayed({
                navigateToNextActivity(ip)
            }, 1000)
        }
    }
    fun updateDeviceList(devices: List<WifiP2pDevice>) {
        deviceList.clear()
        deviceList.addAll(devices)
        adapter.notifyDataSetChanged()
    }


    private fun navigateToNextActivity(ip: String) {
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("IP_ADDRESS", ip)
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(receiver, intentFilter)
    }
//    private fun isNetworkAvailable(): Boolean {
//        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
//        val network = connectivityManager.activeNetwork
//        val capabilities = connectivityManager.getNetworkCapabilities(network)
//        return capabilities != null &&
//                (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
//                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
//    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }

   // connection error : bind failed eaddrinuse(Address alrady in use)
}
