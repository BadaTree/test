package com.example.cslabposco.sensors

import android.util.Log


class GPS{
    var longitude: Double = 0.0
    var latitude: Double = 0.0

    var first_range = arrayOf(0, 0, 0, 0)

        //////////////1구역//////////////////
//    var bin = arrayOf(0)
//    var range_list = arrayOf(
//        arrayOf(0, 252, 0, 1332))
    /////////////////////////////////////

//    //////////////2구역//////////////////
//    var bin = arrayOf(127.0252824, 127.02584039999999)
//    var range_list = arrayOf(
//        arrayOf(0, 222, -6, 732),
//        arrayOf(6, 252, 552, 1284))
//    /////////////////////////////////////

//    //////////////3구역//////////////////
//    var bin = arrayOf(127.0252824, 127.0256544, 127.02602639999999)
//    var range_list = arrayOf(
//        arrayOf(0, 222, -6, 612),
//        arrayOf(0, 252, 228, 1068),
//        arrayOf(84, 252, 618, 1284))
//    /////////////////////////////////////

//    //////////////4구역//////////////////
//    var bin = arrayOf(127.0252824, 127.0255614, 127.0258404, 127.02611940000001)
//    var range_list = arrayOf(
//        arrayOf(0, 222, -6, 486),
//        arrayOf(0, 222, 216, 732),
//        arrayOf(6, 252, 552, 1068),
//        arrayOf(84, 252, 618, 1284))
//    /////////////////////////////////////

//    //////////////5구역//////////////////
//    var bin = arrayOf(127.0252824, 127.0255056, 127.02572880000001, 127.02595200000002, 127.02617520000003)
//    var range_list = arrayOf(
//        arrayOf(0, 222, -6, 486),
//        arrayOf(0, 222, 216, 732),
//        arrayOf(0, 252, 228, 846),
//        arrayOf(84, 252, 612, 1068),
//        arrayOf(84, 252, 618, 1284))
//    /////////////////////////////////////

//        //////////////6구역//////////////////
    var bin = arrayOf(127.0252824, 127.0254684, 127.0256544, 127.02584039999999, 127.02602639999999, 127.02621239999999)
    var range_list = arrayOf(
        arrayOf(0, 222, -6, 474),
        arrayOf(0, 222, 216, 612),
        arrayOf(0, 222, 228, 732),
        arrayOf(6, 252, 552, 1068),
        arrayOf(84, 252, 642, 1260),
        arrayOf(102, 252, 618, 1284))
//    /////////////////////////////////////

//        //////////////7구역//////////////////
//    var bin = arrayOf(127.0252824, 127.02544182857143, 127.02560125714287, 127.0257606857143, 127.02592011428574, 127.02607954285718, 127.02623897142861)
//    var range_list = arrayOf(
//        arrayOf(0, 222, -6, 438),
//        arrayOf(0, 216, 216, 486),
//        arrayOf(0, 222, 246, 732),
//        arrayOf(0, 240, 228, 846),
//        arrayOf(84, 252, 732, 1260),
//        arrayOf(102, 252, 618, 1284),
//        arrayOf(84, 252, 612, 1068))
//    /////////////////////////////////////

    fun find_range(lat: Double, long: Double): Array<Int>{
        var ans_idx = 0
        if(long == 0.0){
            return arrayOf(0, 260, 0, 1600)
        }
        if(bin.size != 1) {
            for (i in bin.indices) {
                if (i == 0) {
                    if ((long <= bin[i]) || ((bin[i] <= long) && (long < bin[i + 1]))) {
                        ans_idx = i
                        break
                    }
                } else {
                    if (i != bin.size - 1) {
                        if ((bin[i] <= long) && (long < bin[i + 1])) {
                            ans_idx = i
                            break
                        }
                    }
                    ans_idx = i
                }
            }
        }
        Log.d("digitize", long.toString() + "\t" + ans_idx.toString())
        first_range = range_list[ans_idx]
        return range_list[ans_idx]
    }
}