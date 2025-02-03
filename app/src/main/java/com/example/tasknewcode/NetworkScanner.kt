package com.example.tasknewcode

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress

class NetworkScanner(private val context: Context) {
    fun getLocalIpAddress(): String? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        val ip = wifiInfo.ipAddress
        return String.format("%d.%d.%d.%d", ip and 0xFF, ip shr 8 and 0xFF, ip shr 16 and 0xFF, ip shr 24 and 0xFF)
    }

    fun getSubnet(ip: String): String {
        return ip.substringBeforeLast(".") + "."
    }

    fun scanNetwork(onResult: (List<String>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val localIp = getLocalIpAddress() ?: return@launch
            val subnet = getSubnet(localIp)
            val activeDevices = mutableListOf<String>()

            val jobs = (1..254).map { i ->
                async {
                    val testIp = "$subnet$i"
                    val address = InetAddress.getByName(testIp)
                    if (address.isReachable(300)) {
                        activeDevices.add(testIp)
                    }
                }
            }
            jobs.awaitAll()
            withContext(Dispatchers.Main) {
                onResult(activeDevices)
            }
        }
    }
}