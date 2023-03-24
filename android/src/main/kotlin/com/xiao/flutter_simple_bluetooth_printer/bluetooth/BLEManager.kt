package com.xiao.flutter_simple_bluetooth_printer.bluetooth

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import com.polidea.rxandroidble3.*
import com.polidea.rxandroidble3.scan.IsConnectable
import com.polidea.rxandroidble3.scan.ScanResult
import com.polidea.rxandroidble3.scan.ScanSettings
import io.flutter.plugin.common.MethodChannel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.util.*
import java.util.concurrent.TimeUnit


/**
 * @author Xiao
 * @date 2023/01
 * Bluetooth LE Manager, Using https://github.com/dariuszseweryn/RxAndroidBle
 */

class BLEManager(context: Context) : IBluetoothManager() {
    companion object {
        const val CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb"
    }

    private val rxBleClient: RxBleClient = RxBleClient.create(context)

    private var scanDisposable: Disposable? = null

    private val nearbyDevices: MutableList<BluetoothDevice> = mutableListOf()
    private var methodChannel: MethodChannel? = null

    private var connectionData: BLEConnectionData? = null

    private var lastConnectState: BTConnectState? = null

    fun bindMethodChannel(channel: MethodChannel?) {
        methodChannel = channel
    }

    private fun isConnected() = connectionData?.getConnection() != null

    private fun isConnected(macAddress: String) = isConnected() && connectionData?.macAddress == macAddress

    fun ensureConnected(address: String, result: FlutterResultWrapper) {
        if (isConnected(address)) {
            // Already connected
            connectionData?.isActive = true
            result.success(true)
            return
        }
        result.success(false)
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

        val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build()
        scanDisposable = rxBleClient.scanBleDevices(scanSettings)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ scanResult ->
                    result.success(null)
                    addNearbyDevice(scanResult)
                }, { throwable ->
                    val error = throwable.toFlutterPlatformError
                    result.error(error.code, error.message, error.details)
                })
    }

    fun stopDiscovery(result: FlutterResultWrapper) {
        scanDisposable.tryDispose()
        result.success(true)
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

    fun connect(macAddress: String?, config: LEConnectionConfig, result: FlutterResultWrapper) {
        if (macAddress == null) {
            result.error(BTError.ErrorWithMessage.ordinal.toString(), "address is null", null)
            return
        }
        val device = rxBleClient.getBleDevice(macAddress)
        if (device == null) {
            result.error(BTError.ErrorWithMessage.ordinal.toString(), "can't not found device by $macAddress", null)
            return
        }
        if (isConnected()) {
            if (isConnected(macAddress)) {
                // Already connected
                connectionData!!.isActive = true
                result.success(true)
                return
            }
            // Disconnect the previous connection
            removeConnectionData()
        }
        connectionData = BLEConnectionData(macAddress)

        observeConnectionState(device)

        val connectionObservable = if (config.timeout > 0) {
            device.establishConnection(config.autoConnect, Timeout(config.timeout, TimeUnit.MILLISECONDS))
        } else {
            device.establishConnection(config.autoConnect)
        }
        connectionObservable
                .requestConnectionPriorityIfNeeded(config.priority)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    connectionData?.setConnection(it)
                    connectionData?.isActive = true
                    result.success(true)
                }, { throwable ->
                    removeConnectionData()
                    val error = throwable.toFlutterPlatformError
                    result.error(error.code, error.message, error.details)
                })
                .let { connectionData?.addDisposable(it) }
    }

    private fun observeConnectionState(device: RxBleDevice) {
        device.observeConnectionStateChanges()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { connectionState ->
                    doUpdateConnectionState(connectionState.toState)
                    if (connectionState == RxBleConnection.RxBleConnectionState.DISCONNECTED) {
                        removeConnectionData()
                    }
                }.let { connectionData?.addDisposable(it) }
    }

    private fun removeConnectionData() {
        connectionData?.dispose()
        connectionData = null
    }

    private fun Observable<RxBleConnection>.requestConnectionPriorityIfNeeded(priority: Int): Observable<RxBleConnection> {
        return this.switchMap { connection ->
            if (priority != connectionData?.connectionPriority)
                connection.requestConnectionPriority(priority, 100L, TimeUnit.MILLISECONDS)
                        .doOnComplete { connectionData?.connectionPriority = priority }
                        .andThen(Observable.just(connection))
            else Observable.just(connection)
        }
    }

    fun setupNotification(result: FlutterResultWrapper, characteristicUuid: String) {
        val connection = getConnectionOrNull(result) ?: return
        connection.setupNotification(UUID.fromString(characteristicUuid))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { result.success(true) },
                        { throwable ->
                            val error = throwable.toFlutterPlatformError
                            result.error(error.code, error.message, error.details)
                        })
                .let { connectionData?.addDisposable(it) }
    }

    fun setupIndication(result: FlutterResultWrapper, characteristicUuid: String) {
        val connection = getConnectionOrNull(result) ?: return
        connection.setupIndication(UUID.fromString(characteristicUuid))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { result.success(true) },
                        { throwable ->
                            val error = throwable.toFlutterPlatformError
                            result.error(error.code, error.message, error.details)
                        })
                .let { connectionData?.addDisposable(it) }
    }

    fun requestMtu(result: FlutterResultWrapper, mtu: Int) {
        val connection = getConnectionOrNull(result) ?: return
        connection.requestMtu(mtu)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { result.success(it) },
                        { throwable ->
                            val error = throwable.toFlutterPlatformError
                            result.error(error.code, error.message, error.details)
                        })
                .let { connectionData?.addDisposable(it) }
    }
//
//    private fun rescanAndConnect(macAddress: String) {
//        val scanSettings = ScanSettings.Builder()
//                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
//                .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
//                .build()
//        val filter = ScanFilter.Builder()
//                .setDeviceAddress(macAddress)
//                .build()
//        rxBleClient.scanBleDevices(scanSettings, filter)
//                .take(5000, TimeUnit.MILLISECONDS)
//                .doFinally { }
//
//    }

    fun disconnect(result: FlutterResultWrapper, delay: Int) {
        if (delay <= 0) {
            removeConnectionData()
            result.success(true)
            return
        }
        connectionData?.isActive = false
        Observable.timer(delay.toLong(), TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    if (connectionData?.isActive == false) removeConnectionData()
                }
        result.success(true)
    }

    private fun getConnectionOrNull(result: FlutterResultWrapper): RxBleConnection? {
        val connection = connectionData?.getConnection()
        if (connection == null) {
            result.error(BTError.ErrorWithMessage.ordinal.toString(), "connection is null", null)
        }
        return connection
    }

    fun writeRawData(data: ByteArray, result: FlutterResultWrapper, characteristicUuid: String?) {
        val connection = getConnectionOrNull(result) ?: return
        val characteristicUUID = if (characteristicUuid.isNullOrEmpty()) {
            getWritableCharacteristic(connection).toObservable().take(1)
        } else {
            connection.getCharacteristic(UUID.fromString(characteristicUuid)).toObservable()
        }
        val longWrite = data.size > connection.maxWriteSize
        characteristicUUID
                .switchMap { if (longWrite) connection.performLongWrite(data, it) else connection.performWrite(data, it) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { result.success(true) },
                        { throwable ->
                            val error = throwable.toFlutterPlatformError
                            result.error(error.code, error.message, error.details)
                        })
                .let { connectionData?.addDisposable(it) }
    }

    private fun RxBleConnection.performWrite(data: ByteArray, characteristic: BluetoothGattCharacteristic) = writeCharacteristic(characteristic, data).toObservable()

    private fun RxBleConnection.performLongWrite(data: ByteArray, characteristic: BluetoothGattCharacteristic) = createNewLongWriteBuilder().setBytes(data).setCharacteristic(characteristic).build()

    private fun getWritableCharacteristic(connection: RxBleConnection): Single<BluetoothGattCharacteristic> {
        return connection.discoverServices().flatMap { services ->
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
        state ?: return
        if (lastConnectState == state) return
        lastConnectState = state
        updateConnectionState(state)
    }

}

private fun Disposable?.tryDispose() = this?.takeIf { !it.isDisposed }?.dispose()

private val RxBleConnection.maxWriteSize: Int get() = mtu - 3

private val BluetoothGattCharacteristic.isNotifiable: Boolean
    get() = properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0

private val BluetoothGattCharacteristic.isReadable: Boolean
    get() = properties and BluetoothGattCharacteristic.PROPERTY_READ != 0

private val BluetoothGattCharacteristic.isWriteable: Boolean
    get() = properties == BluetoothGattCharacteristic.PROPERTY_WRITE || properties == BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE || properties == BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE


class BLEConnectionData(val macAddress: String) {
    private var connection: RxBleConnection? = null
    private val connectionDisposables = CompositeDisposable()
    var isActive = false
    var connectionPriority = BluetoothGatt.CONNECTION_PRIORITY_BALANCED

    fun setConnection(connection: RxBleConnection) {
        this.connection = connection
    }

    fun getConnection(): RxBleConnection? {
        return connection
    }

    fun addDisposable(disposable: Disposable) {
        connectionDisposables.add(disposable)
    }

    fun dispose() {
        connectionDisposables.clear()
    }
}