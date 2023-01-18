package com.xiao.flutter_simple_bluetooth_printer.bluetooth

import io.flutter.plugin.common.EventChannel

/**
 * @author Xiao
 * @date 2023/01
 */

abstract class IBluetoothManager {
    private var eventSink: EventChannel.EventSink? = null
    fun bindEventSink(sink: EventChannel.EventSink?) {
        eventSink = sink
    }

    protected fun updateConnectionState(state: BTConnectState) {
        val params = mapOf("state" to state.ordinal)
        eventSink?.success(params)
    }
}