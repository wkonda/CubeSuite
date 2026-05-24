package com.wkonda.cubesuite

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.wkonda.cubesuite.mvave.CubeBaby
import com.wkonda.cubesuite.mvave.Preset
import com.wkonda.cubesuite.mvave.Setting
import com.wkonda.cubesuite.mvave.Settings
import com.wkonda.cubesuite.ui.MainContent
import com.wkonda.cubesuite.ui.theme.CubeSuiteTheme
import com.wkonda.cubesuite.usb.UsbConnectionHandler
import com.wkonda.cubesuite.usb.UsbReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val cube by lazy { CubeBaby(UsbConnectionHandler(getSystemService(USB_SERVICE) as UsbManager)) }
    private val actions = MutableSharedFlow<Pair<Setting, Byte>>(extraBufferCapacity = 1)
    private val receiver by lazy {
        UsbReceiver(
            cube,
            lifecycleScope,
            { allSettings = it },
            actions
        )
    }
    private var allSettings by mutableStateOf<Map<Preset, Settings>?>(null)
    private var activePreset by mutableStateOf(Preset.A)
    private var isSuccess by mutableStateOf<Boolean?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val launcher =
                rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                    if (it) receiver.start(context) else finish()
                }
            DisposableEffect(Unit) {
                receiver.register(context)
                onDispose { receiver.unregister(context) }
            }
            LaunchedEffect(Unit) {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                ) receiver.start(context, { activePreset })
                else launcher.launch(Manifest.permission.RECORD_AUDIO)
            }
            CubeSuiteTheme {
                MainContent(allSettings, activePreset, isSuccess, { activePreset = it }, {
                    isSuccess = null
                    lifecycleScope.launch(Dispatchers.IO) {
                        val ok = cube.save(allSettings!!)
                        withContext(Dispatchers.Main) { isSuccess = ok }
                    }
                }) { type, value ->
                    actions.tryEmit(type to value)
                    allSettings = allSettings?.plus(
                        activePreset to allSettings!![activePreset]!!.update(
                            type,
                            value
                        )
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) receiver.start(
            this,
            { activePreset })
    }
}
