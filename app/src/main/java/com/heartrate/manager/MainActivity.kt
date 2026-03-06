package com.heartrate.manager

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.heartrate.manager.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var bleManager: BleManager
    private lateinit var webSocketManager: WebSocketManager
    private lateinit var lineChart: LineChart
    
    private val heartRateData = mutableListOf<Entry>()
    private val heartRateTimes = mutableListOf<String>() // 存储每个数据点的时间
    private var deviceListDialog: DeviceListDialog? = null
    private var selectedDevice: BluetoothDevice? = null
    private var connectionTime: Long? = null // 记录连接时间
    private var startTime: Long = System.currentTimeMillis() // 记录开始时间，用于计算秒数

    @SuppressLint("MissingPermission")
    private inner class DeviceListDialog(private val context: android.content.Context) {
        private var dialog: android.app.AlertDialog? = null
        private var deviceList: List<BluetoothDevice> = emptyList()

        fun show(devices: List<BluetoothDevice>) {
            deviceList = devices
            val deviceNames = devices.map { it.name ?: "Unknown Device" }.toTypedArray()

            if (dialog == null || !dialog!!.isShowing) {
                val builder = android.app.AlertDialog.Builder(context)
                builder.setTitle("选择设备")
                    .setItems(deviceNames) { _, which ->
                        this@MainActivity.selectedDevice = deviceList[which]
                        binding.tvTitle.text = deviceList[which].name ?: "Unknown Device"
                        binding.btnScan.text = getString(R.string.scan_devices)
                        bleManager.stopScan()
                        dialog?.dismiss()
                    }
                    .setNegativeButton("取消") { d, _ ->
                        d.dismiss()
                        // 点击取消时自动退出扫描
                        if (bleManager.isScanning()) {
                            bleManager.stopScan()
                            binding.btnScan.text = getString(R.string.scan_devices)
                        }
                    }
                
                dialog = builder.create()
                // 设置固定尺寸
                dialog?.window?.setLayout(
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    600 // 固定高度，单位为像素
                )
                
                dialog?.show()
            } else {
                // 关闭之前的对话框，创建新的对话框
                dialog?.dismiss()
                val builder = android.app.AlertDialog.Builder(context)
                builder.setTitle("选择设备")
                    .setItems(deviceNames) { _, which ->
                        this@MainActivity.selectedDevice = deviceList[which]
                        binding.tvTitle.text = deviceList[which].name ?: "Unknown Device"
                        binding.btnScan.text = getString(R.string.scan_devices)
                        bleManager.stopScan()
                        dialog?.dismiss()
                    }
                    .setNegativeButton("取消") { d, _ ->
                        d.dismiss()
                        // 点击取消时自动退出扫描
                        if (bleManager.isScanning()) {
                            bleManager.stopScan()
                            binding.btnScan.text = getString(R.string.scan_devices)
                        }
                    }
                
                dialog = builder.create()
                // 设置固定尺寸
                dialog?.window?.setLayout(
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    600 // 固定高度，单位为像素
                )
                
                dialog?.show()
            }
        }

        fun dismiss() {
            dialog?.dismiss()
            dialog = null
        }

        fun isShowing(): Boolean {
            return dialog?.isShowing ?: false
        }
    }
    
    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "需要蓝牙和位置权限", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViews()
        setupChart()
        initManagers()
        checkPermissions()
        setupObservers()
    }

    private fun setupViews() {
        lineChart = binding.lineChart
        
        binding.btnScan.setOnClickListener {
            if (bleManager.isScanning()) {
                bleManager.stopScan()
                binding.btnScan.text = getString(R.string.scan_devices)
            } else {
                bleManager.startScan()
                binding.btnScan.text = getString(R.string.stop_scan)
            }
        }

        binding.btnConnect.setOnClickListener {
            if (bleManager.connectionState.value is BleManager.ConnectionState.Connected) {
                bleManager.disconnect()
                binding.btnConnect.text = getString(R.string.connect)
            } else {
                if (selectedDevice != null) {
                    bleManager.connect(selectedDevice!!)
                    binding.btnConnect.text = getString(R.string.disconnect)
                    
                    // 启动连接超时检测
                    lifecycleScope.launch {
                        kotlinx.coroutines.delay(3000) // 3秒超时
                        if (bleManager.connectionState.value is BleManager.ConnectionState.Connecting) {
                            // 连接超时，显示失败消息
                            Toast.makeText(this@MainActivity, "连接失败，请重新扫描设备", Toast.LENGTH_SHORT).show()
                            // 断开连接
                            bleManager.disconnect()
                            // 重置按钮文本
                            binding.btnConnect.text = getString(R.string.connect)
                        }
                    }
                } else {
                    Toast.makeText(this, "请先扫描并选择设备", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnWebSocket.setOnClickListener {
            if (webSocketManager.isConnected()) {
                webSocketManager.disconnect()
                binding.btnWebSocket.text = getString(R.string.start_websocket)
            } else {
                webSocketManager.connect()
                binding.btnWebSocket.text = getString(R.string.stop_websocket)
            }
        }
    }

    private fun setupChart() {
        lineChart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            setDrawGridBackground(false)
            
            // 禁用标记视图，使用Toast显示信息
            setDrawMarkers(false)
            // 添加点击事件来显示心率和时间
            setOnChartValueSelectedListener(object : com.github.mikephil.charting.listener.OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry, h: com.github.mikephil.charting.highlight.Highlight) {
                    val index = e.x.toInt()
                    if (index < heartRateTimes.size) {
                        val time = heartRateTimes[index]
                        val heartRate = e.y.toInt()
                        val toast = android.widget.Toast.makeText(this@MainActivity, "时间: $time\n心率: $heartRate BPM", android.widget.Toast.LENGTH_SHORT)
                        toast.setGravity(android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL, 0, 200)
                        toast.show()
                    }
                }
                
                override fun onNothingSelected() {
                }
            })
            

            
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                setDrawAxisLine(true) // 显示坐标轴
                setDrawLabels(true) // 显示标签
                granularity = 1f
                textSize = 10f
                textColor = android.graphics.Color.WHITE
                axisMinimum = 0f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return "${value.toInt()}s"
                    }
                }
            }
            
            axisLeft.apply {
                setDrawGridLines(false)
                setDrawAxisLine(true) // 显示坐标轴
                setDrawLabels(true) // 显示标签
                axisMinimum = 40f
                axisMaximum = 200f
                granularity = 20f // 设置刻度间隔
                textSize = 10f
                textColor = android.graphics.Color.WHITE
            }
            
            axisRight.isEnabled = false
            
            legend.isEnabled = false
        }
    }

    private fun initManagers() {
        bleManager = BleManager(this)
        webSocketManager = WebSocketManager()
    }

    private fun checkPermissions() {
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            bleManager.heartRate.collect { heartRate ->
                heartRate?.let {
                    binding.tvHeartRate.text = it.toString()
                    updateChart(it)
                    
                    if (webSocketManager.isConnected()) {
                        webSocketManager.sendHeartRate(it)
                    }
                }
            }
        }

        var previousState: BleManager.ConnectionState? = null
        lifecycleScope.launch {
            bleManager.connectionState.collect { state ->
                val statusText = when (state) {
                    is BleManager.ConnectionState.Disconnected -> getString(R.string.disconnected)
                    is BleManager.ConnectionState.Connecting -> getString(R.string.connecting)
                    is BleManager.ConnectionState.Connected -> getString(R.string.connected)
                }
                binding.tvStatus.text = statusText
                
                // 检查连接失败
                if (previousState is BleManager.ConnectionState.Connecting && state is BleManager.ConnectionState.Disconnected) {
                    // 连接失败，显示提示
                    Toast.makeText(this@MainActivity, "连接失败，请重新扫描设备", Toast.LENGTH_SHORT).show()
                }
                
                // 记录连接时间
                when (state) {
                    is BleManager.ConnectionState.Connected -> {
                        connectionTime = System.currentTimeMillis()
                        startTime = System.currentTimeMillis() // 重置开始时间
                        // 重置图表数据
                        heartRateData.clear()
                        heartRateTimes.clear()
                        // 更新图表显示
                        lineChart.data = LineData()
                        lineChart.notifyDataSetChanged()
                        lineChart.invalidate()
                        binding.btnConnect.text = getString(R.string.disconnect)
                    }
                    else -> {
                        connectionTime = null
                        binding.btnConnect.text = getString(R.string.connect)
                    }
                }
                
                previousState = state
            }
        }

        // 不再通过 connectedDeviceName 来更新设备名显示，因为我们在选择设备时直接更新
        // 这样可以确保即使设备断开连接，设备名仍然显示


        lifecycleScope.launch {
            bleManager.scanResults.collect { devices ->
                if (devices.isNotEmpty() && bleManager.isScanning()) {
                    showDeviceListDialog(devices)
                }
            }
        }

        lifecycleScope.launch {
            webSocketManager.connectionState.collect { state ->
                updateWebSocketStatus()
            }
        }

        lifecycleScope.launch {
            webSocketManager.clientCount.collect { count ->
                updateWebSocketStatus()
            }
        }
    }

    private fun showDeviceListDialog(devices: List<BluetoothDevice>) {
        if (deviceListDialog == null) {
            deviceListDialog = DeviceListDialog(this)
        }
        deviceListDialog?.show(devices)
    }

    private fun updateWebSocketStatus() {
        val state = webSocketManager.connectionState.value
        val clientCount = webSocketManager.clientCount.value
        val statusText = when (state) {
                    is WebSocketManager.ConnectionState.Disconnected -> "未启动websocket服务器"
                    is WebSocketManager.ConnectionState.Connecting -> "启动中..."
                    is WebSocketManager.ConnectionState.Connected -> if (clientCount == 0) "服务器已启动，等待客户端连接" else "有${clientCount}个客户端连接"
                    is WebSocketManager.ConnectionState.Disconnecting -> "关闭中..."
                }
        binding.tvWebSocketUrl.text = "${webSocketManager.serverAddress.value}\n状态: $statusText"
    }

    private fun updateChart(heartRate: Int) {
        // 获取当前时间
        val currentTime = System.currentTimeMillis()
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = currentTime
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = calendar.get(java.util.Calendar.MINUTE)
        val second = calendar.get(java.util.Calendar.SECOND)
        val timeString = String.format("%02d:%02d:%02d", hour, minute, second)
        
        // 使用从开始时间计算的秒数作为 x 轴的值
        val xValue = ((currentTime - startTime) / 1000f) // 转换为秒
        
        heartRateData.add(Entry(xValue, heartRate.toFloat()))
        heartRateTimes.add(timeString)
        
        if (heartRateData.size > 60) {
            heartRateData.removeAt(0)
            heartRateTimes.removeAt(0)
        }
        
        val dataSet = LineDataSet(heartRateData, "心率").apply {
            color = ContextCompat.getColor(this@MainActivity, R.color.red)
            setCircleColor(ContextCompat.getColor(this@MainActivity, R.color.red))
            lineWidth = 2f
            circleRadius = 3f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            cubicIntensity = 0.2f
        }
        
        val lineData = LineData(dataSet)
        lineChart.data = lineData
        lineChart.notifyDataSetChanged()
        lineChart.invalidate()
    }

    override fun onDestroy() {
        super.onDestroy()
        bleManager.disconnect()
        webSocketManager.disconnect()
    }
}