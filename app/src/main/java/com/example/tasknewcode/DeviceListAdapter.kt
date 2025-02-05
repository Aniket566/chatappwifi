package com.example.tasknewcode

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.net.wifi.p2p.WifiP2pDevice

class DeviceListAdapter(
    private val devices: List<WifiP2pDevice>,
    private var connectedDeviceIp: String?,
    private val onDeviceClick: (WifiP2pDevice) -> Unit,
    private val onConnectedDeviceClick: (String) -> Unit
) : RecyclerView.Adapter<DeviceListAdapter.DeviceViewHolder>() {

    class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDeviceName: TextView = view.findViewById(R.id.tvDecName)
        val tvDeviceStatus: TextView = view.findViewById(R.id.tvDeviceIp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]

        // Set device name
        holder.tvDeviceName.text = device.deviceName ?: "Unknown Device"

        // Set status text
        holder.tvDeviceStatus.text = when {
            device.status == WifiP2pDevice.CONNECTED && connectedDeviceIp != null -> {
                if (device.isGroupOwner) {
                    "Connected (Group Owner) - $connectedDeviceIp"
                } else {
                    "Connected (Client) - $connectedDeviceIp"
                }
            }
            else -> getDeviceStatus(device.status)
        }

        // Set click listener
        holder.itemView.setOnClickListener {
            if (device.status == WifiP2pDevice.CONNECTED && connectedDeviceIp != null) {
                onConnectedDeviceClick(connectedDeviceIp!!)
            } else {
                onDeviceClick(device)
            }
        }
    }

    override fun getItemCount(): Int = devices.size

    fun updateConnectedDeviceIp(ip: String) {
        connectedDeviceIp = ip
        notifyDataSetChanged()
    }

    private fun getDeviceStatus(deviceStatus: Int): String {
        return when (deviceStatus) {
            WifiP2pDevice.AVAILABLE -> "Available"
            WifiP2pDevice.INVITED -> "Invited"
            WifiP2pDevice.CONNECTED -> "Connected"
            WifiP2pDevice.FAILED -> "Failed"
            WifiP2pDevice.UNAVAILABLE -> "Unavailable"
            else -> "Unknown"
        }
    }
}