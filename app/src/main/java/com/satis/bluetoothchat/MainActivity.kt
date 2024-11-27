package com.satis.bluetoothchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.satis.bluetoothchat.ui.theme.BluetoothChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BluetoothChatTheme {
                val navController = rememberNavController()
                BTChatApp()
            }
        }
    }

    @Composable
    fun BTChatApp() {

        Scaffold(
            topBar = { MainAppBar() },
            content = { innerPadding ->
                MainScreenLayout(modifier = Modifier.padding(innerPadding))
            }
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainAppBar() {
        TopAppBar(
            title = { Text("Bluetooth chat") },

            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary
            )
        )
    }

    @Composable
    fun MainScreenLayout(modifier: Modifier = Modifier) {
        var devices by remember { mutableStateOf(listOf("device 1", "device 2")) }
        // Column layout to arrange text and button vertically
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = modifier.fillMaxSize()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Button to start scanning
                Button(onClick = {
                    print("******* START SCANNING ********")
                    devices = devices + "added device"
                }) {
                    Text(text = "Start scanning")
                }

                // Button to stop scanning
                Button(onClick = {
                    print("******* STOP SCANNING ********")
                    devices = devices - "added device"
                }) {
                    Text(text = "Stop scanning")
                }

            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // display list of scanned devices here
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(devices) { device ->
                        Text(
                            text = "${device}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                        Divider()
                    }
                }
            }
        }
    }
}