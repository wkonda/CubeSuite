package com.wkonda.cubesuite

import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.wkonda.cubesuite.mvave.CubeBaby
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
    private var settings by mutableStateOf<Settings?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CubeSuiteTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(), color = AppDarkBackground
                ) {
                    settings?.let { current ->
                        SettingsScreen(
                            settings = current, onAction = { type, newValue ->
                                actionFlow.tryEmit(type to newValue)
                                settings = current.update(type, newValue)
                            })
                    }
                }
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val opened = cube.findAndOpen(this@MainActivity)
            if (!opened) return@launch

            val s = cube.getCurrentSettings()
            withContext(Dispatchers.Main) {
                settings = s
            }
            @OptIn(FlowPreview::class) actionFlow.debounce(250).collect {
                cube.send(it.first, it.second)
            }
        }
    }
}