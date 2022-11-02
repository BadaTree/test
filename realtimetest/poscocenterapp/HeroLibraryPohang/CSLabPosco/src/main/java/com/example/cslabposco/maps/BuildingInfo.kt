package com.example.cslabposco.maps

import android.util.Log
import java.io.InputStream

internal class BuildingInfo constructor(buildingInfoFileStream : InputStream) {
    private var infoData = arrayListOf<BuilInfo>()

    init {
        var splitData : Array<String>
        buildingInfoFileStream.bufferedReader().useLines { lines -> lines.forEach {
            splitData = it.split("\t").toTypedArray()
            infoData.add(
                BuilInfo(splitData[0],
                    when(splitData[0]) {
                        "EL1", "EL2", "EL3", "EL4" -> arrayOf(splitData[1].toDouble(), splitData[2].toDouble(), splitData[3].toDouble(), splitData[4].toDouble())
                        "Es2" -> arrayOf(splitData[1].toDouble(), splitData[2].toDouble(), splitData[3].toDouble(), splitData[4].toDouble())
                        "sm" -> arrayOf(splitData[1].toDouble(), splitData[2].toDouble(), splitData[3].toDouble(), splitData[4].toDouble())
                        else -> arrayOf() },
                    when(splitData[0]) {
                        "EL1", "EL2", "EL3", "EL4" -> arrayOf(splitData[5].toDouble(), splitData[6].toDouble())
                        "Es2" -> arrayOf(splitData[5].toDouble(), splitData[6].toDouble(), splitData[7].toDouble(), splitData[8].toDouble())
                        else -> null
                    }
                )
            )
        } }
    }

    private fun check() {
        for (i in infoData) {
            when(i.type) {
                "EL1", "EL2", "EL3", "EL4" -> Log.d("Building Info Data", "${i.type} : (${i.area[0]}, ${i.area[1]}) ~ (${i.area[2]}, ${i.area[3]})\narrival : (${i.area[4]}, ${i.area[5]})")
                "Es2" -> Log.d("Building Info Data", "${i.type} : (${i.area[0]}, ${i.area[1]}) ~ (${i.area[2]}, ${i.area[3]})\nUp floor arrival : (${i.area[4]}, ${i.area[5]})\nDown floor arrival : (${i.area[6]}, ${i.area[7]})")
                "sm" -> Log.d("Building Info Data", "${i.type} : (${i.area[0]}, ${i.area[1]}) ~ (${i.area[2]}, ${i.area[3]})")
                else -> Log.d("Building Info Data", "Unknown Data")
            }
        }
    }

    internal fun search(x : Double, y : Double) : BuilInfo? {
        for (i in infoData) {

            if (x in i.area[0]..i.area[2] && y in i.area[1]..i.area[3]) {
                return i
            }
        }

        return null
    }
}

data class BuilInfo(
    val type : String,              //종류 : El, Es1, Es2, sm
    val area : Array<Double>,       //해당 구조물이 위치한 영역 좌표
    val arrival : Array<Double>?    //엘베, 에스컬레이터의 경우에만 도착 좌표 포함
)