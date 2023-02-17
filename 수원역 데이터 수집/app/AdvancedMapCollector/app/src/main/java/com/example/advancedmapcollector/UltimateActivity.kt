package com.example.advancedmapcollector

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.*
import android.telephony.CellInfo
import android.telephony.TelephonyManager
import android.telephony.gsm.GsmCellLocation
import android.util.Log
import android.view.View
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.example.advancedmapcollector.net.BTConstant.BT_REQUEST_ENABLE
import com.example.advancedmapcollector.net.BluetoothServer
import com.example.advancedmapcollector.net.SocketListener
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.TedPermission
import com.kircherelectronics.fsensor.filter.gyroscope.OrientationGyroscope
import kotlinx.android.synthetic.main.activity_ultimate.*
import org.apache.commons.math3.complex.Quaternion
import java.io.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.*

private const val l : Double = 10.0

class UltimateActivity : AppCompatActivity(), SensorEventListener {
//    private fun PackageManager.missingSystemFeature(name: String) : Boolean = !hasSystemFeature(name)
//    private val bluetoothAdapter : BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
//        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
//        bluetoothManager.adapter
//     }
//    private val BluetoothAdapter.isDisabled : Boolean
//        get() = !isEnabled
//    private val REQUEST_ENABLE_BT = 1000



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

    //-------------------BLE SERVER-----------------------
    private var handler: Handler = Handler()
    private var sbLog = StringBuilder()
    private var btServer: BluetoothServer = BluetoothServer()

    private lateinit var svLogView: ScrollView
    private lateinit var tvLogView: TextView
    private lateinit var etMessage: EditText
    //----------------------------------------------------

    //--------------------WiFi----------------------------
    var cnt = 0

    var wifidata = ""
    var gpsdata = ""
    var ltedata = ""
    var ltehistdata = ""

    var wifipermitted = false
    var scanstarted = false

    var firstscan = false

    lateinit var wifiManager: WifiManager

    val wifiScanReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            var success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            Log.d("extra wifi check", success.toString())
            if (success) {
                wifipermitted = true
            } else {
                wifipermitted = false
            }
        }
    }
    //--------------------------------------------------------

    //--------------------GPS/LTE-----------------------------
    var longitude: Double = 0.0
    var latitude: Double = 0.0

    var cellID: Int = 0
    var lac: Int = 0

    var cellIDlist = ArrayList<Int>()
    var mccmnc: Int = 0

    //--------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ultimate)

        //WiFi/////////////////////////
        wifiManager = this.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkPermission()
        }

        AppController.Instance.init(this, btServer)

        initUI()
        setListener()

        btServer.setOnSocketListener(mOnSocketListener)
        btServer.accept()

        threadStart()
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
        btnBLEOn.setOnClickListener {

            btServer.sendData("SET\t${positionXView.text.toString()}\t${positionYView.text.toString()}\t${angleView.text.toString()}\t${fileName.text.toString()}")
        }
    }

    private fun initUI() {
        svLogView = findViewById(R.id.svLogView)
        tvLogView = findViewById(R.id.tvLogView)
        etMessage = findViewById(R.id.etMessage)
    }

    private fun setListener() {
//        findViewById<Button>(R.id.btnAccept).setOnClickListener {
//            btServer.accept()
//        }
//
//        findViewById<Button>(R.id.btnStop).setOnClickListener {
//            btServer.stop()
//        }
//
        findViewById<Button>(R.id.btnSendData).setOnClickListener {
            if (etMessage.text.toString().isNotEmpty()) {
                btServer.sendData(etMessage.text.toString())
                etMessage.setText("")
            }
        }
    }

    private fun init() {
        Glide.with(this).load(R.raw.whiteloading).into(loadingView)

        positionXView.setText("0")
        positionYView.setText("0")
        stepCountView.text = "  step count : ${stepCount}"

        var path = getExternalPath()
        var file = File(path)
        if (!file.exists()) file.mkdir()

        path = getwifiExternalPath()
        file = File(path)
        if (!file.exists()) file.mkdir()

        path = getgpsExternalPath()
        file = File(path)
        if (!file.exists()) file.mkdir()

        path = getlteExternalPath()
        file = File(path)
        if (!file.exists()) file.mkdir()
    }

    fun log(message: String) {
        sbLog.append(message.trimIndent() + "\n")
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
        collectionLog.text = fullLog

        if (!nowSampling) {
            btServer.sendData("START")
            btnSamplingStart.text = "STOP RECORDING"
            nowSampling = true
            saveData = "${round(positionX)}\t${round(positionY)}\t${this.caliX}\t${this.caliY}\t${this.caliZ}\r\n"
            backUpPosition[0] = positionX
            backUpPosition[1] = positionY
            backUpSaveData = saveData
            stepCount=0

            longitude = 0.0
            latitude = 0.0
            cellID = 0
            lac = 0
            wifidata = ""
            gpsdata = ""
            ltedata = ""
            ltehistdata = ""
            cellIDlist = ArrayList<Int>()

            cnt = 0
            var dump = ""

            registerReceiver(wifiScanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
            wifipermitted = false
            scanstarted = true
            firstscan = true
            wifiManager.startScan()
//            dump = getgpsinfo(round(positionX).toString(), round(positionY).toString())
//            getcellinfo(round(positionX).toString(), round(positionY).toString())
//            getcellinfo2(round(positionX).toString(), round(positionY).toString())


            stepCountView.text = "  step count : ${stepCount}"
            if (isRemember && !isRestore) {
                backUpHeading = arrayListOf(if (isAngleFixed) angleView.text.toString().toDouble() else gyroView.text.toString().toDouble())
            }
            Toast.makeText(this, "지금부터 데이터를 기록합니다.", Toast.LENGTH_SHORT).show()
        } else {
            btServer.sendData("STOP")
            btnSamplingStart.text = "START RECORDING"
            nowSampling = false
            writeFile(if (fileName.text.toString().isEmpty()) "default${autoFileNum++}.txt" else fileName.text.toString(), saveData)

            writewifiFile(
                if (fileName.text.toString()
                        .isEmpty()
                ) "default.txt" else fileName.text.toString(), wifidata
            )
            writelteFile(
                if (fileName.text.toString()
                        .isEmpty()
                ) "default.txt" else fileName.text.toString(), ltedata
            )
            writegpsFile(
                if (fileName.text.toString()
                        .isEmpty()
                ) "default.txt" else fileName.text.toString(), gpsdata
            )
            writeltehistFile(
                if (fileName.text.toString()
                        .isEmpty()
                ) "defaultcellhist.txt" else fileName.text.toString() + "cellhist.txt", ltehistdata
            )
            unregisterReceiver(wifiScanReceiver)
        }
    }

    fun onBleStart(v: View?) {

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
            saveData += round(nextX).toString() + "\t" + round(nextY).toString() + "\t" + dataMagx.poll() + "\t" + dataMagy.poll() + "\t" + dataMagz.poll() + "\r\n"
            wifidata += getWifiInfo(round(nextX).toString(), round(nextY).toString())
//            gpsdata += getgpsinfo(round(nextX).toString(), round(nextY).toString())
//            ltedata += getcellinfo(round(nextX).toString(), round(nextY).toString())
//            ltehistdata += getcellinfo2(round(nextX).toString(), round(nextY).toString())

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
        if (isSensorStabled) {
            Glide.with(this).pauseAllRequests()
            mFrameLayout.removeView(loadingLayout)
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

                                btServer.sendData("STEP")
                                extractSaveData()
                                wifiManager.startScan()
                                cnt = 0

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

    private fun checkFunction() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
                && ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                && ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ), 101
                )
            } else {
                ActivityCompat.requestPermissions(
                    this, arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ), 101
                )
            }
        } else {
        }
    }

    private fun getWifiInfo(nowX : String, nowY : String) : String{
        var updatedata = ""
        if (firstscan) {
            if (wifipermitted) {
                var scanResultList = wifiManager.scanResults
                for (i in 1 until scanResultList!!.size) {
                    updatedata += cnt.toString() + "\t" + nowX + "\t" + nowY + "\t" +
                            scanResultList[i].SSID.toString() + "\t" + scanResultList[i].BSSID.toString() +
                            "\t" + scanResultList[i].level.toString() + "\r\n"
                }
                Log.d("wifilist", updatedata)
                cnt += 1

                Log.d("getwifiinfocheck", wifipermitted.toString())
                wifipermitted = false
                scanstarted = false
                firstscan = false
                vibrator.vibrate(160)
                //}
            }
        } else{
            var scanResultList = wifiManager.scanResults
            for (i in 1 until scanResultList!!.size) {
                updatedata += cnt.toString() + "\t" + nowX + "\t" + nowY + "\t" +
                        scanResultList[i].SSID.toString() + "\t" + scanResultList[i].BSSID.toString() +
                        "\t" + scanResultList[i].level.toString() + "\r\n"
            }
            Log.d("wifilist", updatedata)
            cnt += 1

            Log.d("getwifiinfocheck", wifipermitted.toString())
            wifipermitted = false
            scanstarted = false
        }
        return updatedata
    }

    fun getgpsinfo(nowX: String, nowY: String) : String {
        var updatedata = ""
        var locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        if(locationManager != null) {
            var providers = locationManager.allProviders
            val isGPSEnabled: Boolean =
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled: Boolean =
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                checkFunction()
            } else {
                when {
                    isNetworkEnabled -> {
                        val location =
                            locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                        longitude = location?.longitude!!
                        latitude = location?.latitude!!

                        updatedata = nowX + "\t" + nowY + "\t" + latitude.toString() + "\t" + longitude.toString() + "\r\n"
//                    Toast.makeText(this, "네트워크 기반 현재 위치", Toast.LENGTH_SHORT).show()
                    }
//                isGPSEnabled -> {
//                    val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
//                    longitude = location?.longitude!!
//                    latitude = location?.latitude!!
//                    Toast.makeText(this, "gps 기반 현재 위치", Toast.LENGTH_SHORT).show()
//                }
                    else -> {
                        Toast.makeText(this, "현재 위치 확인 불가", Toast.LENGTH_SHORT).show()
                    }
                }
                Log.d("providercheck", longitude.toString() + "\t" + latitude.toString())
//                locationManager.requestLocationUpdates(
//                    LocationManager.GPS_PROVIDER,
//                    1,
//                    0F,
//                    gpsLocationListener
//                )
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    1,
                    1F,
                    gpsLocationListener
                )
                locationManager.removeUpdates(gpsLocationListener)
            }
            locationManager.removeUpdates(gpsLocationListener)
        }
        return updatedata
    }

    fun getcellinfo(nowX: String, nowY: String): String {
        var updatedata = ""
        var returnbool = false
        var tmpcellID = cellID

        var tm = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            checkFunction()
        } else {
            var location = tm.cellLocation as GsmCellLocation
            cellID = location.cid
//            if ((cellID != tmpcellID) && (!cellIDlist.contains(cellID))) {
//                cellIDlist.add(cellID)
//                returnbool = true
//            }
            lac = location.lac
            mccmnc = tm.networkOperator.toInt()
            updatedata = cnt.toString() + "\t" + nowX + "\t" + nowY + "\t" + mccmnc.toString() + "\t" +
                    cellID.toString() + "\t" + lac.toString() + "\r\n"
        }
        return updatedata
    }
    fun getcellinfo2(nowX: String, nowY: String): String {
        var updatedata = ""
        var returnstr = ""
        var tmpcellID = cellID

        var tm = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            checkFunction()
        } else {
            var celllist = tm.allCellInfo
            for (n : CellInfo in celllist){
                returnstr += n.toString() + "\n\r"
                Log.d("celllist", n.toString())
            }
            updatedata = cnt.toString() + "\t" + positionX.toString() + "\t" + positionY.toString() + "\r\n" + returnstr + "\r\n" +"\r\n"
        }
        return updatedata
    }

    val gpsLocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val provider: String = location.provider
            val longitude: Double = location.longitude
            val latitude: Double = location.latitude
            val altitude: Double = location.altitude
        }

        override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private fun getwifiExternalPath(): String {
        var sdPath = ""
        val ext = Environment.getExternalStorageState()
        sdPath = if (ext == Environment.MEDIA_MOUNTED) {
            Environment.getExternalStorageDirectory()
                .absolutePath + "/wifidata/"
        } else {
            "$filesDir/wifidata/"
        }
        return sdPath
    }

    private fun getgpsExternalPath(): String {
        var sdPath = ""
        val ext = Environment.getExternalStorageState()
        sdPath = if (ext == Environment.MEDIA_MOUNTED) {
            Environment.getExternalStorageDirectory()
                .absolutePath + "/gpsdata/"
        } else {
            "$filesDir/gpsdata/"
        }
        return sdPath
    }

    private fun getlteExternalPath(): String {
        var sdPath = ""
        val ext = Environment.getExternalStorageState()
        sdPath = if (ext == Environment.MEDIA_MOUNTED) {
            Environment.getExternalStorageDirectory()
                .absolutePath + "/ltedata/"
        } else {
            "$filesDir/ltedata/"
        }
        return sdPath
    }

    private fun writewifiFile(title: String, body: String) {
        try {
            val path = getwifiExternalPath()
            val bw = BufferedWriter(FileWriter(path + title, false))
            bw.write(body)
            bw.close()
            Toast.makeText(this, path + "에 저장완료", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun writegpsFile(title: String, body: String) {
        try {
            val path = getgpsExternalPath()
            val bw = BufferedWriter(FileWriter(path + title, false))
            bw.write(body)
            bw.close()
            Toast.makeText(this, path + "에 저장완료", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun writelteFile(title: String, body: String) {
        try {
            val path = getlteExternalPath()
            val bw = BufferedWriter(FileWriter(path + title, false))
            bw.write(body)
            bw.close()
            Toast.makeText(this, path + "에 저장완료", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun writeltehistFile(title: String, body: String) {
        try {
            val path = getlteExternalPath()
            val bw = BufferedWriter(FileWriter(path + title, false))
            bw.write(body)
            bw.close()
            Toast.makeText(this, path + "에 저장완료", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
        }
    }

    inner class dataThread : Thread() {
        override fun run() {
            while (true) {
                try {
                    sleep(500)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                if(nowSampling){
                    if (firstscan){
                        if(wifipermitted){
                            wifidata = getWifiInfo(round(positionX).toString(), round(positionY).toString())
//                            gpsdata = getgpsinfo(round(positionX).toString(), round(positionY).toString())
//                            ltedata = getcellinfo(round(positionX).toString(), round(positionY).toString())
//                            ltehistdata = getcellinfo2(round(positionX).toString(), round(positionY).toString())
                            break
                        }
                    }
//                    else{
////                        wifiManager.startScan()
//                    }
                }
            }
        }
    }

    fun threadStart() {
        val thread1 = dataThread()
        thread1.isDaemon = true
        thread1.start()
    }


    override fun onResume() {
        super.onResume()
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME)
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_GAME)
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_GAME)
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION), SensorManager.SENSOR_DELAY_GAME)
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_GAME)

        registerReceiver(wifiScanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))

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

        unregisterReceiver(wifiScanReceiver)
    }
}