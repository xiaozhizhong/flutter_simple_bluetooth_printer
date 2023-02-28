package com.xiao.flutter_simple_bluetooth_printer.bluetooth

/**
 * @author Xiao
 * @date 2023/02
 */

enum class BTState(val value: Int) {
    Unsupported(2),
    Unauthorized(3),
    PoweredOff(4),
    Available(5),
}