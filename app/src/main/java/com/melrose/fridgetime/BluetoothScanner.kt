package com.melrose.fridgetime

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred

class BluetoothScanner(private val context: ComponentActivity) {
    val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var deviceDiscoveryCallback: ((List<BluetoothDevice>) -> Unit)? = null
    private val discoveredDevices = mutableListOf<BluetoothDevice>()
    private val bluetoothPermissionScan = Manifest.permission.BLUETOOTH_SCAN
    private val bluetoothPermissionConnect = Manifest.permission.BLUETOOTH_CONNECT
    private val internet = Manifest.permission.INTERNET
    private val permissions = listOf(bluetoothPermissionScan, bluetoothPermissionConnect, internet)
    private val permissionsGranted: CompletableDeferred<Boolean> = CompletableDeferred(null)


    private val requestPermissionLauncher: ActivityResultLauncher<Array<String>> =
        context.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { isGranted ->
            if (isGranted.values.all { it }) {
                permissionsGranted.complete(true)
            } else {
                permissionsGranted.complete(false)
            }
        }


    suspend fun bondedDevices(): List<BluetoothDevice> {
        if (permissions.any {
                ContextCompat.checkSelfPermission(
                    context,
                    it
                ) != PackageManager.PERMISSION_GRANTED
            }) {
            if (!requestPermissions()) {
                return listOf()
            }
        }
        return bluetoothAdapter?.bondedDevices?.toList() ?: listOf()
    }

    private suspend fun requestPermissions(): Boolean {
        requestPermissionLauncher.launch(
            arrayOf(
                bluetoothPermissionScan,
                bluetoothPermissionConnect,
                internet
            )
        )
        return permissionsGranted.await()
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
}
