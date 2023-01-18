package com.xiao.flutter_simple_bluetooth_printer.bluetooth

import io.flutter.plugin.common.MethodChannel.Result

/**
 * @author Xiao
 * @date 2023/01
 */

class FlutterResultWrapper(private val result: Result) {
    var isComplete = false

    fun success(data: Any?) {
        if (isComplete) return
        isComplete = true
        result.success(data)
    }

    fun error(code: String, message: String?, details: Any?) {
        if (isComplete) return
        isComplete = true
        result.error(code, message, details)
    }
}

val Result.toWrapper: FlutterResultWrapper
    get() = FlutterResultWrapper(this)
