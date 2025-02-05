package com.example.tasknewcode

interface MessageListener {
    fun onProgressUpdate(current: Int, total: Int, rate: Long, bufferUsed: Int)
    fun onError(message: String)
    fun onFileReceived(filePath: String)
}
