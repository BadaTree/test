package com.example.mylibrary.floor

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.example.heropdr.MovingAverage
import com.example.mylibrary.filters.LowPassFilter


internal class LightElv(whichFloorUserNow: Int, front: Boolean) {

    private var isFront = front
    private val elvPressure : ElvPressure = ElvPressure(3, 0.005)
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
        pressureCnt ++

        if(!isFront ) {
            pressureCnt ++
        }

        if(isFront && nowAccZ > 0.2 ) {
            pressureCnt ++
        }

        if( pressureCnt > 100) {
            elvPressureGap = elvPressure.calculatePressure(pressure)
            moveDirection = elvPressure.getDirection(elvPressureGap)
            Log.d("suminNowAccZ", nowAccZ.toString())
            if(nowAccZ > 0.3 || nowAccZ < -0.4 && !checkToast) {
                movingToast  = true
                checkToast = true
            }

            if(moveDirection == 1 && movingToast) {
                if(cnt == 0) {
                    val handler = Handler(Looper.getMainLooper())
                    handler.postDelayed(Runnable { Toast.makeText(acontext, "엘리베이터가 이동중입니다.",
                        Toast.LENGTH_SHORT).show() }, 0)
                    cnt++ //왜 메시지가 안꺼지는거야
                }
                movingToast = false
            }

            if(moveDirection == 2 && movingToast) {
                if(cnt == 0) {
                    val handler = Handler(Looper.getMainLooper())
                    handler.postDelayed(Runnable {
                        Toast.makeText(acontext, "엘리베이터가 이동중입니다.", Toast.LENGTH_SHORT).show()
                    }, 0)
                    cnt++ //왜 메시지가 안꺼지는거야
                }
                movingToast = false
            }

            if (moveDirection == 1 && nowAccZ < -0.4 && !callStop && elvPressureGap < -(pressureGapDecideMove) ) {
                isArrivalAccZ = true
                callStop = true
            }

            if (moveDirection == 2 && nowAccZ > 0.2 && !callStop && (elvPressureGap > pressureGapDecideMove) ) {
                isArrivalAccZ = true
                callStop = true
            }

            if(isArrivalAccZ) {
                determineFloor(elvPressureGap)
            }

            if(!isArrivalAccZ) {

                if (elvPressureGap <= -0.75 && elvPressureGap >= -0.9 && !passOneFloor) {
                    passingFloorNumber = startFloor + 1
                    passOneFloor = true
                }
                if (elvPressureGap <= -1.3 && elvPressureGap >= -2 && !passTwoFloor) {
                    passingFloorNumber = startFloor + 2
                    passTwoFloor = true
                }

                if (elvPressureGap in 0.75..0.9 && !passOneFloor) {
                    passingFloorNumber = startFloor - 1
                    passOneFloor = true
                }
                if (elvPressureGap in 1.3..2.0 && !passTwoFloor) {
                    passingFloorNumber = startFloor - 2
                    passTwoFloor = true
                }

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
    }

        private fun determineFloor(elvPressureGap: Double) {
            Log.d("suminPressureGap", elvPressureGap.toString())
        if(moveDirection == 1) {

            if (elvPressureGap >= -0.8 && !htmlReady) {
                determinedPassingFloor = 1
            }

            if (elvPressureGap < -0.8 && this.elvPressureGap >= -1.15 &&  !htmlReady) {
                determinedPassingFloor = 2
            }

            if (elvPressureGap < -1.15 &&  !htmlReady) {
                determinedPassingFloor = 3
            }
        }

        if(moveDirection == 2) {

            if (elvPressureGap <= 0.8 && !htmlReady) {
                determinedPassingFloor = 1
            }

            if (elvPressureGap > 0.8 && elvPressureGap <= 1.15 &&  !htmlReady) {
                determinedPassingFloor = 2
            }

            if (elvPressureGap > 1.15 &&  !htmlReady) {
                determinedPassingFloor = 3
            }
        }
            Log.d("suminFloor", determinedPassingFloor.toString())

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
        cnt = 0

        return finalFloor
    }

}