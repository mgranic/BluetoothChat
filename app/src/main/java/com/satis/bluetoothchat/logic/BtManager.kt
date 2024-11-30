package com.satis.bluetoothchat.logic

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
//import androidx.activity.result.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.PermissionChecker.checkSelfPermission

class BtManager(val ctx: Context, val activity: ComponentActivity) {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<Array<String>>

    // Define the activity result launcher
    val enableBluetoothResultLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Log.d("****SATIS****", "Bluetooth has been enabled")
            // Proceed with Bluetooth operations
        } else {
            Log.d("****SATIS****", "Bluetooth enabling was canceled by the user")
            Toast.makeText(ctx, "Bluetooth is required to proceed", Toast.LENGTH_SHORT).show()
        }
    }

    fun startBluetooth() {
        requestPermissions()
        enableBluetooth()
    }

    fun requestPermissions() {

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



    fun enableBluetooth() {

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
}