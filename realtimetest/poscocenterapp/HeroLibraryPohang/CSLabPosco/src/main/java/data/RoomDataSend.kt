package data

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface RoomDataSend {
    @POST("/roomcheck")
    fun message(@Body loginInfo: RoomInfo) : Call<RoomResponse>
}