package com.example.tasknewcode

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.*
import java.io.*
import java.net.Socket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class Sender(private val context: Context) {
    private var socket: Socket? = null
    private var outputStream: DataOutputStream? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val bufferPool = BufferPool(BUFFER_POOL_SIZE)
    private val averageRateCounter = AverageRateCounter(5)

    companion object {
        private const val PORT = 8888
        private const val CONNECTION_TIMEOUT = 4000
        private const val SOCKET_TIMEOUT = 30000
        private const val BUFFER_POOL_SIZE = 8 * 1024 * 1024
        private const val TCP_BUFFER_SIZE = 32 * 1024
        private const val TAG = "WiFiP2PSender"
    }

    private class BufferPool(poolSize: Int) {
        private val buffers = ArrayDeque<ByteBuffer>()
        private val bufferSize = 8192

        init {
            val numBuffers = poolSize / bufferSize
            repeat(numBuffers) {
                buffers.add(ByteBuffer.allocate(bufferSize))
            }
        }

        @Synchronized
        fun acquire(): ByteBuffer {
            return buffers.removeFirstOrNull() ?: ByteBuffer.allocate(bufferSize)
        }

        @Synchronized
        fun release(buffer: ByteBuffer) {
            buffer.clear()
            buffers.add(buffer)
        }
    }

    private class AverageRateCounter(private val windowSize: Int) {
        private val rates = ArrayDeque<Long>()
        private var total: Long = 0

        @Synchronized
        fun add(bytes: Long) {
            rates.add(bytes)
            total += bytes
            if (rates.size > windowSize) {
                total -= rates.removeFirst()
            }
        }

        fun getRate(): Long = total / windowSize
    }

    private class TransferProgress(
        var currentBytes: Long = 0,
        var totalBytes: Long = 0,
        var currentFile: String = "",
        var bufferUsed: Int = 0
    )

    interface TransferCallback {
        fun onProgress(progress: Int, speed: Long, bufferUsed: Int)
        fun onComplete(success: Boolean)
        fun onError(message: String)
    }

    fun connect(ipAddress: String, callback: TransferCallback) {
        scope.launch {
            try {
                socket = Socket().apply {
                    setPerformancePreferences(0, 0, 1)
                    trafficClass = 0x08
                    sendBufferSize = TCP_BUFFER_SIZE
                    soTimeout = SOCKET_TIMEOUT
                    setSoLinger(true, 0)
                    tcpNoDelay = true
                    connect(InetSocketAddress(ipAddress, PORT), CONNECTION_TIMEOUT)
                }
                outputStream = DataOutputStream(BufferedOutputStream(socket?.getOutputStream()))
                callback.onComplete(true)
                Log.d(TAG, "Connected to $ipAddress:$PORT")
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed: ${e.message}")
                callback.onError("Connection failed: ${e.message}")
                callback.onComplete(false)
            }
        }
    }

    fun sendFile(uri: Uri, callback: TransferCallback) {
        scope.launch {
            val progress = TransferProgress()
            val progressJob = startProgressUpdates(progress, callback)

            try {
                val fileInfo = getFileInfo(uri)
                progress.apply {
                    totalBytes = fileInfo.size
                    currentFile = fileInfo.name
                }

                if (socket == null || socket!!.isClosed) {
                    throw IOException("Socket is closed before sending file")
                }

                outputStream?.apply {
                    writeUTF("FILE:${fileInfo.name}")
                    writeLong(fileInfo.size)
                    flush()
                }

                context.contentResolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        outputStream?.write(buffer, 0, bytesRead)
                        outputStream?.flush()
                        progress.currentBytes += bytesRead
                        progress.bufferUsed = bytesRead
                    }
                }

                outputStream?.flush()
                callback.onComplete(true)
                Log.d(TAG, "File sent successfully: ${fileInfo.name}")
            } catch (e: IOException) {
                Log.e(TAG, "Failed to send file: ${e.message}")
                callback.onError("Failed to send file: ${e.message}")
                callback.onComplete(false)
            } finally {
                progressJob.cancel()
            }
        }
    }


    private fun startProgressUpdates(
        progress: TransferProgress,
        callback: TransferCallback
    ) = scope.launch {
        while (isActive) {
            if (progress.totalBytes > 0) {
                val percentage = ((progress.currentBytes * 100) / progress.totalBytes).toInt()
                val speed = averageRateCounter.getRate()
                callback.onProgress(percentage, speed, progress.bufferUsed)
            }
            delay(1000)
        }
    }

    private data class FileInfo(val name: String, val size: Long)

    private fun getFileInfo(uri: Uri): FileInfo {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex("_display_name")
                val sizeIndex = cursor.getColumnIndex("_size")
                FileInfo(
                    name = if (nameIndex != -1) cursor.getString(nameIndex) else "unknown",
                    size = if (sizeIndex != -1) cursor.getLong(sizeIndex) else 0
                )
            } else {
                FileInfo("unknown", 0)
            }
        } ?: FileInfo("unknown", 0)
    }

    fun disconnect() {
        scope.launch {
            try {
                outputStream?.close()
                socket?.apply {
                    setSoLinger(true, 0)
                    close()
                }
                Log.d(TAG, "Disconnected")
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting: ${e.message}")
            } finally {
                socket = null
                outputStream = null
            }
        }
    }
}