package com.example.CollectorForElevator

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Environment
import android.os.Vibrator
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.kircherelectronics.fsensor.filter.gyroscope.OrientationGyroscope
import kotlinx.android.synthetic.main.activity_ultimate.*
import kotlinx.android.synthetic.main.activity_ultimate.angleFixMode
import kotlinx.android.synthetic.main.activity_ultimate.angleView
import kotlinx.android.synthetic.main.activity_ultimate.btnGyroReset
import kotlinx.android.synthetic.main.activity_ultimate.btnSamplingStart
import kotlinx.android.synthetic.main.activity_ultimate.collectionLog
import kotlinx.android.synthetic.main.activity_ultimate.fileName
import kotlinx.android.synthetic.main.activity_ultimate.gyroView
import kotlinx.android.synthetic.main.activity_ultimate.magXView
import kotlinx.android.synthetic.main.activity_ultimate.magYView
import kotlinx.android.synthetic.main.activity_ultimate.magZView
import kotlinx.android.synthetic.main.activity_ultimate.positionXView
import kotlinx.android.synthetic.main.activity_ultimate.positionYView
import kotlinx.android.synthetic.main.activity_ultimate.stepCountView
import org.apache.commons.math3.complex.Quaternion
import java.io.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.*

private const val l : Double = 10.0

class UltimateActivity : AppCompatActivity(), SensorEventListener {
    private val mSensorManager by lazy {
        getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    private val vibrator by lazy {
        getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    private val accZMovingAverage : MovingAverage = MovingAverage(10)

    private var quaternion = FloatArray(4)
    private var magMatrix = FloatArray(3)
    private var accMatrix = FloatArray(3)
    private var roVecMatrix = FloatArray(5)
    private var gameRotationVector = FloatArray(5)
    private var backUpPosition = DoubleArray(2)

    private var saveData : String = ""
    private var backUpSaveData : String = ""
    private var linearAccX : Float = 0f
    private var linearAccY : Float = 0f
    private var linearAccZ : Float = 0f
    private var transformedAccX : Float = 0f
    private var transformedAccY : Float = 0f
    private var transformedAccZ : Float = 0f
    private var quaRoll : Float = 0f
    private var quaPitch : Float = 0f
    private var quaYaw : Float = 0f
    private var timeStamp : Double = 0.0
    private var upPeakTime : Double = 0.0
    private var downPeakTime : Double = 0.0
    private var magnitudeOfMagnetic : Double = 0.0
    private var positionX : Double = 0.0
    private var positionY : Double = 0.0
    private var autoFileNum : Int = 0
    private var stepCount : Int = 0
    private var backUpStepCount : Int = 0
    private var maxAccZ : Double = 0.0
    private var minAccZ : Double = 0.0
    private var k : Double = 0.445
    private var isAngleFixed : Boolean = false
    private var isUpPeak : Boolean = false
    private var isDownPeak : Boolean = false
    private var isStepFinished : Boolean = false
    private var nowSampling : Boolean = false
    private var paused : Boolean = false
    private var isRemember : Boolean = false
    private var isRestore : Boolean = false

    private val period : Int = 5
    private var positionLog : Queue<String> = LinkedList()
    private var dataPosition : Queue<String> = LinkedList()
    private var dataMagx : Queue<String> = LinkedList()
    private var dataMagy : Queue<String> = LinkedList()
    private var dataMagz : Queue<String> = LinkedList()

    private var backUpHeading : ArrayList<Double> = arrayListOf()

    //-----------------매뉴얼 변수--------------------------
    private val mRotationMatrix = FloatArray(16)
    private var angleA = 0.0
    private val rotation = FloatArray(3)
    private var fusedOrientation = FloatArray(3)
    //----------------------------------------------------

    //---------------원준 변수-----------------------------
    private val orientationGyroscope by lazy {
        OrientationGyroscope()
    }
    private var mRot = FloatArray(3)
    private var mAzimuth : Float = 0f
    private var caliX : Double= 0.0
    private var caliY : Double= 0.0
    private var caliZ : Double= 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ultimate)
        init()

        angleFixMode.setOnClickListener { isAngleFixed = angleFixMode.isChecked }

        btnGyroReset.setOnClickListener { orientationGyroscope.reset() }

        btnPause.setOnClickListener{
            paused = !paused

            if (paused) {
                btnPause.text = "GO"
            } else {
                btnPause.text = "PAUSE"
                backUpPosition[0] = positionX
                backUpPosition[1] = positionY
                backUpSaveData = saveData
                backUpStepCount = stepCount
                dataPosition.clear()
                dataMagx.clear()
                dataMagy.clear()
                dataMagz.clear()
            }
        }

        btnRetry.setOnClickListener {
            positionXView.setText(backUpPosition[0].toString())
            positionYView.setText(backUpPosition[1].toString())
            positionX = backUpPosition[0]
            positionY = backUpPosition[1]
            saveData = backUpSaveData
            stepCount = backUpStepCount
            dataPosition.clear()
            dataMagx.clear()
            dataMagy.clear()
            dataMagz.clear()
        }

        backUpHeadingMode.setOnClickListener {
            isRemember = backUpHeadingMode.isChecked
            if (isRemember) {
                cbBackUp.isChecked = false
                cbRestore.isChecked = false
                backUpHeading.clear()
            }
        }

        cbBackUp.setOnClickListener {
            isRestore = !cbBackUp.isChecked
            cbRestore.isChecked = !cbBackUp.isChecked
        }
        cbRestore.setOnClickListener {
            isRestore = cbRestore.isChecked
            cbBackUp.isChecked = !cbRestore.isChecked
        }
    }

    private fun init() {
        positionXView.setText("0")
        positionYView.setText("0")
        stepCountView.text = "  step count : ${stepCount.toString()}"

        var path = getExternalPath()
        val file = File(path)
        if (!file.exists()) file.mkdir()
    }

    private fun getExternalPath() : String {
        var sdPath = ""
        val ext = Environment.getExternalStorageState()
        sdPath = if (ext == Environment.MEDIA_MOUNTED) {
            Environment.getExternalStorageDirectory()
                .absolutePath + "/CollectorForElevator/"
        } else {
            "$filesDir/CollectorForElevator/"
        }
        return sdPath
    }

    private fun writeFile(title: String, body: String) {
        try {
            val path = getExternalPath()
            val bw = BufferedWriter(FileWriter(path + title, false))
            bw.write(body)
            bw.close()
            Toast.makeText(this, path + "에 저장완료", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
        }
        dataPosition.clear()
        dataMagx.clear()
        dataMagy.clear()
        dataMagz.clear()
    }

    fun onChangePositionClicked(v: View) {
        positionX = positionXView.text.toString().toDouble()
        positionY = positionYView.text.toString().toDouble()
        when (v.id) {
            R.id.plusX -> positionX++
            R.id.minusX -> if (positionX > 0) positionX--
            R.id.plusY -> positionY++
            R.id.minusY -> if (positionY > 0) positionY--
        }
        positionXView.setText(positionX.toString())
        positionYView.setText(positionY.toString())
    }

    fun onBtnSamplingStartClicked(v: View?) {
        positionX = positionXView.text.toString().toDouble()
        positionY = positionYView.text.toString().toDouble()

        positionLog.add("($positionX, $positionY)")
        if (positionLog.size > period) positionLog.remove()

        var fullLog = " "
        for (s in positionLog) {
            fullLog += "$s   "
        }
        collectionLog.text = fullLog

        if (!nowSampling) {
            btnSamplingStart.text = "STOP RECORDING"
            nowSampling = true
            saveData = "${round(positionX)}\t${round(positionY)}\t${this.caliX}\t${this.caliY}\t${this.caliZ}\t${(Math.toDegrees(this.fusedOrientation[2].toDouble()) + 360) % 360}\r\n"
            backUpPosition[0] = positionX
            backUpPosition[1] = positionY
            backUpSaveData = saveData
            stepCount=0
            stepCountView.text = "  step count : ${stepCount.toString()}"
            if (isRemember && !isRestore) {
                backUpHeading = arrayListOf(if (isAngleFixed) angleView.text.toString().toDouble() else gyroView.text.toString().toDouble())
            }
            Toast.makeText(this, "지금부터 데이터를 기록합니다.", Toast.LENGTH_SHORT).show()
        } else {
            btnSamplingStart.text = "START RECORDING"
            nowSampling = false
            writeFile(if (fileName.text.toString().isEmpty()) "default${autoFileNum++}.txt" else fileName.text.toString(), saveData)
        }
    }

    private fun axisTransform(axis : Char, rawDataX : Float, rawDataY : Float, rawDataZ : Float) : Float {
        return when(axis) {
            'x' -> { cos(-quaYaw) * cos(-quaRoll) * rawDataX+ (cos(-quaYaw) * sin(-quaRoll) * sin(quaPitch) - sin(-quaYaw) * cos(quaPitch)) * rawDataY+ (cos(-quaYaw) * sin(-quaRoll) * cos(quaPitch) + sin(-quaYaw) * sin(quaPitch)) * rawDataZ }
            'y' -> { sin(-quaYaw) * cos(-quaRoll) * rawDataX+ (sin(-quaYaw) * sin(-quaRoll) * sin(quaPitch) + cos(-quaYaw) * cos(quaPitch)) * rawDataY+ (sin(-quaYaw) * sin(-quaRoll) * cos(quaPitch) - cos(-quaYaw) * sin(quaPitch)) * rawDataZ }
            'z' -> { -sin(quaRoll) * rawDataX+ (cos(quaRoll) * sin(-quaPitch)) * rawDataY+ (cos(quaRoll) * cos(-quaPitch)) * rawDataZ }
            else -> -966.966966f
        }
    }

    private fun extractSaveData() {
        val extractPeriod = dataPosition.size / 6
        var theta = 0.0
        var nextX = 0.0
        var nextY = 0.0
        for (i in 0..5) {
            for (j in 0 until extractPeriod - 1) {
                dataPosition.poll()
                dataMagx.poll()
                dataMagy.poll()
                dataMagz.poll()
            }
            var posi = dataPosition.poll().split("\t")

            if (isAngleFixed) {
                theta = angleView.text.toString().toDouble()
            } else {
                theta = gyroView.text.toString().toDouble()
            }
            if (isRemember) {
                if (isRestore) {
                    theta = backUpHeading[if ((i+1)+(6*stepCount) >= backUpHeading.size) backUpHeading.size-1 else (i+1)+(6*stepCount)]
                } else {
                    backUpHeading.add(theta)
                }
            }
            nextX = posi[0].toDouble() + sin(Math.toRadians(theta)) * (i+1)
            nextY = posi[1].toDouble() + cos(Math.toRadians(theta)) * (i+1)
            saveData += round(nextX).toString() + "\t" + round(nextY) + "\t" + dataMagx.poll() + "\t" + dataMagy.poll() + "\t" + dataMagz.poll() + "\t"+ (Math.toDegrees(fusedOrientation[2].toDouble()) + 360) % 360 + "\r\n"
        }
        dataPosition.clear()
        dataMagx.clear()
        dataMagy.clear()
        dataMagz.clear()
        autoPosition(nextX, nextY)
    }

    private fun autoPosition(posiX : Double, posiY : Double) {
        positionX = posiX
        positionY = posiY
        positionXView.setText(posiX.toString())
        positionYView.setText(posiY.toString())
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

    }

    override fun onSensorChanged(event: SensorEvent?) {
        var accDataForStepDetection : Double
        if (event != null) {
            when(event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> accMatrix = event.values.clone()
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    magMatrix = event.values.clone()

                    if (magMatrix.isNotEmpty() && gameRotationVector.isNotEmpty()) {
                        var I = FloatArray(9)
                        // 인스턴트 로컬라이제이션 실시간 데이터 수집용
//                        gameRotationVector[2] = 0.0f
                        SensorManager.getRotationMatrixFromVector(mRotationMatrix, gameRotationVector)

                        mRot[0] = mRotationMatrix[0] * magMatrix[0] + mRotationMatrix[1] * magMatrix[1] + mRotationMatrix[2] * magMatrix[2]
                        mRot[1] = mRotationMatrix[4] * magMatrix[0] + mRotationMatrix[5] * magMatrix[1] + mRotationMatrix[6] * magMatrix[2]
                        mRot[2] = mRotationMatrix[8] * magMatrix[0] + mRotationMatrix[9] * magMatrix[1] + mRotationMatrix[10] * magMatrix[2]

                        caliX = mRot[0].toDouble()
                        caliY = mRot[1].toDouble()
                        caliZ = mRot[2].toDouble()

                        magXView.text = "X: " + (Math.round(mRot[0])).toString()
                        magYView.text = "Y: " + (Math.round(mRot[1])).toString()
                        magZView.text = "Z: " + (Math.round(mRot[2])).toString()
//                        AzimuthView.text = "M: " + Math.round(sqrt(magMatrix[0].pow(2) + magMatrix[1].pow(2) + magMatrix[2].pow(2)).toDouble()).toString()
                    }
                }
                Sensor.TYPE_GYROSCOPE -> {
                    System.arraycopy(event.values, 0, rotation, 0, event.values.size)
                    if (!orientationGyroscope.isBaseOrientationSet)
                        orientationGyroscope.setBaseOrientation(Quaternion.IDENTITY)
                    else
                        fusedOrientation = orientationGyroscope.calculateOrientation(rotation, event.timestamp)

                    gyroView.text = Math.round((((Math.toDegrees(fusedOrientation[2].toDouble()) + 360) % 360)*100)/100.0).toString()
                }
                Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                    gameRotationVector = event.values.clone()
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
                Sensor.TYPE_LINEAR_ACCELERATION -> {
                    if (nowSampling && !paused) {
                        timeStamp = System.currentTimeMillis().toString().substring(6).toDouble()

                        quaYaw = atan2(2.0 * (quaternion[3] * quaternion[0] + quaternion[1] * quaternion[2]), 1 - 2.0 * (quaternion[0] * quaternion[0] + quaternion[1] * quaternion[1])).toFloat()
                        quaPitch = (-atan2(2 * (quaternion[0] * quaternion[1] + quaternion[3] * quaternion[2]).toDouble(), quaternion[3] * quaternion[3] + quaternion[0] * quaternion[0] - quaternion[1] * quaternion[1] - (quaternion[2] * quaternion[2]).toDouble())).toFloat()
                        quaRoll = asin(2 * (quaternion[0] * quaternion[2] - quaternion[3] * quaternion[1]).toDouble()).toFloat()

                        linearAccX = event.values[0]
                        linearAccY = event.values[1]
                        linearAccZ = event.values[2]

                        transformedAccX = axisTransform('x', linearAccX, linearAccY, linearAccZ)
                        transformedAccY = axisTransform('y', linearAccX, linearAccY, linearAccZ)
                        transformedAccZ = axisTransform('z', linearAccX, linearAccY, linearAccZ)
                        accZMovingAverage.newData(transformedAccZ.toDouble())
                        accDataForStepDetection = accZMovingAverage.getAvg()

                        if (!isUpPeak && !isDownPeak && !isStepFinished) { //가속도의 up peak점 검출 c=1, maxiaz에는 up peak의 가속도값이 저장
                            if (accDataForStepDetection > 0.5) {
                                if (accDataForStepDetection < maxAccZ) {
                                    isUpPeak = true
                                    upPeakTime = timeStamp
                                } else
                                    maxAccZ = accDataForStepDetection
                            }
                        }
                        if (isUpPeak && !isDownPeak && !isStepFinished) {
                            if (accDataForStepDetection > maxAccZ) {
                                maxAccZ = accDataForStepDetection
                                upPeakTime = timeStamp
                            } else if (accDataForStepDetection < -0.3) {
                                if (accDataForStepDetection > minAccZ) {
                                    isDownPeak = true
                                    downPeakTime = timeStamp
                                } else
                                    minAccZ = accDataForStepDetection
                            }
                        }
                        if (isUpPeak && isDownPeak && !isStepFinished) {
                            if (accDataForStepDetection < minAccZ) {
                                minAccZ = accDataForStepDetection
                                downPeakTime = timeStamp
                            } else if (accDataForStepDetection > 0.2)
                                isStepFinished = true
                        }
                        if (isUpPeak && isDownPeak && isStepFinished) {
                            var time_peak2peak = downPeakTime - upPeakTime

                            if (time_peak2peak > 150 && time_peak2peak < 400 /*&& maxAccZ < 5 && minAccZ > -4*/) {
                                vibrator.vibrate(80)
                                // EX weinberg approach
                                //lastStepLength = k * Math.sqrt(Math.sqrt(maxAccZ - minAccZ))
                                //stepLengthMovingAverage.newData(lastStepLength)

                                extractSaveData()

                                stepCount++
                                stepCountView.text = "  step count : ${stepCount.toString()}"
                            }
                            isUpPeak = false
                            isDownPeak = false
                            isStepFinished = false
                            maxAccZ = 0.0
                            minAccZ = 0.0
                        }
                        dataPosition.add(positionX.toString() + "\t" + positionY.toString())
                        dataMagx.add(this.caliX.toString())
                        dataMagy.add(this.caliY.toString())
                        dataMagz.add(this.caliZ.toString())
                    } else {
                        isUpPeak = false
                        isDownPeak = false
                        isStepFinished = false
                        maxAccZ = Double.NEGATIVE_INFINITY
                        minAccZ = Double.POSITIVE_INFINITY
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME)
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_GAME)
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_GAME)
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION), SensorManager.SENSOR_DELAY_GAME)
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_GAME)
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_GAME)
    }

    override fun onPause() {
        super.onPause()
        mSensorManager.unregisterListener(this)
    }
    private fun getRotationMatrixFromVector_Quaternion(
        R: FloatArray,
        rotationVector: FloatArray
    ) {
        var q0: Float
        val q1 = rotationVector[0]
        val q2 = rotationVector[1]
        val q3 = rotationVector[2]
        if (rotationVector.size >= 4) {
            q0 = rotationVector[3]
        } else {
            q0 = 1 - q1 * q1 - q2 * q2 - q3 * q3
            q0 = if (q0 > 0) Math.sqrt(q0.toDouble()).toFloat() else 0.0f
        }
        val sq_q1 = 2 * q1 * q1
        val sq_q2 = 2 * q2 * q2
        val sq_q3 = 2 * q3 * q3
        val q1_q2 = 2 * q1 * q2
        val q3_q0 = 2 * q3 * q0
        val q1_q3 = 2 * q1 * q3
        val q2_q0 = 2 * q2 * q0
        val q2_q3 = 2 * q2 * q3
        val q1_q0 = 2 * q1 * q0
        if (R.size == 9) {
            R[0] = 1 - sq_q2 - sq_q3
            R[1] = q1_q2 - q3_q0
            R[2] = q1_q3 + q2_q0
            R[3] = q1_q2 + q3_q0
            R[4] = 1 - sq_q1 - sq_q3
            R[5] = q2_q3 - q1_q0
            R[6] = q1_q3 - q2_q0
            R[7] = q2_q3 + q1_q0
            R[8] = 1 - sq_q1 - sq_q2
        } else if (R.size == 16) {
            R[0] = 1 - sq_q2 - sq_q3
            R[1] = q1_q2 - q3_q0
            R[2] = q1_q3 + q2_q0
            R[3] = 0.0f
            R[4] = q1_q2 + q3_q0
            R[5] = 1 - sq_q1 - sq_q3
            R[6] = q2_q3 - q1_q0
            R[7] = 0.0f
            R[8] = q1_q3 - q2_q0
            R[9] = q2_q3 + q1_q0
            R[10] = 1 - sq_q1 - sq_q2
            R[11] = 0.0f
            R[14] = 0.0f
            R[13] = R[14]
            R[12] = R[13]
            R[15] = 1.0f
            if ((q2_q3 + q1_q0) / 2 > 0.499) {
                R[1] = q2
                R[5] = q0
                R[8] = 0.000001f
            } else if ((q2_q3 + q1_q0) / 2 < -0.499) {
                R[1] = q2
                R[5] = q0
                R[8] = -0.000001f
            }
        }
    }

    private fun getOrientation_Quaternion(R: FloatArray, values: FloatArray): FloatArray? {
        /*
         * 4x4 (length=16) case:
         *   /  R[ 0]   R[ 1]   R[ 2]   0  \
         *   |  R[ 4]   R[ 5]   R[ 6]   0  |
         *   |  R[ 8]   R[ 9]   R[10]   0  |
         *   \      0       0       0   1  /
         *
         * 3x3 (length=9) case:
         *   /  R[ 0]   R[ 1]   R[ 2]  \
         *   |  R[ 3]   R[ 4]   R[ 5]  |
         *   \  R[ 6]   R[ 7]   R[ 8]  /
         *
         */
        if (R.size == 9) {
            values[0] = Math.atan2(R[1].toDouble(), R[4].toDouble()).toFloat()
            values[1] = Math.asin((-R[7]).toDouble()).toFloat()
            values[2] = Math.atan2((-R[6]).toDouble(), R[8].toDouble()).toFloat()
        } else {
            if (R[8] == 0.000001f) {
                values[0] = -2.0f * Math.atan2(R[1].toDouble(), R[5].toDouble()).toFloat()
                values[1] = Math.asin((-R[9]).toDouble()).toFloat()
                values[2] = 0.0f
            } else if (R[8] == -0.000001f) {
                values[0] = 2.0f * Math.atan2(R[1].toDouble(), R[5].toDouble()).toFloat()
                values[1] = Math.asin((-R[9]).toDouble()).toFloat()
                values[2] = 0.0f
            } else {
                values[0] = Math.atan2(R[1].toDouble(), R[5].toDouble()).toFloat()
                values[1] = Math.asin((-R[9]).toDouble()).toFloat()
                values[2] = Math.atan2((-R[8]).toDouble(), R[10].toDouble()).toFloat()
            }
        }
        return values
    }
}