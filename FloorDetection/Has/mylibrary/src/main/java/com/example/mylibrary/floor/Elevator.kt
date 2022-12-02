package com.example.mylibrary.floor

import com.example.heropdr.MovingAverage
import com.example.mylibrary.filters.LowPassFilter
import java.util.ArrayList

internal class Elevator(elevatorNumber : Int, whichFloorUserNow : Int) {

    private var dtWarpList = arrayListOf<ArrayList<DynamicTimeWarping>>()
    private var returnDistanceList = arrayListOf<ArrayList<Float>>() // Dtw 돌리고 난후 결과 데이터 리스트
    private var dtWarpDownList = arrayListOf<ArrayList<DynamicTimeWarping>>()
    private var returnDistanceDownList = arrayListOf<ArrayList<Float>>()

    init {
        decideReference(elevatorNumber)
        threadStart()
    }

    private val elvPressure : ElvPressure = ElvPressure(3, 0.005)
    private val accZMovingAverage : MovingAverage = MovingAverage(10)
    private val lowPassFilterLinearAccZ : LowPassFilter = LowPassFilter()
    private var lowPassFilterX : LowPassFilter = LowPassFilter()
    private var lowPassFilterY : LowPassFilter = LowPassFilter()
    private var lowPassFilterZ : LowPassFilter = LowPassFilter()
    private var startFloor = whichFloorUserNow
    private var elevatorToggleStartDetecting : Boolean = false
    private var isXMatched : Boolean = false
    private var isYMatched : Boolean = false
    private var isZMatched : Boolean = false
    private var sendResult : Boolean = false
    internal var htmlReady : Boolean = false
    private var runElevator : Boolean = false
    internal var resultReady : Boolean = false
    private var whichFloorDecision : Boolean = false
    private var elvPressureGap: Double = 0.0
    private var finalMagX : Double = 0.0
    private var finalMagY : Double = 0.0
    private var finalMagZ : Double = 0.0
    private var nowAccZ : Double = 0.0
    private var detectionThresholdX : Float = 8500f
    private var detectionThresholdY : Float = 8500f
    private var detectionThresholdZ : Float = 8500f
    private var min : Float = 100000000000.0f
    private var passNumberOfFloor : Int = 0
    private var moveDirection: Int = 0
    private var pressureCnt : Int = 0
    private var minIndex : Int = 0
    private var finalFloor : Int = 0
    private var resultMoveDirection : String = ""

    /***********장소에 따라 최적화 해야 할 것들**************/
    private var accZDecideArrival : Double = -0.3
    private var pressureGapDecideMove : Double = 0.05

    internal fun startElevator(pressure : Double, caliX : Double, caliY : Double, caliZ : Double, linearAccZ : Double) {
        finalMagX = lowPassFilterX.lpf(caliX, 0.7)
        finalMagY = lowPassFilterY.lpf(caliY, 0.7)
        finalMagZ = lowPassFilterZ.lpf(caliZ, 0.7)
        accZMovingAverage.newData(linearAccZ)
        nowAccZ = lowPassFilterLinearAccZ.lpf(accZMovingAverage.getAvg(), 0.7)
        elvPressureGap = elvPressure.calculatePressure(pressure)
        pressureCnt++
        elevatorToggleStartDetecting = true
        runElevator = true
        if (pressureCnt > 90) {
            moveDirection = elvPressure.getDirection(elvPressureGap)
            if (moveDirection == 1 && elvPressureGap < -(pressureGapDecideMove)) {
                findTotalMin(returnDistanceList)
            }
            if (moveDirection == 2 && elvPressureGap > pressureGapDecideMove) {
                findTotalMin(returnDistanceDownList)
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
        elevatorToggleStartDetecting = !(isXMatched || isYMatched || isZMatched)
        if (!elevatorToggleStartDetecting) {
            htmlReady = true
            if (minIndex in 0..8) {
                passNumberOfFloor = 1 //1개의 층 지나쳤다
            }
            resultReady = true

            if (moveDirection == 1) {
                resultMoveDirection = "상승"
            } else if (moveDirection == 2) {
                resultMoveDirection = "하강"
            }
        }
    }

    private fun findTotalMin(distanceList : ArrayList<ArrayList<Float>>) {
        for (i in 0..8) {
            var totalDistance = distanceList[i].sum()
            if (totalDistance < min && totalDistance > 0) {
                minIndex = i
                min = totalDistance
            }
        }
        if (moveDirection == 1) {
            if (elevatorToggleStartDetecting) {
                if (returnDistanceList[minIndex][0] != -1f && returnDistanceList[minIndex][1] != -1f && returnDistanceList[minIndex][2] != -1f) {
                    if (returnDistanceList[minIndex][0] > 0 && returnDistanceList[minIndex][1] > 0 && returnDistanceList[minIndex][2] > 0) {
                        if (nowAccZ <= accZDecideArrival) {
                            whichFloorDecision = true
                        }
                        if (whichFloorDecision) {
                            isBelowThreshold(returnDistanceList[minIndex])
                        }
                    }
                }
            }
        }
        if (moveDirection == 2) {
            if (elevatorToggleStartDetecting) {
                if (returnDistanceDownList[minIndex][0] != -1f && returnDistanceDownList[minIndex][1] != -1f && returnDistanceDownList[minIndex][2] != -1f) {
                    if (returnDistanceDownList[minIndex][0] > 0 && returnDistanceDownList[minIndex][1] > 0 && returnDistanceDownList[minIndex][2] > 0) {
                        if (nowAccZ >= -accZDecideArrival) {
                            whichFloorDecision = true
                        }
                        if (whichFloorDecision) {
                            isBelowThreshold(returnDistanceDownList[minIndex])
                        }
                    }
                }
            }
        }
    }

    inner class DtwStartThread : Thread() {
        override fun run() {
            while (true) {
                if (elevatorToggleStartDetecting) {
                    if (moveDirection == 1) {
                        for (i in 0..8) {
                            returnDistanceList[i][0] = dtWarpList[i][0].exec(finalMagX.toFloat())
                            returnDistanceList[i][1] = dtWarpList[i][1].exec(finalMagY.toFloat())
                            returnDistanceList[i][2] = dtWarpList[i][2].exec(finalMagZ.toFloat())
                        }
                    }
                    if (moveDirection == 2) {
                        for (i in 0..8) {
                            returnDistanceDownList[i][0] = dtWarpDownList[i][0].exec(finalMagX.toFloat())
                            returnDistanceDownList[i][1] = dtWarpDownList[i][1].exec(finalMagY.toFloat())
                            returnDistanceDownList[i][2] = dtWarpDownList[i][2].exec(finalMagZ.toFloat())
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
        val thread1 = DtwStartThread()
        thread1.start()
    }

    internal fun getElevatorResult() : Int {
        if (!sendResult && resultReady) {
            if (moveDirection == 1) {
                finalFloor = (startFloor + passNumberOfFloor)
            }
            if (moveDirection == 2) {
                finalFloor = (startFloor - passNumberOfFloor)
            }
            sendResult = true
        }
        return finalFloor
    }
}