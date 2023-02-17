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
import android.os.*
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import android.widget.*
import androidx.annotation.RequiresApi
import com.example.advancedmapcollector.net.BTConstant.BT_REQUEST_ENABLE
import com.example.advancedmapcollector.net.BluetoothClient
import com.example.advancedmapcollector.net.SocketListener
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.TedPermission
import com.kircherelectronics.fsensor.filter.gyroscope.OrientationGyroscope
import kotlinx.android.synthetic.main.activity_ultimate_pocket.*
import org.apache.commons.math3.complex.Quaternion
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.*

class UltimatePocketActivity : AppCompatActivity(), SensorEventListener {
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
    private var gamerotationVector = FloatArray(5)
    private var gamerotationVector_for_noncali = FloatArray(5)
    private var backUpPosition = DoubleArray(2)

    private var saveData : String = ""
    private var backUpSaveData : String = ""
    private var stepLength : String = ""
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
    private var isAngleFixed : Boolean = true
    private var isTestMode : Boolean = false
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
    private var dataMagx2 : Queue<String> = LinkedList()
    private var dataMagy2 : Queue<String> = LinkedList()
    private var dataMagz2 : Queue<String> = LinkedList()

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
    private var nonCaliX : Double= 0.0
    private var nonCaliY : Double= 0.0
    private var nonCaliZ : Double= 0.0
    //----------------------------------------------------


    //---------------센서 안정화 체크 변수--------------------
    private var isSensorStabled : Boolean = false
    private var magStableCount : Int = 50
    private var accStableCount : Int = 50
    //----------------------------------------------------

    //----------------블루투스-------------------
    private var handler: Handler = Handler()
    private var sbLog = StringBuilder()
    private var btClient: BluetoothClient = BluetoothClient()

    private lateinit var svLogView: ScrollView
    private lateinit var tvLogView: TextView
    private lateinit var etMessage: EditText

    private var isSet = false
    private var isStartOrStop = false
    private var isStep = false

    private var receiveMSG = arrayOf("")
    //------------------------------------------


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ultimate_pocket)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkPermission()
        }

        AppControllerCl.Instance.init(this, btClient)

        initUI()
        setListener()
        init()

        btClient.setOnSocketListener(mOnSocketListener)

        angleFixMode.isChecked = true

        angleFixMode.setOnClickListener {
            isAngleFixed = angleFixMode.isChecked
            isTestMode = !isAngleFixed
            testMode.isChecked = !isAngleFixed
        }
        testMode.setOnClickListener {
            isTestMode = testMode.isChecked
            isAngleFixed = !isTestMode
            angleFixMode.isChecked = !isTestMode
        }

        btnGyroReset.setOnClickListener {
            orientationGyroscope.reset()

            mSensorManager.unregisterListener(this)

            mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME)
            mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_GAME)
            mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_GAME)
            mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_GAME)
            mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_GAME)
        }
    }

    private fun initUI() {
        svLogView = findViewById(R.id.svLogView)
        tvLogView = findViewById(R.id.tvLogView)
        etMessage = findViewById(R.id.etMessage)
    }

    private fun setListener() {
        findViewById<Button>(R.id.btnScan).setOnClickListener {
            ScanActivity.startForResult(this, 102)
        }

        findViewById<Button>(R.id.btnDisconnect).setOnClickListener {
            btClient.disconnectFromServer()
        }

        findViewById<Button>(R.id.btnSendData).setOnClickListener {
            if (etMessage.text.toString().isNotEmpty()) {
                btClient.sendData(etMessage.text.toString())
                //etCommand.setText("")
            }
        }
    }

    private fun log(message: String) {
        val tmp = message.trimIndent()
        sbLog.append(tmp + "\n")
        handler.post {
            tvLogView.text = sbLog.toString()
            svLogView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    private val mOnSocketListener: SocketListener = object : SocketListener {
        override fun onConnect() {
            log("Connect!\n")
        }

        override fun onDisconnect() {
            log("Disconnect!\n")
        }

        override fun onError(e: Exception?) {
            e?.let { log(e.toString() + "\n") }
        }

        override fun onReceive(msg: String?) {
            msg?.let { log("Receive : $it\n") }

            if (msg.isNullOrEmpty()){
                return
            }

            val testMsg = msg.split("\t")
            for (v in testMsg) {
                Log.d("testmsg22222", v)
            }
            when(testMsg[0]) {
                "SET" ->  {
                    isSet = true
                    receiveMSG = testMsg.toTypedArray()
                }
                "START" -> isStartOrStop = true
                "STOP" -> isStartOrStop = true
                "STEP" -> {
                    isStep = true
                    if (isTestMode) {
                        receiveMSG = testMsg.toTypedArray()
                    }
                }
            }
        }

        override fun onSend(msg: String?) {
            msg?.let { log("Send : $it\n") }
        }

        override fun onLogPrint(msg: String?) {
            msg?.let { log("$it\n") }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            BT_REQUEST_ENABLE -> if (resultCode == Activity.RESULT_OK) {
                Toast.makeText(applicationContext, "블루투스 활성화", Toast.LENGTH_LONG).show()
            } else if (resultCode == Activity.RESULT_CANCELED) {
                Toast.makeText(applicationContext, "취소", Toast.LENGTH_LONG).show()
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun checkPermission() {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
        )

        for (permission in permissions) {
            val chk = checkCallingOrSelfPermission(Manifest.permission.WRITE_CONTACTS)
            if (chk == PackageManager.PERMISSION_DENIED) {
                requestPermissions(permissions, 0)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 0) {
            for (element in grantResults) {
                if (element == PackageManager.PERMISSION_GRANTED) {
                } else {
                    TedPermission(this)
                        .setPermissionListener(object : PermissionListener {
                            override fun onPermissionGranted() {

                            }

                            override fun onPermissionDenied(deniedPermissions: java.util.ArrayList<String?>) {

                            }
                        })
                        .setDeniedMessage("You have permission to set up.")
                        .setPermissions(
                            Manifest.permission.BLUETOOTH,
                            Manifest.permission.BLUETOOTH_ADMIN
                        )
                        .setGotoSettingButton(true)
                        .check();
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        AppController.Instance.bluetoothOff()
    }
    //-------------------------------------------------------------------------------------------------

    private fun init() {
//        Glide.with(this).load(R.raw.whiteloading).into(loadingView)

        positionXView.setText("0")
        positionYView.setText("0")
        stepCountView.text = "  step count : $stepCount"

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
        dataMagx2.clear()
        dataMagy2.clear()
        dataMagz2.clear()
    }

    private fun axisTransform(axis : Char, rawDataX : Float, rawDataY : Float, rawDataZ : Float) : Float {
        return when(axis) {
            'x' -> { cos(-quaYaw) * cos(-quaRoll) * rawDataX+ (cos(-quaYaw) * sin(-quaRoll) * sin(quaPitch) - sin(-quaYaw) * cos(quaPitch)) * rawDataY+ (cos(-quaYaw) * sin(-quaRoll) * cos(quaPitch) + sin(-quaYaw) * sin(quaPitch)) * rawDataZ }
            'y' -> { sin(-quaYaw) * cos(-quaRoll) * rawDataX+ (sin(-quaYaw) * sin(-quaRoll) * sin(quaPitch) + cos(-quaYaw) * cos(quaPitch)) * rawDataY+ (sin(-quaYaw) * sin(-quaRoll) * cos(quaPitch) - cos(-quaYaw) * sin(quaPitch)) * rawDataZ }
            'z' -> { -sin(quaRoll) * rawDataX+ (cos(quaRoll) * sin(-quaPitch)) * rawDataY+ (cos(quaRoll) * cos(-quaPitch)) * rawDataZ }
            else -> -966.966966f
        }
    }

    private fun autoPosition(posiX : Double, posiY : Double) {
        positionX = posiX
        positionY = posiY
        positionXView.setText(posiX.toString())
        positionYView.setText(posiY.toString())
    }

    private fun extractSaveData() {
        val extractPeriod = dataPosition.size / 6
        var theta = 0.0
        var nextX = 0.0
        var nextY = 0.0

        if (isTestMode) {
            var posi = dataPosition.poll().split("\t")
            nextX = posi[0].toDouble() + sin(Math.toRadians(theta)) * (10 * stepLength.toDouble())
            nextY = posi[1].toDouble() + cos(Math.toRadians(theta)) * (10 * stepLength.toDouble())
            saveData += nextX.toString() + "\t" + nextY.toString() + "\t" + dataMagx.poll() + "\t" + dataMagy.poll() + "\t" + dataMagz.poll() + "\t" + dataMagx2.poll() + "\t" + dataMagy2.poll() + "\t" + dataMagz2.poll() + "\t" + gyroView.text.toString() + "\t" + stepLength + "\r\n"

        } else {
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
                        theta =
                            backUpHeading[if ((i + 1) + (6 * stepCount) >= backUpHeading.size) backUpHeading.size - 1 else (i + 1) + (6 * stepCount)]
                    } else {
                        backUpHeading.add(theta)
                    }
                }
                nextX = posi[0].toDouble() + sin(Math.toRadians(theta)) * (i + 1)
                nextY = posi[1].toDouble() + cos(Math.toRadians(theta)) * (i + 1)
                saveData += round(nextX).toString() + "\t" + round(nextY) + "\t" + dataMagx.poll() + "\t" + dataMagy.poll() + "\t" + dataMagz.poll() + "\r\n"
            }
        }


        dataPosition.clear()
        dataMagx.clear()
        dataMagy.clear()
        dataMagz.clear()
        dataMagx2.clear()
        dataMagy2.clear()
        dataMagz2.clear()
        autoPosition(nextX, nextY)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

    }

    private fun sensorReady(event: SensorEvent) : Boolean {
        if (isSensorStabled)
            return true

        when(event.sensor.type) {
            Sensor.TYPE_MAGNETIC_FIELD -> {
                if (magStableCount > 0)
                    magStableCount--
            }
        }

        isSensorStabled = magStableCount==0
        if (isSensorStabled) {
//            Glide.with(this).pauseAllRequests()
//            mFrameLayout.removeView(loadingLayout)
            Toast.makeText(this, "Let's go~", Toast.LENGTH_SHORT).show()
        }
        return isSensorStabled
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if ((event?:false)?.let {sensorReady(event!!)}) {
            when(event!!.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> accMatrix = event.values.clone()
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    magMatrix = event.values.clone()
                    magnitudeOfMagnetic =
                        sqrt(magMatrix[0].pow(2) + magMatrix[1].pow(2) + magMatrix[2].pow(2)).toDouble()

                    if (accMatrix.isNotEmpty() && magMatrix.isNotEmpty() && gamerotationVector.isNotEmpty()) {
                        var I = FloatArray(9)

                        // 쿼터니언 미사용
//                        var success = SensorManager.getRotationMatrix(mRotationMatrix, I, accMatrix, magMatrix)
//                        mRot[0] = mRotationMatrix[0] * magMatrix[0] + mRotationMatrix[1] * magMatrix[1] + mRotationMatrix[2] * magMatrix[2]
//                        mRot[1] = mRotationMatrix[4] * magMatrix[0] + mRotationMatrix[5] * magMatrix[1] + mRotationMatrix[6] * magMatrix[2]
//                        mRot[2] = mRotationMatrix[8] * magMatrix[0] + mRotationMatrix[9] * magMatrix[1] + mRotationMatrix[10] * magMatrix[2]
//                        if (success) {
//                            var orientation = FloatArray(3)
//                            SensorManager.getOrientation(mRotationMatrix, orientation)
//                            mAzimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
//                        }
//
//                        angleA = mAzimuth - (Math.toDegrees(fusedOrientation[0].toDouble()) + 360) % 360
//                        caliX = -1 * sqrt(magnitudeOfMagnetic.pow(2) - mRot[2].pow(2)) * sin(angleA * PI / 180)
//                        caliY = sqrt(magnitudeOfMagnetic.pow(2) - mRot[2].pow(2)) * cos(angleA * PI / 180)
//                        caliZ = mRot[2].toDouble()
                        // 쿼터니언 사용
                        SensorManager.getRotationMatrixFromVector(
                            mRotationMatrix,
                            gamerotationVector
                        )
                        mRot[0] =
                            mRotationMatrix[0] * magMatrix[0] + mRotationMatrix[1] * magMatrix[1] + mRotationMatrix[2] * magMatrix[2]
                        mRot[1] =
                            mRotationMatrix[4] * magMatrix[0] + mRotationMatrix[5] * magMatrix[1] + mRotationMatrix[6] * magMatrix[2]
                        mRot[2] =
                            mRotationMatrix[8] * magMatrix[0] + mRotationMatrix[9] * magMatrix[1] + mRotationMatrix[10] * magMatrix[2]
                        caliX = mRot[0].toDouble()
                        caliY = mRot[1].toDouble()
                        caliZ = mRot[2].toDouble()


                        SensorManager.getRotationMatrixFromVector(
                            mRotationMatrix,
                            gamerotationVector_for_noncali
                        )
                        mRot[0] =
                            mRotationMatrix[0] * magMatrix[0] + mRotationMatrix[1] * magMatrix[1] + mRotationMatrix[2] * magMatrix[2]
                        mRot[1] =
                            mRotationMatrix[4] * magMatrix[0] + mRotationMatrix[5] * magMatrix[1] + mRotationMatrix[6] * magMatrix[2]
                        mRot[2] =
                            mRotationMatrix[8] * magMatrix[0] + mRotationMatrix[9] * magMatrix[1] + mRotationMatrix[10] * magMatrix[2]
                        nonCaliX = mRot[0].toDouble()
                        nonCaliY = mRot[1].toDouble()
                        nonCaliZ = mRot[2].toDouble()


                        magXView.text = "X: " + caliX.toString()
                            .substring(0..if (caliX.toString().length >= 8) 7 else caliX.toString().length - 1)
                        magYView.text = "Y: " + caliY.toString()
                            .substring(0..if (caliY.toString().length >= 8) 7 else caliY.toString().length - 1)
                        magZView.text = "Z: " + caliZ.toString()
                            .substring(0..if (caliZ.toString().length >= 8) 7 else caliZ.toString().length - 1)


                    }
                }
                Sensor.TYPE_GYROSCOPE -> {
                    System.arraycopy(event.values, 0, rotation, 0, event.values.size)
                    if (!orientationGyroscope.isBaseOrientationSet)
                        orientationGyroscope.setBaseOrientation(Quaternion.IDENTITY)
                    else
                        fusedOrientation =
                            orientationGyroscope.calculateOrientation(rotation, event.timestamp)

                    gyroView.text =
                        Math.round((((Math.toDegrees(fusedOrientation[0].toDouble()) + 360) % 360) * 100) / 100.0).toString()
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
                Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                    gamerotationVector = event.values.clone()
                    gamerotationVector_for_noncali = event.values.clone()
                    gamerotationVector_for_noncali[2] = 0.0f
                }
            }

            if (isSet) {
                positionXView.setText(receiveMSG[1])
                positionYView.setText(receiveMSG[2])
                angleView.setText(receiveMSG[3])
                fileName.setText(receiveMSG[4])
                isSet = false
            }
            if (isStartOrStop) {
                isStartOrStop = false
                positionX = positionXView.text.toString().toDouble()
                positionY = positionYView.text.toString().toDouble()


                if (!nowSampling) {
                    nowSampling = true
                    saveData = if (isTestMode) {
                        "${round(positionX)}\t${round(positionY)}\t${this.caliX}\t${this.caliY}\t${this.caliZ}\t${this.nonCaliX}\t${this.nonCaliY}\t${this.nonCaliZ}\t${(Math.toDegrees(fusedOrientation[0].toDouble())+360)%360}\t0.0\r\n"

                    } else {
                        "${round(positionX)}\t${round(positionY)}\t${this.caliX}\t${this.caliY}\t${this.caliZ}\r\n"
                    }
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
                    nowSampling = false
                    writeFile(if (fileName.text.toString().isEmpty()) "default${autoFileNum++}.txt" else fileName.text.toString(), saveData)
                }
            }
            if (isStep) {
                isStep = false
                vibrator.vibrate(80)

                if (isTestMode) {
                    dataPosition.add(positionX.toString() + "\t" + positionY.toString())
                    dataMagx.add(this.caliX.toString())
                    dataMagy.add(this.caliY.toString())
                    dataMagz.add(this.caliZ.toString())
                    dataMagx2.add(this.nonCaliX.toString())
                    dataMagy2.add(this.nonCaliY.toString())
                    dataMagz2.add(this.nonCaliZ.toString())

                    stepLength = receiveMSG[1]
                }

                extractSaveData()

                stepCount++
                stepCountView.text = "  step count : $stepCount"
            }

            if (nowSampling && !isTestMode) {
                dataPosition.add(positionX.toString() + "\t" + positionY.toString())
                dataMagx.add(this.caliX.toString())
                dataMagy.add(this.caliY.toString())
                dataMagz.add(this.caliZ.toString())

            }
        }
    }

    override fun onResume() {
        super.onResume()
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME)
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_GAME)
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_GAME)
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_GAME)
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_GAME)
//
//        packageManager.takeIf { it.missingSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) }?.also {
//            Toast.makeText(this, "ble_not_supported", Toast.LENGTH_SHORT).show()
//            finish()
//        }
//
//        bluetoothAdapter?.takeIf { it.isDisabled }?.apply {
//            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
//            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
//        }
    }

    override fun onPause() {
        super.onPause()
        mSensorManager.unregisterListener(this)
    }
}