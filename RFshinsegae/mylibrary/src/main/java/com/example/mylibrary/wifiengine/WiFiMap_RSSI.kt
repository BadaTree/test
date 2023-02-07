package com.example.mylibrary.wifiengine

import android.util.Log
import com.example.mylibrary.maps.WiFiDataMap

internal class WifiMap_RSSI constructor(wiFiDataMap: WiFiDataMap) {
    var pos = arrayListOf<Int>()
    var posx = arrayListOf<Int>()
    var posy = arrayListOf<Int>()

    lateinit var wifilist : Array<String>
    var wifilistsize : Int = 0

    private var wifi = mutableMapOf<Int, Array<String>>()
    private var wifi_rssi = mutableMapOf<Int, Array<String>>()

    private var mapWidth : Int = 0
    private var mapHeight : Int = 0


    //하나스퀘어
    private var rssi_thres = -75
    private var range_thres = 6
    private var rssi_range_num = 30

    init {
        wifi = wiFiDataMap.wifi
        wifi_rssi = wiFiDataMap.wifi_rssi
        wifilist = wiFiDataMap.wifilist
        wifilistsize = wiFiDataMap.wifilistsize

        mapWidth = wiFiDataMap.mapWidth
        mapHeight = wiFiDataMap.mapHeight

        pos = wiFiDataMap.pos
        posx = wiFiDataMap.posx
        posy = wiFiDataMap.posy

    }

    fun vectorcompare(wifi_string: String) : ArrayList<Int> {

        var test_vector = Array(wifilistsize, {0})
        var rssi_vector = Array(wifilistsize, {0})

        var splitline = wifi_string.split("\r\n").toTypedArray()
        for (i in splitline){
            var data = i.split("\t").toTypedArray()
            if(data.size == 2) {
                var SSID = data[0]
                var RSSI = data[1].toInt()

                if ((wifilist.indexOf(SSID) != -1) and (RSSI >= rssi_thres)) {
                    test_vector[wifilist.indexOf(SSID)] = 1
                    rssi_vector[wifilist.indexOf(SSID)] = RSSI
                }
            }
        }
        Log.d("timecheck1", "timecheck")

        var cnt_list = arrayListOf<Int>()
        var rssi_diff_list = arrayListOf<Double>()

        var range_idx = range_thres
        var rssi_range_idx = rssi_range_num

        var ans_range = arrayListOf(0, 0, 0, 0)

        for (i in pos){
            var both_cnt = 0
            var rssi_sum = 0.0

            for (j in 0 .. wifilistsize - 1 step(1)){
                if(test_vector[j] + wifi[i]!![j].toInt() == 2){
                    both_cnt += 1
                    rssi_sum += Math.abs(rssi_vector[j] - wifi_rssi[i]!![j].toInt())
                }
            }
            cnt_list.add(both_cnt)
            rssi_diff_list.add(rssi_sum / both_cnt)
        }
        Log.d("timecheck12", "timecheck")

        var unq_cnt_list = cnt_list.distinct().sortedDescending()
        var unq_rssi_diff_list = rssi_diff_list.distinct().sorted()

        if (unq_cnt_list.size <= range_thres){
            range_idx = unq_cnt_list.size - 1
        } else{
            range_idx = range_thres
        }
        var rangeval = unq_cnt_list[range_idx]

        if (unq_rssi_diff_list.size <= rssi_range_num){
            rssi_range_idx = unq_rssi_diff_list.size - 1
        } else{
            rssi_range_idx = rssi_range_num
        }
        var rssi_rangeval = unq_rssi_diff_list[rssi_range_idx]


        var x_list = arrayListOf<Int>()
        var y_list = arrayListOf<Int>()

        for (i in pos.indices){
            if((cnt_list[i] >= rangeval) and (rssi_diff_list[i] <= rssi_rangeval)){
                x_list.add(posx[i])
                y_list.add(posy[i])
            }
        }

        if (x_list.minOrNull() == null){
            ans_range = arrayListOf(0, mapWidth, 0, mapHeight)
        }else{
            ans_range = arrayListOf(x_list.minOrNull()!!.toInt(), x_list.maxOrNull()!!.toInt(), y_list.minOrNull()!!.toInt(), y_list.maxOrNull()!!.toInt())
        }
        Log.d("rangeval", ans_range.joinToString("\t"))

        return ans_range
    }

}
