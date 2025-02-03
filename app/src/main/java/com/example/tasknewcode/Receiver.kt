package com.example.tasknewcode

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

class Receiver(private val context: Context) {
    private var serverSocket: ServerSocket? = null
    private var isListening = false
    private val scope = CoroutineScope(Dispatchers.IO)
    private val activeClients = mutableListOf<Socket>()

    companion object {
        private const val MESSAGE_TYPE_TEXT = "TEXT"
        private const val MESSAGE_TYPE_FILE = "FILE"
        private const val BUFFER_SIZE = 8192
    }

    fun startListening(port: Int) {
        if (isListening) {
            println("Already listening")
            return
        }

        scope.launch {
            try {
                println("Starting server socket on port $port")
                serverSocket = ServerSocket(port)
                isListening = true
                println("Server socket started successfully")

                while (isListening) {
                    try {
                        serverSocket?.let { server ->
                            println("Waiting for incoming connection...")
                            val socket = server.accept()
                            println("Connection accepted from: ${socket.inetAddress}")

                            activeClients.add(socket)
                            handleClient(socket)
                        } ?: break
                    } catch (e: Exception) {
                        e.printStackTrace()
                        println("Error accepting connection: ${e.message}")
                        break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                println("Error starting server: ${e.message}")
            }
        }
    }


    private fun handleClient(socket: Socket) {
        scope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                while (isListening && !socket.isClosed) {
                    val message = reader.readLine()
                    if (message == null) {
                        println("Client disconnected")
                        break
                    }

                    println("Received message: $message")
                    withContext(Dispatchers.Main) {
                        val intent = Intent("com.example.tasknewcode.NEW_MESSAGE")
                        intent.putExtra("message", message)
                        context.sendBroadcast(intent)
                        println("Broadcasted message to app")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                println("Error handling client: ${e.message}")
            } finally {
                activeClients.remove(socket)
                try {
                    socket.close()
                    println("Closed client socket")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun stopListening() {
        println("Stopping server...")
        isListening = false

        activeClients.forEach { socket ->
            try {
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        activeClients.clear()

        try {
            serverSocket?.close()
            serverSocket = null
            println("Server stopped successfully")
        } catch (e: Exception) {
            e.printStackTrace()
            println("Error stopping server: ${e.message}")
        }
    }
}
