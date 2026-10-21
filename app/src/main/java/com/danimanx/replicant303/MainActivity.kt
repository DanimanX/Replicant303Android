package com.danimanx.replicant303

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danimanx.replicant303.audio.AudioEngineService
import com.danimanx.replicant303.ui.ReplicantApp
import com.danimanx.replicant303.ui.theme.ReplicantTheme
import com.danimanx.replicant303.ui.theme.paletteFor

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* The engine still runs; the notification is simply less visible. */ }

    private val exportPreset = registerForActivityResult(
        ActivityResultContracts.CreateDocument("audio/midi")
    ) { uri ->
        if (uri != null) viewModel.exportPreset(uri)
    }

    private val importPreset = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importPreset(uri)
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = if (resources.configuration.smallestScreenWidthDp >= 600) {
            ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching { window.setSustainedPerformanceMode(true) }
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        startEngineService()

        setContent {
            val skin by viewModel.skin.collectAsStateWithLifecycle()
            val palette = paletteFor(skin)
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val windowSizeClass = calculateWindowSizeClass(this)
            ReplicantTheme(skin = skin) {
                ReplicantApp(
                    windowSizeClass = windowSizeClass,
                    viewModel = viewModel,
                    onExportPreset = {
                        exportPreset.launch("replicant303_preset.mid")
                    },
                    onImportPreset = {
                        importPreset.launch(arrayOf("audio/midi", "audio/x-midi", "application/octet-stream"))
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        startEngineService()
    }

    override fun onStop() {
        viewModel.saveSession()
        super.onStop()
    }

    private fun startEngineService() {
        runCatching {
            ContextCompat.startForegroundService(
                this,
                Intent(this, AudioEngineService::class.java)
            )
        }
    }
}
