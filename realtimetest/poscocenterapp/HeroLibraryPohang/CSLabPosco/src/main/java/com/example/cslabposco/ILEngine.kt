package com.example.cslabposco

import android.util.Log
import com.example.cslabposco.filters.ParticleFilter
import com.example.cslabposco.instant.InstantLocalization
import com.example.cslabposco.instant.InstantParticle_Mother
import com.example.cslabposco.maps.MagneticFieldMap
import kotlin.math.*

internal class ILEngine constructor(map_hand: MagneticFieldMap, private var map_for_instant_hand: ArrayList<FloatArray>) {

    // 맵 파일 읽기
    private var mapVector = map_hand
    private var mapVector_temp = map_for_instant_hand
    private var instantLocalization: InstantLocalization = InstantLocalization(mapVector, mapVector_temp)
    private var particleOn = false
    private var positioning_success = false
    private var aon_continue_number = 0
    private var distance_threshold = 10
    private var totally_converge_succss = false
    private var wrong_number = 0
    private var IL_result = mutableMapOf<String, Float>()
    private lateinit var particleFilter : ParticleFilter

    //승규 hand to other other to hand 관련 //
    var bth_pos : Array<Double> = arrayOf(0.0, 0.0)
    var gyroCaliValue : Float = 0f
    var back_to_hand : Boolean = false
    var hand_to_other : Boolean = false
    var posture_onhand : Boolean = false
    var deeplrn_pos_array : java.util.ArrayList<Array<Double>> = arrayListOf()
    var deviceposture = 0

    ////승규 수정////
    private fun cal_angle(pos_array : java.util.ArrayList<Array<Double>>): Double {
        var tot_size = pos_array.size
        var a : Array<Double>
        var b : Array<Double>
        if (tot_size >= 5){
            a = pos_array[tot_size - 1]
            b = pos_array[tot_size - 5]
        } else {
            a = pos_array[tot_size - 1]
            b = pos_array[0]
        }
        return atan2(b[1] - a[1], b[0] - a[0])
    }
    ////승규 수정////
    ////WiFi//////
    var wifi_range = arrayListOf(0, 0, 0, 0)

    fun getLocation(magx : Float, magy : Float, magz : Float, magx_for_pf : Float, magy_for_pf : Float, magz_for_pf : Float , stepLength : Float, gyro_for_pf : Float, gyro :Float, state : Int): MutableMap<String, Float> {
        instantLocalization.init_range = wifi_range
        if(!particleOn) {
            var instant_result = instantLocalization.getLocation(magx, magy, magz, stepLength, gyro, state)
            IL_result = instant_result
            if (instant_result["status_code"] == 200.0f) {
                particleOn = true
                particleFilter = ParticleFilter(mapVector, 100, round(instant_result["pos_x"]!!).toInt(), round(instant_result["pos_y"]!!).toInt(), 10)
            }
        }
        else {
            //승규 수정//
//            if (back_to_hand){
//                particleFilter = ParticleFilter(mapVector, 100, round(bth_pos[0]).toInt(), round(bth_pos[1]).toInt(), 10)
//                instantLocalization.__init_for_always_on_mode(gyroCaliValue)
//                back_to_hand = false
//            }
            //승규 수정//
//            if(deviceposture == 0){
//                posture_onhand = true
//                if(hand_to_other){
//                    if(deeplrn_pos_array.size > 1) {
//                        var angle_result = cal_angle(deeplrn_pos_array)
//                        var pos_result = deeplrn_pos_array[deeplrn_pos_array.size - 1]
//                        bth_pos = pos_result
//                        gyroCaliValue = Math.toDegrees(angle_result).toFloat()
//                        deeplrn_pos_array = arrayListOf()
//                    }
//                    back_to_hand = true
//                    hand_to_other = false
//                }
//            } else {
//                posture_onhand = false
//                hand_to_other = true
//            }
            /////////////////
            Log.d("particlefiltergyro", gyro.toString())
            var pf_result = particleFilter.step(arrayOf(magx_for_pf.toDouble(), magx_for_pf.toDouble(), magx_for_pf.toDouble()), gyro_for_pf.toDouble(), stepLength.toDouble())
            if (!totally_converge_succss) {
                if(did_converge_success(pf_result, arrayOf(magx_for_pf, magy_for_pf, magz_for_pf)))
                    distance_threshold = 15
                else
                    distance_threshold = 1000
            }
            var aon_result = instantLocalization.getLocation(magx, magy, magz, stepLength, gyro, state)
            Log.d("wrongnumber", aon_result["status_code"].toString())

            var final_position = compare_result(pf_result, aon_result)
            IL_result["gyro_from_map"] = aon_result["gyro_from_map"]!!
            IL_result["status_code"] = 201.0f
            IL_result["pos_x"] = final_position[0].toFloat()
            IL_result["pos_y"] = final_position[1].toFloat()
            if (!final_position.contentEquals(pf_result)) { // aon으로 보정됐다면?
                IL_result["status_code"] = 202.0f
                wrong_number = 0
                aon_continue_number = 0
            }
            else{
                // TODO : 완전 수렴한 경우의 조건이 디버깅해봤을 때 이상함. 실험을 통해 수정 필요.
                if(aon_continue_number >= 200)
                    totally_converge_succss = false
            }

        }
        return IL_result
    }

    fun is_aon_mode(): Boolean {
        return particleOn
    }

    private fun did_converge_success(pf_result:Array<Double>, input_vector:Array<Float>): Boolean {
        var map_data = mapVector.getData(pf_result[0], pf_result[1])
        val diff_x = abs(input_vector[0] - map_data[0])
        val diff_y = abs(input_vector[1] - map_data[1])
        val diff_z = abs(input_vector[2] - map_data[2])
        val input_m = sqrt(input_vector[0].pow(2) + input_vector[1].pow(2) + input_vector[2].pow(2))
        val map_m = sqrt(map_data[0].pow(2) + map_data[1].pow(2) + map_data[2].pow(2))
        val diff_m = abs(input_m - map_m)

//        val diff_threshold = 20
        val diff_threshold = 10 // 포스코 센터 최적화

        if (((diff_x >= diff_threshold) && (diff_y >= diff_threshold)) || ((diff_x >= diff_threshold) && (diff_z >= diff_threshold)) || ((diff_y >= diff_threshold) && (diff_z >= diff_threshold))){
            wrong_number += 1
        }
        if (diff_x >= diff_threshold && diff_y >= diff_threshold && diff_z >= diff_threshold) {
            wrong_number += 1
        }
        if (diff_m > (diff_threshold+10))
            wrong_number += 1
        return wrong_number < 10
    }

    private fun compare_result(pf_result: Array<Double>, aon_result:MutableMap<String, Float>): Array<Double> {
        if(aon_result["status_code"]==200.0f){
            var distance_two_answser = sqrt((aon_result["pos_x"]!! - pf_result[0]).pow(2.0) + (aon_result["pos_y"]!! - pf_result[1]).pow(2.0))
            if (distance_two_answser <= distance_threshold){
                particleFilter = ParticleFilter(mapVector, 100, round(aon_result["pos_x"]!!).toInt(), round(aon_result["pos_y"]!!).toInt(), 10)
                return arrayOf(aon_result["pos_x"]!!.toDouble(), aon_result["pos_y"]!!.toDouble())
            }
            else{
                return pf_result
            }
        }
        else{
            return pf_result
        }
    }


}

