package com.satis.bluetoothchat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
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

        //BtManager(ctx = this, activity = this).startBluetooth()
        //btManager = BtManager(ctx = this, activity = this)
    }

    @Composable
    fun BTChatApp() {
        val navController = rememberNavController()
        Scaffold(
            topBar = { MainAppBar() },
            content = { innerPadding ->
                //MainScreenLayout(modifier = Modifier.padding(innerPadding))
                NavHost(
                    navController = navController,
                    startDestination = "home_screen",
                    modifier = Modifier.padding(innerPadding)
                ) {
                    composable(route = "chat_screen") {
                        chatScreen.DisplayChatScreen()
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
        //var devices by remember { mutableStateOf(listOf("device 1", "device 2")) }
        //val btManager by remember { mutableStateOf(BtManager(ctx = this, activity = this)) }
        //btManager.startBluetooth()

        val lifecycleOwner = LocalLifecycleOwner.current

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
                DropdownMenu()
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
                            //navCont.navigate("chat_screen")
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

    /*
    @Composable
    fun IntroScreenLayout(modifier: Modifier = Modifier, navCont: NavController) {

        // Column layout to arrange text and button vertically
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = modifier.fillMaxSize()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Button to start scanning
                Button(onClick = {
                    btManager.startGattServer()
                    navCont.navigate("home_screen") {
                        popUpTo("intro_screen") { inclusive = true }
                    }
                }) {
                    Text(text = "Start server")
                }

                // Button to stop scanning
                Button(onClick = {
                    navCont.navigate("home_screen") {
                        popUpTo("intro_screen") { inclusive = true }
                    }
                }) {
                    Text(text = "Start client")
                }

            }
        }
    } */

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun DropdownMenu() {
        var expanded by remember { mutableStateOf(false) }
        val context = LocalContext.current
        val menuOptions = listOf("Ping", "Hello", "Write")
        //var selectedOption by btManager.selectedOption

        Box(
            modifier = Modifier
                //.fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            // Button to open the dropdown menu
            TextButton(onClick = { expanded = !expanded }) {
                Text(text = btManager.selectedOption.value)
            }

            // Dropdown menu
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                menuOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            btManager.selectedOption.value = option
                            expanded = false
                            handleOptionSelection(option, context)
                        }
                    )
                }
            }
        }
    }

    // Handle dropdown menu selection
    fun handleOptionSelection(option: String, context: android.content.Context) {
        when (option) {
            "Ping" -> {
                Toast.makeText(context, "Ping Selected", Toast.LENGTH_SHORT).show()
                // Add Ping-related logic here
            }
            "Hello" -> {
                Toast.makeText(context, "Hello Selected", Toast.LENGTH_SHORT).show()
                // Add Hello-related logic here
            }
            "Write" -> {
                Toast.makeText(context, "Write Selected", Toast.LENGTH_SHORT).show()
                // Add Custom-related logic here
            }
        }
    }

    fun navigateToChat() {

    }
}