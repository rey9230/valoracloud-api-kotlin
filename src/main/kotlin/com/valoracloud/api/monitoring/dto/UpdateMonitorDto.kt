package com.valoracloud.api.monitoring.dto

import jakarta.validation.constraints.*

data class UpdateMonitorDto(
    var isActive: Boolean? = null,
    var protocol: String? = null,
    var checkUrl: String? = null,
    @field:Min(1) @field:Max(65535)
    var checkPort: Int? = null,
    @field:Min(30) @field:Max(3600)
    var checkIntervalSeconds: Int? = null,
    @field:Min(1) @field:Max(65535)
    var agentPort: Int? = null,
    var agentSecret: String? = null,
    @field:Min(50)
    var alertThresholdPingMs: Int? = null,
    @field:Min(1) @field:Max(100)
    var alertThresholdCpuPct: Int? = null,
    @field:Min(1) @field:Max(100)
    var alertThresholdRamPct: Int? = null,
    @field:Min(1) @field:Max(100)
    var alertThresholdDiskPct: Int? = null,
    @field:Email
    var notifyEmail: String? = null,
    var notifyTelegramChatId: String? = null,
    var notes: String? = null,
)
