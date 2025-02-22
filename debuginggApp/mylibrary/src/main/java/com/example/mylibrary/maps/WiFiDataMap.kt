package com.example.mylibrary.maps

import android.util.Log
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
                Log.d("wifihash width", mapWidth.toString())
            }
            if (y > mapHeight) {
                mapHeight = y
                Log.d("wifihash Height", mapHeight.toString())
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
                Log.d("wifihash width", mapWidth.toString())
            }
            if (y > mapHeight) {
                mapHeight = y
                Log.d("wifihash Height", mapHeight.toString())
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
//                Log.d("wifihash width", mapWidth.toString())
            }
            if (y > mapHeight) {
                mapHeight = y
//                Log.d("wifihash Height", mapHeight.toString())
            }
        }}
//     ### Unique wigfi 읽어들이기
        wifiStreamUnq.bufferedReader().useLines { lines -> lines.forEach{
            splitData = it.split("\t").toTypedArray()
            Log.d("rssi", splitData[0])
            wifilist = splitData
            wifilistsize = splitData.size

        }}

//     #### WIFI RSSI 읽어들이기
//     #### 11.23 바다 수정, Anam RSSI File 첫 데이터에 "0" 값이 들어와서 읽기 오류 발생 -> Try catch문으로 예외처리
        wifiStreamRSSI.bufferedReader().useLines { lines -> lines.forEach{
            splitData = it.split("\t").toTypedArray()

            var datalen = splitData.size
            try{
                x = splitData[0].toInt()
                y = splitData[1].toInt()
                index = x * 10000 + y
                var temp = splitData.slice(2 .. splitData.size - 1)
                wifi_rssi.apply{ this[index] = splitData.slice(2.. splitData.size - 1).toTypedArray()}
                temp_wifi_rssi.apply{this[index] = 0}
                temp_wifi_cnt.apply{this[index] = 0}

//                Log.d("RSSImap width", mapWidth.toString())
//                Log.d("RSSImap Height", mapHeight.toString())
                if (x > mapWidth) {
                    mapWidth = x
                }
                if (y > mapHeight) {
                    mapHeight = y
                }
            }catch (e : Exception){
                Log.d("RSSI 읽어들이기 실패",splitData[0])
            }


        }}

    }

}