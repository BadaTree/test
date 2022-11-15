package com.example.mylibrary.maps

import java.io.InputStream
import kotlin.math.round

class MagneticFieldMap {

    private val floorIndex = arrayListOf<ArrayList<String>>()
    internal var pos = arrayListOf<Int>()
    internal var mag = mutableMapOf<Int, Array<Double>>()
    private var mapWidth : Int = 0
    private var mapHeight : Int = 0

    constructor(data : Array<Array<Double>>) {
        var x: Int
        var y: Int
        var index: Int
        for (n in data){
            x = n[0].toInt()
            y = n[1].toInt()
            index = x * 10000 + y
            mag.apply {
                this[index] = arrayOf(n[2], n[3], n[4])
            }
            pos.add(index)
            if (x > mapWidth) {
                mapWidth = x
            }
            if (y > mapHeight) {
                mapHeight = y
            }
        }
    }

    constructor(inputStream: InputStream) {
        var splitData : Array<String>
        var x : Int
        var y : Int
        var index : Int
        inputStream.bufferedReader().useLines { lines -> lines.forEach{
            splitData = it.split("\t").toTypedArray()
            x = splitData[0].toInt()
            y = splitData[1].toInt()
            index = x * 10000 + y
            mag.apply{ this[index] = arrayOf(splitData[2].toDouble(), splitData[3].toDouble(), splitData[4].toDouble()) }
            pos.add(index)
            if (x > mapWidth) {
                mapWidth = x
            }
            if (y > mapHeight) {
                mapHeight = y
            }
        }}
    }

    internal fun getWidth() : Int {
        return mapWidth
    }

    internal fun getHeight() : Int {
        return mapHeight
    }

    /**
     * 요청된 좌표에 맞는 자기장 데이터를 찾아 반환하는 메소드
     *
     * @param [what] 요청하려는 데이터가 자기장 맵 x, y, z 혹은 floor 맵 중에서 어느것인지를 의미하는 단일 텍스트
     * @param [dx] 요청하고자 하는 데이터의 x좌표
     * @param [dy] 요청하고자 하는 데이터의 y좌표
     * @return 선택한 맵에서 요청한 좌표의 데이터를 Double 형태로 반환
     */

    fun getData(dx : Double, dy : Double) : Array<Double> {
        return mag[10000 * round(dx).toInt() + round(dy).toInt()]?:arrayOf(0.0, 0.0, 0.0)//mag.getOrDefault(10000 * round(dx).toInt() + round(dy).toInt(), arrayOf(0.0, 0.0, 0.0))
    }

    internal fun isPossiblePosition(dx: Double, dy: Double) : Boolean {
        val x = round(dx).toInt()
        val y = round(dy).toInt()
        return when {
            (mag.containsKey(10000*x+y).not()) -> false
            (x<0 || y<0) -> false
            (x>=getWidth() || y>=getHeight()) -> false
            else -> true
        }
    }

    internal fun isPossiblePosition2(dx: Double, dy: Double) : Boolean {
        return when {
            (dx<0 || dy<0) -> false
            (dx>getWidth() || dy>getHeight()) -> false
            else -> true
        }
    }

}