package com.valoracloud.api.privatenetworks

import com.valoracloud.api.common.exceptions.BadRequestException
import com.valoracloud.api.common.exceptions.ForbiddenException
import com.valoracloud.api.common.exceptions.NotFoundException
import com.valoracloud.api.config.PrivateNetworkAssignmentRepository
import com.valoracloud.api.config.PrivateNetworkRepository
import com.valoracloud.api.config.ServerRepository
import com.valoracloud.api.contabo.ContaboService
import com.valoracloud.api.entity.PrivateNetworkAssignment
import com.valoracloud.api.notifications.service.NotificationsService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PrivateNetworksService(
    private val privateNetworkRepository: PrivateNetworkRepository,
    private val privateNetworkAssignmentRepository: PrivateNetworkAssignmentRepository,
    private val serverRepository: ServerRepository,
    private val contabo: ContaboService,
    private val notifications: NotificationsService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun listUserPrivateNetworks(userId: String) =
        privateNetworkRepository.findByUserId(userId)

    fun getPrivateNetworkDetail(userId: String, networkId: String): Any {
        val network = privateNetworkRepository.findById(networkId)
            .orElseThrow { NotFoundException("Private network", networkId) }
        if (network.userId != userId) throw ForbiddenException("Access denied")
        val assignments = privateNetworkAssignmentRepository.findByPrivateNetworkId(networkId)
        return mapOf("network" to network, "assignments" to assignments)
    }

    fun createPrivateNetwork(userId: String, dto: CreatePrivateNetworkDto): Any {
        log.info("Creating private network for user $userId: ${dto.name}")
        // TODO: ContaboService.createPrivateNetwork(dto) → returns contaboNetworkId
        return privateNetworkRepository.save(
            com.valoracloud.api.entity.PrivateNetwork(
                userId = userId,
                contaboNetworkId = "", // TODO: from Contabo
                name = dto.name,
                description = dto.description,
                region = dto.region ?: "EU",
            )
        )
    }

    fun updatePrivateNetwork(userId: String, networkId: String, dto: UpdatePrivateNetworkDto): Any {
        val network = privateNetworkRepository.findById(networkId)
            .orElseThrow { NotFoundException("Private network", networkId) }
        if (network.userId != userId) throw ForbiddenException("Access denied")
        // TODO: ContaboService.updatePrivateNetwork(network.contaboNetworkId, dto)
        if (dto.name != null) network.name = dto.name
        if (dto.description != null) network.description = dto.description
        return privateNetworkRepository.save(network)
    }

    fun deletePrivateNetwork(userId: String, networkId: String) {
        val network = privateNetworkRepository.findById(networkId)
            .orElseThrow { NotFoundException("Private network", networkId) }
        if (network.userId != userId) throw ForbiddenException("Access denied")

        val assignments = privateNetworkAssignmentRepository.findByPrivateNetworkId(networkId)
        if (assignments.isNotEmpty()) {
            throw BadRequestException("Cannot delete private network with assigned instances")
        }

        if (network.contaboNetworkId.isBlank()) {
            throw com.valoracloud.api.common.exceptions.BadRequestException(
                "Private network has no Contabo ID — cannot delete. Contact support."
            )
        }

        try {
            contabo.deletePrivateNetwork(network.contaboNetworkId.toLong())
            log.info("Private network ${network.id} deleted in Contabo (contaboId=${network.contaboNetworkId})")
        } catch (e: Exception) {
            notifications.sendCancellationFailureAlert(
                serviceType = "PRIVATE_NETWORK",
                resourceId = network.id,
                contaboId = network.contaboNetworkId,
                userId = network.userId,
                errorMessage = e.message ?: "Unknown error",
                errorStack = e.stackTraceToString(),
            )
            throw e
        }
        privateNetworkRepository.delete(network)
    }

    fun assignInstanceToNetwork(userId: String, networkId: String, serverId: String): Any {
        val network = privateNetworkRepository.findById(networkId)
            .orElseThrow { NotFoundException("Private network", networkId) }
        if (network.userId != userId) throw ForbiddenException("Access denied")

        val server = serverRepository.findById(serverId)
            .orElseThrow { NotFoundException("Server", serverId) }
        if (server.userId != userId) throw ForbiddenException("Access denied")

        if (privateNetworkAssignmentRepository.findByPrivateNetworkIdAndServerId(networkId, serverId) != null) {
            throw BadRequestException("Instance already assigned to this network")
        }

        val existingAssignments = privateNetworkAssignmentRepository.findByServerId(serverId)

        if (network.contaboNetworkId.isBlank()) {
            throw com.valoracloud.api.common.exceptions.BadRequestException(
                "Private network has no Contabo ID — cannot assign instance. Contact support."
            )
        }
        if (server.contaboInstanceId.isBlank()) {
            throw com.valoracloud.api.common.exceptions.BadRequestException(
                "Server has no Contabo instance ID — cannot assign to network. Contact support."
            )
        }

        contabo.assignInstanceToPrivateNetwork(network.contaboNetworkId.toLong(), server.contaboInstanceId.toLong())
        log.info("Server ${server.id} assigned to private network ${network.id} in Contabo")

        return privateNetworkAssignmentRepository.save(
            PrivateNetworkAssignment(
                privateNetworkId = networkId,
                serverId = serverId,
            )
        ).let {
            mapOf(
                "assignment" to it,
                "requiresReinstall" to existingAssignments.isEmpty(),
                "requiresRestart" to existingAssignments.isNotEmpty(),
            )
        }
    }

    fun unassignInstanceFromNetwork(userId: String, networkId: String, serverId: String) {
        val network = privateNetworkRepository.findById(networkId)
            .orElseThrow { NotFoundException("Private network", networkId) }
        if (network.userId != userId) throw ForbiddenException("Access denied")

        val server = serverRepository.findById(serverId)
            .orElseThrow { NotFoundException("Server", serverId) }
        if (server.userId != userId) throw ForbiddenException("Access denied")

        val assignment = privateNetworkAssignmentRepository.findByPrivateNetworkIdAndServerId(networkId, serverId)
            ?: throw NotFoundException("Assignment", "$networkId/$serverId")

        if (network.contaboNetworkId.isBlank()) {
            throw com.valoracloud.api.common.exceptions.BadRequestException(
                "Private network has no Contabo ID — cannot unassign instance. Contact support."
            )
        }

        contabo.unassignInstanceFromPrivateNetwork(network.contaboNetworkId.toLong(), server.contaboInstanceId.toLong())
        log.info("Server ${server.id} unassigned from private network ${network.id} in Contabo")

        privateNetworkAssignmentRepository.delete(assignment)
    }
}
