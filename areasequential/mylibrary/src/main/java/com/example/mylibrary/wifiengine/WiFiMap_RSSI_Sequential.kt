package com.example.mylibrary.wifiengine

import android.util.Log
import com.example.mylibrary.maps.WiFiDataMap
import com.example.mylibrary.maps.MagneticFieldMap
import kotlin.math.*


class WiFiMap_RSSI_Sequential constructor(wiFiDataMap: WiFiDataMap, map_hand: MagneticFieldMap) {
    private var instant_result : MutableMap<String, Float> = mutableMapOf("status_code" to -1.0f, "gyro_from_map" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)

    internal var pos = arrayListOf<Int>()
    internal var posx = arrayListOf<Int>()
    internal var posy = arrayListOf<Int>()

    internal lateinit var wifilist : Array<String>
    var wifilistsize : Int = 0
    var wifidata = ""
    var if_first = true

    private var wifi = mutableMapOf<Int, Array<String>>()
    private var wifi_rssi = mutableMapOf<Int, Array<String>>()

    private var temp_wifi_rssi = mutableMapOf<Int, Double>()
    private var temp_wifi_cnt = mutableMapOf<Int, Int>()


    private var mapWidth : Int = 0
    private var mapHeight : Int = 0

    private var mapVector = map_hand

    var universal_range = arrayListOf(0.0f, 0.0f, 0.0f, 0.0f)
    var universal_range2 = arrayListOf(0.0f, 0.0f, 0.0f, 0.0f)

    var ans_range = arrayListOf(0.0f, 0.0f, 0.0f, 0.0f)

    private lateinit var test_vector : Array<Int>
    private lateinit var rssi_vector : Array<Int>

    private var angle_step_num : Int = 10
    private var angleList : IntArray = (0..359 step angle_step_num).toList().toIntArray()

    var foundpos = false
    var founddir = false

    var cur_step = -1

    // hanasquare
/*    private var early_stop_in_n_mother : Float = 4.0f
    private var rssi_thres = -75
    private var range_thres = 7
    private var second_range_thres = 7 //7
    private var rssi_range_num = 40
    private var second_rssi_range_num = 40*/

    // anam staion
    // 지하 [-75,3], 플랫폼 [-66,4]
    private var early_stop_in_n_mother : Float = 4.0f
    private var rssi_thres = -75
    private var range_thres = 7
    private var second_range_thres = 7 //7
    private var rssi_range_num = 40
    private var second_rssi_range_num = 40


    var rssi_rangeval = 0.0
    var rangeval = 0

    var final_posx_list = arrayListOf(0)
    var final_posy_list = arrayListOf(0)

    private var totally_converge_succss = false
    var distance_threshold = 1000000

    var temp_gyro = 0.0f

    private var wifi_particle_mother_list = arrayListOf<WiFiParticle_Mother>()

    var range_check = arrayListOf(0.0f, 0.0f, 0.0f, 0.0f)

    var area_check_pos_list = arrayListOf<Array<Float>>()
    var init_area = true

    var orig_wifi_data = ""
    var wifi_data_change = false

    var correction_angle = 0.0f

    var area_check_range = arrayListOf(0.0f, 0.0f, 0.0f, 0.0f)
    var particle_num = 0

    init {
        wifi = wiFiDataMap.wifi
        wifi_rssi = wiFiDataMap.wifi_rssi
        wifilist = wiFiDataMap.wifilist
        wifilistsize = wiFiDataMap.wifilistsize

        mapWidth = wiFiDataMap.mapWidth
        mapHeight = wiFiDataMap.mapHeight

        pos = wiFiDataMap.pos
        posx = wiFiDataMap.posx
        posy = wiFiDataMap.posy

        test_vector = Array(wifilistsize, {0})
        rssi_vector = Array(wifilistsize, {0})

        instant_result = mutableMapOf("status_code" to -1.0f, "gyro_from_map" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)
    }

    private fun init_parameters(status_code:Float, gyro:Float=0.0f){
        if ((status_code == 200.0f) || (status_code == 202.0f)){
            var cur_gyro = gyro.toInt()
//            angleList = ((360-cur_gyro)-90..(360-cur_gyro)+90 step 90).toList().toIntArray()
//            angleList = ((360-cur_gyro)-90..(360-cur_gyro)+90 step angle_step_num).toList().toIntArray()
            angleList = (0..359 step angle_step_num).toList().toIntArray()
            cur_step = -1
            wifi_particle_mother_list = arrayListOf<WiFiParticle_Mother>()
            instant_result = mutableMapOf("status_code" to -1.0f, "gyro_from_map" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)
        }
        else if (status_code == 400.0f) {

            angleList = (0..359 step angle_step_num).toList().toIntArray()
            cur_step = -1
            wifi_particle_mother_list = arrayListOf<WiFiParticle_Mother>()
            instant_result = mutableMapOf("status_code" to -1.0f, "gyro_from_map" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)
        }
    }
    private fun check_wifi_change(wifi_string: String){
        if (orig_wifi_data != wifi_string){
            orig_wifi_data = wifi_string
            wifi_data_change = true
        }
    }

    fun getLocation(wifi_string: String, stepLength: Double, gyro_for_pf: Float, gyro: Float, pf_result: Array<Double>) : MutableMap<String, Float> {

        if(!foundpos) {
            instant_result = getlocation_WF(wifi_string, stepLength, gyro, gyro_for_pf)
        }
        else {
            instant_result = getlocation_WF(wifi_string, stepLength, gyro, gyro_for_pf)
            instant_result["status_code"] = 400.0f
        }
        return instant_result
    }

    fun getlocation_WF(wifi_string: String, stepLength: Double, gyro: Float, gyro_for_pf:Float) : MutableMap<String, Float>{
        init_parameters(instant_result["status_code"]!!, gyro.toFloat())

        cur_step += 1
        check_wifi_change(wifi_string)

        vectorcompare(wifi_string, cur_step)
        if(foundpos){
//            universal_range = moveRange(ans_range, universal_range, stepLength, gyro_for_pf)
            universal_range = ans_range
//            universal_range = moveRange2(ans_range, universal_range, stepLength)
            range_check = universal_range
            universal_range2 = moveRange2(ans_range, universal_range2, stepLength)
        }
        else{
            universal_range = ans_range
            universal_range2 = moveRange2(ans_range, universal_range2, stepLength)
        }

        if(foundpos){
            moveArea(stepLength, gyro_for_pf)
            if(area_check_pos_list.size == 0){
                correction_angle = 0.0f
                first_find_area(universal_range2)
            }
        }else{
//            first_find_area(ans_range)
            first_find_area(universal_range2)
        }
        wifi_data_change = false

        if (instant_result["status_code"] == 400.0f){
            return instant_result
        }
        if(!foundpos) {
            if (cur_step == 0) {
                if (foundpos) {
                    first_find_particles(universal_range2)
                } else {
                    first_find_particles(
                        arrayListOf(
                            ans_range[0], ans_range[1],
                            ans_range[2], ans_range[3]
                        )
                    )
                }
                instant_result["status_code"] = 100.0f
                return instant_result
            }

            var cur_idx = -1
            while (true) {
                cur_idx += 1
                if (wifi_particle_mother_list.size == 0) {
                    instant_result["status_code"] = 400.0f
                    return instant_result
                }
                var particle_mother = wifi_particle_mother_list[cur_idx]
                moveChildren(particle_mother, stepLength, gyro)
//                matchingChildren(particle_mother)
                if (particle_mother.particle_children_list.size == 0) {
                    wifi_particle_mother_list.remove(particle_mother)
                    cur_idx -= 1
                }
                if (cur_idx == (wifi_particle_mother_list.size - 1)) {
                    break
                }
            }
            instant_result = estimateInitialDirAndPos(wifi_particle_mother_list, gyro.toFloat())
        }
        return instant_result
    }

    fun vectorcompare(wifi_string: String, cur_step : Int){
        wifidata = wifi_string
        temp_wifi_cnt = mutableMapOf<Int, Int>()
        temp_wifi_rssi = mutableMapOf<Int, Double>()

        test_vector = Array(wifilistsize, {0})
        rssi_vector = Array(wifilistsize, {0})

        var cnt_list = arrayListOf<Int>()
        var rssi_diff_list = arrayListOf<Double>()

        var splitline = wifi_string.split("\r\n").toTypedArray()
        for (i in splitline){
            var data = i.split("\t").toTypedArray()
            if(data.size == 2) {
                var SSID = data[0]
                var RSSI = data[1].toInt()

                if ((wifilist.indexOf(SSID) != -1) and (RSSI >= rssi_thres)) {
                    test_vector[wifilist.indexOf(SSID)] = 1
                    rssi_vector[wifilist.indexOf(SSID)] = RSSI
                }
            }
        }

        var range_idx = range_thres
        var rssi_range_idx = rssi_range_num

        for (i in pos){
            var both_cnt = 0

            for (j in 0 .. wifilistsize - 1 step(1)){
                if(test_vector[j] + wifi[i]!![j].toInt() == 2){
                    both_cnt += 1
                }
            }
            temp_wifi_cnt[i] = both_cnt
            cnt_list.add(both_cnt)
        }

        var unq_cnt_list = cnt_list.distinct().sortedDescending()

        var compare_val = 0

        if (cur_step == 0){
            compare_val = range_thres
        }else{
            compare_val = second_range_thres
        }

        if (unq_cnt_list.size <= compare_val){
            range_idx = unq_cnt_list.size - 1
        } else{
            range_idx = compare_val
        }

        rangeval = unq_cnt_list[range_idx]

        for (i in pos){
            var both_cnt = 0
            var rssi_sum = 0.0
            for (j in 0..wifilistsize - 1 step (1)) {
                if ((test_vector[j] + wifi[i]!![j].toInt() == 2) and (rssi_vector[j] != 0)) {
                    both_cnt += 1
                    rssi_sum += Math.abs(rssi_vector[j] - wifi_rssi[i]!![j].toInt())

                }


            }
            temp_wifi_rssi[i] = rssi_sum
            if (both_cnt >= rangeval){
                rssi_diff_list.add(rssi_sum / both_cnt)
            } else{
                rssi_diff_list.add(100000.0)
            }
        }

        var unq_rssi_diff_list = rssi_diff_list.distinct().sorted()

        if (cur_step == 0){
            compare_val = rssi_range_num
        }else{
            compare_val = second_rssi_range_num
        }

        if (unq_rssi_diff_list.size <= compare_val){
            rssi_range_idx = unq_rssi_diff_list.size - 1
        } else{
            rssi_range_idx = compare_val
        }
        rssi_rangeval = unq_rssi_diff_list[rssi_range_idx]

        var for_range_rangeval = rangeval//unq_cnt_list[5]
        var for_range_rssi_rangeval = rssi_rangeval//unq_rssi_diff_list[40]

        var x_list = arrayListOf<Int>()
        var y_list = arrayListOf<Int>()

        for (i in pos.indices){
            if((cnt_list[i] >= for_range_rangeval) and (rssi_diff_list[i] <= for_range_rssi_rangeval)){
                x_list.add(posx[i])
                y_list.add(posy[i])
            }
        }

        if (x_list.minOrNull() == null){
            ans_range = arrayListOf(0.0f, mapWidth.toFloat(), 0.0f, mapHeight.toFloat())
        }else{
            ans_range = arrayListOf(x_list.minOrNull()!!.toFloat(), x_list.maxOrNull()!!.toFloat(),
                y_list.minOrNull()!!.toFloat(), y_list.maxOrNull()!!.toFloat())
        }
    }
    fun first_find_area(ans_range : ArrayList<Float>){
        var minX = ans_range[0]
        var maxX = ans_range[1]
        var minY = ans_range[2]
        var maxY = ans_range[3]

        area_check_pos_list = arrayListOf<Array<Float>>()
        for (i in pos.indices){
            if((minX <= posx[i]) && (posx[i] <= maxX) && (minY <= posy[i]) && (posy[i] <= maxY)){
                if (mapVector.isPossiblePosition(posx[i].toDouble(), posy[i].toDouble())) {
                    area_check_pos_list.add(arrayOf(posx[i].toFloat(), posy[i].toFloat()))
                }
            }
        }

    }
    fun first_find_particles(ans_range : ArrayList<Float>){
        var minX = ans_range[0]
        var maxX = ans_range[1]
        var minY = ans_range[2]
        var maxY = ans_range[3]

        var coordx = arrayListOf<Int>()
        var coordy = arrayListOf<Int>()

        for (i in pos.indices){
            if((minX <= posx[i]) && (posx[i] <= maxX) && (minY <= posy[i]) && (posy[i] <= maxY)){
                coordx.add(posx[i])
                coordy.add(posy[i])
            }
        }

        for (i in angleList.indices){
            wifi_particle_mother_list.add(WiFiParticle_Mother(angleList[i]))
            for (j in coordx.indices){
                if (wifi_particle_mother_list.size == 0) {
                    wifi_particle_mother_list.add(WiFiParticle_Mother(angleList[i]))
                }
                wifi_particle_mother_list[wifi_particle_mother_list.size - 1].appendChildren(
                    arrayListOf(coordx[j].toFloat(), coordy[j].toFloat()))
            }
        }
    }

//    fun first_find_particles(ans_range : ArrayList<Float>){
//        var minX = ans_range[0]
//        var maxX = ans_range[1]
//        var minY = ans_range[2]
//        var maxY = ans_range[3]
//
//        var coordx = arrayListOf<Int>()
//        var coordy = arrayListOf<Int>()
//
//        var coord_particle = listOf<WiFiParticle>()
//        for (i in pos.indices){
//            if((minX <= posx[i]) && (posx[i] <= maxX) && (minY <= posy[i]) && (posy[i] <= maxY)){
//                coord_particle = coord_particle.plus(WiFiParticle(arrayListOf(posx[i].toFloat(), posy[i].toFloat())))
//            }

    //
//        for (i in angleList.indices){
//            wifi_particle_mother_list.add(WiFiParticle_Mother(angleList[i]))
//            if (wifi_particle_mother_list.size == 0) {
//                wifi_particle_mother_list.add(WiFiParticle_Mother(angleList[i]))
//            }
//            wifi_particle_mother_list[wifi_particle_mother_list.size - 1].add_children_whole(coord_particle)
//            }
//
//
//    }
    fun moveRange(ans_range: ArrayList<Float>, universal_range:ArrayList<Float>, step_length : Double, gyro : Float)
            : ArrayList<Float>{
        var minX = universal_range[0]
        var maxX = universal_range[1]
        var minY = universal_range[2]
        var maxY = universal_range[3]

        minX -= (step_length * 10 * sin((-gyro) * PI / 180)).toFloat()
        maxX -= (step_length * 10 * sin((-gyro) * PI / 180)).toFloat()

        minY += (step_length * 10 * cos((-gyro) * PI / 180)).toFloat()
        maxY += (step_length * 10 * cos((-gyro) * PI / 180)).toFloat()

        minX = max(ans_range[0], minX)
        maxX = min(ans_range[1], maxX)

        minY = max(ans_range[2], minY)
        maxY = min(ans_range[3], maxY)

        if((minX >= maxX - 6) || (minY >= maxY - 6)){
            return arrayListOf(ans_range[0], ans_range[1], ans_range[2], ans_range[3])
            instant_result["status_code"] = 400.0f
        }else{
            return arrayListOf(minX, maxX, minY, maxY)
        }
//        return arrayListOf(ans_range[0], ans_range[1], ans_range[2], ans_range[3])
    }

    fun moveArea(step_length : Double, gyro : Float){
        var gyro_result = gyro
        var temp_area_list = arrayListOf<Array<Float>>()
        var minx = 1000000f
        var miny = 1000000f
        var maxx = 0f
        var maxy = 0f
        for (pos in area_check_pos_list) {
            var posx = pos[0]
            var posy = pos[1]
            posx -= (step_length * 10 * sin((- correction_angle - gyro_result) * PI / 180)).toFloat()
            posy += (step_length * 10 * cos((- correction_angle - gyro_result) * PI / 180)).toFloat()

            if (mapVector.isPossiblePosition(
                    posx.toDouble(),
                    posy.toDouble()
                ) == true
            ) {
                if (check_in_range(posx.toDouble(), posy.toDouble(), universal_range2)) {
                    temp_area_list.add(arrayOf(posx, posy))
                    minx = min(posx, minx)
                    maxx = max(posx, maxx)
                    miny = min(posy, miny)
                    maxy = max(posy, maxy)
                }
            }
        }
        if (temp_area_list.size == 0){
            var cnt = 0
            var ans_angle = 0.0f
            while(true){
                if(cnt == 19){
                    area_check_pos_list = temp_area_list
                    break
                }
                if(temp_area_list.size != 0){
                    area_check_pos_list = temp_area_list
                    correction_angle = (ans_angle + 360) % 360
                    break
                }
                cnt += 1
                var temp_1 = find_angle(step_length, gyro, 10.0f * (cnt))
                var temp_2 = find_angle(step_length, gyro, -10.0f * (cnt))
                if((temp_1.size != 0) || (temp_2.size != 0)){
                    if(temp_1.size >= temp_2.size){
                        temp_area_list = temp_1
                        ans_angle = 10.0f * cnt
                    }else{
                        temp_area_list = temp_2
                        ans_angle = -10.0f * cnt
                    }
                }
            }
        }else{
            area_check_pos_list = temp_area_list
            area_check_range[0] = minx
            area_check_range[1] = maxx
            area_check_range[2] = miny
            area_check_range[3] = maxy
        }
        if(area_check_pos_list.size != 0) {
            if ((area_check_range[1] - area_check_range[0]) < 36) {
                var temp_area_list = arrayListOf<Array<Float>>()
                var x_avg = (minx + maxx) / 2
//                x_avg -= 3 * (step_length * 10 * sin((- correction_angle - gyro_result) * PI / 180)).toFloat()
                var x_min = (x_avg - 18).toInt()
                var x_max = (x_avg + 18).toInt()
                var y_min = miny.toInt()
                var y_max = maxy.toInt()
                for (i in (x_min)..(x_max)) {
                    for (j in (y_min)..(y_max)) {
                        if (mapVector.isPossiblePosition(
                                i.toDouble(),
                                j.toDouble()
                            ) == true
                        ) {
                            temp_area_list.add(arrayOf(i.toFloat(), j.toFloat()))
                        }
                    }
                }
                area_check_range[0] = x_min.toFloat()
                area_check_range[1] = x_max.toFloat()

                area_check_pos_list = temp_area_list
            }
            if ((area_check_range[3] - area_check_range[2]) < 36) {
                var temp_area_list = arrayListOf<Array<Float>>()
                var y_avg = (miny + maxy) / 2
//                y_avg += 3 * (step_length * 10 * cos((- correction_angle - gyro_result) * PI / 180)).toFloat()
                var x_min = minx.toInt()
                var x_max = maxx.toInt()
                var y_min = (y_avg - 18).toInt()
                var y_max = (y_avg + 18).toInt()
                for (i in (x_min)..(x_max)) {
                    for (j in (y_min)..(y_max)) {
                        if (mapVector.isPossiblePosition(
                                i.toDouble(),
                                j.toDouble()
                            ) == true
                        ) {
                            temp_area_list.add(arrayOf(i.toFloat(), j.toFloat()))
                        }
                    }
                }
                area_check_range[2] = y_min.toFloat()
                area_check_range[3] = y_max.toFloat()

                area_check_pos_list = temp_area_list
            }
        }
    }

    fun find_angle(step_length : Double, gyro : Float, search_angle : Float) : ArrayList<Array<Float>>{
        var gyro_result = gyro
        var temp_area_list = arrayListOf<Array<Float>>()
        var minx = 1000000f
        var miny = 1000000f
        var maxx = 0f
        var maxy = 0f
        for (pos in area_check_pos_list) {
            var posx = pos[0]
            var posy = pos[1]
            posx -= (step_length * 10 * sin((- correction_angle - search_angle -gyro_result) * PI / 180)).toFloat()
            posy += (step_length * 10 * cos((- correction_angle - search_angle -gyro_result) * PI / 180)).toFloat()

            if (mapVector.isPossiblePosition(
                    posx.toDouble(),
                    posy.toDouble()
                ) == true
            ) {
                if (check_in_range(posx.toDouble(), posy.toDouble(), universal_range2)) {
                    temp_area_list.add(arrayOf(posx, posy))
                    minx = min(posx, minx)
                    maxx = max(posx, maxx)
                    miny = min(posy, miny)
                    maxy = max(posy, maxy)
                }
            }
        }
        if(temp_area_list.size != 0){
            area_check_range[0] = minx
            area_check_range[1] = maxx
            area_check_range[2] = miny
            area_check_range[3] = maxy
        }
        return temp_area_list
    }

    fun moveRange2(ans_range: ArrayList<Float>, universal_range:ArrayList<Float>, step_length : Double)
            : ArrayList<Float>{
        var minX = universal_range[0]
        var maxX = universal_range[1]
        var minY = universal_range[2]
        var maxY = universal_range[3]

        minX -= (step_length * 10).toFloat()
        maxX += (step_length * 10).toFloat()

        minY -= (step_length * 10).toFloat()
        maxY += (step_length * 10).toFloat()
//
        if(wifi_data_change) {
            minX = max(ans_range[0], minX)
            maxX = min(ans_range[1], maxX)

            minY = max(ans_range[2], minY)
            maxY = min(ans_range[3], maxY)
        }
        if((minX >= maxX - 18) || (minY >= maxY - 18)){
            return arrayListOf(ans_range[0], ans_range[1], ans_range[2], ans_range[3])
        }else{
            return arrayListOf(minX, maxX, minY, maxY)
        }
//        return arrayListOf(ans_range[0], ans_range[1], ans_range[2], ans_range[3])
    }
    fun moveChildren(particle_mother: WiFiParticle_Mother, step_length : Double, gyro : Float) {
        var cur_idx = -1
        while (true) {
            var gyro_result = gyro
            cur_idx += 1
            if (cur_idx == particle_mother.particle_children_list.size) {
                break
            }
            var children = particle_mother.particle_children_list[cur_idx]

            children.x -= (step_length * 10 * sin((particle_mother.my_angle - gyro_result).toFloat() * PI / 180)).toFloat()
            children.y += (step_length * 10 * cos((particle_mother.my_angle - gyro_result).toFloat() * PI / 180)).toFloat()
//            if (mapVector.isPossiblePosition(children.x.toDouble(), children.y.toDouble()) == false) {
            if (check_in_range(
                    children.x.toDouble(),
                    children.y.toDouble(),
                    ans_range
                ) == false
            ) {
                particle_mother.removeChildren(cur_idx)
                cur_idx -= 1
            }
//            }
        }
//        if (wifi_data_change) {
//            while (true) {
//                var gyro_result = gyro
//                cur_idx += 1
//                if (cur_idx == particle_mother.particle_children_list.size) {
//                    break
//                }
//                var children = particle_mother.particle_children_list[cur_idx]
//
//                children.x -= (step_length * 10 * sin((particle_mother.my_angle - gyro_result).toFloat() * PI / 180)).toFloat()
//                children.y += (step_length * 10 * cos((particle_mother.my_angle - gyro_result).toFloat() * PI / 180)).toFloat()
////            if (mapVector.isPossiblePosition(children.x.toDouble(), children.y.toDouble()) == false) {
//                if (check_in_range(
//                        children.x.toDouble(),
//                        children.y.toDouble(),
//                        universal_range
//                    ) == false
//                ) {
//                    particle_mother.removeChildren(cur_idx)
//                    cur_idx -= 1
//                }
////            }
//            }
//            wifi_data_change = false
//        } else {
//            while (true) {
//                var gyro_result = gyro
//                cur_idx += 1
//                if (cur_idx == particle_mother.particle_children_list.size) {
//                    break
//                }
//                var children = particle_mother.particle_children_list[cur_idx]
//
//                children.x -= (step_length * 10 * sin((particle_mother.my_angle - gyro_result).toFloat() * PI / 180)).toFloat()
//                children.y += (step_length * 10 * cos((particle_mother.my_angle - gyro_result).toFloat() * PI / 180)).toFloat()
//
//            }
//        }
    }

    private fun matchingChildren(particle_mother: WiFiParticle_Mother) {
        var cur_idx = -1
        while (true) {
            var remain_bool = false
            cur_idx += 1
            if (cur_idx == particle_mother.particle_children_list.size) {
                break
            }
            var children = particle_mother.particle_children_list[cur_idx]
            var act_x = children.x
            var act_y = children.y

            var min_x = act_x - act_x % 6
            var max_x = (act_x - act_x % 6) + 6

            var min_y = act_y - act_y % 6
            var max_y = (act_y - act_y % 6) + 6

            if ((act_x - min_x) <= (max_x - act_x)) {
                act_x = min_x
            } else {
                act_x = max_x
            }
            if ((act_y - min_y) <= (max_y - act_y)) {
                act_y = min_y
            } else {
                act_y = max_y
            }

            var cur_pos = (act_x * 10000 + act_y).toInt()
//            if ((temp_wifi_cnt.containsKey(cur_pos))) {
////                remain_bool = true
//                if ((temp_wifi_cnt[cur_pos] != 0) && (mapVector.isPossiblePosition(
//                        act_x.toDouble(),
//                        act_y.toDouble()
//                    ))
//                ) {
//                    var value = temp_wifi_rssi[cur_pos]!! / temp_wifi_cnt[cur_pos]!!
//                    if ((temp_wifi_cnt[cur_pos]!! >= rangeval)){// && (value <= rssi_rangeval) && (value != 0.0)){// && (temp_wifi_cnt[cur_pos]!! >= rangeval)) {
//                        remain_bool = true
//                    }
//                }
//            }
            if (mapVector.isPossiblePosition(
                    act_x.toDouble(),
                    act_y.toDouble()
                )){
                remain_bool = true
            }
            if (remain_bool == false) {
                particle_mother.removeChildren(cur_idx)
                cur_idx -= 1
            }
        }
    }
    // 방향 수렴 후 초기 위치 계산(수렴 판단)
    private fun calculate_answer_position(mother : WiFiParticle_Mother) : ArrayList<Double>{
        var answer_x = 0.0
        var answer_y = 0.0
        // 살아남은 파티클들의 x좌표 합(answer_x),y 좌표 합(answer_y) 계산
        for (children in mother.particle_children_list){
            answer_x += children.x
            answer_y += children.y
        }

        // 살아남은 파티클들의 무게중심 계산
        var num_of_children = mother.particle_children_list.size
        answer_x = answer_x / num_of_children
        answer_y = answer_y / num_of_children

        // 무게중심과 파티클 사이의 거리 평균 계산
        var dist_avg = 0.0
        for (children in mother.particle_children_list) {
            dist_avg += sqrt(Math.pow(answer_x - children.x, 2.0) + Math.pow(answer_y - children.y, 2.0)) * 0.1
        }
        dist_avg = dist_avg / mother.particle_children_list.size
        if (dist_avg > 3.0) {
            return arrayListOf(-1.0, -1.0)
        }
        return arrayListOf(answer_x, answer_y)
    }
    // 초기 위치 방향 추정
    private fun estimateInitialDirAndPos(mother_list: List<WiFiParticle_Mother>, gyro: Float): MutableMap<String, Float> {
        var num_of_mother = wifi_particle_mother_list.size
        particle_num = num_of_mother
        lateinit var best_mother : WiFiParticle_Mother

        // 위치 후보군이 여러개일 때
        // IL 진행 중. 아직 수렴 안됨
        if(num_of_mother >= 2){
            return mutableMapOf("status_code" to 100.0f, "gyro_from_map" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)
        }
        // 위치 후보군이 한 개일 때, 방향만 수렴
        else if(num_of_mother == 1){
            best_mother = mother_list[0]
            if(!founddir) {
                founddir = true
//                var minx = 1000000f
//                var miny = 1000000f
//                var maxx = 0f
//                var maxy = 0f
//
//                area_check_pos_list = arrayListOf<Array<Float>>()
//                for (c in best_mother.particle_children_list) {
//                    area_check_pos_list.add(arrayOf(c.x, c.y))
//                }
            }

        }else if(num_of_mother == 0){
            // 400.0f -> IL 혹은 AON 에러. 수렴하지 못함.
            return return mutableMapOf("status_code" to 400.0f, "gyro_from_map" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)
        }
        // 혹시 모를 에러를 방지
        var num_of_children = best_mother.particle_children_list.size
        if (num_of_children == 0) {
            // 400.0f -> IL 혹은 AON 에러. 수렴하지 못함.
            return return mutableMapOf("status_code" to 400.0f, "gyro_from_map" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)
        }

        var answer_position_xy = calculate_answer_position(best_mother)
        var answer_x = answer_position_xy[0].toFloat()
        var answer_y = answer_position_xy[1].toFloat()
        var answer_dir = ((((360 - (best_mother.my_angle).toInt()) + gyro) + 360) % 360)

        if (answer_position_xy == arrayListOf(-1.0, -1.0)) {
            founddir = true
            foundpos = true
            temp_gyro = answer_dir
            // 200.0f -> IL 완료. 완전 수렴
            return mutableMapOf("status_code" to 200.0f, "gyro_from_map" to answer_dir, "pos_x" to answer_x, "pos_y" to answer_y)
//            return return mutableMapOf("status_code" to 101.0f, "gyro_from_map" to answer_dir, "pos_x" to -1.0f, "pos_y" to -1.0f)
        }
        else {
            foundpos = true
            founddir = true
            temp_gyro = answer_dir
            // 200.0f -> IL 완료. 완전 수렴
            return mutableMapOf("status_code" to 200.0f, "gyro_from_map" to answer_dir, "pos_x" to answer_x, "pos_y" to answer_y)
        }
    }
    fun check_in_range(x: Double, y: Double, ans_range: ArrayList<Float>): Boolean{
//        if(wifi_data_change) {
//            var minx = ans_range[0]
//            var maxx = ans_range[1]
//            var miny = ans_range[2]
//            var maxy = ans_range[3]
////            return mutableMapOf("status_code" to 100.0f, "gyro_from_map" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)
//
//            var return_bool = false
//            if ((minx <= x) && (x <= maxx) && (miny <= y) && (y <= maxy)) {
//                return_bool = true
//            }
//            return return_bool
//        }else{
//            return true
//        }
        var minx = ans_range[0]
        var maxx = ans_range[1]
        var miny = ans_range[2]
        var maxy = ans_range[3]
//            return mutableMapOf("status_code" to 100.0f, "gyro_from_map" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)

        var return_bool = false
        if ((minx <= x) && (x <= maxx) && (miny <= y) && (y <= maxy)) {
            return_bool = true
        }
        return return_bool
    }


}