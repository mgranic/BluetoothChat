package com.satis.bluetoothchat.logic

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

class BluetoothServer(private val adapter: BluetoothAdapter) {
    private val serverUUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
    private val serverName = "BluetoothChatApp"
    private var serverSocket: BluetoothServerSocket? = null

    suspend fun startServer() = withContext(Dispatchers.IO) {
        try {
            serverSocket = adapter.listenUsingRfcommWithServiceRecord(serverName, serverUUID)
            Log.d("BluetoothServer", "Waiting for connection...")
            val socket = serverSocket?.accept() // Block until a client connects
            Log.d("BluetoothServer", "Client connected: ${socket?.remoteDevice?.name}")

            // Handle the connected socket
            socket?.let { handleConnection(it) }
        } catch (e: IOException) {
            Log.e("BluetoothServer", "Error starting server", e)
        } finally {
            serverSocket?.close()
        }
    }

    private fun handleConnection(socket: BluetoothSocket) {
        val inputStream = socket.inputStream
        val outputStream = socket.outputStream

        try {
            val buffer = ByteArray(1024)
            var bytes: Int

            while (true) {
                bytes = inputStream.read(buffer)
                val receivedMessage = String(buffer, 0, bytes)
                Log.d("BluetoothServer", "Message received: $receivedMessage")

                // Send a response
                val response = "Hello from server!".toByteArray()
                outputStream.write(response)
            }
        } catch (e: IOException) {
            Log.e("BluetoothServer", "Error handling connection", e)
        } finally {
            socket.close()
        }
    }
}
