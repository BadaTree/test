package com.example.mylibrary.sensors

import android.hardware.SensorManager
import kotlin.math.*

/*
2022.04.26 (원준)
- VectorCalibration 클래스 추가함.
- 기존과 변경된 점 없음
 */


internal class VectorCalibration {
    private var mRotationMatrix = FloatArray(16)
    private var quaternion = FloatArray(4)
    var mAzimuth: Float = 0.0f

    fun calibrate(accMatrix: FloatArray, magMatrix: FloatArray, gyro: Float): Array<Double> {
        var caliX = 0.0
        var caliY = 0.0
        var caliZ = 0.0
        var magnitudeOfMagnetic = sqrt(magMatrix[0].pow(2) + magMatrix[1].pow(2) + magMatrix[2].pow(2)).toDouble()
        if (accMatrix.isNotEmpty() && magMatrix.isNotEmpty()) {
            var I = FloatArray(9)
            var success =
                SensorManager.getRotationMatrix(mRotationMatrix, I, accMatrix, magMatrix)
            var mRot = FloatArray(3)
            mRot[0] =
                mRotationMatrix[0] * magMatrix[0] + mRotationMatrix[1] * magMatrix[1] + mRotationMatrix[2] * magMatrix[2]
            mRot[1] =
                mRotationMatrix[4] * magMatrix[0] + mRotationMatrix[5] * magMatrix[1] + mRotationMatrix[6] * magMatrix[2]
            mRot[2] =
                mRotationMatrix[8] * magMatrix[0] + mRotationMatrix[9] * magMatrix[1] + mRotationMatrix[10] * magMatrix[2]
            if (success) {
                var orientation = FloatArray(3)
                SensorManager.getOrientation(mRotationMatrix, orientation)
                mAzimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
            }

            var angleA = ((mAzimuth - gyro) + 360) % 360
            caliX = -1 * sqrt(magnitudeOfMagnetic.pow(2) - mRot[2].pow(2)) * sin(angleA * PI / 180)
            caliY = sqrt(magnitudeOfMagnetic.pow(2) - mRot[2].pow(2)) * cos(angleA * PI / 180)
            caliZ = mRot[2].toDouble()

//            angleA_AON = (mAzimuth - Math.toDegrees(gyro_for_always_on.toDouble()) + 360) % 360
//            caliX_AON =
//                -1 * sqrt(magnitudeOfMagnetic.pow(2) - mRot[2].pow(2)) * sin(angleA_AON * PI / 180)
//            caliY_AON =
//                sqrt(magnitudeOfMagnetic.pow(2) - mRot[2].pow(2)) * cos(angleA_AON * PI / 180)
//            caliZ_AON = mRot[2].toDouble()
//            /////////////////////////////////////
//
//            /*hanjun 수정시작*/
//            caliXx = axisTransformQuar('x', magMatrix[0], magMatrix[1], magMatrix[2])
//            caliYy = axisTransformQuar('y', magMatrix[0], magMatrix[1], magMatrix[2])
//            caliZz = axisTransformQuar('z', magMatrix[0], magMatrix[1], magMatrix[2])
//            /*hanjun 수정끝*/
//            if (isFirst) {
//                if (mRot[2] != 0f) {
//                    isFirst = false
//                    Log.d("mVector", "${caliX}, ${caliY}, ${caliZ}")
//                    magneticQueue.add(arrayListOf(caliX, caliY, caliZ, 0.0, dir))
//                }
//            }

        }
//        Log.d("vector_cal", "${caliX}\t${caliY}\t${caliZ}\t")

        var caliVector = arrayOf(caliX, caliY, caliZ)

        return caliVector
    }

    fun calibrate_quaternion(magMatrix: FloatArray): Array<Double> {
        var caliX = axisTransformQuar('x', magMatrix[0], magMatrix[1], magMatrix[2])
        var caliY = axisTransformQuar('y', magMatrix[0], magMatrix[1], magMatrix[2])
        var caliZ = axisTransformQuar('z', magMatrix[0], magMatrix[1], magMatrix[2])

        var caliVector = arrayOf(caliX, caliY, caliZ)
        return caliVector
    }

    fun setQuaternion(gameRotationVector : FloatArray){
        quaternion[0]= gameRotationVector[3]
        quaternion[1]= gameRotationVector[0]
        quaternion[2]= gameRotationVector[1]
        quaternion[3]= gameRotationVector[2]
    }

    private fun axisTransformQuar(axis : Char, rawDataX : Float, rawDataY : Float, rawDataZ : Float) : Double {
        return when(axis) {
            'x' -> { ( (quaternion[0].pow(2) + quaternion[1].pow(2) - quaternion[2].pow(2) - quaternion[3].pow(2)) * rawDataX + 2 * (quaternion[1] * quaternion[2] - quaternion[0] * quaternion[3]) * rawDataY + 2 * (quaternion[0] * quaternion[2] + quaternion[1] * quaternion[3]) * rawDataZ ).toDouble() }
            'y' -> { ( 2 * (quaternion[1] * quaternion[2] + quaternion[0] * quaternion[3]) * rawDataX + (quaternion[0].pow(2) - quaternion[1].pow(2) + quaternion[2].pow(2) - quaternion[3].pow(2)) * rawDataY + 2 * (quaternion[2] * quaternion[3] - quaternion[0] * quaternion[1]) * rawDataZ ).toDouble() }
            'z' -> { ( 2 * (quaternion[1] * quaternion[3] - quaternion[0] * quaternion[2]) * rawDataX + 2 * (quaternion[0] * quaternion[1] + quaternion[2] * quaternion[3]) * rawDataY +  (quaternion[0].pow(2) - quaternion[1].pow(2) - quaternion[2].pow(2) + quaternion[3].pow(2)) * rawDataZ ).toDouble() }
            else -> -966.966966
        }
    }
}
