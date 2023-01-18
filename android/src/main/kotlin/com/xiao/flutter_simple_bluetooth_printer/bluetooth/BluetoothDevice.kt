package com.xiao.flutter_simple_bluetooth_printer.bluetooth

import android.annotation.SuppressLint
import com.polidea.rxandroidble3.RxBleDevice
import com.polidea.rxandroidble3.scan.ScanResult

/**
 * @author xiao
 * @date 2023/01
 */

@SuppressLint("MissingPermission")
data class BluetoothDevice(
    val name: String,
    val address: String,
    val rssi:Int,
    val type: Int,
    val deviceClass : Int,
    val majorDeviceClass:Int,
    val bondState: Int
) {
    companion object {
        fun fromScanResult(result: ScanResult): BluetoothDevice {
            val device = result.bleDevice
            return BluetoothDevice(
                name= device.name!!,
                address = device.macAddress,
                rssi = result.rssi,
                type = device.bluetoothDevice.type,
                deviceClass = device.bluetoothDevice.bluetoothClass.deviceClass,
                majorDeviceClass = device.bluetoothDevice.bluetoothClass.majorDeviceClass,
                bondState = device.bluetoothDevice.bondState
            )
        }
        fun fromRxBleDevices(device: RxBleDevice): BluetoothDevice {
            return BluetoothDevice(
                name= device.name!!,
                address = device.macAddress,
                rssi = -1,
                type = device.bluetoothDevice.type,
                deviceClass = device.bluetoothDevice.bluetoothClass.deviceClass,
                majorDeviceClass = device.bluetoothDevice.bluetoothClass.majorDeviceClass,
                bondState = device.bluetoothDevice.bondState
            )
        }
    }

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "name" to name,
            "address" to address,
            "rssi" to rssi,
            "type" to type,
            "deviceClass" to deviceClass,
            "majorDeviceClass" to majorDeviceClass,
            "bondState" to bondState
        )
    }
}
