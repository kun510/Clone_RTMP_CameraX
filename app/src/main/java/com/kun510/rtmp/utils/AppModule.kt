package com.kun510.rtmp.utils

import android.content.Context
import com.kun510.rtmp.local.MySharedPreference
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    fun provideContext(@ApplicationContext context:Context) : Context = context.applicationContext

    @Provides
    fun provideGson():Gson = Gson()

    @Provides
    @Named("TokenInterceptor")
    fun providesToken(
        sharedPreference: MySharedPreference
    ): Interceptor {

        return Interceptor { chain ->
            val request = chain.request().newBuilder().addHeader(
                "Authorization",
                "Bearer ${sharedPreference.getToken()}"
            ).build()
            chain.proceed(request)
        }
    }
}