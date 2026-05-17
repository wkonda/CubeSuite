package com.wkonda.cubesuite.usb

import android.content.Context

interface UsbConnection {
    suspend fun findAndOpen(context: Context): Boolean
    suspend fun send(sysExMessage: ByteArray): Boolean
    suspend fun receive(): ByteArray?
    fun close()
}
