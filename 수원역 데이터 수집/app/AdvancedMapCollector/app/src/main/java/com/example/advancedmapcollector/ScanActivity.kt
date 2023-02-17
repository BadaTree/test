package com.example.advancedmapcollector

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.advancedmapcollector.adapter.DeviceAdapter
import com.example.advancedmapcollector.net.BluetoothClient

class ScanActivity : AppCompatActivity() {
    private var rvDevices: RecyclerView? = null
    private lateinit var btClient: BluetoothClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)

        btClient = AppControllerCl.Instance.bluetoothClient

        rvDevices = findViewById(R.id.rvDevices)
        rvDevices?.let {
            it.setHasFixedSize(true)
            it.layoutManager = LinearLayoutManager(AppControllerCl.Instance.mainActivity)
            rvDevices?.adapter = DeviceAdapter(btClient.getPairedDevices(), onConnectListener)
        }
    }

    private val onConnectListener: OnConnectListener = object : OnConnectListener {
        override fun connectToServer(device: BluetoothDevice) {
            btClient.connectToServer(device)
            finish()
        }
    }

    interface OnConnectListener {
        fun connectToServer(device: BluetoothDevice)
    }

    companion object {
        fun startForResult(context: Activity, requestCode: Int) =
            context.startActivityForResult(
                Intent(context, ScanActivity::class.java), requestCode
            )
    }
}