package com.example.tasknewcode

import android.net.Uri
import android.content.Context
import android.content.Intent
import android.provider.OpenableColumns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.*
import java.net.Socket

class Sender(private val context: Context) {
    private var socket: Socket? = null
    private var outputStream: DataOutputStream? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var writer: PrintWriter? = null
    private var isConnected = false

    companion object {
        private const val MESSAGE_TYPE_TEXT = "TEXT"
        private const val MESSAGE_TYPE_FILE = "FILE"
        private const val HEADER_END = "\n"
        private const val BUFFER_SIZE = 8192
    }

    fun connect(ip: String, port: Int) {
        scope.launch {
            try {
                if (socket?.isConnected == true) {
                    println("Already connected, disconnecting first")
                    disconnect()
                }

                println("Connecting to $ip:$port")
                socket = Socket(ip, port)
                writer = socket?.getOutputStream()?.let {
                    PrintWriter(BufferedWriter(OutputStreamWriter(it)), true)
                }

                if (writer != null) {
                    isConnected = true
                    println("Connected successfully to $ip:$port")
                } else {
                    println("Failed to create writer")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                println("Connection failed: ${e.message}")
                isConnected = false
            }
        }
    }


    fun sendMessage(message: String) {
        if (!isConnected) {
            println("Not connected, cannot send message")
            return
        }

        scope.launch {
            try {
                println("Attempting to send message: $message")
                writer?.let { writer ->
                    writer.println(message)
                    writer.flush()
                    println("Message sent successfully")
                } ?: println("Writer is null, message not sent")
            } catch (e: Exception) {
                e.printStackTrace()
                println("Failed to send message: ${e.message}")
                isConnected = false
                reconnect()
            }
        }
    }
    fun sendFile(fileUri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val inputStream = context.contentResolver.openInputStream(fileUri)
                val fileName = getFileName(fileUri)
                val fileSize = getFileSize(fileUri)

                println("Sending file: $fileName (Size: $fileSize bytes)")

                val header = "$MESSAGE_TYPE_FILE:$fileName:$fileSize$HEADER_END"
                outputStream?.write(header.toByteArray())

                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead = 0
                var totalSent = 0L

                while (inputStream?.read(buffer).also { bytesRead = it ?: -1 } != -1) {
                    outputStream?.write(buffer, 0, bytesRead)
                    totalSent += bytesRead

                    val progress = (totalSent * 100 / fileSize).toInt()
                    val progressIntent = Intent("com.example.tasknewcode.PROGRESS_UPDATE")
                    progressIntent.putExtra("progress", progress)
                    context.sendBroadcast(progressIntent)

                    println("Progress: $totalSent/$fileSize bytes")
                }
                outputStream?.flush()
                inputStream?.close()

                println("File sent successfully")
            } catch (e: Exception) {
                e.printStackTrace()
                println("Failed to send file: ${e.message}")
                reconnect()
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex != -1) {
                return cursor.getString(nameIndex)
            }
        }
        return "unknown_file_${System.currentTimeMillis()}"
    }

    private fun getFileSize(uri: Uri): Long {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst() && sizeIndex != -1) {
                return cursor.getLong(sizeIndex)
            }
        }
        return -1L
    }

    private fun reconnect() {
        socket?.let { existingSocket ->
            connect(existingSocket.inetAddress.hostAddress, existingSocket.port)
        }
    }

    fun disconnect() {
        try {
            isConnected = false
            writer?.close()
            socket?.close()
            writer = null
            socket = null
            println("Disconnected and cleaned up resources")
        } catch (e: Exception) {
            e.printStackTrace()
            println("Error during disconnect: ${e.message}")
        }
    }

    fun isConnected(): Boolean = isConnected
}
