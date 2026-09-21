package com.example.data

import android.content.Context
import android.util.Log
import com.example.service.ServiceState
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.PostgresAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

object SupabaseManager {

    private const val TAG = "SupabaseManager"

    @Volatile
    private var clientInstance: SupabaseClient? = null

    @Volatile
    private var activeUrl: String = ""

    @Volatile
    private var activeKey: String = ""

    val supabaseUrl: String
        get() = activeUrl

    val supabaseKey: String
        get() = activeKey

    val isConfigured: Boolean
        get() = activeUrl.isNotBlank() &&
                SupabaseConfigManager.isValidHttpsUrl(activeUrl) &&
                !SupabaseConfigManager.isPlaceholderKey(activeKey)

    val client: SupabaseClient?
        get() = clientInstance

    @Synchronized
    fun init(context: Context) {
        val url = SupabaseConfigManager.getSupabaseUrl(context)
        val key = SupabaseConfigManager.getSupabaseKey(context)
        updateCredentials(url, key)
    }

    @Synchronized
    fun updateCredentials(url: String, key: String) {
        val cleanUrl = url.trim().trimEnd('/')
        val cleanKey = key.trim()

        if (cleanUrl == activeUrl && cleanKey == activeKey && clientInstance != null) {
            return
        }

        activeUrl = cleanUrl
        activeKey = cleanKey

        clientInstance = if (isConfigured) {
            try {
                Log.d(TAG, "Creating SupabaseClient with configured URL: $activeUrl")
                createSupabaseClient(
                    supabaseUrl = activeUrl,
                    supabaseKey = activeKey
                ) {
                    install(Postgrest)
                    install(Realtime)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing Supabase client: ${e.localizedMessage}", e)
                null
            }
        } else {
            Log.w(TAG, "Supabase client not initialized: credentials missing or invalid in local config.")
            null
        }
    }

    suspend fun fetchOldestPendingCommand(deviceId: String? = null): Result<CommandRow?> {
        val result = fetchPendingCommands(deviceId)
        return result.map { list -> list.firstOrNull() }
    }

    suspend fun fetchPendingCommands(deviceId: String? = null): Result<List<CommandRow>> {
        val supabase = client ?: return Result.failure(IllegalStateException("Supabase client is not configured"))
        return try {
            val response = supabase.postgrest["commands"]
                .select {
                    filter {
                        eq("status", "pending")
                    }
                    order("created_at", Order.ASCENDING)
                }
                .decodeList<CommandRow>()

            val filtered = response.filter { cmd ->
                cmd.device_id.isNullOrBlank() || (deviceId != null && cmd.device_id == deviceId)
            }
            Result.success(filtered)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch pending commands: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

    suspend fun updateCommandStatus(
        id: String,
        status: String,
        resultMessage: String? = null,
        executedAt: String? = null
    ): Result<Unit> {
        val supabase = client ?: return Result.failure(IllegalStateException("Supabase client is not configured"))
        return try {
            val payload = buildJsonObject {
                put("status", status)
                if (resultMessage != null) {
                    put("result", resultMessage)
                }
                if (executedAt != null) {
                    put("executed_at", executedAt)
                } else if (status == "sent" || status == "failed") {
                    put("executed_at", Instant.now().toString())
                }
            }

            Log.d(TAG, "Updating commands table for ID $id with payload: $payload")
            ServiceState.addLog("Updating commands table for ID $id with payload: $payload")

            supabase.postgrest["commands"]
                .update(payload) {
                    filter {
                        eq("id", id)
                    }
                }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update command $id: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

    suspend fun insertTestCommand(targetNumber: String): Result<CommandRow> {
        val supabase = client ?: return Result.failure(IllegalStateException("Supabase client is not configured"))
        return try {
            val newCommand = CommandRow(
                target_number = targetNumber,
                status = "pending",
                created_at = Instant.now().toString()
            )
            supabase.postgrest["commands"].insert(newCommand)
            Result.success(newCommand)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert test command: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

    suspend fun updateDeviceHeartbeat(
        deviceId: String,
        deviceModel: String,
        batteryLevel: Int,
        forwardingEnabled: Boolean,
        connectionType: String = "none",
        chargingStatus: String = "not_charging"
    ): Result<Unit> {
        val supabase = client ?: return Result.failure(IllegalStateException("Supabase client is not configured"))
        return try {
            val payload = buildJsonObject {
                put("device_id", deviceId)
                put("device_model", deviceModel)
                put("battery_level", batteryLevel)
                put("forwarding_enabled", forwardingEnabled)
                put("connection_type", connectionType)
                put("charging_status", chargingStatus)
                put("last_seen", Instant.now().toString())
            }
            supabase.postgrest["device_status"].upsert(payload)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update device heartbeat: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }
}
