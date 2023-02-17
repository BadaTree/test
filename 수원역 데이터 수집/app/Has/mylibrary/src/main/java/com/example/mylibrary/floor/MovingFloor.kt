package com.example.mylibrary.floor

import android.content.Context
import android.util.Log

internal class MovingFloor {

    private var useMag : Boolean = false // 일단 무조건 안쓰는 것으로
    private lateinit var elevatorModule : Elevator
    private lateinit var escalatorModule : Escalator
    private lateinit var lightElvModule : LightElv
    private lateinit var lightEscalModule : LightEscal
    private lateinit var stairModule : Stairs
    private var threadMoving : MovingThread = MovingThread()
    private var runningMovingThread : Boolean = false
    private var pressureFlag : Boolean = false
    private var magFlag : Boolean = false
    private var linearAccFlag : Boolean = false
    private var elvType : Boolean = false
    private var escalType : Boolean = false
    private var stairType : Boolean = false
    internal var isArrival : Boolean = false
    internal var isResultReady : Boolean = false
    private var pressure : Double = 0.0
    private var caliX : Double = 0.0
    private var caliY : Double = 0.0
    private var caliZ : Double = 0.0
    private var linearAccZ : Double = 0.0
    private var floor : Int = 0

    internal fun getCondition(movingType : String, startFloor : Int , front :  Boolean , firstP : Double) {

        if(useMag) {
            if (movingType == "EL1") {
                elevatorModule = Elevator(1, startFloor)
                elvType = true
            } else if (movingType == "EL2") {
                elevatorModule = Elevator(2, startFloor)
                elvType = true
            } else if (movingType == "EL3") {
                elevatorModule = Elevator(3, startFloor)
                elvType = true
            } else if (movingType == "EL4") {
                elevatorModule = Elevator(4, startFloor)
                elvType = true
            } else if (movingType == "Es1") {
                escalatorModule = Escalator(1, startFloor)
                escalType = true
            }
        }
        if(!useMag) {
            lightElvModule = LightElv(startFloor , front, firstP )
            elvType = true
//            if (movingType == "EL1" || movingType == "EL2" || movingType == "EL3" || movingType == "EL4" || movingType == "EL1Front" || movingType == "EL2Front" || movingType == "EL2Front2" ) {
//
//            }
//            if(movingType == "Es1") {
//                lightEscalModule = LightEscal(startFloor)
//                escalType = true
//            }
        }

        if(movingType == "ST" ) {
            stairModule = Stairs(startFloor)
            stairType = true
        }


        if(threadMoving.isAlive()) {
            threadMoving.interrupt()
        }
        threadMoving = MovingThread()
        threadMoving.isDaemon = true
        threadMoving.start()
        runningMovingThread = true
    }

    internal fun getPressure(getPr : Double) {
        pressure = getPr
        pressureFlag = true
    }

    internal fun getMag(getCaliX : Double, getCaliY : Double, getCaliZ : Double) {
        caliX = getCaliX
        caliY = getCaliY
        caliZ = getCaliZ
        magFlag = true
    }

    internal fun getLinerAccZ(getAccZ : Double) {
        linearAccZ = getAccZ
        linearAccFlag = true
    }

    internal fun getFloor() : Int {
        if(elvType && useMag) {
            floor = elevatorModule.getElevatorResult()
            elevatorModule.htmlReady = false
            elevatorModule.resultReady = false
            elvType = false
        }
        if(elvType && !useMag) {
            floor = lightElvModule.getElevatorResult()
            lightElvModule.htmlReady = false
            lightElvModule.resultReady = false
            elvType = false
        }
        if(escalType && useMag) {
            floor = escalatorModule.getResultEscalator()
            escalatorModule.htmlReady = false
            escalatorModule.resultReady = false
            escalType = false
        }
        if(escalType && !useMag) {
            floor = lightEscalModule.getResultEscalator()
            lightEscalModule.htmlReady = false
            lightEscalModule.resultReady = false
            escalType = false
        }

        if(stairType) {
            floor = stairModule.getResultEscalator()
            stairModule.htmlReady = false
            stairModule.resultReady = false
            stairType = false
        }

       threadMoving.interrupt()

        pressureFlag = false
        magFlag = false
        linearAccFlag = false
        return floor
    }


    inner class MovingThread : Thread() {
        override fun run() {
            while(runningMovingThread && !this.isInterrupted) {
                try {

                    if (elvType && !useMag) {
                        if (pressureFlag && linearAccFlag && !useMag) {
                            lightElvModule.startElevator(pressure, linearAccZ)
                            pressureFlag = false
                            linearAccFlag = false
                        }

                        if (lightElvModule.htmlReady) {
                            isArrival = true
                        }
                        if (lightElvModule.resultReady) {
                            isResultReady = true
                            runningMovingThread = false
                        }
                    }

                    if (escalType && !useMag) {
                        if (pressureFlag) {
                            lightEscalModule.startEscalator(pressure)
                            pressureFlag = false
                        }
                        if (lightEscalModule.htmlReady) {
                            isArrival = true
                        }
                        if (lightEscalModule.resultReady) {
                            isResultReady = true
                            runningMovingThread = false
                        }
                    }

                    if (stairType) {
                        if (pressureFlag) {
                            stairModule.startEscalator(pressure)
                            pressureFlag = false
                        }
                        if (stairModule.htmlReady) {
                            isArrival = true
                        }
                        if (stairModule.resultReady) {
                            isResultReady = true
                            runningMovingThread = false
                        }
                    }
                } catch (e : InterruptedException) {
                    interrupt()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}