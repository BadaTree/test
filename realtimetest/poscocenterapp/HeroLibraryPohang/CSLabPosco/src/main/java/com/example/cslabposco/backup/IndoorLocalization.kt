package com.example.cslabposco

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import com.kircherelectronics.fsensor.filter.gyroscope.OrientationGyroscope
import org.apache.commons.math3.complex.Quaternion
import java.io.InputStream
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.*

/**
 * 자기장 기반 실내측위 결과를 얻기 위한 클래스
 *
 * SensorEventListener 를 implementation 한 후, override 된 onSensorChanged 메소드 내부에서 본 클래스의 sensorChanged 메소드를 지속적으로 호출
 *
 * Required Permissions -> WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE, INTERNET, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION,
 *
 * Required Sensors -> Sensor.TYPE_ACCELEROMETER, TYPE_MAGNETIC_FIELD, TYPE_GYROSCOPE, TYPE_LINEAR_ACCELERATION, TYPE_ROTATION_VECTOR
 *
 *
 */
private const val OFFSET_PERIOD = 5
private const val ON_HAND = 0
private const val IN_POCKET = 1
private const val HAND_SWING = 2

class IndoorLocalization {
    /**
     * 사용자의 식별정보 및 맵 데이터 확보
     *
     * @param inputStreamOfMap  자기장 맵 파일의 inputStream     ex) raw 폴더 운용시  resources.openRawResource(R.raw.ktx2f)
     */
    constructor(inputStreamOfMap : InputStream, inputStreamOfInstant : InputStream,
                inputStreamOfMap_Pocket : InputStream, inputStreamOfInstant_Pocket : InputStream) {
        //myUUID = uuid
        map = Map(inputStreamOfMap)
        map_pocket = Map(inputStreamOfMap_Pocket)
        instantLocalization = InstantLocalization(map, inputStreamOfInstant, map_pocket, inputStreamOfInstant_Pocket)
        magneticQueue = arrayListOf()   // ###
        instantResult = arrayOf("", "", "", "") // ###
        val thread = InstantLocalizationThread() // ###
        thread.start()
    }

    /**
     * 사용자의 식별정보, 맵 데이터 및 진입 좌표 확보
     *
     * @param inputStreamOfMap  자기장 맵 파일의 inputStream     ex) raw 폴더 운용시  resources.openRawResource(R.raw.intersect)
     * @param x        비콘 위치에 기반하여 전달받은 x 좌표
     * @param y        비콘 위치에 기반하여 전달받은 y 좌표
     * @param heading  사용자가 이동하던 방향
     */
    constructor(inputStreamOfMap : InputStream, inputStreamOfBuilding : InputStream,
                inputStreamOfMap_Pocket : InputStream,
                x : Float, y : Float, heading : Double) {
        //myUUID = uuid
        map = Map(inputStreamOfMap)
        map_pocket = Map(inputStreamOfMap_Pocket)
        orientationGyroscope.reset()
        gyroCaliValue = Math.toRadians(heading).toFloat()
        particleOn = true
        particleFilter = ParticleFilter(map, 100, round(x).toInt(), round(y).toInt(), 20)
        runningInsLoca = false
        isFirst = false
        returnIns = "Given position (${round(x).toInt()}, ${round(y).toInt()})"
    }

    private var angleA_AON: Double = 0.0
    private var caliX_AON: Double = 0.0
    private var caliY_AON: Double = 0.0
    private var caliZ_AON: Double = 0.0
    private var gyro_for_always_on: Float = 0.0f
    private var always_on_result: Array<String> = arrayOf()
    private val lstmServer by lazy {
        LSTMServer()
    }

    private var map : Map //= Map(inputStreamTotal)
    private var map_pocket : Map //= Map(inputStreamTotal)
    private lateinit var particleFilter : ParticleFilter
    private var currentFloor : String = ""
    //private var myUUID : String //= uuid

    private val accXMovingAverage : MovingAverage = MovingAverage(10)
    private val accYMovingAverage : MovingAverage = MovingAverage(10)
    private val accZMovingAverage : MovingAverage = MovingAverage(10)
    private val stepLengthMovingAverage : MovingAverage = MovingAverage(10)
    private val pressureMovingAverage : MovingAverage = MovingAverage(10)

    private var quaternion = FloatArray(4)
    private var magMatrix = FloatArray(3)
    private var accMatrix = FloatArray(3)
    private var roVecMatrix = FloatArray(5)
    private var pressure = FloatArray(1)

    private var light : Float = 0f
    /*private*/ var transformedAccZ : Float = 0f
    private var quaRoll : Float = 0f
    private var quaPitch : Float = 0f
    private var quaYaw : Float = 0f
    private var timeStamp : Double = 0.0
    private var upPeakTime : Double = 0.0
    private var downPeakTime : Double = 0.0
    private var magnitudeOfMagnetic : Double = 0.0
    private var magnitudeOfAcceleration : Double =0.0
    private var maxMagni : Double = 0.0

    private val oneStepPressure : Queue<Double> = LinkedList()
    private var totalStepCount : Int = 0
    private var oneStepSum : Double = 0.0
    private var lastStepLength : Double = 0.0
    private var maxAccX: Double = 0.0       //@@@@@@
    private var minAccX : Double = 0.0
    private var maxAccY : Double = 0.0
    private var minAccY : Double = 0.0
    private var maxAccZ : Double = 0.0
    private var minAccZ : Double = 0.0
    private var k : Double = 0.445
    private var isMapRotationMode : Boolean = false
    private var isUpPeak : Boolean = false
    private var isDownPeak : Boolean = false
    private var isStepFinished : Boolean = false
    private var particleOn : Boolean = false
    private var isSetFloor : Boolean = true

    private var isSensorStabled : Boolean = false
    private var magStableCount : Int = 100
    private var accStableCount : Int = 50

    //@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@ 원준 변수 @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
    private val orientationGyroscope by lazy {
        OrientationGyroscope()
    }
    private lateinit var instantLocalization : InstantLocalization
    private var rotation = FloatArray(3)
    private var fusedOrientation = FloatArray(3)
    private var mRotationMatrix = FloatArray(16)
    private var mRot = FloatArray(3)
    private var mAzimuth : Float = 0f
    private var angleA : Double = 0.0
    private var caliX : Double= 0.0
    private var caliY : Double= 0.0
    private var caliZ : Double= 0.0
    private var nowPosition = arrayOf(0.0, 0.0)
    private var gyroCaliValue : Float = 0f

    private var dir = 0.0
    private var preDir : Double = 0.0
    private var smoothDir : Double = 0.0
    private var dirOffset : Double = 0.0
    private var poseChanged : Boolean = false
    private var devicePosture : Int = 0

    private var isFailInstantLocalization : Boolean = false
    private var runningInsLoca : Boolean = true
    private var isFirst : Boolean = true
    private lateinit var magneticQueue : java.util.ArrayList<java.util.ArrayList<Double>> //= arrayListOf()   // ###
    private lateinit var instantResult : Array<String> //= arrayOf("", "", "", "") // ###
    private var stepCount : Int = 0

    private var returnGyro : String = ""
    private var returnIns : String = ""
    private var returnX : String = "unknown"
    private var returnY : String = "unknown"

    private var magnetic_sensor_accruacy : Int = 0

    init {
        //lstmServer.play()
        orientationGyroscope.reset()
//        instantLocalization = InstantLocalization(map)
//        val thread = InstantLocalizationThread() // ###
//        thread.start()
    }

    private fun axisTransform(axis : Char, rawDataX : Float, rawDataY : Float, rawDataZ : Float) : Double {
        return when(axis) {
            'x' -> { (cos(-quaYaw) * cos(-quaRoll) * rawDataX + (cos(-quaYaw) * sin(-quaRoll) * sin(quaPitch) - sin(-quaYaw) * cos(quaPitch)) * rawDataY + (cos(-quaYaw) * sin(-quaRoll) * cos(quaPitch) + sin(-quaYaw) * sin(quaPitch)) * rawDataZ).toDouble() }
            'y' -> { (sin(-quaYaw) * cos(-quaRoll) * rawDataX + (sin(-quaYaw) * sin(-quaRoll) * sin(quaPitch) + cos(-quaYaw) * cos(quaPitch)) * rawDataY + (sin(-quaYaw) * sin(-quaRoll) * cos(quaPitch) - cos(-quaYaw) * sin(quaPitch)) * rawDataZ).toDouble() }
            'z' -> { (-sin(quaRoll) * rawDataX + (cos(quaRoll) * sin(-quaPitch)) * rawDataY + (cos(quaRoll) * cos(-quaPitch)) * rawDataZ).toDouble() }
            else -> -966.966966
        }
    }


    private fun sensorReady(event: SensorEvent) : Boolean {
        if (isSensorStabled)
            return true

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
//        if (isSensorStabled) {
//            Glide.with(this).pauseAllRequests()
//            mFrameLayout.removeView(loadingLayout)
//            Toast.makeText(this, "Let's go~", Toast.LENGTH_SHORT).show()
//        }
        return isSensorStabled
    }

    private fun resetPosition() : String {

//        instantLocalization = InstantLocalization(map, inputStreamOfInstant)
        Log.d("mVector3", "resetPosition Call!!")
        instantResult = arrayOf("", "", "", "")
        returnIns = ""
        runningInsLoca = true
        isFailInstantLocalization = false
        magneticQueue.clear()
        magneticQueue.add(arrayListOf(caliX, caliY, caliZ, 0.0, dir))
        totalStepCount = 0
        val thread = InstantLocalizationThread() // ###
        thread.start()
        return "reset....."
    }

    /**
     * 측위 결과를 얻기 위한 메소드(실행 초반에는 센서가 안정화 될 때 까지 "The sensors is not ready yet" 리턴)
     *
     * ex)
     *
     * override fun onSensorChanged(event: SensorEvent?) {
     *
     * var result = indoorLocalization.sensorChanged(event)...
     *
     *
     * @param event SensorEvent   ex) override fun onSensorChanged(event: SensorEvent?) {
     * @return String array, size 5  -> {Gyro, 초기 위치 인식 상태, count, X측위좌표, Y측위좌표}
     */
    fun sensorChanged(event: SensorEvent?) : Array<String> {
        if ((event?:false)?.let {sensorReady(event!!)}) {
            when(event!!.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> accMatrix = event.values.clone()
                Sensor.TYPE_LIGHT -> {
                    light = event.values.clone()[0]
//                    locationView.text = "light : $light"
                    if (light > 10 && poseChanged) {
                        poseChanged = false
                        dirOffset = 0.0
                    }
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    magnetic_sensor_accruacy = event.accuracy
                    if (magnetic_sensor_accruacy == 3) {
                        magMatrix = event.values.clone()
                        magnitudeOfMagnetic = sqrt(magMatrix[0].pow(2) + magMatrix[1].pow(2) + magMatrix[2].pow(2) ).toDouble()
                        if ( accMatrix.isNotEmpty() && magMatrix.isNotEmpty() ) {
                            var I = FloatArray(9)
                            var success = SensorManager.getRotationMatrix(mRotationMatrix, I, accMatrix, magMatrix)
                            mRot[0] = mRotationMatrix[0] * magMatrix[0] + mRotationMatrix[1] * magMatrix[1] + mRotationMatrix[2] * magMatrix[2]
                            mRot[1] = mRotationMatrix[4] * magMatrix[0] + mRotationMatrix[5] * magMatrix[1] + mRotationMatrix[6] * magMatrix[2]
                            mRot[2] = mRotationMatrix[8] * magMatrix[0] + mRotationMatrix[9] * magMatrix[1] + mRotationMatrix[10] * magMatrix[2]
                            if (success) {
                                var orientation = FloatArray(3)
                                SensorManager.getOrientation(mRotationMatrix, orientation)
                                mAzimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
//                                Log.d("sampling", "${Math.toDegrees(orientation[0].toDouble())}\t${Math.toDegrees(orientation[1].toDouble())}\t${Math.toDegrees(orientation[2].toDouble())}")
                            }
                            // Instant Localization 끝나기 전까지는 자기장 x, y가 글로벌 좌표계에서의 nonCali 값으로 들어가야됨.
                            // 글로벌 좌표계에서의 nonCali를 위해 그냥 angleA 만 바꿔주면 됨.
//                            angleA = if (particleOn) {
//                                (mAzimuth - Math.toDegrees(fusedOrientation[0].toDouble()) + 360) % 360    // Instant Localization 에서 받아온 초기 방향 값을 사용
//                            } else {
//                                (mAzimuth.toDouble() + 360) % 360
//                            }
                            angleA = (mAzimuth - Math.toDegrees(fusedOrientation[0].toDouble()) + 360) % 360    // Instant Localization 에서 받아온 초기 방향 값을 사용
                            ////////////////////
                            caliX = -1 * sqrt(magnitudeOfMagnetic.pow(2) - mRot[2].pow(2)) * sin(angleA * PI / 180)
                            caliY = sqrt(magnitudeOfMagnetic.pow(2) - mRot[2].pow(2)) * cos(angleA * PI / 180)
                            caliZ = mRot[2].toDouble()
                            /////////////////////////////////////

                            /////////////////// 211007 원준 수정 ////////////////////////
                            angleA_AON = (mAzimuth - Math.toDegrees(gyro_for_always_on.toDouble()) + 360) % 360    // Instant Localization 에서 받아온 초기 방향 값을 사용
                            ////////////////////
                            caliX_AON = -1 * sqrt(magnitudeOfMagnetic.pow(2) - mRot[2].pow(2)) * sin(angleA_AON * PI / 180)
                            caliY_AON = sqrt(magnitudeOfMagnetic.pow(2) - mRot[2].pow(2)) * cos(angleA_AON * PI / 180)
                            caliZ_AON = mRot[2].toDouble()
                            /////////////////////////////////////
                            if (isFirst) {
                                if (mRot[2] != 0f) {
                                    isFirst = false
                                    magneticQueue.add(arrayListOf(caliX, caliY, caliZ, 0.0, dir))
                                    ///test///
//                                    magneticQueue.add(arrayListOf(-14.80063863, 6.562735305, -9.376439095, 0.0, 360.0))
                                }
                            }
                        }
                    }
                }
                Sensor.TYPE_GYROSCOPE -> {
                    System.arraycopy(event.values, 0, rotation, 0, event.values.size)
                    if (!orientationGyroscope.isBaseOrientationSet)
                        orientationGyroscope.setBaseOrientation(Quaternion.IDENTITY)
                    else
                        fusedOrientation = orientationGyroscope.calculateOrientation(rotation, event.timestamp)

                    gyro_for_always_on = fusedOrientation[0]
                    fusedOrientation[0] += gyroCaliValue

                    dir = round( (Math.toDegrees(fusedOrientation[0].toDouble()) + 360) % 360)
                    smoothDir = (dir/10).roundToInt() * 10.0
                }
                Sensor.TYPE_ROTATION_VECTOR -> {
                    roVecMatrix = event.values.clone()
                    if (roVecMatrix.isNotEmpty()) {
                        quaternion[0]= roVecMatrix[3]
                        quaternion[1]= roVecMatrix[0]
                        quaternion[2]= roVecMatrix[1]
                        quaternion[3]= roVecMatrix[2]
                    }
                }
//                Sensor.TYPE_PRESSURE -> {
//                    pressure = event.values.clone()
//                    pressureMovingAverage.newData(pressure[0].toDouble())
//                    oneStepPressure.add(pressureMovingAverage.getAvg())
//                    oneStepSum += pressureMovingAverage.getAvg()
//                }
                Sensor.TYPE_LINEAR_ACCELERATION -> {
                    timeStamp = System.currentTimeMillis().toString().substring(6).toDouble()

                    quaYaw = atan2(2.0 * (quaternion[3] * quaternion[0] + quaternion[1] * quaternion[2]),
                        1 - 2.0 * (quaternion[0] * quaternion[0] + quaternion[1] * quaternion[1])).toFloat()
                    quaPitch = (-atan2(2 * (quaternion[0] * quaternion[1] + quaternion[3] * quaternion[2]).toDouble(),
                        quaternion[3] * quaternion[3] + quaternion[0] * quaternion[0] - quaternion[1] * quaternion[1] - (quaternion[2] * quaternion[2]).toDouble())).toFloat()
                    quaRoll = asin(2 * (quaternion[0] * quaternion[2] - quaternion[3] * quaternion[1]).toDouble()).toFloat()

                    accXMovingAverage.newData(event.values[0].toDouble())
                    accYMovingAverage.newData(event.values[1].toDouble())
                    accZMovingAverage.newData(event.values[2].toDouble())

                    magnitudeOfAcceleration = sqrt((accXMovingAverage.getAvg().pow(2) + accYMovingAverage.getAvg().pow(2) + accZMovingAverage.getAvg().pow(2)))
                    maxMagni = if (maxMagni > magnitudeOfAcceleration) maxMagni else magnitudeOfAcceleration

                    val accDataForStepDetection = axisTransform('z', accXMovingAverage.getAvg().toFloat(), accYMovingAverage.getAvg().toFloat(), accZMovingAverage.getAvg().toFloat())
                    transformedAccZ = accDataForStepDetection.toFloat()

                    val accX = accXMovingAverage.getAvg()       //@@@@@@
                    maxAccX = if (maxAccX > accX) maxAccX else accX
                    minAccX = if (minAccX > accX) accX else minAccX

                    val accY = accYMovingAverage.getAvg()
                    maxAccY = if (maxAccY > accY) maxAccY else accY
                    minAccY = if (minAccY > accY) accY else minAccY

                    if ( !isUpPeak && !isDownPeak && !isStepFinished ) { //가속도의 up peak점 검출 c=1, maxiaz에는 up peak의 가속도값이 저장
                        if (accDataForStepDetection > 0.4) {
                            if (accDataForStepDetection < maxAccZ) {
                                isUpPeak = true
                                upPeakTime = timeStamp
                            } else
                                maxAccZ = accDataForStepDetection
                        }
                    }
                    if ( isUpPeak && !isDownPeak && !isStepFinished ) {
                        if (accDataForStepDetection > maxAccZ) {
                            maxAccZ = accDataForStepDetection
                            upPeakTime = timeStamp
                        } else if (accDataForStepDetection < -0.2) {
                            if (accDataForStepDetection > minAccZ) {
                                isDownPeak = true
                                downPeakTime = timeStamp
                            } else
                                minAccZ = accDataForStepDetection
                        }
                    }
                    if ( isUpPeak && isDownPeak && !isStepFinished ) {
                        if (accDataForStepDetection < minAccZ) {
                            minAccZ = accDataForStepDetection
                            downPeakTime = timeStamp
                        } else if (accDataForStepDetection > 0.0)
                            isStepFinished = true
                    }
                    if ( isUpPeak && isDownPeak && isStepFinished ) {
                        var timePeak2Peak = downPeakTime - upPeakTime

                        if ( timePeak2Peak > 100 && timePeak2Peak < 1000 /*&& maxAccZ < 5 && minAccZ > -4*/) {
//                            vibrator.vibrate(80)
                            totalStepCount++
//                            oneStepSum = 0.0
//                            oneStepPressure.clear()

                            if (light < 10) {

                                if(!poseChanged) {
                                    poseChanged = true
                                    dirOffset = (smoothDir - preDir)
                                    devicePosture = IN_POCKET       //@@@@@@

                                    val myThread = OffsetControl()
                                    myThread.isDaemon = true
                                    myThread.start()
                                }
                            } else {
                                preDir = smoothDir
                                devicePosture = if (maxMagni < 5) ON_HAND else HAND_SWING
                            }

                            // EX weinberg approach
                            lastStepLength = k * sqrt(sqrt(maxAccZ - minAccZ))
                            stepLengthMovingAverage.newData(lastStepLength)
                            var stepLength = stepLengthMovingAverage.getAvg()
                            //lstmServer.dataSaveAndSend(cali_x, cali_y, cali_z)

                            //var msg : String = ""
                            if (particleOn) {
                                nowPosition = particleFilter.step(arrayOf(caliX, caliY, caliZ), round((dir + dirOffset + 360)%360), stepLength)

                                /////////////////// 211007 원준 수정 ////////////////////////
                                //// Always On
//                                always_on_result = instantLocalization.getLocation(caliX_AON.toFloat(), caliY_AON.toFloat(), caliZ_AON.toFloat(), stepLength.toFloat(), gyro_for_always_on, devicePosture)
//                                Log.d("aon", always_on_result[0].toString())
//                                if (always_on_result[0] == "완전 수렴") {
////                                    orientationGyroscope.reset()
////                                    gyroCaliValue = Math.toRadians(always_on_result[1].toDouble()).toFloat()
////                                    particleFilter = ParticleFilter(map, 100, always_on_result[2].toFloat().toInt(), always_on_result[3].toFloat().toInt(), 10)
////                                    nowPosition[0] = always_on_result[2].toDouble()
////                                    nowPosition[1] = always_on_result[3].toDouble()
//                                }
                                /////////////////////////////////////////////////////////

                                when(isFailInstantLocalization) {
                                    true -> resetPosition()
                                    else -> {
                                        if (runningInsLoca)
                                            magneticQueue.add(arrayListOf(caliX, caliY, caliZ, stepLength, dir))  // ###
                                    }
                                }

                                returnX = nowPosition[0].toString()
                                returnY = nowPosition[1].toString()
                            } else {
                                if (isFailInstantLocalization) {
                                    resetPosition()
                                } else {
                                    if (magnetic_sensor_accruacy == 3) {
                                        magneticQueue.add(arrayListOf(caliX, caliY, caliZ, stepLength, dir))  // ###
                                    }
                                }
                            }
                        }
                        isUpPeak = false
                        isDownPeak = false
                        isStepFinished = false
                        maxMagni = 0.0
                        maxAccX = 0.0
                        minAccX =0.0
                        maxAccY = 0.0
                        minAccY = 0.0       //@@@@@@
                        maxAccZ = 0.0
                        minAccZ = 0.0
                    }

                }
            }
        } else {
            return arrayOf("The sensors is not ready yet", "The sensors is not ready yet", "The sensors is not ready yet", "The sensors is not ready yet", "The sensors is not ready yet")
        }
        returnGyro = round(dir).toString()
        return arrayOf(returnGyro, returnIns, totalStepCount.toString(), returnX, returnY)
    }

    fun getStatus(): String {
        var s = when(devicePosture) {
            0 -> "ON HAND"
            1 -> "IN POCKET"
            2 -> "HAND SWING"
            else -> ""
        }
        return s
    }

    private inner class InstantLocalizationThread: Thread() {
        override fun run() {
            while(runningInsLoca) {
                if (magneticQueue.size > 0) {
                    var inputData : java.util.ArrayList<Double>
                    // 210913 원준 수정 : magneticQueue에 아무 값도 없음에도 간혹 이 if문에 들어오는 경우가 있어,
                    // 아래 문장을 try ~ catch로 우선 해결. 근본적인 그 원인을 찾아야함.
                    try {
                        inputData = magneticQueue.removeAt(0)
                    }
                    catch (e:java.lang.Exception) {
                        continue
                    }
                    Log.d("mVector2", "${inputData[0].toFloat()}, ${inputData[1].toFloat()}, ${inputData[2].toFloat()}, ${inputData[3].toFloat()}, ${inputData[4].toFloat()}")
                    angleA = (mAzimuth - Math.toDegrees(fusedOrientation[0].toDouble()) + 360) % 360    // Instant Localization 에서 받아온 초기 방향 값을 사용
                    instantResult = instantLocalization.getLocation(inputData[0].toFloat(), inputData[1].toFloat(), inputData[2].toFloat(), inputData[3].toFloat(), inputData[4].toFloat(), state=devicePosture /*isfirst = first_instant_localization*/)
                    returnIns = instantResult[0]
                    // 어플 켜지자마자 바로 instant localization 첫 연산 시작
                    if (instantResult[0] == "완전 수렴") {
                        returnX = instantResult[2]
                        returnY = instantResult[3]

                        orientationGyroscope.reset()
                        gyroCaliValue = Math.toRadians(instantResult[1].toDouble()).toFloat()
                        particleFilter = ParticleFilter(map, 100, instantResult[2].toFloat().toInt(), instantResult[3].toFloat().toInt(), 10)

                        particleOn = true
                        runningInsLoca = false
                        break
                    } else if (instantResult[0] == "에러! 일치되는 좌표가 없음") {
                        isFailInstantLocalization = true
                        runningInsLoca = false
                        break
                    }
                }
            }
        }
    }

    inner class OffsetControl : Thread() {
        override fun run() {
            var i = 0
            var preStepCount = totalStepCount
            var sumCos = 0.0
            var sumSin = 0.0
            while (i< OFFSET_PERIOD) {
                try {
                    if (preStepCount != totalStepCount) {
                        //Log.d("in whileLoop", "ok : $i       dir : $dir")
                        preStepCount = totalStepCount
                        i++
                        sumCos += cos(fusedOrientation[0])
                        sumSin += sin(fusedOrientation[0])
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            dirOffset =  preDir - ((round((Math.toDegrees(atan2(sumSin, sumCos))) * 10) /10)+360) % 360
        }
    }

    private var handler: Handler = object : Handler(Looper.myLooper()!!) {
        override fun handleMessage(msg: Message) {
            if (msg.what == 1) {
                stepCount = 0
                resetPosition()
            }
        }
    }

    private inner class AutoInsLoca: Thread() {
        override fun run() {
            while (true) {
                var w = when(stepCount) {
                    in 0..30 -> 0
                    else -> 1
                }
                handler.sendEmptyMessage(w)
                try {
                    sleep(1000)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}