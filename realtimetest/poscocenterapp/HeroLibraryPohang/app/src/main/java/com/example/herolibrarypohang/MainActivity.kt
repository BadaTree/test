package com.example.herolibrarypohang

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import android.webkit.WebView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
//import com.example.cslabposco.IndoorLocalization
import com.example.cslabposco.ExIndoorLocalization
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import kotlinx.android.synthetic.main.activity_main.*
import java.util.ArrayList
import com.example.cslabposco.LSTMServer
import com.example.cslabposco.maps.MagneticFieldMap
import java.io.InputStream
import com.example.cslabposco.WifiMap

private val WEB_ADDRESS = "file:///android_asset/poscocenter-trace.html" //"file:///android_asset/SeoulStation.html"

class MainActivity : AppCompatActivity(), SensorEventListener {
    private val mSensorManager by lazy {
        getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    private val vibrator by lazy {
        getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private val lstmServer by lazy {
        LSTMServer()
    }
    private lateinit var indoorLocalization : ExIndoorLocalization
    private var myUUID : String = ""
    private val mHandler : Handler = Handler(Looper.myLooper()!!)
    private var countI = 100
    private lateinit var wvLayout0401v3 : WebView

    private var lastStep = 0
    var x = 2622f - 2478f
    var y = 930f - 672f

    private val XRANGE = 200
    val DATA_RANGE = 180
    val AXIS_VALUE = 3F
    var zVal = ArrayList<Entry>()
    var setZcomp = LineDataSet(zVal, "acc Z")
    var zVals = ArrayList<String>()
    var zDataSets = ArrayList<ILineDataSet>()

    private var sensor_accuracy : Int = 0

    private lateinit var runningMode : String

    private var is_popup_on : Boolean = false
    private var isFirstInit : Boolean = true
    private lateinit var alertDialog : AlertDialog

    ////////SK WIFI////////////
    lateinit var mainMap_obj : MagneticFieldMap
    var subMap_obj : ArrayList<FloatArray> = arrayListOf()

    var wifipermitted = false
    var scanstarted = false
    var wifidataready = false
    var firstwifiscan = true

    lateinit var ans_list : ArrayList<Int>

    lateinit var wifiManager: WifiManager

    private lateinit var wifimap: WifiMap


    private lateinit var readwifihashmapFile : InputStream
    private lateinit var readwifilistFile : InputStream
    private lateinit var readwifitestFile : InputStream
    private lateinit var readwifirssihashmapFile : InputStream


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

    var checkrange = true
    /////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        runningMode = intent.getStringExtra("mode").toString()
        checkFunction()

        Glide.with(this).load(R.raw.whiteloading).into(loadingView)

        myUUID = Settings.Secure.getString(this.contentResolver, Settings.Secure.ANDROID_ID).toString()
        wvLayout0401v3 = webView
        wvLayout0401v3.setInitialScale(190)
        wvLayout0401v3.scrollTo(1900, 110)

        val webSettings = wvLayout0401v3.settings
        webSettings.useWideViewPort = true
        webSettings.builtInZoomControls = true
        webSettings.javaScriptEnabled = true
        webSettings.javaScriptCanOpenWindowsAutomatically = false
        webSettings.setSupportMultipleWindows(false)
        webSettings.setSupportZoom(true)

        wvLayout0401v3.goBack()
        wvLayout0401v3.loadUrl(WEB_ADDRESS)
        wvLayout0401v3.goBack()
        wvLayout0401v3.loadUrl(WEB_ADDRESS)

        /////SK WIFI//////
        wifiManager = this.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        readwifihashmapFile = resources.openRawResource(R.raw.poscocenter_wifihashmap)
        readwifilistFile = resources.openRawResource(R.raw.poscocenter_wifilist)
        readwifitestFile = resources.openRawResource(R.raw.wifitest)

        wifimap = WifiMap(readwifihashmapFile, readwifilistFile, readwifitestFile)

        wifipermitted = false
        scanstarted = true
        wifiManager.startScan()

        /////////////////////

        initChart()
        threadStart()

        val mainMap = resources.openRawResource(R.raw.handhashmap)
        val subMap = resources.openRawResource(R.raw.handhashmap_for_instant_3)

        val mainMap_obj = MagneticFieldMap(mainMap)
        var subMap_obj : ArrayList<FloatArray> = arrayListOf()
        var splitData : Array<String>
        subMap.bufferedReader().useLines { lines -> lines.forEach{
            splitData = it.split("\t").toTypedArray()
            subMap_obj.add(floatArrayOf(splitData[0].toFloat(), splitData[1].toFloat(), splitData[2].toFloat(), splitData[3].toFloat(), splitData[4].toFloat()))
        }}
        indoorLocalization = ExIndoorLocalization(mainMap_obj, subMap_obj, resources.openRawResource(R.raw.build_map), getgpsinfo())
        indoorLocalization.serverReset(myUUID)

        btnReset.setOnClickListener {
            vibrator.vibrate(30)
            indoorLocalization.serverReset(myUUID)
            val intent = Intent(applicationContext, MainActivity::class.java)
            intent.putExtra("mode", runningMode)
            startActivity(intent)
            finish()
        }
        coverSwitch.setOnClickListener {
            coverLayout.visibility = if (coverSwitch.isChecked) View.VISIBLE else View.INVISIBLE
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

    private fun initChart() {
        chartZ.axisLeft.setAxisMaxValue(AXIS_VALUE)
        chartZ.axisLeft.setAxisMinValue(-AXIS_VALUE)
        chartZ.axisRight.setAxisMaxValue(AXIS_VALUE)
        chartZ.axisRight.setAxisMinValue(-AXIS_VALUE)
        setZcomp.color = Color.BLUE
        setZcomp.lineWidth = 2F
        setZcomp.setDrawValues(false)
        setZcomp.setDrawCircles(false)
        setZcomp.setDrawCubic(true)
        setZcomp.axisDependency = YAxis.AxisDependency.RIGHT
        zDataSets.add(setZcomp)

        for (i in 0 until XRANGE) {
            zVals.add("")
        }
        chartZ.data = LineData(zVals, zDataSets)
        chartZ.invalidate()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {

            when (event.sensor.type) {
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    sensor_accuracy = event.accuracy
                    if (event.accuracy != 3) {   // 자기장 센서 accuracy 가 3이 아닐 땐,
//                        notiView.text = "I Need Calibration!"
                        if (is_popup_on==false) {  // popup 창이 여러개 뜨는 것을 방지
                            is_popup_on=true
                            showSettingPopup(event.accuracy)   // popup 창을 띄움
                        }
                    }
                }
            }

            if ((sensor_accuracy == 3) and (wifidataready)) {
                var result = indoorLocalization.sensorChanged(event, myUUID)

                when(result[1]) {
                    "The sensors is not ready yet" -> return
                    "완전 수렴" -> {
                        if (lastStep != result[2].toInt()) {
                            SyncPositionSend(result[3].toDouble(), result[4].toDouble())
                            lastStep = result[2].toInt()
                            checkroom(result[3].toDouble(), result[4].toDouble())
                            lstmServer.roomdatasend(cur_pos, new_pos)
                            lstmServer.stepsend("check")
                            Log.d("roomdatasendcheck", lstmServer.checkTestSend)
                            printroominfo(lstmServer.checkTestSend)
                            cur_pos = new_pos

                        }
                    }
                    else -> {
                        if (isFirstInit) {
                            //                    Glide.with(this).pauseAllRequests()
                            mFrameLayout.removeView(loadingLayout)
                            Toast.makeText(this, "지금부터 걸어주세요.", Toast.LENGTH_SHORT).show()
                            isFirstInit = false
                            is_popup_on=true
                        }

                    }
                }

                statusView.text = indoorLocalization.getPose()

                gyroView.text = result[0]
                insView.text = result[1]
                scView.text = result[2]
                xView.text = result[3]
                yView.text = result[4]
            }




        }


    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor != null) {
            if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                if (is_popup_on == true)
                    showSettingPopup(accuracy)   // sensor calibration 동작으로 sensor accuracy 가 변했다면, popup을 새로 띄움.
            }
        }
    }

    private fun showSettingPopup(accuracy: Int) {
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.alert_popup, null)
        val textView: TextView = view.findViewById(R.id.textView)
        val imageView: ImageView = view.findViewById(R.id.gif_image)
        Glide.with(this).load(R.drawable.compass).into(imageView);
        var accuracyLevel : String = ""
        var txtColor : Int = Color.RED
        accuracyLevel = when(accuracy) {
            0 -> "Sensor Accuracy : Very LOW"
            1 -> "Sensor Accuracy : LOW"
            2 -> "Sensor Accuracy : MEDIUM"
            3 -> "Sensor Accuracy : HIGH"
            else ->  "기기를 8자로 돌려주세요"
        }
        txtColor = when(accuracy) {
            0 -> Color.RED
            1 -> Color.RED
            2 -> Color.GREEN
            3 -> Color.BLUE
            else -> Color.BLACK
        }

        textView.text = accuracyLevel   // popup의 text에 현재 accuracy level을 띄움.
        textView.setTextColor(txtColor)

        try {
            alertDialog.dismiss()
        }
        catch (e: java.lang.Exception){
        }
        alertDialog = AlertDialog.Builder(this)
//            .setTitle("기기를 8자로 돌려주세요\n")
            .setPositiveButton("완료", {dialog, which ->  is_popup_on=false})
            .create()

        alertDialog.setView(view)
        alertDialog.show()

    }

    override fun onResume() {
        super.onResume()
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME)
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_GAME)
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_GAME)
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION), SensorManager.SENSOR_DELAY_GAME)
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_GAME)
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE), SensorManager.SENSOR_DELAY_GAME)

        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT), SensorManager.SENSOR_DELAY_GAME)
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_GAME)

        registerReceiver(wifiScanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
    }

    override fun onPause() {
        super.onPause()
        mSensorManager.unregisterListener(this)

        unregisterReceiver(wifiScanReceiver)

    }

    override fun onDestroy() {
        super.onDestroy()
        indoorLocalization.thread.interrupt()
        thread1.interrupt()
    }

    fun SyncPositionSend(x : Double, y : Double/*, dir:Int, color: Int*/) {
        mHandler.postDelayed(Runnable {
            //wvLayout0401v3.loadUrl("javascript:androidBridge1($x, $y)")   ////// 200328_원준_수정
            wvLayout0401v3.loadUrl("javascript:androidBridge($x, $y, \"${indoorLocalization.getPose()}\")")   ////// 200328_원준_수정
        }, 100)
    }

    fun printroominfo(data : String) {
        mHandler.postDelayed(Runnable {
            wvLayout0401v3.loadUrl("javascript:print_room_info('$data')")   ////// 200328_원준_수정
        }, 100)
    }

    var cur_pos : String = "hall"
    var new_pos : String = "hall"

    fun checkroom(x : Double, y : Double) {
        if ((19.0 <= x) && (x<= 60.0) && (0.0 <= y) && (y <= 63.0)){
            new_pos = "idea2"
        }else if ((67.0 <= x) && (x<= 113.0) && (0.0 <= y) && (y <= 63.0)){
            new_pos = "idea1"
        }else if ((19.0 <= x) && (x<= 60.0) && (70.0 <= y) && (y <= 123.0)){
            new_pos = "idea4"
        }else if ((67.0 <= x) && (x<= 113.0) && (70.0 <= y) && (y <= 117.0)){
            new_pos = "idea3"
        }else if ((175.0 <= x) && (x<= 300.0) && (0.0 <= y) && (y <= 81.0)){
            new_pos = "conference3"
        }else if ((175.0 <= x) && (x<= 300.0) && (88.0 <= y) && (y <= 189.0)){
            new_pos = "conference2"
        }else if ((175.0 <= x) && (x<= 300.0) && (196.0 <= y) && (y <= 303.0)){
            new_pos = "conference1"
        }else if (480.0 <= y){
            new_pos = "eventhall"
        }
        else{
            new_pos = "hall"
        }
    }

    fun ResetAllPoint() {
        mHandler.postDelayed(Runnable {
            //wvLayout0401v3.loadUrl("javascript:androidBridge1($x, $y)")   ////// 200328_원준_수정
            wvLayout0401v3.loadUrl("javascript:removeAllPoints()")   ////// 200328_원준_수정
        }, 100)
    }

    fun showColorList(index_num : Int) {
        wvLayout0401v3.loadUrl("javascript:showColorList($index_num)")   ////// 200328_원준_수정
    }

    private fun chartZUpdate(x : String) {
        if (zVal.size > DATA_RANGE) {
            zVal.removeAt(0)
            for (i in 0 until DATA_RANGE) {
                zVal[i].xIndex = i
            }
        }
        zVal.add(Entry(x.toFloat(), zVal.size))
        setZcomp.notifyDataSetChanged()
        chartZ.notifyDataSetChanged()
        chartZ.invalidate()
    }

    var handlerZ: Handler = object : Handler(Looper.myLooper()!!) {
        override fun handleMessage(msg: Message) {
            val now_acc = indoorLocalization.getAccZ()
            if (msg.what == 0) {
                chartZUpdate(now_acc.toString())
            }
        }
    }



    inner class MyThread3 : Thread() {
        override fun run() {
            while (true) {
                handlerZ.sendEmptyMessage(0)
                try {
                    sleep(50)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    inner class roominfothread : Thread() {
        override fun run() {
            while (true) {
                try {
                    sleep(2000)
                    lstmServer.stepsend("check")
                    printroominfo(lstmServer.checkTestSend)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }


    var thread1 = dataThread()
    fun threadStart() {
        val thread3 = MyThread3()
        thread3.isDaemon = true
        thread3.start()

        val thread2 = roominfothread()
        thread2.isDaemon = true
        thread2.start()

//        val thread1 = dataThread()
        thread1.isDaemon = true
        thread1.start()
    }

    var longitude: Double = 0.0
    var latitude: Double = 0.0
    fun getgpsinfo() : ArrayList<Double> {
        var locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        Log.d("gpstestest", locationManager.toString())
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
                        latitude = location.latitude
                    }
//                isGPSEnabled -> {
//                    val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
//                    longitude = location?.longitude!!
//                    latitude = location?.latitude!!
////                    Toast.makeText(this, "gps 기반 현재 위치", Toast.LENGTH_SHORT).show()
//                }
                    else -> {
                        Toast.makeText(this, "현재 위치 확인 불가", Toast.LENGTH_SHORT).show()
                    }
                }
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1,
                    0F,
                    gpsLocationListener
                )
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    1,
                    0F,
                    gpsLocationListener
                )
                locationManager.removeUpdates(gpsLocationListener)
            }
            locationManager.removeUpdates(gpsLocationListener)
        }
        Log.d("gpstestest", latitude.toString() + longitude.toString())

        return arrayListOf(latitude, longitude)
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

    private fun getWifiInfo(){
        if (wifipermitted) { // wifiScan을 한 경우에만 getScanResult를 사용하도록 flag 변수 구현
            var scanResultList = wifiManager.scanResults
            var updatedata = ""
            for (i in 1 until scanResultList!!.size) {
                updatedata += scanResultList[i].BSSID.toString() +
                        "\t" + scanResultList[i].level.toString() + "\r\n"
            }

            ans_list = wifimap.vectorcompare(4, updatedata)


            wifipermitted = false
            scanstarted = false

            if(firstwifiscan) {
                indoorLocalization.wifi_range = ans_list
                indoorLocalization.serverReset(myUUID)
                vibrator.vibrate(160)
                firstwifiscan = false
                wifidataready = true
            } else{
                indoorLocalization.wifi_first_scan = false
                indoorLocalization.wifi_range = ans_list
            }
        }
    }

    inner class dataThread : Thread() {
        override fun run() {
            while (true && !this.isInterrupted) {
                try {
                    sleep(1000)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                if((wifipermitted) and (scanstarted)) {
                    getWifiInfo()
                }
                scanstarted = true
                wifiManager.startScan()
            }
        }
    }
}