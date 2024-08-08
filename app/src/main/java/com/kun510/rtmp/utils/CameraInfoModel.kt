package com.kun510.rtmp.utils

data class CameraInfoModel(

    //only if front camera is off
    val flashLight:Boolean = false,

    //0, 90, 180, 270
    val orientation:Int = 180,

    //1 to 8
    val zoomLevel:Int=1,

    //0f to 1f
    val iso:Float=0.5f,

    //shutter speed mode
    val shutterSpeed:ExposureMode=ExposureMode.AUTO,

    //exposure
    //-20 to 20
    val exposureCompensation:Int =0,

    //camera quality
    val width:Int=720,
    val height:Int=1080,
    val fps:Int=30,
    val bitrate:Int=2500000,

    //focus
    //0 to 1f
    val focusPercent:Float=0.5f,

    //white balance
    //0f to 1f
    val red:Float = 0.5f,
    //0f to 1f
    val blue:Float = 0.5f,
    //0f to 1f
    val green:Float = 0.5f,


    val isAutoWhiteBalance : Boolean = false,

    //0.0f to 2.0f
    val contrast:Float = 1.0f,

    //0.1f to 5.0f
    val gamma:Float = 0.01f


    )

enum class ExposureMode {
    AUTO,
    EXPOSURE_1_4000,
    EXPOSURE_1_2000,
    EXPOSURE_1_1000,
    EXPOSURE_1_500,
    EXPOSURE_1_250,
    EXPOSURE_1_125,
    EXPOSURE_1_60,
    EXPOSURE_1_30,
    EXPOSURE_1_15,
    EXPOSURE_1_8,
    EXPOSURE_1_4,
    EXPOSURE_1_2,
    EXPOSURE_1,
    EXPOSURE_2,
    EXPOSURE_4,
    EXPOSURE_8,
    EXPOSURE_15,
    EXPOSURE_30
}
