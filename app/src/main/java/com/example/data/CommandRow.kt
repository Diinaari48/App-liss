package com.example.data

import kotlinx.serialization.Serializable

@Serializable
data class CommandRow(
    val id: String? = null,
    val device_id: String? = null,
    val target_number: String = "",
    val status: String = "pending",
    val created_at: String? = null,
    val executed_at: String? = null,
    val result: String? = null
)
