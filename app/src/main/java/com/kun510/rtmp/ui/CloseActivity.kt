package com.kun510.rtmp.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.kun510.rtmp.service.MainServiceRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CloseActivity : AppCompatActivity() {

    @Inject lateinit var repository: MainServiceRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository.stopService()
        finishAffinity()
    }
}