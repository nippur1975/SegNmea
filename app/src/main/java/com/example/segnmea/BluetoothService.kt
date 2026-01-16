package com.example.segnmea

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
import java.io.IOException
import java.io.InputStream
import java.util.UUID

class BluetoothService(private val listener: BluetoothListener) {

    interface BluetoothListener {
        fun onMessageReceived(message: String)
        fun onStatusChange(status: String)
        fun onConnected(deviceName: String)
        fun onConnectionFailed()
    }

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null
    private val handler = Handler(Looper.getMainLooper())

    // Standard Serial Port Service ID
    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): Set<BluetoothDevice>? {
        return try {
            bluetoothAdapter?.bondedDevices
        } catch (e: Exception) {
            listener.onStatusChange("Error getting devices: ${e.message}")
            null
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        if (bluetoothAdapter == null) {
            listener.onStatusChange("Bluetooth not supported")
            return
        }

        connectThread?.cancel()
        connectedThread?.cancel()

        connectThread = ConnectThread(device)
        connectThread?.start()
        listener.onStatusChange("Connecting to ${device.name}...")
    }

    fun stop() {
        connectThread?.cancel()
        connectedThread?.cancel()
        connectThread = null
        connectedThread = null
    }

    private inner class ConnectThread(private val device: BluetoothDevice) : Thread() {
        private val socket: BluetoothSocket? by lazy {
            try {
                // Insecure to avoid some pairing issues, or standard secure
                device.createRfcommSocketToServiceRecord(MY_UUID)
            } catch (e: Exception) {
                null
            }
        }

        @SuppressLint("MissingPermission")
        override fun run() {
            try {
                bluetoothAdapter?.cancelDiscovery()
            } catch (e: Exception) {
                // Ignore
            }

            try {
                socket?.connect()
            } catch (e: IOException) {
                try {
                    socket?.close()
                } catch (e2: IOException) {
                    // Ignore
                }
                handler.post { listener.onConnectionFailed() }
                return
            }

            socket?.let {
                connectedThread = ConnectedThread(it)
                connectedThread?.start()
                handler.post { listener.onConnected(device.name ?: "Device") }
            }
        }

        fun cancel() {
            try {
                socket?.close()
            } catch (e: IOException) {
                // Ignore
            }
        }
    }

    private inner class ConnectedThread(private val socket: BluetoothSocket) : Thread() {
        private val inputStream: InputStream? = try { socket.inputStream } catch (e: IOException) { null }
        private val buffer = ByteArray(1024)

        override fun run() {
            if (inputStream == null) return
            val stringBuilder = StringBuilder()

            while (true) {
                try {
                    val bytes = inputStream.read(buffer)
                    if (bytes > 0) {
                        val chunk = String(buffer, 0, bytes)
                        stringBuilder.append(chunk)

                        // Process lines
                        var index = stringBuilder.indexOf('\n')
                        while (index >= 0) {
                            val line = stringBuilder.substring(0, index).trim()
                            if (line.isNotEmpty()) {
                                handler.post { listener.onMessageReceived(line) }
                            }
                            stringBuilder.delete(0, index + 1)
                            index = stringBuilder.indexOf('\n')
                        }
                    }
                } catch (e: IOException) {
                    handler.post { listener.onStatusChange("Disconnected") }
                    break
                }
            }
        }

        fun cancel() {
            try {
                socket.close()
            } catch (e: IOException) {
                // Ignore
            }
        }
    }
}
