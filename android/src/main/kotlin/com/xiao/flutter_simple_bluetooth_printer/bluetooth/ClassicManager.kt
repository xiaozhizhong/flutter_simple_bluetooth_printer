package com.xiao.flutter_simple_bluetooth_printer.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * @author xiao
 * @date 2023/01
 */

class ClassicManager(context: Context) : IBluetoothManager() {

    companion object {
        val DEFAULT_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothManager: BluetoothManager? = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var connectionThread: ConnectionThread? = null

    private var mState: BTConnectState = BTConnectState.Disconnect

    private fun doUpdateConnectionState(state: BTConnectState) {
        Observable.just(state).observeOn(AndroidSchedulers.mainThread()).subscribe {
            updateConnectionState(it)
            mState = state
        }
    }

    private fun isConnected() = connectionThread != null && !connectionThread!!.requestedClosing

    private fun isConnected(macAddress: String) = isConnected() && connectionThread?.macAddress == macAddress

    fun ensureConnected(address: String, result: FlutterResultWrapper) {
        if (isConnected(address)) {
            // Already connected
            connectionThread?.isActive = true
            result.success(true)
            return
        }
        result.success(false)
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String?, result: FlutterResultWrapper) {
        if (address.isNullOrEmpty()) {
            result.error(BTError.ErrorWithMessage.ordinal.toString(), "address is null", null)
            return
        }

        if (isConnected()) {
            if (isConnected(address)) {
                // Already connected
                connectionThread?.isActive = true
                result.success(true)
                return
            }
            // Disconnect the previous connection
            disconnect()
        }

        val device = bluetoothAdapter!!.getRemoteDevice(address)
        if (device == null) {
            result.error(BTError.ErrorWithMessage.ordinal.toString(), "device not found by $address", null)
            return
        }
        doUpdateConnectionState(BTConnectState.Connecting)

        try {
            val socket = device.createRfcommSocketToServiceRecord(DEFAULT_UUID)
            if (socket == null) {
                doUpdateConnectionState(BTConnectState.Fail)
                result.error(BTError.ErrorWithMessage.ordinal.toString(), "socket connection not established", null)
                return
            }

            // Cancel discovery, even though we didn't start it
            bluetoothAdapter.cancelDiscovery()

            socket.connect()
            connectionThread = ConnectionThread(address, socket)
            connectionThread!!.start()

            doUpdateConnectionState(BTConnectState.Connected)
            result.success(true)
        } catch (e: Exception) {
            doUpdateConnectionState(BTConnectState.Fail)
            result.error(BTError.ErrorWithMessage.ordinal.toString(), e.toString(), null)
        }
    }

    fun disconnect(result: FlutterResultWrapper, delay: Int) {
        if (delay <= 0) {
            disconnect()
            result.success(true)
            return
        }
        connectionThread?.isActive = false
        Observable.timer(delay.toLong(), TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    if (connectionThread?.isActive == false) disconnect()
                }
        result.success(true)
    }

    private fun disconnect() {
        connectionThread?.cancel()
        connectionThread = null
    }

    fun writeRawData(out: ByteArray, result: FlutterResultWrapper) {
        if (!isConnected()) {
            result.error(BTError.ErrorWithMessage.ordinal.toString(), "Not connect to device yet", null)
            return
        }
        connectionThread?.write(out, result)
    }

    private fun onDisconnected() {
        doUpdateConnectionState(BTConnectState.Disconnect)
    }

    /// Thread to handle connection I/O
    inner class ConnectionThread constructor(val macAddress: String, socket: BluetoothSocket) : Thread() {
        private val socket: BluetoothSocket?
        private val input: InputStream?
        private val output: OutputStream?
        var requestedClosing = false
        var isActive = true

        init {
            this.socket = socket
            var tmpIn: InputStream? = null
            var tmpOut: OutputStream? = null
            try {
                tmpIn = socket.inputStream
                tmpOut = socket.outputStream
            } catch (e: IOException) {
                e.printStackTrace()
            }
            input = tmpIn
            output = tmpOut
        }

        /// Thread main code
        override fun run() {
            val buffer = ByteArray(1024)
            var bytes: Int
            while (!requestedClosing) {
                try {
                    bytes = input!!.read(buffer)
                } catch (e: IOException) {
                    // `input.read` throws when closed by remote device
                    break
                }
            }

            // Make sure output stream is closed
            if (output != null) {
                try {
                    output.close()
                } catch (_: Exception) {
                }
            }

            // Make sure input stream is closed
            if (input != null) {
                try {
                    input.close()
                } catch (_: Exception) {
                }
            }

            // Callback on disconnected
            onDisconnected()

            // Just prevent unnecessary `cancel`ing
            requestedClosing = true
        }

        /// Writes to output stream
        fun write(bytes: ByteArray?, result: FlutterResultWrapper) {
            if (output == null) {
                result.error(BTError.ErrorWithMessage.ordinal.toString(), "output stream is null", null)
                return
            }
            try {
                output.write(bytes)
                result.success(true)
            } catch (e: IOException) {
                e.printStackTrace()
                result.error(BTError.ErrorWithMessage.ordinal.toString(), e.toString(), null)
            }
        }

        /// Stops the thread, disconnects
        fun cancel() {
            if (requestedClosing) {
                return
            }
            requestedClosing = true

            // Flush output buffers befoce closing
            try {
                output?.flush()
            } catch (_: Exception) {
            }

            // Close the connection socket
            if (socket != null) {
                try {
                    // Might be useful (see https://stackoverflow.com/a/22769260/4880243)
                    sleep(111)
                    socket.close()
                } catch (_: Exception) {
                }
            }
        }
    }

}