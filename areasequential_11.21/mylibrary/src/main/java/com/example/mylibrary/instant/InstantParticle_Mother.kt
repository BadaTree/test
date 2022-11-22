package com.example.mylibrary.instant

internal class InstantParticle_Mother constructor(angle : Int) {
    var particle_children_list = listOf<InstantParticle>()
    var my_angle : Int = 0
    var win_num : Int = 0
    init{
        my_angle = angle
    }
    fun appendChildren(position: ArrayList<Float>, map_value: Array<Double>){
        particle_children_list = particle_children_list.plus(InstantParticle(position, map_value))
    }
    fun removeChildren(idx: Int){
        particle_children_list = particle_children_list.minus(particle_children_list[idx])
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