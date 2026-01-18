package com.example.segnmea

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
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

class BluetoothService(private val context: Context, private val onDataReceived: (NmeaData) -> Unit) {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null
    private val handler = Handler(Looper.getMainLooper())
    private val nmeaParser = NmeaParser()

    // SPP UUID
    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun getPairedDevices(): Set<BluetoothDevice>? {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            return null
        }
        return bluetoothAdapter?.bondedDevices
    }

    fun connect(device: BluetoothDevice) {
        disconnect()
        connectThread = ConnectThread(device)
        connectThread?.start()
    }

    fun disconnect() {
        connectThread?.cancel()
        connectedThread?.cancel()
        connectThread = null
        connectedThread = null
    }

    @SuppressLint("MissingPermission")
    private inner class ConnectThread(device: BluetoothDevice) : Thread() {
        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            try {
                device.createRfcommSocketToServiceRecord(MY_UUID)
            } catch (e: IOException) {
                Log.e("BluetoothService", "Socket's create() method failed", e)
                null
            }
        }

        override fun run() {
            // Cancel discovery because it otherwise slows down the connection.
            bluetoothAdapter?.cancelDiscovery()

            mmSocket?.let { socket ->
                try {
                    socket.connect()
                    // The connection attempt succeeded. Perform work associated with
                    // the connection in a separate thread.
                    manageMyConnectedSocket(socket)
                } catch (e: IOException) {
                    Log.e("BluetoothService", "Unable to connect", e)
                    try {
                        socket.close()
                    } catch (closeException: IOException) {
                        Log.e("BluetoothService", "Could not close the client socket", closeException)
                    }
                    return
                }
            }
        }
    }

    private fun manageMyConnectedSocket(socket: BluetoothSocket) {
        connectedThread = ConnectedThread(socket)
        connectedThread?.start()
    }

    private inner class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {
        private val mmInStream: InputStream = mmSocket.inputStream
        private val mmBuffer: ByteArray = ByteArray(1024) // mmBuffer store for the stream

        override fun run() {
            var numBytes: Int // bytes returned from read()
            val stringBuilder = StringBuilder()

            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                try {
                    // Read from the InputStream.
                    numBytes = mmInStream.read(mmBuffer)
                    val readMessage = String(mmBuffer, 0, numBytes)
                    stringBuilder.append(readMessage)

                    // Check for complete lines
                    var newlineIndex = stringBuilder.indexOf('\n')
                    while (newlineIndex != -1) {
                        val line = stringBuilder.substring(0, newlineIndex).trim()
                        stringBuilder.delete(0, newlineIndex + 1)
                        processNmeaLine(line)
                        newlineIndex = stringBuilder.indexOf('\n')
                    }

                } catch (e: IOException) {
                    Log.d("BluetoothService", "Input stream was disconnected", e)
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

    private fun processNmeaLine(line: String) {
        if (line.startsWith("$")) {
            if (line.contains("GPRMC") || line.contains("GNRMC")) {
                val data = nmeaParser.parseGPRMC(line)
                if (data != null && data.isValid) {
                    handler.post {
                        onDataReceived(data)
                    }
                }
            } else if (line.contains("IIXDR") || line.contains("XXDR")) {
                val updated = nmeaParser.parseIIXDR(line)
                // If updated, we might want to trigger an update, but we usually wait for GPRMC for position.
                // However, if we just want to update display, we can callback too if needed.
                // But `onDataReceived` expects `NmeaData`.
                // Let's create a partial NmeaData with current pitch/roll if needed, or just let the next GPRMC pick it up.
                // NmeaParser stores pitch/roll state, so next GPRMC will have it.
            }
        }
    }
}
