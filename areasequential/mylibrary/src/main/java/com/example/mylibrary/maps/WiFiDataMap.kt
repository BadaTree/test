package com.example.mylibrary.maps

import java.io.InputStream

// RSSI Map file 읽어 들여와서 MAP(wifi, unique wifi list, RSSI wifi) 생성

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


    constructor(wifiStreamTotal : InputStream, wifiStreamRSSI : InputStream, wifiStreamUnq : InputStream){
        var splitData : Array<String>
        var x : Int
        var y : Int
        var index : Int

        var SSID : String
        var RSSI : Int

//      #### wifihasmap 읽어들이기
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

//      # Map 폭과 높이 계산
            if (x > mapWidth) {
                mapWidth = x
            }
            if (y > mapHeight) {
                mapHeight = y
            }
        }}
//     ### Unique wigfi 읽어들이기
        wifiStreamUnq.bufferedReader().useLines { lines -> lines.forEach{
            splitData = it.split("\t").toTypedArray()
            wifilist = splitData
            wifilistsize = splitData.size

        }}

//     #### WIFI RSSI 읽어들이기
        wifiStreamRSSI.bufferedReader().useLines { lines -> lines.forEach{
            splitData = it.split("\t").toTypedArray()
            var datalen = splitData.size
            print(splitData[0])
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