package com.example.mylibrary.maps

import android.util.Log
import java.io.InputStream

internal class ResourceDataManager {
    internal var isMagneticMapUsable : Boolean = false
    internal var isWiFiMapUsable : Boolean = false
    internal var noMapMode : Boolean = false
    internal lateinit var magneticFieldMap : MagneticFieldMap
    internal lateinit var instantMap : MagneticFieldMap
    internal lateinit var wifiDataMap : WiFiDataMap

    constructor() {
        noMapMode = true
    }

//    constructor(url : String, useMagnetic : Boolean, useWiFi : Boolean) {
//        if (useMagnetic) {
//            val retrofit = Retrofit.Builder()
//                .baseUrl(url)
//                .addConverterFactory(GsonConverterFactory.create()).build()
//            val mapSending: MapRequest = retrofit.create(MapRequest::class.java)
//            mapSending.message().enqueue(object : Callback<MapResponse> {
//                override fun onFailure(call: Call<MapResponse>, t: Throwable) {
//                    Log.d("serverResponse:::", t.stackTraceToString())
//                }
//
//                override fun onResponse(call: Call<MapResponse>, response: Response<MapResponse>) {
//                    val msg1 = response.body()!!.map_array
//                    val msg2 = response.body()!!.map_array_instant
////                    Log.d("mapDataMagnetic", "${msg1.size}     ${msg2.size}")
//                    magneticFieldMap = MagneticFieldMap(msg1.clone())
//                    instantMap = MagneticFieldMap(msg2.clone())
//                    isMagneticMapUsable = true
//                }
//            })
//        }
//        if (useWiFi) {
//            val retrofit = Retrofit.Builder()
//                .baseUrl(url)
//                .addConverterFactory(GsonConverterFactory.create()).build()
//            val wifiRequest: WiFiDataRequest = retrofit.create(WiFiDataRequest::class.java)
//            wifiRequest.message().enqueue(object : Callback<WiFiData> {
//                override fun onFailure(call: Call<WiFiData>, t: Throwable) {
//                    Log.d("serverResponse:::", t.stackTraceToString())
//                }
//
//                override fun onResponse(call: Call<WiFiData>, response: Response<WiFiData>) {
//                    val msg1 = response.body()!!.wifi
//                    val msg2 = response.body()!!.wifiRssi
//                    val msg3 = response.body()!!.wifiUnq
////                    Log.d("mapDataWiFi1", "${msg1}")
////                    Log.d("mapDataWiFi2", "${msg2.size}")
////                    Log.d("mapDataWiFi3", "${msg3.size}")
//                    wifiDataMap = WiFiDataMap(msg1, msg2, msg3)
//                    isWiFiMapUsable = true
////                    var str = ""
////                    for(n in msg2) {
////                        str = ""
////                        for (i in n) {
////                            str += "$i    "
////                        }
////                        Log.d("mapDataCheck", "n:${n.size}      $str")
////                    }
//                }
//            })
//        }
//    }

    constructor(magneticStream : InputStream?, magneticStreamForInstant : InputStream?, wifiStreamTotal : InputStream?, wifiStreamRSSI : InputStream?, wifiStreamUnq : InputStream?) {
        if (magneticStream != null && magneticStreamForInstant != null) {
            magneticFieldMap = MagneticFieldMap(magneticStream)
            instantMap = MagneticFieldMap(magneticStreamForInstant)
            isMagneticMapUsable = true
        }
        if (wifiStreamTotal != null && wifiStreamRSSI != null && wifiStreamUnq != null) {
            wifiDataMap = WiFiDataMap(wifiStreamTotal, wifiStreamRSSI, wifiStreamUnq)
            isWiFiMapUsable = true
        }
        noMapMode = (!isMagneticMapUsable && !isWiFiMapUsable)
    }

    internal fun mapChange(floor : Int) {
        when(floor) {
            1 -> {

            }
            -1 -> {

            }
        }
    }
}