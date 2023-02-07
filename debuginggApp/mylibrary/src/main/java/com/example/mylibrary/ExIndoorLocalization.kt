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
import com.example.mylibrary.wifiengine.WifimapRssiSequential
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import java.io.InputStream
import java.util.concurrent.TimeUnit

class ExIndoorLocalization {

    constructor(magneticStream : InputStream?, magneticStreamForInstant : InputStream?, wifiStreamTotal : InputStream?, wifiStreamRSSI : InputStream?, wifiStreamUnq : InputStream?, testData : InputStream?) {
        resourceDataManager = ResourceDataManager(magneticStream, magneticStreamForInstant, wifiStreamTotal, wifiStreamRSSI, wifiStreamUnq)
        instantLocalization = InstantLocalization(resourceDataManager.magneticFieldMap, resourceDataManager.instantMap)
        wifiengine = WifimapRssiSequential(resourceDataManager.wifiDataMap, resourceDataManager.magneticFieldMap)
    }
    fun setClass(magneticStream : InputStream?, magneticStreamForInstant : InputStream?, wifiStreamTotal : InputStream?, wifiStreamRSSI : InputStream?, wifiStreamUnq : InputStream?, testData: InputStream?){
        resourceDataManager = ResourceDataManager(magneticStream, magneticStreamForInstant, wifiStreamTotal, wifiStreamRSSI, wifiStreamUnq)
        instantLocalization = InstantLocalization(resourceDataManager.magneticFieldMap, resourceDataManager.instantMap)
        wifiengine = WifimapRssiSequential(resourceDataManager.wifiDataMap, resourceDataManager.magneticFieldMap)
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
    internal var particleOn : Boolean = false
    internal var main_step : String = ""

    // 자이로스코프 관련 // (원준)
    private val getGyroscope by lazy {
        GetGyroscope()
    }
    private var gyro_constant_from_app_start_to_map_collection : Float = 0f
    private var gyro_value_map_collection : Float = 0f
    private var gyro_value_reset_direction : Float = 0f

    // Vector Calibration 관련 // (원준)
    private val vectorCalibration = VectorCalibration()
    private var caliVector: Array<Double> = arrayOf()
    private var caliVector_AON: Array<Double> = arrayOf()


    // 파티클 필터 관련 // (원준)
    private var PFResult : Array<Double> = arrayOf(-1.0, -1.0)

    // 인스턴트 관련 // (원준)
    private var first_sampling : Boolean = true
    private var instantLocalization : InstantLocalization

    var wifi_range = arrayListOf(-100, -100, -100, -100)  // (원준) 승규로부터 넘어와야될 wifi_range. 이 변수는 인스턴트 엔진의 getLocation 메소드에 인자로 들어가게 됨.
    private var ILResult : MutableMap<String, Float> = mutableMapOf("status_code" to -1.0f, "gyro_from_map" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)
    private var unqWifi: Int = 0
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

    var wifiengine : WifimapRssiSequential
    var wifidata = ""
    var wifichange = false


    fun sensorChanged() : Array<String> {

        TimeUnit.SECONDS.sleep(3)

        wifiengine.getLocation(wifidata, 0.0)

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
        CoroutineScope(Dispatchers.Default).async {
            var (ILResult_,unqWifi_)  = async { wifiengine.getLocation(wifidata, temp_steplength) }.await()
            ILResult = ILResult_
            unqWifi = unqWifi_
            rangecheck = wifiengine.ansRange
        }
        if((ILResult["status_code"]!! == 400.0f)){
            returnState = "완전 실패"
        }
        else if ((ILResult["status_code"] == 200.0f) || (ILResult["status_code"] == 202.0f)) {
            getGyroscope.setGyroCalivalue(ILResult["gyro_from_map"]!!)
            getGyroscope.gyro_reset()

            particleOn = true
            returnState = "완전 수렴"
        }
        else if (particleOn && (ILResult["status_code"]!! > 200.0f)) {
        }


        returnGyro = gyro_value_map_collection.toString()
        returnIns = ILResult["status_code"].toString()
        returnX = PFResult[0].toString()
        returnY = PFResult[1].toString()
        main_step = stepCount.toString()
        // 11.22 bada check 1 : not support라고 뜸
        return arrayOf(returnGyro, returnState, returnIns, stepCount.toString(), returnX, returnY, unqWifi.toString())
    }
}