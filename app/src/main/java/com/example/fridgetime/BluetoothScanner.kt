package com.example.fridgetime

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class BluetoothScanner(private val context: ComponentActivity) {
    val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var deviceDiscoveryCallback: ((List<BluetoothDevice>) -> Unit)? = null
    private val discoveredDevices = mutableListOf<BluetoothDevice>()
    private val bluetoothPermissionScan = Manifest.permission.BLUETOOTH_SCAN
    private val bluetoothPermissionConnect = Manifest.permission.BLUETOOTH_CONNECT

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<Array<String>>

    fun bondedDevices(callback: (List<BluetoothDevice>) -> Unit) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(false, callback)
        } else {
            callback(bluetoothAdapter?.bondedDevices?.toList() ?: listOf())
        }

    }
    private fun requestPermissions(scan: Boolean, callback: (List<BluetoothDevice>) -> Unit) {
        requestPermissionLauncher =
            context.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { isGranted ->
                if (isGranted.values.all { it }) {
                    if (scan) {
                        startScanning(callback)
                    } else {
                        callback(bluetoothAdapter?.bondedDevices?.toList() ?: listOf())
                    }
                } else {
                    println("no permissions: $isGranted")
                }
            }
        requestPermissionLauncher.launch(arrayOf(bluetoothPermissionScan, bluetoothPermissionConnect))
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    println("found device")
                    val device =
                        intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        discoveredDevices.add(it)
                    }
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    println("discovery finished")
                    deviceDiscoveryCallback?.invoke(discoveredDevices)
                    //println("cancelling discovery: ${bluetoothAdapter?.cancelDiscovery()}")
                    context?.unregisterReceiver(this)
                    deviceDiscoveryCallback = null
                }
            }
        }
    }

    fun startScanning(callback: (List<BluetoothDevice>) -> Unit) {
        println("starting to scan...")
        deviceDiscoveryCallback = callback
        discoveredDevices.clear()
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        // Check if Bluetooth permission is granted
        if (ContextCompat.checkSelfPermission(context, bluetoothPermissionConnect)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // Request Bluetooth permission
            println("requesting permissions")
            requestPermissions(true, callback)
        } else {
            println("starting discovery")
            // Permission already granted, start scanning
            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            context.registerReceiver(receiver, filter)
            //println("cancelling prior discovery: ${bluetoothAdapter?.cancelDiscovery()}")
            println("discovery started? ${bluetoothAdapter?.startDiscovery()}")
        }

    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        //context.unregisterReceiver(receiver)
        // bluetoothAdapter?.cancelDiscovery()
    }
}
