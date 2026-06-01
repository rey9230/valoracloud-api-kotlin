package com.valoracloud.api.servers

import com.valoracloud.api.common.dto.PaginatedResponse
import com.valoracloud.api.common.dto.PaginationDto
import com.valoracloud.api.common.exceptions.ForbiddenException
import com.valoracloud.api.common.exceptions.NotFoundException
import com.valoracloud.api.config.ProvisioningLogRepository
import com.valoracloud.api.config.ServerRepository
import com.valoracloud.api.entity.Server
import org.springframework.stereotype.Service

@Service
class ServersService(
    private val serverRepository: ServerRepository,
    private val provisioningLogRepository: ProvisioningLogRepository,
    // TODO: Inject ContaboService, EncryptionUtil, NotificationsService, NodeSSH client
) {
    fun findByUser(userId: String, dto: PaginationDto): PaginatedResponse<Map<String, Any?>> {
        val servers = serverRepository.findByUserIdOrderByCreatedAtDesc(userId)
        val page = dto.page; val limit = dto.limit
        val offset = dto.offset
        val paged = servers.drop(offset).take(limit)

        val data = paged.map { server ->
            mapOf(
                "id" to server.id,
                "hostname" to server.hostname,
                "ipAddress" to server.ipAddress,
                "status" to server.status,
                "os" to server.os,
                "region" to server.region,
                "provisionedAt" to server.provisionedAt,
                "expiresAt" to server.expiresAt,
                "createdAt" to server.createdAt,
            )
        }

        val totalPages = if (limit > 0) (servers.size + limit - 1) / limit else 1
        return PaginatedResponse(data, servers.size.toLong(), page, limit, totalPages)
    }

    fun findOne(serverId: String, userId: String): Map<String, Any?> {
        val server = serverRepository.findById(serverId)
            .orElseThrow { NotFoundException("Server", serverId) }
        if (server.userId != userId) throw ForbiddenException("Access denied")

        return mapOf(
            "id" to server.id,
            "hostname" to server.hostname,
            "ipAddress" to server.ipAddress,
            "status" to server.status,
            "os" to server.os,
            "region" to server.region,
            "provisionedAt" to server.provisionedAt,
            "expiresAt" to server.expiresAt,
            "createdAt" to server.createdAt,
        )
    }

    fun start(serverId: String, userId: String): Map<String, String> {
        val server = getServerForAction(serverId, userId)
        // TODO: ContaboService.startInstance(server.contaboInstanceId)
        server.status = com.valoracloud.api.common.model.ServerStatus.RUNNING
        serverRepository.save(server)
        return mapOf("message" to "Server starting")
    }

    fun stop(serverId: String, userId: String): Map<String, String> {
        val server = getServerForAction(serverId, userId)
        // TODO: ContaboService.stopInstance(server.contaboInstanceId)
        server.status = com.valoracloud.api.common.model.ServerStatus.STOPPED
        serverRepository.save(server)
        return mapOf("message" to "Server stopping")
    }

    fun restart(serverId: String, userId: String): Map<String, String> {
        val server = getServerForAction(serverId, userId)
        // TODO: ContaboService.restartInstance(server.contaboInstanceId)
        return mapOf("message" to "Server restarting")
    }

    fun reinstall(serverId: String, userId: String, imageId: String, password: String?): Map<String, String> {
        val server = getServerForAction(serverId, userId)
        // TODO: Full reinstall flow with Contabo, post-provisioning SSH
        return mapOf("message" to "Server reinstall initiated")
    }

    fun getLogs(serverId: String, userId: String): List<Any> {
        val server = serverRepository.findById(serverId)
            .orElseThrow { NotFoundException("Server", serverId) }
        if (server.userId != userId) throw ForbiddenException("Access denied")
        return provisioningLogRepository.findByServerIdOrderByCreatedAtAsc(serverId)
    }

    fun changePassword(serverId: String, userId: String, newPassword: String): Map<String, String> {
        val server = getServerForAction(serverId, userId)
        // TODO: SSH into server and change password via chpasswd, then encrypt & save
        return mapOf("message" to "Password changed successfully")
    }

    private fun getServerForAction(serverId: String, userId: String): Server {
        val server = serverRepository.findById(serverId)
            .orElseThrow { NotFoundException("Server", serverId) }
        if (server.userId != userId) throw ForbiddenException("Access denied")
        if (server.status == com.valoracloud.api.common.model.ServerStatus.SUSPENDED) {
            throw ForbiddenException("Server is suspended. Contact support to reactivate your service.")
        }
        return server
    }
}
