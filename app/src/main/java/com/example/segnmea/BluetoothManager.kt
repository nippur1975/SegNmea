package com.example.segnmea

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.UUID

interface BluetoothListener {
    fun onStatusChange(status: String)
    fun onDataReceived(line: String)
    fun onError(error: String)
}

class BluetoothManager(private val context: Context, private val listener: BluetoothListener) {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null
    private val handler = Handler(Looper.getMainLooper())
    // Standard UUID for SPP (Serial Port Profile)
    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): Set<BluetoothDevice>? {
        if (bluetoothAdapter == null) {
            listener.onError("Bluetooth not supported")
            return null
        }
        if (!bluetoothAdapter.isEnabled) {
            listener.onError("Bluetooth not enabled")
            return null
        }
        return try {
            bluetoothAdapter.bondedDevices
        } catch (e: Exception) {
            listener.onError("Error getting devices: ${e.message}")
            null
        }
    }

    fun connect(deviceAddress: String) {
        disconnect()

        try {
            val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
            if (device != null) {
                listener.onStatusChange("Connecting...")
                connectThread = ConnectThread(device)
                connectThread?.start()
            }
        } catch (e: Exception) {
            listener.onError("Connection init failed: ${e.message}")
        }
    }

    fun disconnect() {
        connectThread?.cancel()
        connectedThread?.cancel()
        connectThread = null
        connectedThread = null
        listener.onStatusChange("Disconnected")
    }

    @SuppressLint("MissingPermission")
    private inner class ConnectThread(private val device: BluetoothDevice) : Thread() {
        private val socket: BluetoothSocket? by lazy {
            try {
                device.createRfcommSocketToServiceRecord(MY_UUID)
            } catch (e: IOException) {
                null
            }
        }

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
                } catch (e2: IOException) { }
                handler.post { listener.onError("Connection Failed") }
                return
            }

            socket?.let {
                // Connection successful
                connectedThread = ConnectedThread(it)
                connectedThread?.start()
                handler.post { listener.onStatusChange("Connected") }
            }
        }

        fun cancel() {
            try {
                socket?.close()
            } catch (e: IOException) { }
        }
    }

    private inner class ConnectedThread(private val socket: BluetoothSocket) : Thread() {
        private var reader: BufferedReader? = null

        init {
            try {
                reader = BufferedReader(InputStreamReader(socket.inputStream))
            } catch (e: IOException) {
                handler.post { listener.onError("Error getting stream") }
            }
        }

        override fun run() {
            val r = reader ?: return
            while (true) {
                try {
                    val line = r.readLine() ?: break
                    // Ensure non-empty lines
                    if (line.isNotEmpty()) {
                        handler.post { listener.onDataReceived(line) }
                    }
                } catch (e: IOException) {
                    handler.post { listener.onError("Connection Lost") }
                    break
                }
            }
        }

        fun cancel() {
            try {
                socket.close()
            } catch (e: IOException) { }
        }
    }
}
