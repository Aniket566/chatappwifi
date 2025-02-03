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
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageAdapter(private val context: Context, private val messages: List<ChatMessage>) :
    RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]

        if (message.isSender) {
            holder.senderContainer.visibility = View.VISIBLE
            holder.receiverContainer.visibility = View.GONE
            configureSenderMessage(holder, message)
        } else {
            holder.receiverContainer.visibility = View.VISIBLE
            holder.senderContainer.visibility = View.GONE
            configureReceiverMessage(holder, message)
        }
        handleFileAttachment(holder, message)
    }

    private fun configureSenderMessage(holder: MessageViewHolder, message: ChatMessage) {
        if (message.filePath != null) {
            val file = File(message.filePath)
            holder.senderText.text = "📎 ${file.name}"
        } else {
            holder.senderText.text = message.text
        }
    }

    private fun configureReceiverMessage(holder: MessageViewHolder, message: ChatMessage) {
        if (message.filePath != null) {
            val file = File(message.filePath)
            holder.receiverText.text = "📎 ${file.name}"
        } else {
            holder.receiverText.text = message.text
        }
    }

    private fun handleFileAttachment(holder: MessageViewHolder, message: ChatMessage) {
        holder.imagePreview.visibility = View.GONE
        holder.documentPreview.visibility = View.GONE

        message.filePath?.let { filePath ->
            val file = File(filePath)
            if (!file.exists()) return

            val fileType = file.extension.lowercase()

            when (fileType) {
                "jpg", "jpeg", "png", "gif" -> {
                    configureImagePreview(holder, file)
                }
                "pdf", "doc", "docx", "txt", "xlsx", "pptx", "csv" -> {
                    configureDocumentPreview(holder, file)
                }
                else -> {
                    configureGenericFilePreview(holder, file)
                }
            }
        }
    }

    private fun configureImagePreview(holder: MessageViewHolder, file: File) {
        holder.imagePreview.visibility = View.VISIBLE
        Glide.with(context)
            .load(file)
            .placeholder(R.drawable.ic_launcher_background)
            .error(R.drawable.ic_launcher_background)
            .into(holder.imagePreview)

        holder.imagePreview.setOnClickListener {
            openFile(file)
        }
    }

    private fun configureDocumentPreview(holder: MessageViewHolder, file: File) {
        holder.documentPreview.visibility = View.VISIBLE
        holder.documentPreview.text = "📄 ${file.name}"
        setFileClickListener(holder.documentPreview, file)
    }

    private fun configureGenericFilePreview(holder: MessageViewHolder, file: File) {
        holder.documentPreview.visibility = View.VISIBLE
        holder.documentPreview.text = "📎 ${file.name}"
        setFileClickListener(holder.documentPreview, file)
    }

    private fun setFileClickListener(view: View, file: File) {
        view.setOnClickListener { openFile(file) }
    }

    private fun openFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val mimeType = context.contentResolver.getType(uri) ?: "*/*"

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(intent, "Open file with"))
        } catch (e: Exception) {
            e.printStackTrace()
            // Show error toast or message to user
        }
    }

    override fun getItemCount(): Int = messages.size

    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val senderContainer: View = view.findViewById(R.id.senderContainer)
        val receiverContainer: View = view.findViewById(R.id.receiverContainer)
        val senderText: TextView = view.findViewById(R.id.senderText)
        val receiverText: TextView = view.findViewById(R.id.receiverText)
        val imagePreview: ImageView = view.findViewById(R.id.imagePreview)
        val documentPreview: TextView = view.findViewById(R.id.documentPreview)
    }
}