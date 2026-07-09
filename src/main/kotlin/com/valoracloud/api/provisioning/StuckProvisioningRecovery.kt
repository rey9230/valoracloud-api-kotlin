package com.valoracloud.api.provisioning

import com.valoracloud.api.common.model.ServerStatus
import com.valoracloud.api.config.ServerRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Provisioning and reinstalls run on in-process async threads; a container
 * restart (every deploy) kills them mid-flight and leaves servers stuck in
 * REINSTALLING/PROVISIONING forever — the user dash then hangs on
 * "Reinstalación en progreso" with no way out (real incident 2026-07-09).
 *
 * Any server still in one of those states at application startup is by
 * definition orphaned (its worker thread died with the previous process),
 * so park it in NEEDS_PROVISION for ops to finish manually or the customer
 * to retry.
 */
@Component
class StuckProvisioningRecovery(
        private val serverRepo: ServerRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun recoverOrphanedServers() {
        val stuck = serverRepo.findAll().filter {
            it.status == ServerStatus.REINSTALLING || it.status == ServerStatus.PROVISIONING
        }
        if (stuck.isEmpty()) return
        stuck.forEach { server ->
            log.warn(
                    "Server ${server.id} was stuck in ${server.status} from a previous process — marking NEEDS_PROVISION"
            )
            server.status = ServerStatus.NEEDS_PROVISION
        }
        serverRepo.saveAll(stuck)
        log.warn("Recovered ${stuck.size} orphaned server(s) to NEEDS_PROVISION")
    }
}
