package com.wkonda.cubesuite

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.wkonda.cubesuite.mvave.CubeBaby
import com.wkonda.cubesuite.mvave.Preset
import com.wkonda.cubesuite.mvave.Setting
import com.wkonda.cubesuite.mvave.Settings
import com.wkonda.cubesuite.ui.SettingsScreen
import com.wkonda.cubesuite.ui.theme.AppDarkBackground
import com.wkonda.cubesuite.ui.theme.CubeSuiteTheme
import com.wkonda.cubesuite.usb.UsbConnectionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val cube by lazy {
        val h = UsbConnectionHandler(getSystemService(USB_SERVICE) as UsbManager)
        CubeBaby(h)
    }
    val actionFlow = MutableSharedFlow<Pair<Setting, Byte>>(extraBufferCapacity = 1)
    private var allSettings by mutableStateOf<Map<Preset, Settings>?>(null)
    private var activePreset by mutableStateOf(Preset.A)
    private var isSuccess by mutableStateOf<Boolean?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { if (it) startUsbConnection() else finish() }

            LaunchedEffect(Unit) {
                if (ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.RECORD_AUDIO
                    )
                    == PackageManager.PERMISSION_GRANTED
                ) startUsbConnection()
                else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }

            CubeSuiteTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(), color = AppDarkBackground
                ) {
                    allSettings ?: return@Surface

                    SettingsScreen(
                        activePreset = activePreset,
                        settings = allSettings!![activePreset]!!,
                        isSuccess = isSuccess,
                        onPresetSelected = { activePreset = it },
                        onSave = {
                            isSuccess = null
                            lifecycleScope.launch(Dispatchers.IO) {
                                val success = cube.save(allSettings!!)
                                withContext(Dispatchers.Main) {
                                    isSuccess = success
                                }
                            }
                        },
                        onAction = { type, newValue ->
                            actionFlow.tryEmit(type to newValue)
                            allSettings = allSettings?.plus(
                                activePreset to allSettings!![activePreset]!!.update(
                                    type, newValue
                                )
                            )
                        })
                }
            }
        }
    }

    private fun startUsbConnection() {
        lifecycleScope.launch(Dispatchers.IO) {
            val opened = cube.findAndOpen(this@MainActivity)
            if (!opened) return@launch

            val deviceName = cube.getDeviceName()
            val s = cube.getCurrentSettings()
            withContext(Dispatchers.Main) {
                allSettings = s
            }
            @OptIn(FlowPreview::class) actionFlow.debounce(250).collect {
                cube.send(activePreset, it.first, it.second)
            }
        }
    }
}