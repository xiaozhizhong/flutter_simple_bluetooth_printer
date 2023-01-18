package com.xiao.flutter_simple_bluetooth_printer.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import io.flutter.plugin.common.MethodChannel.Result
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import java.util.*

/**
 * @author xiao
 * @date 2023/01
 */

class ClassicManager(context: Context) : IBluetoothManager() {

    private val bluetoothManager: BluetoothManager? = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var mConnectThread: ConnectThread? = null
    private var mConnectedThread: ConnectedThread? = null
    private var mState: BTConnectState = BTConnectState.Disconnect

    private fun doUpdateConnectionState(state: BTConnectState) {
        Observable.just(state).observeOn(AndroidSchedulers.mainThread()).subscribe {
            updateConnectionState(it)
            mState = state
        }
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     *
     * @param address The BluetoothDevice address to connect
     */
    @Synchronized
    fun connect(address: String?, result: FlutterResultWrapper) {
        if (address.isNullOrEmpty()) {
            result.error(BTError.ErrorWithMessage.ordinal.toString(), "address is null", null)
            return
        }

        val device = bluetoothAdapter!!.getRemoteDevice(address)

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }

        doUpdateConnectionState(BTConnectState.Connecting)
        // Start the thread to connect with the given device
        mConnectThread = ConnectThread(device, result)
        mConnectThread!!.start()
    }

    /**
     * Stop all threads
     */
    @Synchronized
    fun disconnect(result: FlutterResultWrapper) {
        if (mConnectThread != null) {
            mConnectThread!!.cancel()
            mConnectThread = null
        }
        doUpdateConnectionState(BTConnectState.Disconnect)
        result.success(true)
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     *
     * @param out The bytes to write
     * @see ConnectedThread.write
     */
    fun writeRawData(out: ByteArray, result: FlutterResultWrapper) {
        // Create temporary object
        var r: ConnectedThread? = null
        // Synchronize a copy of the ConnectedThread
        synchronized(this@ClassicManager) {
            if (mState == BTConnectState.Connected) r = mConnectedThread
        }
        if (r == null) {
            result.error(BTError.ErrorWithMessage.ordinal.toString(), "Not connect to device yet", null)
        } else {
            // Perform the write unsynchronized
            r!!.write(out, result)
        }
    }

    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    @SuppressLint("MissingPermission")
    private inner class ConnectThread(
        private val mmDevice: BluetoothDevice,
        private val mmResult: FlutterResultWrapper,
    ) : Thread() {
        private val mmSocket: BluetoothSocket?
        override fun run() {
            name = "ConnectThread"
            if (mmSocket == null) {
                onConnectionFailed()
                return
            }

            // Make a connection to the BluetoothSocket
            try {
                mmSocket.connect()
            } catch (e: Exception) {
                // Close the socket
                try {
                    mmSocket.close()
                } catch (e2: IOException) {
                    Log.e("ClassicManager", "unable to close() socket during connection failure")
                }
                onConnectionFailed()
                return
            }

            // Reset the ConnectThread because we're done
            synchronized(this@ClassicManager) { mConnectThread = null }

            // Start the connected thread
            onConnected(mmSocket)
        }

        @Synchronized
        private fun onConnectionFailed() {
            mmResult.success(false)
            synchronized(this@ClassicManager) { mConnectThread = null }
            doUpdateConnectionState(BTConnectState.Fail)
        }


        /**
         * Start the ConnectedThread to begin managing a Bluetooth connection
         *
         * @param socket The BluetoothSocket on which the connection was made
         * @param device The BluetoothDevice that has been connected
         */
        @Synchronized
        private fun onConnected(socket: BluetoothSocket) {
            mmResult.success(true)
            doUpdateConnectionState(BTConnectState.Connected)

            // Start the thread to manage the connection and perform transmissions
            mConnectedThread = ConnectedThread(socket)
            mConnectedThread!!.start()
        }

        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: IOException) {
                Log.e("ClassicManager", "close() of connect socket failed")
            }
        }

        init {
            var tmp: BluetoothSocket? = null

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                tmp = mmDevice.createInsecureRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"))
            } catch (e: IOException) {
                Log.e("ClassicManager", "Socket: create() failed")
            }
            mmSocket = tmp
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private inner class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {

        private val mmInStream: InputStream? = mmSocket.inputStream
        private val mmOutStream: OutputStream? = mmSocket.outputStream
        private val mmBuffer: ByteArray = ByteArray(1024) // mmBuffer store for the stream

        override fun run() {
            // Keep listening to the InputStream while connected
            while (true) {
                // Read from the InputStream.
                try {
                    mmInStream?.read(mmBuffer)
                } catch (e: IOException) {
                    onConnectionLost()
                    break
                }
            }
        }

        /**
         * Indicate that the connection was lost and notify the UI Activity.
         */
        private fun onConnectionLost() {
            doUpdateConnectionState(BTConnectState.Disconnect)
        }

        /**
         * Write to the connected OutStream.
         * @param bytes The bytes to write
         */
        fun write(bytes: ByteArray?, result: FlutterResultWrapper) {
            try {
                mmOutStream?.write(bytes)
            } catch (e: IOException) {
                Log.e("ClassicManager", "Exception during write", e)
                result.success(false)
                return
            }
            result.success(true)
        }

        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
                Log.e("ClassicManager", "close() of connect socket failed")
            }
        }
    }

}