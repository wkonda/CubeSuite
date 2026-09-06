package com.wkonda.cubesuite.tuner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.wkonda.cubesuite.tuner.ui.PermissionRequestScreen
import com.wkonda.cubesuite.tuner.ui.TunerScreen
import com.wkonda.cubesuite.tuner.ui.theme.TunerTheme

class MainActivity : ComponentActivity() {
    private var hasMicPermission by mutableStateOf(false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasMicPermission = granted
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasMicPermission = hasMicrophonePermission()
        setContent {
            TunerTheme {
                if (hasMicPermission) {
                    TunerScreen()
                } else {
                    PermissionRequestScreen(
                        onRequestPermission = {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasMicPermission = hasMicrophonePermission()
    }

    private fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
}
