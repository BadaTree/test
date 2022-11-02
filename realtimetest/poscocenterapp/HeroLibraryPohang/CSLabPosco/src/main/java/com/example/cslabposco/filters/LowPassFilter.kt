package com.example.cslabposco.filters

internal class LowPassFilter {
    // 이전 스텝의 예측값
    private var prevX : Double = 0.0

    // 측정값 x와 상수 알파를 입력 받아 low pass filter를 수행합니다.
    fun lpf(x : Double, alpha: Double) : Double {
        // low pass filter
        var xLpf = alpha * prevX + (1 - alpha) * x
        // 이전 스텝 값 갱신
        prevX = xLpf
        return xLpf
    }
}