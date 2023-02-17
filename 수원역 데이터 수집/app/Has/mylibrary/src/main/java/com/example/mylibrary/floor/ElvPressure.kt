package com.example.mylibrary.floor

import java.util.*
import kotlin.math.abs

internal class ElvPressure constructor(period : Int, threshold : Double) {
    private val oneFloorPressure : Queue<Double> = LinkedList()
    private val collectingForGradient : Queue<Double> = LinkedList()
    private val threshold : Double = abs(threshold)
    private var initialPressureSum : Double = 0.0
    private var aftPressure : Double = 0.0
    private var difference : Double = 0.0
    private var preShift : Double = 0.0
    private var result : Double = 0.0
    private val oneFloorPeriod : Int = 1
    private var floorCnt : Int = 0
    private var status : Int = 0

    internal fun calculatePressure(getPressure : Double) : Double {
        var pressure = getPressure
        if (oneFloorPeriod > floorCnt && pressure != 0.0) {
            oneFloorPressure.add(pressure)
            initialPressureSum += pressure
            floorCnt++
            if (oneFloorPressure.size > oneFloorPeriod) {
                initialPressureSum -= oneFloorPressure.poll()
            }
        }
        aftPressure = pressure
        if(floorCnt == 1) {
            difference = (aftPressure) - (initialPressureSum / oneFloorPressure.size)
        }
        return difference
    }

    internal fun calculatePressureInFront(getPressure : Double, firstP : Double) : Double {
        var pressure = firstP
        aftPressure = getPressure

        difference = (aftPressure) - (pressure)

        return difference
    }


    internal fun getDirection(difference : Double) : Int {
        status = when {
            difference >= threshold -> 2
            difference <= (-1*threshold) -> 1
            else -> 0
        }
        return status
    }

    internal fun getPressureGradient(pressure : Double) : Double {
        if (collectingForGradient.size == 9) {
            preShift = collectingForGradient.poll()
            result = ( pressure - preShift ) / 10
        }
        collectingForGradient.add(pressure)
        return result
    }

}