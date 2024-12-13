package com.satis.bluetoothchat.logic

import com.satis.bluetoothchat.model.Message

class SharedMessageManager {
    private var messages = mutableListOf<Message>()

    @Synchronized
    fun addMessage(message: Message) {
        messages.add(message)
    }

    @Synchronized
    fun getMessages(): List<Message> {
        return messages.toList()
    }
}