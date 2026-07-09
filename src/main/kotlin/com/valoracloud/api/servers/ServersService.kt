package com.valoracloud.api.servers

import com.valoracloud.api.common.dto.PaginatedResponse
import com.valoracloud.api.common.dto.PaginationDto
import com.valoracloud.api.common.exceptions.BadRequestException
import com.valoracloud.api.common.exceptions.ForbiddenException
import com.valoracloud.api.common.exceptions.NotFoundException
import com.valoracloud.api.common.model.ServerStatus
import com.valoracloud.api.common.utils.EncryptionUtil
import com.valoracloud.api.config.ProvisioningLogRepository
import com.valoracloud.api.config.ServerRepository
import com.valoracloud.api.contabo.ContaboCreateSecretRequest
import com.valoracloud.api.contabo.ContaboSecretType
import com.valoracloud.api.contabo.ContaboService
import com.valoracloud.api.entity.Server
import com.valoracloud.api.notifications.service.NotificationsService
import com.valoracloud.api.provisioning.ProvisioningDefaults
import com.valoracloud.api.provisioning.processor.ProvisioningProcessor
import java.util.Base64
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class ServersService(
    private val serverRepository: ServerRepository,
    private val provisioningLogRepository: ProvisioningLogRepository,
    private val contabo: ContaboService,
    private val notifications: NotificationsService,
    private val provisioningProcessor: ProvisioningProcessor,
    @Value("\${app.encryption-key:}") private val encryptionKey: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun findByUser(userId: String, dto: PaginationDto): PaginatedResponse<Map<String, Any?>> {
        // Dead servers are noise for the customer: a server cancelled a year ago (or a
        // failed provision they were never charged for) has no place in their dashboard.
        val servers = serverRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .filter { it.status != ServerStatus.CANCELLED && it.status != ServerStatus.ERROR }
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
                "sshUser" to server.sshUser,
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
            "sshUser" to server.sshUser,
            "provisionedAt" to server.provisionedAt,
            "expiresAt" to server.expiresAt,
            "createdAt" to server.createdAt,
        )
    }

    fun cancel(serverId: String, userId: String): Map<String, String> {
        val server = serverRepository.findById(serverId)
            .orElseThrow { NotFoundException("Server", serverId) }
        if (server.userId != userId) throw ForbiddenException("Access denied")

        if (server.status == ServerStatus.CANCELLED) {
            return mapOf("message" to "Server is already cancelled")
        }

        if (server.contaboInstanceId.isBlank()) {
            throw com.valoracloud.api.common.exceptions.AppException(
                org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                "Server has no Contabo instance ID — cannot cancel. Contact support."
            )
        }

        try {
            contabo.cancelInstance(server.contaboInstanceId.toLong())
            log.info("Server ${server.id} cancellation scheduled in Contabo (instanceId=${server.contaboInstanceId})")
        } catch (e: Exception) {
            notifications.sendCancellationFailureAlert(
                serviceType = "VPS",
                resourceId = server.id,
                contaboId = server.contaboInstanceId,
                userId = server.userId,
                errorMessage = e.message ?: "Unknown error",
                errorStack = e.stackTraceToString(),
            )
            throw e
        }

        server.status = ServerStatus.CANCELLED
        serverRepository.save(server)

        return mapOf("message" to "Server cancellation scheduled. It will remain active until end of billing period.")
    }

    fun start(serverId: String, userId: String): Map<String, String> {
        val server = getServerForAction(serverId, userId)
        contabo.startInstance(server.contaboInstanceId.toLong())
        server.status = ServerStatus.RUNNING
        serverRepository.save(server)
        return mapOf("message" to "Server starting")
    }

    fun stop(serverId: String, userId: String): Map<String, String> {
        val server = getServerForAction(serverId, userId)
        contabo.stopInstance(server.contaboInstanceId.toLong())
        server.status = ServerStatus.STOPPED
        serverRepository.save(server)
        return mapOf("message" to "Server stopping")
    }

    fun restart(serverId: String, userId: String): Map<String, String> {
        val server = getServerForAction(serverId, userId)
        contabo.restartInstance(server.contaboInstanceId.toLong())
        return mapOf("message" to "Server restarting")
    }

    fun reinstall(serverId: String, userId: String, imageId: String, password: String?): Map<String, String> {
        val server = getServerForAction(serverId, userId)
        if (server.status == ServerStatus.REINSTALLING) {
            throw ForbiddenException("A reinstall is already in progress for this server")
        }

        // Password: use the one provided, or keep the current one
        val newPassword = password?.trim()?.takeIf { it.isNotEmpty() }
            ?: if (encryptionKey.isNotBlank()) EncryptionUtil.decrypt(server.rootPassword, encryptionKey)
               else server.rootPassword
        if (newPassword.length !in 8..72) {
            throw BadRequestException("Password must be between 8 and 72 characters")
        }

        // Resolve the image to determine the login user (Linux → root, Windows → administrator)
        val image = try {
            contabo.getImage(imageId)
        } catch (e: Exception) {
            throw BadRequestException("Unknown image: $imageId")
        }
        val sshUser = ProvisioningDefaults.sshUserFor(ProvisioningDefaults.isWindows(image.name, image.osType))

        // Contabo secret so the reinstall sets the password, plus cloud-init as belt-and-braces
        var secretId: Long? = null
        try {
            val secret = contabo.createSecret(
                ContaboCreateSecretRequest(
                    name = "reinstall-${serverId.take(8)}",
                    type = ContaboSecretType.password,
                    value = newPassword,
                )
            )
            secretId = secret.secretId
        } catch (e: Exception) {
            log.warn("Could not create reinstall secret: ${e.message}")
        }
        val cloudInitB64 = Base64.getEncoder().encodeToString(
            ProvisioningProcessor.buildCloudInit(newPassword, sshUser).toByteArray()
        )

        contabo.reinstallInstance(
            instanceId = server.contaboInstanceId.toLong(),
            imageId = image.imageId,
            rootPassword = secretId,
            userData = cloudInitB64,
            defaultUser = sshUser,
        )

        server.status = ServerStatus.REINSTALLING
        server.os = image.imageId
        server.sshUser = sshUser
        server.rootPassword = if (encryptionKey.isNotBlank()) EncryptionUtil.encrypt(newPassword, encryptionKey) else newPassword
        serverRepository.save(server)
        log.info("Reinstall started for server $serverId → image=${image.name} user=$sshUser")

        // Poll + finish asynchronously; the endpoint returns right away
        provisioningProcessor.finalizeReinstall(serverId, secretId, newPassword)

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

    /**
     * Credentials and console are only exposed once the server is RUNNING — i.e.
     * fully provisioned AND re-branded. Before that the box may still show the
     * upstream provider, and the customer must never reach it.
     */
    fun credentials(serverId: String, userId: String): Map<String, Any?> {
        val server = getServerForAction(serverId, userId)
        requireRunning(server)
        return mapOf(
            "id" to server.id,
            "hostname" to server.hostname,
            "ipAddress" to server.ipAddress,
            "sshUser" to server.sshUser,
            "rootPassword" to
                if (encryptionKey.isNotBlank()) EncryptionUtil.decrypt(server.rootPassword, encryptionKey)
                else server.rootPassword,
        )
    }

    fun console(serverId: String, userId: String): Map<String, Any?> {
        val server = getServerForAction(serverId, userId)
        requireRunning(server)
        val vnc = contabo.getVncAccess(server.contaboInstanceId.toLong())
        return mapOf("hostname" to server.hostname, "ipAddress" to server.ipAddress, "vnc" to vnc)
    }

    private fun requireRunning(server: Server) {
        if (server.status != ServerStatus.RUNNING) {
            throw ForbiddenException(
                "Your server is being prepared. Access details will be available once it is ready."
            )
        }
    }

    private fun getServerForAction(serverId: String, userId: String): Server {
        val server = serverRepository.findById(serverId)
            .orElseThrow { NotFoundException("Server", serverId) }
        if (server.userId != userId) throw ForbiddenException("Access denied")
        if (server.status == ServerStatus.SUSPENDED) {
            throw ForbiddenException("Server is suspended. Contact support to reactivate your service.")
        }
        return server
    }
}
