package com.example.cslabposco

import kotlin.math.PI

internal class Particle constructor(x : Double, y : Double, a : Double, w : Double) {
    var x : Double = x
    var y : Double = y
    var a : Double = a
        set(value) {
            var v = value
            while (v<0) v += 2 * PI
            while (v>2* PI) v -= 2* PI
            field = v
        }
    var w : Double = w
}