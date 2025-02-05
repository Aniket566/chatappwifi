package com.example.tasknewcode

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tasknewcode.room.ChatDatabase
import com.example.tasknewcode.room.MessageDao
import kotlinx.coroutines.*
import java.io.File

class ChatActivity : AppCompatActivity(), MessageListener {
    private lateinit var recyclerView: RecyclerView
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var etMessage: EditText
    private lateinit var tvIp: TextView
    private lateinit var btnSendFile: ImageButton
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var database: ChatDatabase
    private lateinit var messageDao: MessageDao
    private var sender: Sender? = null
    private var receiver: Receiver? = null
    private var ipAddress: String = "Unknown"
    private var localIpAddress: String = "Unknown"
    private var currentFileMessage: ChatMessage? = null

    companion object {
        private const val TAG = "ChatActivity"
        private const val CONNECTION_RETRY_DELAY = 3000L
        private const val MAX_CONNECTION_ATTEMPTS = 3
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        initializeViews()
        setupDatabase()
        setupRecyclerView()
        setupNetworking()
        setupClickListeners()
        loadPreviousMessages()
    }

    private fun initializeViews() {
        tvIp = findViewById(R.id.tvIp)
        recyclerView = findViewById(R.id.recyclerView)
        etMessage = findViewById(R.id.etMessage)
        btnSendFile = findViewById(R.id.btnSendFile)

        ipAddress = intent.getStringExtra("IP_ADDRESS") ?: "Unknown"
        tvIp.text = "Chat with: $ipAddress"
    }

    private fun setupDatabase() {
        database = ChatDatabase.getDatabase(this)
        messageDao = database.messageDao()
    }

    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter(this, messages)
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity)
            adapter = messageAdapter
        }
    }

    private fun setupNetworking() {
        receiver = Receiver(this, this)
        localIpAddress = receiver?.getLocalIpAddress() ?: "Unknown"
        Log.d(TAG, "Local IP: $localIpAddress")

        receiver?.startListening()
        connectSender()
    }

    private fun connectSender(attempts: Int = 0) {
        if (sender != null) return

        sender = Sender(this)
        sender?.connect(ipAddress, object : Sender.TransferCallback {
            override fun onProgress(progress: Int, speed: Long, bufferUsed: Int) {

            }

            override fun onComplete(success: Boolean) {
                runOnUiThread {
                    if (success) {
                        Toast.makeText(
                            this@ChatActivity,
                            "Connected to $ipAddress",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        if (attempts < MAX_CONNECTION_ATTEMPTS) {
                            lifecycleScope.launch {
                                delay(CONNECTION_RETRY_DELAY)
                                connectSender(attempts + 1)
                            }
                        } else {
                            Toast.makeText(
                                this@ChatActivity,
                                "Failed to connect after multiple attempts",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }

            override fun onError(message: String) {
                runOnUiThread {
                    Toast.makeText(
                        this@ChatActivity,
                        message,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })
    }

    private fun setupClickListeners() {
        findViewById<ImageButton>(R.id.btnSend).setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
                etMessage.text.clear()
            }
        }

        btnSendFile.setOnClickListener {
            selectFile()
        }
    }

    private fun sendMessage(text: String) {
        val message = ChatMessage(
            ipAddress = ipAddress,
            text = text,
            isSender = true,
            filePath = null,
            timestamp = System.currentTimeMillis()
        )

        lifecycleScope.launch(Dispatchers.IO) {
            messageDao.insertMessage(message)

            withContext(Dispatchers.Main) {
                messages.add(message)
                messageAdapter.notifyItemInserted(messages.lastIndex)
                recyclerView.scrollToPosition(messages.lastIndex)

//                sender?.(text) {
//                    runOnUiThread {
//                        Toast.makeText(
//                            this@ChatActivity,
//                            "Failed to send message. Retrying...",
//                            Toast.LENGTH_SHORT
//                        ).show()
//                    }
//                }
            }
        }
    }

    private fun selectFile() {
        filePicker.launch("*/*")
    }

//    private val filePicker =
//        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
//            uri?.let { fileUri ->
//                val fileName = getFileName(fileUri)
//                val message = ChatMessage(
//                    ipAddress = ipAddress,
//                    text = "📎 Sending file: $fileName",
//                    isSender = true,
//                    filePath = fileUri.toString(),
//                    timestamp = System.currentTimeMillis()
//                )
//
//                lifecycleScope.launch(Dispatchers.IO) {
//                    messageDao.insertMessage(message)
//
//                    withContext(Dispatchers.Main) {
//                        messages.add(message)
//                        messageAdapter.notifyItemInserted(messages.lastIndex)
//                        recyclerView.scrollToPosition(messages.lastIndex)
//                        sender?.sendFile(fileUri)
//                    }
//                }
//            }
//        }

    private fun loadPreviousMessages() {
        lifecycleScope.launch(Dispatchers.IO) {
            val previousMessages = messageDao.getAllMessages()

            withContext(Dispatchers.Main) {
                messages.clear()
                messages.addAll(previousMessages)
                messageAdapter.notifyDataSetChanged()
                recyclerView.scrollToPosition(messages.lastIndex)
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            } else null
        } ?: "unknown_file"
    }

    override fun onDestroy() {
        super.onDestroy()
        sender?.disconnect()
        receiver?.stopListening()
    }

    override fun onProgressUpdate(current: Int, total: Int, rate: Long, bufferUsed: Int) {
        currentFileMessage?.let { message ->
            if (message.text.startsWith("📎")) {
                val progress = if (total > 0) (current * 100 / total) else 0
                val speed = formatSpeed(rate)
                val buffer = formatSize(bufferUsed.toLong())
                message.text = "${message.text.split("(")[0]} ($progress% | $speed/s | Buffer: $buffer)"
                runOnUiThread {
                    messageAdapter.notifyItemChanged(messages.indexOf(message))
                }
            }
        }
    }

    override fun onError(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            currentFileMessage?.let { fileMessage ->
                fileMessage.text = "${fileMessage.text.split("(")[0]} (Failed: $message)"
                messageAdapter.notifyItemChanged(messages.indexOf(fileMessage))
            }
        }
    }

    override fun onFileReceived(filePath: String) {
        val fileName = File(filePath).name
        val chatMessage = ChatMessage(
            ipAddress = ipAddress,
            text = "📎 Received file: $fileName (Completed)",
            isSender = false,
            filePath = filePath,
            timestamp = System.currentTimeMillis()
        )

        lifecycleScope.launch(Dispatchers.IO) {
            messageDao.insertMessage(chatMessage)

            withContext(Dispatchers.Main) {
                messages.add(chatMessage)
                messageAdapter.notifyItemInserted(messages.lastIndex)
                recyclerView.scrollToPosition(messages.lastIndex)
                currentFileMessage = null
            }
        }
    }

    private fun formatSpeed(bytesPerSecond: Long): String {
        return when {
            bytesPerSecond >= 1024 * 1024 -> "%.2f MB".format(bytesPerSecond / (1024.0 * 1024.0))
            bytesPerSecond >= 1024 -> "%.2f KB".format(bytesPerSecond / 1024.0)
            else -> "$bytesPerSecond B"
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> "%.2f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    private val filePicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { fileUri ->
                val fileName = getFileName(fileUri)
                val message = ChatMessage(
                    ipAddress = ipAddress,
                    text = "📎 Sending file: $fileName",
                    isSender = true,
                    filePath = fileUri.toString(),
                    timestamp = System.currentTimeMillis()
                )

                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        messageDao.insertMessage(message)
                    }

                    withContext(Dispatchers.Main) {
                        messages.add(message)
                        currentFileMessage = message
                        messageAdapter.notifyItemInserted(messages.lastIndex)
                        recyclerView.scrollToPosition(messages.lastIndex)
                    }

                    withContext(Dispatchers.IO) {
                        sender?.sendFile(fileUri, object : Sender.TransferCallback {
                            override fun onProgress(progress: Int, speed: Long, bufferUsed: Int) {
                                lifecycleScope.launch(Dispatchers.Main) {
                                    onProgressUpdate(progress, 100, speed, bufferUsed)
                                }
                            }

                            override fun onComplete(success: Boolean) {
                                lifecycleScope.launch(Dispatchers.Main) {
                                    if (success) {
                                        message.text = "${message.text.split("(")[0]} (Completed)"
                                    }
                                    messageAdapter.notifyItemChanged(messages.indexOf(message))
                                    currentFileMessage = null
                                }
                            }

                            override fun onError(errorMessage: String) {
                                lifecycleScope.launch(Dispatchers.Main) {
                                    Toast.makeText(this@ChatActivity, "Error: $errorMessage", Toast.LENGTH_SHORT).show()
                                }
                            }
                        })
                    }
                }
            }
        }

}
