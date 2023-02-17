package com.example.advancedmapcollector.net

import java.util.*

object BTConstant {
    const val BT_REQUEST_ENABLE = 1
    const val BT_MESSAGE_READ = 2
    const val BT_CONNECTING_STATUS = 3

    val BLUETOOTH_UUID_SPP = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
    val BLUETOOTH_UUID_SECURE= UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66")
    val BLUETOOTH_UUID_INSECURE = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
}