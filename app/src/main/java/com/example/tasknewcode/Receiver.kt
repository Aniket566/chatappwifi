package com.example.tasknewcode

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.*
import java.io.*
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList

class Receiver(
    private val context: Context,
    private val listener: MessageListener
) {
    private var serverSocket: ServerSocket? = null
    private var isListening = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeClients = CopyOnWriteArrayList<Socket>()
    private val bufferPool = BufferPool(BUFFER_POOL_SIZE)
    private val averageRateCounter = AverageRateCounter(5)

    companion object {
        private const val PORT = 8888
        private const val BUFFER_POOL_SIZE = 112 * 1024 * 1024
        private const val SOCKET_TIMEOUT = 30000
        private const val TAG = "WiFiP2PReceiver"
    }
    private class BufferPool(size: Int) {
        private val buffers = ArrayDeque<ByteBuffer>()
        private val bufferSize = 8192

        init {
            val numBuffers = size / bufferSize
            repeat(numBuffers) {
                buffers.add(ByteBuffer.allocate(bufferSize))
            }
        }

        fun acquire(): ByteBuffer {
            return synchronized(buffers) {
                buffers.removeFirstOrNull() ?: ByteBuffer.allocate(bufferSize)
            }
        }

        fun release(buffer: ByteBuffer) {
            buffer.clear()
            synchronized(buffers) {
                buffers.add(buffer)
            }
        }
    }
    private class AverageRateCounter(private val windowSize: Int) {
        private val rates = ArrayDeque<Long>()
        private var total: Long = 0

        fun add(bytes: Long) {
            synchronized(rates) {
                rates.add(bytes)
                total += bytes
                if (rates.size > windowSize) {
                    total -= rates.removeFirst()
                }
            }
        }

        fun getRate(): Long = synchronized(rates) {
            if (rates.isEmpty()) return 0
            return total / rates.size
        }
    }


    fun getLocalIpAddress(): String {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        val ipAddress = wifiInfo.ipAddress
        return String.format(
            "%d.%d.%d.%d",
            ipAddress and 0xff,
            ipAddress shr 8 and 0xff,
            ipAddress shr 16 and 0xff,
            ipAddress shr 24 and 0xff
        )
    }

    fun startListening() = scope.launch {
        if (isListening) return@launch

        try {
            serverSocket = ServerSocket(PORT).apply {
                soTimeout = 1000
                setPerformancePreferences(0, 0, 1)
                receiveBufferSize = 64 * 1024
            }
            isListening = true

            Log.d(TAG, "Server started on port $PORT with IP: ${getLocalIpAddress()}")

            while (isListening) {
                try {
                    val socket = serverSocket?.accept()
                    socket?.let {
                        it.apply {
                            soTimeout = SOCKET_TIMEOUT
                            setPerformancePreferences(0, 0, 1)
                            trafficClass = 0x08
                            tcpNoDelay = true
                        }
                        activeClients.add(it)
                        handleClient(it)
                    }
                } catch (e: SocketTimeoutException) {

                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Server error: ${e.message}")
            withContext(Dispatchers.Main) {
                listener.onError("Connection Error: ${e.message}")
            }
        }
    }
    private fun handleClient(socket: Socket) = scope.launch {
        try {
            val channel = Channel(BUFFER_POOL_SIZE)
            val progress = Progress()

            val progressJob = startProgressUpdates(progress, channel)

            receiveData(socket, channel, progress)

            progressJob.cancel()

        } catch (e: Exception) {
            Log.e(TAG, "Client error: ${e.message}")
        } finally {
            closeSocket(socket)
        }
    }
    private suspend fun receiveData(socket: Socket, channel: Channel, progress: Progress) {
        socket.getInputStream().use { input ->
            while (isListening && !socket.isClosed) {
                val buffer = bufferPool.acquire()
                var bytesRead = 0

                try {
                    while (buffer.hasRemaining()) {
                        val read = input.read(
                            buffer.array(),
                            buffer.arrayOffset() + buffer.position(),
                            buffer.remaining()
                        )
                        if (read < 0) break
                        bytesRead += read
                        buffer.position(buffer.position() + read)
                    }

                    buffer.flip()
                    if (bytesRead == 0) break

                    averageRateCounter.add(bytesRead.toLong())
                    channel.write(buffer)
                    progress.current = bytesRead.toLong()
                    progress.total = channel.getCapacity().toLong()

                } finally {
                    bufferPool.release(buffer)
                }
            }
        }
    }
    private fun startProgressUpdates(progress: Progress, channel: Channel) = scope.launch {
        while (isActive) {
            val rate = averageRateCounter.getRate()
            val used = channel.getCapacity() - channel.getAvailable()

            withContext(Dispatchers.Main) {
                listener.onProgressUpdate(
                    progress.current.toInt(),
                    progress.total.toInt(),
                    rate,
                    used
                )
            }
            delay(1000)
        }
    }
    private data class Progress(
        var current: Long = 0,
        var total: Long = 0
    )

    private class Channel(private val capacity: Int) {
        private val buffer = ByteArray(capacity)
        private var position = 0

        @Synchronized
        fun write(data: ByteBuffer) {
            val remaining = capacity - position
            val length = minOf(data.remaining(), remaining)
            System.arraycopy(data.array(), data.arrayOffset(), buffer, position, length)
            position += length
        }

        fun getCapacity() = capacity
        fun getAvailable() = capacity - position
    }





    private fun receiveFile(dataInputStream: DataInputStream, fileName: String, fileSize: Long) {
        scope.launch {
            try {
                val file = File(context.getExternalFilesDir(null), fileName)
                val fileOutputStream = FileOutputStream(file)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalReceived = 0L

                while (totalReceived < fileSize) {
                    val remaining = (fileSize - totalReceived).toInt().coerceAtMost(buffer.size)
                    bytesRead = dataInputStream.read(buffer, 0, remaining)
                    if (bytesRead == -1) break

                    fileOutputStream.write(buffer, 0, bytesRead)
                    totalReceived += bytesRead

                    val progress = ((totalReceived * 100) / fileSize).toInt()
                    withContext(Dispatchers.Main) {
                        //listener.onProgressUpdate(progress)
                    }
                }

                fileOutputStream.flush()
                fileOutputStream.close()

                withContext(Dispatchers.Main) {
                    listener.onFileReceived(file.absolutePath)
                }

                Log.d(TAG, "File received successfully: ${file.absolutePath}")

            } catch (e: IOException) {
                Log.e(TAG, "File transfer failed: ${e.message}")
            }
        }
    }


    private fun closeSocket(socket: Socket) {
        try {
            if (!socket.isClosed) {
                socket.close()
                Log.d(TAG, "Socket closed: ${socket.inetAddress}")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error closing socket: ${e.message}")
        }
        activeClients.remove(socket)
    }

    fun stopListening() {
        isListening = false
        activeClients.forEach { closeSocket(it) }
        activeClients.clear()
        try {
            serverSocket?.close()
            Log.d(TAG, "Server stopped")
        } catch (e: IOException) {
            Log.e(TAG, "Error stopping server: ${e.message}")
        }
        serverSocket = null
        scope.cancel()
    }
}