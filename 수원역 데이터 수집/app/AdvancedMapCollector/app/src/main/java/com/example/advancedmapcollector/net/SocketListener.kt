package com.example.advancedmapcollector.net

interface SocketListener {
    fun onConnect()
    fun onDisconnect()
    fun onError(e: Exception?)
    fun onReceive(msg: String?)
    fun onSend(msg: String?)

    fun onLogPrint(msg: String?)
}