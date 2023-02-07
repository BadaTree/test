package com.example.mylibrary.wifiengine

import com.example.mylibrary.wifiengine.WiFiParticle

class WiFiParticle_Mother constructor(angle : Int) {
    var particle_children_list = listOf<WiFiParticle>()
    var my_angle : Int = 0
    var win_num : Int = 0
    init{
        my_angle = angle
    }
    fun appendChildren(position: ArrayList<Float>){
        particle_children_list = particle_children_list.plus(WiFiParticle(position))
    }
    fun removeChildren(idx: Int){
        particle_children_list = particle_children_list.minus(particle_children_list[idx])
    }
    fun add_children_whole(childrenlist : List<WiFiParticle>){
        particle_children_list = childrenlist
    }

    // 210913 원준 수정
    fun getAvgWeight(): Double {
        var sum = 0.0
        var result = 0.0
        for (c in particle_children_list) {
            sum += c.weight
        }
        if (particle_children_list.size == 0){
            result = 0.0
        }
        else {
            result = sum / particle_children_list.size
        }
        return result
    }

}