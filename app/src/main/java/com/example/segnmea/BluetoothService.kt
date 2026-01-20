package com.example.segnmea

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.util.UUID

class BluetoothService(private val onDataReceived: (String) -> Unit) {

    private val mAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var mConnectThread: ConnectThread? = null
    private var mConnectedThread: ConnectedThread? = null
    // Standard Serial Port Profile UUID
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val handler = Handler(Looper.getMainLooper())

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        Log.d("BluetoothService", "Connecting to ${device.name}")
        // Cancel any existing connections
        cancel()
        mConnectThread = ConnectThread(device)
        mConnectThread?.start()
    }

    fun isConnected(): Boolean {
        return mConnectedThread != null && mConnectedThread!!.isAlive
    }

    fun cancel() {
        mConnectThread?.cancel()
        mConnectThread = null
        mConnectedThread?.cancel()
        mConnectedThread = null
    }

    @SuppressLint("MissingPermission")
    private inner class ConnectThread(private val mmDevice: BluetoothDevice) : Thread() {
        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            mmDevice.createRfcommSocketToServiceRecord(SPP_UUID)
        }

        override fun run() {
            // Cancel discovery because it otherwise slows down the connection.
            mAdapter?.cancelDiscovery()

            mmSocket?.let { socket ->
                try {
                    socket.connect()
                    // The connection attempt succeeded. Perform work associated with
                    // the connection in a separate thread.
                    Log.d("BluetoothService", "Socket connected")
                    mConnectedThread = ConnectedThread(socket)
                    mConnectedThread?.start()
                } catch (e: IOException) {
                    Log.e("BluetoothService", "Socket connection failed", e)
                    try {
                        socket.close()
                    } catch (e2: IOException) {
                        Log.e("BluetoothService", "Could not close the client socket", e2)
                    }
                    return
                }
            }
        }

        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: IOException) {
                Log.e("BluetoothService", "Could not close the client socket", e)
            }
        }
    }

    private inner class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {
        private val mmInStream: InputStream = mmSocket.inputStream

        override fun run() {
            val buffer = ByteArray(1024)
            var bytes: Int
            val sb = StringBuilder()

            Log.d("BluetoothService", "Listening for data...")

            while (true) {
                try {
                    bytes = mmInStream.read(buffer)
                    val str = String(buffer, 0, bytes)
                    sb.append(str)

                    var endOfLineIndex = sb.indexOf("\n")
                    while (endOfLineIndex >= 0) {
                        val line = sb.substring(0, endOfLineIndex).trim()
                        if (line.isNotEmpty()) {
                             handler.post {
                                onDataReceived(line)
                             }
                        }
                        sb.delete(0, endOfLineIndex + 1)
                        endOfLineIndex = sb.indexOf("\n")
                    }
                } catch (e: IOException) {
                    Log.e("BluetoothService", "Input stream disconnected", e)
                    break
                }
            }
        }

        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
                Log.e("BluetoothService", "Could not close the connect socket", e)
            }
        }
    }
}
