package com.xiao.flutter_simple_bluetooth_printer.bluetooth

import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import com.jakewharton.rx3.ReplayingShare
import com.polidea.rxandroidble3.RxBleClient
import com.polidea.rxandroidble3.RxBleConnection
import com.polidea.rxandroidble3.RxBleDevice
import com.polidea.rxandroidble3.Timeout
import com.polidea.rxandroidble3.scan.IsConnectable
import com.polidea.rxandroidble3.scan.ScanResult
import com.polidea.rxandroidble3.scan.ScanSettings
import com.polidea.rxandroidble3.ConnectionSetup
import com.polidea.rxandroidble3.scan.ScanFilter
import io.flutter.plugin.common.MethodChannel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.subjects.PublishSubject
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

    private val disconnectTriggerSubject = PublishSubject.create<Unit>()
    private var connectionObservable: Observable<RxBleConnection>? = null
    private val connectionDisposables = CompositeDisposable()
    private var stateDisposable: Disposable? = null

    private val nearbyDevices: MutableList<BluetoothDevice> = mutableListOf()
    private var methodChannel: MethodChannel? = null

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

        val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build()
        scanDisposable = rxBleClient.scanBleDevices(scanSettings)
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally { scanDisposable = null }
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

    fun connect(macAddress: String?, timeout: Int, autoConnect: Boolean, result: FlutterResultWrapper) {
        if (macAddress == null) {
            result.error(BTError.ErrorWithMessage.ordinal.toString(), "address is null", null)
            return
        }
        val device = rxBleClient.getBleDevice(macAddress)
        if (device == null) {
            result.error(BTError.ErrorWithMessage.ordinal.toString(), "can't not found device by $macAddress", null)
            return
        }
        if (device.isConnected) {
            result.success(true)// Already connected
            return
        }
        observeConnectionState(device)

        val operationTimeout = if (timeout > 0) Timeout(timeout.toLong(), TimeUnit.MILLISECONDS) else Timeout(ConnectionSetup.DEFAULT_OPERATION_TIMEOUT.toLong(), TimeUnit.SECONDS)
        prepareConnectionObservable(device, autoConnect, operationTimeout)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnDispose { connectionObservable = null }
                .subscribe({ result.success(true) }, { throwable ->
                    val error = throwable.toFlutterPlatformError
                    result.error(error.code, error.message, error.details)
                })
                .let { connectionDisposables.add(it) }
    }

    private fun rescanAndConnect(macAddress: String) {
        val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
                .build()
        val filter = ScanFilter.Builder()
                .setDeviceAddress(macAddress)
                .build()
        rxBleClient.scanBleDevices(scanSettings, filter)
                .take(5000, TimeUnit.MILLISECONDS)
                .doFinally {  }

    }

    private fun prepareConnectionObservable(device: RxBleDevice, autoConnect: Boolean, timeout: Timeout): Observable<RxBleConnection> {
        connectionObservable = device
                .establishConnection(autoConnect, timeout)
                .takeUntil(disconnectTriggerSubject)
                .compose(ReplayingShare.instance())
        return connectionObservable!!
    }

    private fun observeConnectionState(device: RxBleDevice) {
        stateDisposable = device.observeConnectionStateChanges()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { connectionState ->
                    doUpdateConnectionState(connectionState.toState)
                    if (connectionState == RxBleConnection.RxBleConnectionState.DISCONNECTED) {
                        stateDisposable.tryDispose()
                    }
                }
    }

    fun disconnect(result: FlutterResultWrapper) {
        triggerDisconnect()
        connectionDisposables.clear()
        result.success(true)
    }

    private fun triggerDisconnect() = disconnectTriggerSubject.onNext(Unit)

    fun writeRawData(data: ByteArray, result: FlutterResultWrapper, characteristicUuid: String?) {
        val connection = try {
            connectionObservable?.blockingFirst()
        } catch (e: NoSuchElementException) {
            null
        }
        if (connection == null) {
            result.error(BTError.ErrorWithMessage.ordinal.toString(), "connection is null", null)
            return
        }
        val characteristicUUID = if (characteristicUuid.isNullOrEmpty()) {
            getWritableCharacteristic(connection).toObservable().take(1).map { it.uuid }
        } else {
            Observable.just(UUID.fromString(characteristicUuid))
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
                .let { connectionDisposables.add(it) }
    }

    private fun RxBleConnection.performWrite(data: ByteArray, characteristicUuid: UUID) = writeCharacteristic(characteristicUuid, data).toObservable()

    private fun RxBleConnection.performLongWrite(data: ByteArray, characteristicUuid: UUID) = createNewLongWriteBuilder().setBytes(data).setCharacteristicUuid(characteristicUuid).build()

    private fun getWritableCharacteristic(connection: RxBleConnection): Single<BluetoothGattCharacteristic> {
        return connection.discoverServices().flatMap { services ->
            var characteristic = services.bluetoothGattServices.flatMap { it.characteristics.filter { char -> char.isWriteable } }.minByOrNull { it.uuid }
            if (characteristic == null) {
                characteristic = services.bluetoothGattServices.flatMap { it.characteristics }.firstNotNullOfOrNull {
                    if (it.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG)) != null) it else null
                }
            }
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
        updateConnectionState(state)
    }

}

private fun Disposable?.tryDispose() = this?.takeIf { !it.isDisposed }?.dispose()

private val RxBleDevice.isConnected: Boolean get() = connectionState == RxBleConnection.RxBleConnectionState.CONNECTED

private val RxBleConnection.maxWriteSize: Int get() = mtu - 3

private val BluetoothGattCharacteristic.isNotifiable: Boolean
    get() = properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0

private val BluetoothGattCharacteristic.isReadable: Boolean
    get() = properties and BluetoothGattCharacteristic.PROPERTY_READ != 0

private val BluetoothGattCharacteristic.isWriteable: Boolean
    get() = properties == BluetoothGattCharacteristic.PROPERTY_WRITE || properties == BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE || properties == BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE