package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CommandRow
import com.example.data.SupabaseConfigManager
import com.example.data.SupabaseManager
import com.example.service.ServiceState
import com.example.service.UssdListenerService
import com.example.utils.DeviceUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    val isServiceRunning: StateFlow<Boolean> = ServiceState.isServiceRunning
    val isForwardingEnabled: StateFlow<Boolean> = ServiceState.isForwardingEnabled
    val deviceId: StateFlow<String> = ServiceState.deviceId
    val lastExecutedCommand: StateFlow<CommandRow?> = ServiceState.lastExecutedCommand
    val logs: StateFlow<List<String>> = ServiceState.logs
    val lastCheckTime: StateFlow<String?> = ServiceState.lastCheckTime
    val batteryLevel: StateFlow<Int?> = ServiceState.batteryLevel
    val deviceModel: StateFlow<String?> = ServiceState.deviceModel
    val connectionType: StateFlow<String?> = ServiceState.connectionType
    val chargingStatus: StateFlow<String?> = ServiceState.chargingStatus
    val lastHeartbeatTime: StateFlow<String?> = ServiceState.lastHeartbeatTime

    private var heartbeatJob: Job? = null

    private val _isSupabaseConfigured = MutableStateFlow(false)
    val isSupabaseConfigured: StateFlow<Boolean> = _isSupabaseConfigured.asStateFlow()

    private val _supabaseUrl = MutableStateFlow("")
    val supabaseUrl: StateFlow<String> = _supabaseUrl.asStateFlow()

    private val _supabaseKey = MutableStateFlow("")
    val supabaseKey: StateFlow<String> = _supabaseKey.asStateFlow()

    val supabaseUrlHost: String
        get() = try {
            val url = _supabaseUrl.value.ifBlank { SupabaseManager.supabaseUrl }
            if (url.startsWith("http")) {
                java.net.URI(url).host ?: url
            } else if (url.isNotBlank()) url else "NOT SET"
        } catch (e: Exception) {
            "NOT SET"
        }

    private val _isPermissionGranted = MutableStateFlow(false)
    val isPermissionGranted: StateFlow<Boolean> = _isPermissionGranted.asStateFlow()

    private val _isSendingTestCommand = MutableStateFlow(false)
    val isSendingTestCommand: StateFlow<Boolean> = _isSendingTestCommand.asStateFlow()

    private val _testCommandResult = MutableStateFlow<String?>(null)
    val testCommandResult: StateFlow<String?> = _testCommandResult.asStateFlow()

    fun initDeviceAndHeartbeat(context: Context) {
        refreshConfig(context)
        val id = DeviceUtils.getDeviceId(context)
        ServiceState.setDeviceId(id)

        if (heartbeatJob == null || heartbeatJob?.isActive != true) {
            heartbeatJob = viewModelScope.launch {
                while (isActive) {
                    performHeartbeat(context)
                    delay(60_000)
                }
            }
        }
    }

    fun refreshConfig(context: Context) {
        SupabaseManager.init(context)
        _isSupabaseConfigured.value = SupabaseConfigManager.isConfigured(context)
        _supabaseUrl.value = SupabaseConfigManager.getSupabaseUrl(context)
        _supabaseKey.value = SupabaseConfigManager.getSupabaseKey(context)
    }

    fun saveSupabaseConfig(context: Context, url: String, key: String): Boolean {
        if (!SupabaseConfigManager.isValidHttpsUrl(url) || SupabaseConfigManager.isPlaceholderKey(key)) {
            return false
        }
        SupabaseConfigManager.saveConfig(context, url, key)
        refreshConfig(context)
        ServiceState.addLog("Supabase configuration saved: $url")
        performHeartbeat(context)
        return true
    }

    fun performHeartbeat(context: Context) {
        viewModelScope.launch {
            val id = ServiceState.deviceId.value.ifBlank { DeviceUtils.getDeviceId(context) }
            val battery = DeviceUtils.getBatteryLevel(context)
            val model = DeviceUtils.getDeviceModel()
            val connType = DeviceUtils.getConnectionType(context)
            val charging = DeviceUtils.getChargingStatus(context)
            val forwarding = isForwardingEnabled.value

            ServiceState.updateHeartbeat(battery, model, connType, charging)

            if (SupabaseManager.isConfigured) {
                SupabaseManager.updateDeviceHeartbeat(
                    deviceId = id,
                    deviceModel = model,
                    batteryLevel = battery,
                    forwardingEnabled = forwarding,
                    connectionType = connType,
                    chargingStatus = charging
                )
            }
        }
    }

    fun setForwardingEnabled(context: Context, enabled: Boolean) {
        ServiceState.setForwardingEnabled(enabled)
        if (enabled) {
            UssdListenerService.startService(context)
        } else {
            UssdListenerService.stopService(context)
        }
        performHeartbeat(context)
    }

    fun updatePermissionState(granted: Boolean) {
        _isPermissionGranted.value = granted
    }

    fun toggleService(context: Context) {
        setForwardingEnabled(context, !isForwardingEnabled.value)
    }

    fun sendTestCommand(targetNumber: String) {
        if (targetNumber.isBlank()) return
        viewModelScope.launch {
            _isSendingTestCommand.value = true
            _testCommandResult.value = null

            val result = SupabaseManager.insertTestCommand(targetNumber)
            result.onSuccess { cmd ->
                _testCommandResult.value = "Test command queued (ID: ${cmd.id ?: "new"})"
                ServiceState.addLog("Inserted test pending command for $targetNumber")
            }.onFailure { err ->
                _testCommandResult.value = "Failed to queue test command: ${err.localizedMessage}"
                ServiceState.addLog("Failed to insert test command: ${err.localizedMessage}")
            }
            _isSendingTestCommand.value = false
        }
    }

    fun clearLogs() {
        ServiceState.clearLogs()
    }
}
