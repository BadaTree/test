package com.example.mylibrary.wifiengine

import android.util.Log
import com.example.mylibrary.maps.WiFiDataMap
import java.io.InputStream
import java.util.Collections.max

class WifiMap_Floorcheck constructor(f1alist: InputStream, f1blist: InputStream, fb1list: InputStream) {
    lateinit var f1awifilist : Array<String>
    lateinit var f1bwifilist : Array<String>
    lateinit var fb1wifilist : Array<String>



    init {
        var splitData : Array<String>
        f1alist.bufferedReader().useLines { lines -> lines.forEach{
            splitData = it.split("\t").toTypedArray()
            f1awifilist = splitData
        }}

        f1blist.bufferedReader().useLines { lines -> lines.forEach{
            splitData = it.split("\t").toTypedArray()
            f1bwifilist = splitData
        }}

        fb1list.bufferedReader().useLines { lines -> lines.forEach{
            splitData = it.split("\t").toTypedArray()
            fb1wifilist = splitData
        }}

    }

    fun detect_floor(wifi_string: String) : String {
        var splitline = wifi_string.split("\r\n").toTypedArray()

        var cnt_list = arrayListOf(0, 0, 0)

        var cnt = 0
        for (i in splitline){
            var data = i.split("\t").toTypedArray()
            var SSID = data[0]
            for (j in f1awifilist){
                if (SSID == j){
                   cnt += 1
                }
            }
        }
        cnt_list[0] = cnt

        cnt = 0
        for (i in splitline){
            var data = i.split("\t").toTypedArray()
            var SSID = data[0]
            for (j in f1bwifilist){
                if (SSID == j){
                    cnt += 1
                }
            }
        }
        cnt_list[1] = cnt

        cnt = 0
        for (i in splitline){
            var data = i.split("\t").toTypedArray()
            var SSID = data[0]
            for (j in fb1wifilist){
                if (SSID == j){
                    cnt += 1
                }
            }
        }
        cnt_list[2] = cnt

        var max_index = cnt_list.indexOf(max(cnt_list))

        var result = ""

        if (max_index == 0){
            result = "1a"
        }else if(max_index == 1){
            result = "1b"
        }else{
            result = "B1"
        }
        return result
    }

}