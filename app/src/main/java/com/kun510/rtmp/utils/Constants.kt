package com.kun510.rtmp.utils

import android.util.Range
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.util.concurrent.TimeUnit

fun Float.fromPercent(range:Range<Int>):Int {
    if (this>=1) return range.upper-1
    if (this<=0) return range.lower+1
    val remainingNumber = (this*100).toInt()
    val difference = range.upper-1 - range.lower+1
    val calculated = remainingNumber*difference / 100
    return calculated + range.lower+1
}

object Constants {
    const val BASE_RTMP_URL = "rtmp://164.92.142.251/live/"
    fun getSocketUrl(token: String) :String{
        return "ws://164.92.142.251:3002?token=$token"
    }
    private fun getAuthHeader(token:String): Interceptor {
        return Interceptor { chain ->
            val request = chain.request().newBuilder().addHeader(
                "Authorization",
                "Bearer $token"
            ).build()
            chain.proceed(request)
        }
    }

    private fun getOkHttpClient(interceptor: Interceptor): OkHttpClient {
        return OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(interceptor).readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS).build()

    }
    fun getRetrofitObject(token:String) : Retrofit {
        return Retrofit.Builder()
            .baseUrl("http://164.92.142.251:3000/api/v1/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(getOkHttpClient(getAuthHeader(token)))
            .build()
    }
    fun getRetrofit2Object() : OurApi {
        return Retrofit.Builder()
            .baseUrl("http://141.11.184.69:3000/api/v1/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OurApi::class.java)
    }

    interface OurApi {
        @GET("status")
        suspend fun getStatus():Response<Unit>
    }
}