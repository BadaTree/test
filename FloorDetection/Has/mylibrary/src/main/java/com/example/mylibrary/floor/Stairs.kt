package com.example.mylibrary.floor

import com.example.heropdr.MovingAverage

internal class Stairs(whichFloorUserNow : Int) {
    private val pressureMovingAverage20 : MovingAverage = MovingAverage(20)
    private val elvPressure : ElvPressure = ElvPressure(3, 0.005)
    private var startFloor = whichFloorUserNow
    private var elvPressureGap : Double = 0.0
    private var elvPressureGradient : Double = 0.0
    private var moveDirection : Int = 0
    private var pressureCnt : Int = 0
    private var finalFloor : Int = 0
    private var sendResult : Boolean = false
    internal var resultReady : Boolean = false
    internal var htmlReady : Boolean = false

    internal fun startEscalator(pressure : Double)  {
        elvPressureGap = elvPressure.calculatePressure(pressure)
        pressureMovingAverage20.newData(pressure)
        elvPressureGradient = elvPressure.getPressureGradient(pressureMovingAverage20.getAvg())
        pressureCnt++
        if (pressureCnt > 100) {
            moveDirection = elvPressure.getDirection(elvPressureGap)
            if(moveDirection == 1) {
                decideArrival(moveDirection,elvPressureGradient)
            }
            if(moveDirection == 2) {
                decideArrival(moveDirection,elvPressureGradient)
            }
        }
    }

    private fun decideArrival(moveDirection : Int, elvPressureGradient : Double) {
        if(moveDirection == 1 && elvPressureGap < -0.6 && elvPressureGradient*1000 >= 0.0) {
            resultReady = true
            htmlReady = true
        }
        if(moveDirection == 2 && elvPressureGap > 0.6 && elvPressureGradient*1000 <= 0.0) {
            resultReady = true
            htmlReady = true
        }
    }

    internal fun getResultEscalator() : Int {
        if(!sendResult && resultReady) {
            if(moveDirection == 1) {
                finalFloor = (startFloor + 1)
            }
            if (moveDirection == 2) {
                finalFloor = (startFloor - 1)
            }
            sendResult = false
        }
        return finalFloor
    }
}