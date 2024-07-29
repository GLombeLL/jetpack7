@file:Suppress("DEPRECATION")

package com.metosoft.jetpack7

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.metosoft.jetpack7.ui.theme.Jetpack7Theme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class MainActivity : ComponentActivity() {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val devices = mutableStateListOf<BluetoothDevice>()


    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action: String? = intent.action
            if (BluetoothDevice.ACTION_FOUND == action) {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                device?.let { devices.add(it) }
            }
        }
    }

    lateinit var requestPermissionLauncher: ActivityResultLauncher<Array<String>>

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(receiver, filter)

        // Register permission request callback
        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            when {
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true -> {
                    // Location permission granted, start discovery
                    startDiscovery()
                }
                permissions[Manifest.permission.BLUETOOTH_CONNECT] == true -> {
                    // Bluetooth connect permission granted, refresh device list
                    setContent {
                        Jetpack7Theme {
                            BluetoothDeviceList(
                                devices = devices,
                                startDiscovery = { startDiscovery() },
                                connectToDevice = { connectToDevice(it) }
                            )
                        }
                    }
                }
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == false -> {
                    // Location permission denied, notify user
                    Toast.makeText(this, "Konum izni gerekli. Lütfen izin verin.", Toast.LENGTH_SHORT).show()
                }
                permissions[Manifest.permission.BLUETOOTH_CONNECT] == false -> {
                    // Bluetooth connect permission denied, notify user
                    Toast.makeText(this, "Bluetooth bağlantı izni gerekli. Lütfen izin verin.", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    // Any other permissions not granted, handle accordingly
                    Toast.makeText(this, "Gerekli izinler verilmedi.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        }

        setContent {
            Jetpack7Theme {
                BluetoothDeviceList(
                    devices = devices,
                    startDiscovery = { startDiscovery() },
                    connectToDevice = { connectToDevice(it) }
                )
            }
        }
    }
    private fun startDiscovery() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            bluetoothAdapter?.startDiscovery()
        } else {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun connectToDevice(device: BluetoothDevice) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
            return
        }

        val uuid: UUID = device.uuids[0].uuid

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val socket: BluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
                socket.connect()

                val outputStream: OutputStream = socket.outputStream
                val inputStream: InputStream = socket.inputStream

                // Example data sending
                val message = "Hello Bluetooth!"
                outputStream.write(message.toByteArray())

                // Data receiving
                val buffer = ByteArray(1024)
                var bytes: Int
                while (true) {
                    bytes = inputStream.read(buffer)
                    val receivedMessage = String(buffer, 0, bytes)
                    println("Received data: $receivedMessage")
                }

            } catch (e: IOException) {
                e.printStackTrace() // Connection error
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }
}

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun BluetoothCheckAndEnable() {
    val context = LocalContext.current
    val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    if (bluetoothAdapter == null) {
        Text("This device doesn't support Bluetooth")
    } else {
        if (!bluetoothAdapter.isEnabled) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                (context as? MainActivity)?.requestPermissionLauncher?.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
            } else {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                context.startActivity(enableBtIntent)
            }
        } else {
            Text("Bluetooth is enabled")
        }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun BluetoothDeviceList(
    devices: List<BluetoothDevice>,
    startDiscovery: () -> Unit,
    connectToDevice: (BluetoothDevice) -> Unit
) {
    val context = LocalContext.current

    Column {
        BluetoothCheckAndEnable()
        Button(onClick = { startDiscovery() }) {
            Text("Start Discovery")
        }
        LazyColumn {
            items(devices) { device ->
                val deviceName = if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    device.name ?: "Unknown Device"
                } else {
                    "Permission Required"
                }
                Text(text = deviceName, modifier = Modifier.clickable {
                    connectToDevice(device)
                })
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    Jetpack7Theme {
        BluetoothDeviceList(
            devices = emptyList(),
            startDiscovery = {},
            connectToDevice = {}
        )
    }
}
