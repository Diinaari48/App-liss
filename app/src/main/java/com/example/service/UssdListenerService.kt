package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.CommandRow
import com.example.data.SupabaseManager
import com.example.utils.DeviceUtils
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import kotlin.coroutines.resume

class UssdListenerService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var pollingJob: Job? = null
    private var realtimeJob: Job? = null
    private var heartbeatJob: Job? = null

    companion object {
        private const val TAG = "UssdListenerService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "ussd_command_listener_channel"

        const val ACTION_START = "com.example.action.START_SERVICE"
        const val ACTION_STOP = "com.example.action.STOP_SERVICE"

        fun startService(context: Context) {
            val intent = Intent(context, UssdListenerService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, UssdListenerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        SupabaseManager.init(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.d(TAG, "Stopping UssdListenerService via action")
                stopForegroundService()
                return START_NOT_STICKY
            }
            else -> {
                Log.d(TAG, "Starting UssdListenerService")
                startForegroundNotification()
                ServiceState.setServiceRunning(true)
                startCommandListener()
                return START_STICKY
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        ServiceState.setServiceRunning(false)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun stopForegroundService() {
        ServiceState.setServiceRunning(false)
        pollingJob?.cancel()
        realtimeJob?.cancel()
        heartbeatJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Command Listener Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors Supabase for call forwarding USSD commands"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, UssdListenerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Command Listener Active")
            .setContentText("Listening for pending call-forwarding commands...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Stop Service", stopPendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startCommandListener() {
        // 1. Fallback periodic polling every 30 seconds
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            while (isActive) {
                try {
                    ServiceState.updateLastCheckTime()
                    checkAndExecuteNextPendingCommand()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in periodic polling loop: ${e.localizedMessage}", e)
                    ServiceState.addLog("Polling error: ${e.localizedMessage}")
                }
                delay(30_000) // 30s safety net interval
            }
        }

        // 2. Heartbeat periodic loop every 60 seconds
        startHeartbeatLoop()

        // 3. Realtime subscription for immediate trigger
        setupRealtimeSubscription()
    }

    private fun startHeartbeatLoop() {
        heartbeatJob?.cancel()
        heartbeatJob = serviceScope.launch {
            while (isActive) {
                try {
                    val deviceId = DeviceUtils.getDeviceId(this@UssdListenerService)
                    val battery = DeviceUtils.getBatteryLevel(this@UssdListenerService)
                    val model = DeviceUtils.getDeviceModel()
                    val connType = DeviceUtils.getConnectionType(this@UssdListenerService)
                    val charging = DeviceUtils.getChargingStatus(this@UssdListenerService)
                    val forwardingEnabled = ServiceState.isForwardingEnabled.value

                    ServiceState.updateHeartbeat(battery, model, connType, charging)

                    if (SupabaseManager.isConfigured) {
                        SupabaseManager.updateDeviceHeartbeat(
                            deviceId = deviceId,
                            deviceModel = model,
                            batteryLevel = battery,
                            forwardingEnabled = forwardingEnabled,
                            connectionType = connType,
                            chargingStatus = charging
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Heartbeat error: ${e.localizedMessage}", e)
                }
                delay(60_000) // 60s interval
            }
        }
    }

    private fun setupRealtimeSubscription() {
        realtimeJob?.cancel()
        realtimeJob = serviceScope.launch {
            val supabase = SupabaseManager.client ?: run {
                ServiceState.addLog("Realtime listener disabled: Supabase not configured.")
                return@launch
            }

            try {
                ServiceState.addLog("Subscribing to Supabase Realtime INSERT events...")
                val channel = supabase.channel("commands_realtime")
                val changeFlow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                    table = "commands"
                }

                changeFlow.onEach { action ->
                    Log.d(TAG, "Realtime INSERT event received: ${action.record}")
                    ServiceState.addLog("Realtime INSERT event received! Processing queue...")
                    checkAndExecuteNextPendingCommand()
                }.launchIn(this)

                channel.subscribe()
                ServiceState.addLog("Realtime subscription active.")
            } catch (e: Exception) {
                Log.e(TAG, "Realtime setup notice: ${e.localizedMessage}", e)
                ServiceState.addLog("Realtime setup notice: ${e.localizedMessage}")
            }
        }
    }

    private val isProcessingMutex = java.util.concurrent.atomic.AtomicBoolean(false)

    private suspend fun checkAndExecuteNextPendingCommand() {
        if (!SupabaseManager.isConfigured) {
            ServiceState.addLog("Skipping check: SUPABASE_URL / SUPABASE_ANON_KEY missing.")
            return
        }

        if (!isProcessingMutex.compareAndSet(false, true)) {
            ServiceState.addLog("Queue processing already in progress. Skipping duplicate trigger.")
            return
        }

        val deviceId = DeviceUtils.getDeviceId(this@UssdListenerService)

        try {
            val processedInThisBatch = mutableSetOf<String>()

            while (serviceScope.isActive) {
                val fetchResult = SupabaseManager.fetchPendingCommands(deviceId)
                val pendingList = fetchResult.getOrNull() ?: emptyList()

                val queryLog = if (pendingList.isEmpty()) {
                    "Pending commands query returned 0 rows"
                } else {
                    "Pending commands query returned ${pendingList.size} row(s): [${pendingList.joinToString { it.target_number }}]"
                }
                ServiceState.addLog(queryLog)

                if (pendingList.isEmpty()) {
                    break
                }

                // Pick oldest pending command not already handled in this batch loop
                val command = pendingList.firstOrNull { cmd ->
                    cmd.id != null && !processedInThisBatch.contains(cmd.id)
                }

                if (command == null) {
                    break
                }

                val commandId = command.id!!
                processedInThisBatch.add(commandId)

                try {
                    processSingleCommand(command)
                } catch (e: Exception) {
                    Log.e(TAG, "Error executing command $commandId: ${e.localizedMessage}", e)
                    ServiceState.addLog("Error executing command $commandId: ${e.localizedMessage}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Queue processing error: ${e.localizedMessage}", e)
            ServiceState.addLog("Queue processing error: ${e.localizedMessage}")
        } finally {
            isProcessingMutex.set(false)
        }
    }

    private suspend fun processSingleCommand(command: CommandRow) {
        val commandId = command.id ?: return
        val targetNumber = command.target_number.trim()

        // 1. Mark status = "processing" immediately before executing
        ServiceState.addLog("Updating command $commandId status to 'processing'...")
        val updateProcessing = SupabaseManager.updateCommandStatus(
            id = commandId,
            status = "processing",
            resultMessage = "Processing command..."
        )
        if (updateProcessing.isFailure) {
            val errMessage = updateProcessing.exceptionOrNull()?.localizedMessage ?: "Unknown error"
            ServiceState.addLog("Notice: Status update to 'processing' returned error ($errMessage). Proceeding with USSD execution.")
        }

        // Build USSD Code string
        val ussdCode = if (targetNumber.startsWith("*") && targetNumber.endsWith("#")) {
            targetNumber
        } else {
            "*21*$targetNumber#"
        }

        // 2. Validate strict pattern: starts with "*" and ends with "#"
        if (!ussdCode.startsWith("*") || !ussdCode.endsWith("#")) {
            val errMsg = "invalid USSD format"
            ServiceState.addLog("USSD Format Error: '$ussdCode' does not match USSD pattern. Aborting.")
            
            val failedCmd = command.copy(
                status = "failed",
                result = errMsg,
                executed_at = Instant.now().toString()
            )
            ServiceState.updateLastExecuted(failedCmd)
            SupabaseManager.updateCommandStatus(
                id = commandId,
                status = "failed",
                resultMessage = errMsg
            )
            return
        }

        // 3. Permission check
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            val errMsg = "Error: CALL_PHONE permission not granted on device"
            ServiceState.addLog(errMsg)
            val failedCmd = command.copy(
                status = "failed",
                result = errMsg,
                executed_at = Instant.now().toString()
            )
            ServiceState.updateLastExecuted(failedCmd)
            SupabaseManager.updateCommandStatus(
                id = commandId,
                status = "failed",
                resultMessage = errMsg
            )
            return
        }

        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        if (telephonyManager == null) {
            val errMsg = "Error: TelephonyManager unavailable on device"
            ServiceState.addLog(errMsg)
            val failedCmd = command.copy(
                status = "failed",
                result = errMsg,
                executed_at = Instant.now().toString()
            )
            ServiceState.updateLastExecuted(failedCmd)
            SupabaseManager.updateCommandStatus(
                id = commandId,
                status = "failed",
                resultMessage = errMsg
            )
            return
        }

        if (telephonyManager.simState != TelephonyManager.SIM_STATE_READY) {
            ServiceState.addLog("Warning: SIM card not ready (simState=${telephonyManager.simState})")
        }

        // Log exact string being sent to sendUssdRequest()
        ServiceState.addLog("Sending USSD request to TelephonyManager: '$ussdCode'")

        // 4. Execute USSD Request via TelephonyManager.sendUssdRequest()
        val (success, responseMessage) = try {
            withTimeoutOrNull(15_000) {
                executeUssd(telephonyManager, ussdCode)
            } ?: Pair(false, "USSD execution timed out (15s)")
        } catch (e: Exception) {
            Pair(false, "Exception executing USSD: ${e.localizedMessage}")
        }

        val finalStatus = if (success) "sent" else "failed"
        val logMsg = "USSD Result [$finalStatus]: $responseMessage"
        ServiceState.addLog(logMsg)

        val updatedCmd = command.copy(
            status = finalStatus,
            result = responseMessage,
            executed_at = Instant.now().toString()
        )
        ServiceState.updateLastExecuted(updatedCmd)

        // 5. Update Supabase Database Row
        SupabaseManager.updateCommandStatus(
            id = commandId,
            status = finalStatus,
            resultMessage = responseMessage
        )
    }


    private suspend fun executeUssd(
        telephonyManager: TelephonyManager,
        ussdCode: String
    ): Pair<Boolean, String> = suspendCancellableCoroutine { continuation ->
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            continuation.resume(Pair(false, "API level < 26 not supported for sendUssdRequest"))
            return@suspendCancellableCoroutine
        }

        val callback = object : TelephonyManager.UssdResponseCallback() {
            override fun onReceiveUssdResponse(
                telephonyManager: TelephonyManager?,
                request: String?,
                response: CharSequence?
            ) {
                if (continuation.isActive) {
                    val resp = response?.toString() ?: "USSD command code sent successfully"
                    continuation.resume(Pair(true, resp))
                }
            }

            override fun onReceiveUssdResponseFailed(
                telephonyManager: TelephonyManager?,
                request: String?,
                failureCode: Int
            ) {
                if (continuation.isActive) {
                    val reason = if (failureCode == TelephonyManager.USSD_RETURN_FAILURE) {
                        "Carrier returned failure (-1)"
                    } else {
                        "USSD failed with code: $failureCode"
                    }
                    continuation.resume(Pair(false, reason))
                }
            }
        }

        try {
            ServiceState.addLog("Executing TelephonyManager.sendUssdRequest('$ussdCode')")

            telephonyManager.sendUssdRequest(
                ussdCode,
                callback,
                Handler(Looper.getMainLooper())
            )
        } catch (e: SecurityException) {
            if (continuation.isActive) {
                continuation.resume(Pair(false, "SecurityException: CALL_PHONE permission required"))
            }
        } catch (e: Exception) {
            if (continuation.isActive) {
                continuation.resume(Pair(false, "Telephony Exception: ${e.localizedMessage}"))
            }
        }
    }
}
