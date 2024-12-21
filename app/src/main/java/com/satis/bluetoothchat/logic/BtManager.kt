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
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotMutableState
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

    var selectedOption = mutableStateOf("Select an Option")
    //private val _selectedOption = mutableStateOf("Select an Option")
    //val selectedOption: SnapshotMutableState<String> = _selectedOption

    //var selectedOption : SnapshotMutableState<String> = mutableStateOf("Select an Option")

    private val btManager: BluetoothManager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = btManager.adapter //BluetoothAdapter.getDefaultAdapter()
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<Array<String>>

    private lateinit var bluetoothGattServer: BluetoothGattServer

    private lateinit var advertiser: BluetoothLeAdvertiser

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
        startBluetoothAdvertising()
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
        //val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

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
        Log.d("****SATIS****", "selectedOption is ${selectedOption.value}")
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
        when (type) {
            BluetoothDevice.DEVICE_TYPE_LE -> connectToLEDevice(device)
            BluetoothDevice.DEVICE_TYPE_DUAL -> connectToLEDevice(device)
            BluetoothDevice.DEVICE_TYPE_CLASSIC -> connectToClassicDevice(device)
            else -> Toast.makeText(ctx, "Unknown device type", Toast.LENGTH_SHORT).show()
        }
    }

    private fun connectToClassicDevice(device: BluetoothDevice) {
        // Ensure Bluetooth permissions
        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.d("****SATIS****", "Bluetooth CONNECT permission is missing.")
            return
        }

        // Common UUID for serial communication
        val uuid = device.uuids?.firstOrNull()?.uuid ?: return
        val bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)

        try {
            // Cancel discovery to avoid interference
            //val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter.cancelDiscovery()
            }

            // Connect to the device
            bluetoothSocket.connect()
            Log.d("****SATIS****", "Connected to ${device.name} (${device.address})")
        } catch (e: Exception) {
            Log.e("****SATIS****", "Failed to connect: ${e.message}")
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
                    Log.d("****SATIS****", "Disconnected from GATT server on ${device.name}")
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                super.onServicesDiscovered(gatt, status)
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("****SATIS****", "Services discovered: ${gatt.services}")

                    // Find the GAP service
                    //val gapService = gatt.getService(UUID.fromString("00001800-0000-1000-8000-00805f9b34fb"))
                    val gapService = getGattService(gatt)
                    if (gapService != null) {
                        //val deviceNameCharacteristic = gapService.getCharacteristic(UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb"))
                        val deviceNameCharacteristic = getGattCharacteristic(gapService)
                        if (deviceNameCharacteristic != null) {
                            readWriteGattService(gatt, deviceNameCharacteristic)
                            SharedMessageManager.gatt = gatt
                            SharedMessageManager.deviceNameCharacteristic = deviceNameCharacteristic
                            startKeepAlive()
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
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    //if (characteristic.uuid == UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb")) {
                    if (characteristic.uuid == UUID.fromString(getGattCharacteristicUUIDString())) {
                        val deviceName = characteristic.value.toString(Charsets.UTF_8)
                        //Log.d("****SATIS****", "Device Name: $deviceName")
                        //activity.runOnUiThread {
                        //    Toast.makeText(ctx, "Device Name: $deviceName response message", Toast.LENGTH_LONG).show()
                        //}
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
        // Define a custom UUID for the service and characteristic
        val serviceUuid = UUID.fromString("12345678-1234-5678-1234-567812345678")
        val characteristicUuid = UUID.fromString("a7e550c4-69d1-4a6b-9fe7-8e21e5d571b6")

        val message = "Hello from the server side!"

        // Create the characteristic
        val characteristic = BluetoothGattCharacteristic(
            characteristicUuid,
            BluetoothGattCharacteristic.PROPERTY_WRITE  or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
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
        val bluetoothGattServer = bluetoothManager.openGattServer(ctx, object : BluetoothGattServerCallback() {
            //override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            //    if (newState == BluetoothProfile.STATE_CONNECTED) {
            //        Log.d("************SATIS****************", "Device connected: ${device.address}")
            //    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            //        Log.d("************SATIS****************", "Device disconnected: ${device.address}")
            //    }
            //}
//
            //override fun onCharacteristicWriteRequest(
            //    device: BluetoothDevice,
            //    requestId: Int,
            //    characteristic: BluetoothGattCharacteristic,
            //    preparedWrite: Boolean,
            //    responseNeeded: Boolean,
            //    offset: Int,
            //    value: ByteArray
            //) {
            //    val message = String(value, Charsets.UTF_8)
            //    Log.d("************SATIS****************", "onCharacteristicWriteRequest Received message: $message")
//
            //    Toast.makeText(ctx, "Bluetooth permissions are required", Toast.LENGTH_SHORT).show()
//
            //    if (responseNeeded) {
            //        if (ActivityCompat.checkSelfPermission(
            //                ctx,
            //                Manifest.permission.BLUETOOTH_CONNECT
            //            ) != PackageManager.PERMISSION_GRANTED
            //        ) {
            //            // TODO: Consider calling
            //            //    ActivityCompat#requestPermissions
            //            // here to request the missing permissions, and then overriding
            //            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //            //                                          int[] grantResults)
            //            // to handle the case where the user grants the permission. See the documentation
            //            // for ActivityCompat#requestPermissions for more details.
            //            return
            //        }
            //        bluetoothGattServer.sendResponse(
            //            device,
            //            requestId,
            //            BluetoothGatt.GATT_SUCCESS,
            //            offset,
            //            message.toByteArray(Charsets.UTF_8)
            //        )
            //    }
            //}
            override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d("*******SATIS*******", "GATT SEVER Device connected: ${device.address}")
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d("*******SATIS*******", "GATT SEVER Device disconnected: ${device.address}")
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
                    val responseValue = "Hello from GATT Server!".toByteArray(Charsets.UTF_8)

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
                            null
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
                if (value.toString(Charsets.UTF_8).isNotEmpty()) {
                    SharedMessageManager.messages.add(Message(value.toString(Charsets.UTF_8), isSentByMe = false))
                }
                // Handle characteristic write request
            }
        })

        // Add the service to the GATT server
        val serviceAdded = bluetoothGattServer.addService(service)
        Log.d("*******SATIS*******", "**************** Service added to GATT server: $serviceAdded **********************")
    }

    fun startBluetoothAdvertising() {
        val bluetoothManager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e("*******SATIS*******", "Bluetooth is not enabled or not supported.")
            return
        }

        //val advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.e("*******SATIS*******", "BLE advertising is not supported on this device.")
            return
        }

        // Define the advertising settings
        val advertisingSettings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        // Define the advertising data
        val advertisingData = AdvertiseData.Builder()
            //.setIncludeDeviceName(true) // Include device name in advertising
            .addServiceUuid(ParcelUuid(UUID.fromString("12345678-1234-5678-1234-567812345678"))) // Use the same service UUID as GATT server
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
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e("*******SATIS*******", "Advertising failed to start. Error code: $errorCode")
            }
        })
    }



    /*
    fun startGattClient() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
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
                Log.d("BLE", "Device found: ${device.name} (${device.address})")
                // Optionally, stop scanning when the desired device is found
                if (scanner != null) {
                    scanner.stopScan(this)
                }
                connectToLEDevice(device) // Proceed to connection
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e("BLE", "Scan failed with error code $errorCode")
            }
        }

        // Start scanning
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner?.startScan(null, scanSettings, scanCallback)
    }
    */

    private fun getGattService(gatt: BluetoothGatt): BluetoothGattService {
        when (selectedOption.value) {
            "Ping" -> return gatt.getService(UUID.fromString("00001800-0000-1000-8000-00805f9b34fb"))
            "Hello" -> return gatt.getService(UUID.fromString("12345678-1234-5678-1234-567812345678"))
            "Write" -> return gatt.getService(UUID.fromString("12345678-1234-5678-1234-567812345678"))
            else -> return gatt.getService(UUID.fromString("00001800-0000-1000-8000-00805f9b34fb"))
        }
    }

    private fun getGattCharacteristic(gapService: BluetoothGattService): BluetoothGattCharacteristic {
        when (selectedOption.value) {
            "Ping" -> return gapService.getCharacteristic(UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb"))
            "Hello" -> return gapService.getCharacteristic(UUID.fromString("a7e550c4-69d1-4a6b-9fe7-8e21e5d571b6"))
            "Write" -> return gapService.getCharacteristic(UUID.fromString("a7e550c4-69d1-4a6b-9fe7-8e21e5d571b6"))
            else -> return gapService.getCharacteristic(UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb"))
        }
    }

    private fun getGattCharacteristicUUIDString(): String {
        when (selectedOption.value) {
            "Ping" -> return "00002a00-0000-1000-8000-00805f9b34fb"
            "Hello" -> return "a7e550c4-69d1-4a6b-9fe7-8e21e5d571b6"
            "Write" -> return "a7e550c4-69d1-4a6b-9fe7-8e21e5d571b6"
            else -> return "00002a00-0000-1000-8000-00805f9b34fb"
        }
    }

    private fun readWriteGattService(gatt: BluetoothGatt, deviceNameCharacteristic: BluetoothGattCharacteristic) {
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
        when (selectedOption.value) {
            "Ping" -> gatt.readCharacteristic(deviceNameCharacteristic)
            "Hello" ->  {
                deviceNameCharacteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                gatt.readCharacteristic(deviceNameCharacteristic)
            }
            "Write" ->  {
                // Set the value to be written to the characteristic
                val valueToSend = "Hello from the client side".toByteArray(Charsets.UTF_8)
                deviceNameCharacteristic.value = valueToSend
                deviceNameCharacteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE //BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                gatt.writeCharacteristic(deviceNameCharacteristic)
            }
            else -> gatt.readCharacteristic(deviceNameCharacteristic)
        }
    }

    fun writeGattService(gatt: BluetoothGatt, deviceNameCharacteristic: BluetoothGattCharacteristic, message: String) {
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
        // Set the value to be written to the characteristic
        val valueToSend = message.toByteArray(Charsets.UTF_8)
        deviceNameCharacteristic.value = valueToSend
        deviceNameCharacteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        gatt.writeCharacteristic(deviceNameCharacteristic)
    }

    fun navigateTo(route: String) {
        viewModelScope.launch {
            _navigationEvent.emit(route) // Emit navigation route
        }
    }

    fun startKeepAlive() {
        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                sendKeepAliveMessage()
            }
        }, 0, 1000) // Initial delay = 0, period = 5000 ms (5 seconds)
    }

    fun stopKeepAlive() {
        timer.cancel()
    }

    private fun sendKeepAliveMessage() {
        readWriteGattService(gatt = SharedMessageManager.gatt!!, deviceNameCharacteristic = SharedMessageManager.deviceNameCharacteristic!!)
    }

}