package com.xiao.flutter_simple_bluetooth_printer.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import io.flutter.plugin.common.EventChannel

/**
 * @author Xiao
 * @date 2023/01
 */

abstract class IBluetoothManager(context: Context) {
    private var eventSink: EventChannel.EventSink? = null

    private val bluetoothManager: BluetoothManager? = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    fun bindEventSink(sink: EventChannel.EventSink?) {
        eventSink = sink
    }

    protected fun updateConnectionState(state: BTConnectState) {
        val params = mapOf("state" to state.ordinal)
        eventSink?.success(params)
    }
}