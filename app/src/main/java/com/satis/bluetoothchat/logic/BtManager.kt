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
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotMutableState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.core.app.ActivityCompat
import java.util.UUID

class BtManager(val ctx: Context, val activity: ComponentActivity) {
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
    }

    fun startBluetoothScan() {
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
                            // Read the Device Name characteristic
                            //if (ActivityCompat.checkSelfPermission(
                            //        ctx,
                            //        Manifest.permission.BLUETOOTH_CONNECT
                            //    ) != PackageManager.PERMISSION_GRANTED
                            //) {
                            //    // TODO: Consider calling
                            //    //    ActivityCompat#requestPermissions
                            //    // here to request the missing permissions, and then overriding
                            //    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                            //    //                                          int[] grantResults)
                            //    // to handle the case where the user grants the permission. See the documentation
                            //    // for ActivityCompat#requestPermissions for more details.
                            //    return
                            //}
                            //gatt.readCharacteristic(deviceNameCharacteristic)
                            readWriteGattService(gatt, deviceNameCharacteristic)
                            Log.d("****SATIS****", "Reading Device Name characteristic")
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
                        Log.d("****SATIS****", "Device Name: $deviceName")
                        activity.runOnUiThread {
                            Toast.makeText(ctx, "Device Name: $deviceName response message", Toast.LENGTH_LONG).show()
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
        // Define a custom UUID for the service and characteristic
        val serviceUuid = UUID.fromString("12345678-1234-5678-1234-567812345678")
        val characteristicUuid = UUID.fromString("a7e550c4-69d1-4a6b-9fe7-8e21e5d571b6")

        val message = "Hello from the server side!"

        // Create the characteristic
        val characteristic = BluetoothGattCharacteristic(
            characteristicUuid,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        // Create the service and add the characteristic
        val service = BluetoothGattService(serviceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(characteristic)

        // Get the BluetoothManager and BluetoothAdapter
        val bluetoothManager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

        // Initialize the GATT Server
        val bluetoothGattServer = bluetoothManager.openGattServer(ctx, object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d("************SATIS****************", "Device connected: ${device.address}")
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d("************SATIS****************", "Device disconnected: ${device.address}")
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
                val message = String(value, Charsets.UTF_8)
                Log.d("************SATIS****************", "Received message: $message")

                if (responseNeeded) {
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
                        BluetoothGatt.GATT_SUCCESS,
                        offset,
                        message.toByteArray(Charsets.UTF_8)
                    )
                }
            }
        })

        // Add the service to the GATT server
        val serviceAdded = bluetoothGattServer.addService(service)
        Log.d("*******SATIS*******", "**************** Service added to GATT server: $serviceAdded **********************")
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
                deviceNameCharacteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                gatt.writeCharacteristic(deviceNameCharacteristic)
            }
            else -> gatt.readCharacteristic(deviceNameCharacteristic)
        }
    }

}