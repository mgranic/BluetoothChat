package com.satis.bluetoothchat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.satis.bluetoothchat.logic.BtManager
import com.satis.bluetoothchat.screens.ChatScreen
import com.satis.bluetoothchat.ui.theme.BluetoothChatTheme

class MainActivity : ComponentActivity() {
    private lateinit var btManager: BtManager
    private var chatScreen: ChatScreen = ChatScreen()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        btManager = BtManager(ctx = this, activity = this)
        setContent {
            BluetoothChatTheme {
                BTChatApp()
            }
        }
    }

    @Composable
    fun BTChatApp() {
        val navController = rememberNavController()
        Scaffold(
            topBar = { MainAppBar() },
            content = { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = "home_screen",
                    modifier = Modifier.padding(innerPadding)
                ) {
                    composable(route = "chat_screen") {
                        chatScreen.DisplayChatScreen(btManager = btManager)
                    }
                    composable(route = "home_screen") {
                        MainScreenLayout(modifier = Modifier.padding(innerPadding), navCont = navController)
                    }
                }
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
    fun MainScreenLayout(modifier: Modifier = Modifier, navCont: NavController) {

        // Collect navigation events
        LaunchedEffect(btManager.navigationEvent) {
            btManager.navigationEvent.collect { route ->
                navCont.navigate(route) // Trigger navigation
            }
        }

        // Column layout to arrange text and button vertically
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = modifier.fillMaxSize()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Button to start scanning
                Button(onClick = {
                    btManager.startBluetoothScan()
                }) {
                    Text(text = "Start scanning")
                }

                // Button to stop scanning
                Button(onClick = {
                    btManager.stopBluetoothDiscovery()
                }) {
                    Text(text = "Stop scanning")
                }

            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // display list of scanned devices here
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(btManager.discoveredDevices) { device ->
                        Button(onClick = {
                            if (ActivityCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.BLUETOOTH_CONNECT
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                // missing permissions
                                return@Button
                            }
                            Log.d("*********SATIS*********", "Device clicked: ${device.name} --- ${device.address}")
                            btManager.connectToBtDevice(device = device)
                        }) {
                            Text(
                                text = "Device = ${device.name} --- ${device.address}",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}