package com.example.tasknewcode

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DeviceListAdapter(
    private val devices: List<String>,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<DeviceListAdapter.DeviceViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val ip = devices[position]
        holder.tvDeviceIp.text = ip
        holder.itemView.setOnClickListener { onItemClick(ip) }
    }

    override fun getItemCount(): Int = devices.size

    class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDeviceIp: TextView = view.findViewById(R.id.tvDeviceIp)
    }
}
