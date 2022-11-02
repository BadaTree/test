package com.example.cslabposco

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.util.Log
import com.example.cslabposco.filters.ParticleFilter
import com.example.cslabposco.instant.InstantLocalization
import com.example.cslabposco.maps.BuilInfo
import com.example.cslabposco.maps.BuildingInfo
import com.example.cslabposco.maps.MagneticFieldMap
import com.example.cslabposco.sensors.GPS
import com.example.cslabposco.sensors.GetGyroscope
import com.example.cslabposco.sensors.VectorCalibration
import com.example.heropdr.*
import java.io.InputStream
import java.util.ArrayList
import kotlin.math.*

class ExIndoorLocalization {
    constructor(inputStreamOfMap : MagneticFieldMap, inputStreamOfInstant : ArrayList<FloatArray>, inputStreamOfBuilding : InputStream, gpsArray : ArrayList<Double>) {
        map = inputStreamOfMap
//        instantLocalization = InstantLocalization(map, inputStreamOfInstant)
        ILEngine = ILEngine(map, inputStreamOfInstant)
        setGPS(gpsArray)
        buildingInfo = BuildingInfo(inputStreamOfBuilding)
        getGyroscope.init()
        thread = InstantLocalizationThread()
        thread.start()
    }

    //    private var mag_cnt: Int = 0
    private var map : MagneticFieldMap
    private val buildingInfo : BuildingInfo
    private val lstmServer : LSTMServer = LSTMServer()
    private var magMatrix = FloatArray(3)
    private var accMatrix = FloatArray(3)

    // PDR 관련 & Particle Filter 관련
    private val heroPDR : HeroPDR = HeroPDR()
    private lateinit var pdrResult : PDR
    private lateinit var particleFilter: ParticleFilter
    private val accXMovingAverage : MovingAverage = MovingAverage(10)
    private val accYMovingAverage : MovingAverage = MovingAverage(10)
    private val accZMovingAverage : MovingAverage = MovingAverage(10)
    private var userDir : Double = 0.0
    private var stepCount : Int = 0
    private var devicePosture : Int = 0
    private var stepType : Int = 0
    private var particleOn : Boolean = false
    private var nowPosition: Array<Double> = arrayOf()
    private var returnGyro : String = "0.0"
    private var returnIns : String = "not support"
    private var returnX : String = "unknown"
    private var returnY : String = "unknown"
    private val poseTypes = arrayOf("On Hand", "In Pocket", "Hand Swing")
    private val stepTypes = arrayOf("normal", "fast", "slow", "prowl", "non")

    // 센서 안정화 관련
    private var isSensorStabled : Boolean = false
    private var magStableCount : Int = 100
    private var accStableCount : Int = 50

    // GPS 관련
    private val gps : GPS = GPS()
    private var lat : Double = 0.0
    private var long : Double = 0.0
    private var first_range : Array<Int> = arrayOf(0, 0, 0, 0)

    // WiFi
    var wifi_range = arrayListOf(-100, -100, -100, -100)
    var wifi_first_scan = true


    // Gyroscope 관련 //
    /*
    용도별 자이로 값 설명 (이 주석은 닫아놓고 쓰세요. 코드 왼쪽 (-) 아이콘 누르면 닫힘.)
    1. gyro_constant_from_app_start_to_map_collection :
                단위 : degree
                기준 : -
                특징 : 첫 수렴 이후, [맵 수집 방향]과 [앱 시작시의 방향]의 차이를 갖고 있음.
                       첫 수렴 이후에 값이 정해지며, 이후에는 자이로가 변해도 그냥 계속 값이 일정한 상수임.
                       한준이형만 신경쓰면 되는 값.

    2. gyro_value_map_collection :
                단위 : degree
                기준 : 수렴전 - 앱 시작시의 방향 / 수렴후 - 맵 수집 방향
                특징 : gyro_value_reset_direction + gyroCalivalue 의 결과값임.
                       맵 수집 방향을 기준으로 회전된 각도이므로, 수렴 이후 vector calibration을 통해 자기장 맵과 지문을 일치시키기 위한 용도로 사용됨.
                       Always On Instant 에 의해 수시로 보정됨.
                       Particle Filter에서 사용.

    3. gyro_value_reset_direction :
                단위 : degree
                기준 : gyro reset이 됐을 때의 방향
                특징 : gyro reset이 될 때마다 기준 방향이 변하기 때문에 해당 값이 수시로 변함.
                       Always On Instant를 위해 사용됨.

     */
    private var gyro_constant_from_app_start_to_map_collection : Float = 0f
    private var gyro_value_map_collection : Float = 0f
    private var gyro_value_reset_direction : Float = 0f
    private val getGyroscope by lazy {
        GetGyroscope()
    }

    // Vector Calibration 관련 //
    private val vectorCalibration = VectorCalibration()
    private var caliVector: Array<Double> = arrayOf()
    private var caliVector_AON: Array<Double> = arrayOf()
    private var caliX_Q : Double= 0.0
    private var caliY_Q : Double= 0.0
    private var caliZ_Q : Double= 0.0

    // Instant Localization 관련 //
//    private val instantLocalization : InstantLocalization
    private var magneticQueue : ArrayList<Array<Double>> = arrayListOf()
    private var ILResult : MutableMap<String, Float> = mutableMapOf()
    private var AON_result : MutableMap<String, Float> = mutableMapOf()
    private var instant_thread_on : Boolean = true
    private var first_sampling : Boolean = true
    private var always_on_mode : Boolean = false
    private var did_finish_AON : Boolean = false
    private var ILEngine : ILEngine
    var thread : InstantLocalizationThread

    // Deep Learning //
    private var DL_PF_position : Array<Double> = arrayOf(0.0, 0.0)
    private var DL_position : Array<String> = arrayOf("","","")
    private var DL_first : Boolean = true
    private var DL_PF_on : Boolean = false
    private var state_transition : Boolean = false
    private var DLgyro : Double  = 0.0
    private lateinit var particleFilter_DL : ParticleFilter
    private var step_direction_PF_DL_x = DoubleArray(6)
    private var step_direction_PF_DL_y = DoubleArray(6)
    private var angle_PF_DL = 0.0
    private var distance_Pf_DL = 0.0
    private var step_direction_PF_DL_count = 0


    //수민
    private var getInZone : Boolean = false
    private var sendSensorToMovingFloor : Boolean = false
    private var checkMovingFloor : Boolean = false
    var floorchange : Boolean = false
    var buildingInfoResult : BuilInfo? = null
    var infoType : String = ""
    var startfloor : Int = 0  // 출발 층 임의로 제시
    lateinit var whereUserAfterMove : Array<Double>
    lateinit var finalarrival : Array<Double>
//    private var movingFloor : MovingFloor = MovingFloor()
//    private var acontext : Context = AppforContext.myappContext()

    private fun sensorReady(event: SensorEvent) : Boolean {
        if (isSensorStabled) return true

        when(event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                accXMovingAverage.newData(event.values[0].toDouble())
                accYMovingAverage.newData(event.values[1].toDouble())
                accZMovingAverage.newData(event.values[2].toDouble())

                accStableCount += if (accXMovingAverage.getAvg() in -0.3..0.3 && accYMovingAverage.getAvg() in -0.3..0.3 && accZMovingAverage.getAvg() in -0.3..0.3) -1 else 1

                accStableCount = if (accStableCount > 50) 50 else accStableCount
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                magStableCount--
            }
        }

        isSensorStabled = accStableCount<0 && magStableCount<=0
        return isSensorStabled
    }

    fun sensorChanged(event: SensorEvent?, myUUID : String) : Array<String> {
        if ((event?:false).let {sensorReady(event!!)}) {
            when(event!!.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    accMatrix = event.values.clone()
                }
                Sensor.TYPE_LIGHT -> {
                    heroPDR.setLight(event.values[0])
                    Log.d("light::", "${event.values[0]}")
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    magMatrix = event.values.clone()
                    caliVector = vectorCalibration.calibrate(accMatrix, magMatrix, gyro_value_map_collection)
                    // 앱 켜자마자 바로 instant 시작
                    if (first_sampling && caliVector[0]!=0.0 && caliVector[1]!=0.0 && caliVector[2]!=0.0 ) {
//                        mag_cnt += 1
                        magneticQueue.add(caliVector + caliVector + arrayOf(0.0, gyro_value_map_collection.toDouble(), gyro_value_reset_direction.toDouble()))
                        first_sampling = false
                        gps.find_range(lat, long)
                        first_range = gps.first_range
                        // 승규 수정 //
//                        instantLocalization.init_range = gps.first_range
                        ILEngine.wifi_range = wifi_range
                    }

                    var caliVector_Q = vectorCalibration.calibrate_quaternion(magMatrix)
                    caliX_Q = caliVector_Q[0]
                    caliY_Q = caliVector_Q[1]
                    caliZ_Q = caliVector_Q[2]

//                    if(sendSensorToMovingFloor) { ///수민
//                        movingFloor.getMag(caliVector[0] ,caliVector[1],caliVector[2])
//                    }
                }
                Sensor.TYPE_GYROSCOPE -> {
                    getGyroscope.calc_gyro_value(event)
                    gyro_value_map_collection = getGyroscope.from_map_collection()
                    gyro_value_reset_direction = getGyroscope.from_reset_direction()
                    gyro_constant_from_app_start_to_map_collection = getGyroscope.from_app_start_to_map_collection()

                    heroPDR.setDirection(gyro_value_map_collection.toDouble())
                }
                Sensor.TYPE_ROTATION_VECTOR -> {
                    heroPDR.setQuaternion(event.values.clone())
                }
                Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                    var roVecMatrix = event.values.clone()
                    if (roVecMatrix.isNotEmpty()) {
                        vectorCalibration.setQuaternion(roVecMatrix)
                    }
                }
                Sensor.TYPE_PRESSURE -> {
//                    if(sendSensorToMovingFloor) { ///수민
//                        movingFloor.getPressure(event.values[0].toDouble())
//                    }
                }
                Sensor.TYPE_LINEAR_ACCELERATION -> {
                    accXMovingAverage.newData(event.values[0].toDouble())
                    accYMovingAverage.newData(event.values[1].toDouble())
                    accZMovingAverage.newData(event.values[2].toDouble())

//                    if(sendSensorToMovingFloor) {///수민
//                        movingFloor.getLinerAccZ(event.values[2].toDouble())
//                    }

                    ///////////////pdrResult의 내부 구조////////////////////////
//                    data class PDR(
//                      val devicePosture : Int,
//                      val stepType : Int,
//                      val stepLength : Double,
//                      val direction : Double,
//                      val totalStepCount : Int
//                    )
                    /////////////////////////////////////////////////////////

                    if (heroPDR.isStep(arrayOf(accXMovingAverage.getAvg(), accYMovingAverage.getAvg(), accZMovingAverage.getAvg()), caliVector)) {
                        pdrResult = heroPDR.getStatus()
                        devicePosture = pdrResult.devicePosture
                        stepType = pdrResult.stepType
                        stepCount = pdrResult.totalStepCount
                        Log.d("stepCount", stepCount.toString())
                        userDir = pdrResult.direction
                        caliVector_AON = vectorCalibration.calibrate(accMatrix, magMatrix, gyro_value_reset_direction)

                        gps.find_range(lat, long)
                        first_range = gps.first_range
                        // 승규 수정 //
                        ILEngine.wifi_range = wifi_range
//                        instantLocalization.init_range = gps.first_range
                        ILEngine.deviceposture = pdrResult.devicePosture

                        if (!ILEngine.is_aon_mode()) {
                            magneticQueue.add(caliVector + caliVector + arrayOf(pdrResult.stepLength, gyro_value_map_collection.toDouble(), gyro_value_reset_direction.toDouble()))
                        } else {


                            magneticQueue.add(caliVector_AON + caliVector + arrayOf(pdrResult.stepLength, gyro_value_map_collection.toDouble(), gyro_value_reset_direction.toDouble()))


                            /**********************************수민*******************************************/
//                            buildingInfoResult = buildingInfo.search(nowPosition[0], nowPosition[1])
//
//                            if(buildingInfoResult != null && !getInZone) { // 엘베 또는 에스컬레이터 공간에 들어오면 시작
//
//                                infoType = buildingInfoResult!!.type // moving type 저장 // infoType == "EL1", "EL2","Es1"
//                                whereUserAfterMove = buildingInfoResult?.arrival!! //도착 좌표 미리 받아놓기.
//
//                                movingFloor.getCondition(infoType,startfloor) // 기준파일 읽게 하기
//                                sendSensorToMovingFloor = true //센서 보내기
//                                checkMovingFloor = true
//                                getInZone = true
//                            }
//
//                            if(checkMovingFloor) {
//                                // html 전환하기
//                                if (movingFloor.elvhtml || movingFloor.escalhtml) {
//                                    floorchange = true
//                                    movingFloor.elvhtml = false
//                                    movingFloor.escalhtml = false
//                                }
//
//                                // 결과 받아오기
//                                if (movingFloor.elvresult || movingFloor.escalresult) {
//                                    startfloor = movingFloor.getFloor()
//                                    getMovingResult(startfloor, whereUserAfterMove[0], whereUserAfterMove[1])// 함수 호출
//
//                                    // TODO : 층 변환시, 변환된 자기장 맵으로 instant localization 재시작하게 해야됨. 지금은 임시.
//                                    // 엘베 결과 나오면, 일단 AON 끄고, particle filter만 사용하도록..
//                                    always_on_mode = false
//                                    instant_thread_on = false
//
//                                    movingFloor.escalresult = false
//                                    movingFloor.elvresult = false
//                                    checkMovingFloor = false
//                                    getInZone = false // 이제 구역에 다시 들어오면 모듈이 다시 작동된다.
//                                }
//                            }
                            /***********************************************************************************/

                        }

                        ////////////DL
                        if (myUUID != "0") {
//                            DL_position = lstmServer.dataSaveAndSend(caliY_Q, -1*caliX_Q, caliZ_Q, gyro_constant_from_app_start_to_map_collection, devicePosture, ILResult, myUUID)
//
//                            Log.d("result56a", "---------------------------------------${DL_position[0]}    ${DL_position[1]}")
//                            if (lstmServer.sendcomplete) {
//                                lstmServer.sendcomplete = false
//                                ////승규 수정////
//                                if(ILEngine.hand_to_other){
//                                    ILEngine.deeplrn_pos_array.add(arrayOf(DL_position[0].toDouble(), DL_position[1].toDouble()))
//                                }
//                            }
//                            ///////////DL+PF
//                            if ( devicePosture!= ON_HAND && DL_position[0].toDouble() != 0.0){
//                                state_transition = true
//                                DL_first = false
//                            }
//                            if ( devicePosture== ON_HAND && DL_position[0].toDouble() != 0.0) {
//                                if ((ILResult["status_code"] == 200.0f ||ILResult["status_code"] == 201.0f||ILResult["status_code"] == 202.0f) && !DL_PF_on) {
//                                    particleFilter_DL = ParticleFilter(map, 100, DL_position[0].toDouble().toInt(), DL_position[1].toDouble().toInt(), 20)
//                                    DL_PF_on = true
//                                }
//                                if (state_transition){
//                                    particleFilter_DL = ParticleFilter(map, 100, DL_position[0].toDouble().toInt(), DL_position[1].toDouble().toInt(), 20)
//                                    state_transition=false
//                                    DL_PF_on = true
//                                }
//                                if (DL_PF_on) {
//                                    if (DL_first) {
//                                        DL_PF_position = particleFilter_DL.step(caliVector, round((gyro_value_map_collection.toDouble() + 360) % 360), 0.0)
//                                        DL_first = false
//                                    } else {
//                                        DL_PF_position = particleFilter_DL.step(arrayOf(caliVector[0], caliVector[1], caliVector[2]), round((gyro_value_map_collection.toDouble() + DLgyro + 360) % 360), pdrResult.stepLength)
//                                    }
//                                    var distance_Pf_DL = sqrt((DL_PF_position[0] - DL_position[0].toDouble()).pow(2) + (DL_PF_position[1] - DL_position[1].toDouble()).pow(2))
//                                    //angle
//                                    angle_PF_DL = Math.toDegrees(atan2(step_direction_PF_DL_x[5]-step_direction_PF_DL_x[0],step_direction_PF_DL_y[5]-step_direction_PF_DL_y[0])) - (gyro_value_map_collection.toDouble() + DLgyro )
//                                    step_direction_PF_DL_count++
//                                    if (step_direction_PF_DL_count>6){
//                                        step_direction_PF_DL_count = 6
//                                    }else{
//                                        angle_PF_DL = 60.0
//                                    }
//                                    if (angle_PF_DL>180) angle_PF_DL -= 360
//                                    else if (angle_PF_DL<-180) angle_PF_DL += 360
//                                    if ( DL_position[2].toBoolean() && ((abs(angle_PF_DL)>60 && distance_Pf_DL > 27) ||(distance_Pf_DL > 80) )) {
//                                        step_direction_PF_DL_count = 0
//                                        particleFilter_DL = ParticleFilter(
//                                            map,
//                                            100,
//                                            DL_position[0].toDouble().toInt(),
//                                            DL_position[1].toDouble().toInt(),
//                                            20
//                                        )
//                                        DL_PF_position = particleFilter_DL.step(
//                                            arrayOf(
//                                                caliY_Q,
//                                                -1*caliX_Q,
//                                                caliZ_Q/*
//                                                caliVector[0],
//                                                caliVector[1],
//                                                caliVector[2]*/
//                                            ),
//                                            round((DL_position[3].toDouble() + 360) % 360),
//                                            0.0
//                                        )
//                                        DLgyro =
//                                            DL_position[3].toDouble() - (gyro_value_map_collection.toDouble())
//                                        Log.d("DLcheck", "reconverge")
//                                    }
//
//
//                                    //////to find direction difference
//                                    for (i in 0 until 5) {
//                                        step_direction_PF_DL_x[i]=step_direction_PF_DL_x[i+1]
//                                        step_direction_PF_DL_y[i]=step_direction_PF_DL_y[i+1]
//                                    }
//                                    step_direction_PF_DL_x[5] = DL_PF_position[0]
//                                    step_direction_PF_DL_y[5] = DL_PF_position[1]
//                                    ///////////////////////
//                                }
//                            }
                        }
                    } else {
                        devicePosture = heroPDR.getPosture()
                        stepType = heroPDR.getStepType()
                    }
                }
            }
        } else {
            return arrayOf("The sensors is not ready yet", "The sensors is not ready yet", "-1", "The sensors is not ready yet", "The sensors is not ready yet")
        }
        returnGyro = gyro_value_map_collection.toString()
        return arrayOf(returnGyro, returnIns, stepCount.toString(), returnX, returnY,DL_position[0],DL_position[1],DL_PF_position[0].toString(),DL_PF_position[1].toString())
    }

    private fun resetParticleFilter(map : MagneticFieldMap, x : Double, y : Double) {
        particleFilter = ParticleFilter(map, 100, round(x).toInt(), round(y).toInt(), 10)
        particleOn = true
    }



    fun setGPS(gpsArray : ArrayList<Double>) {
        lat = gpsArray[0]
        long = gpsArray[1]
    }

    fun serverReset(myUUID : String) {
        lstmServer.reset(myUUID)
    }

    fun getPose(): String {
        return poseTypes[devicePosture]
    }

    fun getType() : String {
        return stepTypes[stepType]
    }

    fun getAccZ() : Float {
        return heroPDR.transformedAccZ
    }

    private fun getMovingResult(finalFloor: Int, arrivalX: Double, arrivalY: Double) {
//        startfloor = finalFloor
//        finalarrival = (arrayOf(arrivalX,arrivalY))
//
//        //여기서 자기장맵이 바꿔주는 것이 맞는 것인가?일단 particle에 쓰이는 맵만 전환함.
//        if((infoType == "EL1") || (infoType == "EL2")) { //1106
//            map = MagneticFieldMap(acontext.resources.openRawResource(R.raw.hana1floor_first))
//            resetParticleFilter(map, finalarrival[0], finalarrival[1])
////            ParticleFilter(map, 100, finalarrival[0].toInt(), finalarrival[1].toInt(), 1)
//        } else if((infoType == "EL3") || (infoType == "EL4")) {
//            map = MagneticFieldMap(acontext.resources.openRawResource(R.raw.hana1floor_second))
////            ParticleFilter(map, 100, finalarrival[0].toInt(), finalarrival[1].toInt(), 1)
//            resetParticleFilter(map, finalarrival[0], finalarrival[1])
//        }
//

    }

    inner class InstantLocalizationThread: Thread() {
        override fun run() {
            while(instant_thread_on && !this.isInterrupted) {
                if ((magneticQueue.size > 0) && (wifi_range[0] != -100)) {
                    Log.d("mqueue", "OK")
                    var inputData: Array<Double>
                    try {
                        inputData = magneticQueue.removeAt(0)

                    } catch (e: java.lang.Exception) {
                        continue
                    }
                    ILEngine.wifi_range = wifi_range
                    ILResult = ILEngine.getLocation(inputData[0].toFloat(), inputData[1].toFloat(), inputData[2].toFloat(), inputData[3].toFloat(), inputData[4].toFloat(), inputData[5].toFloat(), inputData[6].toFloat(), inputData[7].toFloat(), inputData[8].toFloat(), state = devicePosture)
                    returnX = ILResult["pos_x"].toString()
                    returnY = ILResult["pos_y"].toString()
                    when (ILResult["status_code"]) {
                        100.0f -> returnIns = "아직 수렴 안됨"
                        101.0f -> returnIns = "방향만 수렴"
                        200.0f -> returnIns = "완전 수렴"
                        201.0f -> returnIns = "완전 수렴" // instant는 수렴. aon은 진행중.
                        202.0f -> returnIns = "완전 수렴" // aon 수렴.
                        400.0f -> returnIns = "에러! 수렴 완전 실패!"
                    }

                    if ((ILResult["status_code"] == 200.0f) || (ILResult["status_code"] == 202.0f)) {
                        getGyroscope.setGyroCalivalue(ILResult["gyro_from_map"]!!)
                        getGyroscope.gyro_reset()
                    }
                }
            }
        }
    }

}