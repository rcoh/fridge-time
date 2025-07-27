package com.melrose.fridgetime

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
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Model(
    val printer: DatePrinter? = null,
    val message: String? = null,
    val state: State,
    val backgroundServiceStarted: Boolean
) {
    fun connecting(): Boolean = state == State.Connecting

    fun canPrint(): Boolean = state == State.Connected
}

sealed class State(val message: String) {
    object AppStartup : State("App is starting...")
    object Connecting : State("Connecting to printer")
    object Connected : State("Printer is connected!")

    object Printing : State("Printing...")
    data class ConnectFailed(val error: String) : State("Connect failed ($error)")
}

class MainActivity : ComponentActivity() {
    private val bluetoothScanner = BluetoothScanner(this)
    private val modelFlow =
        MutableStateFlow(Model(null, state = State.AppStartup, backgroundServiceStarted = false))

    private fun updateModel(update: (Model) -> Model) {
        runOnUiThread { modelFlow.update(update) }
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
        enableEdgeToEdge()
        // Start the service
        lifecycleScope.launch(Dispatchers.IO) {
            connectPrinter()
        }
        setContent { Ui(modelFlow) }
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
                val printer = DatePrinter(this, client)
                updateModel { it.copy(printer = printer, state = State.Connected) }
                return
            }
        }
        updateModel { it.copy(state = state) }
    }

    private fun startBackgroundService() {
        val serviceIntent = android.content.Intent(this, AutoPrintService::class.java)
        startForegroundService(serviceIntent)
    }

    private fun printToday(printer: DatePrinter): () -> Unit {
        return { lifecycleScope.launch { print { printer.printToday() } } }
    }

    private fun queryState(printer: DatePrinter): () -> Unit {
        return { lifecycleScope.launch { print { printer.printerState() } } }
    }

    private fun printTomorrow(printer: DatePrinter): () -> Unit {
        return { lifecycleScope.launch { print { printer.printTomorrow() } } }
    }

    private fun printText(printer: DatePrinter, text: String): () -> Unit {
        return { lifecycleScope.launch { print { printer.printText(text) } } }
    }

    private suspend fun print(f: suspend () -> Unit) {
        updateModel { it.copy(state = State.Printing) }
        f()
        updateModel { it.copy(state = State.Connected) }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Ui(flow: StateFlow<Model>) {
        val model by flow.collectAsState()
        var customText by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
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
                Button(
                    onClick = model.printer?.let { printToday(it) } ?: {},
                    enabled = model.canPrint()
                ) {
                    Text("Print Today")
                }

                Button(
                    onClick = model.printer?.let { printTomorrow(it) } ?: {},
                    enabled = model.canPrint()
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

                Button(
                    onClick = model.printer?.let { printer ->
                        {
                            printText(printer, customText)()
                            customText = ""
                        }
                    } ?: {},
                    enabled = model.canPrint()
                ) {
                    Text("Print Custom Text")
                }
            }
            // Divider for visual separation
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Button(
                onClick =
                    {
                        startBackgroundService()
                        updateModel { it.copy(backgroundServiceStarted = true) }
                    },
                enabled = !model.backgroundServiceStarted
            ) {
                val text = if (model.backgroundServiceStarted) "Background Service Running" else "Start Background Service"
                Text(text)
            }
        }
    }
}

