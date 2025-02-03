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

class MessageAdapter(private val context: Context, private val messages: List<ChatMessage>) :
    RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]

        if (message.isSender) {
            holder.senderText.text = message.text
            holder.senderContainer.visibility = View.VISIBLE
            holder.receiverContainer.visibility = View.GONE
        } else {
            holder.receiverText.text = message.text
            holder.senderContainer.visibility = View.GONE
            holder.receiverContainer.visibility = View.VISIBLE
        }

        if (message.filePath != null) {
            val file = File(message.filePath)
            val fileType = file.extension.lowercase()

            when (fileType) {
                "jpg", "jpeg", "png" -> {
                    holder.imagePreview.visibility = View.VISIBLE
                    holder.documentPreview.visibility = View.GONE
                    Glide.with(context).load(file).into(holder.imagePreview)
                }
                "pdf", "docx", "txt", "xlsx", "pptx" -> {
                    holder.imagePreview.visibility = View.GONE
                    holder.documentPreview.visibility = View.VISIBLE
                    holder.documentPreview.text = "📄 ${file.name}"
                    holder.documentPreview.setOnClickListener { openFile(file) }
                }
                else -> {
                    holder.imagePreview.visibility = View.GONE
                    holder.documentPreview.visibility = View.GONE
                }
            }
        } else {
            holder.imagePreview.visibility = View.GONE
            holder.documentPreview.visibility = View.GONE
        }
    }

    private fun openFile(file: File) {
        val uri: Uri = FileProvider.getUriForFile(context, "com.example.chatapp.provider", file)
        val mimeType = context.contentResolver.getType(uri) ?: "*/*"

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        try {
            context.startActivity(Intent.createChooser(intent, "Open file with"))
        } catch (e: Exception) {
            e.printStackTrace()
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
