package com.kun510.rtmp.utils

import CameraController
import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest.Builder
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.kun510.rtmp.remote.UserApi
import com.kun510.rtmp.utils.Constants.BASE_RTMP_URL
import com.haishinkit.event.Event
import com.haishinkit.event.IEventListener
import com.haishinkit.media.Camera2Source
import com.haishinkit.rtmp.RtmpConnection
import com.haishinkit.rtmp.RtmpStream
import com.haishinkit.view.HkSurfaceView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.reflect.Field
import javax.inject.Singleton

@Singleton
class RtmpClient(
    private val context: Context,
    private val surfaceView: HkSurfaceView,
    private val userApi: UserApi
) : Camera2Source.Listener, IEventListener {

    private val TAG = "RtmpClient3"

    private var connection: RtmpConnection = RtmpConnection()
    private var localStream: RtmpStream = RtmpStream(connection)
    private var videoSource: Camera2Source = Camera2Source(context).apply {
        open(CameraCharacteristics.LENS_FACING_BACK)
    }
    private lateinit var url: String

    private var session: CameraCaptureSession? = null
    private var cameraManager: CameraManager? = null
    private var requestBuilder: Builder? = null
    private var key: String? = null

    private var cameraController: CameraController? = null
    private fun getCameraController(): CameraController? {
        return if (cameraController != null) {
            cameraController
        } else {
            try {
                requestBuilder?.let { requestCameraControlBuild(it, true) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            null
        }
    }
    val mainHandler = Handler(Looper.getMainLooper())

    private var isCameraOpen = false
    private var isPublishing = false
    private var currentCameraInfo: CameraInfoModel? = null

    init {
        localStream.attachVideo(videoSource)
        connection.addEventListener(Event.RTMP_STATUS, this@RtmpClient)
        surfaceView.attachStream(localStream)
        videoSource.listener = this@RtmpClient
    }

    fun start(
        info: CameraInfoModel, key: String?,
        isCameraOpenResult: (Boolean) -> Unit
    ) {
        Log.d(TAG, "kael start called publishing:$isPublishing camera:$isCameraOpen : $info ")
        if (currentCameraInfo == null) currentCameraInfo = info
        this@RtmpClient.key = key
        url = "$BASE_RTMP_URL$key"
        Log.d(TAG, "start: url hereeee = $url")
        handleStartOrUpdate(info, url)
        CoroutineScope(Dispatchers.IO).launch {
            delay(2000)
            isCameraOpenResult(isCameraOpen)
        }

    }


    private fun handleStartOrUpdate(info: CameraInfoModel, url: String) {
        if (currentCameraInfo?.fps != info.fps || currentCameraInfo?.bitrate != info.bitrate
            || currentCameraInfo?.width != info.width || currentCameraInfo?.height != info.height
        ) {
            CoroutineScope(Dispatchers.IO).launch {
                stopPublishing()
//                delay(1000)
                startPublishing(info, url)
            }

        } else {
            if (!isPublishing) {
                CoroutineScope(Dispatchers.IO).launch {
                    stopPublishing()
//                    delay(1000)
                    startPublishing(info, url)
                }
            } else {
                updatePublishing(
                    info,
                    info.exposureCompensation != currentCameraInfo?.exposureCompensation
                )
            }
        }

        currentCameraInfo = info

    }

    private fun updatePublishing(info: CameraInfoModel, isExposureUpdated: Boolean) {
        try {
            CoroutineScope(Dispatchers.IO).launch {
                getCameraController()?.updateCameraInfo(info, isExposureUpdated)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private fun startPublishing(info: CameraInfoModel, url: String) {
        try {
            localStream.attachVideo(videoSource)
            localStream.videoSetting.width = info.width // The width  of video output.
            localStream.videoSetting.height = info.height // The height  of video output.
            localStream.videoSetting.bitRate = info.bitrate // The bitRate of video output.
            localStream.videoSetting.frameRate = if (info.fps < 15) 15 else info.fps
            localStream.videoSetting.IFrameInterval = 2
            connection.connect(url)
            localStream.publish(url.split("live/")[1])
            CoroutineScope(Dispatchers.IO).launch {
                delay(3000)
                if (requestBuilder == null || session == null || cameraManager == null) {
                    try {
                        CoroutineScope(Dispatchers.IO).launch {
                            delay(2000)
                            val cameraManagerField: Field =
                                Camera2Source::class.java.getDeclaredField("manager")
                            cameraManagerField.isAccessible = true
                            cameraManager = cameraManagerField.get(videoSource) as CameraManager

                            val cameraSessionField: Field =
                                Camera2Source::class.java.getDeclaredField("session")
                            cameraSessionField.isAccessible = true
                            try {
                                delay(2000)
                                session = cameraSessionField.get(videoSource) as CameraCaptureSession
                                Log.d(TAG, "onCreate 11: $session")
                                getCameraController()
                                delay(2000)
                                Log.d(TAG, "startPublishing: update publishing 1 call shod")
                                delay(1000)
                                updatePublishing(
                                    info,
                                    info.exposureCompensation != currentCameraInfo?.exposureCompensation
                                )

                            } catch (e: Exception) {
                                e.printStackTrace()
                            }

                        }

                    } catch (e: NoSuchFieldException) {
                        Log.d(TAG, "onCreate: ${e.message}")
                        e.printStackTrace()
                    } catch (e: IllegalAccessException) {
                        Log.d(TAG, "onCreate: ${e.message}")
                        e.printStackTrace()
                    }
                }

            }

        } catch (e: Exception) {
            Log.d(TAG, "startPublishing: error  ${e.message}")
            e.printStackTrace()
        }

    }

//    private fun rotateCameraPreview(degrees: Int) {
//        Log.d(TAG, "rotateCameraPreview: called")
//        try {
//            requestBuilder.set(CaptureRequest.JPEG_ORIENTATION, 270)
//            session.setRepeatingRequest(requestBuilder.build(), null, null)
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
//    }


    private fun stopPublishing() {

        try {
            localStream.close()
            connection.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("SuspiciousIndentation")
    override fun onCreateCaptureRequest(builder: Builder) {
        this.requestBuilder = builder
        requestCameraControlBuild(builder, false)
    }

    private fun requestCameraControlBuild(builder: Builder, refresh: Boolean) {

        try {
            if (!refresh) {
                cameraController = cameraManager?.let {
                    session?.let { it1 ->
                        CameraController(
                            0.toString(),
                            it, it1, builder, mainHandler
                        )
                    }
                }
                isCameraOpen = true
            } else {
                try {
                    CoroutineScope(Dispatchers.IO).launch {
                        delay(2000)
                        val cameraManagerField: Field =
                            Camera2Source::class.java.getDeclaredField("manager")
                        cameraManagerField.isAccessible = true
                        cameraManager = cameraManagerField.get(videoSource) as CameraManager
                        delay(2000)
                        try {
                            val cameraSessionField: Field =
                                Camera2Source::class.java.getDeclaredField("session")
                            cameraSessionField.isAccessible = true
                            session = cameraSessionField.get(videoSource) as CameraCaptureSession
                            Log.d(TAG, "onCreate: $session")
                            if (cameraManager != null && session != null && requestBuilder != null) {
                                cameraController = CameraController(
                                    0.toString(), cameraManager!!, session!!, requestBuilder!!,
                                    mainHandler
                                )
                                isCameraOpen = true
                                delay(1000)
                                currentCameraInfo?.let {
                                    updatePublishing(
                                        it,
                                        false
                                    )
                                }
                            }
                        }catch (e:Exception){
                            e.printStackTrace()
                        }

                    }
                } catch (e: NoSuchFieldException) {
                    Log.d(TAG, "onCreate: ${e.message}")
                    e.printStackTrace()
                } catch (e: IllegalAccessException) {
                    Log.d(TAG, "onCreate: ${e.message}")
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onError(camera: CameraDevice, error: Int) {
        isCameraOpen = false
    }

    override fun handleEvent(event: Event) {
        isPublishing = event.data.toString().contains("code=NetConnection.Connect.Success")
                && event.type == "rtmpStatus"

//        if (isPublishing) {
//            CoroutineScope(Dispatchers.IO).launch {
////                delay(3000)
//                try {
//                    userApi.resetStream(key ?: "")
//                } catch (e: Exception) {
//                    Log.d(TAG, "handleEvent: ${e.message}")
//                    e.printStackTrace()
//                }
//            }
//        }
        Log.d(TAG, "handleStartOrUpdate: kael publisher setter 4 $isPublishing")
        Log.d(TAG, "handleStartOrUpdate: kael  ${event.data}")
        Log.d(TAG, "handleStartOrUpdate: kael  ${event.type}")

        if (event.data.toString().contains("code=NetConnection.Connect.Closed")
            && event.type == "rtmpStatus"
        ) {
            CoroutineScope(Dispatchers.IO).launch {

//                delay(3000)
                if (!isPublishing) {
                    stopPublishing()
                    delay(1000)
                    currentCameraInfo?.let { startPublishing(it, url) }
                }
            }
        }
    }

    fun restartConnection(){
        try {
            CoroutineScope(Dispatchers.IO).launch {
                localStream.close()
                connection.close()
                delay(1000)
                localStream.attachVideo(videoSource)
                connection.connect(url)
                localStream.publish(url.split("live/")[1])
            }
        }catch (e:Exception){
            e.printStackTrace()
        }
    }
    fun stop() {
        try {
            localStream.close()
            connection.close()
        }catch (e:Exception){
            e.printStackTrace()
        }
    }


}

