package com.example.fridgetime

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

sealed class State(val message: String) {
    object AppStartup : State("App is starting...")
    object Connecting : State("Connecting to printer")
    object Connected : State("Printer is connected!")
    data class ConnectFailed(val error: String) : State("Connect failed ($error)")
}

class MainActivity : ComponentActivity() {
    private val bluetoothScanner = BluetoothScanner(this)
    private var model: Model = Model(null, state = State.AppStartup)

    private fun updateModel(update: (Model) -> Model) {
        this.model = update(this.model)
        runOnUiThread {
            setContent { Ui(this.model) }
        }
    }


    @SuppressLint("MissingPermission")
    suspend fun findDevice(deviceName: String): BluetoothDevice? {
        return bluetoothScanner.bondedDevices().find { device ->
            device.name.startsWith(
                deviceName
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateModel { x -> x }
        lifecycleScope.launch(Dispatchers.IO) {
            connectPrinter()
        }
    }

    private suspend fun connectPrinter() {
        updateModel { model -> model.copy(state = State.Connecting) }
        val bonded = findDevice("D110")
        val state = if (bonded == null) {
            State.ConnectFailed("Please pair a printer")
        } else {
            val client =
                NiimbotPrinterClient(bonded.address, bluetoothScanner.bluetoothAdapter!!)
            if (!client.connected()) {
                State.ConnectFailed("printer paired but not currently connected")
            } else {
                val printer = DatePrinter(client)
                updateModel { it.copy(printer = printer, state = State.Connected) }
                return
            }
        }
        updateModel { it.copy(state = state) }
    }

    private fun printToday(printer: DatePrinter): () -> Unit {
        return { lifecycleScope.launch { printer.printToday() } }
    }

    private fun printTomorrow(printer: DatePrinter): () -> Unit {
        return { lifecycleScope.launch { printer.printTomorrow() } }
    }

    private fun printText(printer: DatePrinter, text: String): () -> Unit {
        return { lifecycleScope.launch { printer.printText(text) } }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Ui(model: Model) {
        var customText by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Printing buttons row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = model.printer?.let { printToday(it) } ?: {},
                    enabled = model.printer != null
                ) {
                    Text("Print Today")
                }

                Button(onClick = model.printer?.let { printTomorrow(it) } ?: {},
                    enabled = model.printer != null
                ) {
                    Text("Print Tomorrow")
                }
            }

            // Divider for visual separation
            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Connection status and button row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val text = if (model.connecting()) "Connecting..." else "Reconnect Printer"
                Button(onClick = {
                    lifecycleScope.launch(Dispatchers.IO) {
                        connectPrinter()
                    }
                }, enabled = !model.connecting()) {
                    Text(text = text)
                }
            }

            // Status message
            Text(
                text = model.state.message,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Custom text input and print button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TextField(
                    value = customText,
                    onValueChange = { customText = it },
                    label = { Text("Enter Custom Text") },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(onClick = model.printer?.let { printText(it, customText) } ?: {},
                    enabled = model.printer != null && !model.connecting()
                ) {
                    Text("Print Custom Text")
                }
            }

        }
    }


    @Composable
    private fun UiOld(model: Model) {
        return Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
                val text = when (model.connecting()) {
                    true -> "Connecting..."
                    false -> "Reconnect printer"
                }
                Button(onClick = {
                    lifecycleScope.launch(Dispatchers.IO) {
                        connectPrinter()
                    }
                }, enabled = !model.connecting()) {
                    Text(text = text)
                }
            }

            Text(
                text = model.state.message,
                //style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}

data class Model(val printer: DatePrinter? = null, val message: String? = null, val state: State) {
    fun connecting(): Boolean = state == State.Connecting
}
