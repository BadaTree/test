package data

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface MagSend {
    @POST("/magdata")
    fun message(@Body loginInfo: MagInfo) : Call<MagResponse>
}