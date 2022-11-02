package com.example.cslabposco

import kotlin.math.*
import android.util.Log
import java.io.InputStream

/*
2020. 11. 21.
- Instant Localization 알고리즘 더 간결하게 수정.
- 현재 측정된 값 그 자체로 비교하는 것이 아닌, 변화량 값으로 비교하는 방법으로 수정. (Dynamic bias normalization)
*/

internal class InstantLocalization constructor(map_hand: Map,
                                               private var map_for_instant_hand: InputStream,
                                               map_pocket: Map,
                                               private var map_for_instant_pocket: InputStream){
    private var instant_result: Array<String> = arrayOf("", "", "", "")

    // 맵 파일 읽기
    private var mapVector_hand = map_hand
    private var mapVector_pocket = map_pocket
    private var mapVector_temp_hand = __read_hand_map_file()
    private var mapVector_temp_pocket = __read_pocket_map_file()

    private var mapVector = mapVector_hand
    private var mapVector_temp = mapVector_temp_hand

    // 필요 변수들
    private var angleList : IntArray = (0..359 step 45).toList().toIntArray()
    private var sampled_sequence_average_list : ArrayList<Array<Float>> = arrayListOf()
    private var cur_step : Int = -1
    private var sampled_vector_magnitude : Float = 0.0f
    private var instant_particle_mother_list = arrayListOf<InstantParticle_Mother>()
    private var pre_state : Int = 0
    private var cur_state : Int = 0

    private var vector_threshold: Float = 7.0f
    private var vector_threshold_second: Float = 3.0f
    private var magnitude_threshold: Float = 3.0f
    private var firstThreshold: Float = 5.0f
    private var weight_according_step: Float = 1.0f
    private var vector_weight: Float = 1.0f


    init {
        // threshold 값 세팅
        __set_hand_threshold()
    }

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
        else if ((state == 1) || (state == 2)){
            mapVector = mapVector_pocket
            mapVector_temp = mapVector_temp_pocket
        }
    }

    private fun __set_hand_threshold() {
        vector_threshold = 5.0f
        vector_threshold_second = 3.0f
        magnitude_threshold = 3.0f
        firstThreshold = 6.0f
        weight_according_step = 0.5f
        vector_weight = 0.5f
    }
    private fun __set_pocket_threshold() {
        vector_threshold = 20.0f
        vector_threshold_second = 10.0f
        magnitude_threshold = 10.0f
        firstThreshold = 30.0f
        weight_according_step = 1.0f
        vector_weight = 1.0f
    }

    private fun __read_hand_map_file() : ArrayList<FloatArray>{
        var splitData : Array<String>
        var mapVector_temp = arrayListOf<FloatArray>()
        map_for_instant_hand.bufferedReader().useLines { lines -> lines.forEach{
            splitData = it.split("\t").toTypedArray()
            mapVector_temp.add(floatArrayOf(splitData[0].toFloat(), splitData[1].toFloat(), splitData[2].toFloat(), splitData[3].toFloat(), splitData[4].toFloat()))
        }}
        return mapVector_temp
    }
    private fun __read_pocket_map_file() : ArrayList<FloatArray>{
        var splitData : Array<String>
        var mapVector_temp = arrayListOf<FloatArray>()
        // TODO : 이건 불필요한 듯. 왜냐면, 어짜피 mapVector_temp는 가장 처음(앱 start)에만 필요하기 때문
//        map_for_instant_pocket.bufferedReader().useLines { lines -> lines.forEach{
//            splitData = it.split("\t").toTypedArray()
//            mapVector_temp.add(floatArrayOf(splitData[0].toFloat(), splitData[1].toFloat(), splitData[2].toFloat(), splitData[3].toFloat(), splitData[4].toFloat()))
//        }}
        return mapVector_temp
    }

    private fun __first_matching_with_map_and_create_mothers(vectorList : ArrayList<Array<Float>>, state_changed : Boolean = false) {
        sampled_sequence_average_list = vectorList
        if (!state_changed) {

            for (i in angleList.indices){
                var mother_born = false
                for (row_idx in mapVector_temp.indices) {

                    var mapValue = arrayOf(mapVector_temp[row_idx][2].toDouble(), mapVector_temp[row_idx][3].toDouble(), mapVector_temp[row_idx][4].toDouble())
                    var diffX  = abs(vectorList[i][0] - mapValue[0])
                    var diffY  = abs(vectorList[i][1] - mapValue[1])
                    var diffZ  = abs(vectorList[i][2] - mapValue[2])
                    if((diffX <= firstThreshold) && (diffY <= firstThreshold) && (diffZ<= firstThreshold)) {
                        if (mother_born == false) {
                            instant_particle_mother_list.add(InstantParticle_Mother(angleList[i]))
                            mother_born = true
                        }
                        instant_particle_mother_list[instant_particle_mother_list.size - 1].appendChildren(arrayListOf(mapVector_temp[row_idx][0].toFloat(), mapVector_temp[row_idx][1].toFloat()), mapValue)
                    }
                }
            }

            __only_take_n_mothers(n=10)
        }
        else if (state_changed) {
            Log.d("testtest", "${state_changed}")

            for (mother in instant_particle_mother_list) {
                var cur_idx = -1
                while (true){
                    cur_idx += 1
                    if (cur_idx == mother.particle_children_list.size)
                        break
                    var child = mother.particle_children_list[cur_idx]
                    var mapValue = mapVector.getData(child.x.toDouble(), child.y.toDouble())
                    child.sequence_average = mapValue
                    var angle_index = angleList.indexOf(mother.my_angle)
                    var diffX  = abs(vectorList[angle_index][0] - mapValue[0])
                    var diffY  = abs(vectorList[angle_index][1] - mapValue[1])
                    var diffZ  = abs(vectorList[angle_index][2] - mapValue[2])
                    if(!((diffX <= firstThreshold) && (diffY <= firstThreshold) && (diffZ<= firstThreshold))) {
                        mother.removeChildren(cur_idx)
                        cur_idx -= 1
                    }
                }

            }
        }
    }
    private fun __only_take_n_mothers(n : Int = 15) {
        var take_n_mother = n
        if (instant_particle_mother_list.size < take_n_mother) {
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


    fun getLocation(magx : Float, magy : Float, magz : Float, stepLength : Float, gyro : Float, state : Int) : Array<String> {
        if (instant_result[0] == "완전 수렴") {
            Log.d("aon", "aon start!")
            __init_for_always_on_mode(instant_result[1])
        }

        var state_changed = __state_changed(state)
        if (state_changed) {
            __change_threshold(state)
//            __change_map(state)
            sampled_sequence_average_list = arrayListOf(arrayOf())
            cur_step = -1
            sampled_vector_magnitude = 0.0f
        }

        cur_step += 1
        val vectorList = createVectorForEachOrientation(arrayOf(magx, magy, magz))
//        sampled_vector_magnitude = calculate_magnitude(arrayOf(magx, magy, magz))
        sampled_vector_magnitude = calculate_magnitude(vectorList[0])

//        if(stepLength == 0.0f){
        if(cur_step == 0){
            if (state_changed) {
                for (mother in instant_particle_mother_list){
                    moveChildren(mother, stepLength, gyro)
                }
            }

            __first_matching_with_map_and_create_mothers(vectorList, state_changed)

            instant_result = arrayOf("수렴되지 않음", "unknown", "unknown", "unknown")
            return instant_result
        }

        var cur_idx = -1
        while(true) {
            cur_idx += 1
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
            // n 번째 걸음이라면, 아이들 대규모로 삭제.
            if ((cur_step == 5) && (particle_mother.particle_children_list.size != 0)) {
                filteringChildren(particle_mother)
            }
//            else if ((cur_step == 15) && (particle_mother.particle_children_list.size != 0)) {
//                var cut_num = 5
//                if (particle_mother.particle_children_list.size < cut_num){
//                    cut_num = particle_mother.particle_children_list.size
//                }
//                particle_mother.particle_children_list = particle_mother.particle_children_list.sortedByDescending{it.weight}.slice(0 .. cut_num - 1)
//            }
            if (cur_idx == (instant_particle_mother_list.size - 1)) {
                break
            }
        }

        // TODO : 수렴 속도 빠르게 하기 위한 다른 방법 생각
//        if(cur_step <= 9){
//            instant_particle_mother_list = ArrayList(instant_particle_mother_list.sortedByDescending {it.particle_children_list.size})
//            var num_mother = instant_particle_mother_list.size
//            var number_of_winner = 0
//
//            if(num_mother >= 3){
//                number_of_winner = 3
//            }else{
//                number_of_winner = num_mother
//            }
//            for(i in (0 .. number_of_winner-1)){
//                instant_particle_mother_list[i].win_num += (3-i)
//            }
//        }

//        if(cur_step == 9){
//            instant_particle_mother_list = ArrayList(filteringMother(instant_particle_mother_list))
//        }

        var result = estimateInitialDirAndPos(instant_particle_mother_list, gyro)
        instant_result = result
        return result
    }

    private fun __init_for_always_on_mode(cur_gyro: String) {
//        cur_gyro = int(float(cur_gyro))
//        self.angleList = [i for i in range((360-cur_gyro)-40, (360-cur_gyro)+41, 10)]
        angleList = (0..359 step 45).toList().toIntArray()
        sampled_sequence_average_list = arrayListOf()
        cur_step = -1
        sampled_vector_magnitude = 0.0f
        instant_particle_mother_list = arrayListOf<InstantParticle_Mother>()
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


    private fun estimateInitialDirAndPos(mother_list: List<InstantParticle_Mother>, gyro: Float): Array<String> {
        var num_of_mother = instant_particle_mother_list.size
        lateinit var best_mother : InstantParticle_Mother

        if(num_of_mother >= 3){
            if (num_of_mother <= 6){
                var sorted_mother = ArrayList(instant_particle_mother_list.sortedByDescending {it.particle_children_list.size})
                var first_mother = sorted_mother[0]
                var second_mother = sorted_mother[1]
                if (second_mother.particle_children_list.size >= 1) {
                    if (cal_difference_angle(first_mother.my_angle, second_mother.my_angle) <= 10) {
                        var first_mother_answer = calculate_answer_position(first_mother)
                        var second_mother_answer = calculate_answer_position(second_mother)
                        if (first_mother_answer == arrayListOf(-1.0, -1.0) || second_mother_answer == arrayListOf(-1.0, -1.0)) {
                            return arrayOf("수렴되지 않음", "unknown", "unknown", "unknown")
                        }
                        else {
                            var distance_with_two_answer = sqrt(Math.pow(first_mother_answer[0]-second_mother_answer[0],2.0) + Math.pow(first_mother_answer[1]-second_mother_answer[1], 2.0)) * 0.1
                            if (distance_with_two_answer <= 1.5) {
                                var answer_dir_temp = if (abs(first_mother.my_angle - second_mother.my_angle) != 350) (first_mother.my_angle + second_mother.my_angle) / 2 else (first_mother.my_angle + second_mother.my_angle + 360) / 2
                                var answer_dir = ((((360 - answer_dir_temp)+ gyro)+360)%360).toString()
                                var answer_x = ((first_mother_answer[0] + second_mother_answer[0]) / 2).toString()
                                var answer_y = ((first_mother_answer[1] + second_mother_answer[1]) / 2).toString()
                                return arrayOf("완전 수렴", answer_dir, answer_x, answer_y)
                            }

                            else{
                                return arrayOf("수렴되지 않음", "unknown", "unknown", "unknown")
                            }

                        }
                    }
                    else if (second_mother.particle_children_list.size == 1 && first_mother.particle_children_list.size != 1) {
                        best_mother = first_mother
                    }
                    else {
                        return arrayOf("수렴되지 않음", "unknown", "unknown", "unknown")
                    }
                }

                else {
                    return arrayOf("수렴되지 않음", "unknown", "unknown", "unknown")
                }
            }
            else {
                return arrayOf("수렴되지 않음", "unknown", "unknown", "unknown")
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
            return arrayOf("에러! 일치되는 좌표가 없음", "unknown", "unknown", "unknown")
        }

        // 혹시 모를 에러를 방지
        var num_of_children = best_mother.particle_children_list.size
        if (num_of_children == 0) {
            return arrayOf("에러! 일치되는 좌표가 없음", "unknown", "unknown", "unknown")
        }

        var answer_position_xy = calculate_answer_position(best_mother)
        var answer_x = answer_position_xy[0]
        var answer_y = answer_position_xy[1]
        var answer_dir = ((((360 - (best_mother.my_angle).toInt()) + gyro) + 360) % 360)

        if (answer_position_xy == arrayListOf(-1.0, -1.0)) {
            return arrayOf("방향만 수렴", answer_dir.toString(), "unknown", "unknown")
        }
        else {
            return arrayOf("완전 수렴", answer_dir.toString(), answer_x.toString(), answer_y.toString())
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
