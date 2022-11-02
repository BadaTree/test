package com.example.cslabposco

import android.util.Log
import java.io.InputStream
import java.lang.Math.abs

class WifiMap_RSSI constructor(inputStreamTotal: InputStream, inputStreamRSSI: InputStream, inputStreamunq: InputStream, inputStreamtest: InputStream) {
    internal var pos = arrayListOf<Int>()
    internal var posx = arrayListOf<Int>()
    internal var posy = arrayListOf<Int>()

    internal lateinit var wifilist : Array<String>
    var wifilistsize : Int = 0

    private var wifi = mutableMapOf<Int, String>()
    private var wifi_rssi = mutableMapOf<Int, String>()

    private var mapWidth : Int = 0
    private var mapHeight : Int = 0

    private lateinit var test_vector : Array<Int>
    private lateinit var rssi_test_vector : Array<Int>

    init {
        var splitData : Array<String>
        var x : Int
        var y : Int
        var index : Int

        var SSID : String
        var RSSI : Int

        inputStreamTotal.bufferedReader().useLines { lines -> lines.forEach{
            splitData = it.split("\t").toTypedArray()
            var datalen = splitData.size
            x = splitData[0].toInt()
            y = splitData[1].toInt()
            index = x * 10000 + y

            wifi.apply{ this[index] = splitData[2]}
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

        inputStreamRSSI.bufferedReader().useLines { lines -> lines.forEach{
            splitData = it.split("\t").toTypedArray()
            var datalen = splitData.size
            x = splitData[0].toInt()
            y = splitData[1].toInt()
            index = x * 10000 + y

            wifi_rssi.apply{ this[index] = splitData[2]}

            if (x > mapWidth) {
                mapWidth = x
            }
            if (y > mapHeight) {
                mapHeight = y
            }
        }}

        inputStreamunq.bufferedReader().useLines { lines -> lines.forEach{
            splitData = it.split("\t").toTypedArray()
            wifilist = splitData
            wifilistsize = splitData.size
        }}


        test_vector = Array(wifilistsize, {0})


        inputStreamtest.bufferedReader().useLines{ lines -> lines.forEach{
            splitData = it.split("\t").toTypedArray()

            SSID = splitData[4]
            RSSI = splitData[5].toInt()

            if ((wifilist.indexOf(SSID) != -1) and (RSSI >= -75)){
                test_vector[wifilist.indexOf(SSID)] = 1
                rssi_test_vector[wifilist.indexOf(SSID)] = RSSI
            }
        }}
    }

    fun vectorcompare(range_thres: Int, rssi_thres: Int, wifi_string: String) : ArrayList<Int> {

        var test_vector = Array(wifilistsize, {0})
        var rssi_vector = Array(wifilistsize, {0})

        var splitline = wifi_string.split("\r\n").toTypedArray()

        for (i in splitline){
            var data = i.split("\t").toTypedArray()
            if(data.size == 2) {
                var SSID = data[0]
                var RSSI = data[1].toInt()

                if ((wifilist.indexOf(SSID) != -1) and (RSSI >= -63)) {
                    test_vector[wifilist.indexOf(SSID)] = 1
                    rssi_vector[wifilist.indexOf(SSID)] = RSSI
                }
            }
        }

        var cnt_list = arrayListOf<Int>()
        var rssi_diff_list = arrayListOf<Double>()

        var range_idx = range_thres
        var rssi_range_idx = rssi_thres

        var ans_range = arrayListOf(0, 0, 0, 0)

        for (i in pos){
            var both_cnt = 0
            var rssi_sum = 0.0

            var vector = wifi[i]
            var ref_rssi_vector = wifi_rssi[i]!!.split(" ").toTypedArray()
            for (j in 0 .. wifilistsize - 1 step(1)){
                if ((vector!![j].toString() == "1") and (test_vector[j].toString() == "1")){
                    both_cnt +=1
                    rssi_sum += abs(rssi_vector[j] - ref_rssi_vector[j].toInt())
                }
            }
            cnt_list.add(both_cnt)
            rssi_diff_list.add(rssi_sum / both_cnt)
        }
        var unq_cnt_list = cnt_list.distinct().sortedDescending()
        var unq_rssi_diff_list = rssi_diff_list.distinct().sorted()

        if (unq_cnt_list.size < range_thres){
            range_idx = unq_cnt_list.size - 1
        }
        var rangeval = unq_cnt_list[range_idx]

        if (unq_rssi_diff_list.size < rssi_thres){
            rssi_range_idx = unq_rssi_diff_list.size - 1
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
        Log.d("rangeval", x_list.minOrNull().toString() + "\t" + x_list.maxOrNull().toString() + "\t"
                + y_list.minOrNull().toString() + "\t" + y_list.maxOrNull().toString() + "\t")

        ans_range = arrayListOf(x_list.minOrNull()!!.toInt(), x_list.maxOrNull()!!.toInt(), y_list.minOrNull()!!.toInt(), y_list.maxOrNull()!!.toInt())

        return ans_range
    }

    fun testcompare(range_thres: Int, rssi_thres: Int) : ArrayList<Int> {
        var cnt_list = arrayListOf<Int>()
        var rssi_diff_list = arrayListOf<Double>()

        var range_idx = range_thres
        var rssi_range_idx = rssi_thres

        var ans_range = arrayListOf(0, 0, 0, 0)

        for (i in pos){
            var both_cnt = 0
            var rssi_sum = 0.0

            var vector = wifi[i]
            var ref_rssi_vector = wifi_rssi[i]!!.split(" ").toTypedArray()
            for (j in 0 .. wifilistsize - 1 step(1)){
                if ((vector!![j].toString() == "1") and (test_vector[j].toString() == "1")){
                    both_cnt +=1
                    rssi_sum += abs(rssi_test_vector[j] - ref_rssi_vector[j].toInt())
                }
            }
            cnt_list.add(both_cnt)
            rssi_diff_list.add(rssi_sum / both_cnt)
        }
        var unq_cnt_list = cnt_list.distinct().sortedDescending()
        var unq_rssi_diff_list = rssi_diff_list.distinct().sorted()

        if (unq_cnt_list.size < range_thres){
            range_idx = unq_cnt_list.size - 1
        }
        var rangeval = unq_cnt_list[range_idx]

        if (unq_rssi_diff_list.size < rssi_thres){
            rssi_range_idx = unq_rssi_diff_list.size - 1
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
        Log.d("rangeval", x_list.minOrNull().toString() + "\t" + x_list.maxOrNull().toString() + "\t"
                + y_list.minOrNull().toString() + "\t" + y_list.maxOrNull().toString() + "\t")

        ans_range = arrayListOf(x_list.minOrNull()!!.toInt(), x_list.maxOrNull()!!.toInt(), y_list.minOrNull()!!.toInt(), y_list.maxOrNull()!!.toInt())

        return ans_range
    }

}