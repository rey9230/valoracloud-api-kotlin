package com.valoracloud.api.provisioning.service

import com.valoracloud.api.config.ProvisioningLogRepository
import com.valoracloud.api.entity.ProvisioningLog
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ProvisioningService(
    private val provisioningLogRepo: ProvisioningLogRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun logEvent(
        serverId: String,
        step: String,
        status: String, // "pending" | "success" | "error"
        message: String? = null,
    ) {
        provisioningLogRepo.save(
            ProvisioningLog(
                serverId = serverId,
                step = step,
                status = status,
                message = message,
            )
        )
        log.info("[$serverId] $step: $status ${message ?: ""}")
    }
}
