package com.example.mylibrary.instant

import android.util.Log
import com.example.mylibrary.maps.MagneticFieldMap
import kotlin.math.*


/*
InstantLocalization Result Status Code
100.0f : 아직 수렴 안됨
101.0f : 방향만 수렴
200.0f : 완전 수렴
400.0f : 일치되는 좌표 없음
*/

//internal class InstantLocalization constructor(map_hand: MagneticFieldMap, private var map_for_instant_hand: ArrayList<FloatArray>) {
internal class InstantLocalization constructor(map_hand: MagneticFieldMap, map_for_instant_hand: MagneticFieldMap) {

    private var instant_result : MutableMap<String, Float> = mutableMapOf("status_code" to -1.0f, "gyro_from_map" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)
    // 맵 파일 읽기
    private var mapVector = map_hand
    private var mapVector_temp = map_for_instant_hand

    // 필요 변수들
    private var angle_step_num : Int = 10
    private var angleList : IntArray = (0..359 step angle_step_num).toList().toIntArray()
    private var sampled_sequence_average_list : ArrayList<Array<Float>> = arrayListOf()
    private var cur_step : Int = -1
    private var sampled_vector_magnitude : Float = 0.0f
    private var instant_particle_mother_list = arrayListOf<InstantParticle_Mother>()
    private var pre_state : Int = 0
    private var cur_state : Int = 0
    private var totally_converge_succss = false
    var wrong_number = 0
    var distance_threshold = 10
    private var aon_continue_number = 0




    ////역추적////
    var gyro_list = arrayListOf<Float>()
    var ans_dir = 0.0f
    var ans_pos = arrayListOf(0.0f, 0.0f)
    var step_len_list = arrayListOf<Float>()
    var past_ans_pos = ArrayList<Array<Float>>()


    // 하나스퀘어 최적화 parameter //
    private var early_stop_in_n_mother : Float = 10.0f
    private var take_n_mother : Int = 36
    private var vector_weight : Float = 0.5f
    private var weight_according_step : Float = 0.2f
    private var magnitude_threshold : Float = 6.0f
    private var vector_threshold_second : Float = 6.0f
    private var vector_threshold : Float = 12.0f
    private var firstThreshold : Float = 12.0f

    // 포스코 센터 최적화 parameter //
//    private var early_stop_in_n_mother : Float = 10.0f
//    private var take_n_mother : Int = 36
//    private var vector_threshold = 5.0f
//    private var vector_threshold_second = 3.0f
//    private var magnitude_threshold = 3.0f
//    private var firstThreshold = 7.0f
//    private var weight_according_step = 0.5f
//    private var vector_weight = 0.5f

//    private var early_stop_in_n_mother : Float = 10.0f
//    private var take_n_mother : Int = 36
//    private var vector_weight : Float = 0.5f
//    private var weight_according_step : Float = 0.1f
//    private var magnitude_threshold : Float = 5.0f
//    private var vector_threshold_second : Float = 6.0f
//    private var vector_threshold : Float = 14.0f
//    private var firstThreshold : Float = 14.0f

    //수민
    private var floorChange : Boolean = false

    init {

    }



    /*
    private fun __change_threshold(state : Int) {
        if (state == 0){
            __set_hand_threshold()
        }
        else if ((state == 1) || (state == 2)){
            __set_hand_threshold()
        }
    }

    private fun __change_map(state: Int){
        if (state == 0){
            mapVector = mapVector_hand
            mapVector_temp = mapVector_temp_hand
        }
    }

    private fun __set_hand_threshold() {
        ////// 하나스퀘어
        early_stop_in_n_mother = 10.0f
        take_n_mother = 36
        vector_weight = 0.5f
        weight_according_step = 0.12f
        magnitude_threshold = 4.0f
        vector_threshold_second = 7.0f
        vector_threshold = 12.0f
        firstThreshold = 12.0f
        ////////////////////////////////
    }

     */
//
//    private fun __read_hand_map_file() : ArrayList<FloatArray>{
//        var splitData : Array<String>
//        var mapVector_temp = arrayListOf<FloatArray>()
//        map_for_instant_hand.bufferedReader().useLines { lines -> lines.forEach{
//            splitData = it.split("\t").toTypedArray()
//            mapVector_temp.add(floatArrayOf(splitData[0].toFloat(), splitData[1].toFloat(), splitData[2].toFloat(), splitData[3].toFloat(), splitData[4].toFloat()))
//        }}
//
//        return mapVector_temp
//    }
    var init_range = arrayListOf(0, 0, 0, 0)

    //    ///승규 /////
    fun check_in_range(x: Double, y:Double): Boolean{
        var result = false
        if((init_range[0] <= x) && (x <= init_range[1]) && (init_range[2] <= y) && (y <= init_range[3])){
            result = true
        }
        return result
    }
    //    /////////////////
    private fun __first_matching_with_map_and_create_mothers(vectorList : ArrayList<Array<Float>>, state_changed : Boolean = false) {
        sampled_sequence_average_list = vectorList
        if (!state_changed) {

            for (i in angleList.indices) {
                instant_particle_mother_list.add(InstantParticle_Mother(angleList[i]))
                var vector = vectorList[i]
                var range_x = arrayOf(vector[0] - firstThreshold, vector[0] + firstThreshold)
                var range_y = arrayOf(vector[1] - firstThreshold, vector[1] + firstThreshold)
                var range_z = arrayOf(vector[2] - firstThreshold, vector[2] + firstThreshold)
                for (row in mapVector_temp.mag){
                    if(check_in_range((row.key/10000).toDouble(), (row.key%10000).toDouble())) {
                    if ((range_x[0] < row.value[0] && row.value[0] < range_x[1]) && (range_y[0] < row.value[1] && row.value[1] < range_y[1]) && (range_z[0] < row.value[2] && row.value[2] < range_z[1])) {
                        if (instant_particle_mother_list.size == 0) {
                            instant_particle_mother_list.add(InstantParticle_Mother(angleList[i]))
                        }
                        instant_particle_mother_list[instant_particle_mother_list.size - 1].appendChildren(
                            arrayListOf(row.key/10000.toFloat(), row.key%10000.toFloat()),
                            arrayOf(row.value[0], row.value[1], row.value[2]))
                    }
                    }

                }
            }

            __only_take_n_mothers(n=take_n_mother)
        }

    }
    private fun __only_take_n_mothers(n : Int = 15) {
        if (instant_particle_mother_list.size < n) {
            take_n_mother = instant_particle_mother_list.size
        }
        instant_particle_mother_list = ArrayList(instant_particle_mother_list.sortedByDescending{it.particle_children_list.size}.slice(0 .. take_n_mother - 1) )

    }

    private fun __state_changed(state: Int) : Boolean{
        cur_state = state
        if (pre_state != state) {
            pre_state = state
            return true
        }
        else {
            return false
        }
    }

    fun reset_all(){
        angleList = (0..359 step 10).toList().toIntArray()
        sampled_sequence_average_list = arrayListOf()
        cur_step = -1
        sampled_vector_magnitude = 0.0f
        instant_particle_mother_list = arrayListOf<InstantParticle_Mother>()
        instant_result = mutableMapOf("status_code" to -1.0f, "gyro_from_map" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)
    }

    fun __init_for_always_on_mode(cur_gyro: Float) {
        var cur_gyro_int = cur_gyro.toDouble().toInt()
        angleList = ((360-cur_gyro_int)-90..(360-cur_gyro_int)+90 step 10).toList().toIntArray()
//        angleList = (0..359 step 10).toList().toIntArray()
        sampled_sequence_average_list = arrayListOf()
        cur_step = -1
        sampled_vector_magnitude = 0.0f
        instant_particle_mother_list = arrayListOf<InstantParticle_Mother>()
        instant_result = mutableMapOf("status_code" to -1.0f, "gyro_from_map" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)
    }

    private fun init_parameters(status_code:Float, gyro:Float=0.0f){
        if ((status_code == 200.0f) || (status_code == 202.0f)){
            var cur_gyro = gyro.toInt()
//            angleList = ((360-cur_gyro)-90..(360-cur_gyro)+90 step 90).toList().toIntArray()
            angleList = ((360-cur_gyro)-90..(360-cur_gyro)+90 step angle_step_num).toList().toIntArray()
            cur_step = -1
            sampled_vector_magnitude = 0.0f
            instant_particle_mother_list = arrayListOf<InstantParticle_Mother>()
            instant_result = mutableMapOf("status_code" to -1.0f, "gyro_from_map" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)
        }
        else if (status_code == 400.0f) {
            angleList = (0..359 step angle_step_num).toList().toIntArray()
            cur_step = -1
            sampled_vector_magnitude = 0.0f
            instant_particle_mother_list = arrayListOf<InstantParticle_Mother>()
            instant_result = mutableMapOf("status_code" to -1.0f, "gyro_from_map" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)
        }
//        else
//            return
//        cur_step = -1
//        sampled_vector_magnitude = 0.0f
//        instant_particle_mother_list = arrayListOf<InstantParticle_Mother>()
//        instant_result = mutableMapOf("status_code" to -1.0f, "gyro_from_map" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)
    }

    private fun getLocation_IL(magx : Float, magy : Float, magz : Float, stepLength : Float, gyro : Float, state : Int)  : MutableMap<String, Float> {

        if (state > 0) {
            init_parameters(400.0f)
            instant_result["status_code"] = 400.0f
            return instant_result
        }
        init_parameters(instant_result["status_code"]!!, gyro)

        var state_changed = __state_changed(state)
        if (state_changed) {
            init_parameters(400.0f)
        }
        cur_step += 1
        val vectorList = createVectorForEachOrientation(arrayOf(magx, magy, magz))
        sampled_vector_magnitude = calculate_magnitude(vectorList[0])
        if(cur_step == 0){
            if (state_changed) {
                for (mother in instant_particle_mother_list){
                    moveChildren(mother, stepLength, gyro)
                }
            }
            __first_matching_with_map_and_create_mothers(vectorList, state_changed)

            for (i in instant_particle_mother_list){
                Log.d("instanttest", "${i.my_angle}\t${i.particle_children_list.size}")
            }

            instant_result["status_code"] = 100.0f
            return instant_result
        }

        var cur_idx = -1
        while(true) {
            cur_idx += 1
            if (instant_particle_mother_list.size == 0) {
                instant_result["status_code"] = 400.0f
                return instant_result
            }
            var particle_mother = instant_particle_mother_list[cur_idx]
            // 아이들 움직이기. 움직이자마자 벽에 부딪히는 아이들은 다 죽이기.
            moveChildren(particle_mother, stepLength, gyro)
            // 아이들 매칭시키기. 가중치 값 조절하고, 매칭 안되는 애들 죽이고.
            matchingChildren(particle_mother, vectorList[angleList.indexOf(particle_mother.my_angle)])
            // 아이 안 갖고 있는 mother는 삭제.
            if (particle_mother.particle_children_list.size == 0) {
                instant_particle_mother_list.remove(particle_mother)
                cur_idx -= 1
            }
            if ((state==0) && (cur_step >= 5) && (particle_mother.getAvgWeight() < 0.0 )) {
                instant_particle_mother_list.remove(particle_mother)
                cur_idx -= 1
            }

            if (cur_idx == (instant_particle_mother_list.size - 1)) {
                break
            }
        }
        instant_result = estimateInitialDirAndPos(instant_particle_mother_list, gyro)

        return instant_result
    }

    fun changedFloor_and_resetInstatLocalization(map_vector : MagneticFieldMap, map_for_instant_hand : MagneticFieldMap, gyro: Float=-1.0f) {

        mapVector = map_vector
        mapVector_temp = map_for_instant_hand
        floorChange = true // 수민
        Log.d("suminMap",mapVector.toString())
        if (gyro == -1.0f){
            init_parameters(400.0f)
        }
        else {
            init_parameters(200.0f, gyro)
        }

    }

    fun getLocation(mag_vector: Array<Double>, mag_vector_for_pf: Array<Double>, stepLength: Double, gyro_for_pf: Float, gyro: Float, state: Int, pf_result: Array<Double>, wifi_range: ArrayList<Int>) : MutableMap<String, Float> {
        var magx = mag_vector[0].toFloat()
        var magy = mag_vector[1].toFloat()
        var magz = mag_vector[2].toFloat()
        var magx_for_pf = mag_vector_for_pf[0].toFloat()
        var magy_for_pf = mag_vector_for_pf[1].toFloat()
        var magz_for_pf = mag_vector_for_pf[2].toFloat()
        init_range = wifi_range
        Log.d("getlocation", "${instant_result["status_code"]}\t${instant_result["pos_x"]}\t${instant_result["pos_y"]}")

        if(pf_result.contentEquals(arrayOf(-1.0, -1.0)) ) {
            instant_result = getLocation_IL(magx, magy, magz, stepLength.toFloat(), gyro, state)
            /////////역 추적/////////
//            gyro_list.add(gyro)
//            step_len_list.add(stepLength.toFloat())
//
//            if (instant_result["status_code"] == 200.0f) {
//                ////////역 추적/////////
//                ans_pos = arrayListOf(instant_result["pos_x"]!!.toFloat(), instant_result["pos_y"]!!.toFloat())
//                ans_dir = ((360 - instant_result["gyro_from_map"]!!.toFloat()) + gyro + 360) % 360
//
//            }
//            else if(instant_result["status_code"] == 400.0f){
//                gyro_list = arrayListOf<Float>()
//                ans_dir = 0.0f
//                ans_pos = arrayListOf(0.0f, 0.0f)
//                step_len_list = arrayListOf<Float>()
//                past_ans_pos = ArrayList<Array<Float>>()
//            }
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
            if (!totally_converge_succss) {
                if(did_converge_success(pf_result, arrayOf(magx_for_pf, magy_for_pf, magz_for_pf)))
                    distance_threshold = 20
                else
                    distance_threshold = 1000
            }
            // Always On
            var aon_result = getLocation_IL(magx, magy, magz, stepLength.toFloat(), gyro, state)

            if (aon_result["status_code"] == 200.0f ) {
                var final_position = compare_result(pf_result, aon_result) // pf 좌표 쓸것인지, aon 좌표 쓸것인지 결정
                if (!final_position.contentEquals(pf_result)) { // aon으로 보정됐다면?
                    instant_result["gyro_from_map"] = aon_result["gyro_from_map"]!!
                    instant_result["status_code"] = 202.0f
                    instant_result["pos_x"] = final_position[0].toFloat()
                    instant_result["pos_y"] = final_position[1].toFloat()
//                    if(floorChange) { // 수민
//                        instant_result["pos_x"] = pf_result[0].toFloat()
//                        instant_result["pos_y"] = pf_result[1].toFloat()
//                    }
                    wrong_number = 0
                    aon_continue_number = 0
                }
                else { // aon으로 보정하지 못했다면?
                    instant_result["status_code"] = 400.0f  // 실패로 간주
//                    // TODO : 완전 수렴한 경우의 조건이 디버깅해봤을 때 이상함. 실험을 통해 수정 필요.
//                    if(aon_continue_number >= 200)
//                        totally_converge_succss = false  // 일단 그냥 false로..
//
                }
            }
            else if ((aon_result["status_code"] == 101.0f) || (aon_result["status_code"] == 100.0f)){
                instant_result["gyro_from_map"] = aon_result["gyro_from_map"]!!
                instant_result["status_code"] = 201.0f
            }
            else if (aon_result["status_code"] == 400.0f){
                wrong_number = 0
                aon_continue_number = 0
                instant_result["status_code"] = 400.0f

            }

        }


        return instant_result
    }


    private fun did_converge_success(pf_result:Array<Double>, input_vector:Array<Float>): Boolean {
        var map_data = mapVector.getData(pf_result[0], pf_result[1])
        val diff_x = abs(input_vector[0] - map_data[0])
        val diff_y = abs(input_vector[1] - map_data[1])
        val diff_z = abs(input_vector[2] - map_data[2])
        val input_m = sqrt(input_vector[0].pow(2) + input_vector[1].pow(2) + input_vector[2].pow(2))
        val map_m = sqrt(map_data[0].pow(2) + map_data[1].pow(2) + map_data[2].pow(2))
        val diff_m = abs(input_m - map_m)

        val diff_threshold = 20
//        val diff_threshold = 10 // 포스코 센터 최적화

        if (((diff_x >= diff_threshold) && (diff_y >= diff_threshold)) || ((diff_x >= diff_threshold) && (diff_z >= diff_threshold)) || ((diff_y >= diff_threshold) && (diff_z >= diff_threshold))){
            wrong_number += 1
        }
        if (diff_x >= diff_threshold && diff_y >= diff_threshold && diff_z >= diff_threshold) {
            wrong_number += 1
        }
        if (diff_m > (diff_threshold+10))
            wrong_number += 1
        return wrong_number < 20
    }

    private fun compare_result(pf_result: Array<Double>, aon_result:MutableMap<String, Float>): Array<Double> {
        if(aon_result["status_code"]==200.0f){
            var distance_two_answser = sqrt((aon_result["pos_x"]!! - pf_result[0]).pow(2.0) + (aon_result["pos_y"]!! - pf_result[1]).pow(2.0))

            if (distance_two_answser <= distance_threshold){
                // pf 분리하므로, 아래 문장 삭제
//                particleFilter = ParticleFilter(mapVector, 100, round(aon_result["pos_x"]!!).toInt(), round(aon_result["pos_y"]!!).toInt(), 10)
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

    private fun createVectorForEachOrientation(v: Array<Float>): ArrayList<Array<Float>>{

        var vectorList = ArrayList<Array<Float>>()
        val azimuth = (-1) * atan2(v[0], v[1]) * (180 / PI)
        val magnitude_xy = sqrt(v[0].pow(2) + v[1].pow(2))
        var temp_vector_list = emptyArray<Float>()

        for (i in angleList.indices) {
            var angle = angleList[i]
            temp_vector_list = arrayOf((-1) * magnitude_xy * sin((azimuth + angle)* Math.PI/180).toFloat(),
                magnitude_xy * cos((azimuth + angle) * Math.PI / 180).toFloat(),
                v[2])
            if (cur_step != 0) {
                sampled_sequence_average_list[i] = arrayOf(
                    (sampled_sequence_average_list[i][0] * cur_step + temp_vector_list[0]) / (cur_step + 1),
                    (sampled_sequence_average_list[i][1] * cur_step + temp_vector_list[1]) / (cur_step + 1),
                    (sampled_sequence_average_list[i][2] * cur_step + temp_vector_list[2]) / (cur_step + 1),
                )
                vectorList.add(
                    arrayOf(
                        temp_vector_list[0] - sampled_sequence_average_list[i][0],
                        temp_vector_list[1] - sampled_sequence_average_list[i][1],
                        temp_vector_list[2] - sampled_sequence_average_list[i][2]
                    )
                )

            }
            else {
                vectorList.add(arrayOf(
                    temp_vector_list[0],
                    temp_vector_list[1],
                    temp_vector_list[2]))

            }


        }
        return vectorList
    }

    private fun cal_difference_angle(a : Int, b : Int): Double {
        var result1 : Double = ((abs(a - b) + 360) % 360).toDouble()
        var result2 : Double = 360 - result1

        if (result1 <= result2) {
            return result1
        }
        else {
            return result2
        }
    }


    private fun estimateInitialDirAndPos(mother_list: List<InstantParticle_Mother>, gyro: Float): MutableMap<String, Float> {
        var num_of_mother = instant_particle_mother_list.size
        lateinit var best_mother : InstantParticle_Mother

        if(num_of_mother >= 3){
            if (num_of_mother <= early_stop_in_n_mother){
                var sorted_mother = ArrayList(instant_particle_mother_list.sortedByDescending {it.particle_children_list.size})
                var first_mother = sorted_mother[0]
                var second_mother = sorted_mother[1]
                if (second_mother.particle_children_list.size >= 1) {
                    if (cal_difference_angle(first_mother.my_angle, second_mother.my_angle) <= 10) {
                        var first_mother_answer = calculate_answer_position(first_mother)
                        var second_mother_answer = calculate_answer_position(second_mother)
                        if (first_mother_answer == arrayListOf(-1.0, -1.0) || second_mother_answer == arrayListOf(-1.0, -1.0)) {
                            return mutableMapOf("status_code" to 100.0f, "gyro_from_map" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)
                        }
                        else {
                            var distance_with_two_answer = sqrt(Math.pow(first_mother_answer[0]-second_mother_answer[0],2.0) + Math.pow(first_mother_answer[1]-second_mother_answer[1], 2.0)) * 0.1
                            if (distance_with_two_answer <= 1.5) {
                                var answer_dir_temp = if (abs(first_mother.my_angle - second_mother.my_angle) != 350) (first_mother.my_angle + second_mother.my_angle) / 2 else (first_mother.my_angle + second_mother.my_angle + 360) / 2
                                var answer_dir = ((((360 - answer_dir_temp)+ gyro)+360)%360).toFloat()
                                var answer_x = ((first_mother_answer[0] + second_mother_answer[0]) / 2).toFloat()
                                var answer_y = ((first_mother_answer[1] + second_mother_answer[1]) / 2).toFloat()

                                return mutableMapOf("status_code" to 200.0f, "gyro_from_map" to answer_dir, "pos_x" to answer_x, "pos_y" to answer_y)
                            }

                            else{
                                return mutableMapOf("status_code" to 100.0f, "gyro_from_map" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)
                            }

                        }
                    }
                    else if (second_mother.particle_children_list.size == 1 && first_mother.particle_children_list.size != 1) {
                        best_mother = first_mother
                    }
                    else {
                        return mutableMapOf("status_code" to 100.0f, "gyro_from_map" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)
                    }
                }

                else {
                    return mutableMapOf("status_code" to 100.0f, "gyro_from_map" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)
                }
            }
            else {
                return mutableMapOf("status_code" to 100.0f, "gyro_from_map" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)
            }

        }
        else if(num_of_mother == 2){
            var weight_sum_list = arrayListOf<Double>()
            for (mother in mother_list) {
                var weight_sum = 0.0
                for (children in mother.particle_children_list){
                    weight_sum += children.weight
                }
                weight_sum_list.add(weight_sum)
            }
            if (weight_sum_list[0] >= weight_sum_list[1]){
                best_mother = mother_list[0]
            }
            else {
                best_mother = mother_list[1]
            }

        }
        else if(num_of_mother == 1){
            best_mother = mother_list[0]
        }else if(num_of_mother == 0){
            return return mutableMapOf("status_code" to 400.0f, "gyro_from_map" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)
        }

        // 혹시 모를 에러를 방지
        var num_of_children = best_mother.particle_children_list.size
        if (num_of_children == 0) {
            return return mutableMapOf("status_code" to 400.0f, "gyro_from_map" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)
        }

        var answer_position_xy = calculate_answer_position(best_mother)
        var answer_x = answer_position_xy[0].toFloat()
        var answer_y = answer_position_xy[1].toFloat()
        var answer_dir = ((((360 - (best_mother.my_angle).toInt()) + gyro) + 360) % 360)

        if (answer_position_xy == arrayListOf(-1.0, -1.0)) {
            return return mutableMapOf("status_code" to 101.0f, "gyro_from_map" to answer_dir, "pos_x" to -1.0f, "pos_y" to -1.0f)
        }
        else {
            return mutableMapOf("status_code" to 200.0f, "gyro_from_map" to answer_dir, "pos_x" to answer_x, "pos_y" to answer_y)
        }
    }

    private fun calculate_answer_position(mother : InstantParticle_Mother) : ArrayList<Double>{
        var answer_x = 0.0
        var answer_y = 0.0
        for (children in mother.particle_children_list){
            answer_x += children.x
            answer_y += children.y
        }

        var num_of_children = mother.particle_children_list.size
        answer_x = answer_x / num_of_children
        answer_y = answer_y / num_of_children
        var dist_avg = 0.0
        for (children in mother.particle_children_list) {
            dist_avg += sqrt(Math.pow(answer_x - children.x, 2.0) + Math.pow(answer_y - children.y, 2.0)) * 0.1
        }
        dist_avg = dist_avg / mother.particle_children_list.size
        if (dist_avg > 1.5) {
            return arrayListOf(-1.0, -1.0)
        }
        return arrayListOf(answer_x, answer_y)
    }


    private fun moveChildren(particle_mother: InstantParticle_Mother, step_length: Float, gyro: Float){
        var cur_idx = -1
        while(true){
            var gyro_result = gyro
            cur_idx += 1
            if(cur_idx == particle_mother.particle_children_list.size){
                break
            }
            var children = particle_mother.particle_children_list[cur_idx]

            children.x -= (step_length * 10 * sin((particle_mother.my_angle - gyro_result).toFloat() * PI / 180)).toFloat()
            children.y += (step_length * 10 * cos((particle_mother.my_angle - gyro_result).toFloat() * PI / 180)).toFloat()
            if (mapVector.isPossiblePosition(children.x.toDouble(), children.y.toDouble()) == false){
                particle_mother.removeChildren(cur_idx)
                cur_idx -= 1
            }
        }

    }

    private fun calculate_magnitude(vector: Array<Float>) : Float{
        return sqrt(vector[0].pow(2) + vector[1].pow(2) + vector[2].pow(2))
    }

    private fun matchingChildren(particle_mother: InstantParticle_Mother, vector_list: Array<Float>){
        var golden_ticket = true
        var cur_idx = -1
        while(true){
            cur_idx += 1
            if(cur_idx == particle_mother.particle_children_list.size){
                break
            }
            var children = particle_mother.particle_children_list[cur_idx]

            var childrens_vector_original = mapVector.getData(children.x.toDouble(), children.y.toDouble())

            children.sequence_average = arrayOf(
                ((children.sequence_average[0] * cur_step + childrens_vector_original[0]).toDouble() / (cur_step + 1).toDouble()),
                ((children.sequence_average[1] * cur_step + childrens_vector_original[1]).toDouble() / (cur_step + 1).toDouble()),
                ((children.sequence_average[2] * cur_step + childrens_vector_original[2]).toDouble() / (cur_step + 1).toDouble()))

            var childrens_vector = arrayOf(
                (childrens_vector_original[0] - children.sequence_average[0]).toFloat(),
                (childrens_vector_original[1] - children.sequence_average[1]).toFloat(),
                (childrens_vector_original[2] - children.sequence_average[2]).toFloat())


            var diffX = abs(childrens_vector[0] - vector_list[0])
            var diffY = abs(childrens_vector[1] - vector_list[1])
            var diffZ = abs(childrens_vector[2] - vector_list[2])
            var diffM = abs(calculate_magnitude(arrayOf(childrens_vector[0].toFloat(), childrens_vector[1].toFloat(), childrens_vector[2].toFloat())) - sampled_vector_magnitude)



            if (diffM <= magnitude_threshold){
                children.weight += 1 * (cur_step) // 210913 원준 수정 : 걸음이 진행됨에 따라 weight의 중요도를 높임
            }else{
                children.weight -= (diffM - magnitude_threshold) * (cur_step * weight_according_step) // 210913 원준 수정 : 걸음이 진행됨에 따라 weight의 중요도를 높임
            }

            if((diffX <= vector_threshold) && (diffY <= vector_threshold) && (diffZ <= vector_threshold)){ // 210913 원준 수정 : and --> &&
                if(diffX <= vector_threshold_second){
                    children.weight += vector_weight * cur_step
                }else{
                    children.weight -= ((diffX - vector_threshold_second) * vector_weight)
                }
                if(diffY <= vector_threshold_second){
                    children.weight += vector_weight * cur_step
                }else{
                    children.weight -= ((diffY - vector_threshold_second) * vector_weight)
                }
                if(diffZ <= vector_threshold_second){
                    children.weight += vector_weight * cur_step
                }else{
                    children.weight -= ((diffZ - vector_threshold_second) * vector_weight)
                }

                if((cur_state == 0) && (cur_step >= 5)){
                    if(children.weight <= 1){
                        particle_mother.removeChildren(cur_idx)
                        cur_idx -= 1
                    }
                }
            }
            else{
                if((cur_step >= 5) && (golden_ticket == true)){
                    if(particle_mother.particle_children_list.sortedByDescending{it.weight}[0] == children){
                        golden_ticket = false
                        continue
                    }
                }
                particle_mother.removeChildren(cur_idx)
                cur_idx -= 1
            }
        }
    }

    private fun filteringChildren(particle_mother: InstantParticle_Mother){
        // TODO : 필터링 방법 다시 생각
//        particle_mother.particle_children_list = particle_mother.particle_children_list.sortedByDescending{it.weight}
//        particle_mother.particle_children_list =
//            particle_mother.particle_children_list.slice(0 .. ((particle_mother.particle_children_list).size * 0.5).toInt()-1)

    }

    private fun filteringMother(mother_list: List<InstantParticle_Mother>) : List<InstantParticle_Mother> {
        var sorted_mother_list = mother_list.sortedByDescending{it.win_num}
        if (sorted_mother_list.size == 0) {
            return arrayListOf()
        }
        var best_mother = sorted_mother_list[0]
        return listOf(best_mother)
    }
}
