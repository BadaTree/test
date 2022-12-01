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
    private lateinit var particleFilter: ParticleFilter

    // 인스턴트 관련 // (원준)
    private var first_sampling : Boolean = true
    private var instantLocalization : InstantLocalization

    var wifi_range = arrayListOf(-100, -100, -100, -100)  // (원준) 승규로부터 넘어와야될 wifi_range. 이 변수는 인스턴트 엔진의 getLocation 메소드에 인자로 들어가게 됨.
    private var ILResult : MutableMap<String, Float> = mutableMapOf("status_code" to -1.0f, "gyro_from_map" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)
    private var unqWifi: Int = 0
    var pos_pos : MutableMap<String, ArrayList<Int>> = mutableMapOf("pos_x_list" to arrayListOf(0), "pos_y_list" to arrayListOf(0))

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
                        Log.d("range", "aosdjasoidj" + wifi_range.joinToString("\t"))
                        //////WiFi Engine
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
                        CoroutineScope(Dispatchers.Default).async {
                            var (ILResult_, unqWifi_) = async { wifiengine.getLocation(wifidata, temp_steplength, gyro_value_map_collection, gyro_value_reset_direction, PFResult) }.await()
                            ILResult = ILResult_
                            unqWifi = unqWifi_
                            // 11.22 bada check 2 :
                            Log.d("Indoor_ILResult", ILResult["status_code"].toString())
                            Log.d("Indoor_unqWifi", unqWifi.toString())
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
        main_step = stepCount.toString()

        return arrayOf(returnGyro, returnState, returnIns, stepCount.toString(), returnX, returnY, unqWifi.toString())
    }

    fun getPose(): String {
        return poseTypes[devicePosture]
    }
    fun getType() : String {
        return wifiengine.getParticleNum().toString()
    }
}