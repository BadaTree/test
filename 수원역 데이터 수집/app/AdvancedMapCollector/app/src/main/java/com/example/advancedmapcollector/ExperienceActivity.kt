package com.example.advancedmapcollector

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import android.widget.*
import androidx.core.app.ActivityCompat
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.kircherelectronics.fsensor.filter.gyroscope.OrientationGyroscope
import kotlinx.android.synthetic.main.activity_experience.*
import org.apache.commons.math3.complex.Quaternion
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.*

class ExperienceActivity : AppCompatActivity(), SensorEventListener {
    private val mSensorManager by lazy {
        getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    private val vibrator by lazy {
        getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private val accXMovingAverage : MovingAverage = MovingAverage(10)
    private val accYMovingAverage : MovingAverage = MovingAverage(10)
    private val accZMovingAverage : MovingAverage = MovingAverage(10)

    private var quaternion = FloatArray(4)
    private var magMatrix = FloatArray(3)
    private var accMatrix = FloatArray(3)
    private var roVecMatrix = FloatArray(5)
    private var backUpPosition = DoubleArray(2)

    private var saveData : String = ""
    private var backUpSaveData : String = ""
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
    //----------------------------------------------------


    //---------------센서 안정화 체크 변수--------------------
    private var isSensorStabled : Boolean = false
    private var magStableCount : Int = 50
    private var accStableCount : Int = 50
    //----------------------------------------------------

    private val XRANGE = 200
    val DATA_RANGE = 180
    val AXIS_VALUE = 3F
    var xVal = ArrayList<Entry>()
    var yVal = ArrayList<Entry>()
    var zVal = ArrayList<Entry>()
    var setXcomp = LineDataSet(xVal, "Magnetic field X")
    var setYcomp = LineDataSet(yVal, "Magnetic field Y")
    var setZcomp = LineDataSet(zVal, "Magnetic field Z")
    var xVals = ArrayList<String>()
    var yVals = ArrayList<String>()
    var zVals = ArrayList<String>()
    var xDataSets = ArrayList<ILineDataSet>()
    var yDataSets = ArrayList<ILineDataSet>()
    var zDataSets = ArrayList<ILineDataSet>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_experience)

        checkFunction()
        init()
        initChart()

        val thread = MyThread1()
        thread.isDaemon = true
        thread.start()

        angleFixMode.setOnClickListener { isAngleFixed = angleFixMode.isChecked }

        btnGyroReset.setOnClickListener { orientationGyroscope.reset() }
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
        positionXView.setText("0")
        positionYView.setText("0")
        stepCountView.text = "  step count : ${stepCount}"

        var path = getExternalPath()
        val file = File(path)
        if (!file.exists()) file.mkdir()
    }

    private fun initChart() {
        chartMagnetic.axisLeft.setAxisMaxValue(AXIS_VALUE)
        chartMagnetic.axisLeft.setAxisMinValue(-AXIS_VALUE)
        chartMagnetic.axisRight.setAxisMaxValue(AXIS_VALUE)
        chartMagnetic.axisRight.setAxisMinValue(-AXIS_VALUE)
        chartMagnetic.axisLeft.textColor = Color.WHITE
        chartMagnetic.axisRight.textColor = Color.WHITE
        chartMagnetic.legend.textColor = Color.WHITE
        setXcomp.color = Color.RED
        setXcomp.lineWidth = 2F
        setXcomp.setDrawValues(false)
        setXcomp.setDrawCircles(false)
        setXcomp.setDrawCubic(true)
        setXcomp.axisDependency = YAxis.AxisDependency.RIGHT
        xDataSets.add(setXcomp)

        setYcomp.color = Color.BLUE
        setYcomp.lineWidth = 2F
        setYcomp.setDrawValues(false)
        setYcomp.setDrawCircles(false)
        setYcomp.setDrawCubic(true)
        setYcomp.axisDependency = YAxis.AxisDependency.RIGHT
        xDataSets.add(setYcomp)

        setZcomp.color = Color.GREEN
        setZcomp.lineWidth = 2F
        setZcomp.setDrawValues(false)
        setZcomp.setDrawCircles(false)
        setZcomp.setDrawCubic(true)
        setZcomp.axisDependency = YAxis.AxisDependency.RIGHT
        xDataSets.add(setZcomp)

        for (i in 0 until XRANGE) {
            xVals.add("")
            yVals.add("")
            zVals.add("")
        }
        chartMagnetic.data = LineData(xVals, xDataSets)
        chartMagnetic.invalidate()
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

        if (!nowSampling) {
            btnSamplingStart.text = "STOP RECORDING"
            nowSampling = true
            saveData = "${round(positionX)}\t${round(positionY)}\t${this.caliX}\t${this.caliY}\t${this.caliZ}\r\n"
            backUpPosition[0] = positionX
            backUpPosition[1] = positionY
            backUpSaveData = saveData
            stepCount=0
            stepCountView.text = "  step count : ${stepCount}"
            if (isRemember && !isRestore) {
                backUpHeading = arrayListOf(if (isAngleFixed) angleView.text.toString().toDouble() else gyroView.text.toString().toDouble())
            }
            Toast.makeText(this, "지금부터 데이터를 기록합니다.", Toast.LENGTH_SHORT).show()
        } else {
            btnSamplingStart.text = "START RECORDING"
            nowSampling = false
            writeFile(if (fileName.text.toString().isEmpty()) "default${autoFileNum++}.txt" else "${fileName.text}.txt", saveData)
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
            saveData += round(nextX).toString() + "\t" + round(nextY) + "\t" + dataMagx.poll() + "\t" + dataMagy.poll() + "\t" + dataMagz.poll() + "\r\n"

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

    private fun sensorReady(event: SensorEvent) : Boolean {
        if (isSensorStabled)
            return true

        when(event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                accXMovingAverage.newData(event.values[0].toDouble())
                accYMovingAverage.newData(event.values[1].toDouble())
                accZMovingAverage.newData(event.values[2].toDouble())

                accStableCount += if (accXMovingAverage.getAvg() in -0.3..0.3
                    && accYMovingAverage.getAvg() in -0.3..0.3
                    && accZMovingAverage.getAvg() in -0.3..0.3) -1 else 1
                accStableCount = if (accStableCount > 50) 50 else accStableCount
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                if (magStableCount > 0)
                    magStableCount--
            }
        }

        isSensorStabled = accStableCount<0 && magStableCount==0

        return isSensorStabled
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if ((event?:false)?.let {sensorReady(event!!)}) {
            when(event!!.sensor.type) {
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
                    if (nowSampling && !paused) {
                        timeStamp = System.currentTimeMillis().toString().substring(6).toDouble()

                        quaYaw = atan2(2.0 * (quaternion[3] * quaternion[0] + quaternion[1] * quaternion[2]), 1 - 2.0 * (quaternion[0] * quaternion[0] + quaternion[1] * quaternion[1])).toFloat()
                        quaPitch = (-atan2(2 * (quaternion[0] * quaternion[1] + quaternion[3] * quaternion[2]).toDouble(), quaternion[3] * quaternion[3] + quaternion[0] * quaternion[0] - quaternion[1] * quaternion[1] - (quaternion[2] * quaternion[2]).toDouble())).toFloat()
                        quaRoll = asin(2 * (quaternion[0] * quaternion[2] - quaternion[3] * quaternion[1]).toDouble()).toFloat()

                        accXMovingAverage.newData(event.values[0].toDouble())
                        accYMovingAverage.newData(event.values[1].toDouble())
                        accZMovingAverage.newData(event.values[2].toDouble())

                        val accDataForStepDetection = axisTransform('z', accXMovingAverage.getAvg().toFloat(), accYMovingAverage.getAvg().toFloat(), accZMovingAverage.getAvg().toFloat()).toDouble()

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

                            if (time_peak2peak > 150 && time_peak2peak < 500 /*&& maxAccZ < 5 && minAccZ > -4*/) {
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

    }

    override fun onPause() {
        super.onPause()
        mSensorManager.unregisterListener(this)
    }

    private fun chartXUpdate(x : String, y : String, z : String) {
        if (xVal.size > DATA_RANGE) {
            xVal.removeAt(0)
            for (i in 0 until DATA_RANGE) {
                xVal[i].xIndex = i
            }
        }
        xVal.add(Entry(x.toFloat(), xVal.size))
        setXcomp.notifyDataSetChanged()

        if (yVal.size > DATA_RANGE) {
            yVal.removeAt(0)
            for (i in 0 until DATA_RANGE) {
                yVal[i].xIndex = i
            }
        }
        yVal.add(Entry(y.toFloat(), yVal.size))
        setYcomp.notifyDataSetChanged()

        if (zVal.size > DATA_RANGE) {
            zVal.removeAt(0)
            for (i in 0 until DATA_RANGE) {
                zVal[i].xIndex = i
            }
        }
        zVal.add(Entry(z.toFloat(), zVal.size))
        setZcomp.notifyDataSetChanged()

        chartMagnetic.notifyDataSetChanged()
        chartMagnetic.invalidate()
    }

    var handler: Handler = object : Handler(Looper.myLooper()) {
        override fun handleMessage(msg: Message) {
            val magx = caliX
            val magy = caliZ
            val magz = caliY
            if (msg.what == 0) {
                val maximum = max(max(magx, magy), magz) + 15
                val minimum = min(min(magx, magy), magz) - 15
                chartMagnetic.axisRight.setAxisMaxValue( ceil(maximum / 10).toFloat() * 10 )
                chartMagnetic.axisRight.setAxisMinValue( floor(minimum / 10).toFloat() * 10 )
                chartMagnetic.axisLeft.setAxisMaxValue( ceil(maximum / 10).toFloat() * 10 )
                chartMagnetic.axisLeft.setAxisMinValue( floor(minimum / 10).toFloat() * 10 )
                chartXUpdate(magx.toString(), magy.toString(), magz.toString())
            }
        }
    }

    inner class MyThread1 : Thread() {
        override fun run() {
            while (true) {
                handler.sendEmptyMessage(0)
                try {
                    sleep(50)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

}