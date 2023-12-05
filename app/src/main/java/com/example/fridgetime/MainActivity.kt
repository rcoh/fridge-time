package com.example.fridgetime

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MainActivity : ComponentActivity() {
    private val bluetoothScanner = BluetoothScanner(this)
    private var model: Model = Model(null, "app is starting...", true)

    private fun updateModel(update: (Model) -> Model) {
        this.model = update(this.model)
        runOnUiThread {
            setContent { ui(this.model) }
        }
    }


    @SuppressLint("MissingPermission")
    suspend fun findDevice(deviceName: String): BluetoothDevice? {
        return suspendCoroutine { continuation ->
            bluetoothScanner.bondedDevices {
                continuation.resume(it.find {
                    it.name.startsWith(
                        deviceName
                    )
                })
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateModel { x -> x }
        lifecycleScope.launch(Dispatchers.IO) {
            connectPrinter()
        }
    }

    private suspend fun connectPrinter() {
        updateModel { model -> model.copy(connecting = true, message = "connecting to printer")  }
        val bonded = findDevice("D110")
        if (bonded == null) {
            updateModel { it.copy(message = "No printer currently connected", connecting = false) }
        } else {
            val client =
                NiimbotPrinterClient(bonded.address, bluetoothScanner.bluetoothAdapter!!)
            if (!client.connected()) {
                updateModel { it.copy(message = "found printer, but not connected", connecting = false) }
            } else {
                val printer = DatePrinter(client)
                updateModel { it.copy(printer = printer, connecting = false, message = "ready to print") }
            }
        }
    }

    private fun printToday(printer: DatePrinter): () -> Unit {
        return { lifecycleScope.launch { printer.printToday() } }
    }

    private fun printTomorrow(printer: DatePrinter): () -> Unit {
        return { lifecycleScope.launch { printer.printTomorrow() } }
    }
    /*
        runOnUiThread {
            setContent { Text("app is starting") }
        }
        lifecycleScope.launch {
            println("looking for device...")
            val bonded = findDevice("D110")
            println("found device")

            if (bonded == null)  {
                runOnUiThread { setContent { Text("no bonded clients") } }
            } else {
                runOnUiThread { setContent {
                    Button(onClick = {
                        lifecycleScope.launch {
                            val client = withContext(Dispatchers.IO) { NiimbotPrinterClient(bonded.address, bluetoothScanner.bluetoothAdapter!!) }
                            if (!client.connected()) {
                                runOnUiThread { setContent { Text("printer not connected") } }
                            } else {
                                val hb = withContext(Dispatchers.IO) {
                                    DatePrinter(client).printSomething()
                                }
                                println("battery life: $hb")
                                runOnUiThread { setContent { Text("got heart beat!, $hb") } }
                            }
                        }
                    }) { Text("print!")}
                } }
            }
        }
        return
    }*/

    /*
        bluetoothScanner.startScanning {
            println("callback hit...")
            val printer = it.filter { (it.name ?: "").startsWith("D110") }.firstOrNull()
            runOnUiThread {
                if (printer == null) {
                    setContent { Text("printer not found (${it.map { it.name }})") }
                } else {
                    setContent { Text("found printer! ${printer.address}, ${printer.bondState}") }
                }
            }
            if (printer != null) {
                val client =
                    NiimbotPrinterClient(printer.address, bluetoothScanner.bluetoothAdapter!!)
                suspend {
                    val hb = client.heartbeat()
                    runOnUiThread { setContent { Text("got heart beat!, $hb") } }
                }
            }
            /*runOnUiThread {
                setContent {
                    Text(it.map { it.name }.toString())
                }
            }*/
        }
}*/

    @Composable
    private fun ui(model: Model) {
        val message = model.message
        return Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Static label to display the message

            // Row to align buttons horizontally
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // "Print Today" button
                Button(onClick = model.printer?.let { printToday(it) } ?: {},
                    enabled = model.printer != null
                ) {
                    Text("Print Today")
                }

                // "Print Tomorrow" button
                Button(onClick = model.printer?.let { printTomorrow(it) } ?: {},
                    enabled = model.printer != null
                ) {
                    Text("Print Tomorrow")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (model.printer == null && !model.connecting) {
                    Button(onClick = {
                        lifecycleScope.launch(Dispatchers.IO) {
                            connectPrinter()
                        }
                    }) {
                        Text(text = "Reconnect printer")
                    }
                }
            }

            Text(
                text = message,
                //style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }


    override fun onPause() {
        super.onPause()
        bluetoothScanner.stopScanning()
    }
}

data class Model(val printer: DatePrinter?, val message: String, val connecting: Boolean)
