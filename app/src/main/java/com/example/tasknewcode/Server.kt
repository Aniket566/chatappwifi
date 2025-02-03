package com.example.tasknewcode

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket

class Server(private val onMessageReceived: (String) -> Unit) {

    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    fun startServer(port: Int) {
        isRunning = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = ServerSocket(port)
                while (isRunning) {
                    val client: Socket = serverSocket!!.accept()
                    handleClient(client)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    private fun handleClient(client: Socket) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                while (isRunning) {
                    val message = reader.readLine() ?: break
                    withContext(Dispatchers.Main) {
                        onMessageReceived(message)
                    }
                }
                client.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopServer() {
        isRunning = false
        serverSocket?.close()
    }
}