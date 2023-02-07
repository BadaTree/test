package com.example.mylibrary

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.util.Log
import com.example.heropdr.HeroPDR
import com.example.heropdr.MovingAverage
import com.example.heropdr.PDR
import com.example.mylibrary.filters.ParticleFilter
import com.example.mylibrary.maps.ResourceDataManager
import com.example.mylibrary.sensors.VectorCalibration
import com.example.mylibrary.wifiengine.WifimapRssiSequential
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import java.io.InputStream

class ExIndoorLocalization {

    constructor(magneticStream : InputStream?, magneticStreamForInstant : InputStream?, wifiStreamTotal : InputStream?, wifiStreamRSSI : InputStream?, wifiStreamUnq : InputStream?) {
        resourceDataManager = ResourceDataManager(magneticStream, magneticStreamForInstant, wifiStreamTotal, wifiStreamRSSI, wifiStreamUnq)
        wifiengine = WifimapRssiSequential(resourceDataManager.wifiDataMap, resourceDataManager.magneticFieldMap)
    }
    fun setClass(magneticStream : InputStream?, magneticStreamForInstant : InputStream?, wifiStreamTotal : InputStream?, wifiStreamRSSI : InputStream?, wifiStreamUnq : InputStream?){
        resourceDataManager = ResourceDataManager(magneticStream, magneticStreamForInstant, wifiStreamTotal, wifiStreamRSSI, wifiStreamUnq)
        wifiengine = WifimapRssiSequential(resourceDataManager.wifiDataMap, resourceDataManager.magneticFieldMap)
    }
    var rangecheck = arrayListOf(0.0f, 0.0f, 0.0f, 0.0f)


    private var isSensorStabled : Boolean = false
    private var magStableCount : Int = 100
    private var accStableCount : Int = 50
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
    internal var particleOn : Boolean = false
    internal var main_step : String = ""

    // Vector Calibration 관련 // (원준)
    private val vectorCalibration = VectorCalibration()
    private var caliVector: Array<Double> = arrayOf()

    // 파티클 필터 관련 // (원준)
    private var PFResult : Array<Double> = arrayOf(-1.0, -1.0)

    private var ILResult : MutableMap<String, Float> = mutableMapOf("status_code" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)
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

    var wifiengine : WifimapRssiSequential
    var wifidata = ""
    var wifichange = false



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


                        CoroutineScope(Dispatchers.Default).async {
                            ILResult = async { wifiengine.getlocationWF(wifidata)}.await()
                            rangecheck = wifiengine.ansRange
                            Log.d("ILResult", ILResult.toString())
                        }
                        if((ILResult["status_code"]!! == 400.0f)){
                            returnState = "완전 실패"
                        }
                        else if ((ILResult["status_code"] == 200.0f) || (ILResult["status_code"] == 202.0f)) {
                            particleOn = true
                            returnState = "완전 수렴"
                        }
                    } else {
                        devicePosture = 0//heroPDR.getPosture()
                        stepType = heroPDR.getStepType()
                    }
                }
            }
        } else {
            return arrayOf("The sensors is not ready yet", "The sensors is not ready yet", "The sensors is not ready yet", "-1", "The sensors is not ready yet", "The sensors is not ready yet", "0")
        }
        returnIns = ILResult["status_code"].toString()
        returnX = PFResult[0].toString()
        returnY = PFResult[1].toString()
        main_step = stepCount.toString()

        return arrayOf(returnState, returnIns, stepCount.toString(), returnX, returnY, unqWifi.toString())
    }
}