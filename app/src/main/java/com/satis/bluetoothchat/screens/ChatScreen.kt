package com.satis.bluetoothchat.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.satis.bluetoothchat.logic.BtManager
import com.satis.bluetoothchat.logic.SharedMessageManager
import com.satis.bluetoothchat.model.Message

//data class Message(val content: String, val isSentByMe: Boolean)

class ChatScreen {
    @Composable
    fun DisplayChatScreen(btManager: BtManager) {
        var messageText by remember { mutableStateOf("") }
        //val messages = remember { mutableStateListOf<Message>() }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            // Chat Area
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                reverseLayout = true // Newer messages at the bottom
            ) {
                items(SharedMessageManager.messages.size) { index ->
                    val message = SharedMessageManager.messages[SharedMessageManager.messages.lastIndex - index] // Show newer messages at the bottom
                    ChatBubble(message)
                }
            }

            // Input Box
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                BasicTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp)
                        .background(Color.LightGray, shape = RoundedCornerShape(16.dp))
                        .padding(8.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (messageText.isNotBlank()) {
                                SharedMessageManager.messages.add(Message(messageText, isSentByMe = true))
                                messageText = ""
                            }
                        }
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .background(Color.Blue, shape = RoundedCornerShape(16.dp))
                        .padding(12.dp)
                        .clickable {
                            if (messageText.isNotBlank()) {
                                SharedMessageManager.messages.add(Message(messageText, isSentByMe = true))
                                SharedMessageManager.outgoingMessages.add(Message(messageText, isSentByMe = true))

                                messageText = ""
                            }
                        }
                ) {
                    BasicText(
                        text = "Send",
                        modifier = Modifier.padding(8.dp),
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 16.sp
                        )
                    )
                }
            }
        }
    }

    @Composable
    fun ChatBubble(message: Message) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (message.isSentByMe) Arrangement.End else Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .background(
                        if (message.isSentByMe) Color.Green else Color.Gray,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(8.dp)
            ) {
                BasicText(
                    text = message.content,
                    modifier = Modifier.padding(8.dp),
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 16.sp
                    )
                )
            }
        }
    }
}


