package com.heartrate.manager

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

class BleManager(private val context: Context) {

    private val bluetoothManager: BluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager.adapter
    }

    private val bluetoothLeScanner: BluetoothLeScanner? by lazy {
        bluetoothAdapter?.bluetoothLeScanner
    }

    private var bluetoothGatt: BluetoothGatt? = null

    private val _scanResults = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val scanResults: StateFlow<List<BluetoothDevice>> = _scanResults

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _heartRate = MutableStateFlow<Int?>(null)
    val heartRate: StateFlow<Int?> = _heartRate

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName

    private var isScanning = false

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (!_scanResults.value.contains(device)) {
                _scanResults.value = _scanResults.value + device
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error: $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothGatt.STATE_CONNECTED -> {
                    _connectionState.value = ConnectionState.Connected
                    gatt.discoverServices()
                }
                BluetoothGatt.STATE_DISCONNECTED -> {
                    _connectionState.value = ConnectionState.Disconnected
                    gatt.close()
                    bluetoothGatt = null
                    _connectedDeviceName.value = null
                    Log.d(TAG, "Disconnected")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                enableHeartRateNotification(gatt)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == HEART_RATE_MEASUREMENT_UUID) {
                val heartRateValue = parseHeartRate(characteristic.value)
                if (heartRateValue != null) {
                    _heartRate.value = heartRateValue
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS &&
                characteristic.uuid == HEART_RATE_MEASUREMENT_UUID) {
                val heartRateValue = parseHeartRate(characteristic.value)
                if (heartRateValue != null) {
                    _heartRate.value = heartRateValue
                }
            }
        }
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    fun startScan() {
        if (!isBluetoothEnabled()) {
            Log.e(TAG, "Bluetooth is not enabled")
            return
        }

        if (isScanning) return

        // 检查权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasScanPermission = context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasScanPermission) {
                Log.e(TAG, "BLUETOOTH_SCAN permission not granted")
                return
            }
        }

        _scanResults.value = emptyList()
        isScanning = true
        bluetoothLeScanner?.startScan(scanCallback)
        Log.d(TAG, "Started scanning")
    }

    fun stopScan() {
        if (!isScanning) return

        // 检查权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasScanPermission = context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasScanPermission) {
                Log.e(TAG, "BLUETOOTH_SCAN permission not granted")
                isScanning = false
                return
            }
        }

        isScanning = false
        bluetoothLeScanner?.stopScan(scanCallback)
        Log.d(TAG, "Stopped scanning")
    }

    fun connect(device: BluetoothDevice) {
        // 检查权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasConnectPermission = context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasConnectPermission) {
                Log.e(TAG, "BLUETOOTH_CONNECT permission not granted")
                return
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
        }
        _connectionState.value = ConnectionState.Connecting
        _connectedDeviceName.value = device.name
        Log.d(TAG, "Connecting to ${device.name}")
    }

    fun disconnect() {
        bluetoothGatt?.disconnect()
        // 不要立即关闭，等待onConnectionStateChange回调处理
        Log.d(TAG, "Disconnecting...")
    }

    private fun enableHeartRateNotification(gatt: BluetoothGatt) {
        // 检查权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasConnectPermission = context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasConnectPermission) {
                Log.e(TAG, "BLUETOOTH_CONNECT permission not granted")
                return
            }
        }

        val service = gatt.getService(HEART_RATE_SERVICE_UUID)
        if (service != null) {
            val characteristic = service.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)
            if (characteristic != null) {
                gatt.setCharacteristicNotification(characteristic, true)

                val ccc = characteristic.getDescriptor(CCC_UUID)
                if (ccc != null) {
                    ccc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(ccc)
                }
            }
        }
    }

    private fun parseHeartRate(data: ByteArray?): Int? {
        if (data == null || data.isEmpty()) return null

        val flags = data[0].toInt()
        val isHeartRateIn16Bit = (flags and 0x01) == 1

        return if (isHeartRateIn16Bit && data.size >= 3) {
            ((data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8))
        } else if (data.size >= 2) {
            data[1].toInt() and 0xFF
        } else {
            null
        }
    }

    fun clearHeartRate() {
        _heartRate.value = null
    }

    fun isScanning(): Boolean {
        return isScanning
    }

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
    }

    companion object {
        private const val TAG = "BleManager"
        
        val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_MEASUREMENT_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CCC_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}