package com.satis.bluetoothchat.logic

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

class BluetoothClient(private val adapter: BluetoothAdapter, private val serverDevice: BluetoothDevice) {
    private val serverUUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")

    suspend fun connectToServer() = withContext(Dispatchers.IO) {
        try {
            val socket = serverDevice.createRfcommSocketToServiceRecord(serverUUID)
            adapter.cancelDiscovery() // Stop discovery to avoid interference
            socket.connect()

            Log.d("BluetoothClient", "Connected to server: ${serverDevice.name}")

            // Handle the connected socket
            handleConnection(socket)
        } catch (e: IOException) {
            Log.e("BluetoothClient", "Error connecting to server", e)
        }
    }

    private fun handleConnection(socket: BluetoothSocket) {
        val inputStream = socket.inputStream
        val outputStream = socket.outputStream

        try {
            // Send a message
            val message = "Hello from client!".toByteArray()
            outputStream.write(message)

            // Receive a response
            val buffer = ByteArray(1024)
            val bytes = inputStream.read(buffer)
            val response = String(buffer, 0, bytes)
            Log.d("BluetoothClient", "Message received: $response")
        } catch (e: IOException) {
            Log.e("BluetoothClient", "Error handling connection", e)
        } finally {
            socket.close()
        }
    }
}
