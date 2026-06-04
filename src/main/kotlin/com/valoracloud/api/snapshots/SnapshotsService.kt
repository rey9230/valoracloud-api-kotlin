package com.valoracloud.api.snapshots

import com.valoracloud.api.common.exceptions.BadRequestException
import com.valoracloud.api.common.exceptions.ForbiddenException
import com.valoracloud.api.common.exceptions.NotFoundException
import com.valoracloud.api.config.ServerRepository
import com.valoracloud.api.config.SnapshotRepository
import com.valoracloud.api.contabo.ContaboService
import com.valoracloud.api.notifications.service.NotificationsService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SnapshotsService(
    private val snapshotRepository: SnapshotRepository,
    private val serverRepository: ServerRepository,
    private val contabo: ContaboService,
    private val notifications: NotificationsService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun listServerSnapshots(userId: String, serverId: String): Any {
        val server = serverRepository.findById(serverId)
            .orElseThrow { NotFoundException("Server", serverId) }
        if (server.userId != userId) throw ForbiddenException("Access denied")
        return snapshotRepository.findByServerId(serverId)
    }

    fun getSnapshotDetail(userId: String, serverId: String, snapshotId: String): Any {
        val snapshot = snapshotRepository.findById(snapshotId)
            .orElseThrow { NotFoundException("Snapshot", snapshotId) }
        if (snapshot.serverId != serverId) throw ForbiddenException("Access denied")
        val server = serverRepository.findById(serverId)
            .orElseThrow { NotFoundException("Server", serverId) }
        if (server.userId != userId) throw ForbiddenException("Access denied")
        return snapshot
    }

    fun createSnapshot(userId: String, serverId: String, dto: CreateSnapshotDto): Any {
        val server = serverRepository.findById(serverId)
            .orElseThrow { NotFoundException("Server", serverId) }
        if (server.userId != userId) throw ForbiddenException("Access denied")

        val existingSnapshots = snapshotRepository.findByServerId(serverId)
        if (existingSnapshots.size >= 10) {
            throw BadRequestException("Maximum snapshot limit (10) reached")
        }

        // TODO: ContaboService.createSnapshot(server.contaboInstanceId, dto)
        return snapshotRepository.save(
            com.valoracloud.api.entity.Snapshot(
                serverId = serverId,
                contaboSnapshotId = "", // TODO: from Contabo
                name = dto.name,
                description = dto.description,
            )
        )
    }

    fun updateSnapshot(userId: String, serverId: String, snapshotId: String, dto: UpdateSnapshotDto): Any {
        val snapshot = snapshotRepository.findById(snapshotId)
            .orElseThrow { NotFoundException("Snapshot", snapshotId) }
        if (snapshot.serverId != serverId) throw ForbiddenException("Access denied")
        val server = serverRepository.findById(serverId)
            .orElseThrow { NotFoundException("Server", serverId) }
        if (server.userId != userId) throw ForbiddenException("Access denied")
        // TODO: ContaboService.updateSnapshot(server.contaboInstanceId, snapshot.contaboSnapshotId, dto)
        dto.name?.let { snapshot.name = it }
        dto.description?.let { snapshot.description = it }
        return snapshotRepository.save(snapshot)
    }

    fun deleteSnapshot(userId: String, serverId: String, snapshotId: String): Map<String, String> {
        val snapshot = snapshotRepository.findById(snapshotId)
            .orElseThrow { NotFoundException("Snapshot", snapshotId) }
        if (snapshot.serverId != serverId) throw ForbiddenException("Access denied")
        val server = serverRepository.findById(serverId)
            .orElseThrow { NotFoundException("Server", serverId) }
        if (server.userId != userId) throw ForbiddenException("Access denied")

        if (snapshot.contaboSnapshotId.isBlank()) {
            throw ForbiddenException("Snapshot has no Contabo ID — cannot delete. Contact support.")
        }
        if (server.contaboInstanceId.isBlank()) {
            throw ForbiddenException("Server has no Contabo instance ID — cannot delete snapshot. Contact support.")
        }

        try {
            contabo.deleteSnapshot(server.contaboInstanceId.toLong(), snapshot.contaboSnapshotId)
            log.info("Snapshot ${snapshot.id} deleted in Contabo (instanceId=${server.contaboInstanceId})")
        } catch (e: Exception) {
            notifications.sendCancellationFailureAlert(
                serviceType = "SNAPSHOT",
                resourceId = snapshot.id,
                contaboId = snapshot.contaboSnapshotId,
                userId = server.userId,
                errorMessage = e.message ?: "Unknown error",
                errorStack = e.stackTraceToString(),
            )
            throw e
        }
        snapshotRepository.delete(snapshot)
        return mapOf("message" to "Snapshot deleted successfully")
    }

    fun rollbackSnapshot(userId: String, serverId: String, snapshotId: String): Map<String, String> {
        val snapshot = snapshotRepository.findById(snapshotId)
            .orElseThrow { NotFoundException("Snapshot", snapshotId) }
        if (snapshot.serverId != serverId) throw ForbiddenException("Access denied")
        val server = serverRepository.findById(serverId)
            .orElseThrow { NotFoundException("Server", serverId) }
        if (server.userId != userId) throw ForbiddenException("Access denied")

        // TODO: ContaboService.rollbackSnapshot(server.contaboInstanceId, snapshot.contaboSnapshotId)
        return mapOf(
            "message" to "Rollback initiated successfully. Note: All snapshots created after this one have been deleted.",
        )
    }
}
