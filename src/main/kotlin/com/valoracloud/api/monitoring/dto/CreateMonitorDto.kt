package com.valoracloud.api.monitoring.dto

import jakarta.validation.constraints.*

data class CreateMonitorDto(
    @field:Min(1) @field:Max(65535)
    var checkPort: Int = 80,

    @field:Min(30) @field:Max(3600)
    var checkIntervalSeconds: Int = 60,

    @field:Min(50)
    var alertThresholdPingMs: Int = 200,

    @field:Min(1) @field:Max(100)
    var alertThresholdCpuPct: Int = 85,

    @field:Min(1) @field:Max(100)
    var alertThresholdRamPct: Int = 85,

    @field:Min(1) @field:Max(100)
    var alertThresholdDiskPct: Int = 90,

    var protocol: String = "https",
    var checkUrl: String? = null,
    var agentPort: Int? = null,
    var agentSecret: String? = null,
    @field:Email
    var notifyEmail: String? = null,
    var notifyTelegramChatId: String? = null,
    var notes: String? = null,
)
