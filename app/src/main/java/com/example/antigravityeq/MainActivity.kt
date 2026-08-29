package com.example.antigravityeq

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.example.antigravityeq.theme.AntigravityEQTheme

class MainActivity : ComponentActivity() {

    companion object {
        var isCaptureRunning = mutableStateOf(false)
        var triggerCaptureLauncher: (() -> Unit)? = null
        var stopCaptureService: (() -> Unit)? = null
    }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, AudioStreamCaptureService::class.java).apply {
                action = AudioStreamCaptureService.ACTION_START_CAPTURE
                putExtra(AudioStreamCaptureService.EXTRA_RESULT_DATA, result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            isCaptureRunning.value = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        triggerCaptureLauncher = {
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjectionLauncher.launch(mpManager.createScreenCaptureIntent())
        }

        stopCaptureService = {
            val serviceIntent = Intent(this, AudioStreamCaptureService::class.java).apply {
                action = AudioStreamCaptureService.ACTION_STOP_CAPTURE
            }
            startService(serviceIntent)
            isCaptureRunning.value = false
        }

        setContent {
            AntigravityEQTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainNavigation()
                }
            }
        }
    }
}

