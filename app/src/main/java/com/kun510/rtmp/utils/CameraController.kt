import android.content.Context
import android.graphics.Rect
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CameraMetadata.CONTROL_AE_MODE_OFF
import android.hardware.camera2.CameraMetadata.CONTROL_AE_MODE_ON
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.RggbChannelVector
import android.hardware.camera2.params.TonemapCurve
import android.os.Handler
import android.util.Log
import android.util.Range
import com.kun510.rtmp.utils.CameraInfoModel
import com.kun510.rtmp.utils.ExposureMode
import com.kun510.rtmp.utils.fromPercent
import java.lang.StrictMath.pow
import kotlin.math.max
import kotlin.math.min

/**
 * A controller class for managing various camera functionalities and parameters.
 *
 * @param cameraId The ID of the camera device.
 * @param cameraManager The CameraManager instance.
 * @param captureSession The active CameraCaptureSession.
 * @param captureBuilder The CaptureRequest.Builder.
 */
class CameraController(
    private val cameraId: String,
    private val cameraManager: CameraManager,
    private val captureSession: CameraCaptureSession,
    private val captureBuilder: CaptureRequest.Builder,
    private val handler: Handler,
) {
    private val TAG = "CameraController"

    /**
     * Get the characteristics of the camera device.
     *
     * @return CameraCharacteristics for the specified camera ID.
     */
    // Function to get camera characteristics
    private fun getCameraCharacteristics(): CameraCharacteristics {
        return cameraManager.getCameraCharacteristics(cameraId)
    }

    fun updateCameraInfo(info: CameraInfoModel, exposureUpdated: Boolean) {

//        val availableApertures =
//            getCameraCharacteristics().get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
//        Log.d(TAG, "updateCameraInfo aaa : ${isHDRSupported()}")
        try {
            setCustomWhiteBalance(info.red, info.green, info.blue)

            if (!exposureUpdated) {
//            if (!info.flashLight) {
                setExposureTime(info.shutterSpeed)
                getIsoRange()?.let { range ->
                    Log.d(TAG, "updateCameraInfo: isoRange $range")
                    val value = info.iso.fromPercent(range)
                    Log.d(TAG, "updateCameraInfo: isoValue $value")

                    setIso(value)
                }
            } else {
                setExposureCompensation(info.exposureCompensation)
            }


            if (info.zoomLevel >= 91) {
                setZoom(91)
            } else {
                setZoom(info.zoomLevel + 9)
            }

            val gama = if (info.gamma <= 0.1f) {
                0.1f
            } else if (info.gamma >= 5.0f) {
                5.0f
            } else {
                info.gamma
            }
//
            val contrast = if (info.contrast <= 0.1f) {
                0.1f
            } else if (info.contrast >= 2.0f) {
                2.0f
            } else {
                info.contrast
            }
            adjustGammaAndContrast2(gama, contrast)
////            if (info.flashLight) turnOnFlash() else turnOffFlash()
//
            if (info.isAutoWhiteBalance) {
                setAutoWhiteBalanceOn()
            } else {
                setCustomWhiteBalance(info.red, info.green, info.blue)
            }
//
            //            //focus mode
            val focus = if (info.focusPercent <= 0.1f) {
                0.1f
            } else {
                info.focusPercent
            }
//            turnOnFlash()
            setCustomFocusPercent2(focus * 100)
            setJpegQuality(100)
//            setFrameDuration(33333333)
//            setAERegion(focus)
//            if (info.flashLight) {
//                setAutoFocusForContinousOn()
//            } else {
//                setCustomFocusPercent2(focus * 100)
//            }
//            setHDRMode()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setJpegQuality(quality: Int) {
        if (quality !in 0..100) {
            throw IllegalArgumentException("JPEG quality must be between 0 and 100")
        }

        captureBuilder.set(
            CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE,
            CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY
        )

        captureBuilder.set(CaptureRequest.JPEG_QUALITY, quality.toByte())
        captureSession.setRepeatingRequest(captureBuilder.build(), null, null)

    }

    private fun setAutoFocusForContinousOn() {
        captureBuilder.set(
            CaptureRequest.CONTROL_AF_MODE,
            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE // Use CONTROL_AF_MODE_CONTINUOUS_VIDEO for video
//            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO // Use CONTROL_AF_MODE_CONTINUOUS_VIDEO for video
        )
        captureSession.setRepeatingRequest(captureBuilder.build(), null, null)
    }

    private fun setAutoFocusForContinousOff() {
        captureBuilder.set(
            CaptureRequest.CONTROL_EFFECT_MODE,
            CaptureRequest.CONTROL_EFFECT_MODE_OFF
        )
        captureSession.setRepeatingRequest(captureBuilder.build(), null, null)
    }

    private fun setAutoWhiteBalanceOn() {
        captureBuilder.set(
            CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO
        )
        captureBuilder.set(
            CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO
        )

        captureSession.setRepeatingRequest(captureBuilder.build(), null, null)

    }

    private fun setHDRMode() {
        try {
            // Create a CaptureRequest builder for still image capture
            captureBuilder.set(
                CaptureRequest.CONTROL_MODE,
                CaptureRequest.CONTROL_MODE_USE_SCENE_MODE
            )
            captureBuilder.set(
                CaptureRequest.CONTROL_SCENE_MODE,
                CaptureRequest.CONTROL_SCENE_MODE_HDR
            )
            // Capture the image with HDR
            captureSession.capture(captureBuilder.build(), null, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun isHDRSupported(): Boolean {
        try {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val availableSceneModes =
                characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES)

            return availableSceneModes?.contains(CameraCharacteristics.CONTROL_SCENE_MODE_HDR) == true
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
        return false
    }

    private fun setCustomWhiteBalance(redGain: Float, greenGain: Float, blueGain: Float) {

        try {
            captureBuilder.set(
                CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF
            )
            captureBuilder.set(
                CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF
            )
            val gains = RggbChannelVector(redGain, greenGain, greenGain, blueGain)
            captureBuilder.set(CaptureRequest.COLOR_CORRECTION_GAINS, gains)
            captureSession.setRepeatingRequest(captureBuilder.build(), null, null)
//            Log.d(TAG, "setCustomWhiteBalance: called")
        } catch (e: CameraAccessException) {
            Log.d(TAG, "setCustomWhiteBalance: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Set the zoom level of the camera.
     *
     * @param zoomLevel The desired zoom level (should be between 1.0 and maxZoom).
     */
    private fun setZoom(zoomLevel: Int) {
        // Ensure the input is within 1-100
        val safeZoomLevel = zoomLevel.coerceIn(1, 100)

        val characteristics = getCameraCharacteristics()
        val maxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)

        maxZoom?.let {
            // Calculate the zoom ratio
            val zoomRatio = safeZoomLevel / 100f * it

            // Define a minimum effective zoom ratio
            val minEffectiveSize = 0.1f // Ensure this is a Float to match zoomRatio's type

            // Use the larger of zoomRatio and minEffectiveSize
            val effectiveZoomRatio = maxOf(zoomRatio, minEffectiveSize)

            val zoomRect = calculateZoomRect(characteristics, effectiveZoomRatio)
            Log.d(
                TAG,
                "setZoom: max zoom $it, current zoom $effectiveZoomRatio zoom rect $zoomRect"
            )
            captureBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoomRect)
            captureSession.setRepeatingRequest(captureBuilder.build(), null, null)
        }
    }

    /**
     * Perform custom focus at a specific area on the camera preview.
     *
     * @param x The x-coordinate of the top-left corner of the focus area.
     * @param y The y-coordinate of the top-left corner of the focus area.
     * @param width The width of the focus area.
     * @param height The height of the focus area.
     */
    // Function to calculate zoom Rect
    private fun calculateZoomRect(characteristics: CameraCharacteristics, zoomLevel: Float): Rect {
        val sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        val centerX = sensorSize?.width()?.div(2) ?: 0
        val centerY = sensorSize?.height()?.div(2) ?: 0

        val deltaX = (sensorSize?.width()?.div((2 * zoomLevel)))?.toInt() ?: 0
        val deltaY = (sensorSize?.height()?.div((2 * zoomLevel)))?.toInt() ?: 0

        return Rect(centerX - deltaX, centerY - deltaY, centerX + deltaX, centerY + deltaY)
    }

    /**
     * Get the supported ISO range for the camera.
     *
     * @return Range of supported ISO values.
     */
    // Function to get ISO range
    private fun getIsoRange(): Range<Int>? {
        return getCameraCharacteristics().get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
    }

    /**
     * Set the ISO sensitivity of the camera.
     *
     * @param isoValue The desired ISO value (should be within supported range).
     */
    // Function to set ISO sensitivity (isoValue should be within supported range)
    private fun setIso(isoValue: Int) {
        val isoRange = getIsoRange()
        if (isoRange != null && isoValue in isoRange) {
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CONTROL_AE_MODE_OFF)

            captureBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, isoValue)
            captureSession.setRepeatingRequest(captureBuilder.build(), null, null)
            Log.d(TAG, "setIso: called")
        }
    }

    //shutter speed section
    /**
     * Get the supported exposure time range for the camera.
     *
     * @return Range of supported exposure times in nanoseconds.
     */
    // Function to get exposure time range
    private fun getExposureTimeRange(): Range<Long>? {
        return getCameraCharacteristics().get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
    }

    /**
     * SHUTTER SPEED
     * Set the exposure time of the camera.
     *
     * @param exposureTime The desired exposure time in nanoseconds.
     */
    // Function to set exposure time (exposureTime in nanoseconds)
    private fun setExposureTime(exposureTime: ExposureMode?) {
        val range = getExposureTimeRange()

        val exposureTimeInNs = when (exposureTime) {
            ExposureMode.EXPOSURE_1_4000 -> 1_000_000_000L / 4000
            ExposureMode.EXPOSURE_1_2000 -> 1_000_000_000L / 2000
            ExposureMode.EXPOSURE_1_1000 -> 1_000_000_000L / 1000
            ExposureMode.EXPOSURE_1_500 -> 1_000_000_000L / 500
            ExposureMode.EXPOSURE_1_250 -> 1_000_000_000L / 250
            ExposureMode.EXPOSURE_1_125 -> 1_000_000_000L / 125
            ExposureMode.EXPOSURE_1_60 -> 1_000_000_000L / 60
            ExposureMode.EXPOSURE_1_30 -> 1_000_000_000L / 30
            ExposureMode.EXPOSURE_1_15 -> 1_000_000_000L / 15
            ExposureMode.EXPOSURE_1_8 -> 1_000_000_000L / 8
            ExposureMode.EXPOSURE_1_4 -> 1_000_000_000L / 4
            ExposureMode.EXPOSURE_1_2 -> 1_000_000_000L / 2
            ExposureMode.EXPOSURE_1 -> 1_000_000_000L
            ExposureMode.EXPOSURE_2 -> 1_000_000_000L * 2
            ExposureMode.EXPOSURE_4 -> 1_000_000_000L * 4
            ExposureMode.EXPOSURE_8 -> 1_000_000_000L * 8
            ExposureMode.EXPOSURE_15 -> 1_000_000_000L * 15
            ExposureMode.EXPOSURE_30 -> 1_000_000_000L * 30
            else -> null
        }

        exposureTimeInNs?.let { calculatedTime ->
            val scaledTime = calculatedTime.coerceIn(range?.lower, range?.upper)
//            Log.d(TAG, "setExposureTime: called $scaledTime range$range")

            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CONTROL_AE_MODE_OFF)
            captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, scaledTime)
            captureSession.setRepeatingRequest(captureBuilder.build(), null, null)
        }
    }


    /**
     * Set the exposure compensation value of the camera.
     *
     * @param exposureCompensationValue The desired exposure compensation value in EV units.
     * -20 to 20
     */
    // Function to set exposure compensation (exposureCompensationValue should be in EV units)
    private fun setExposureCompensation(exposureCompensationValue: Int) {
        val aeCompensationRange =
            getCameraCharacteristics().get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)

        try {
//            val convertedVersion = exposureCompensationValue.toFloat().fromPercent(Range(aeCompensationRange!!.lower-1,aeCompensationRange.upper+1))
            val convertedVersion = mapNumber(
                exposureCompensationValue, -20, 20,
                aeCompensationRange!!.lower, aeCompensationRange.upper
            )
//            Log.d(TAG, "setExposureCompensation: range chosen $convertedVersion")
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CONTROL_AE_MODE_ON)

            captureBuilder.set(
                CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, convertedVersion
            )
//            Log.d(TAG, "setExposureCompensation: called $convertedVersion")
            captureSession.setRepeatingRequest(
                captureBuilder.build(),
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        super.onCaptureCompleted(session, request, result)

                        val isoValue = result.get(CaptureResult.SENSOR_SENSITIVITY)
                        Log.d(TAG, "ISO: $isoValue")

                        // Log Shutter Speed
                        val shutterSpeed = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
                        Log.d(TAG, "Shutter Speed: $shutterSpeed")

                        // Log Frame Duration
                        val frameDuration = result.get(CaptureResult.SENSOR_FRAME_DURATION)
                        Log.d(TAG, "Frame Duration: $frameDuration")

                        // Log AE Mode
                        val aeMode = result.get(CaptureResult.CONTROL_AE_MODE)
                        Log.d(TAG, "AE Mode: $aeMode")

                        // Log AE Exposure Compensation
                        val aeExposureCompensation =
                            result.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION)
                        Log.d(TAG, "AE Exposure Compensation: $aeExposureCompensation")

                        // Log AE Lock
                        val aeLock = result.get(CaptureResult.CONTROL_AE_LOCK)
                        Log.d(TAG, "AE Lock: $aeLock")

                        // Log AE State
                        val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                        Log.d(TAG, "AE State: $aeState")

                        // Log AE Regions
                        val aeRegions = result.get(CaptureResult.CONTROL_AE_REGIONS)
                            ?.joinToString { it.toString() }
                        Log.d(TAG, "AE Regions: $aeRegions")

                    }

                    override fun onCaptureFailed(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        failure: CaptureFailure
                    ) {
                        super.onCaptureFailed(session, request, failure)
                        Log.d(TAG, "onCaptureFailed: ${failure.reason}")
                        // Handle capture failure
                    }
                },
                handler
            )

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun mapNumber(
        value: Int,
        originalRangeStart: Int,
        originalRangeEnd: Int,
        targetRangeStart: Int,
        targetRangeEnd: Int
    ): Int {
        // Check if the input number is within the initial range
        if (value !in originalRangeStart..originalRangeEnd) {
            throw IllegalArgumentException("Input number is outside the initial range")
        }
        return targetRangeStart + (value - originalRangeStart) * (targetRangeEnd - targetRangeStart) / (originalRangeEnd - originalRangeStart)
    }

    /**
     * Turn on the flash of the camera.
     */
    fun turnOnFlash() {
        captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
        captureSession.setRepeatingRequest(captureBuilder.build(), null, null)
    }

    /**
     * Turn off the flash of the camera.
     */
    // Function to turn off the flash
    fun turnOffFlash() {
        captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
        captureSession.setRepeatingRequest(captureBuilder.build(), null, null)
    }

    /**
     * Perform custom focus at a specific area on the camera preview.
     *
     * @param x The x-coordinate of the top-left corner of the focus area.
     * @param y The y-coordinate of the top-left corner of the focus area.
     * @param width The width of the focus area.
     * @param height The height of the focus area.
     */
    fun setCustomFocus(x: Int, y: Int, width: Int, height: Int) {
        val rect = Rect(x, y, x + height, y + width)
        val meteringRectangle = MeteringRectangle(rect, MeteringRectangle.METERING_WEIGHT_MAX)

        val meteringRectangles = arrayOf(meteringRectangle)

        captureBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, meteringRectangles)
        captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
        captureSession.setRepeatingRequest(captureBuilder.build(), null, null)
    }

    private fun setCustomFocusPercent(percent: Float) {
        try {
            val characteristics = getCameraCharacteristics()
            val sensorSize =
                characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)

            val x = (sensorSize?.width()!! * percent / 100).toInt()
            val y = (sensorSize.height() * percent / 100).toInt()

            val halfWidth =
                (sensorSize.width() * 0.1).toInt()  // Assuming 10% of the width for focus area
            val halfHeight =
                (sensorSize.height() * 0.1).toInt()  // Assuming 10% of the height for focus area

            val rect = Rect(x - halfWidth, y - halfHeight, x + halfWidth, y + halfHeight)

            val meteringRectangle = MeteringRectangle(rect, MeteringRectangle.METERING_WEIGHT_MAX)

            val meteringRectangles = arrayOf(meteringRectangle)

            // Disable auto focus
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)

            // Set focus distance manually
            val minFocusDistance =
                characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
            val focusDistance = minFocusDistance + (percent / 100) * (1 - minFocusDistance)
            captureBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, focusDistance)

            captureBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, meteringRectangles)
            captureSession.setRepeatingRequest(captureBuilder.build(), null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private fun setCustomFocusPercent2(percent: Float) {
        try {
            val characteristics = getCameraCharacteristics()
            val minFocusDistance =
                characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
                    ?: return

            // Convert the input percent to a focus distance value
            // 0% corresponds to infinity, and 100% to the minimum focus distance
            val focusDistance = minFocusDistance * (1 - percent / 100)

            // Set the manual focus distance
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            captureBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, focusDistance)

            captureSession.setRepeatingRequest(captureBuilder.build(), null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private fun setFrameDuration(percent: Int) {

        val maxFrameDuration =
            getMaxSupportedFrameDuration() // Get the maximum frame duration supported by the camera


        Log.d(TAG, "setFrameDuration: $maxFrameDuration")
        Log.d(TAG, "setFrameDuration2: ${(maxFrameDuration!! * percent).toLong()}")

        captureBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, (percent).toLong())
        // Apply other settings and start the capture session as needed...
        captureSession.setRepeatingRequest(captureBuilder.build(), null, null)

    }

    private fun getMaxSupportedFrameDuration(): Long? {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        return characteristics.get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION)
    }

    fun setAERegion(percent: Float) {
        try {
            val sensorArraySize =
                getCameraCharacteristics().get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)

            // Calculate the AE region size and position based on the percentage
            val regionWidth = (sensorArraySize?.width()?.times(percent))?.toInt()
            val regionHeight = (sensorArraySize?.height()?.times(percent))?.toInt()
            val regionX = (regionWidth?.let { sensorArraySize.width().minus(it) })?.div(2)
            val regionY = (regionHeight?.let { sensorArraySize.height().minus(it) })?.div(2)

            val aeRegion = MeteringRectangle(
                regionX!!,
                regionY!!,
                regionWidth,
                regionHeight,
                MeteringRectangle.METERING_WEIGHT_MAX
            )

            // Set the AE region in the capture request
            captureBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(aeRegion))

        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    /**
     * Set the scene mode of the camera.
     *
     * @param sceneMode The desired scene mode.
     */
    // Function to set scene mode
    fun setSceneMode(sceneMode: Int) {
        captureBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, sceneMode)
        captureSession.setRepeatingRequest(captureBuilder.build(), null, null)
    }

    /**
     * Enable or disable face detection.
     *
     * @param enabled Boolean indicating whether face detection should be enabled.
     */
    // Function to enable/disable face detection
    fun setFaceDetectionEnabled(enabled: Boolean) {
        captureBuilder.set(
            CaptureRequest.STATISTICS_FACE_DETECT_MODE,
            if (enabled) CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL else CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF
        )
        captureSession.setRepeatingRequest(captureBuilder.build(), null, null)
    }

    private fun adjustGammaAndContrast(gamma: Float, contrast: Float) {
        // Adjust Gamma
        val MAX_GAMMA = 5.0f
        val MIN_GAMMA = 0.1f
        val adjustedGamma = max(MIN_GAMMA, min(gamma, MAX_GAMMA))

        // Adjust Contrast
        val MAX_CONTRAST = 2.0f
        val MIN_CONTRAST = 0.0f
        val adjustedContrast = max(MIN_CONTRAST, min(contrast, MAX_CONTRAST))

        val mid = 0.5f
        val size = 256
        val curve = FloatArray(size * 2)
        for (i in 0 until size) {
            val originalValue = i / 255.0f

            // Apply Gamma Adjustment
            val gammaCorrectedValue =
                pow(originalValue.toDouble(), (1.0f / adjustedGamma).toDouble()).toFloat()

            // Apply Contrast Adjustment
            val contrastCorrectedValue = if (gammaCorrectedValue < mid) {
                (pow(
                    (gammaCorrectedValue / mid).toDouble(),
                    adjustedContrast.toDouble()
                ) * mid).toFloat()
            } else {
                (1 - pow(
                    ((1 - gammaCorrectedValue) / mid).toDouble(),
                    adjustedContrast.toDouble()
                ) * mid).toFloat()
            }

            curve[i * 2] = originalValue
            curve[i * 2 + 1] = contrastCorrectedValue
        }

        val tonemapCurve = TonemapCurve(curve, curve, curve)
        captureBuilder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE)
        captureBuilder.set(CaptureRequest.TONEMAP_CURVE, tonemapCurve)
    }

    private fun adjustGammaAndContrast2(gamma: Float, contrast: Float) {
        val cameraCharacteristics = getCameraCharacteristics()
        val (gammaRange, contrastRange) = setGammaAndContrastRanges(cameraCharacteristics)

        // Adjust Gamma within the range
        val adjustedGamma = max(gammaRange.start, min(gamma, gammaRange.endInclusive))

        // Adjust Contrast within the range
        val adjustedContrast = max(contrastRange.start, min(contrast, contrastRange.endInclusive))

        val mid = 0.5f
        val size = 256
        val curve = FloatArray(size * 2)
        for (i in 0 until size) {
            val originalValue = i / 255.0f

            // Apply Gamma Adjustment
            val gammaCorrectedValue =
                pow(originalValue.toDouble(), (1.0f / adjustedGamma).toDouble()).toFloat()

            // Apply Contrast Adjustment
            val contrastCorrectedValue = if (gammaCorrectedValue < mid) {
                (pow(
                    (gammaCorrectedValue / mid).toDouble(),
                    adjustedContrast.toDouble()
                ) * mid).toFloat()
            } else {
                (1 - pow(
                    ((1 - gammaCorrectedValue) / mid).toDouble(),
                    adjustedContrast.toDouble()
                ) * mid).toFloat()
            }

            curve[i * 2] = originalValue
            curve[i * 2 + 1] = contrastCorrectedValue
        }

        val tonemapCurve = TonemapCurve(curve, curve, curve)
        captureBuilder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE)
        captureBuilder.set(CaptureRequest.TONEMAP_CURVE, tonemapCurve)
    }

    fun getCameraCapabilities(context: Context, cameraId: String): CameraCharacteristics {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return cameraManager.getCameraCharacteristics(cameraId)
    }

    fun setGammaAndContrastRanges(cameraCharacteristics: CameraCharacteristics): Pair<ClosedFloatingPointRange<Float>, ClosedFloatingPointRange<Float>> {
        val maxCurvePoints =
            cameraCharacteristics.get(CameraCharacteristics.TONEMAP_MAX_CURVE_POINTS)
        val SOME_THRESHOLD = 10 // Example threshold, adjust based on your requirements

        val gammaRange = if (maxCurvePoints != null && maxCurvePoints >= SOME_THRESHOLD) {
            0.1f..5.0f
        } else {
            0.5f..2.0f
        }

        val contrastRange = if (maxCurvePoints != null && maxCurvePoints >= SOME_THRESHOLD) {
            0.0f..2.0f
        } else {
            0.2f..1.5f
        }

        return Pair(gammaRange, contrastRange)
    }


//    fun adjustGamma(gamma: Float) {
//        val MAX_GAMMA = 5.0f
//        val MIN_GAMMA = 0.1f
//        val adjustedGamma = max(MIN_GAMMA, min(gamma, MAX_GAMMA))
//
//        val size = 256
//        val curve = FloatArray(size * 2)
//        for (i in 0 until size) {
//            curve[i * 2] = i / 255.0f
//            curve[i * 2 + 1] =
//                pow(curve[i * 2].toDouble(), (1.0f / adjustedGamma).toDouble()).toFloat()
//        }
//
//        val tonemapCurve = TonemapCurve(curve, curve, curve)
//        captureBuilder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE)
//        captureBuilder.set(CaptureRequest.TONEMAP_CURVE, tonemapCurve)
//    }
//
//    fun adjustContrast(contrast: Float) {
//        val MAX_CONTRAST = 2.0f
//        val MIN_CONTRAST = 0.0f
//        val adjustedContrast = max(MIN_CONTRAST, min(contrast, MAX_CONTRAST))
//
//        val mid = 0.5f
//        val size = 256
//        val curve = FloatArray(size * 2)
//        for (i in 0 until size) {
//            val value = i / 255.0f
//            curve[i * 2] = value
//            if (value < mid) {
//                curve[i * 2 + 1] =
//                    (pow((value / mid).toDouble(), adjustedContrast.toDouble()) * mid).toFloat()
//            } else {
//                curve[i * 2 + 1] = (1 - pow(
//                    ((1 - value) / mid).toDouble(),
//                    adjustedContrast.toDouble()
//                ) * mid).toFloat()
//            }
//        }
//
//        val tonemapCurve = TonemapCurve(curve, curve, curve)
//        captureBuilder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE)
//        captureBuilder.set(CaptureRequest.TONEMAP_CURVE, tonemapCurve)
//    }


}
