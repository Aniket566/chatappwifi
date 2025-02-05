package com.example.tasknewcode

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File

class MessageAdapter(private val context: Context, private val messages: List<ChatMessage>) :
    RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]

        holder.apply {
            senderContainer.isVisible = message.isSender
            receiverContainer.isVisible = !message.isSender
            imagePreview.isVisible = false
            documentPreview.isVisible = false

            if (message.isSender) {
                senderText.text = getDisplayText(message)
            } else {
                receiverText.text = getDisplayText(message)
            }

            message.filePath?.let { filePath ->
                val file = File(filePath)
                when (getFileType(file)) {
                    "image" -> {
                        imagePreview.isVisible = true
                        Glide.with(context).load(file).into(imagePreview)
                    }
                    "document" -> {
                        documentPreview.isVisible = true
                        documentPreview.text = "📄 ${file.name}"
                        documentPreview.setOnClickListener { openFile(file) }
                    }
                }
            }
        }
    }

    private fun getDisplayText(message: ChatMessage) = when {
        message.text.startsWith("📎 Sending file:") -> "File: ${message.text.substring(15)}"
        message.text.startsWith("📎 Received file:") -> "File: ${message.text.substring(16)}"
        else -> message.text
    }

    private fun getFileType(file: File) = when (file.extension.lowercase()) {
        in setOf("jpg", "jpeg", "png", "gif") -> "image"
        in setOf("pdf", "docx", "txt", "xlsx", "pptx") -> "document"
        else -> "unknown"
    }

    private fun openFile(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val mimeType = context.contentResolver.getType(uri) ?: "*/*"

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        try {
            context.startActivity(Intent.createChooser(intent, "Open file"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getItemCount() = messages.size

    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val senderContainer: View = view.findViewById(R.id.senderContainer)
        val receiverContainer: View = view.findViewById(R.id.receiverContainer)
        val senderText: TextView = view.findViewById(R.id.senderText)
        val receiverText: TextView = view.findViewById(R.id.receiverText)
        val imagePreview: ImageView = view.findViewById(R.id.imagePreview)
        val documentPreview: TextView = view.findViewById(R.id.documentPreview)
    }
}