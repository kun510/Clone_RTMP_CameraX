package com.kun510.rtmp.remote

import com.kun510.rtmp.remote.models.GetStreamKeyResponse
import com.kun510.rtmp.remote.models.LoginBody
import com.kun510.rtmp.remote.models.LoginResponse
import com.kun510.rtmp.utils.CameraInfoModel
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface UserApi {

    @POST("driver/login")
    suspend fun login(
        @Body loginBody: LoginBody
    ): LoginResponse

    @GET("stream-key")
    suspend fun getStreamKey(): Response<GetStreamKeyResponse>

    @GET("driver/camera-config")
    suspend fun getCameraConfig():CameraInfoModel

    @POST("stream/restart")
    suspend fun resetStream(
        @Query("streamKey") streamKey:String
    ):Response<Unit?>

}