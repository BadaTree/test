package com.example.advancedmapcollector

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Environment
import android.os.Vibrator
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.kircherelectronics.fsensor.filter.gyroscope.OrientationGyroscope
import kotlinx.android.synthetic.main.activity_main.*
import org.apache.commons.math3.complex.Quaternion
import java.io.*
import java.util.*
import kotlin.math.*

private const val l : Double = 10.0

class MainActivity : AppCompatActivity(), SensorEventListener {
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
    //private var backUpPosition = DoubleArray(2)

    private var saveData : String = ""
    //private var backUpSaveData : String = ""
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
    private var maxAccZ : Double = 0.0
    private var minAccZ : Double = 0.0
    private var k : Double = 0.445
    private var isAutoSampling : Boolean = false
    private var isRoundSampling : Boolean = false
    private var isGradientSampling : Boolean = false
    private var isAngleFixed : Boolean = false
    private var isUpPeak : Boolean = false
    private var isDownPeak : Boolean = false
    private var isStepFinished : Boolean = false
    private var isAutoPositionChange : Boolean = false
    private var nowSampling : Boolean = false
    //private var paused : Boolean = false

    private val period : Int = 5
    private var positionLog : Queue<String> = LinkedList()
    private var dataPosition : Queue<String> = LinkedList()
    private var dataMagx : Queue<String> = LinkedList()
    private var dataMagy : Queue<String> = LinkedList()
    private var dataMagz : Queue<String> = LinkedList()

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
        setContentView(R.layout.activity_main)

        checkFunction()
        init()

        positionChangeMode.setOnClickListener {
            if (positionChangeMode.isChecked) {
                isAutoPositionChange = true
                groupX.visibility = View.INVISIBLE
                groupY.visibility = View.INVISIBLE
                Toast.makeText(applicationContext, "방향에 따라 좌표가 자동으로 변합니다.", Toast.LENGTH_SHORT).show()
            } else {
                isAutoPositionChange = false
                groupX.visibility = View.VISIBLE
                groupY.visibility = View.VISIBLE
                Toast.makeText(applicationContext, "좌표의 변화를 수동 설정합니다.", Toast.LENGTH_SHORT).show()
            }
        }

        angleFixMode.setOnClickListener {
            isAngleFixed = angleFixMode.isChecked
        }

        btnGyroReset.setOnClickListener {
            orientationGyroscope.reset()
        }

        groupSamplingMode.setOnCheckedChangeListener { group, checkedId ->
            nowSampling = false
            if (autoSampling.isChecked) {
                isAutoSampling = true
                btnSamplingStart.text = "START RECORDING"
                roundSampling.visibility = View.VISIBLE
                roundSampling.isChecked = false
                gradientSampling.visibility = View.VISIBLE
                gradientSampling.isChecked = false
                positionChangeMode.visibility = View.INVISIBLE
                groupX.visibility = View.INVISIBLE
                groupY.visibility = View.INVISIBLE
                circleCenter.visibility = View.INVISIBLE
                //additionalBtn.visibility = View.VISIBLE
                edge.visibility = View.INVISIBLE
                circleCenter.visibility = View.INVISIBLE
                Toast.makeText(applicationContext, "PDR 샘플링 모드입니다.", Toast.LENGTH_SHORT).show()
            } else if (manualSampling.isChecked) {
                isAutoSampling = false
                isRoundSampling = false
                btnSamplingStart.text = "RECORD"
                roundSampling.visibility = View.INVISIBLE
                gradientSampling.visibility = View.INVISIBLE
                positionChangeMode.visibility = View.VISIBLE
                positionChangeMode.isChecked = false
                groupX.visibility = View.VISIBLE
                groupY.visibility = View.VISIBLE
                circleCenter.visibility = View.INVISIBLE
                edge.visibility = View.INVISIBLE
                //additionalBtn.visibility = View.INVISIBLE
                Toast.makeText(applicationContext, "Manual 샘플링 모드입니다.", Toast.LENGTH_SHORT).show()
            } else if (roundSampling.isChecked) {
                isRoundSampling = true
                circleCenter.visibility = View.VISIBLE
                isGradientSampling = false
                edge.visibility = View.INVISIBLE
                Toast.makeText(applicationContext, "Round 샘플링 모드입니다.", Toast.LENGTH_SHORT).show()
            } else if (gradientSampling.isChecked) {
                isGradientSampling = true
                edge.visibility = View.VISIBLE
                isRoundSampling = false
                circleCenter.visibility = View.INVISIBLE
                Toast.makeText(applicationContext, "Gradient 샘플링 모드입니다.", Toast.LENGTH_SHORT).show()
            }
        }
        btnUltimate.setOnClickListener {
            val intent = Intent(applicationContext, UltimateActivity::class.java)
            startActivity(intent)
            finish()
        }

        btnDeepLearning.setOnClickListener {
            val intent = Intent(applicationContext, DeepLearningActivity::class.java)
            startActivity(intent)
            finish()
        }

        btnSimultaneous.setOnClickListener {
            val intent = Intent(applicationContext, UltimatePocketActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun checkFunction() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)
                && ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                && ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                ActivityCompat.requestPermissions(this, arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE), 101)
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE), 101)
            }
        }
    }

    private fun init() {
        positionChangeMode.isChecked = false
        fixedX.isChecked = true
        fixedY.isChecked = true
        manualSampling.isChecked = true
        roundSampling.visibility = View.INVISIBLE
        gradientSampling.visibility = View.INVISIBLE
        edge.visibility = View.INVISIBLE
        circleCenter.visibility = View.INVISIBLE
        //additionalBtn.visibility = View.INVISIBLE
        positionXView.setText("0")
        positionYView.setText("0")

        var path = getExternalPath()
        val file = File(path)
        if (!file.exists()) file.mkdir()
    }

    private fun getExternalPath() : String {
        var sdPath = ""
        val ext = Environment.getExternalStorageState()
        sdPath = if (ext == Environment.MEDIA_MOUNTED) {
            Environment.getExternalStorageDirectory()
                .absolutePath + "/AdvancedMapCollector/"
        } else {
            "$filesDir/AdvancedMapCollector/"
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

    private fun writeTextFile(filename: String, contents: String) {
        try {
            //파일 output stream 생성
            val fos = FileOutputStream(getExternalPath() + filename, true)
            //파일쓰기
            val writer = BufferedWriter(OutputStreamWriter(fos))
            writer.write(contents)
            writer.flush()
            writer.close()
            fos.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
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

        if (isAutoSampling) {
            if (!nowSampling) {
                btnSamplingStart.text = "STOP RECORDING"
                nowSampling = true
                saveData = "${round(positionX)}\t${round(positionY)}\t${this.caliX}\t${this.caliY}\t${this.caliZ}\r\n"
//                backUpPosition[0] = positionX
//                backUpPosition[1] = positionY
//                backUpSaveData = saveData
                stepCount=0
                Toast.makeText(this, "지금부터 데이터를 기록합니다.", Toast.LENGTH_SHORT).show()
            } else {
                btnSamplingStart.text = "START RECORDING"
                nowSampling = false
                writeFile(if (fileName.text.toString().isEmpty()) "default${autoFileNum++}.txt" else fileName.text.toString(), saveData)
            }
        } else {
            val result = "${this.caliX}\t${this.caliY}\t${this.caliZ}\r\n"
            writeTextFile(if (fileName.text.toString().isEmpty()) "MAG.txt" else fileName.text.toString(), positionX.toString() + "\t")
            writeTextFile(if (fileName.text.toString().isEmpty()) "MAG.txt" else fileName.text.toString(), positionY.toString() + "\t")
            writeTextFile(if (fileName.text.toString().isEmpty()) "MAG.txt" else fileName.text.toString(), result)
            Toast.makeText(applicationContext, "수집 끝!", Toast.LENGTH_SHORT).show()
            if (!isAutoPositionChange) positionChange()
            else autoPosition()
            vibrator.vibrate(200)
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

    fun positionChange() {
        when (groupX.checkedRadioButtonId) {
            R.id.increaseX -> positionX++
            R.id.decreaseX -> if (positionX > 0) positionX--
            R.id.fixedX -> { }
        }
        when (groupY.checkedRadioButtonId) {
            R.id.increaseY -> positionY++
            R.id.decreaseY -> if (positionY > 0) positionY--
            R.id.fixedY -> { }
        }
        positionXView.setText(positionX.toString())
        positionYView.setText(positionY.toString())
    }

    private fun extractSaveData() {
        val extractPeriod = dataPosition.size / 6
        for (i in 0..5) {
            for (j in 0 until extractPeriod - 1) {
                dataPosition.poll()
                dataMagx.poll()
                dataMagy.poll()
                dataMagz.poll()
            }
            var posi = dataPosition.poll().split("\t")
            if (isRoundSampling) {
                val alpha = alphaView.text.toString().toDouble()
                val beta = betaView.text.toString().toDouble()
                val r = rView.text.toString().toDouble()
                val theta = ((i+1)+(6*stepCount))*l/r

                var nextX = round((posi[0].toDouble()-alpha)*cos(theta) + (posi[1].toDouble()-beta)*sin(theta) + alpha)
                var nextY = round(-(posi[0].toDouble()-alpha)*sin(theta) + (posi[1].toDouble()-beta)*cos(theta) + beta)
                saveData += nextX.toString() + "\t" + nextY + "\t" + dataMagx.poll() + "\t" + dataMagy.poll() + "\t" + dataMagz.poll() + "\r\n"
                //((posi[0].toDouble()-alpha)*cos((i+1)*l* PI/(r*180))+(posi[1].toDouble()-beta)*sin((i+1)*l* PI/(r*180))+alpha).toString() +"\t"+ (-(posi[0].toDouble()-alpha)*sin((i+1)*l* PI/(r*180))+(posi[1].toDouble()-beta)*cos((i+1)*l* PI/(r*180)) +beta).toString() + "\t" + data_magx.poll() + "\t" + data_magy.poll() + "\t" + data_magz.poll() + "\r\n"
            } else if (isGradientSampling) {
                var r = 0.0
                if (isAngleFixed) {
                    r = angleView.text.toString().toDouble()
                } else {
                    r = gyroView.text.toString().toDouble()
                }
                var nextX = round(posi[0].toDouble() + sin(Math.toRadians(r)) * (i+1))
                var nextY = round(posi[1].toDouble() + cos(Math.toRadians(r)) * (i+1))
                saveData += nextX.toString() + "\t" + nextY + "\t" + dataMagx.poll() + "\t" + dataMagy.poll() + "\t" + dataMagz.poll() + "\r\n"
            } else {
                saveData += posi[0] + "\t" + posi[1] + "\t" + dataMagx.poll() + "\t" + dataMagy.poll() + "\t" + dataMagz.poll() + "\r\n"
            }
        }
        dataPosition.clear()
        dataMagx.clear()
        dataMagy.clear()
        dataMagz.clear()
    }

    private fun autoPosition() {
        val gyroAngle = gyroView.text.toString().toInt()
        positionX = positionXView.text.toString().toDouble()
        positionY = positionYView.text.toString().toDouble()

        if (isRoundSampling) {
//            val alpha = alphaView.text.toString().toDouble()
//            val beta = betaView.text.toString().toDouble()
//            val r = rView.text.toString().toDouble()
//
//            positionX = (positionX-alpha)*cos(6*l/r)+(positionY-beta)*sin(6*l/r)+alpha
//            positionY = -(positionX-alpha)*sin(6*l/r)+(positionY-beta)*cos(6*l/r)+beta
        } else if (isGradientSampling) {
            var r = 0.0
            if (isAngleFixed) {
                r = angleView.text.toString().toDouble()
            } else {
                r = gyroView.text.toString().toDouble()
            }
            positionX += sin(Math.toRadians(r)) * 6
            positionY += cos(Math.toRadians(r)) * 6
        } else {
            if (gyroAngle > 315 || gyroAngle < 46) positionY++
            else if (gyroAngle in 46..135) positionX++
            else if (gyroAngle in 136..225) {
                positionY = if (positionY > 0) positionY - 1 else 0.0
            } else {
                positionX = if (positionX > 0) positionX - 1 else 0.0
            }
        }
        positionXView.setText(positionX.toString() + "")
        positionYView.setText(positionY.toString() + "")
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
                        }

                        angleA = mAzimuth - (Math.toDegrees(fusedOrientation[0].toDouble()) + 360) % 360
                        caliX = -1 * sqrt(magnitudeOfMagnetic.pow(2) - mRot[2].pow(2)) * sin(angleA * PI / 180)
                        caliY = sqrt(magnitudeOfMagnetic.pow(2) - mRot[2].pow(2)) * cos(angleA * PI / 180)
                        caliZ = mRot[2].toDouble()

                        magXView.text = "X: " + caliX.toString().substring(0..if(caliX.toString().length >= 8) 7 else caliX.toString().length-1)
                        magYView.text = "Y: " + caliY.toString().substring(0..if(caliY.toString().length >= 8) 7 else caliY.toString().length-1)
                        magZView.text = "Z: " + caliZ.toString().substring(0..if(caliZ.toString().length >= 8) 7 else caliZ.toString().length-1)
                    }
                }
                Sensor.TYPE_GYROSCOPE -> {
                    System.arraycopy(event.values, 0, rotation, 0, event.values.size)
                    if (!orientationGyroscope.isBaseOrientationSet)
                        orientationGyroscope.setBaseOrientation(Quaternion.IDENTITY)
                    else
                        fusedOrientation = orientationGyroscope.calculateOrientation(rotation, event.timestamp)

                    gyroView.text = Math.round((((Math.toDegrees(fusedOrientation[0].toDouble()) + 360) % 360)*100)/100.0).toString()
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
                    if (isAutoSampling && nowSampling /* && !paused*/) {
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

                            if (time_peak2peak > 150 && time_peak2peak < 400 && maxAccZ < 5 && minAccZ > -4) {
                                vibrator.vibrate(80)
                                stepCountView.text = "  step count : ${stepCount.toString()}"
                                // EX weinberg approach
                                //lastStepLength = k * Math.sqrt(Math.sqrt(maxAccZ - minAccZ))
                                //stepLengthMovingAverage.newData(lastStepLength)

                                extractSaveData()
                                autoPosition()

                                stepCount++
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
    }

    override fun onPause() {
        super.onPause()
        mSensorManager.unregisterListener(this)
    }
}