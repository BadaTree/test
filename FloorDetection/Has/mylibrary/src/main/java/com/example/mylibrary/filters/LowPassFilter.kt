package com.example.mylibrary.filters

internal class LowPassFilter {
    private var prevX : Double = 0.0

    internal fun lpf(x : Double, alpha: Double) : Double {
        var xLpf = alpha * prevX + (1 - alpha) * x
        prevX = xLpf
        return xLpf
    }
}