package data

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface Reset {
    @POST("/reset")
    fun message(@Body loginInfo: ResetInfo) : Call<ResetResponse>
}