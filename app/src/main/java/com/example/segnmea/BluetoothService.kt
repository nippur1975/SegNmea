package com.example.segnmea

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.io.InputStream
import java.util.UUID

class BluetoothService(private val context: Context, private val onDataReceived: (String) -> Unit) {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null
    private val handler = Handler(Looper.getMainLooper())

    // SPP UUID
    private val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun getPairedDevices(): Set<BluetoothDevice>? {
        if (!hasPermission()) return null
        return bluetoothAdapter?.bondedDevices
    }

    fun connect(device: BluetoothDevice) {
        if (!hasPermission()) return

        connectThread?.cancel()
        connectedThread?.cancel()

        connectThread = ConnectThread(device)
        connectThread?.start()
    }

    fun disconnect() {
        connectThread?.cancel()
        connectedThread?.cancel()
    }

    fun isConnected(): Boolean {
        return connectedThread != null
    }

    private fun hasPermission(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        } else {
             if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    @SuppressLint("MissingPermission")
    private inner class ConnectThread(device: BluetoothDevice) : Thread() {
        private val socket: BluetoothSocket? by lazy {
            try {
                device.createRfcommSocketToServiceRecord(MY_UUID)
            } catch (e: IOException) {
                Log.e("BluetoothService", "Socket create failed", e)
                null
            }
        }

        override fun run() {
            bluetoothAdapter?.cancelDiscovery()

            socket?.let { sock ->
                try {
                    sock.connect()
                    // Start the connected thread
                    connectedThread = ConnectedThread(sock)
                    connectedThread?.start()
                } catch (e: IOException) {
                    Log.e("BluetoothService", "Socket connect failed", e)
                    try {
                        sock.close()
                    } catch (closeException: IOException) {
                        Log.e("BluetoothService", "Could not close the client socket", closeException)
                    }
                    return
                }
            }
        }

        fun cancel() {
            try {
                socket?.close()
            } catch (e: IOException) {
                Log.e("BluetoothService", "Could not close the client socket", e)
            }
        }
    }

    private inner class ConnectedThread(private val socket: BluetoothSocket) : Thread() {
        private val inputStream: InputStream = socket.inputStream
        private val buffer = ByteArray(1024)

        override fun run() {
            var bytes: Int
            val sb = StringBuilder()

            while (true) {
                try {
                    bytes = inputStream.read(buffer)
                    val readMessage = String(buffer, 0, bytes)
                    sb.append(readMessage)

                    val endOfLineIndex = sb.indexOf("\n")
                    if (endOfLineIndex > 0) {
                        val fullMessage = sb.substring(0, endOfLineIndex).trim()
                        sb.delete(0, endOfLineIndex + 1)

                        handler.post {
                            onDataReceived(fullMessage)
                        }
                    }
                } catch (e: IOException) {
                    Log.d("BluetoothService", "Input stream was disconnected", e)
                    break
                }
            }
        }

        fun cancel() {
            try {
                socket.close()
            } catch (e: IOException) {
                Log.e("BluetoothService", "Could not close the connect socket", e)
            }
        }
    }
}
