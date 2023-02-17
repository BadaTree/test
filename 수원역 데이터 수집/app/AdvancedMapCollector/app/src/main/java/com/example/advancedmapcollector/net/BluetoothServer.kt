package com.example.advancedmapcollector.net

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

class BluetoothServer {

    private var btAdapter = BluetoothAdapter.getDefaultAdapter()
    private var acceptThread: AcceptThread? = null
    private var commThread : CommThread? = null
    private var inputStream = arrayOfNulls<InputStream?>(2)
    private var outputStream = arrayOfNulls<OutputStream?>(2)
    private var socketListener: SocketListener? = null
    private var devicecount: Int = 2
    private var i: Int =0


    fun setOnSocketListener(listener: SocketListener?) {
        socketListener = listener
    }

    fun onConnect() {
        socketListener?.onConnect()
    }

    fun onDisconnect() {
        socketListener?.onDisconnect()
    }

    fun onLogPrint(message: String?) {
        socketListener?.onLogPrint(message)
    }

    fun onError(e: Exception) {
        socketListener?.onError(e)
    }

    fun onReceive(msg: String) {
        socketListener?.onReceive(msg)
    }

    fun onSend(msg: String) {
        socketListener?.onSend(msg)
    }

    fun accept() {
        stop()

        onLogPrint("Waiting for accept the client..")
        acceptThread = AcceptThread()
        acceptThread?.start()
    }

    fun stop() {
        if (acceptThread == null) return

        try {
            acceptThread?.let {
                onLogPrint("Stop accepting")

                it.stopThread()
                it.join(1000)
                it.interrupt()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    inner class AcceptThread : Thread() {
        private var acceptSocket: BluetoothServerSocket? = null
        private var socket: BluetoothSocket? = null

        override fun run() {
            while (true) {
                socket = try {
                    acceptSocket?.accept()
                } catch (e: Exception) {
                    e.printStackTrace()
                    break
                }
                if(socket != null) {
                    outputStream[i] = socket?.outputStream
                    inputStream[i] = socket?.inputStream
                    socket = null
                    i++
                    if(i==2){
                        i=0
                        break
                    }
                  }
            }
        }

        fun stopThread() {
            try {
                acceptSocket?.close()
                socket?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        init {
            try {
                acceptSocket = btAdapter.listenUsingRfcommWithServiceRecord(
                    "bluetoothTest",
                    BTConstant.BLUETOOTH_UUID_INSECURE)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    internal inner class CommThread(private val socket: BluetoothSocket?): Thread() {

        override fun run() {
            try {
//                for(i in outputStream.indices){
//                    outputStream[i] = socket?.outputStream
//                    inputStream[i] = socket?.inputStream

//                }

            } catch (e: Exception) {
                e.printStackTrace()
            }

            var len: Int
            val buffer = ByteArray(1024)
            val byteArrayOutputStream = ByteArrayOutputStream()

            while (true) {
                try {

                    len = socket?.inputStream?.read(buffer)!!
                    val data = buffer.copyOf(len)
                    byteArrayOutputStream.write(data)

                    socket.inputStream?.available()?.let { available ->

                        if (available == 0) {
                            val dataByteArray = byteArrayOutputStream.toByteArray()
                            val dataString = String(dataByteArray)
                            onReceive(dataString)

                            byteArrayOutputStream.reset()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    stopThread()
                    accept()
                    break
                }
            }
        }

        fun stopThread() = try {
            for(i in outputStream.indices){
            inputStream[i]?.close()
            outputStream[i]?.close()}
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun sendData(msg: String) {
        if (outputStream == null) return
0
        try {
            for(i in outputStream.indices){
                outputStream[i]?.let {
                    onSend(msg)
                it.write(msg.toByteArray())
                it.flush()}}



        } catch (e: Exception) {
            onError(e)
            e.printStackTrace()
            stop()
        }
    }
}