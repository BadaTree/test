package com.example.cslabposco

import kotlin.math.*
import kotlin.random.Random

internal class ParticleFilter constructor(map: Map, particleCount : Int, posX : Int, posY : Int, maxR : Int) {
    private val map: Map = map
    private val rnd = Random
    private var particles = arrayListOf<Particle>()
    private val particleCount = particleCount

    init {
        var x: Int
        var y: Int
        for (i in 0 until particleCount) {
            do {
                x = rnd.nextInt(2 * maxR) + posX - maxR
                y = rnd.nextInt(2 * maxR) + posY - maxR
            } while (!map.isPossiblePosition(x.toDouble(), y.toDouble()))
            particles.add(
                Particle(
                    x.toDouble(),
                    y.toDouble(),
                    rnd.nextInt(360).toDouble(),
                    1.0 / particleCount
                )
            )
        }
    }

    fun step(sensorValues: Array<Double>, heading: Double, stepLength: Double): Array<Double> {
        var x = 0.0
        var y = 0.0
        var w = 0.0
        moveParticles(stepLength, heading)
        applyObservation(sensorValues)
        for (p in particles) {
            w += p.w
            x += p.x * p.w
            y += p.y * p.w
        }
        x /= w
        y /= w
        particles = resample(particles, heading)
        blocking(x, y, stepLength)
        return arrayOf(x, y)
    }

    private fun moveParticles(stepLength: Double, heading: Double) {
        var r: Double
        var x: Double
        var y: Double
        var stepNoise: Int
        var headingNoise: Int

        particles.forEach { p ->
            do {
                stepNoise = rnd.nextInt(5) - 2
                headingNoise = rnd.nextInt(3) - 1
                r = (heading + headingNoise) % 360
                x = round(p.x + sin(Math.toRadians(r)) * ((stepLength * 10) + stepNoise))
                y = round(p.y + cos(Math.toRadians(r)) * ((stepLength * 10) + stepNoise))
                x = when {
                    x >= map.getWidth() -> map.getWidth().toDouble()
                    x <= 0 -> 0.0
                    else -> x
                }
                y = when {
                    y >= map.getHeight() -> map.getHeight().toDouble()
                    y <= 0 -> 0.0
                    else -> y
                }
            } while (!map.isPossiblePosition2(x, y))
            p.a = r
            p.x = x
            p.y = y
        }
    }

    private fun applyObservation(sensorValues: Array<Double>) {
        var errX: Double
        var errY: Double
        var errZ: Double
        var mapData: Array<Double>

        particles.forEach { p ->
            mapData = map.getData(p.x, p.y)
            errX = sensorValues[0] - mapData[0]
            errY = sensorValues[1] - mapData[1]
            errZ = sensorValues[2] - mapData[2]
            p.w = E.pow(-1 * (errX.pow(2) / 200)) + E.pow(-1 * (errY.pow(2) / 200)) + E.pow(
                -1 * (errZ.pow(2) / 200)
            )
        }
    }

    private fun resample(p: ArrayList<Particle>, angle: Double): ArrayList<Particle> {
        //var numParticles = p.size
        var newParticles = ArrayList<Particle>()

        var B = 0f
        var best = getBestParticle(p)
        var index = (rnd.nextFloat() * particleCount).toInt()
        for (i in 0 until particleCount) {
            B += rnd.nextFloat() * 2f * best.w.toFloat()
            while (B > p[index].w) {
                B -= p[index].w.toFloat()
                index = circle(index + 1, particleCount)
            }
            newParticles.add(
                Particle(
                    round(p[index].x),
                    round(p[index].y),
                    angle,
                    1.0 / particleCount
                )
            )
        }
        return newParticles
    }

    private fun circle(n: Int, length: Int): Int {
        var num = n
        while (num > length - 1) {
            num -= length
        }
        while (num < 0) {
            num += length
        }
        return num
    }

    private fun getBestParticle(particles: ArrayList<Particle>): Particle {
        var particle = particles[0]
        particles.forEach { p -> particle = if (p.w > particle.w) p else particle }
        return particle
    }

    private fun blocking(x: Double, y: Double, stepLength: Double) {
        val area = round((stepLength * 10) * 6).toInt()      //round((stepLength * 10) * 4).toInt()
        particles.forEach { p ->
            while (!map.isPossiblePosition(p.x, p.y)) {
                p.x = rnd.nextInt(area) + x - (area / 2)   // area / 2
                p.y = rnd.nextInt(area) + y - (area / 2)   // area / 2
            }
        }
    }
}