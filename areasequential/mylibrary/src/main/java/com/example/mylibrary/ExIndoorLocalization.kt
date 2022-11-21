package com.example.mylibrary

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.util.Log
import com.example.heropdr.HeroPDR
import com.example.heropdr.MovingAverage
import com.example.heropdr.PDR
import com.example.mylibrary.filters.ParticleFilter
import com.example.mylibrary.instant.InstantLocalization
import com.example.mylibrary.maps.ResourceDataManager
import com.example.mylibrary.sensors.GetGyroscope
import com.example.mylibrary.sensors.VectorCalibration
import com.example.mylibrary.wifiengine.WiFiMap_RSSI_Sequential
import com.example.mylibrary.wifiengine.WifiMap_RSSI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import java.io.InputStream
import kotlin.math.round

class ExIndoorLocalization {

    constructor(magneticStream : InputStream?, magneticStreamForInstant : InputStream?, wifiStreamTotal : InputStream?, wifiStreamRSSI : InputStream?, wifiStreamUnq : InputStream?) {
        resourceDataManager = ResourceDataManager(magneticStream, magneticStreamForInstant, wifiStreamTotal, wifiStreamRSSI, wifiStreamUnq)
        instantLocalization = InstantLocalization(resourceDataManager.magneticFieldMap, resourceDataManager.instantMap)
        wifiengine = WiFiMap_RSSI_Sequential(resourceDataManager.wifiDataMap, resourceDataManager.magneticFieldMap)
    }
    fun setClass(magneticStream : InputStream?, magneticStreamForInstant : InputStream?, wifiStreamTotal : InputStream?, wifiStreamRSSI : InputStream?, wifiStreamUnq : InputStream?){
        resourceDataManager = ResourceDataManager(magneticStream, magneticStreamForInstant, wifiStreamTotal, wifiStreamRSSI, wifiStreamUnq)
        instantLocalization = InstantLocalization(resourceDataManager.magneticFieldMap, resourceDataManager.instantMap)
        wifiengine = WiFiMap_RSSI_Sequential(resourceDataManager.wifiDataMap, resourceDataManager.magneticFieldMap)
    }
    var rangecheck = arrayListOf(0.0f, 0.0f, 0.0f, 0.0f)


    private var isSensorStabled : Boolean = false
    private var magStableCount : Int = 100
    private var accStableCount : Int = 50
    private var returnGyro : String = "0.0"
    private var returnState : String = "not support"
    private var returnIns : String = "not support"
    private var returnX : String = "unknown"
    private var returnY : String = "unknown"
    private var resourceDataManager : ResourceDataManager

    private var magMatrix = FloatArray(3)
    private var accMatrix = FloatArray(3)

    private val heroPDR : HeroPDR = HeroPDR()
    private lateinit var pdrResult : PDR
    private val accXMovingAverage : MovingAverage = MovingAverage(10)
    private val accYMovingAverage : MovingAverage = MovingAverage(10)
    private val accZMovingAverage : MovingAverage = MovingAverage(10)
    private var userDir : Double = 0.0
    private var stepCount : Int = 0
    private var devicePosture : Int = 0
    private var stepType : Int = 0
    private val poseTypes = arrayOf("On Hand", "In Pocket", "Hand Swing")
    private val stepTypes = arrayOf("normal", "fast", "slow", "prowl", "non")
    internal var particleOn : Boolean = false
    internal var  particle_num = 0
    ///////////////pdrResult의 내부 구조////////////////////////
//                    data class PDR(
//                      val devicePosture : Int,
//                      val stepType : Int,
//                      val stepLength : Double,
//                      val direction : Double,
//                      val totalStepCount : Int
//                    )
    /////////////////////////////////////////////////////////

    // 자이로스코프 관련 // (원준)
    private val getGyroscope by lazy {
        GetGyroscope()
    }
    private var gyro_constant_from_app_start_to_map_collection : Float = 0f
    private var gyro_value_map_collection : Float = 0f
    private var gyro_value_reset_direction : Float = 0f
    /*
        자이로 값 설명
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
                           Always On Instant를 위해 사용됨. 원준만 신경쓰면 되는 값.
    */



    // Vector Calibration 관련 // (원준)
    private val vectorCalibration = VectorCalibration()
    private var caliVector: Array<Double> = arrayOf()
    private var caliVector_AON: Array<Double> = arrayOf()
    /*
        캘리브레이션 벡터 값 설명
        1. caliVector
                    보정 기준 : 자기장 벡터를 '맵 수집 방향'으로 보정
                    특징 : 측정되는 자기장 벡터를 모두 '맵 수집 방향' 으로 보정.
                           일반적으로 알고 있는 그 자기장 벡터임. 모든 사람들이 이 벡터를 사용하면 됨.

        2. caliVector_AON
                    보정 기준 : 자기장 벡터를 'Always On이 처음 시작한 그 순간의 방향'으로 보정
                    특징 : 측정되는 자기장 벡터를 'Always On이 처음 시작한 그 순간의 방향'으로 보정.
                           절대적인 이동 방향을 추정해내기 위해, 이 벡터를 사용함. 원준이만 신경쓰면 됨.
    */


    // 파티클 필터 관련 // (원준)
    private var PFResult : Array<Double> = arrayOf(-1.0, -1.0)
    private lateinit var particleFilter: ParticleFilter

    // 인스턴트 관련 // (원준)
    private var first_sampling : Boolean = true
    private var instantLocalization : InstantLocalization

    var wifi_range = arrayListOf(-100, -100, -100, -100)  // (원준) 승규로부터 넘어와야될 wifi_range. 이 변수는 인스턴트 엔진의 getLocation 메소드에 인자로 들어가게 됨.
    private var ILResult : MutableMap<String, Float> = mutableMapOf("status_code" to -1.0f, "gyro_from_map" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)
    var pos_pos : MutableMap<String, ArrayList<Int>> = mutableMapOf("pos_x_list" to arrayListOf(0), "pos_y_list" to arrayListOf(0))
    /*
    ILResult :
        <Key & Value 설명>
        "pos_x" : IL 혹은 AON의 추정 좌표 x 가 들어있음 (수렴하지 않았으면 "pos_x" 는 -1.0f)
        "pos_y" : IL 혹은 AON의 추정 좌표 x 가 들어있음 (수렴하지 않았으면 "pos_y" 는 -1.0f)
        "gyro_from_map" : IL 혹은 AON이 추정한 절대적인 이동 방향. --> 이 값은 getGyroscope.setGyroCalivalue() 메서드에 인자로 들어가야함. (sensorChanged 메소드 본문 참고.)
        "status_code" : IL 혹은 AON의 상태 정보
                        100.0f -> IL 진행 중. 아직 수렴 안됨
                        101.0f -> IL 진행 중. 방향만 수렴
                        200.0f -> IL 완료. 완전 수렴. --> 해당 status일 때, 파티클필터가 바로 "pos_x", "pos_y", "gyro_from_map" 가져다가 initialize 해주면 됨.
                        201.0f -> AON 진행 중. (IL 완전 수렴 이후의 상태임.)
                        202.0f -> AON 수렴 완료. (IL 완전 수렴 이후의 상태임.) --> 해당 status일 때, 파티클필터가 바로 "pos_x", "pos_y", "gyro_from_map" 가져다가 initialize 해주면 됨.
                        400.0f -> IL 혹은 AON 에러. 수렴하지 못함.
     */

    /*
    changedFloor_and_resetInstatLocalization(map_vector : MagneticFieldMap, map_for_instant_hand : MagneticFieldMap, gyro: Float=-1.0f)
          사용형태 : changedFloor_and_resetInstatLocalization(new_map_vector, new_map_for_instant_hand, gyro)
          입력 : 새로 바뀐 층의 맵, 새로 바뀐 층의 인스턴트 전용 맵, 층이 바뀌고 난 이후의 자이로 (자이로는 안넣어줘도 무방)
          출력 : None
          특징 : 엘베타고 층 도착하자마자 해당 메소드 호출 해야됨.
                 엘베 도착 후의 메소드 호출 순서가 대략 이런식이어야 됨.
                        엘베 도착 -> changedFloor_and_resetInstatLocalization() 호출 -> 파티클 필터 리셋
                 이렇게 하고서 파티클 필터는 계속 점을 찍고 있어줘야 되고,
                 getLocation() 메소드에 파티클 필터 좌표값들을 넣어주기만 한다면, 자동으로 AON 시작함.
     */

    var wifiengine : WiFiMap_RSSI_Sequential
    var wifidata = ""
    var wifichange = false
    var wifidataready = false
    var wifiengineready = false



    private fun isReadyLocalization(event: SensorEvent) : Boolean {
        if (isSensorStabled) {
            return true
        }


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

    fun sensorChanged(event: SensorEvent?) : Array<String> {
        if ((event ?: false).let { isReadyLocalization(event!!) }) {
            when (event!!.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    accMatrix = event.values.clone()
                }
                Sensor.TYPE_LIGHT -> {
                    heroPDR.setLight(event.values[0])
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    magMatrix = event.values.clone()
                    caliVector = vectorCalibration.calibrate(accMatrix, magMatrix, gyro_value_map_collection)
                    // 폰 켜지자마자 바로 instant 실행되도록 함
                    if (first_sampling && caliVector[0]!=0.0 && caliVector[1]!=0.0 && caliVector[2]!=0.0 && (wifidata != "")) { // 해당 if문은 꼭 있어야됨. 자기장 센서 안정화가 안될 때가 간혹있어서, 여기서 한번더 확실하게 안정화해주는 것임.
                        //////WiFi Engine
                        Log.d("rangeval12", "asdasdasd123"+ wifi_range.joinToString("\t"))
                        wifiengine.getLocation(wifidata, 0.0, gyro_value_map_collection, gyro_value_reset_direction, PFResult)
                        first_sampling = false
                    }

                    //자기장 쓸 경우 자기장 값도 MovingFloor로 넘겨줘야 함. 지금은 자기장 안쓰니 보류.


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
                Sensor.TYPE_LINEAR_ACCELERATION -> {
                    accXMovingAverage.newData(event.values[0].toDouble())
                    accYMovingAverage.newData(event.values[1].toDouble())
                    accZMovingAverage.newData(event.values[2].toDouble())

                    if (heroPDR.isStep(arrayOf(accXMovingAverage.getAvg(), accYMovingAverage.getAvg(), accZMovingAverage.getAvg()), caliVector)) {
                        pdrResult = heroPDR.getStatus()
                        devicePosture = 0//pdrResult.devicePosture
                        stepType = pdrResult.stepType
                        stepCount = pdrResult.totalStepCount
                        userDir = pdrResult.direction

                        ////WiFI Engine

                        caliVector_AON = vectorCalibration.calibrate(accMatrix, magMatrix, gyro_value_reset_direction)

                        //////Instant Localization
                        var temp_steplength = 0.7
                        //pdrResult.stepLength

                        // #### ILResult : wifiengine.getLocation
                        CoroutineScope(Dispatchers.Default).async {
                            ILResult = async { wifiengine.getLocation(wifidata, temp_steplength, gyro_value_map_collection, gyro_value_reset_direction, PFResult) }.await()
                            rangecheck = wifiengine.range_check
                        }
//                        ILResult = wifiengine.getLocation(wifidata, temp_steplength, gyro_value_map_collection, gyro_value_reset_direction, PFResult)
//                        if((ILResult["status_code"] == 101.0f)){
//                            pos_pos["pos_x_list"] = wifiengine.final_posx_list
//                            pos_pos["pos_y_list"] = wifiengine.final_posy_list
//                            returnState = "방향 수렴"
//                        }
                        if((ILResult["status_code"]!! == 400.0f)){
//                            getGyroscope.gyro_reset()
                            returnState = "완전 실패"
                        }
                        else if ((ILResult["status_code"] == 200.0f) || (ILResult["status_code"] == 202.0f)) {
//                            particleFilter = ParticleFilter(resourceDataManager.magneticFieldMap, 100, round(ILResult["pos_x"]!!).toInt(), round(ILResult["pos_y"]!!).toInt(), 10)
//                            PFResult = arrayOf(ILResult["pos_x"]!!.toDouble(), ILResult["pos_y"]!!.toDouble())
                            getGyroscope.setGyroCalivalue(ILResult["gyro_from_map"]!!)
                            getGyroscope.gyro_reset()
//                            PFResult = particleFilter.step(caliVector, ILResult["gyro_from_map"]!!.toDouble(), pdrResult.stepLength * 1)

                            particleOn = true
                            returnState = "완전 수렴"
                        }
//                        else if (!particleOn && (ILResult["status_code"]!! == 400.0f)){
//                            getGyroscope.gyro_reset()
//                        }
                        else if (particleOn && (ILResult["status_code"]!! > 200.0f)) {
//                            PFResult = particleFilter.step(caliVector, gyro_value_map_collection.toDouble(), pdrResult.stepLength)
//                            if(ILResult["status_code"]!! == 400.0f){
//                                getGyroscope.gyro_reset()
//                            }
                        }
                    } else {
                        devicePosture = 0//heroPDR.getPosture()
                        stepType = heroPDR.getStepType()
                    }
                }
            }
        } else {
            return arrayOf("The sensors is not ready yet", "The sensors is not ready yet", "The sensors is not ready yet", "-1", "The sensors is not ready yet", "The sensors is not ready yet")
        }
        returnGyro = gyro_value_map_collection.toString()
        returnIns = ILResult["status_code"].toString()
        returnX = PFResult[0].toString()
        returnY = PFResult[1].toString()
        particle_num = wifiengine.particle_num
        return arrayOf(returnGyro, returnState, returnIns, stepCount.toString(), returnX, returnY)
    }

    fun getPose(): String {
        return poseTypes[devicePosture]
    }
    fun getType() : String {
        return particle_num.toString()
    }
}