package com.example.hasequential

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
import android.view.View
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

//private val WEB_ADDRESS = "file:///android_asset/HanaSquare_map_for_result.html"
private val WEB_ADDRESS = "file:///android_asset/anam_result.html"

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

    ////////////////SK WiFi////////////////////
    var wifipermitted = false
    var scanstarted = false
    var wifidataready = false
    var firstwifiscan = true

    lateinit var wifiManager: WifiManager

    val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            var success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            if(success){
                wifipermitted = true
            } else{
                wifipermitted = false
            }
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

        exIndoorLocalization = ExIndoorLocalization(resources.openRawResource(R.raw.hashmap_1f),
            resources.openRawResource(R.raw.hashmap_1f),
            resources.openRawResource(R.raw.wifihashmap_1),
            resources.openRawResource(R.raw.wifirssihashmap_1),
            resources.openRawResource(R.raw.wifilist_1))
//        exIndoorLocalization = ExIndoorLocalization(resources.openRawResource(R.raw.hanahandhashmap),
//               resources.openRawResource(R.raw.hanahandhashmap_for_instant_3),
//               resources.openRawResource(R.raw.hanasquare_wifihashmap),
//               resources.openRawResource(R.raw.hanasquare_wifirssihashmap),
//               resources.openRawResource(R.raw.hanasquare_wifilist))

        btnB1.setOnClickListener {
            wvLayout0401v3.loadUrl("javascript:changeMapB1()")
            exIndoorLocalization.setClass(resources.openRawResource(R.raw.hashmap_1f),
                resources.openRawResource(R.raw.hashmap_1f),
                resources.openRawResource(R.raw.wifihashmap_1),
                resources.openRawResource(R.raw.wifirssihashmap_1),
                resources.openRawResource(R.raw.wifilist_1))
        }
        btnB2.setOnClickListener {
            wvLayout0401v3.loadUrl("javascript:changeMapB2()")
            exIndoorLocalization.setClass(resources.openRawResource(R.raw.hashmap_2f),
                resources.openRawResource(R.raw.hashmap_2f),
                resources.openRawResource(R.raw.wifihashmap_2),
                resources.openRawResource(R.raw.wifirssihashmap_2),
                resources.openRawResource(R.raw.wifilist_2))
        }
        btnB3_bo.setOnClickListener {
            wvLayout0401v3.loadUrl("javascript:changeMapB3_bo()")
            exIndoorLocalization.setClass(resources.openRawResource(R.raw.hashmap_3f),
                resources.openRawResource(R.raw.hashmap_3f),
                resources.openRawResource(R.raw.wifihashmap_3),
                resources.openRawResource(R.raw.wifirssihashmap_3),
                resources.openRawResource(R.raw.wifilist_3))
        }
        btnB3_ko.setOnClickListener {
            wvLayout0401v3.loadUrl("javascript:changeMapB3_ko()")
            exIndoorLocalization.setClass(resources.openRawResource(R.raw.hashmap_4f),
                resources.openRawResource(R.raw.hashmap_4f),
                resources.openRawResource(R.raw.wifihashmap_4),
                resources.openRawResource(R.raw.wifirssihashmap_4),
                resources.openRawResource(R.raw.wifilist_4))
        }

        btnReset.setOnClickListener {
            vibrator.vibrate(30)
//            exIndoorLocalization.serverReset(myUUID)
            val intent = Intent(applicationContext, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        coverSwitch.setOnClickListener {
            val isCoverVisible = if (coverSwitch.isChecked) View.VISIBLE else View.INVISIBLE
//            coverLayout.visibility = isCoverVisible
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
        webView.loadUrl(WEB_ADDRESS)
        wvLayout0401v3 = webView
        webView.scrollTo(1380, 450)
        webView.isScrollbarFadingEnabled = true
        webView.setInitialScale(143)
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
     // ## 2022.11.21 바다 수정 : 자기장 센서 안정화 코드 불 필요하여 주석 처리
     // ## wifi engine에서는 측위에 자기장 센서 사용하지않음
//            if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
//                if (event.accuracy != 3) {
//                    if (!is_popup_on) {  // popup 창이 여러개 뜨는 것을 방지
//                        is_popup_on = true
//                        showSettingPopup(event.accuracy)  // popup 창을 띄움
//                    }
//                    return
//                }
//            }
//            if (wifidataready) {
//                if (exIndoorLocalization.wifi_range[0] == -100) {
            var result = exIndoorLocalization.sensorChanged(event)
            when (result[1]) {
                "The sensors is not ready yet" -> {
                    return}

//                        "완전 실패" -> {
//                            if (lastStep != result[3].toInt()) {
//                                vibrator.vibrate(30)
//                            }
//                            }
                "완전 수렴" -> {
//                            walkingPopup(false)
                    Log.d("완전수렴", "완전수렴")
                    if (lastStep != result[3].toInt()) {
//                                exIndoorLocalization.setGPS(getGpsInfo())
//                                if (exIndoorLocalization.getPose() == "On Hand") {
//                                    SyncPositionSendRed(result[4].toDouble(), result[5].toDouble())
//                                } else {
//                                    SyncPositionSendBlue(result[4].toDouble(), result[5].toDouble())
//                                }
                        lastStep = result[3].toInt()
                        vibrator.vibrate(30)

//                                wvLayout0401v3.loadUrl("javascript:remove_children")
                        var children_pos_list =
                            exIndoorLocalization.wifiengine.area_check_pos_list
                        var string_pos_list = "["
                        var cnt = 0
                        if (children_pos_list.size >= 100) {
                            for (pos in children_pos_list) {
                                if (cnt % 2 == 0) {
                                    string_pos_list += "[${pos[0]}, ${pos[1]}],"
                                }
                                cnt += 1
                            }
                        }else {
                            for (pos in children_pos_list) {
                                string_pos_list += "[${pos[0]}, ${pos[1]}],"
                            }
                        }
                        string_pos_list += "]"
                        wvLayout0401v3.loadUrl("javascript:show_all_children($string_pos_list)")
                    }
                }
                // 11.22 바다수정 :  INDOOR 결과로 사용되지 않는 방향 수렴 주석 처리
//                "방향 수렴" -> {
//                    Log.d("방향수렴", "방향수렴")
//                    if (lastStep != result[3].toInt()) {
//                        //                        exIndoorLocalization.setGPS(getGpsInfo())
//                        lastStep = result[3].toInt()
//                        wvLayout0401v3.loadUrl("javascript:remove_children")
//                        var children_pos_list =
//                            exIndoorLocalization.wifiengine.area_check_pos_list
//                        var string_pos_list = "["
//                        for (pos in children_pos_list) {
//                            string_pos_list += "[${pos[0]}, ${pos[1]}],"
//                        }
//                        string_pos_list += "]"
//                        wvLayout0401v3.loadUrl("javascript:show_all_children($string_pos_list)")
//                        vibrator.vibrate(30)
//
//                    }
//                }
                else -> {

                    if (isFirstInit) {
                        mFrameLayout.removeView(loadingLayout)
                        Toast.makeText(this, "지금부터 걸어주세요.", Toast.LENGTH_SHORT).show()
                        isFirstInit = false
                        is_popup_on = true
//                                walkingPopup(true)

                    }
                    // 걸음 검출 및 갱신
                    if (lastStep != result[3].toInt()) {
                        lastStep = result[3].toInt()
//                                if (exIndoorLocalization.getPose() == "On Hand") {
//                                    SyncPositionSendRed(result[4].toDouble(), result[5].toDouble())
//                                } else {
//                                    SyncPositionSendBlue(result[4].toDouble(), result[5].toDouble())
//                                }
//                                wvLayout0401v3.loadUrl("javascript:remove_children")

                        var children_pos_list =
                            exIndoorLocalization.wifiengine.area_check_pos_list
                        var string_pos_list = "["
                        var cnt = 0
                        Log.d("else", children_pos_list.size.toString())
                        if (children_pos_list.size >= 100) {
                            for (pos in children_pos_list) {
                                if (cnt % 2 == 0) {
                                    string_pos_list += "[${pos[0]}, ${pos[1]}],"
                                }
                                cnt += 1
                            }
                        }else {
                            for (pos in children_pos_list) {
                                string_pos_list += "[${pos[0]}, ${pos[1]}],"
                            }
                        }
                        string_pos_list += "]"
                        wvLayout0401v3.loadUrl("javascript:show_all_children($string_pos_list)")
                    }
                }
            }
            typeView.text = exIndoorLocalization.getType()
            unqWifi.text = result[6]
            gyroView.text = result[0]
            insView.text = result[2]
            scView.text = result[3]
//                    xView.text = result[4]
//                    yView.text = result[5]
            minxView.text = exIndoorLocalization.rangecheck[0].toString()
            maxxView.text = exIndoorLocalization.rangecheck[1].toString()
            minyView.text = exIndoorLocalization.rangecheck[2].toString()
            maxyView.text = exIndoorLocalization.rangecheck[3].toString()

            if (mapRotationSwitch.isChecked) {
                image_angle = round(result[0].toFloat())
                SyncImageRotationSend(image_angle)
            }
        }
//            }
//        }
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
                //실시간 WIFI 잘 받아오는지 확인
//                Log.d ("wifiinfo####", updatedata.toString())
            }


            wifipermitted = false
            scanstarted = false

            if(firstwifiscan) {
                //실시간 WIFI 잘 받아오는지 확인
//                Log.d ("wifiinfo", updatedata.toString())
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

    fun SyncPositionSendRed(x : Double, y : Double) {
        mHandler.postDelayed(Runnable {
            wvLayout0401v3.loadUrl("javascript:androidBridge($x, $y)")
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