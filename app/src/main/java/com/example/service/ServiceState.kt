package com.example.service

import com.example.data.CommandRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object ServiceState {
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _isForwardingEnabled = MutableStateFlow(false)
    val isForwardingEnabled: StateFlow<Boolean> = _isForwardingEnabled.asStateFlow()

    private val _deviceId = MutableStateFlow("")
    val deviceId: StateFlow<String> = _deviceId.asStateFlow()

    private val _lastExecutedCommand = MutableStateFlow<CommandRow?>(null)
    val lastExecutedCommand: StateFlow<CommandRow?> = _lastExecutedCommand.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _lastCheckTime = MutableStateFlow<String?>("-")
    val lastCheckTime: StateFlow<String?> = _lastCheckTime.asStateFlow()

    private val _batteryLevel = MutableStateFlow<Int?>(-1)
    val batteryLevel: StateFlow<Int?> = _batteryLevel.asStateFlow()

    private val _deviceModel = MutableStateFlow<String?>("-")
    val deviceModel: StateFlow<String?> = _deviceModel.asStateFlow()

    private val _connectionType = MutableStateFlow<String?>("none")
    val connectionType: StateFlow<String?> = _connectionType.asStateFlow()

    private val _chargingStatus = MutableStateFlow<String?>("not_charging")
    val chargingStatus: StateFlow<String?> = _chargingStatus.asStateFlow()

    private val _lastHeartbeatTime = MutableStateFlow<String?>("-")
    val lastHeartbeatTime: StateFlow<String?> = _lastHeartbeatTime.asStateFlow()

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun setDeviceId(id: String) {
        _deviceId.value = id
    }

    fun setForwardingEnabled(enabled: Boolean) {
        _isForwardingEnabled.value = enabled
    }

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
        if (running) {
            addLog("Command execution foreground service started.")
        } else {
            addLog("Command execution foreground service stopped.")
        }
    }

    fun updateLastExecuted(command: CommandRow) {
        _lastExecutedCommand.value = command
    }

    fun updateLastCheckTime() {
        _lastCheckTime.value = LocalTime.now().format(timeFormatter)
    }

    fun updateHeartbeat(battery: Int, model: String, connType: String = "none", charging: String = "not_charging") {
        _batteryLevel.value = battery
        _deviceModel.value = model
        _connectionType.value = connType
        _chargingStatus.value = charging
        _lastHeartbeatTime.value = LocalTime.now().format(timeFormatter)
    }

    fun addLog(message: String) {
        val timestamp = LocalTime.now().format(timeFormatter)
        val formattedMsg = "[$timestamp] $message"
        val current = _logs.value.toMutableList()
        current.add(0, formattedMsg) // Most recent at top
        if (current.size > 100) {
            current.removeAt(current.lastIndex)
        }
        _logs.value = current
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
