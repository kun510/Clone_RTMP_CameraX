package com.kun510.rtmp.ui.main

import android.R.attr.text
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.kun510.rtmp.databinding.ActivityMainBinding
import com.kun510.rtmp.local.MySharedPreference
import com.kun510.rtmp.service.MainService
import com.kun510.rtmp.service.MainServiceRepository
import com.kun510.rtmp.ui.login.LoginActivity
import com.kun510.rtmp.utils.Constants
import com.kun510.rtmp.utils.Constants.BASE_RTMP_URL
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : AppCompatActivity(), MainService.Listener {
    @Inject
    lateinit var sharedPreference: MySharedPreference
    private lateinit var views: ActivityMainBinding

    @Inject
    lateinit var viewModel: MainViewModel

    @Inject
    lateinit var mainServiceRepository: MainServiceRepository

    private val orientationList = listOf(
        "PORTRAIT", "LANDSCAPE_RTL", "LANDSCAPE_LTR"
    )
    private val orientationAdapter: ArrayAdapter<String> by lazy {
        ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, orientationList)
    }

    private val resolutionList = listOf(
        "320x480", "480x640", "720x1080", "1080x1920",
    )
    private val resolutionAdapter: ArrayAdapter<String> by lazy {
        ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, resolutionList)
    }

    private fun renderUi() {
        val model = sharedPreference.getCameraModel()
        views.apply {

            orientationSpinner.adapter = orientationAdapter
            when (model.orientation) {
                0 -> {
                    orientationSpinner.setSelection(0)
                }

                1 -> {
                    orientationSpinner.setSelection(1)
                }

                3 -> {
                    orientationSpinner.setSelection(2)
                }
            }

            zoomSeekbar.progress = model.zoomLevel

            resolutionSpinner.adapter = resolutionAdapter
            when (model.width) {
                320 -> {
                    resolutionSpinner.setSelection(0)
                }

                480 -> {
                    resolutionSpinner.setSelection(1)
                }

                720 -> {
                    resolutionSpinner.setSelection(2)
                }

                1080 -> {
                    resolutionSpinner.setSelection(3)
                }
            }

            fpsSeekBar.progress = model.fps
            streamBitrateEt.setText(model.bitrate.toString())

        }
    }

    private fun saveSettings() {
        views.apply {
            val model = sharedPreference.getCameraModel().copy(
                orientation = when (orientationSpinner.selectedItemPosition) {
                    0 -> 0
                    1 -> 1
                    else -> 3
                },
                zoomLevel = zoomSeekbar.progress,
                width = when (resolutionSpinner.selectedItemPosition) {
                    0 -> 320
                    1 -> 480
                    2 -> 720
                    else -> 1080
                },
                height = when (resolutionSpinner.selectedItemPosition) {
                    0 -> 480
                    1 -> 640
                    2 -> 1080
                    else -> 1920
                },
                fps = fpsSeekBar.progress,
                bitrate = streamBitrateEt.text.toString().toInt(),
            )
            sharedPreference.setCameraModel(model)
            mainServiceRepository.updateCamera()
        }


    }


    private val tag = "MainActivity"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        views = ActivityMainBinding.inflate(layoutInflater)
        setContentView(views.root)
        init()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        finishAffinity()
    }

    override fun onDestroy() {
        MainService.isUiActive = false
        MainService.listener = null
        super.onDestroy()
    }

    private fun init() {
        MainService.isUiActive = true
        MainService.listener = this

        views.saveBtn.setOnClickListener {
            saveSettings()
        }
        views.urlTv.text = MainService.currentUrl
        views.urlTv.setOnClickListener {
            val textToCopy = views.urlTv.text

            // Get the ClipboardManager service
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

            // Create a ClipData object holding the text
            val clip = ClipData.newPlainText("label", textToCopy)

            // Set the ClipData to the clipboard
            clipboard.setPrimaryClip(clip)

            // Optionally show a message to the user
            Toast.makeText(this, "Text copied to clipboard", Toast.LENGTH_SHORT).show()

        }
        if (MainService.isServiceRunning) {
            finishAffinity()
        }
        if (sharedPreference.getToken().isNullOrEmpty()) {
            this@MainActivity.startActivity(Intent(this@MainActivity, LoginActivity::class.java))
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                viewModel.init({ isDone, response ->
                    if (isDone && response != null) {
                        finishAffinity()
                        MainService.currentUrl = "$BASE_RTMP_URL${response.streamKey}"
                        views.urlTv.setText(MainService.currentUrl)
                    }
                }, {
                    this@MainActivity.startActivity(
                        Intent(
                            this@MainActivity,
                            LoginActivity::class.java
                        )
                    )
                })
                delay(5000)
                val result = try {
                    Constants.getRetrofit2Object().getStatus()
                } catch (e:Exception){
                    e.printStackTrace()
                    Log.d("TAG", "init: ${e.message}")
                    null
                }
                Log.d("TAG", "init: $result")
                if (result?.code() == 201) {
                    viewModel.finish()
                    runOnUiThread {
                        finishAffinity()
                        Toast.makeText(this@MainActivity, result.message(), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }


    override fun cameraOpenedSuccessfully() {
        runOnUiThread {
            finishAffinity()
        }
    }

}