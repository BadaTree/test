package com.example.CollectorForElevator

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Vibrator
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.kircherelectronics.fsensor.filter.gyroscope.OrientationGyroscope
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main.angleFixMode
import kotlinx.android.synthetic.main.activity_main.angleView
import kotlinx.android.synthetic.main.activity_main.btnGyroReset
import kotlinx.android.synthetic.main.activity_main.btnSamplingStart
import kotlinx.android.synthetic.main.activity_main.fileName
import kotlinx.android.synthetic.main.activity_main.gyroView
import kotlinx.android.synthetic.main.activity_main.magXView
import kotlinx.android.synthetic.main.activity_main.magYView
import kotlinx.android.synthetic.main.activity_main.magZView
import kotlinx.android.synthetic.main.activity_main.positionXView
import kotlinx.android.synthetic.main.activity_main.positionYView
import kotlinx.android.synthetic.main.activity_main.stepCountView
import java.io.*
import java.util.*
import kotlin.math.*


private const val l : Double = 10.0

class MainActivity : AppCompatActivity(), SensorEventListener{
    private val mSensorManager by lazy {
        getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    private val vibrator by lazy {
        getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    private val accZMovingAverage : MovingAverage = MovingAverage(10)

    private var pressure = FloatArray(4)
    private var quaternion = FloatArray(4)
    private var magMatrix = FloatArray(3)
    private var magnitude_of_magnetic = 0.0
    private var accMatrix = FloatArray(3)
    private var roVecMatrix = FloatArray(5)
    private var gameRotationVector = FloatArray(5)
    private var startingTime = System.currentTimeMillis()
    private var data: String = ""
    private var path: String? = null
    private var permission_list = arrayOf(
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    private var btnMode = 0
    private var saveData : String = ""
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
    private var positionX : Double = 0.0
    private var positionY : Double = 0.0
    private var stepCount : Int = 0
    private var maxAccZ : Double = 0.0
    private var minAccZ : Double = 0.0
    private var isAutoSampling : Boolean = false
    private var isRoundSampling : Boolean = false
    private var isGradientSampling : Boolean = false
    private var isAngleFixed : Boolean = false
    private var isUpPeak : Boolean = false
    private var isDownPeak : Boolean = false
    private var isStepFinished : Boolean = false
    private var nowSampling : Boolean = false

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
    private var offsetX : Double= 0.0
    private var offsetY : Double= 0.0
    private var uncali_magnetic = FloatArray(6)



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkFunction()
        init()

        angleFixMode.setOnClickListener {
            isAngleFixed = angleFixMode.isChecked
        }

        btnGyroReset.setOnClickListener { orientationGyroscope.reset() }
///////////////////////////////////
        btnSamplingStart.setOnClickListener{
            btnMode = 1
            startingTime = System.currentTimeMillis()
            data = ""
            btnSamplingStart.isEnabled = false
            btnSamplingDepart.isEnabled = true
        }
        btnSamplingDepart.setOnClickListener{
            btnMode = 2
            data += "----------------출발----------------\r\n"
            btnSamplingDepart.isEnabled = false
            btnSamplingArrival.isEnabled = true
        }

        btnSamplingArrival.setOnClickListener {
            btnMode = 3
            data += "----------------도착----------------\r\n"
            btnSamplingArrival.isEnabled = false
            btnSamplingStop.isEnabled = true
        }

        btnSamplingStop.setOnClickListener {
            btnMode = 0
            val filename = fileName.text.toString() + ".txt"
            checkPermission()

            path = Environment.getExternalStorageDirectory().absolutePath + "/android/data/" + packageName

            writeTextFile(filename, data)
            val file = File(path)
            if(!file.exists()){
                file.mkdir()
            }
            saveToExternalStorage(data, filename)
            btnSamplingStop.isEnabled = false
            btnSamplingStart.isEnabled = true
            println(data)
        }


        groupSamplingMode.setOnCheckedChangeListener { group, checkedId ->
            nowSampling = false
            if (manualSampling.isChecked) {
                isAutoSampling = false
                isRoundSampling = false
                btnSamplingStart.text = "RECORD"
                circleCenter.visibility = View.INVISIBLE
                edge.visibility = View.INVISIBLE
            }
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
        manualSampling.isChecked = true
        edge.visibility = View.INVISIBLE
        circleCenter.visibility = View.INVISIBLE
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
                .absolutePath + "/CollectorForElevator/"
        } else {
            "$filesDir/CollectorForElevator/"
        }
        return sdPath
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

    private fun checkPermission(){
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M){ return }
        for (permission: String in permission_list){
            val chk = checkCallingOrSelfPermission(permission)
            if (chk == PackageManager.PERMISSION_DENIED){
                requestPermissions(permission_list, 0)
                break
            }
        }
    }
    private fun getAppDataFileFromExternalStorage(filename: String) : File{
        val dir = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT){
            getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        }else{
            File(Environment.getExternalStorageDirectory().absolutePath+"/Documents")
        }

        dir?.mkdirs()
        return File("${dir!!.absolutePath}${File.separator}${filename}")
    }
    private fun saveToExternalStorage(text: String, filename: String){
        val fileOutputStream = FileOutputStream(getAppDataFileFromExternalStorage(filename))

        fileOutputStream.write(text.toString().toByteArray())
        fileOutputStream.close()
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
        var accDataForStepDetection: Double
        if (event != null) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> accMatrix = event.values.clone()
                Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> {
                    uncali_magnetic = event.values.clone()
                    offsetX = round(uncali_magnetic[3].toDouble() * 10.0) / 10.0
                    offsetY = round(uncali_magnetic[4].toDouble() * 10.0) / 10.0
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    magMatrix = event.values.clone()
                    magnitude_of_magnetic = sqrt(magMatrix[0].pow(2) + magMatrix[1].pow(2) + magMatrix[2].pow(2) ).toDouble()
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

                        angleA = (mAzimuth - Math.toDegrees(fusedOrientation[2].toDouble()) + 360) % 360
//                        angleA = (round(Math.toDegrees(atan2(By-hardiron_offset[1], Bx-hardiron_offset[0])))- 90.0 - Math.toDegrees(fusedOrientation[0].toDouble()) + 360) % 360
                        caliX = -1 * sqrt(magnitude_of_magnetic.pow(2) - mRot[2].pow(2)) * sin(angleA * PI / 180)
//                        Log.d("hard iron", magnitude_of_magnetic.pow(2).toString())
                        caliY = sqrt(magnitude_of_magnetic.pow(2) - mRot[2].pow(2)) * cos(angleA * PI / 180)
                        caliZ = mRot[2].toDouble()
                        magXView.text = "X: " + (Math.round(caliX)).toString()
                        magYView.text = "Y: " + (Math.round(caliY)).toString()
                        magZView.text = "Z: " + (Math.round(caliZ)).toString()
                        AzimuthView.text = "M: " + Math.round(mAzimuth).toString()
                    }   ////////////////////////////////

                }

                Sensor.TYPE_GYROSCOPE -> {
                    System.arraycopy(event.values, 0, rotation, 0, event.values.size)
                    if (!orientationGyroscope.isBaseOrientationSet)
                        orientationGyroscope.setBaseOrientation(org.apache.commons.math3.complex.Quaternion.IDENTITY)
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
                        quaternion[0] = roVecMatrix[3]
                        quaternion[1] = roVecMatrix[0]
                        quaternion[2] = roVecMatrix[1]
                        quaternion[3] = roVecMatrix[2]
                    }
                }
                Sensor.TYPE_LINEAR_ACCELERATION -> {
                    if (isAutoSampling && nowSampling /* && !paused*/) {
                        timeStamp =
                            System.currentTimeMillis().toString().substring(6).toDouble()

                        quaYaw = atan2(
                            2.0 * (quaternion[3] * quaternion[0] + quaternion[1] * quaternion[2]),
                            1 - 2.0 * (quaternion[0] * quaternion[0] + quaternion[1] * quaternion[1])
                        ).toFloat()
                        quaPitch = (-atan2(
                            2 * (quaternion[0] * quaternion[1] + quaternion[3] * quaternion[2]).toDouble(),
                            quaternion[3] * quaternion[3] + quaternion[0] * quaternion[0] - quaternion[1] * quaternion[1] - (quaternion[2] * quaternion[2]).toDouble()
                        )).toFloat()
                        quaRoll =
                            asin(2 * (quaternion[0] * quaternion[2] - quaternion[3] * quaternion[1]).toDouble()).toFloat()

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
                    data += (System.currentTimeMillis() - startingTime)
                    data += "\t"
                    data += caliX.toString()
                    data += "\t"
                    data += caliY.toString()
                    data += "\t"
                    data += caliZ.toString()
                    data += "\t"
                    data += pressure[0].toString()
                    data += "\r\n"

                }
                Sensor.TYPE_LIGHT -> {
                    pressure = event.values.clone()
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
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED), SensorManager.SENSOR_DELAY_GAME)
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT), SensorManager.SENSOR_DELAY_GAME)
    }

    override fun onPause() {
        super.onPause()
        mSensorManager.unregisterListener(this)
    }

}