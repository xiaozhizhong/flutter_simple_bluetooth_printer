package com.xiao.flutter_simple_bluetooth_printer.bluetooth

/**
 * @author Shawx
 * @date 2023/01
 */
data class FlutterPlatformError(
    val code: String,
    override val message: String?,
    val details: Any?
) : Throwable(message)

val Throwable.toFlutterPlatformError: FlutterPlatformError
    get() {
        return when (this) {
            is FlutterPlatformError -> this
            else -> BTError.ErrorWithMessage.toError(message)
        }
    }

enum class BTError {
    BluetoothNotAvailable,
    PermissionNotGranted,
    ErrorWithMessage,
    Unknown;

    fun toError(message: String? = null) = FlutterPlatformError(code = this.ordinal.toString(), message = message, details = null)

}