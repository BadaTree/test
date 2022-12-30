package com.example.mylibrary.wifiengine

class WifiParticleMother constructor(angle : Int) {
    var particleChildrenList = listOf<WiFiParticle>()
    var myAngle : Int = 0
    init{
        myAngle = angle
    }
    fun appendChildren(position: ArrayList<Float>){
        particleChildrenList = particleChildrenList.plus(WiFiParticle(position))
    }
    fun removeChildren(idx: Int){
        particleChildrenList = particleChildrenList.minus(particleChildrenList[idx])
    }
}