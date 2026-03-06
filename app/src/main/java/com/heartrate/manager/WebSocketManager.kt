package com.heartrate.manager

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface

class WebSocketManager {

    private var webSocketServer: WebSocketServer? = null
    private val connectedClients = mutableSetOf<WebSocket>()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _serverAddress = MutableStateFlow<String>("ws://${getLocalIpAddress()}:8080/heartrate")
    val serverAddress: StateFlow<String> = _serverAddress

    private val _clientCount = MutableStateFlow<Int>(0)
    val clientCount: StateFlow<Int> = _clientCount

    private fun getLocalIpAddress(): String {
        try {
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()
            while (networkInterfaces.hasMoreElements()) {
                val networkInterface = networkInterfaces.nextElement()
                val inetAddresses = networkInterface.inetAddresses
                while (inetAddresses.hasMoreElements()) {
                    val inetAddress = inetAddresses.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress.hostAddress.indexOf(':') < 0) {
                        return inetAddress.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local IP address: ${e.message}")
        }
        return "192.168.1.100" // 默认值
    }

    fun connect(url: String = _serverAddress.value) {
        if (webSocketServer != null) {
            disconnect()
        }

        _serverAddress.value = url
        _connectionState.value = ConnectionState.Connecting

        // 解析地址和端口
        val parts = url.replace("ws://", "").split(":")
        val host = parts[0]
        val port = parts[1].split("/")[0].toInt()

        webSocketServer = object : WebSocketServer(InetSocketAddress(host, port)) {
            override fun onOpen(webSocket: WebSocket, handshake: ClientHandshake) {
                Log.d(TAG, "Client connected: ${webSocket.remoteSocketAddress}")
                connectedClients.add(webSocket)
                _clientCount.value = connectedClients.size
                _connectionState.value = ConnectionState.Connected
            }

            override fun onClose(webSocket: WebSocket, code: Int, reason: String, remote: Boolean) {
                Log.d(TAG, "Client disconnected: ${webSocket.remoteSocketAddress}")
                connectedClients.remove(webSocket)
                _clientCount.value = connectedClients.size
                if (connectedClients.isEmpty()) {
                    _connectionState.value = ConnectionState.Disconnected
                }
            }

            override fun onMessage(webSocket: WebSocket, message: String) {
                Log.d(TAG, "Received message: $message")
            }

            override fun onError(webSocket: WebSocket, ex: Exception) {
                Log.e(TAG, "WebSocket error: ${ex.message}", ex)
            }

            override fun onStart() {
                Log.d(TAG, "WebSocket server started")
                _connectionState.value = ConnectionState.Connected
            }
        }

        webSocketServer?.start()
        Log.d(TAG, "WebSocket server started at $url")
    }

    fun disconnect() {
        webSocketServer?.stop()
        connectedClients.clear()
        _clientCount.value = 0
        _connectionState.value = ConnectionState.Disconnected
        Log.d(TAG, "WebSocket server stopped")
    }

    fun sendHeartRate(heartRate: Int, timestamp: Long = System.currentTimeMillis()) {
        val message = """{"heartRate":$heartRate,"timestamp":$timestamp}"""
        connectedClients.forEach { client ->
            client.send(message)
        }
        Log.d(TAG, "Sent heart rate to ${connectedClients.size} clients: $message")
    }

    fun isConnected(): Boolean {
        return _connectionState.value == ConnectionState.Connected
    }

    fun updateServerAddress(address: String) {
        _serverAddress.value = address
    }

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        object Disconnecting : ConnectionState()
    }

    companion object {
        private const val TAG = "WebSocketManager"
    }
}