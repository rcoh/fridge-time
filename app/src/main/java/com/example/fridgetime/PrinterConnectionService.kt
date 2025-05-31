package com.example.fridgetime

import android.Manifest
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*

class PrinterConnectionService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    // Assume DatePrinter is already set up for dependency injection or accessible
    // For simplicity, let's instantiate it here. In a real app, use DI.
    private lateinit var datePrinter: DatePrinter // You'll need to initialize this
    private var bluetoothAdapter: BluetoothAdapter? = null

    companion object {
        const val EXTRA_DEVICE_ADDRESS = "extra_device_address"
        const val EXTRA_DEVICE_NAME = "extra_device_name" // Optional
    }

    override fun onCreate() {
        super.onCreate()
        // It's good practice to get the adapter in onCreate or lazily
        // as it's unlikely to change during the service's lifecycle.
        val bluetoothManager: BluetoothManager =
            getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        println("PrinterConnectionService Created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val deviceAddress = intent?.getStringExtra(EXTRA_DEVICE_ADDRESS)
        val deviceName = intent?.getStringExtra(EXTRA_DEVICE_NAME) // Optional

        println("PrinterConnectionService Started for device: $deviceName ($deviceAddress)")

        if (deviceAddress != null) {
            // Check for BLUETOOTH_CONNECT permission before attempting to connect/print
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                println("BLUETOOTH_CONNECT permission not granted in Service. Stopping.")
                stopSelf() // Stop the service if permission is missing
                return START_NOT_STICKY
            }

            serviceScope.launch {
                try {
                    // 1. Connect to the printer (if your DatePrinter or underlying SDK needs explicit connection)
                    //    This step heavily depends on your Niimbot printer's SDK or communication protocol.
                    //    For example: YourPrinterSDK.connect(deviceAddress)
                    println("Attempting to connect and print to $deviceAddress...")
                    val printer = DatePrinter(applicationContext, NiimbotPrinterClient(deviceAddress, bluetoothAdapter!!))

                    datePrinter.printToday()
                } catch (e: Exception) {
                    println("Error during printing: ${e.message}")
                } finally {
                    stopSelf(startId)
                }
            }
        } else {
            // No device address provided, stop the service
            stopSelf(startId)
        }

        // If the service is killed, it will not be restarted unless there are pending intents.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Not a bound service
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel() // Cancel any ongoing coroutines
        println("PrinterConnectionService Destroyed")
    }
}