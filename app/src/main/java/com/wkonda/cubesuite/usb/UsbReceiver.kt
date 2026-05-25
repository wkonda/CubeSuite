package com.wkonda.cubesuite.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import com.wkonda.cubesuite.mvave.CubeBaby
import com.wkonda.cubesuite.mvave.Preset
import com.wkonda.cubesuite.mvave.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UsbReceiver(
    private val cube: CubeBaby,
    private val scope: CoroutineScope,
    private val onUpdate: (Map<Preset, Settings>?) -> Unit,
) : BroadcastReceiver() {
    private var job: Job? = null

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) start(context)
        else if (intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
            onUpdate(null)
            cube.close()
            job?.cancel()
        }
    }

    fun start(context: Context) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            if (!cube.findAndOpen(context)) {
                return@launch
            }
            val s = cube.getCurrentSettings()
            withContext(Dispatchers.Main) { onUpdate(s) }
        }
    }

    fun register(context: Context) {
        context.registerReceiver(this, IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        })
    }

    fun unregister(context: Context) {
        context.unregisterReceiver(this)
        cube.close()
        job?.cancel()
    }
}
