package com.example.mylibrary.sensors

import android.hardware.SensorEvent
import org.apache.commons.math3.complex.Quaternion
import com.kircherelectronics.fsensor.filter.gyroscope.OrientationGyroscope

/*
2022.04.26 (원준)
- GetGyroscope 클래스 추가함.
- 기존과 변경된 점 없음
 */



internal class GetGyroscope {
    private var raw_gyro_yaw_value: Float = 0.0f
    private var fusedOrientation = FloatArray(3)
    private var gyro_constant_from_app_start_to_map_collection : Float = 0.0f
    private var gyro_value_map_collection : Float = 0.0f
    private var gyroCaliValue : Float = 0.0f
    private var first_reset : Boolean = true
    private var gyro_history : ArrayList<Float> = arrayListOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f)

    private val orientationGyroscope by lazy {
        OrientationGyroscope()
    }

    fun init(){
        orientationGyroscope.reset()
    }

    fun calc_gyro_value(event : SensorEvent){
        var rotation = FloatArray(3)
        System.arraycopy(event.values, 0, rotation, 0, event.values.size)
        if (!orientationGyroscope.isBaseOrientationSet)
            orientationGyroscope.setBaseOrientation(Quaternion.IDENTITY)
        else
            fusedOrientation = orientationGyroscope.calculateOrientation(rotation, event.timestamp)

        raw_gyro_yaw_value = (Math.toDegrees(fusedOrientation[0].toDouble()).toFloat() + 360) % 360
    }

    fun save_gyro(){
        // gyro 값들을 딱 3개만 넣어놓기. 0 : 가장 과거, 2 : 가장 최근
        gyro_history.removeAt(0)
        gyro_history.add(from_map_collection())
    }

    fun get_pre_gyro_for_in_pocket(idx : Int): Float {
        // idx == 0 : 가장 과거, idx == 2 : 가장 최근
        return gyro_history[idx]
    }

    fun from_app_start_to_map_collection(): Float {
        return gyro_constant_from_app_start_to_map_collection
    }

    fun from_map_collection() : Float{
        gyro_value_map_collection = (raw_gyro_yaw_value + gyroCaliValue + 360) % 360
        return gyro_value_map_collection
    }

    fun from_reset_direction() : Float {
        return raw_gyro_yaw_value
    }

    fun gyro_reset() {
        if (first_reset){
            gyro_constant_from_app_start_to_map_collection = gyroCaliValue - raw_gyro_yaw_value
            first_reset = false
        }
        orientationGyroscope.reset()
    }

    fun setGyroCalivalue(gyro_cali_value: Float){
        gyroCaliValue = gyro_cali_value
    }
}