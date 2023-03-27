package com.xiao.flutter_simple_bluetooth_printer.bluetooth

import io.flutter.plugin.common.MethodCall
import java.util.UUID

sealed class ConnectionConfig {

    companion object {
        fun fromCall(call: MethodCall): ConnectionConfig {
            val isBle: Boolean = call.argument("isBLE") ?: false
            return if (isBle) {
                LEConnectionConfig(
                        autoConnect = call.argument("androidAutoConnect") ?: false,
                        timeout = call.argument<Int>("timeout")?.toLong() ?: 0,
                        priority = call.argument<Int>("connectionPriority") ?: 0
                )
            } else {
                ClassicConnectionConfig
            }
        }
    }
}

object ClassicConnectionConfig : ConnectionConfig()


class LEConnectionConfig(
        val autoConnect: Boolean,
        val timeout: Long,
        val priority: Int
) : ConnectionConfig()


