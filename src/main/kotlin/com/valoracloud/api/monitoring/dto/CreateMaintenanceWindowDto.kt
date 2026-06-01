package com.valoracloud.api.monitoring.dto

data class CreateMaintenanceWindowDto(
    val title: String,
    val startsAt: String,
    val endsAt: String,
    val suppressAlerts: Boolean = true,
)
