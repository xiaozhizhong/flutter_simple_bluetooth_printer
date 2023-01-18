package com.xiao.flutter_simple_bluetooth_printer


import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import com.xiao.flutter_simple_bluetooth_printer.bluetooth.BLEManager
import com.xiao.flutter_simple_bluetooth_printer.bluetooth.BTError
import com.xiao.flutter_simple_bluetooth_printer.bluetooth.ClassicManager
import com.xiao.flutter_simple_bluetooth_printer.bluetooth.toWrapper
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry

/** FlutterSimpleBluetoothPrinterPlugin */
class FlutterSimpleBluetoothPrinterPlugin : FlutterPlugin, MethodCallHandler, PluginRegistry.RequestPermissionsResultListener,
    ActivityAware {

    companion object {
        const val URL_METHOD_CHANNEL = "flutter_simple_bluetooth_printer/method"
        const val URL_EVENT_CHANNEL = "flutter_simple_bluetooth_printer/event"
        private const val REQUEST_BT_PERMISSIONS = 1001
    }

    private var channel: MethodChannel? = null
    private var eventChannel: EventChannel? = null

    private lateinit var bleManager: BLEManager
    private lateinit var classicManager: ClassicManager

    private var currentActivity: Activity? = null

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        val applicationContext = flutterPluginBinding.applicationContext
        bleManager = BLEManager(applicationContext)
        classicManager = ClassicManager(applicationContext)

        channel = MethodChannel(flutterPluginBinding.binaryMessenger, URL_METHOD_CHANNEL)
        channel!!.setMethodCallHandler(this)
        bleManager.bindMethodChannel(channel!!)

        eventChannel = EventChannel(flutterPluginBinding.binaryMessenger, URL_EVENT_CHANNEL)
        eventChannel?.setStreamHandler(object : EventChannel.StreamHandler {

            override fun onListen(p0: Any?, sink: EventChannel.EventSink) {
                bleManager.bindEventSink(sink)
                classicManager.bindEventSink(sink)
            }

            override fun onCancel(p0: Any?) {
                bleManager.bindEventSink(null)
                classicManager.bindEventSink(null)
            }
        })
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel?.setMethodCallHandler(null)
        channel = null
        eventChannel?.setStreamHandler(null)
        eventChannel = null
        bleManager.bindMethodChannel(null)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        currentActivity = binding.activity
        binding.addRequestPermissionsResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        currentActivity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        currentActivity = binding.activity
        binding.addRequestPermissionsResultListener(this)
    }

    override fun onDetachedFromActivity() {
        currentActivity = null
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "getBondedDevices" -> ensureBluetoothAvailable(isScan = false, result = result) {
                bleManager.getBondedDevice(result.toWrapper)
            }
            "startDiscovery" -> ensureBluetoothAvailable(isScan = true, result = result) {
                bleManager.discovery(result.toWrapper)
            }
            "stopDiscovery" -> ensureBluetoothAvailable(isScan = true, result = result) {
                bleManager.stopDiscovery(result.toWrapper)
            }
            "connect" -> ensureBluetoothAvailable(isScan = false, result = result) {
                val address: String? = call.argument("address")
                val isBle: Boolean = call.argument("isBLE") ?: false
                val timeout: Int = call.argument("timeout")!!
                if (isBle) bleManager.connect(macAddress = address, timeout = timeout, result = result.toWrapper)
                else classicManager.connect(address = address, result = result.toWrapper)
            }
            "disconnect" -> ensureBluetoothAvailable(isScan = false, result = result) {
                val isBle: Boolean = call.argument("isBLE") ?: false
                if (isBle) bleManager.disconnect(result.toWrapper)
                else classicManager.disconnect(result.toWrapper)
            }
            "writeData" -> ensureBluetoothAvailable(isScan = false, result = result) {
                val bytes: ByteArray = call.argument("bytes")!!
                val isBle: Boolean = call.argument("isBLE") ?: false
                if (isBle) bleManager.writeRawData(bytes, result.toWrapper)
                else classicManager.writeRawData(bytes, result.toWrapper)
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    fun interface PendingCallback {
        fun onNext()
    }

    private var pendingCallbacks: PendingCallback? = null
    private var pendingResult: Result? = null

    private fun ensureBluetoothAvailable(isScan: Boolean, result: Result, callback: PendingCallback) {
        if (classicManager.bluetoothAdapter == null || !classicManager.bluetoothAdapter!!.isEnabled) {
            result.error(BTError.BluetoothNotAvailable.ordinal.toString(), null, null)
            return
        }
        ensurePermissions(isScan, result, callback)
    }

    private fun ensurePermissions(isScan: Boolean, result: Result, callback: PendingCallback) {
        val permissions = if(isScan) recommendedScanRuntimePermissions() else recommendedConnectRuntimePermissions()
//            bleManager.rxBleClient.run { if (isScan) recommendedScanRuntimePermissions else recommendedConnectRuntimePermissions }
        if (!hasPermissions(currentActivity, permissions)) {
            pendingCallbacks = callback
            pendingResult = result
            ActivityCompat.requestPermissions(currentActivity!!, permissions, REQUEST_BT_PERMISSIONS)
        } else {
            callback.onNext()
        }
    }

    private fun recommendedRuntimePermissions() = recommendedScanRuntimePermissions() + recommendedConnectRuntimePermissions()

    private fun recommendedScanRuntimePermissions(): Array<String> {
        val sdkVersion = Build.VERSION.SDK_INT
        if (sdkVersion < 23 /* pre Android M */) {
            // Before API 23 (Android M) no runtime permissions are needed
            return arrayOf()
        }
        if (sdkVersion < 31 /* pre Android 10 */) {
            // Since API 23 (Android M) ACCESS_COARSE_LOCATION or ACCESS_FINE_LOCATION allows for getting scan results
            return arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
//        if (sdkVersion < 31 /* pre Android 12 */) {
//            // Since API 29 (Android 10) only ACCESS_FINE_LOCATION allows for getting scan results
//            return arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
//        }
        // Since API 31 (Android 12) only BLUETOOTH_SCAN allows for getting scan results
        return arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private fun recommendedConnectRuntimePermissions(): Array<String> {
        val sdkVersion = Build.VERSION.SDK_INT
        if (sdkVersion < 31 /* pre Android M */) {
            // Before API 23 (Android M) no runtime permissions are needed
            return arrayOf()
        }
        return arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
    }

    private fun hasPermissions(context: Context?, permissions: Array<String>): Boolean {
        if (context != null) {
            for (permission in permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false
                }
            }
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray): Boolean {
        when (requestCode) {
            REQUEST_BT_PERMISSIONS -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    pendingCallbacks?.onNext()
                } else {
                    pendingResult?.error(BTError.PermissionNotGranted.ordinal.toString(), null, null)
                }
                pendingCallbacks = null
                pendingResult = null
                return true
            }
        }
        return false
    }

}

