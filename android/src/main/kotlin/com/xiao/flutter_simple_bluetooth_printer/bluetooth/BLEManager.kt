package com.xiao.flutter_simple_bluetooth_printer.bluetooth

import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import com.polidea.rxandroidble3.RxBleClient
import com.polidea.rxandroidble3.RxBleConnection
import com.polidea.rxandroidble3.RxBleDevice
import com.polidea.rxandroidble3.Timeout
import com.polidea.rxandroidble3.scan.IsConnectable
import com.polidea.rxandroidble3.scan.ScanResult
import com.polidea.rxandroidble3.scan.ScanSettings
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.core.Single
import java.util.concurrent.TimeUnit
import io.flutter.plugin.common.MethodChannel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers

/**
 * @author Xiao
 * @date 2023/01
 * Bluetooth LE Manager, Using https://github.com/dariuszseweryn/RxAndroidBle
 */

class BLEManager(context: Context) : IBluetoothManager() {
    val rxBleClient: RxBleClient = RxBleClient.create(context)

    private var scanDisposable: Disposable? = null
    private var connectDisposable: Disposable? = null
    private var stateDisposable: Disposable? = null
    private var connection: RxBleConnection? = null
    private var lastConnectMac: String? = null

    private val nearbyDevices: MutableList<BluetoothDevice> = mutableListOf()
    private var methodChannel: MethodChannel? = null

    private var currentConnectState = BTConnectState.Disconnect

    fun bindMethodChannel(channel: MethodChannel?) {
        methodChannel = channel
    }

    fun getBondedDevice(result: FlutterResultWrapper) {
        try {
            val devices = rxBleClient.bondedDevices.map { BluetoothDevice.fromRxBleDevices(it).toMap() }
            result.success(devices)
        } catch (e: Throwable) {
            val error = e.toFlutterPlatformError
            result.error(error.code, error.message, error.details)
        }
    }

    fun discovery(result: FlutterResultWrapper) {
        // Clear the list of nearby devices
        nearbyDevices.clear()
        var resultSent = false

        scanDisposable = rxBleClient.scanBleDevices(
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // change if needed
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES) // change if needed
                .build() // add filters if needed
        ).subscribe(
            { scanResult ->
                if (!resultSent) {
                    resultSent = true
                    result.success(null)
                }
                addNearbyDevice(scanResult)
            },
            { throwable ->
                if (!resultSent) {
                    resultSent = true
                    val error = throwable.toFlutterPlatformError
                    result.error(error.code, error.message, error.details)
                }
            }
        )
    }

    fun stopDiscovery(result: FlutterResultWrapper) {
        result.success(true)
        scanDisposable?.isDisposed?.let {
            if (!it) scanDisposable?.dispose()
        }
    }

    private fun addNearbyDevice(scanResult: ScanResult) {
        if (scanResult.isConnectable == IsConnectable.NOT_CONNECTABLE || scanResult.bleDevice.name.isNullOrEmpty()) {
            return
        }
        if (nearbyDevices.any { it.name == scanResult.bleDevice.name && it.address == scanResult.bleDevice.macAddress }) {
            return
        }
        val device = BluetoothDevice.fromScanResult(scanResult)
        nearbyDevices.add(device)
        methodChannel?.invokeMethod("scanResult", device.toMap())
    }

    fun connect(macAddress: String?, timeout: Int, result: FlutterResultWrapper) {
        if (macAddress == null) {
            result.error(BTError.ErrorWithMessage.ordinal.toString(), "address is null", null)
            return
        }
        if (currentConnectState == BTConnectState.Connected) {
            if (macAddress == lastConnectMac) {
                // Already connected
                result.success(true)
                return
            }
            doDisconnect()
        }
        val device = rxBleClient.getBleDevice(macAddress)
        if (device == null) {
            result.error(BTError.ErrorWithMessage.ordinal.toString(), "can't not found device by $macAddress", null)
            return
        }
        connectDisposable = device.establishConnection(false, Timeout(timeout.toLong(), TimeUnit.MILLISECONDS))
            .subscribe(
                { connection ->
                    lastConnectMac = macAddress
                    this@BLEManager.connection = connection
                    result.success(true)
                },
                { throwable ->
                    val error = throwable.toFlutterPlatformError
                    result.error(error.code, error.message, error.details)
                }
            )
        stateDisposable = observeConnectionState(device)
    }

    fun disconnect(result: FlutterResultWrapper) {
        result.success(true)
        doDisconnect()
    }

    private fun doDisconnect() {
        doUpdateConnectionState(BTConnectState.Disconnect)
        connection = null
        lastConnectMac = null
        stateDisposable?.isDisposed?.let {
            if (!it) stateDisposable?.dispose()
        }
        connectDisposable?.isDisposed?.let {
            if (!it) connectDisposable?.dispose()
        }
    }

    fun writeRawData(data: ByteArray, result: FlutterResultWrapper) {
        if (connection == null) {
            result.error(BTError.ErrorWithMessage.ordinal.toString(), "connection is null", null)
            return
        }
        getWritableCharacteristic().toObservable().take(1).switchMap {
            connection!!.writeCharacteristic(it, data).toObservable()
        }.subscribe(
            { result.success(true) },
            { throwable ->
                val error = throwable.toFlutterPlatformError
                result.error(error.code, error.message, error.details)
            }
        )
    }

    private fun getWritableCharacteristic(): Single<BluetoothGattCharacteristic> {
        return connection!!.discoverServices()
            .flatMap { services ->
                val characteristic = services.bluetoothGattServices
                    .flatMap { it.characteristics.filter { char -> char.isWriteable } }
                    .minByOrNull { it.uuid }
                if (characteristic == null) {
                    Single.error(BTError.ErrorWithMessage.toError("can't not found writable characteristic"))
                } else {
                    Single.just(characteristic)
                }
            }
    }

    private fun observeConnectionState(device: RxBleDevice) = device.observeConnectionStateChanges()
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
            { connectionState ->
                if (connectionState == RxBleConnection.RxBleConnectionState.DISCONNECTED) doDisconnect()
                doUpdateConnectionState(connectionState.toState)
            },
            { doUpdateConnectionState(null) }
        )

    private val RxBleConnection.RxBleConnectionState?.toState
        get(): BTConnectState? {
            return when (this) {
                RxBleConnection.RxBleConnectionState.CONNECTING -> BTConnectState.Connecting
                RxBleConnection.RxBleConnectionState.CONNECTED -> BTConnectState.Connected
                RxBleConnection.RxBleConnectionState.DISCONNECTING -> null
                RxBleConnection.RxBleConnectionState.DISCONNECTED -> BTConnectState.Disconnect
                null -> BTConnectState.Fail
            }
        }

    private fun doUpdateConnectionState(state: BTConnectState?) {
        state ?:return
        if (currentConnectState == state) return
        currentConnectState = state
        updateConnectionState(state)
    }

}

private val BluetoothGattCharacteristic.isNotifiable: Boolean
    get() = properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0

private val BluetoothGattCharacteristic.isReadable: Boolean
    get() = properties and BluetoothGattCharacteristic.PROPERTY_READ != 0

private val BluetoothGattCharacteristic.isWriteable: Boolean
    get() = properties  == BluetoothGattCharacteristic.PROPERTY_WRITE ||  properties == BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE