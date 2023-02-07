package com.example.mylibrary.wifiengine

import android.util.Log
import com.example.mylibrary.maps.WiFiDataMap
import com.example.mylibrary.maps.MagneticFieldMap
import java.lang.Exception
import kotlin.math.*


class WifimapRssiSequential constructor(wiFiDataMap: WiFiDataMap, map_hand: MagneticFieldMap) {
    private var instantResult : MutableMap<String, Float> = mutableMapOf("status_code" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)
    private var unqWifi: Int = 0

    // pos -> ansRange -> areaCheckPosList -> universalRange
    private var pos = arrayListOf<Int>()
    private var posx = arrayListOf<Int>()
    private var posy = arrayListOf<Int>()

    private var wifilist : Array<String>
    private var wifilistsize : Int = 0

    private var wifi = mutableMapOf<Int, Array<String>>()
    private var wifiRssi = mutableMapOf<Int, Array<String>>()

    private var mapWidth : Int = 0
    private var mapHeight : Int = 0

    private var mapVector = map_hand
    var decreassion: Float = 0.0f
    private var angleStepNum : Int = 10
    private var angleList : IntArray = (0..359 step angleStepNum).toList().toIntArray()

    private var isConvergence = false
    private var foundpos = true
    private var founddir = true

    private var curStep = -1

    // anam staion
    var rssiThres = -85
    var rangeThres = 7
    var rssiRangeNum = 40
    var boxLength = 50
    var stepLength = 10

    private var rssiRangeval = 0.0
    private var rangeval = 0

    private var tempGyro = 0.0f

    private var wifiParticleMotherList = arrayListOf<WifiParticleMother>()

    var areaCheckPosList = arrayListOf<Array<Float>>()

    private var origWifiData = ""

    private var correctionAngle = 0.0f

    var areaCheckRange = arrayListOf(0.0f, 0.0f, 0.0f, 0.0f)
    private var particleNum = 0
    //    private var testVector : Array<Int>
//    private var rssiVector : Array<Int>
    var wifiDataChange = false
    var universalRange = arrayListOf(0.0f, 0.0f, 0.0f, 0.0f)
    var accumulatedIntersection = arrayListOf(0.0f, 0.0f, 0.0f, 0.0f)
    var ansRange = arrayListOf(0.0f, 0.0f, 0.0f, 0.0f)
    private var testVector: Array<Int> = Array(wifilistsize) { 0 }
    private var rssiVector: Array<Int> = Array(wifilistsize) { 0 }

    init {
        wifi = wiFiDataMap.wifi
        wifiRssi = wiFiDataMap.wifi_rssi
        wifilist = wiFiDataMap.wifilist
        wifilistsize = wiFiDataMap.wifilistsize

        mapWidth = wiFiDataMap.mapWidth
        mapHeight = wiFiDataMap.mapHeight

        pos = wiFiDataMap.pos
        posx = wiFiDataMap.posx
        posy = wiFiDataMap.posy

        testVector = Array(wifilistsize) { 0 }
        rssiVector = Array(wifilistsize) { 0 }

        instantResult = mutableMapOf("status_code" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)
    }

    // 민혁 수정 불필요한 조건문 제거
    /**
     * 파라미터 초기화 함수/
     * 실행: angleList를 0~360로 배열 초기화, 스텝 -1 세팅, Mother파티클 초기화, 상태코드 초기화/
     * 완전 수렴, 202.0f수렴, 미수렴 모두 초기화 진행
     */
    private fun initParameters(status_code:Float) {
        if ((status_code == 200.0f) || (status_code == 202.0f) || (status_code == 400.0f)) {
            angleList = (0..359 step angleStepNum).toList().toIntArray()
            curStep = -1
            wifiParticleMotherList = arrayListOf()
            instantResult = mutableMapOf(
                "status_code" to -1.0f,
                "pos_x" to -1.0f,
                "pos_y" to -1.0f
            )
        }
    }

    /** 와이파이가 바뀌었는지 체크함.
     *  입력: wifi ssid
     */
    private fun checkWifiChange(wifiString: String) {
        if (origWifiData != wifiString){
            origWifiData = wifiString
            wifiDataChange = true
        }
    }


    /** 초기 위치 수렴 이후에 영역체크 및 이동 시켜주는 함수
     *  출력: [상태코드, 방향각, x좌표, y좌표]
     */
    fun getlocationWF(wifiString: String) : MutableMap<String, Float> {
        initParameters(instantResult["status_code"]!!)

        checkWifiChange(wifiString)

        // ssid, rssi 유사 상위 값으로만 ansRange 파티클 구성
        var ansRange = vectorcompare(wifiString)

//        var universalRange = arrayListOf(0.0f, 0.0f, 0.0f, 0.0f)


        // 한 스텝 이동 후 교집합 영역을 universalRange에 넣음
        var accumulatedIntersection = moveRange(ansRange, accumulatedIntersection, stepLength)

        var particleArray = getParticle(accumulatedIntersection)

        var answerPositionXY = calculateAnswerPosition(particleArray)

        instantResult = estimateConvergence(accumulatedIntersection, particleArray, answerPositionXY)

        if (instantResult["status_code"] == 200.0f){
            var (areaList, areaRange) = makeBox(particleArray, accumulatedIntersection)
            areaCheckPosList = areaList
            areaCheckRange = areaRange
        }

        return instantResult
    }

    /**
     * 파티클 개수 출력 함수
     */
    fun getParticleNum():Int{
        return particleNum
    }

    /** ssid랑 rssi 비교 후 상위 n개 이상인 좌표들만 추출
     * 입력:[와이파이 데이터]
     */
    private fun vectorcompare(wifiString: String): ArrayList<Float>{
        var ansRange = arrayListOf(0.0f, 0.0f, 0.0f, 0.0f)
        // unique ssid 일치 개수
        val ssidCntList = arrayListOf<Int>()
        // rssi 차이값
        val rssiDiffList = arrayListOf<Double>()
        val splitline = wifiString.split("\r\n").toTypedArray()

        for (i in splitline){
            val data = i.split("\t").toTypedArray()
            if(data.size == 2) {
                val ssid = data[0]
                val rssi = data[1].toInt()
                if ((wifilist.indexOf(ssid) != -1) and (rssi >= rssiThres)) {
                    testVector[wifilist.indexOf(ssid)] = 1
                    rssiVector[wifilist.indexOf(ssid)] = rssi
                    Log.d("common wifi",ssid)
                }
            }
        }

        // 좌표 전체 반복하며 좌표마다 와이파이 리스트 쭉 확인함 -> SSID, RSSI 일치 개수 구함
        for (i in pos){
            var ssidCnt = 0
            var rssiCnt = 0
            var rssiSum = 0.0
            for (j in 0 until wifilistsize step(1)){
                try {
                    if (testVector[j] + wifi[i]!![j].toInt() == 2) {
                        ssidCnt += 1
                        if (rssiVector[j] != 0){
                            rssiCnt += 1
                            rssiSum += abs(rssiVector[j] - wifiRssi[i]!![j].toInt())
                        }
                    }
                }catch (e: Exception){
                    Log.d("wifi rssi error", "error at :$wifi")
                }
            }
            ssidCntList.add(ssidCnt)
            rssiDiffList.add(rssiSum / rssiCnt)
        }
        val unqCntList = ssidCntList.distinct().sortedDescending()

        // rangeThres값보다 너무 안 나오면 현재 사이즈의 마지막 녀석을 인덱싱함
        val rangeIdx = if (unqCntList.size <= rangeThres){
            unqCntList.size - 1
        } else{
            rangeThres
        }

        rangeval = unqCntList[rangeIdx]

        val unqRssiDiffList = rssiDiffList.distinct().sorted()

        val rssiRangeIdx = if (unqRssiDiffList.size <= rssiRangeNum){
            unqRssiDiffList.size - 1
        } else{
            rssiRangeNum
        }
        rssiRangeval = unqRssiDiffList[rssiRangeIdx]

        val xList = arrayListOf<Int>()
        val yList = arrayListOf<Int>()

        // 전 좌표 중에 rssi 값 차이가 작고 And ㅇㅇ
        for (i in pos.indices){
            if((ssidCntList[i] >= rangeval) and (rssiDiffList[i] <= rssiRangeval)){
                xList.add(posx[i])
                yList.add(posy[i])
            }
        }

        // 현재 스텝에서 유효한 파티클 좌표들
        ansRange = if (xList.minOrNull() == null){
            arrayListOf(0.0f, mapWidth.toFloat(), 0.0f, mapHeight.toFloat())
        }else{
            arrayListOf(xList.minOrNull()!!.toFloat(), xList.maxOrNull()!!.toFloat(),
                yList.minOrNull()!!.toFloat(), yList.maxOrNull()!!.toFloat())
        }

        return ansRange
    }

    /** 전역변수 pos(전 좌표) 모든 x,y좌표가 범위에 있는지 체크해주는 함수. 전 좌표 내의 범위에 있으면 해당 좌표를 areaCheckPosList에 추가
     * 입력:[죄표 범위를 나타내는 리스트]
     */
    private fun getParticle(range : ArrayList<Float>): ArrayList<Array<Float>> {
        val minX = range[0]
        val maxX = range[1]
        val minY = range[2]
        val maxY = range[3]

        var particleArray = arrayListOf<Array<Float>>()
        for (i in pos.indices){
            if((minX <= posx[i]) && (posx[i] <= maxX) && (minY <= posy[i]) && (posy[i] <= maxY)){
                if (mapVector.isPossiblePosition(posx[i].toDouble(), posy[i].toDouble())) {
                    particleArray.add(arrayOf(posx[i].toFloat(), posy[i].toFloat()))
                }
            }
//            particleArray.add(arrayOf(posx[i].toFloat(),posy[i].toFloat()))
        }
        return particleArray
    }


    /**
     *
     */
    private fun makeBox(particleArray : ArrayList<Array<Float>>, accumulatedIntersection: ArrayList<Float>): Pair<ArrayList<Array<Float>>, ArrayList<Float>> {
        var areaList = arrayListOf<Array<Float>>()
        var areaRange = arrayListOf(0.0f,0.0f,0.0f,0.0f)
        var minx = 1000000f
        var miny = 1000000f
        var maxx = 0f
        var maxy = 0f

        for (pos in particleArray) {
            var posx = pos[0]
            var posy = pos[1]
            if (mapVector.isPossiblePosition(posx.toDouble(),posy.toDouble()) && checkInRange(posx.toDouble(), posy.toDouble(), accumulatedIntersection)) {
                areaList.add(arrayOf(posx, posy))
                minx = min(posx, minx)
                maxx = max(posx, maxx)
                miny = min(posy, miny)
                maxy = max(posy, maxy)
            }
        }

        areaRange[0] = minx
        areaRange[1] = maxx
        areaRange[2] = miny
        areaRange[3] = maxy
        return Pair(areaList, areaRange)
    }


    /** ansRange랑 이전 영역 universalRange 겹치는 영역을 출력함. 만약 교집합 영역이 작으면 ansRange만 출력함
     * 입력:[]
     */
    private fun moveRange(ansRange: ArrayList<Float>, universalRange:ArrayList<Float>, step_length : Int): ArrayList<Float>{
        var minX = universalRange[0]
        var maxX = universalRange[1]
        var minY = universalRange[2]
        var maxY = universalRange[3]

        minX -= (step_length * 10).toFloat()
        maxX += (step_length * 10).toFloat()

        minY -= (step_length * 10).toFloat()
        maxY += (step_length * 10).toFloat()
//
        if(wifiDataChange) {
            minX = max(ansRange[0], minX)
            maxX = min(ansRange[1], maxX)

            minY = max(ansRange[2], minY)
            maxY = min(ansRange[3], maxY)
        }
        return if((minX >= maxX - 18) || (minY >= maxY - 18)){
            arrayListOf(ansRange[0], ansRange[1], ansRange[2], ansRange[3])
        }else{
            arrayListOf(minX, maxX, minY, maxY)
        }
//        return arrayListOf(ansRange[0], ansRange[1], ansRange[2], ansRange[3])
    }


    /** 방향 수렴 후 초기 위치 계산(수렴 판단)
     *
     */
    private fun calculateAnswerPosition(particleArray: ArrayList<Array<Float>>) : ArrayList<Double>{
        var answerX = 0.0
        var answerY = 0.0
        // 살아남은 파티클들의 x좌표 합(answerX),y 좌표 합(answerY) 계산
        for (particle in particleArray){
            answerX += particle[0]
            answerY += particle[1]
        }

        // 살아남은 파티클들의 무게중심 계산
        val numOfParticle = particleArray.size
        answerX /= numOfParticle
        answerY /= numOfParticle

        // 무게중심과 파티클 사이의 거리 평균 계산
        var distAvg = 0.0
        for (particle in particleArray) {
            distAvg += sqrt((answerX - particle[0]).pow(2.0) + (answerY - particle[1]).pow(2.0)) * 0.1
        }
        distAvg /= particleArray.size
        if (distAvg > 3.0) {
            return arrayListOf(-1.0, -1.0)
        }
        return arrayListOf(answerX, answerY)
    }

    /** 초기 위치 및 방향 추정. 입력: [파티클 mother리스트,자이로값]
     * 모파티클 리스트에 1개만 남았을 시 방향 수렴
     */
    private fun estimateConvergence(accumulatedIntersection: ArrayList<Float>, particleArray: ArrayList<Array<Float>>, answerPositionXY:ArrayList<Double>): MutableMap<String, Float> {

        var minX = accumulatedIntersection[0]
        var maxX = accumulatedIntersection[1]
        var minY = accumulatedIntersection[2]
        var maxY = accumulatedIntersection[3]

        var answerX = answerPositionXY[0]
        var answerY = answerPositionXY[1]

        // IL 진행 중. 아직 수렴 안됨
        if((maxX > minX + boxLength) && (maxY > minY + boxLength)){
            return mutableMapOf("status_code" to 100.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)
        }// 영역 크기 작음
        else{
            foundpos = true
            return mutableMapOf("status_code" to 200.0f, "pos_x" to answerX.toFloat(), "pos_y" to answerY.toFloat())
        }
    }

    /** ansRange범위 안에 x, y가 있는지 확인 하는 함수
     *
     */
    private fun checkInRange(x: Double, y: Double, ansRange: ArrayList<Float>): Boolean{
        val minx = ansRange[0]
        val maxx = ansRange[1]
        val miny = ansRange[2]
        val maxy = ansRange[3]

        var returnBool = false
        if ((minx <= x) && (x <= maxx) && (miny <= y) && (y <= maxy)) {
            returnBool = true
        }
        return returnBool
    }
    fun getAccDec(ansRange: ArrayList<Float>): Float {
        //        var accuracy = successCnt * 100 / stepCount
        return (ansRange[1] - ansRange[0]) * (ansRange[3] - ansRange[2]) / (mapWidth.toFloat() * mapHeight.toFloat())
    }
    fun getDecreassion(){
        decreassion = getAccDec(ansRange)
    }
}