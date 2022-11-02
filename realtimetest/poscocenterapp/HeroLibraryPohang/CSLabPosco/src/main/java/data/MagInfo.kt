package data

data class MagInfo(
    val mag_x : Double,
    val mag_y : Double,
    val mag_z : Double,
    val gyro : Float,
    val posture : Int,
    val instant_result : Array<String>,
    val ID : String
)


