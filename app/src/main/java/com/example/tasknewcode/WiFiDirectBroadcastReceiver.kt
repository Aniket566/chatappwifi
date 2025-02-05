package com.example.tasknewcode

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.p2p.*

class WiFiDirectBroadcastReceiver(
    private val manager: WifiP2pManager,
    private val channel: WifiP2pManager.Channel,
    private val activity: MainActivity
) : BroadcastReceiver() {

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    println("WiFiP2P: Wi-Fi P2P is enabled")
                } else {
                    println("WiFiP2P: Wi-Fi P2P is not enabled")
                }
            }

            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                manager.requestPeers(channel) { peers ->
                    activity.updateDeviceList(peers.deviceList.toList())
                }
            }

            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)

                if (networkInfo?.isConnected == true) {
                    println("WiFiP2P: Connection established, requesting connection info")
                    manager.requestConnectionInfo(channel) { info ->
                        if (info.groupFormed) {
                            val targetIp = if (info.isGroupOwner) {
                                "192.168.49.1"
                            } else {
                                info.groupOwnerAddress?.hostAddress
                            }

                            targetIp?.let { ip ->
                                println("WiFiP2P: Group formed - isGroupOwner: ${info.isGroupOwner}, using IP: $ip")
                                activity.updateConnectedDeviceIp(ip)
                            }
                        }
                    }
                } else {
                    println("WiFiP2P: Disconnected")
                    activity.updateConnectedDeviceIp("")
                }
            }
        }
    }


}
