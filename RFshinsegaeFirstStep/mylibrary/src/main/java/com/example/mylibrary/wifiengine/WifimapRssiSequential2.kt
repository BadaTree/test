package com.example.mylibrary.wifiengine

import android.util.Log
import com.example.mylibrary.maps.WiFiDataMap
import com.example.mylibrary.maps.MagneticFieldMap
import kotlin.math.*


class WifimapRssiSequential2 constructor(wiFiDataMap: WiFiDataMap, map_hand: MagneticFieldMap) {
    private var instantResult : MutableMap<String, Float> = mutableMapOf("status_code" to -1.0f, "gyro_from_map" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)

    internal var pos = arrayListOf<Int>()
    private var posx = arrayListOf<Int>()
    private var posy = arrayListOf<Int>()

    private var wifilist : Array<String>
    private var wifilistsize : Int = 0
    private var wifidata = ""

    private var wifi = mutableMapOf<Int, Array<String>>()
    private var wifiRssi = mutableMapOf<Int, Array<String>>()

    private var tempWifiRssi = mutableMapOf<Int, Double>()
    private var tempWifiCnt = mutableMapOf<Int, Int>()


    private var mapWidth : Int = 0
    private var mapHeight : Int = 0

    private var mapVector = map_hand

    private var universalRange = arrayListOf(0.0f, 0.0f, 0.0f, 0.0f)
    private var universalRange2 = arrayListOf(0.0f, 0.0f, 0.0f, 0.0f)

    private var ansRange = arrayListOf(0.0f, 0.0f, 0.0f, 0.0f)

    private var testVector : Array<Int>
    private var rssiVector : Array<Int>

    private var angleStepNum : Int = 10
    private var angleList : IntArray = (0..359 step angleStepNum).toList().toIntArray()

    private var foundpos = false
    private var founddir = false

    private var curStep = -1
    //하나스퀘어
    private var rssiThres = -75
    private var rangeThres = 7
    private var rssiRangeNum = 40

    private var rssiRangeval = 0.0
    private var rangeval = 0

    private var tempGyro = 0.0f

    private var wifiParticleMotherList = arrayListOf<WifiParticleMother>()

    var areaCheckPosList = arrayListOf<Array<Float>>()

    private var origWifiData = ""
    private var wifiDataChange = false

    private var correctionAngle = 0.0f
    var rangeCheck = arrayListOf(0.0f, 0.0f, 0.0f, 0.0f)

    var areaCheckRange = arrayListOf(0.0f, 0.0f, 0.0f, 0.0f)
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

        instantResult = mutableMapOf("status_code" to -1.0f, "gyro_from_map" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)
    }

    private fun initParameters(status_code: Float){
        if ((status_code == 200.0f) || (status_code == 202.0f)){
            angleList = (0..359 step angleStepNum).toList().toIntArray()
            curStep = -1
            wifiParticleMotherList = arrayListOf()
            instantResult = mutableMapOf("status_code" to -1.0f, "gyro_from_map" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)
        }
        else if (status_code == 400.0f) {

            angleList = (0..359 step angleStepNum).toList().toIntArray()
            curStep = -1
            wifiParticleMotherList = arrayListOf()
            instantResult = mutableMapOf("status_code" to -1.0f, "gyro_from_map" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)
        }
    }
    private fun checkWifiChange(wifiString: String){
        if (origWifiData != wifiString){
            origWifiData = wifiString
            wifiDataChange = true
        }
    }

    fun getLocation(wifiString: String, stepLength: Double, gyro_for_pf: Float, gyro: Float) : MutableMap<String, Float> {

        if(!foundpos) {
            instantResult = getlocationWF(wifiString, stepLength, gyro, gyro_for_pf)
        }
        else {
            instantResult = getlocationWF(wifiString, stepLength, gyro, gyro_for_pf)
            instantResult["status_code"] = 400.0f
        }
        return instantResult
    }

    private fun getlocationWF(wifiString: String, stepLength: Double, gyro: Float, gyro_for_pf:Float) : MutableMap<String, Float>{
        initParameters(instantResult["status_code"]!!)

        curStep += 1
        checkWifiChange(wifiString)

        vectorcompare(wifiString)

        universalRange = ansRange
        universalRange2 = moveRange2(ansRange, universalRange2, stepLength)

        if(foundpos){
            rangeCheck = universalRange
            moveArea(stepLength, gyro_for_pf)
            if(areaCheckPosList.size == 0){
                correctionAngle = 0.0f
                firstFindArea(universalRange2)
            }
        }else{
            firstFindArea(universalRange2)
        }
        wifiDataChange = false

        if (instantResult["status_code"] == 400.0f){
            return instantResult
        }
        if(!foundpos) {
            if (curStep == 0) {
                firstFindParticles(arrayListOf(ansRange[0], ansRange[1],ansRange[2], ansRange[3]))
                instantResult["status_code"] = 100.0f
                return instantResult
            }

            var curIdx = -1
            while (true) {
                curIdx += 1
                if (wifiParticleMotherList.size == 0) {
                    instantResult["status_code"] = 400.0f
                    return instantResult
                }
                val particleMother = wifiParticleMotherList[curIdx]
                moveChildren(particleMother, stepLength, gyro)
//                matchingChildren(particleMother)
                if (particleMother.particleChildrenList.isEmpty()) {
                    wifiParticleMotherList.remove(particleMother)
                    curIdx -= 1
                }
                if (curIdx == (wifiParticleMotherList.size - 1)) {
                    break
                }
            }
            instantResult = estimateInitialDirAndPos(wifiParticleMotherList, gyro)
        }
        return instantResult
    }

    private fun vectorcompare(wifiString: String){
        wifidata = wifiString
        tempWifiCnt = mutableMapOf()
        tempWifiRssi = mutableMapOf()

        testVector = Array(wifilistsize) { 0 }
        rssiVector = Array(wifilistsize) { 0 }

        val cntList = arrayListOf<Int>()
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
                }
            }
        }

        // unq wifi 개수 구하기
        for (i in pos){
            var bothCnt = 0

            for (j in 0 until wifilistsize step(1)){
                try {
                    if (testVector[j] + wifi[i]!![j].toInt() == 2) {
                        bothCnt += 1
                    }
                } catch (e: Exception){Log.d("err", "error is $e")}
            }
            tempWifiCnt[i] = bothCnt
            cntList.add(bothCnt)
        }

        val unqCntList = cntList.distinct().sortedDescending()


        val rangeIdx = if (unqCntList.size <= rangeThres){
            unqCntList.size - 1
        } else{
            rangeThres
        }

        rangeval = unqCntList[rangeIdx]

        // rssi 유사도 구하기
        for (i in pos){
            var bothCnt = 0
            var rssiSum = 0.0

            for (j in 0 until wifilistsize step(1)){
                if(testVector[j] + wifi[i]!![j].toInt() == 2){
                    bothCnt += 1
                    rssiSum += abs(rssiVector[j] - wifiRssi[i]!![j].toInt())
                }
            }
            tempWifiRssi[i] = rssiSum
            if (bothCnt >= rangeval){
                rssiDiffList.add(rssiSum / bothCnt)
            } else{
                rssiDiffList.add(100000.0)
            }
        }

        val unqRssiDiffList = rssiDiffList.distinct().sorted()

        val rssiRangeIdx = if (unqRssiDiffList.size <= rssiRangeNum){
            unqRssiDiffList.size - 1
        } else{
            rssiRangeNum
        }
        rssiRangeval = unqRssiDiffList[rssiRangeIdx]

        val forRangeRangeval = rangeval//unqCntList[5]
        val forRangeRssiRangeval = rssiRangeval//unqRssiDiffList[40]

        val xList = arrayListOf<Int>()
        val yList = arrayListOf<Int>()

        for (i in pos.indices){
            if((cntList[i] >= forRangeRangeval) and (rssiDiffList[i] <= forRangeRssiRangeval)){
                xList.add(posx[i])
                yList.add(posy[i])
            }
        }

        ansRange = if (xList.minOrNull() == null){
            arrayListOf(0.0f, mapWidth.toFloat(), 0.0f, mapHeight.toFloat())
        }else{
            arrayListOf(xList.minOrNull()!!.toFloat(), xList.maxOrNull()!!.toFloat(),
                yList.minOrNull()!!.toFloat(), yList.maxOrNull()!!.toFloat())
        }
    }
    private fun firstFindArea(ansRange : ArrayList<Float>){
        val minX = ansRange[0]
        val maxX = ansRange[1]
        val minY = ansRange[2]
        val maxY = ansRange[3]

        areaCheckPosList = arrayListOf()
        for (i in pos.indices){
            if((minX <= posx[i]) && (posx[i] <= maxX) && (minY <= posy[i]) && (posy[i] <= maxY)){
                if (mapVector.isPossiblePosition(posx[i].toDouble(), posy[i].toDouble())) {
                    areaCheckPosList.add(arrayOf(posx[i].toFloat(), posy[i].toFloat()))
                }
            }
        }

    }
    /** 전 좌표 영역에 있는 모든 좌표 중 범위 내에 있으면 추가
     *  angleList에 있는 모든 값
     */
    private fun firstFindParticles(ansRange : ArrayList<Float>){
        val minX = ansRange[0]
        val maxX = ansRange[1]
        val minY = ansRange[2]
        val maxY = ansRange[3]

        val coordx = arrayListOf<Int>()
        val coordy = arrayListOf<Int>()

        for (i in pos.indices){
            if((minX <= posx[i]) && (posx[i] <= maxX) && (minY <= posy[i]) && (posy[i] <= maxY)){
                coordx.add(posx[i])
                coordy.add(posy[i])
            }
        }

        for (i in angleList.indices){
            wifiParticleMotherList.add(WifiParticleMother(angleList[i]))
            for (j in coordx.indices){
                if (wifiParticleMotherList.size == 0) {
                    wifiParticleMotherList.add(WifiParticleMother(angleList[i]))
                }
                wifiParticleMotherList[wifiParticleMotherList.size - 1].appendChildren(
                    arrayListOf(coordx[j].toFloat(), coordy[j].toFloat()))
            }
        }
    }

    //    fun firstFindParticles(ansRange : ArrayList<Float>){
//        var minX = ansRange[0]
//        var maxX = ansRange[1]
//        var minY = ansRange[2]
//        var maxY = ansRange[3]
//
//        var coordx = arrayListOf<Int>()
//        var coordy = arrayListOf<Int>()
//
//        var coord_particle = listOf<WiFiParticle>()
//        for (i in pos.indices){
//            if((minX <= posx[i]) && (posx[i] <= maxX) && (minY <= posy[i]) && (posy[i] <= maxY)){
//                coord_particle = coord_particle.plus(WiFiParticle(arrayListOf(posx[i].toFloat(), posy[i].toFloat())))
//            }
//        }
//
//
//        for (i in angleList.indices){
//            wifiParticleMotherList.add(WifiParticleMother(angleList[i]))
//            if (wifiParticleMotherList.size == 0) {
//                wifiParticleMotherList.add(WifiParticleMother(angleList[i]))
//            }
//            wifiParticleMotherList[wifiParticleMotherList.size - 1].add_children_whole(coord_particle)
//            }
//
//
//    }
    private fun moveArea(stepLength : Double, gyro : Float){
        var tempAreaList = arrayListOf<Array<Float>>()
        var minx = 1000000f
        var miny = 1000000f
        var maxx = 0f
        var maxy = 0f

        // areaCheckPosList에서 다음 좌표를 tempAreaList에 넣음
        // 다음 스텝 모든 범위를 계산할 게 아니라 아웃라인만 검사해도 될듯
        for (pos in areaCheckPosList) {
            var posx = pos[0]
            var posy = pos[1]
            posx -= (stepLength * 10 * sin((-correctionAngle - gyro) * PI / 180)).toFloat()
            posy += (stepLength * 10 * cos((-correctionAngle - gyro) * PI / 180)).toFloat()
            if (mapVector.isPossiblePosition(posx.toDouble(),posy.toDouble())) {
                if (checkInRange(posx.toDouble(), posy.toDouble(), universalRange2)) {
                    tempAreaList.add(arrayOf(posx, posy))
                    minx = min(posx, minx)
                    maxx = max(posx, maxx)
                    miny = min(posy, miny)
                    maxy = max(posy, maxy)
                }
            }
        }

        // 현 스텝과 다음 스텝이 하나도 안 겹칠 경우 각도 재설정
        // 각도 돌아가며 다음 스텝과 교집합 영역과 겹치는 영역 중 제일 큰 영역 areaCheckPosList에 대입
        // 다음 스텝: areaCheckPosList, 다음 각도
        if (tempAreaList.size == 0){
            var cnt = 0
            var ansAngle = 0.0f
            while(true){
                if(cnt == 19){
                    areaCheckPosList = tempAreaList
                    break
                }
                if(tempAreaList.size != 0){
                    areaCheckPosList = tempAreaList
                    correctionAngle = (ansAngle + 360) % 360
                    break
                }
                cnt += 1
                val temp1 = findAngle(stepLength, gyro, 10.0f * (cnt))
                val temp2 = findAngle(stepLength, gyro, -10.0f * (cnt))
                if((temp1.size != 0) || (temp2.size != 0)){
                    if(temp1.size >= temp2.size){
                        tempAreaList = temp1
                        ansAngle = 10.0f * cnt
                    }else{
                        tempAreaList = temp2
                        ansAngle = -10.0f * cnt
                    }
                }
            }
        }else{
            areaCheckPosList = tempAreaList
            areaCheckRange[0] = minx
            areaCheckRange[1] = maxx
            areaCheckRange[2] = miny
            areaCheckRange[3] = maxy
        }
        if(areaCheckPosList.size != 0) {
            if ((areaCheckRange[1] - areaCheckRange[0]) < 36) {
                val tempAreaList = arrayListOf<Array<Float>>()
                val xAvg = (minx + maxx) / 2
//                xAvg -= 3 * (stepLength * 10 * sin((- correctionAngle - gyroResult) * PI / 180)).toFloat()
                val xMin = (xAvg - 18).toInt()
                val xMax = (xAvg + 18).toInt()
                val yMin = miny.toInt()
                val yMax = maxy.toInt()
                for (i in (xMin)..(xMax)) {
                    for (j in (yMin)..(yMax)) {
                        if (mapVector.isPossiblePosition(i.toDouble(),j.toDouble())) {
                            tempAreaList.add(arrayOf(i.toFloat(), j.toFloat()))
                        }
                    }
                }
                areaCheckRange[0] = xMin.toFloat()
                areaCheckRange[1] = xMax.toFloat()

                areaCheckPosList = tempAreaList
            }
            if ((areaCheckRange[3] - areaCheckRange[2]) < 36) {
                val tempAreaList = arrayListOf<Array<Float>>()
                val yAvg = (miny + maxy) / 2
//                yAvg += 3 * (stepLength * 10 * cos((- correctionAngle - gyroResult) * PI / 180)).toFloat()
                val xMin = minx.toInt()
                val xMax = maxx.toInt()
                val yMin = (yAvg - 18).toInt()
                val yMax = (yAvg + 18).toInt()
                for (i in (xMin)..(xMax)) {
                    for (j in (yMin)..(yMax)) {
                        if (mapVector.isPossiblePosition(i.toDouble(),j.toDouble())) {
                            tempAreaList.add(arrayOf(i.toFloat(), j.toFloat()))
                        }
                    }
                }
                areaCheckRange[2] = yMin.toFloat()
                areaCheckRange[3] = yMax.toFloat()

                areaCheckPosList = tempAreaList
            }
        }
    }

    private fun findAngle(stepLength : Double, gyro : Float, search_angle : Float) : ArrayList<Array<Float>>{
        val tempAreaList = arrayListOf<Array<Float>>()
        var minx = 1000000f
        var miny = 1000000f
        var maxx = 0f
        var maxy = 0f
        for (pos in areaCheckPosList) {
            var posx = pos[0]
            var posy = pos[1]
            posx -= (stepLength * 10 * sin((-correctionAngle - search_angle - gyro) * PI / 180)).toFloat()
            posy += (stepLength * 10 * cos((-correctionAngle - search_angle - gyro) * PI / 180)).toFloat()

            if (mapVector.isPossiblePosition(
                    posx.toDouble(),
                    posy.toDouble()
                )
            ) {
                if (checkInRange(posx.toDouble(), posy.toDouble(), universalRange2)) {
                    tempAreaList.add(arrayOf(posx, posy))
                    minx = min(posx, minx)
                    maxx = max(posx, maxx)
                    miny = min(posy, miny)
                    maxy = max(posy, maxy)
                }
            }
        }
        if(tempAreaList.size != 0){
            areaCheckRange[0] = minx
            areaCheckRange[1] = maxx
            areaCheckRange[2] = miny
            areaCheckRange[3] = maxy
        }
        return tempAreaList
    }

    private fun moveRange2(ansRange: ArrayList<Float>, universalRange:ArrayList<Float>, stepLength : Double)
            : ArrayList<Float>{
        var minX = universalRange[0]
        var maxX = universalRange[1]
        var minY = universalRange[2]
        var maxY = universalRange[3]

        minX -= (stepLength * 10).toFloat()
        maxX += (stepLength * 10).toFloat()

        minY -= (stepLength * 10).toFloat()
        maxY += (stepLength * 10).toFloat()
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
    private fun moveChildren(particleMother: WifiParticleMother, stepLength : Double, gyro : Float) {
        var curIdx = -1
        while (true) {
            curIdx += 1
            if (curIdx == particleMother.particleChildrenList.size) {
                break
            }
            val children = particleMother.particleChildrenList[curIdx]

            children.x -= (stepLength * 10 * sin((particleMother.myAngle - gyro) * PI / 180)).toFloat()
            children.y += (stepLength * 10 * cos((particleMother.myAngle - gyro) * PI / 180)).toFloat()
//            if (mapVector.isPossiblePosition(children.x.toDouble(), children.y.toDouble()) == false) {
            if (!checkInRange(
                    children.x.toDouble(),
                    children.y.toDouble(),
                    ansRange
                )
            ) {
                particleMother.removeChildren(curIdx)
                curIdx -= 1
            }
//            }
        }
    }
    /** 방향 수렴 후 초기 위치 계산(수렴 판단)
     *
     */
    private fun calculateAnswerPosition(mother : WifiParticleMother) : ArrayList<Double>{
        var answerX = 0.0
        var answerY = 0.0

        // 살아남은 파티클들의 x좌표 합(answerX),y 좌표 합(answerY) 계산
        for (children in mother.particleChildrenList){
            answerX += children.x
            answerY += children.y
        }

        // 살아남은 파티클들의 무게중심 계산
        val numOfChildren = mother.particleChildrenList.size
        answerX /= numOfChildren
        answerY /= numOfChildren

        // 무게중심과 파티클 사이의 거리 평균 계산
        var distAvg = 0.0
        for (children in mother.particleChildrenList) {
            distAvg += sqrt((answerX - children.x).pow(2.0) + (answerY - children.y).pow(2.0)) * 0.1
        }
        distAvg /= mother.particleChildrenList.size
        if (distAvg > 3.0) {
            return arrayListOf(-1.0, -1.0)
        }
        return arrayListOf(answerX, answerY)
    }

    /** 초기 위치 및 방향 추정. 입력: [파티클 mother리스트,자이로값]
     * 모파티클 리스트에 1개만 남았을 시 방향 수렴
     */
    private fun estimateInitialDirAndPos(mother_list: List<WifiParticleMother>, gyro: Float): MutableMap<String, Float> {
        val numOfMother = wifiParticleMotherList.size
        lateinit var bestMother : WifiParticleMother

        if(numOfMother >= 2){
            return mutableMapOf("status_code" to 100.0f, "gyro_from_map" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)
        }
        else if(numOfMother == 1){
            bestMother = mother_list[0]
            if(!founddir) {
                founddir = true
//                var minx = 1000000f
//                var miny = 1000000f
//                var maxx = 0f
//                var maxy = 0f
//
//                areaCheckPosList = arrayListOf<Array<Float>>()
//                for (c in bestMother.particleChildrenList) {
//                    areaCheckPosList.add(arrayOf(c.x, c.y))
//                }
            }
        }else if(numOfMother == 0){
            return return mutableMapOf("status_code" to 400.0f, "gyro_from_map" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)
        }
        // 혹시 모를 에러를 방지
        val numOfChildren = bestMother.particleChildrenList.size
        if (numOfChildren == 0) {
            return return mutableMapOf("status_code" to 400.0f, "gyro_from_map" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)
        }

        val answerPositionXY = calculateAnswerPosition(bestMother)
        val answerX = answerPositionXY[0].toFloat()
        val answerY = answerPositionXY[1].toFloat()
        val answerDir = ((((360 - (bestMother.myAngle)) + gyro) + 360) % 360)

        return if (answerPositionXY == arrayListOf(-1.0, -1.0)) {
            founddir = true
            foundpos = true
            tempGyro = answerDir

            mutableMapOf("status_code" to 200.0f, "gyro_from_map" to answerDir, "pos_x" to answerX, "pos_y" to answerY)
    //            return return mutableMapOf("status_code" to 101.0f, "gyro_from_map" to answerDir, "pos_x" to -1.0f, "pos_y" to -1.0f)
        } else {
            foundpos = true
            founddir = true
            tempGyro = answerDir
            mutableMapOf("status_code" to 200.0f, "gyro_from_map" to answerDir, "pos_x" to answerX, "pos_y" to answerY)
        }
    }

    /** ansRange범위 안에 x, y가 있는지 확인 하는 함수
     *
     */
    private fun checkInRange(x: Double, y: Double, ansRange: ArrayList<Float>): Boolean{
//        if(wifiDataChange) {
//            var minx = ansRange[0]
//            var maxx = ansRange[1]
//            var miny = ansRange[2]
//            var maxy = ansRange[3]
////            return mutableMapOf("status_code" to 100.0f, "gyro_from_map" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)
//
//            var returnBool = false
//            if ((minx <= x) && (x <= maxx) && (miny <= y) && (y <= maxy)) {
//                returnBool = true
//            }
//            return returnBool
//        }else{
//            return true
//        }
        val minx = ansRange[0]
        val maxx = ansRange[1]
        val miny = ansRange[2]
        val maxy = ansRange[3]
//            return mutableMapOf("status_code" to 100.0f, "gyro_from_map" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)

        var returnBool = false
        if ((minx <= x) && (x <= maxx) && (miny <= y) && (y <= maxy)) {
            returnBool = true
        }
        return returnBool
    }


}