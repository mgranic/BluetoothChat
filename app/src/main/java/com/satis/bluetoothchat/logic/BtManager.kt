package com.satis.bluetoothchat.logic

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
//import androidx.activity.result.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.core.content.ContextCompat.registerReceiver
import androidx.core.content.PermissionChecker.checkSelfPermission
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

class BtManager(val ctx: Context, val activity: ComponentActivity) {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<Array<String>>

    //private lateinit var deviceListAdapter: ArrayAdapter<String>
    // Observable list of discovered devices
    private val _discoveredDevices = mutableStateListOf<String>()
    val discoveredDevices: SnapshotStateList<String> = _discoveredDevices

    // Define the activity result launcher
    private val enableBluetoothResultLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Log.d("****SATIS****", "Bluetooth has been enabled")
            // Proceed with Bluetooth operations
        } else {
            Log.d("****SATIS****", "Bluetooth enabling was canceled by the user")
            Toast.makeText(ctx, "Bluetooth is required to proceed", Toast.LENGTH_SHORT).show()
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("****SATIS****", "****** BCR 1 ***********")
            val action = intent?.action
            if (BluetoothDevice.ACTION_FOUND == action) {
                Log.d("****SATIS****", "****** BCR 2 ***********")
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
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
                        Log.d("****SATIS****", "****** BCR 3 ***********")
                        return
                    } else {
                        Log.d("****SATIS****", "****** BCR 4 ***********")
                    }
                    it.name ?: "Unknown Device"
                    val deviceAddress = it.address // MAC address
                    val deviceInfo = "$deviceName ($deviceAddress)"
                    Log.d("****SATIS****", "****** BCR 5 ***********")
                    if (!_discoveredDevices.contains(deviceInfo)) {
                        _discoveredDevices.add(deviceInfo)
                        Log.d("satis custom debug", "******* 1 ***** $deviceInfo ********")
                        //deviceListAdapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    // Register the BroadcastReceiver for device discovery
    private val filter = IntentFilter().apply {
        addAction(BluetoothDevice.ACTION_FOUND)
        addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
    }

    init {
        requestPermissions()
    }

    fun startBluetooth() {
        //requestPermissions()
        enableBluetooth()
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
                    Log.d("****SATIS****", "Found device: ${device.name} (${device.address})")
                }
            } else {
                Log.d("****SATIS****", "No devices found")
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
                Log.d("****SATIS****", "All permissions granted")
                enableBluetooth()
            } else {
                Log.d("****SATIS****", "Permissions denied")
                Toast.makeText(ctx, "Bluetooth permissions are required", Toast.LENGTH_SHORT).show()
            }
        }

        // Check if Bluetooth is supported
        if (bluetoothAdapter == null) {
            Log.d("****SATIS****", "Bluetooth not supported on this device")
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
            Log.d("****SATIS****", "Requesting permissions: $requiredPermissions")
            requestPermissionLauncher.launch(requiredPermissions.toTypedArray())
            return
        }

        Log.d("****SATIS****", "All permissions granted, proceeding with Bluetooth actions")
        enableBluetooth()
    }



    private fun enableBluetooth() {

        if (bluetoothAdapter == null) {
            Toast.makeText(ctx, "Bluetooth not supported on this device", Toast.LENGTH_SHORT).show()
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            Log.d("****SATIS****", "Bluetooth is off, prompting user to enable it")
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothResultLauncher.launch(enableBtIntent) // Using the launcher to handle the result
        } else {
            Log.d("****SATIS****", "Bluetooth is already enabled")
        }
    }

    private fun startBluetoothDiscovery(onDiscoveryComplete: (List<BluetoothDevice>) -> Unit) {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (bluetoothAdapter == null) {
            Log.d("****SATIS****", "Bluetooth is not supported on this device")
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
            Log.d("****SATIS****", "Bluetooth discovery started")
        } else {
            Log.d("****SATIS****", "Failed to start Bluetooth discovery")
        }
    }

}