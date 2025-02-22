package com.example.mylibrary.maps

import java.io.InputStream

class WiFiDataMap {
    internal var pos = arrayListOf<Int>()
    internal var posx = arrayListOf<Int>()
    internal var posy = arrayListOf<Int>()

    internal lateinit var wifilist: Array<String>
    var wifilistsize: Int = 0

    internal var wifi = mutableMapOf<Int, Array<String>>()
    internal var wifi_rssi = mutableMapOf<Int, Array<String>>()

    internal var mapWidth: Int = 0
    internal var mapHeight: Int = 0

    internal var temp_wifi_rssi = mutableMapOf<Int, Int>()
    internal var temp_wifi_cnt = mutableMapOf<Int, Int>()

    constructor(wifi_total_data: Array<Array<String>>, wifi_rssi_data: Array<Array<String>>, wifi_uniq_data : Array<Array<String>>) {
        var x: Int
        var y: Int
        var index: Int

        for (n in wifi_total_data) {
            x = n[0].toInt()
            y = n[1].toInt()
            index = x * 10000 + y

            wifi.apply { this[index] = n.slice(2..n.size - 1).toTypedArray() }
            pos.add(index)
            posx.add(x)
            posy.add(y)

            if (x > mapWidth) {
                mapWidth = x
            }
            if (y > mapHeight) {
                mapHeight = y
            }
        }

        for (n in wifi_rssi_data) {
            x = n[0].toInt()
            y = n[1].toInt()
            index = x * 10000 + y

            wifi_rssi.apply { this[index] = n.slice(2..n.size - 1).toTypedArray() }

            temp_wifi_rssi.apply{this[index] = 0}
            temp_wifi_cnt.apply{this[index] = 0}

            if (x > mapWidth) {
                mapWidth = x
            }
            if (y > mapHeight) {
                mapHeight = y
            }
        }

        for (n in wifi_uniq_data) {
            wifilist = n
            wifilistsize = n.size
        }

    }

    constructor(wifiStreamTotal : InputStream, wifiStreamRSSI : InputStream, wifiStreamUnq : InputStream){
        var splitData : Array<String>
        var x : Int
        var y : Int
        var index : Int

        var SSID : String
        var RSSI : Int
        wifiStreamTotal.bufferedReader().useLines { lines -> lines.forEach{
            splitData = it.split("\t").toTypedArray()
            var datalen = splitData.size
            x = splitData[0].toInt()
            y = splitData[1].toInt()
            index = x * 10000 + y

            wifi.apply{ this[index] = splitData.slice(2.. splitData.size - 1).toTypedArray()}
            pos.add(index)
            posx.add(x)
            posy.add(y)

            if (x > mapWidth) {
                mapWidth = x
            }
            if (y > mapHeight) {
                mapHeight = y
            }
        }}
        wifiStreamUnq.bufferedReader().useLines { lines -> lines.forEach{
            splitData = it.split("\t").toTypedArray()
            wifilist = splitData
            wifilistsize = splitData.size

        }}

        wifiStreamRSSI.bufferedReader().useLines { lines -> lines.forEach{
            splitData = it.split("\t").toTypedArray()
            var datalen = splitData.size
            x = splitData[0].toInt()
            y = splitData[1].toInt()
            index = x * 10000 + y
            var temp = splitData.slice(2 .. splitData.size - 1)
            wifi_rssi.apply{ this[index] = splitData.slice(2.. splitData.size - 1).toTypedArray()}
            temp_wifi_rssi.apply{this[index] = 0}
            temp_wifi_cnt.apply{this[index] = 0}

            if (x > mapWidth) {
                mapWidth = x
            }
            if (y > mapHeight) {
                mapHeight = y
            }
        }}

    }
}