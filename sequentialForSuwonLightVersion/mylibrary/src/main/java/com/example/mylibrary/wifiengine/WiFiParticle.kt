package com.example.mylibrary.wifiengine

class WiFiParticle constructor(position : ArrayList<Float>) {
    var x : Float = 0.0f
    var y : Float = 0.0f
    var weight : Float = 0.0f
    init{
        x = position[0]
        y = position[1]
        weight = 0.0f
    }
}