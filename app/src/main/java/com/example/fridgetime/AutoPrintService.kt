package com.example.fridgetime

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

class AutoPrintService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var printer: DatePrinter? = null
    private var lastPrint: Int? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        private const val CHANNEL_ID = "bluetooth_monitor_channel"
    }

    private val quickCheckRunnable = object : Runnable {
        override fun run() {
            val today = LocalDate.now().dayOfYear
            if (lastPrint != null && lastPrint == today) {
                println("we already printed for ${LocalDate.now().dayOfYear}. Going to sleep.")
                handler.postDelayed(this, 15000) // Check every 15 seconds
                return
            }
            if (printer == null) {
                printer = attemptConnection()
            }
            if (printer != null) {
                println("successfull connection. Printing!")
                serviceScope.launch {
                    printer?.printToday()
                    lastPrint = LocalDate.now().dayOfYear
                }
            } else {
                println("did not attempt to connect")
            }
            handler.postDelayed(this, 15000) // Check every 15 seconds
        }
    }

    private fun attemptConnection(prefix: String = "D110"): DatePrinter? {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Permission not granted, can't proceed
            println("no bluetooth permissions")
            return null
        }
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter?.isEnabled != true) return null

        val printer = bluetoothAdapter.bondedDevices.find {
            it.name.startsWith(prefix)
        }
        if (printer == null) {
            println("no printer found")
            return null
        }
        // todo: consider wrapping this in the IO context
        val rawDatePrinter = NiimbotPrinterClient(printer.address, bluetoothAdapter)
        if (!rawDatePrinter.connected()) {
            println("printer not connected")
            return null
        }
        return DatePrinter(this, rawDatePrinter)
    }

    override fun onCreate() {
        println("bluetooth monitor service created")
        super.onCreate()

        createNotificationChannel()

        // Start as foreground service
        startForeground(1, createNotification())

        handler.post(quickCheckRunnable)
    }

    private fun createNotificationChannel() {
        val name = "Bluetooth Monitor"
        val descriptionText = "Monitors Bluetooth printer connections"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }

        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(quickCheckRunnable)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        // Create notification for foreground service
        return NotificationCompat.Builder(this, "bluetooth_channel")
            .setContentTitle("Bluetooth Printer Monitor")
            .setContentText("Monitoring for printer connections")
            .setChannelId(CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }
}