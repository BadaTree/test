package com.example.has

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
import android.net.wifi.WifiManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.util.Log
import android.view.LayoutInflater
import android.webkit.WebView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import com.bumptech.glide.Glide
import com.example.mylibrary.ExIndoorLocalization
import kotlinx.android.synthetic.main.activity_main.*
import kotlin.math.round

private val WEB_ADDRESS_HANA = "file:///android_asset/HanaSquare_map_for_result.html"
private val WEB_ADDRESS_HANA2 = "file:///android_asset/Hanaground.html"
private val WEB_ADDRESS_HANA2_B2 = "file:///android_asset/Hanaground_B2.html"
private val WEB_ADDRESS_HANA2_B3 = "file:///android_asset/Hanaground_B3.html"
private val WEB_ADDRESS_HANA3 = "file:///android_asset/Hanaground2.html"
private val WEB_ADDRESS_HANA3_B2 = "file:///android_asset/Hanaground2_B2.html"
private val WEB_ADDRESS_HANA3_B3 = "file:///android_asset/Hanaground2_B3.html"


class MainActivity : AppCompatActivity(), SensorEventListener {

    private val mSensorManager by lazy {
        getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    private val vibrator by lazy {
        getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    private lateinit var exIndoorLocalization : ExIndoorLocalization

    private val mHandler : Handler = Handler(Looper.myLooper()!!)
    private lateinit var wvLayout0401v3 : WebView
    private var image_angle = 0.0f
    private var lastStep = 0
    private var is_popup_on : Boolean = false
    private var isFirstInit : Boolean = true
    private lateinit var alertDialog : AlertDialog
    private var floor : String = ""

    ////////////////SK WiFi////////////////////
    var wifipermitted = false
    var scanstarted = false
    var wifidataready = false
    var firstwifiscan = true

    lateinit var wifiManager: WifiManager

    val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            var success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
//            Log.d("extra wifi check", success.toString())
            wifipermitted = success
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //Sensor Ready Image Animation
        Glide.with(this).load(R.drawable.whiteloading).into(loadingView)

        checkFunction()

        initUI()
        threadStart()

        /////SK WIFI//////
        wifiManager = this.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifipermitted = false
        scanstarted = true
        wifiManager.startScan()

        exIndoorLocalization = ExIndoorLocalization(resources.openRawResource(R.raw.hanahandhashmap),
            resources.openRawResource(R.raw.hanahandhashmap_for_instant_3),
            resources.openRawResource(R.raw.hanasquare_wifihashmap),
            resources.openRawResource(R.raw.hanasquare_wifirssihashmap),
            resources.openRawResource(R.raw.hanasquare_wifilist),
            resources.openRawResource(R.raw.build_map_1floor)
        )

        btnReset.setOnClickListener {
            vibrator.vibrate(30)
//            exIndoorLocalization.serverReset(myUUID)
            val intent = Intent(applicationContext, MainActivity::class.java)
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

    private fun initUI() {
        webView.goBack()
        webView.loadUrl(WEB_ADDRESS_HANA)
        wvLayout0401v3 = webView
        webView.scrollTo(1300, 480)
        webView.isScrollbarFadingEnabled = true
        webView.setInitialScale(130)
        val webSettings = webView.settings
        webSettings.useWideViewPort = true
        webSettings.builtInZoomControls = true
        webSettings.javaScriptEnabled = true
        webSettings.javaScriptCanOpenWindowsAutomatically = false
        webSettings.setSupportMultipleWindows(false)
        webSettings.setSupportZoom(true)

    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            /* [원준]
               자기장 센서 calibration 단계가 3단계가 아니라면, 어떠한 동작도 하지 않게 함.
               자기장 센서 calibration 단계까 3단계가 될 때까지 calibration 동작만 유도.
             */
            if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                if (event.accuracy != 3) {
                    if (!is_popup_on) {  // popup 창이 여러개 뜨는 것을 방지
                        is_popup_on = true
                        showSettingPopup(event.accuracy)  // popup 창을 띄움
                    }
                    return
                }
            }
//            if (wifidataready) {
//                if (exIndoorLocalization.wifi_range[0] == -100) {
            var result = exIndoorLocalization.sensorChanged(event)
            when (result[1]) {
                "The sensors is not ready yet" -> return
                "완전 수렴" -> {
                    walkingPopup(false)
                    if (lastStep != result[3].toInt()) {
                        //                        exIndoorLocalization.setGPS(getGpsInfo())

//                        if (exIndoorLocalization.getPose() == "On Hand") {
//                            SyncPositionSendRed(result[4].toDouble(), result[5].toDouble())
//                        } else {
//                            SyncPositionSendBlue(result[4].toDouble(), result[5].toDouble())
//                        }
                        SyncPositionSendRed(result[4].toDouble(), result[5].toDouble(), exIndoorLocalization.getPose())


                        lastStep = result[3].toInt()
                        vibrator.vibrate(30)
                    }
                }
                else -> {
                    if (isFirstInit) {
                        mFrameLayout.removeView(loadingLayout)
                        Toast.makeText(this, "지금부터 걸어주세요.", Toast.LENGTH_SHORT).show()
                        isFirstInit = false
                        is_popup_on = true
                        walkingPopup(true)
                    }
                }
            }

            if(result[6] != "0" && exIndoorLocalization.floorchange) {

                if(result[6] == "1") {
                    floor = "1"
                }

                if(result[6] == "-1") {
                    floor = "B2"
                }

                if(result[6] == "-2") {
                    floor = "B3"
                }

                Toast.makeText(this, "하나스퀘어 " + floor + "층에 도착했습니다. " , Toast.LENGTH_SHORT).show()

                if (exIndoorLocalization.infoType == "EL1" || exIndoorLocalization.infoType == "EL2"
                    || exIndoorLocalization.infoType == "EL1Front" || exIndoorLocalization.infoType == "EL2Front"
                    || exIndoorLocalization.infoType == "EL2Front2") {

                    if(result[6] == "1") {
                        webView.goBack()
                        webView.loadUrl(WEB_ADDRESS_HANA2)
                        webView.goBack()
                        webView.loadUrl(WEB_ADDRESS_HANA2)
                        exIndoorLocalization.floorchange = false
                    }
                    if(result[6] == "-1") {
                        webView.goBack()
                        webView.loadUrl(WEB_ADDRESS_HANA2_B2)
                        webView.goBack()
                        webView.loadUrl(WEB_ADDRESS_HANA2_B2)
                        exIndoorLocalization.floorchange = false
                    }
                    if(result[6] == "-2") {
                        webView.goBack()
                        webView.loadUrl(WEB_ADDRESS_HANA2_B3)
                        webView.goBack()
                        webView.loadUrl(WEB_ADDRESS_HANA2_B3)
                        exIndoorLocalization.floorchange = false
                    }
                }

                if (exIndoorLocalization.infoType == "EL3" || exIndoorLocalization.infoType == "EL4"
                    || exIndoorLocalization.infoType == "EL3Front" || exIndoorLocalization.infoType == "EL3Front2"
                    || exIndoorLocalization.infoType == "EL3Front3" || exIndoorLocalization.infoType == "EL4Front"
                    || exIndoorLocalization.infoType == "EL4Front2") {
                    if(result[6] == "1") {
                        webView.goBack()
                        webView.loadUrl(WEB_ADDRESS_HANA3)
                        webView.goBack()
                        webView.loadUrl(WEB_ADDRESS_HANA3)
                        exIndoorLocalization.floorchange = false
                    }
                    if(result[6] == "-1") {
                        webView.goBack()
                        webView.loadUrl(WEB_ADDRESS_HANA3_B2)
                        webView.goBack()
                        webView.loadUrl(WEB_ADDRESS_HANA3_B2)
                        exIndoorLocalization.floorchange = false
                    }
                    if(result[6] == "-2") {
                        webView.goBack()
                        webView.loadUrl(WEB_ADDRESS_HANA3_B3)
                        webView.goBack()
                        webView.loadUrl(WEB_ADDRESS_HANA3_B3)
                        exIndoorLocalization.floorchange = false
                    }
                }
            }

            if(result[6] == "0" && exIndoorLocalization.floorchange) {

                Toast.makeText(this, "하나스퀘어 B1층에 도착했습니다. ", Toast.LENGTH_SHORT).show()

                    webView.goBack()
                    webView.loadUrl(WEB_ADDRESS_HANA)
                    webView.goBack()
                    webView.loadUrl(WEB_ADDRESS_HANA)
                    exIndoorLocalization.floorchange = false

            }

            if(exIndoorLocalization.makeElvIn) {
                SyncPositionSendRed(exIndoorLocalization.elvX, exIndoorLocalization.elvY)
                exIndoorLocalization.makeElvIn = false
            }

            if (mapRotationSwitch.isChecked) {
                image_angle = round(result[0].toFloat())
                SyncImageRotationSend(image_angle)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor != null) {
            if ((sensor.type == Sensor.TYPE_MAGNETIC_FIELD) && accuracy != 3) {
                if (is_popup_on)
                    showSettingPopup(accuracy)   // sensor calibration 동작으로 sensor accuracy 가 변했다면, popup을 새로 띄움.
            }
        }
    }

    private fun walkingPopup(open:Boolean) {
        if (open) {
            val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val view = inflater.inflate(R.layout.walking_popup, null)
            val imageView: ImageView = view.findViewById(R.id.gif_image)
            Glide.with(this).load(R.drawable.walking).into(imageView)

            alertDialog = AlertDialog.Builder(this).create()
            alertDialog.setCanceledOnTouchOutside(false)
            alertDialog.setView(view)
            alertDialog.show()
            alertDialog.window?.setLayout(1000, 774)
        }
        else {
            alertDialog.dismiss()
        }

    }

    private fun showSettingPopup(accuracy: Int) {
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.alert_popup, null)
        val textView: TextView = view.findViewById(R.id.textView)
        val imageView: ImageView = view.findViewById(R.id.gif_image)
        Glide.with(this).load(R.drawable.compass).into(imageView)
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

        textView.text = accuracyLevel
        textView.setTextColor(txtColor)

        try {
            alertDialog.dismiss()
        }
        catch (e: java.lang.Exception){
        }
        alertDialog = AlertDialog.Builder(this)
            .setPositiveButton("완료", {dialog, which ->  is_popup_on=false})
            .create()

        alertDialog.setView(view)
        alertDialog.show()

    }

    private fun getWifiInfo(){
        if (wifipermitted) { // wifiScan을 한 경우에만 getScanResult를 사용하도록 flag 변수 구현
            var scanResultList = wifiManager.scanResults
            var updatedata = ""
            for (i in 1 until scanResultList!!.size) {
                updatedata += scanResultList[i].BSSID.toString() +
                        "\t" + scanResultList[i].level.toString() + "\r\n"
            }


            wifipermitted = false
            scanstarted = false
//            Log.d("wifidatachange", "timecheck")

            if(firstwifiscan) {
                exIndoorLocalization.wifidata = updatedata
                vibrator.vibrate(160)
                firstwifiscan = false
                wifidataready = true
            } else{
                exIndoorLocalization.wifidata = updatedata
                exIndoorLocalization.wifichange = true
            }

        }
    }

    override fun onResume() {
        super.onResume()
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME)
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_GAME)
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_GAME)
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION), SensorManager.SENSOR_DELAY_GAME)
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE), SensorManager.SENSOR_DELAY_GAME)
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_GAME)
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT), SensorManager.SENSOR_DELAY_GAME)
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_GAME)

        registerReceiver(wifiScanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
    }

    override fun onPause() {
        super.onPause()
        mSensorManager.unregisterListener(this)

        unregisterReceiver(wifiScanReceiver)
    }

    fun SyncPositionSendRed(x : Double, y : Double, pos:String="On Hand") {
        mHandler.postDelayed(Runnable {
            wvLayout0401v3.loadUrl("javascript:androidBridge($x, $y, \"$pos\")")
        }, 100)
    }

    fun SyncPositionSendBlue(x : Double, y : Double) {
        mHandler.postDelayed(Runnable {
            wvLayout0401v3.loadUrl("javascript:androidBridge2($x, $y)")
        }, 100)
    }

    fun SyncPositionSendGreen(x : Double, y : Double) {
        mHandler.postDelayed(Runnable {
            wvLayout0401v3.loadUrl("javascript:androidBridge3($x, $y)")
        }, 100)
    }

    fun SyncImageRotationSend(angle : Float) {
        mHandler.postDelayed(Runnable {
            wvLayout0401v3.loadUrl("javascript:image_rotation($image_angle)")
        }, 100)
    }

    inner class WifiThread : Thread() {
        override fun run() {
            while (true && !isInterrupted) {
                try {
                    sleep(500)
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
    val thread2 = WifiThread()

    private fun threadStart() {
        thread2.isDaemon = true
        thread2.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        thread2.interrupt()
    }
}