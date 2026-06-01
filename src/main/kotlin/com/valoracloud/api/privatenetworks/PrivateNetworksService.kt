package com.valoracloud.api.privatenetworks

import com.valoracloud.api.common.exceptions.BadRequestException
import com.valoracloud.api.common.exceptions.ForbiddenException
import com.valoracloud.api.common.exceptions.NotFoundException
import com.valoracloud.api.config.PrivateNetworkAssignmentRepository
import com.valoracloud.api.config.PrivateNetworkRepository
import com.valoracloud.api.config.ServerRepository
import com.valoracloud.api.entity.PrivateNetworkAssignment
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PrivateNetworksService(
    private val privateNetworkRepository: PrivateNetworkRepository,
    private val privateNetworkAssignmentRepository: PrivateNetworkAssignmentRepository,
    private val serverRepository: ServerRepository,
    // TODO: Inject ContaboService
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

        // TODO: ContaboService.deletePrivateNetwork(network.contaboNetworkId)
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

        // TODO: ContaboService.assignInstanceToPrivateNetwork(network.contaboNetworkId, server.contaboInstanceId)

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

        // TODO: ContaboService.unassignInstanceFromPrivateNetwork(network.contaboNetworkId, server.contaboInstanceId)
        privateNetworkAssignmentRepository.delete(assignment)
    }
}
