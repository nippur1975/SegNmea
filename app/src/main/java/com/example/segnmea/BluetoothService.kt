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

class BluetoothService(private val handler: Handler, private val onDataReceived: (String) -> Unit) {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null
    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // Standard SPP UUID

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): Set<BluetoothDevice>? {
        return bluetoothAdapter?.bondedDevices
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        connectThread?.cancel()
        connectedThread?.cancel()

        connectThread = ConnectThread(device)
        connectThread?.start()
    }

    fun stop() {
        connectThread?.cancel()
        connectedThread?.cancel()
    }

    @SuppressLint("MissingPermission")
    private inner class ConnectThread(private val device: BluetoothDevice) : Thread() {
        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            device.createRfcommSocketToServiceRecord(MY_UUID)
        }

        override fun run() {
            bluetoothAdapter?.cancelDiscovery()

            try {
                mmSocket?.connect()
            } catch (e: IOException) {
                try {
                    mmSocket?.close()
                } catch (e2: IOException) {
                    e2.printStackTrace()
                }
                return
            }

            mmSocket?.also { socket ->
                connectedThread = ConnectedThread(socket)
                connectedThread?.start()
                // Notify UI of connection success via Handler if needed
                handler.post {
                    // Optional: callback for connected state
                }
            }
        }

        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private inner class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {
        private val mmInStream: InputStream = mmSocket.inputStream
        private val mmBuffer: ByteArray = ByteArray(1024)

        override fun run() {
            var numBytes: Int

            while (true) {
                try {
                    numBytes = mmInStream.read(mmBuffer)
                    val readMessage = String(mmBuffer, 0, numBytes)
                    handler.post {
                        onDataReceived(readMessage)
                    }
                } catch (e: IOException) {
                    break
                }
            }
        }

        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}
