package com.satis.bluetoothchat.logic

//import androidx.activity.result.ActivityResultContracts
import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
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
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.core.app.ActivityCompat
import com.satis.bluetoothchat.model.BtDevice
import java.util.UUID

class BtManager(val ctx: Context, val activity: ComponentActivity) {
    // Observable list of discovered devices
    private val _discoveredDevices = mutableStateListOf<BluetoothDevice>()
    val discoveredDevices: SnapshotStateList<BluetoothDevice> = _discoveredDevices

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<Array<String>>

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
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                device?.let {
                    val deviceName = if (ActivityCompat.checkSelfPermission(
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
                        "Unknown Device - No permission granted"
                    } else {
                        device.name ?: "Unknown Device"
                    }
                    //it.name ?: "Unknown Device"
                    //val deviceAddress = it.address // MAC address
                    //val deviceInfo = "$deviceName ($deviceAddress)"
                    //if (!_discoveredDevices.contains(deviceInfo)) {
                    //    _discoveredDevices.add(deviceInfo)
                    //}
                    if (!_discoveredDevices.contains(device)) {
                        _discoveredDevices.add(device)
                    }
                }
            }
        }
    }

    // Register the BroadcastReceiver for device discovery
    private val filter = IntentFilter().apply {
        addAction(BluetoothDevice.ACTION_FOUND)
        addAction(BluetoothDevice.ACTION_NAME_CHANGED)
        addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
    }

    init {
        requestPermissions()
    }

    fun startBluetoothScan() {
        //requestPermissions()
        //enableBluetooth()
        startBluetoothDiscovery { devices ->
            if (devices.isNotEmpty()) {
                devices.forEach { device ->
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

                    }
                }
            } else {
            }
        }
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
        } else {
        }
    }

    private fun startBluetoothDiscovery(onDiscoveryComplete: (List<BluetoothDevice>) -> Unit) {
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
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
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

        // Start discovery
        val started = if (ActivityCompat.checkSelfPermission(
                ctx,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            false
        } else {
            true
        }
        bluetoothAdapter.startDiscovery()
        if (started) {
        } else {
        }
    }

    fun stopBluetoothDiscovery() {
        if (bluetoothAdapter?.isDiscovering == true) {
            if (ActivityCompat.checkSelfPermission(
                    ctx,
                    Manifest.permission.BLUETOOTH_SCAN
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
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        } else {
            device.type
        }
        when (type) {
            BluetoothDevice.DEVICE_TYPE_CLASSIC -> connectToClassicDevice(device)
            BluetoothDevice.DEVICE_TYPE_LE -> connectToLEDevice(device)
            BluetoothDevice.DEVICE_TYPE_DUAL -> connectToLEDevice(device)
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
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter.isDiscovering) {
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

        val bluetoothGatt = device.connectGatt(ctx, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    Log.d("****SATIS****", "Connected to GATT server on ${device.name}")
                    // Discover services
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
                    gatt.discoverServices()
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    Log.d("****SATIS****", "Disconnected from GATT server on ${device.name}")
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                super.onServicesDiscovered(gatt, status)
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("****SATIS****", "Services discovered: ${gatt.services}")
                } else {
                    Log.w("****SATIS****", "Failed to discover services: $status")
                }
            }
        })

        Log.d("****SATIS****", "Attempting to connect to ${device.name} (${device.address})")
    }
}