package com.example.mylibrary.floor

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.example.heropdr.MovingAverage
import com.example.mylibrary.filters.LowPassFilter


internal class LightElv(whichFloorUserNow: Int, front: Boolean, firstP : Double ) {

    private var firstpressure = firstP
    private var isFront = front
    private var elvPressure : ElvPressure = ElvPressure(3, 0.005)
    private val accZMovingAverage : MovingAverage = MovingAverage(10)
    private val lowPassFilterLinearAccZ : LowPassFilter = LowPassFilter()
    private var startFloor = whichFloorUserNow
    private var elvPressureGap : Double = 0.0
    private var nowAccZ : Double = 0.0
    private var pressureCnt : Int = 0
    private var moveDirection: Int = 0
    private var finalFloor : Int = 0
    private var sendResult : Boolean = false
    private var callStop : Boolean = false
    internal var htmlReady : Boolean = false
    internal var resultReady : Boolean = false
    internal var isArrivalAccZ : Boolean = false
    private var changeNumber : Boolean = false
    private var movingToast : Boolean = false
    private var acontext: Context = AppforContext.myappContext()
    private var checkToast : Boolean = false

    private var passOneFloor : Boolean = false
    private var passOneFloorCheck : Boolean = false
    private var passTwoFloor : Boolean = false
    private var passTwoFloorCheck : Boolean = false
    private var passingFloorNumber : Int = 0
    private var passingFloorName : String = ""
    private var cnt : Int = 0

    /**건물마다 최적화 필요한 것들**/
    private var pressureGapDecideMove : Double = 0.05
    private var determinedPassingFloor : Int = 0

    internal fun startElevator(pressure : Double, linearAccZ : Double) {

        accZMovingAverage.newData(linearAccZ)
        nowAccZ = lowPassFilterLinearAccZ.lpf(accZMovingAverage.getAvg(), 0.85)
        pressureCnt++

        if (!isFront) {
            pressureCnt++
        }

        if (isFront) {

            elvPressureGap = elvPressure.calculatePressureInFront(pressure, firstpressure)
            moveDirection = elvPressure.getDirection(elvPressureGap)

            checkFloorInElv()
            if (cnt == 0) {
                val handler = Handler(Looper.getMainLooper())
                handler.postDelayed(Runnable {
                    Toast.makeText(
                        acontext, "엘리베이터가 이동중입니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                }, 0)
                cnt++
            }
        }

        if (pressureCnt > 100) {
            elvPressureGap = elvPressure.calculatePressure(pressure)
            moveDirection = elvPressure.getDirection(elvPressureGap)

            checkFloorInElv()

            if (nowAccZ > 0.3 || nowAccZ < -0.4 && !checkToast) {
                movingToast = true
                checkToast = true
            }

            if (movingToast) {
                if (cnt == 0) {
                    val handler = Handler(Looper.getMainLooper())
                    handler.postDelayed(Runnable {
                        Toast.makeText(acontext, "엘리베이터가 이동중입니다.", Toast.LENGTH_SHORT).show() }, 0)
                    cnt++
                }
                movingToast = false
            }

        }
    }

    private fun checkFloorInElv() {


        if (isFront && moveDirection == 1 && nowAccZ < -0.4 ) {
            isArrivalAccZ = true
            callStop = true
        }

        if (isFront && moveDirection == 2 && nowAccZ > 0.2 ) {
            isArrivalAccZ = true
            callStop = true
        }

        if (!isFront && moveDirection == 1 && nowAccZ < -0.4 && !callStop && elvPressureGap < -(pressureGapDecideMove) ) {
            isArrivalAccZ = true
            callStop = true
        }

        if (!isFront && moveDirection == 2 && nowAccZ > 0.2 && !callStop && (elvPressureGap > pressureGapDecideMove) ) {
            isArrivalAccZ = true
            callStop = true
        }

        if(isArrivalAccZ) {
            determineFloorInElv(elvPressureGap)
        }

        if(!isArrivalAccZ) {

            if(startFloor == 1) {
                if(elvPressureGap in 0.8..1.0 && !passOneFloor) {
                    passingFloorNumber = startFloor - 1
                    passOneFloor = true
                }

                if(elvPressureGap in 1.3..1.4 && !passTwoFloor) {
                    passingFloorNumber = startFloor - 2
                    passTwoFloor = true
                }
            }

            if(startFloor == 0) {
                if(elvPressureGap in 0.4..0.6 && !passOneFloor) {
                    passingFloorNumber = startFloor - 1
                    passOneFloor = true
                }
            }

            if(startFloor == -1) {
                if(elvPressureGap in -1.0..-0.6 && !passOneFloor) {
                    passingFloorNumber = startFloor + 1
                    passOneFloor = true
                }
            }

            if(startFloor == -2) {
                if(elvPressureGap in -0.6..-0.5 && !passOneFloor) {
                    passingFloorNumber = startFloor + 1
                    passOneFloor = true
                }

                if(elvPressureGap in -1.3..-0.9 && !passTwoFloor) {
                    passingFloorNumber = startFloor + 2
                    passTwoFloor = true
                }
            }
//            if (elvPressureGap <= -0.75 && elvPressureGap >= -0.9 && !passOneFloor) {
//                passingFloorNumber = startFloor + 1
//                passOneFloor = true
//            }
//            if (elvPressureGap <= -1.3 && elvPressureGap >= -2 && !passTwoFloor) {
//                passingFloorNumber = startFloor + 2
//                passTwoFloor = true
//            }
//
//            if (elvPressureGap in 0.75..0.9 && !passOneFloor) {
//                passingFloorNumber = startFloor - 1
//                passOneFloor = true
//            }
//            if (elvPressureGap in 1.3..2.0 && !passTwoFloor) {
//                passingFloorNumber = startFloor - 2
//                passTwoFloor = true
//            }

            if (passingFloorNumber == 1) {
                passingFloorName = "1"
                changeNumber = true
            }

            if (passingFloorNumber == 0) {
                passingFloorName = "B1"
                changeNumber = true
            }

            if (passingFloorNumber == -1) {
                passingFloorName = "B2"
                changeNumber = true
            }

            if (passingFloorNumber == -2) {
                passingFloorName = "B3"
                changeNumber = true
            }

            if (passOneFloor && !passOneFloorCheck && changeNumber) {
                val handler = Handler(Looper.getMainLooper())
                handler.postDelayed(Runnable { Toast.makeText( acontext,  passingFloorName + "층을 지나쳤습니다", Toast.LENGTH_SHORT).show() },0)
                passOneFloorCheck = true
                changeNumber = false
            }

            if (passTwoFloor && !passTwoFloorCheck && changeNumber) {
                val handler = Handler(Looper.getMainLooper())
                handler.postDelayed(Runnable { Toast.makeText( acontext,  passingFloorName + "층을 지나쳤습니다", Toast.LENGTH_SHORT).show() },0)
                passTwoFloorCheck = true
                changeNumber = false
            }
        }
    }

    private fun determineFloorInElv(elvPressureGap: Double) {

        if(startFloor == 1) {

            if(elvPressureGap < 0.8 && !htmlReady) {
                determinedPassingFloor = 1
            }

            if(elvPressureGap in 0.8..1.13 && !htmlReady) {
                determinedPassingFloor = 2
            }

            if(elvPressureGap > 1.13  && !htmlReady) {
                determinedPassingFloor = 3
            }
        }

        if(startFloor == 0) {
            if(moveDirection == 1 && elvPressureGap >= -0.8 && !htmlReady) {
                determinedPassingFloor = 1
            }

            if(moveDirection == 2 && elvPressureGap <= 0.5 && !htmlReady) {
                determinedPassingFloor = 1
            }

            if(moveDirection == 2 && elvPressureGap > 0.6 && !htmlReady) {
                determinedPassingFloor = 2
            }
        }

        if(startFloor == -1) {
            if(moveDirection == 1 && elvPressureGap >= -0.5 && !htmlReady) {
                determinedPassingFloor = 1
            }

            if(moveDirection == 1 && elvPressureGap in -1.4..-1.0 && !htmlReady) {
                determinedPassingFloor = 2
            }

            if(moveDirection == 2 && elvPressureGap < 0.6 && !htmlReady) {
                determinedPassingFloor = 1
            }
        }

        if(startFloor == -2) {
            if(elvPressureGap >= -0.4 && !htmlReady) {
                determinedPassingFloor = 1
            }

            if(elvPressureGap in -0.9..-0.5 && !htmlReady) {
                determinedPassingFloor = 2
            }

            if(elvPressureGap < -1.1 && !htmlReady) {
                determinedPassingFloor = 3
            }
        }





//            if (elvPressureGap >= -0.8 && !htmlReady) {
//                determinedPassingFloor = 1
//            }
//
//            if (elvPressureGap in -1.15..-0.8 &&  !htmlReady) {
//                determinedPassingFloor = 2
//            }
//
//            if (elvPressureGap < -1.15 &&  !htmlReady) {
//                determinedPassingFloor = 3
//            }
//        }

//        if(moveDirection == 2) {
//
//            if (elvPressureGap < 0.8 && !htmlReady) {
//                determinedPassingFloor = 1
//            }
//
//            if (elvPressureGap in 0.8..1.15 &&  !htmlReady) {
//                determinedPassingFloor = 2
//            }
//
//            if (elvPressureGap > 1.15 &&  !htmlReady) {
//                determinedPassingFloor = 3
//            }
//        }

        val handler = Handler(Looper.getMainLooper())
//        handler.postDelayed(Runnable { Toast.makeText(acontext, elvPressureGap.toString(), Toast.LENGTH_SHORT).show() }, 0)
        htmlReady = true
        resultReady = true

    }

    internal fun getElevatorResult() : Int {
        if(!sendResult && resultReady) {
            if(moveDirection == 1) {
                finalFloor = ( startFloor + determinedPassingFloor )
            }
            if(moveDirection == 2) {
                finalFloor = ( startFloor - determinedPassingFloor )
            }
            sendResult = true
        }

        movingToast = false
        callStop = false
        checkToast = false
        pressureCnt = 0
        cnt = 0

        return finalFloor
    }


//    private fun checkFloorInFront() {
//
//        if (moveDirection == 1 && nowAccZ < -0.4 ) {
//            isArrivalAccZ = true
//            callStop = true
//        }
//
//        if (moveDirection == 2 && nowAccZ > 0.2 ) {
//            isArrivalAccZ = true
//            callStop = true
//        }
//
//
//        if(isArrivalAccZ) {
//            determineFloorInFront(elvPressureGap)
//        }
//
//        if(!isArrivalAccZ) {
//
//            if(moveDirection == 1) {
//                if (elvPressureGap in -0.9..-0.75 && !passOneFloor) {
//                    passingFloorNumber = startFloor + 1
//                    passOneFloor = true
//                }
//                if (elvPressureGap in -1.3..-2.0 && !passTwoFloor) {
//                    passingFloorNumber = startFloor + 2
//                    passTwoFloor = true
//                }
//
//
//            }
//            if(moveDirection == 2) {
//
//                if(startFloor == 1) {
//                    if (elvPressureGap in 0.6..0.8 && !passOneFloor) { //하강
//                        passingFloorNumber = startFloor - 1
//                        passOneFloor = true
//                    }
//                    if (elvPressureGap > 1.3 && !passTwoFloor) {
//                        passingFloorNumber = startFloor - 2
//                        passTwoFloor = true
//                    }
//                } else {
//                    if (elvPressureGap in 0.3..0.6 && !passOneFloor) { //하강
//                        passingFloorNumber = startFloor - 1
//                        passOneFloor = true
//                    }
//                    if (elvPressureGap > 0.7 && !passTwoFloor) {
//                        passingFloorNumber = startFloor - 2
//                        passTwoFloor = true
//                    }
//                }
//
//            }
//
//            if (passingFloorNumber == 1) {
//                passingFloorName = "1"
//                changeNumber = true
//            }
//
//            if (passingFloorNumber == 0) {
//                passingFloorName = "B1"
//                changeNumber = true
//            }
//
//            if (passingFloorNumber == -1) {
//                passingFloorName = "B2"
//                changeNumber = true
//            }
//
//            if (passingFloorNumber == -2) {
//                passingFloorName = "B3"
//                changeNumber = true
//            }
//
//            if (passOneFloor && !passOneFloorCheck && changeNumber) {
//                val handler = Handler(Looper.getMainLooper())
//                handler.postDelayed(Runnable { Toast.makeText( acontext,  passingFloorName + "층을 지나쳤습니다", Toast.LENGTH_SHORT).show() },0)
//                passOneFloorCheck = true
//                changeNumber = false
//            }
//
//            if (passTwoFloor && !passTwoFloorCheck && changeNumber) {
//                val handler = Handler(Looper.getMainLooper())
//                handler.postDelayed(Runnable { Toast.makeText( acontext,  passingFloorName + "층을 지나쳤습니다", Toast.LENGTH_SHORT).show() },0)
//                passTwoFloorCheck = true
//                changeNumber = false
//            }
//        }
//    }




//    private fun determineFloorInFront(elvPressureGap: Double) {
//
//
//        if(moveDirection == 1) {
//
//            if (elvPressureGap >= -0.8 && !htmlReady) {
//                determinedPassingFloor = 1
//            }
//
//            if (elvPressureGap in -1.15..-0.8 &&  !htmlReady) {
//                determinedPassingFloor = 2
//            }
//
//            if (elvPressureGap < -1.15 &&  !htmlReady) {
//                determinedPassingFloor = 3
//            }
//        }
//
//        if(moveDirection == 2) {
//
//            if(startFloor == 1) {
//                if (elvPressureGap < 0.5 && !htmlReady) {
//                    determinedPassingFloor = 1
//                }
//
//                if (elvPressureGap in 0.5..0.9 && !htmlReady) {
//                    determinedPassingFloor = 2
//                }
//
//                if (elvPressureGap > 0.8 && !htmlReady) {
//                    determinedPassingFloor = 3
//                }
//            } else {
//                if (elvPressureGap < 0.5 && !htmlReady) {
//                    determinedPassingFloor = 1
//                }
//
//                if (elvPressureGap in 0.5..0.7 && !htmlReady) {
//                    determinedPassingFloor = 2
//                }
//
//                if (elvPressureGap > 0.7 && !htmlReady) {
//                    determinedPassingFloor = 3
//                }
//            }
//        }
//
//        htmlReady = true
//        resultReady = true
//
//    }



}