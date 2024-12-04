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
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.satis.bluetoothchat.logic.BtManager
import com.satis.bluetoothchat.ui.theme.BluetoothChatTheme

class MainActivity : ComponentActivity() {
    private lateinit var btManager: BtManager
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        btManager = BtManager(ctx = this, activity = this)
        setContent {
            BluetoothChatTheme {
                BTChatApp()
            }
        }

        //BtManager(ctx = this, activity = this).startBluetooth()
        //btManager = BtManager(ctx = this, activity = this)
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
        //var devices by remember { mutableStateOf(listOf("device 1", "device 2")) }
        //val btManager by remember { mutableStateOf(BtManager(ctx = this, activity = this)) }
        //btManager.startBluetooth()

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
                    //devices = devices + "added device"
                }) {
                    Text(text = "Start scanning")
                }

                // Button to stop scanning
                Button(onClick = {
                    btManager.stopBluetoothDiscovery()
                    //devices = devices - "added device"
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
                                // TODO: Consider calling
                                //    ActivityCompat#requestPermissions
                                // here to request the missing permissions, and then overriding
                                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                //                                          int[] grantResults)
                                // to handle the case where the user grants the permission. See the documentation
                                // for ActivityCompat#requestPermissions for more details.

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
                        Divider()
                    }
                }
            }
        }
    }
}