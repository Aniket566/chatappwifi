package com.example.tasknewcode

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.example.tasknewcode.room.ChatDatabase
import com.example.tasknewcode.room.MessageDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ChatActivity : AppCompatActivity() {
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
    private var ipAddress: String? = null

    private val messageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.example.tasknewcode.NEW_MESSAGE" -> {
                    val message = intent.getStringExtra("message")
                    if (message != null) {
                        runOnUiThread {
                            receiveMessage(message)
                        }
                    }
                }
                "com.example.tasknewcode.NEW_FILE" -> {
                    val filePath = intent.getStringExtra("filePath")
                    if (filePath != null) {
                        runOnUiThread {
                            receiveFile(filePath)
                        }
                    }
                }
                "com.example.tasknewcode.PROGRESS_UPDATE" -> {
                    val progress = intent.getIntExtra("progress", 0)
                    runOnUiThread {
                        updateProgress(progress)
                    }
                }
            }
        }
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
        registerReceivers()
        loadPreviousMessages()
    }

    private fun initializeViews() {
        tvIp = findViewById(R.id.tvIp)
        recyclerView = findViewById(R.id.recyclerView)
        etMessage = findViewById(R.id.etMessage)
        btnSendFile = findViewById(R.id.btnSendFile)

        ipAddress = intent.getStringExtra("ip_address") ?: "Unknown IP"
        tvIp.text = "Chat with: $ipAddress"
    }

    private fun setupDatabase() {
        database = ChatDatabase.getDatabase(this@ChatActivity)
        messageDao = database.messageDao()
    }

    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter(this, messages)
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity)
            adapter = messageAdapter
            addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
                if (bottom < oldBottom) {
                    recyclerView.postDelayed({
                        recyclerView.scrollToPosition(messages.size - 1)
                    }, 100)
                }
            }
        }
    }

    private fun setupNetworking() {
        println("Setting up networking with IP: $ipAddress")
        receiver = Receiver(this).apply {
            println("Starting receiver...")
            startListening(9999)
        }
        
        sender = Sender(this).apply {
            ipAddress?.let { ip ->
                println("Connecting sender to $ip")
                connect(ip, 9999)
            }
        }
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

    @SuppressLint("NewApi")
    private fun registerReceivers() {
        val intentFilter = IntentFilter().apply {
            addAction("com.example.tasknewcode.NEW_MESSAGE")
            addAction("com.example.tasknewcode.NEW_FILE")
            addAction("com.example.tasknewcode.PROGRESS_UPDATE")
        }
        registerReceiver(messageReceiver, intentFilter, RECEIVER_NOT_EXPORTED)
    }

    private fun loadPreviousMessages() {
        lifecycleScope.launch {
            val previousMessages = withContext(Dispatchers.IO) {
                messageDao.getAllMessages()
            }
            messages.addAll(previousMessages)
            messageAdapter.notifyDataSetChanged()
            recyclerView.scrollToPosition(messages.size - 1)
        }
    }

    private fun sendMessage(text: String) {
        ipAddress?.let { ip ->
            val message = ChatMessage(
                ipAddress = ip,
                text = text,
                isSender = true,
                timestamp = System.currentTimeMillis()
            )

            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    messageDao.insertMessage(message)
                }

                withContext(Dispatchers.Main) {
                    messages.add(message)
                    messageAdapter.notifyItemInserted(messages.size - 1)
                    recyclerView.scrollToPosition(messages.size - 1)

                    // Send the message through network
                    sender?.sendMessage(text)
                }
            }
        }
    }

    private fun receiveMessage(text: String) {
        ipAddress?.let { ip ->
            val message = ChatMessage(
                ipAddress = ip,
                text = text,
                isSender = false,
                timestamp = System.currentTimeMillis()
            )

            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    messageDao.insertMessage(message)
                }

                // Update UI after database operation
                withContext(Dispatchers.Main) {
                    messages.add(message)
                    messageAdapter.notifyItemInserted(messages.size - 1)
                    recyclerView.scrollToPosition(messages.size - 1)
                }
            }
        }
    }

    private fun receiveFile(filePath: String) {
        ipAddress?.let { ip ->
            val fileName = File(filePath).name
            val message = ChatMessage(
                ipAddress = ip,
                text = "📎 Received file: $fileName",
                isSender = false,
                timestamp = System.currentTimeMillis(),
                filePath = filePath  // Make sure this is set
            )

            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    messageDao.insertMessage(message)
                }

                // Update UI after database operation
                withContext(Dispatchers.Main) {
                    messages.add(message)
                    messageAdapter.notifyItemInserted(messages.size - 1)
                    recyclerView.scrollToPosition(messages.size - 1)
                }
            }
        }
    }

    private fun selectFile() {
        filePicker.launch("*/*")
    }

    private val filePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { fileUri ->
            val fileName = getFileName(fileUri)
            val tempMessage = ChatMessage(
                ipAddress = ipAddress ?: "Unknown",
                text = "📎 Sending file: $fileName",
                isSender = true,
                timestamp = System.currentTimeMillis()
            )

            lifecycleScope.launch {
                // First add temporary message
                withContext(Dispatchers.IO) {
                    messageDao.insertMessage(tempMessage)
                }

                withContext(Dispatchers.Main) {
                    messages.add(tempMessage)
                    messageAdapter.notifyItemInserted(messages.size - 1)
                    recyclerView.scrollToPosition(messages.size - 1)

                    sender?.sendFile(fileUri)
                }
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                result = result?.substring(cut!! + 1)
            }
        }
        return result ?: "unknown_file"
    }
    private fun updateProgress(progress: Int) {
        val lastMessage = messages.lastOrNull()
        if (lastMessage?.text?.startsWith("📎") == true) {
            lastMessage.text = "${lastMessage.text} ($progress%)"
            val position = messages.size - 1
            messageAdapter.notifyItemChanged(position)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(messageReceiver)
        sender?.disconnect()
        receiver?.stopListening()
    }
}
