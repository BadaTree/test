package com.example.cslabposco

import android.os.Handler
import android.os.Looper
import android.util.Log
import data.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.*
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt

class LSTMServer {
    //private val myUniqueID : String = id
    private val mHandler : Handler = Handler(Looper.getMainLooper())
    private val x : Queue<Double> = LinkedList()
    private val y : Queue<Double> = LinkedList()
    private var nowFloor : String = ""

    var returnString : String = ""
    var returnString2 : String = ""
    var checkTestSend : String = ""
    var complete : Boolean = false
    var complete2 : Boolean = false

    var url : String = "http://ec2-54-180-123-235.ap-northeast-2.compute.amazonaws.com:5000/"
    var pos_x = 0.0
    var pos_y = 0.0

    /*hanjun 수정시작*/
    var particle_pos_x = 0.0
    var particle_pos_y = 0.0
    var merged_pos_x = 0.0
    var merged_pos_y = 0.0

    var instant_x = 0.0
    var instant_y = 0.0

    /*hanjun 수정끝*/
    var particlestart : Boolean = false
    var gyroCaliValue : Float = 0.0f

    var convergence : String = ""
    var angle : String = ""

    private var len_seq : Int = 100
    private var window_size : Int = 6
    private var convergence_threshold : Int = 25
    private var DL_result: Array<Double> = arrayOf(0.0,0.0)
    private var step_distance_DL = DoubleArray(window_size)
    private var DL_convergence: Boolean = false
    private var DL_gyro: Double = 0.0
    private lateinit var DL_result_x : Array<Double>
    private lateinit var DL_result_y : Array<Double>
    private var instant_result_String : Array<String> = arrayOf("","","","")
    var sendcomplete = false

    fun play() {
        //Log.d("id::::", myUniqueID)
    }

    fun reset(ID : String) {
        val retrofit = Retrofit.Builder()
            .baseUrl("http://rnn.korea.ac.kr:3896")
            .addConverterFactory(GsonConverterFactory.create()) .build()
        val reset : Reset = retrofit.create(Reset::class.java)

        reset.message(ResetInfo("Reset",ID)).enqueue(object : Callback<ResetResponse> {
            override fun onFailure(call: Call<ResetResponse>, t: Throwable) {
//                Log.d("serverResponse:::", t.stackTraceToString())
            }
            override fun onResponse(call: Call<ResetResponse>, response: Response<ResetResponse>) {
                val msg1 = response.body()?.check.toString()
//                Log.d("serverResponse:::", msg1)

            }
        })
    }
    fun roomdatasend(cur_room: String, new_room: String) {
        val retrofit = Retrofit.Builder()
            .baseUrl(url)//"http://rnn.korea.ac.kr:3896")
            .addConverterFactory(GsonConverterFactory.create()) .build()
        val TestNumSending : RoomDataSend = retrofit.create(RoomDataSend::class.java)

        TestNumSending.message(RoomInfo(cur_room, new_room)).enqueue(object : Callback<RoomResponse> {
            override fun onFailure(call: Call<RoomResponse>, t: Throwable) {
                Log.d("serverResponse:::", t.stackTraceToString())
            }
            override fun onResponse(call: Call<RoomResponse>, response: Response<RoomResponse>) {
                val msg1 = response.body()?.check.toString()
            }
        })
    }
    fun stepsend(step_info: String) {
        val retrofit = Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(GsonConverterFactory.create()) .build()
        val StepSending : StepSend = retrofit.create(StepSend::class.java)

        StepSending.message(StepInfo(step_info)).enqueue(object : Callback<StepResponse> {
            override fun onFailure(call: Call<StepResponse>, t: Throwable) {
                Log.d("serverResponse:::", t.stackTraceToString())
            }
            override fun onResponse(call: Call<StepResponse>, response: Response<StepResponse>) {
                val msg1 = response.body()?.check.toString()
                Log.d("stepsendcheck", msg1)
                checkTestSend = msg1
            }
        })
    }
    fun dataSaveAndSend(mag_x: Double, mag_y: Double, mag_z: Double, gyro: Float, posture: Int, instant_result : MutableMap<String, Float>,ID : String): Array<String> {
        val retrofit = Retrofit.Builder()
            .baseUrl("http://rnn.korea.ac.kr:3896")
            .addConverterFactory(GsonConverterFactory.create()) .build()
        val magSending : MagSend = retrofit.create(MagSend::class.java)
        instant_result_String[0] = instant_result["status_code"].toString()
        instant_result_String[1] = instant_result["gyro_from_map"].toString()
        instant_result_String[2] = instant_result["pos_x"].toString()
        instant_result_String[3] = instant_result["pos_y"].toString()
        Log.d("DLcheck particleFilter",instant_result["status_code"].toString())
        magSending.message(MagInfo(mag_x, mag_y, mag_z,gyro, posture, instant_result_String,ID)).enqueue(object : Callback<MagResponse> {
            override fun onFailure(call: Call<MagResponse>, t: Throwable) {
                Log.d("serverResponse:::", t.stackTraceToString())
            }
            override fun onResponse(call: Call<MagResponse>, response: Response<MagResponse>) {
                val msg1 = response.body()!!.pos_x
                DL_result_x = msg1

                val msg2 = response.body()!!.pos_y
                sendcomplete = true
                DL_result_y = msg2
                DL_result = arrayOf(0.0,0.0)
                val msg3 = response.body()!!.step_count
                Log.d("result56c", "---------------------------------------${DL_result_x[0]}    ${DL_result_y[0]}")
                Log.d("result56c", "---------------------------------------${instant_result_String[0]}")
                DL_convergence = true
                if (instant_result_String[0].toDouble() == 200.0 ||instant_result_String[0].toDouble() == 201.0 ||instant_result_String[0].toDouble() == 202.0 ) {
                    /////////output from sequence & smoothing
                    if (msg3 - 1 < 0) {
                        DL_result[0] = DL_result_x[0]
                        DL_result[1] = DL_result_y[0]
                    } else if (msg3 - 1 < window_size) {
                        DL_result[0] = DL_result_x[msg3 - 1]
                        DL_result[1] = DL_result_y[msg3 - 1]
                        Log.d(
                            "DLcheck particle bf",
                            DL_result[0].toString() + " " + DL_result[1].toString()
                        )
                    } else if (msg3 - 1 < len_seq - 1) {
                        for (i in 0 until window_size) {
                            /////filtering - moving average
                            DL_result[0] += DL_result_x[msg3 - 1 - i] / window_size
                            DL_result[1] += DL_result_y[msg3 - 1 - i] / window_size

                            //DL_result[0] = DL_result_x[msg3 - 1]
                            //DL_result[1] = DL_result_y[msg3 - 1]
                            /////DLconvergence
                            step_distance_DL[i] =
                                sqrt(
                                    (DL_result_x[msg3 - 1 - i] - DL_result_x[msg3 - 2 - i]).pow(2)
                                            + (DL_result_y[msg3 - 1 - i] - DL_result_y[msg3 - 2 - i]).pow(2)
                                )
                            if (step_distance_DL[i] > convergence_threshold)
                                DL_convergence = false
                        }
                        //Log.d("DLcheck step",DL_convergence.toString())
                        DL_gyro = Math.toDegrees(
                            atan2(
                                DL_result_x[msg3 - 1] - DL_result_x[msg3 - 1 - window_size/2],
                                DL_result_y[msg3 - 1] - DL_result_y[msg3 - 1 - window_size/2]
                            )
                        )

                    } else {
                        for (i in 0 until window_size) {
                            /////filtering - moving average
                            DL_result[0] += DL_result_x[len_seq - 1 - i] / window_size
                            DL_result[1] += DL_result_y[len_seq - 1 - i] / window_size


                            //DL_result[0] = DL_result_x[len_seq - 1]
                            //DL_result[1] = DL_result_y[len_seq - 1]
                            /////DLconvergence
                            step_distance_DL[i] =
                                sqrt(
                                    (DL_result_x[len_seq - 1 - i] - DL_result_x[len_seq - 2 - i]).pow(2 )
                                            + (DL_result_y[len_seq - 1 - i] - DL_result_y[len_seq - 2 - i]).pow(2)
                                )
                            if (step_distance_DL[i] > convergence_threshold)
                                DL_convergence = false
                        }
                        DL_gyro = Math.toDegrees(
                            atan2(
                                DL_result_x[len_seq - 1] - DL_result_x[len_seq - 1 - window_size/2],
                                DL_result_y[len_seq - 1] - DL_result_y[len_seq - 1 - window_size/2]
                            )
                        )
                    }


                    /*
                    //////map matching
                    if (!map.isPossiblePosition(DL_result[0], DL_result[1])) {
                        //Log.d("DLcheck particle bf",DL_PF_position[0].toString()+" "+ DL_PF_position[1].toString())
                        var count = 0
                        var rR = 10
                        val particleCount = 30
                        var x = DoubleArray(particleCount)
                        var y = DoubleArray(particleCount)
                        var w: Double
                        var map_match_x = 0.0
                        var map_match_y = 0.0
                        var total_w = 0.0
                        var weight_c = DoubleArray(particleCount)
                        for (i in 0 until particleCount) {
                            do {
                                x[i] = Random.nextInt(2 * rR) + DL_result[0] - rR
                                y[i] = Random.nextInt(2 * rR) + DL_result[1] - rR
                                count++
                                if (count > 30) {
                                    rR += 7
                                    count = 0
                                }
                            } while (!map.isPossiblePosition(x[i], y[i]))
                            w = sqrt((x[i] - DL_result[0]).pow(2) + (y[i] - DL_result[1]).pow(2))
                            w = exp(-1 * w / 100)
                            map_match_x += x[i] * w
                            map_match_y += y[i] * w
                            total_w += w
                            weight_c[i] = total_w
                        }
                        /*
                        var random_weight = Random.nextDouble(total_w)
                        for (i in 0 until particleCount) {
                            if (random_weight<weight_c[i]){
                                DL_result[0] = x[i]
                                DL_result[1] = y[i]
                            }
                        }
                        */
                        DL_result[0] = map_match_x / total_w
                        DL_result[1] = map_match_y / total_w
                        //Log.d("DLcheck particle af",DL_PF_position[0].toString()+" "+ DL_PF_position[1].toString())

                    }*/
                }

            }
        })

        Log.d("result56b", "---------------------------------------${DL_result[0]}    ${DL_result[1]}")
        return arrayOf(DL_result[0].toString(),DL_result[1].toString(),DL_convergence.toString(),DL_gyro.toString())
    }
}