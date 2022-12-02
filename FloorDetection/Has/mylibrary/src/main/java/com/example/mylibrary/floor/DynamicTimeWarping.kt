package com.example.mylibrary.floor

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal class DynamicTimeWarping (private val referenceSequence : FloatArray) {
    private var  cumulativeDistanceMatrix: Array<FloatArray>
    private var sequenceLength : Int = 0
    private var ringBuffer : FloatArray
    private var bufferWritePointer : Int = 0
    private var bufferReadPointer : Int = 0
    private var window = 10
    private var dtwReady = false

    init {
        sequenceLength = referenceSequence.size
        ringBuffer = FloatArray(referenceSequence.size*2)
        bufferWritePointer = (referenceSequence.size - 1)
        cumulativeDistanceMatrix = Array(sequenceLength+1) { FloatArray(referenceSequence.size+1) }
    }

    internal fun exec(value : Float) : Float {
        val distance : Float
        writeToRingBuffer(value)
        if (dtwReady)
            distance = performDTW()
        else {
            distance = -1.0f
        }
        return distance
    }

    private fun performDTW() : Float {
        for(i in 0..sequenceLength) {
            for (j in 0..sequenceLength){
                cumulativeDistanceMatrix[i][j] = Float.POSITIVE_INFINITY //양의 무한대??
            }
        }
        cumulativeDistanceMatrix[0][0] = 0f

        for (i in 1..sequenceLength){
            for (j in max(1, i - window) until min(sequenceLength+1, i+window)){
                val cost = abs(referenceSequence[i-1]-ringBuffer[bufferReadPointer+j-1])
                cumulativeDistanceMatrix[i][j] = cost + min(cumulativeDistanceMatrix[i-1][j-1], min(cumulativeDistanceMatrix[i-1][j],cumulativeDistanceMatrix[i][j-1]))
            }
        }
        bufferReadPointer = (bufferReadPointer + 1) % sequenceLength
        return cumulativeDistanceMatrix[sequenceLength][sequenceLength]
    }

    private fun writeToRingBuffer(value : Float) {
        ringBuffer[bufferWritePointer] = value
        ringBuffer[bufferWritePointer+sequenceLength] = value
        bufferWritePointer = (bufferWritePointer + 1) % sequenceLength
        dtwReady = true

    }
}