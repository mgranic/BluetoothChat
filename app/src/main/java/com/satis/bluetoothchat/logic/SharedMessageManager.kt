package com.satis.bluetoothchat.logic

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.satis.bluetoothchat.model.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object SharedMessageManager {
    // Backing property for the messages list
    private val _messages = mutableStateListOf<Message>()
    val messages: SnapshotStateList<Message> = _messages

    //private val _outgoingMessages = mutableStateListOf<Message>()
    //val outgoingMessages: SnapshotStateList<Message> = _outgoingMessages
    val outgoingMessages = mutableStateListOf<Message>()

    lateinit var gatt: BluetoothGatt
    lateinit var deviceNameCharacteristic: BluetoothGattCharacteristic

    var isServerMode = false

    @Synchronized
    fun addMessage(message: Message) {
        // Update the list and emit the new state
        _messages.add(message)
    }

    @Synchronized
    fun getMessages(): List<Message> {
        return _messages
    }
}
