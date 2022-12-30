package com.example.mylibrary.wifiengine

import android.util.Log
import com.example.mylibrary.maps.WiFiDataMap
import com.example.mylibrary.maps.MagneticFieldMap
import java.lang.Exception
import kotlin.math.*


class WifimapRssiSequential constructor(wiFiDataMap: WiFiDataMap, map_hand: MagneticFieldMap) {
    private var instantResult : MutableMap<String, Float> = mutableMapOf("status_code" to -1.0f, "gyro_from_map" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)
    private var unqWifi: Int = 0

    // pos -> ansRange -> areaCheckPosList -> universalRange
    private var pos = arrayListOf<Int>()
    private var posx = arrayListOf<Int>()
    private var posy = arrayListOf<Int>()

    private var wifilist : Array<String>
    private var rssiTemp = ""
    private var wifilistsize : Int = 0

    private var wifi = mutableMapOf<Int, Array<String>>()
    private var wifiRssi = mutableMapOf<Int, Array<String>>()

    private var mapWidth : Int = 0
    private var mapHeight : Int = 0

    private var mapVector = map_hand

    private var universalRange = arrayListOf(0.0f, 0.0f, 0.0f, 0.0f)

    private var ansRange = arrayListOf(0.0f, 0.0f, 0.0f, 0.0f)

    private var testVector : Array<Int>
    private var rssiVector : Array<Int>

    private var angleStepNum : Int = 10
    private var angleList : IntArray = (0..359 step angleStepNum).toList().toIntArray()

    private var foundpos = false
    private var founddir = false

    private var curStep = -1

    // hanasquare
/*    private var early_stop_in_n_mother : Float = 4.0f
    private var rssiThres = -75
    private var rangeThres = 7
    private var secondRangeThres = 7 //7
    private var rssiRangeNum = 40
    private var secondRssiRangeNum = 40*/

    // anam staion
    // 지하 [-75,3], 플랫폼 [-66,4]
    private var rssiThres = -85
    private var rangeThres = 7
    private var rssiRangeNum = 40

    private var rssiRangeval = 0.0
    private var rangeval = 0

    private var tempGyro = 0.0f

    private var wifiParticleMotherList = arrayListOf<WifiParticleMother>()

    var rangeCheck = arrayListOf(0.0f, 0.0f, 0.0f, 0.0f)

    var areaCheckPosList = arrayListOf<Array<Float>>()

    private var origWifiData = ""

    private var correctionAngle = 0.0f

    private var areaCheckRange = arrayListOf(0.0f, 0.0f, 0.0f, 0.0f)
    private var particleNum = 0

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

        testVector = Array(wifilistsize, {0})
        rssiVector = Array(wifilistsize, {0})

        instantResult = mutableMapOf("status_code" to -1.0f, "gyro_from_map" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)
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
                "gyro_from_map" to -1.0f,
                "pos_x" to -1.0f,
                "pos_y" to -1.0f
            )
        }
    }

    /** 와이파이가 바뀌었는지 체크함. 
     *  입력: wifi ssid
     */ 
    private fun checkWifiChange(wifi_string: String): Boolean {
        if (origWifiData != wifi_string){
            origWifiData = wifi_string
            return true
        }
        return false
    }

    /**
     *  입력: [wifi ssid, 스텝 길이, ?????, 자이로값]
     *  출력: [상태코드, 방향각, x좌표, y좌표]
     */
    // 민혁 수정 불필요한 조건문 제거
    fun getLocation(wifi_string: String, stepLength: Double, gyro_for_pf: Float, gyro: Float) : Pair<MutableMap<String, Float>, Int> {
        val (instant_result_, unqWifi_) = getlocationWF(wifi_string, stepLength, gyro, gyro_for_pf)
        instantResult = instant_result_
        unqWifi = unqWifi_
        if(foundpos) { instantResult["status_code"] = 400.0f }



        return Pair(instantResult, unqWifi)
    }

    /** 초기 위치 수렴 이후에 영역체크 및 이동 시켜주는 함수
     *  출력: [상태코드, 방향각, x좌표, y좌표]
     */
    private fun getlocationWF(wifi_string: String, stepLength: Double, gyro: Float, gyro_for_pf:Float) : Pair<MutableMap<String, Float>, Int> {
        initParameters(instantResult["status_code"]!!)

        curStep += 1
        var isWifiChanged = checkWifiChange(wifi_string)

        // ssid, rssi 유사 상위 값으로만 ansRange 파티클 구성
        val unqWifi = vectorcompare(wifi_string)

        // if문이 같은 게 두 개로 나뉘어져 있어서 합침
        // 한 스텝 이동 후 교집합 영역을 universalRange에 넣음
        universalRange = moveRange(ansRange, universalRange, stepLength, isWifiChanged)

        if(foundpos){
            rangeCheck = ansRange
            moveArea(stepLength, gyro_for_pf)
            if(areaCheckPosList.size == 0){
                correctionAngle = 0.0f
                firstFindArea(universalRange)
            }
        }
        else{
            firstFindArea(universalRange)
        }


        if (instantResult["status_code"] == 400.0f){
            return Pair(instantResult, unqWifi)
        }
        if(!foundpos) {
            if (curStep == 0) {
                if (foundpos) {
                    firstFindParticles(universalRange)
                } else {
                    firstFindParticles(
                        arrayListOf(
                            ansRange[0], ansRange[1],
                            ansRange[2], ansRange[3]
                        )
                    )
                }
                instantResult["status_code"] = 100.0f
                return Pair(instantResult, unqWifi)
            }

            var curIdx = -1
            while (true) {
                curIdx += 1
                if (wifiParticleMotherList.size == 0) {
                    instantResult["status_code"] = 400.0f
                    return Pair(instantResult, unqWifi)
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
            val (instant_result_, particle_num_) = estimateInitialDirAndPos(wifiParticleMotherList, gyro)
            instantResult = instant_result_
            particleNum = particle_num_

            // 11.22 bada check 3
            Log.d("locationWF_instant", instantResult.toString())
            Log.d("getlocationWF_unique", particleNum.toString())
        }
        return Pair(instantResult, unqWifi)
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
    private fun vectorcompare(wifi_string: String): Int{

        testVector = Array(wifilistsize, {0})
        rssiVector = Array(wifilistsize, {0})

        // unique ssid 일치 개수
        val ssidCntList = arrayListOf<Int>()
        // rssi 차이값
        val rssiDiffList = arrayListOf<Double>()

        val splitline = wifi_string.split("\r\n").toTypedArray()

        for (i in splitline){
            val data = i.split("\t").toTypedArray()
            if(data.size == 2) {
                val ssid = data[0]
                val rssi = data[1].toInt()
                rssiTemp = data[1]
                Log.d("RSSŠ", rssiTemp)
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
            if (rssiCnt >= rangeval){
                rssiDiffList.add(rssiSum / rssiCnt)
            } else{
                rssiDiffList.add(100000.0)
            }
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
        Log.d("comm", (unqCntList[0]).toString())
        Log.d("wifimap_cnt", ((unqCntList[0]/ wifilist.size) * 100).toString())
        return (unqCntList[0]/ wifilist.size)*100
    }

    /** 전역변수 pos(전 좌표) 모든 x,y좌표가 범위에 있는지 체크해주는 함수. 전 좌표 내의 범위에 있으면 해당 좌표를 areaCheckPosList에 추가
     * 입력:[죄표 범위를 나타내는 리스트]
     */
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

    /**
     *
     */
    private fun moveArea(step_length : Double, gyro : Float){
        var tempAreaList = arrayListOf<Array<Float>>()
        var minx = 1000000f
        var miny = 1000000f
        var maxx = 0f
        var maxy = 0f
        // 전 좌표 영역중 현스텝 부분에서  다음
        // 다음 스텝 모든 범위를 계산할 게 아니라 아웃라인만 검사해도 될듯
        for (pos in areaCheckPosList) {
            var posx = pos[0]
            var posy = pos[1]
            posx -= (step_length * 10 * sin((-correctionAngle - gyro) * PI / 180)).toFloat()
            posy += (step_length * 10 * cos((-correctionAngle - gyro) * PI / 180)).toFloat()

            // 중복됨 여기가 제일 처음 필터링 하는 부분이니 아예 필터링을 시켜서 저장해야할듯
            // firstfindArea()에서 이미 필터링한 애들만 areaCheckPosList에 있음
            if (mapVector.isPossiblePosition(
                    posx.toDouble(),
                    posy.toDouble()
                )
            ) {
                //다음 스텝 애들 중 교집합에 들어가는 애들만 tempAreaList에 넣음
                // 뒤에서 중복되니 여기서 필터링 시킨 애들만 남기는 게 좋을듯
                if (checkInRange(posx.toDouble(), posy.toDouble(), universalRange)) {
                    tempAreaList.add(arrayOf(posx, posy))
                    minx = min(posx, minx)
                    maxx = max(posx, maxx)
                    miny = min(posy, miny)
                    maxy = max(posy, maxy)
                }
            }
        }

        // 각도 돌아가며 다음 스텝과 교집합 영역과 겹치는 영역 중 제일 큰 영역 tempAreaList에 대입
        // 다음 스텝
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
                val temp1 = findAngle(step_length, gyro, 10.0f * (cnt))
                val temp2 = findAngle(step_length, gyro, -10.0f * (cnt))
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
//                xAvg -= 3 * (step_length * 10 * sin((- correctionAngle - gyroResult) * PI / 180)).toFloat()
                val xMin = (xAvg - 18).toInt()
                val xMax = (xAvg + 18).toInt()
                val yMin = miny.toInt()
                val yMax = maxy.toInt()
                for (i in (xMin)..(xMax)) {
                    for (j in (yMin)..(yMax)) {
                        if (mapVector.isPossiblePosition(
                                i.toDouble(),
                                j.toDouble()
                            )
                        ) {
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
//                yAvg += 3 * (step_length * 10 * cos((- correctionAngle - gyroResult) * PI / 180)).toFloat()
                val xMin = minx.toInt()
                val xMax = maxx.toInt()
                val yMin = (yAvg - 18).toInt()
                val yMax = (yAvg + 18).toInt()
                for (i in (xMin)..(xMax)) {
                    for (j in (yMin)..(yMax)) {
                        if (mapVector.isPossiblePosition(
                                i.toDouble(),
                                j.toDouble()
                            )
                        ) {
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

    /** 현스텝과 다음 스텝 교집합 영역 출력 및 areaCheckRange에 입력
     * 입력: [스텝길이, 자이로값, 찾으려는 각도 값]
     */
    private fun findAngle(step_length : Double, gyro : Float, search_angle : Float) : ArrayList<Array<Float>>{
        val tempAreaList = arrayListOf<Array<Float>>()
        var minx = 1000000f
        var miny = 1000000f
        var maxx = 0f
        var maxy = 0f
        // 다음 스텝과 교집합 영역
        for (pos in areaCheckPosList) {
            var posx = pos[0]
            var posy = pos[1]
            posx -= (step_length * 10 * sin((-correctionAngle - search_angle - gyro) * PI / 180)).toFloat()
            posy += (step_length * 10 * cos((-correctionAngle - search_angle - gyro) * PI / 180)).toFloat()

            if (mapVector.isPossiblePosition(
                    posx.toDouble(),
                    posy.toDouble()
                )
            ) {
                if (checkInRange(posx.toDouble(), posy.toDouble(), universalRange)) {
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

    /** 한 스텝 이동 후의 범위를 출력함. 단, 와이파이가 바뀌었을 경우 이전 스텝이랑 현재 스텝의 교집합 영역을 출력함.
     * 만약 범위가 3걸음 이내(18범위)라면 현재 스텝의 범위만 표시함
     * 입력:[]
     */
    private fun moveRange(ansRange: ArrayList<Float>, universalRange:ArrayList<Float>, step_length : Double, isWifiChanged: Boolean): ArrayList<Float>{
        var minX = universalRange[0]
        var maxX = universalRange[1]
        var minY = universalRange[2]
        var maxY = universalRange[3]

        minX -= (step_length * 10).toFloat()
        maxX += (step_length * 10).toFloat()

        minY -= (step_length * 10).toFloat()
        maxY += (step_length * 10).toFloat()
//
        if(isWifiChanged) {
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

    /** 파티클을 움직이는 함수. 모든 children 이동 후 ansRange(현재 움직인 파티클)영역에 없으면 제거함
     * 입력: [mother파티클, 스텝길이, 자이로값]
     */
    // while문이 가독성이 떨어짐 -> for문으로 변경하는 게 가독성, 계산 속도면에서 좋을듯
    private fun moveChildren(particleMother: WifiParticleMother, step_length : Double, gyro : Float) {
        var curIdx = -1

        // mother의 children 개수만큼 반복
        while (true) {
            curIdx += 1
            if (curIdx == particleMother.particleChildrenList.size) {
                break
            }
            val children = particleMother.particleChildrenList[curIdx]

            children.x -= (step_length * 10 * sin((particleMother.myAngle - gyro) * PI / 180)).toFloat()
            children.y += (step_length * 10 * cos((particleMother.myAngle - gyro) * PI / 180)).toFloat()
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
    private fun estimateInitialDirAndPos(mother_list: List<WifiParticleMother>, gyro: Float): Pair<MutableMap<String, Float>, Int> {
        val numOfMother = wifiParticleMotherList.size
        particleNum = numOfMother
        lateinit var bestMother : WifiParticleMother
        // 11.22 bada check 4
        // 위치 후보군이 여러개일 때
        // IL 진행 중. 아직 수렴 안됨
        if(numOfMother >= 2){
            areaCheckPosList = arrayListOf()
            Log.d("numOfMother >= 2",numOfMother.toString())
            return Pair(mutableMapOf("status_code" to 100.0f, "gyro_from_map" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f), particleNum)
        }
        // 위치 후보군이 한 개일 때, 방향만 수렴
        else if(numOfMother == 1){
            bestMother = mother_list[0]
            Log.d("numOfMother == 1",bestMother.toString())
            if(!founddir) {
                founddir = true
            }

        }else if(numOfMother == 0){
            // 400.0f -> IL 혹은 AON 에러. 수렴하지 못함.
            Log.d("numOfMother == 0",bestMother.toString())
            return Pair(mutableMapOf("status_code" to 400.0f, "gyro_from_map" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f), particleNum)
        }
        // 혹시 모를 에러를 방지
        val numOfChildren = bestMother.particleChildrenList.size
        if (numOfChildren == 0) {
            // 400.0f -> IL 혹은 AON 에러. 수렴하지 못함.
            return Pair(mutableMapOf("status_code" to 400.0f, "gyro_from_map" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f),particleNum)
        }

        val answerPositionXY = calculateAnswerPosition(bestMother)
        val answerX = answerPositionXY[0].toFloat()
        val answerY = answerPositionXY[1].toFloat()
        val answerDir = ((((360 - (bestMother.myAngle)) + gyro) + 360) % 360)

        foundpos = true
        founddir = true
        tempGyro = answerDir
        // 200.0f -> IL 완료. 완전 수렴
        return Pair(mutableMapOf("status_code" to 200.0f, "gyro_from_map" to answerDir, "pos_x" to answerX, "pos_y" to answerY),particleNum)
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
}