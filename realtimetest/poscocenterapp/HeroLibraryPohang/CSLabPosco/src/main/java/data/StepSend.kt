package data

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface StepSend {
    @POST("/step")
    fun message(@Body loginInfo: StepInfo) : Call<StepResponse>
}