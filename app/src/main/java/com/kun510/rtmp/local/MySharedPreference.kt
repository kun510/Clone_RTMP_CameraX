package com.kun510.rtmp.local

import android.content.Context
import android.content.SharedPreferences
import com.kun510.rtmp.utils.CameraInfoModel
import com.google.gson.Gson
import java.lang.Exception
import javax.inject.Inject

class MySharedPreference @Inject constructor(
    context: Context,
    private val gson: Gson
) {
    private val pref: SharedPreferences = context.getSharedPreferences(
        "messenger",
        Context.MODE_PRIVATE
    )
    private val prefsEditor: SharedPreferences.Editor = pref.edit()

    fun setToken(token: String?) {
        prefsEditor.putString("token", token).apply()
    }

    fun getToken(): String? = pref.getString("token", null)

    fun getCameraModel(): CameraInfoModel{
        return try {
            gson.fromJson(
                pref.getString("model",null),CameraInfoModel::class.java
            )
        } catch (e:Exception){
            CameraInfoModel()
        }

    }

    fun setCameraModel(model:CameraInfoModel){
        prefsEditor.putString("model",gson.toJson(model)).apply()
    }

}