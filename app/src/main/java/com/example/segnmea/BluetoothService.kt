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

class BluetoothService(private val handler: Handler) {

    interface BluetoothListener {
        fun onDataReceived(data: String)
        fun onStatusChange(status: String)
        fun onConnected(deviceName: String)
    }

    private var listener: BluetoothListener? = null
    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null
    private var state = STATE_NONE

    companion object {
        const val STATE_NONE = 0
        const val STATE_CONNECTING = 1
        const val STATE_CONNECTED = 2
        private val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // SPP UUID
    }

    fun setListener(listener: BluetoothListener) {
        this.listener = listener
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        if (state == STATE_CONNECTING) {
            connectThread?.cancel()
            connectThread = null
        }
        if (state == STATE_CONNECTED) {
            connectedThread?.cancel()
            connectedThread = null
        }

        connectThread = ConnectThread(device)
        connectThread?.start()
        state = STATE_CONNECTING
        listener?.onStatusChange("Connecting to ${device.name}...")
    }

    @SuppressLint("MissingPermission")
    fun connected(socket: BluetoothSocket, device: BluetoothDevice) {
        if (connectThread != null) {
            connectThread?.cancel()
            connectThread = null
        }
        if (connectedThread != null) {
            connectedThread?.cancel()
            connectedThread = null
        }

        connectedThread = ConnectedThread(socket)
        connectedThread?.start()

        state = STATE_CONNECTED
        listener?.onConnected(device.name ?: "Device")
        listener?.onStatusChange("Connected to ${device.name}")
    }

    fun stop() {
        connectThread?.cancel()
        connectThread = null
        connectedThread?.cancel()
        connectedThread = null
        state = STATE_NONE
        listener?.onStatusChange("Disconnected")
    }

    private inner class ConnectThread(private val device: BluetoothDevice) : Thread() {
        private val socket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            try {
                device.createRfcommSocketToServiceRecord(MY_UUID)
            } catch (e: IOException) {
                null
            }
        }

        @SuppressLint("MissingPermission")
        override fun run() {
            try {
                socket?.connect()
            } catch (connectException: IOException) {
                try {
                    socket?.close()
                } catch (closeException: IOException) { }
                handler.post { listener?.onStatusChange("Connection failed") }
                return
            }
            socket?.let { connected(it, device) }
        }

        fun cancel() {
            try {
                socket?.close()
            } catch (e: IOException) { }
        }
    }

    private inner class ConnectedThread(private val socket: BluetoothSocket) : Thread() {
        private val inputStream: InputStream = socket.inputStream
        private val buffer = ByteArray(1024)

        override fun run() {
            while (true) {
                try {
                    val bytes = inputStream.read(buffer)
                    val message = String(buffer, 0, bytes)
                    handler.post { listener?.onDataReceived(message) }
                } catch (e: IOException) {
                    handler.post { listener?.onStatusChange("Connection lost") }
                    state = STATE_NONE
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
