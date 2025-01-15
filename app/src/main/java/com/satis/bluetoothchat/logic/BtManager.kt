package com.satis.bluetoothchat.logic

//import androidx.activity.result.ActivityResultContracts
import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.satis.bluetoothchat.model.Message
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask
import java.util.UUID

class BtManager(val ctx: Context, val activity: ComponentActivity) : ViewModel() {
    private val _navigationEvent = MutableSharedFlow<String>() // Emit route names
    val navigationEvent = _navigationEvent.asSharedFlow()

    private val timer = Timer()


    // Observable list of discovered devices
    private val _discoveredDevices = mutableStateListOf<BluetoothDevice>()
    val discoveredDevices: SnapshotStateList<BluetoothDevice> = _discoveredDevices

    private val btManager: BluetoothManager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = btManager.adapter
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<Array<String>>

    private lateinit var bluetoothGattServer: BluetoothGattServer

    private lateinit var advertiser: BluetoothLeAdvertiser

    private var isAdvertising = false

    // Define the activity result launcher
    private val enableBluetoothResultLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Proceed with Bluetooth operations
        } else {
            Toast.makeText(ctx, "Bluetooth is required to proceed", Toast.LENGTH_SHORT).show()
        }
    }

    private val receiver = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (BluetoothDevice.ACTION_FOUND == action) {
                val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as? BluetoothDevice
                }

                device?.let {
                    if (!_discoveredDevices.contains(device)) {
                        _discoveredDevices.add(device)
                    }
                }
            }
        }
    }

    init {
        requestPermissions()
        startGattServer()
        //startBluetoothAdvertising()
    }

    fun startBluetoothScan() {
        _discoveredDevices.clear()
        startBluetoothDiscovery()
    }

    private fun requestPermissions() {
        // Initialize the permission launcher
        requestPermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            // Handle the result of the permission request
            val granted = permissions.all { it.value }
            if (granted) {
                enableBluetooth()
            } else {
                Toast.makeText(ctx, "Bluetooth permissions are required", Toast.LENGTH_SHORT).show()
            }
        }

        // Check if Bluetooth is supported
        if (bluetoothAdapter == null) {
            Toast.makeText(ctx, "Bluetooth not supported on this device", Toast.LENGTH_SHORT).show()
            return
        }

        // Collect required permissions for Android 12+ (API 31+)
        val requiredPermissions = mutableListOf<String>()

        if (activity.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (activity.checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }

        if (requiredPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(requiredPermissions.toTypedArray())
            return
        }
        enableBluetooth()
    }



    private fun enableBluetooth() {

        if (bluetoothAdapter == null) {
            Toast.makeText(ctx, "Bluetooth not supported on this device", Toast.LENGTH_SHORT).show()
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothResultLauncher.launch(enableBtIntent) // Using the launcher to handle the result
        }
    }

    private fun startBluetoothDiscovery() {
        if (bluetoothAdapter == null) {
            Toast.makeText(ctx, "Bluetooth is not supported", Toast.LENGTH_SHORT).show()
            return
        }

        if (bluetoothAdapter.isDiscovering) {
            if (ActivityCompat.checkSelfPermission(
                    ctx,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // missing permissions
                return
            }
            bluetoothAdapter.cancelDiscovery() // Stop previous discovery if running
        }

        // Register the BroadcastReceiver for device discovery
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        activity.registerReceiver(receiver, filter)

        bluetoothAdapter.startDiscovery()
    }

    fun stopBluetoothDiscovery() {
        if (bluetoothAdapter?.isDiscovering == true) {
            if (ActivityCompat.checkSelfPermission(
                    ctx,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // missing permissions
                return
            }
            bluetoothAdapter.cancelDiscovery() // Stop previous discovery if running
        }
        // Unregister the BroadcastReceiver
        activity.unregisterReceiver(receiver)
    }

    fun connectToBtDevice(device: BluetoothDevice) {
        val type = if (ActivityCompat.checkSelfPermission(
                ctx,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // missing permissions
            return
        } else {
            device.type
        }
        // check device type
        when (type) {
            BluetoothDevice.DEVICE_TYPE_LE -> connectToLEDevice(device)
            BluetoothDevice.DEVICE_TYPE_DUAL -> connectToLEDevice(device)
            else -> Toast.makeText(ctx, "Unknown device type", Toast.LENGTH_SHORT).show()
        }
    }

    private fun connectToLEDevice(device: BluetoothDevice) {
        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.d("****SATIS****", "Bluetooth CONNECT permission is missing.")
            return
        }

        device.connectGatt(ctx, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    Log.d("****SATIS****", "Connected to GATT server on ${device.name}")
                    // Discover services
                    if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        return
                    }
                    gatt.discoverServices()
                    Log.d("****SATIS****", "Discovering GATT services on ${device.name}")
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    Log.d("****SATIS****", "Disconnected from GATT server on ${device.name}, attempting to reconnect ...")
                    connectToBtDevice(device)
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                super.onServicesDiscovered(gatt, status)
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("****SATIS****", "Services discovered: ${gatt.services}")

                    // Find the GAP service
                    val gapService = gatt.getService(UUID.fromString("12345678-1234-5678-1234-567812345678"))
                    if (gapService != null) {
                        val deviceNameCharacteristic = gapService.getCharacteristic(UUID.fromString("a7e550c4-69d1-4a6b-9fe7-8e21e5d571b6"))
                        if (deviceNameCharacteristic != null) {
                            readGattService(gatt, deviceNameCharacteristic)
                            SharedMessageManager.gatt = gatt
                            SharedMessageManager.deviceNameCharacteristic = deviceNameCharacteristic
                            startCommunicationTimer()
                            Log.d("****SATIS****", "Reading Device Name characteristic")
                            navigateTo("chat_screen")
                        } else {
                            Log.w("****SATIS****", "Device Name characteristic not found in GAP service")
                        }
                    } else {
                        Log.w("****SATIS****", "GAP service not found")
                    }
                } else {
                    Log.w("****SATIS****", "Failed to discover services: $status")
                }
            }

            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                super.onCharacteristicRead(gatt, characteristic, status)
                Log.d("****SATIS****", "Read response: EXECUTED *********")
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    if(characteristic.uuid == UUID.fromString("a7e550c4-69d1-4a6b-9fe7-8e21e5d571b6")) {
                        val readResponse = characteristic.value.toString(Charsets.UTF_8)
                        Log.d("****SATIS****", "Read response: $readResponse")
                        if (readResponse.isNotEmpty()) {
                            SharedMessageManager.messages.add(Message(readResponse, isSentByMe = false))
                        }
                    } else {
                        Log.d("****SATIS****", "Read characteristic: ${characteristic.uuid}, value: ${characteristic.value}")
                    }
                } else {
                    Log.w("****SATIS****", "Failed to read characteristic: ${characteristic.uuid}, status: $status")
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                super.onCharacteristicChanged(gatt, characteristic)
                val receivedData = characteristic.value.toString(Charsets.UTF_8)
                Log.d("****SATIS****", "Data received: $receivedData")
            }

            override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                super.onCharacteristicWrite(gatt, characteristic, status)
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("****SATIS****", "Data successfully written to characteristic: ${characteristic.uuid}")
                } else {
                    Log.w("****SATIS****", "Failed to write to characteristic: ${characteristic.uuid}")
                }
            }
        })

        Log.d("****SATIS****", "Attempting to connect to ${device.name} (${device.address})")
    }

    fun startGattServer() {
        SharedMessageManager.isServerMode = true
        Log.d("*******SATIS*******", "GATT SEVER IS SERVER MODE ??? : ${SharedMessageManager.isServerMode}")

        // Define a custom UUID for the service and characteristic
        val serviceUuid = UUID.fromString("12345678-1234-5678-1234-567812345678")
        val characteristicUuid = UUID.fromString("a7e550c4-69d1-4a6b-9fe7-8e21e5d571b6")

        // Create the characteristic
        val characteristic = BluetoothGattCharacteristic(
            characteristicUuid,
            BluetoothGattCharacteristic.PROPERTY_READ or  // For reading operations
                    BluetoothGattCharacteristic.PROPERTY_WRITE or // For write with response
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, // For write without response
            BluetoothGattCharacteristic.PERMISSION_READ or  // Permission to read
                    BluetoothGattCharacteristic.PERMISSION_WRITE    // Permission to write
        )


        // Create the service and add the characteristic
        val service = BluetoothGattService(serviceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(characteristic)

        // Get the BluetoothManager and BluetoothAdapter
        val bluetoothManager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

        // Initialize the GATT Server
        if (ActivityCompat.checkSelfPermission(
                ctx,
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
            return
        }
        bluetoothGattServer = bluetoothManager.openGattServer(ctx, object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d("*******SATIS*******", "GATT SEVER Device connected: ${device.address}")
                    connectToBtDevice(device)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d("*******SATIS*******", "GATT SEVER Device disconnected: ${device.address}")
                    //stopBluetoothAdvertising()
                }
            }

            override fun onCharacteristicReadRequest(
                device: BluetoothDevice,
                requestId: Int,
                offset: Int,
                characteristic: BluetoothGattCharacteristic
            ) {
                Log.d("*******SATIS*******", "GATT SEVER Read request for characteristic: ${characteristic.uuid}")

                // Example: Handle a specific characteristic
                if (characteristic.uuid == UUID.fromString("a7e550c4-69d1-4a6b-9fe7-8e21e5d571b6")) {
                    // Provide the value for the characteristic
                    var responseValue = "".toByteArray(Charsets.UTF_8)

                    if (SharedMessageManager.outgoingMessages.isNotEmpty()) {
                        responseValue = SharedMessageManager.outgoingMessages.removeAt(0).content.toByteArray(Charsets.UTF_8)
                    }

                    // If the offset is beyond the data size, return an error response
                    if (offset > responseValue.size) {
                        if (ActivityCompat.checkSelfPermission(
                                ctx,
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
                            return
                        }

                        bluetoothGattServer.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_INVALID_OFFSET,
                            offset,
                            responseValue.copyOfRange(offset, responseValue.size)
                        )
                        return
                    }

                    // Send the response back to the client
                    bluetoothGattServer.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        offset,
                        responseValue.copyOfRange(offset, responseValue.size) // Respect offset
                    )
                } else {
                    // Unknown characteristic - return an error
                    bluetoothGattServer.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        offset,
                        null
                    )
                }
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray
            ) {
                Log.d("*******SATIS*******", "GATT SEVER Write request for characteristic ID: ${characteristic.uuid}")
                Log.d("*******SATIS*******", "GATT SEVER Write request for characteristic VALUE: ${value.toString(Charsets.UTF_8)}")
                Log.d("*******SATIS*******", "GATT SEVER IS SERVER MODE 222 ??? : ${SharedMessageManager.isServerMode}")
                if (value.toString(Charsets.UTF_8).isNotEmpty() && SharedMessageManager.isServerMode == true) {
                    SharedMessageManager.messages.add(Message(value.toString(Charsets.UTF_8), isSentByMe = false))
                    navigateTo("chat_screen")
                }
                // Handle characteristic write request
            }
        })

        // Add the service to the GATT server
        val serviceAdded = bluetoothGattServer.addService(service)
        Log.d("*******SATIS*******", "**************** Service added to GATT server: $serviceAdded **********************")
        stopBluetoothAdvertising()
        startBluetoothAdvertising()
    }

    fun startBluetoothAdvertising() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e("*******SATIS*******", "Bluetooth is not enabled or not supported.")
            return
        }

        //val advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        advertiser = bluetoothAdapter.bluetoothLeAdvertiser

        // Define the advertising settings
        val advertisingSettings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        bluetoothAdapter.name ="GATT chat"

        // Define the advertising data
        val advertisingData = AdvertiseData.Builder()
            .setIncludeDeviceName(true) // Include device name in advertising
            .build()

        // Start advertising
        if (ActivityCompat.checkSelfPermission(
                ctx,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            Log.d("*******SATIS*******", "******** PERMISSION FAIL ****************")
            return
        }
        Log.d("*******SATIS*******", "START advertising...")
        advertiser.startAdvertising(advertisingSettings, advertisingData, object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                Log.d("*******SATIS*******", "Advertising started successfully.")
                isAdvertising = true
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e("*******SATIS*******", "Advertising failed to start. Error code: $errorCode")
            }
        })
    }

    fun readGattService(gatt: BluetoothGatt, deviceNameCharacteristic: BluetoothGattCharacteristic) {
        if (ActivityCompat.checkSelfPermission(
                ctx,
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
            return
        }
        deviceNameCharacteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        gatt.readCharacteristic(deviceNameCharacteristic)
    }

    fun navigateTo(route: String) {
        viewModelScope.launch {
            _navigationEvent.emit(route) // Emit navigation route
        }
    }

    fun startCommunicationTimer() {
        timer.schedule(object : TimerTask() {
            override fun run() {
                sendKeepAliveMessage()
            }
        }, 0, 7000) // Initial delay = 0, period = 7000 ms (7 seconds)
    }

    fun stopKeepAlive() {
        timer.cancel()
    }

    private fun sendKeepAliveMessage() {
        readGattService(gatt = SharedMessageManager.gatt!!, deviceNameCharacteristic = SharedMessageManager.deviceNameCharacteristic!!)
    }

    fun stopBluetoothAdvertising() {
        if (ActivityCompat.checkSelfPermission(
                ctx,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }

        if (isAdvertising == false) {
            return
        }



        advertiser.stopAdvertising(object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                Log.d("*******SATIS*******", "Advertising stopped successfully.")
                isAdvertising = false
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e("*******SATIS*******", "Failed to stop advertising. Error code: $errorCode")
            }
        })
        Log.d("*******SATIS*******", "Stopped advertising.")
    }

}