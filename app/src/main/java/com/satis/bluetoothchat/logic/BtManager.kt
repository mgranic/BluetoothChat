package com.satis.bluetoothchat.logic

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class BtManager(val ctx: Context, val activity: ComponentActivity) {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    fun requestPermissions() {
        // Check Bluetooth support
        Log.d("****SATIS****", "************************** 1 ************************")
        if (bluetoothAdapter == null) {
            Toast.makeText(ctx, "Bluetooth not supported on this device", Toast.LENGTH_SHORT).show()
            Log.d("****SATIS****", "************************** 2 ************************")
            return
        }
        Log.d("****SATIS****", "************************** 3 ************************")

        // Check permissions (Android 12+ requires runtime permissions)
        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.d("****SATIS****", "************************** 4 ************************")
            activity.requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_SCAN), 1)
            return
        }
        Log.d("****SATIS****", "************************** 5 ************************")
    }
}