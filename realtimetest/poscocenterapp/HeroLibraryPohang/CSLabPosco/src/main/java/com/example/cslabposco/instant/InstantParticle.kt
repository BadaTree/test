package com.example.cslabposco.instant

class InstantParticle constructor(position : ArrayList<Float>, map_value : Array<Double>) {
    var x : Float = 0.0f
    var y : Float = 0.0f
    var sequence_average : Array<Double> = arrayOf()
    var weight : Float = 0.0f
    init{
        x = position[0]
        y = position[1]
        weight = 0.0f
        sequence_average = map_value
    }
}