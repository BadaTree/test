package com.example.mylibrary.floor

import com.example.heropdr.MovingAverage
import com.example.mylibrary.filters.LowPassFilter
import java.util.ArrayList

internal class Escalator(elevatorNumber: Int, whichFloorUserNow: Int) {

    private var escalatorDTWarpUpList =  arrayListOf<ArrayList<DynamicTimeWarping>>()
    private var returnDistanceEscalatorList = arrayListOf<ArrayList<Float>>()
    private var escalatorDTWarpDownList =  arrayListOf<ArrayList<DynamicTimeWarping>>()
    private var returnDistanceEscalatorDownList = arrayListOf<ArrayList<Float>>()

    init{
        decideReference(elevatorNumber)
        threadStart()
    }

    private val magXMovingAverage20 : MovingAverage = MovingAverage(20)
    private val magYMovingAverage20 : MovingAverage = MovingAverage(20)
    private val magZMovingAverage20 : MovingAverage = MovingAverage(20)
    private val pressureMovingAverage20 : MovingAverage = MovingAverage(20)
    private val elvPressure : ElvPressure = ElvPressure(3, 0.005)
    private var startFloor = whichFloorUserNow
    private var lowPassFilterX : LowPassFilter = LowPassFilter()
    private var lowPassFilterY : LowPassFilter = LowPassFilter()
    private var lowPassFilterZ : LowPassFilter = LowPassFilter()
    private var escalatorToggleStartDetecting : Boolean = false
    private var whichFloorDecision : Boolean = false
    private var isXMatched : Boolean = false
    private var isYMatched : Boolean = false
    private var isZMatched : Boolean = false
    private var sendResult : Boolean = false
    internal var htmlReady : Boolean = false
    internal var resultReady : Boolean = false
    private var runEscalator : Boolean = false
    private var elvPressureGap : Double = 0.0
    private var elvPressureGradient : Double = 0.0
    private var afterFilterMagX : Double = 0.0
    private var afterFilterMagY : Double = 0.0
    private var afterFilterMagZ : Double = 0.0
    private var finalMagX : Double = 0.0
    private var finalMagY : Double = 0.0
    private var finalMagZ : Double = 0.0
    private var detectionThresholdX : Float = 2000f
    private var detectionThresholdY : Float = 2000f
    private var detectionThresholdZ : Float = 2000f
    private var min : Float = 100000000000.0f
    private var moveDirection: Int = 0
    private var pressureCnt : Int = 0
    private var minIndex : Int = 0
    private var magCnt : Int = 0
    private var finalFloor : Int = 0
    private var result : String = ""
    private var resultMoveDirection : String = ""

    internal fun startEscalator(pressure : Double, caliX : Double, caliY : Double, caliZ : Double)  {
        afterFilterMagX = lowPassFilterX.lpf(caliX, 0.7)
        afterFilterMagY = lowPassFilterY.lpf(caliY, 0.7)
        afterFilterMagZ = lowPassFilterZ.lpf(caliZ, 0.7)
        magXMovingAverage20.newData(afterFilterMagX)
        magYMovingAverage20.newData(afterFilterMagY)
        magZMovingAverage20.newData(afterFilterMagZ)
        elvPressureGap = elvPressure.calculatePressure(pressure)
        pressureMovingAverage20.newData(pressure)
        elvPressureGradient = elvPressure.getPressureGradient(pressureMovingAverage20.getAvg())
        escalatorToggleStartDetecting = true
        runEscalator = true
        pressureCnt++
        magCnt++

        if( magCnt % 10 == 0) {
            finalMagX = magXMovingAverage20.getAvg()
            finalMagY = magYMovingAverage20.getAvg()
            finalMagZ = magZMovingAverage20.getAvg()
        }

        if (pressureCnt > 50) {
            moveDirection = elvPressure.getDirection(elvPressureGap)
            if(moveDirection == 1 && elvPressureGap < -0.09) {
                findTotalMin(returnDistanceEscalatorList)
            }
            if(moveDirection == 2 && elvPressureGap > 0.09 ) {
                findTotalMin(returnDistanceEscalatorDownList)
            }
        }
    }

    //엘베번호로 기준 자기장 판단
    private fun decideReference(elevatorNumber : Int) {

    }

    private fun isBelowThreshold(distanceList : ArrayList<Float>) {
        isXMatched = distanceList[0] < detectionThresholdX
        isYMatched = distanceList[1] < detectionThresholdY
        isZMatched = distanceList[2] < detectionThresholdZ
        escalatorToggleStartDetecting = !(isXMatched || isYMatched || isZMatched)

        if (!escalatorToggleStartDetecting) {
            htmlReady = true
            if (moveDirection == 1) {
                result = when (minIndex) {
                    0 -> "Es1"
                    1 -> "Es2"
                    2 -> "Es3"
                    else -> "잘못되었음"
                }
                resultMoveDirection = "상승"
            }
            if (moveDirection == 2) {
                result = when (minIndex) {
                    0 -> "Es4"
                    1 -> "Es5"
                    2 -> "Es6"
                    else -> "잘못되었음"
                }
                resultMoveDirection = "하강"
            }
            resultReady = true
        }
    }

    private fun findTotalMin(distanceList : ArrayList<ArrayList<Float>>) {
        if (moveDirection == 1) {
            for (i in 0..2) {
                var totalDistance = distanceList[i].sum()
                if (totalDistance < min && totalDistance > 0) {
                    minIndex = i
                    min = totalDistance
                }
            }
            if (escalatorToggleStartDetecting) {
                if (returnDistanceEscalatorList[minIndex][0] != -1f && returnDistanceEscalatorList[minIndex][1] != -1f && returnDistanceEscalatorList[minIndex][2] != -1f) {
                    if (returnDistanceEscalatorList[minIndex][0] > 0 && returnDistanceEscalatorList[minIndex][1] > 0 && returnDistanceEscalatorList[minIndex][2] > 0) {
                        if ( (magCnt > 300) && (elvPressureGradient*1000 >= 0.0)) {
                            whichFloorDecision = true
                        }
                        if (whichFloorDecision) {
                            isBelowThreshold(returnDistanceEscalatorList[minIndex])
                        }
                    }
                }
            }
        }
        if(moveDirection == 2) {
            for (i in 0..2) {
                var totalDistance = distanceList[i].sum()
                if (totalDistance < min && totalDistance > 0) {
                    minIndex = i
                    min = totalDistance
                }
            }
            if (escalatorToggleStartDetecting) {
                if (returnDistanceEscalatorDownList[minIndex][0] != -1f && returnDistanceEscalatorDownList[minIndex][1] != -1f && returnDistanceEscalatorDownList[minIndex][2] != -1f) { // 음수인 값이 없을때
                    if (returnDistanceEscalatorDownList[minIndex][0] > 0 && returnDistanceEscalatorDownList[minIndex][1] > 0 && returnDistanceEscalatorDownList[minIndex][2] > 0) {
                        if ( (magCnt > 300) && (elvPressureGradient*1000 <= 0.0)) {
                            whichFloorDecision = true
                        }
                        if (whichFloorDecision) {
                            isBelowThreshold(returnDistanceEscalatorDownList[minIndex])
                        }
                    }
                }
            }
        }
    }

    private inner class EscalatorDTWStartThread : Thread() {
        override fun run() {
            while (true) {
                if (escalatorToggleStartDetecting) {
                    if (moveDirection == 1 ) {
                        for (i in 0..2) {
                            returnDistanceEscalatorList[i][0] = escalatorDTWarpUpList[i][0].exec(finalMagX.toFloat())
                            returnDistanceEscalatorList[i][1] = escalatorDTWarpUpList[i][1].exec(finalMagY.toFloat())
                            returnDistanceEscalatorList[i][2] = escalatorDTWarpUpList[i][2].exec(finalMagZ.toFloat())
                        }
                    }
                    if (moveDirection == 2 ) {
                        for (i in 0..2) {
                            returnDistanceEscalatorDownList[i][0] = escalatorDTWarpDownList[i][0].exec(finalMagX.toFloat())
                            returnDistanceEscalatorDownList[i][1] = escalatorDTWarpDownList[i][1].exec(finalMagY.toFloat())
                            returnDistanceEscalatorDownList[i][2] = escalatorDTWarpDownList[i][2].exec(finalMagZ.toFloat())
                        }
                    }
                    try {
                        sleep(5)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun threadStart() {
        val thread = EscalatorDTWStartThread()
        thread.start()
    }

    internal fun getDirection() : String {
        return resultMoveDirection
    }

    internal fun getResultEscalator() : Int {
        if(!sendResult && resultReady) {
            if(moveDirection == 1) {
                finalFloor = (startFloor + 1)
            }
            if (moveDirection == 2) {
                finalFloor = (startFloor - 1)
            }
            sendResult = true
        }
        return finalFloor
    }
}