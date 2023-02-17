package com.example.advancedmapcollector

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import android.widget.*
import com.example.advancedmapcollector.net.BluetoothServer
import com.kircherelectronics.fsensor.filter.gyroscope.OrientationGyroscope
import kotlinx.android.synthetic.main.activity_timer.*
import org.apache.commons.math3.complex.Quaternion
import java.io.*
import java.util.*
import kotlin.math.*

class TimerActivity : AppCompatActivity(), SensorEventListener {
    private val mSensorManager by lazy {
        getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    private val vibrator by lazy {
        getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private var quaternion = FloatArray(4)
    private var magMatrix = FloatArray(3)
    private var accMatrix = FloatArray(3)
    private var roVecMatrix = FloatArray(5)

    private var saveData : String = ""
    private var magnitudeOfMagnetic : Double = 0.0
    private var autoFileNum : Int = 0
    private var stepCount : Int = 0
    private var nowSampling : Boolean = false

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
        setContentView(R.layout.activity_timer)

        init()

        btnGyroReset.setOnClickListener { orientationGyroscope.reset() }
    }

    private fun init() {
        var path = getExternalPath()
        val file = File(path)
        if (!file.exists()) file.mkdir()

        val thread = MyThread1()
        thread.isDaemon = true
        thread.start()
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
        dataMagx.clear()
        dataMagy.clear()
        dataMagz.clear()
    }

    fun onBtnSamplingStartClicked(v: View?) {
            if (!nowSampling) {
                btnSamplingStart.text = "STOP RECORDING"
                nowSampling = true
                saveData = "${this.caliX}\t${this.caliY}\t${this.caliZ}\r\n"
                stepCount = 0
                Toast.makeText(this, "지금부터 데이터를 기록합니다.", Toast.LENGTH_SHORT).show()
            } else {
                btnSamplingStart.text = "START RECORDING"
                nowSampling = false
                writeFile(if (fileName.text.toString().isEmpty()) "default${autoFileNum++}.txt" else fileName.text.toString(), saveData)
            }

    }

    private fun extractSaveData() {
        saveData +=  dataMagx.poll() + "\t" + dataMagy.poll() + "\t" + dataMagz.poll() + "\r\n"
        vibrator.vibrate(50)

        dataMagx.clear()
        dataMagy.clear()
        dataMagz.clear()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

    }

    override fun onSensorChanged(event: SensorEvent?) {
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

                        angleA = (mAzimuth.toDouble() + 360) % 360

                        magXView.text = "X: " + caliX.toString().substring(0..if(caliX.toString().length >= 8) 7 else caliX.toString().length-1)
                        magYView.text = "Y: " + caliY.toString().substring(0..if(caliY.toString().length >= 8) 7 else caliY.toString().length-1)
                        magZView.text = "Z: " + caliZ.toString().substring(0..if(caliZ.toString().length >= 8) 7 else caliZ.toString().length-1)
                        saveCountView.text = "  save count : ${stepCount}"
                    }
                }
                Sensor.TYPE_GYROSCOPE -> {
                    System.arraycopy(event.values, 0, rotation, 0, event.values.size)
                    if (!orientationGyroscope.isBaseOrientationSet)
                        orientationGyroscope.setBaseOrientation(Quaternion.IDENTITY)
                    else
                        fusedOrientation = orientationGyroscope.calculateOrientation(rotation, event.timestamp)

                    gyroView.text = "${round((((Math.toDegrees(fusedOrientation[0].toDouble()) + 360) % 360)*100)/100.0)}"
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

            }
        }
    }

    override fun onResume() {
        super.onResume()
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME)
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_GAME)
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_GAME)
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_GAME)
    }

    override fun onPause() {
        super.onPause()
        mSensorManager.unregisterListener(this)
    }

    inner class MyThread1 : Thread() {
        override fun run() {
            while (true) {
                if (nowSampling) {
                    dataMagx.add(caliX.toString())
                    dataMagy.add(caliY.toString())
                    dataMagz.add(caliZ.toString())

                    extractSaveData()

                    stepCount++
                }
                try {
                    sleep(500)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}