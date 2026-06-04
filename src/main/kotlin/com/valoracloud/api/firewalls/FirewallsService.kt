package com.valoracloud.api.firewalls

import com.valoracloud.api.common.exceptions.BadRequestException
import com.valoracloud.api.common.exceptions.ForbiddenException
import com.valoracloud.api.common.exceptions.NotFoundException
import com.valoracloud.api.config.*
import com.valoracloud.api.contabo.ContaboService
import com.valoracloud.api.notifications.service.NotificationsService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class FirewallsService(
    private val firewallRepository: FirewallRepository,
    private val firewallRuleRepository: FirewallRuleRepository,
    private val firewallAssignmentRepository: FirewallAssignmentRepository,
    private val serverRepository: ServerRepository,
    private val contabo: ContaboService,
    private val notifications: NotificationsService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun listUserFirewalls(userId: String) =
        firewallRepository.findByUserId(userId)

    fun getFirewallDetail(userId: String, firewallId: String): Any {
        val firewall = firewallRepository.findById(firewallId)
            .orElseThrow { NotFoundException("Firewall", firewallId) }
        if (firewall.userId != userId) throw ForbiddenException("Access denied")
        val rules = firewallRuleRepository.findByFirewallId(firewallId)
        return mapOf("firewall" to firewall, "rules" to rules)
    }

    fun createFirewall(userId: String, dto: CreateFirewallDto): Any {
        log.info("Creating firewall for user $userId: ${dto.name}")
        // TODO: ContaboService.createFirewall(dto) → returns contaboFirewallId
        val firewall = firewallRepository.save(
            com.valoracloud.api.entity.Firewall(
                userId = userId,
                contaboFirewallId = "", // TODO: use Contabo response
                name = dto.name,
                description = dto.description,
                status = dto.status,
            )
        )
        dto.rules?.inbound?.forEach { rule ->
            firewallRuleRepository.save(
                com.valoracloud.api.entity.FirewallRule(
                    firewallId = firewall.id,
                    protocol = rule.protocol,
                    port = rule.port,
                    portRange = rule.portRange,
                    sourceIp = rule.sourceIp,
                    sourceNet = rule.sourceNet,
                    action = rule.action,
                )
            )
        }
        return firewall
    }

    fun updateFirewall(userId: String, firewallId: String, dto: UpdateFirewallDto): Any {
        val firewall = firewallRepository.findById(firewallId)
            .orElseThrow { NotFoundException("Firewall", firewallId) }
        if (firewall.userId != userId) throw ForbiddenException("Access denied")
        // TODO: ContaboService.updateFirewall(firewall.contaboFirewallId, dto)
        if (dto.name != null) firewall.name = dto.name
        if (dto.description != null) firewall.description = dto.description
        if (dto.status != null) firewall.status = dto.status
        return firewallRepository.save(firewall)
    }

    fun updateFirewallRules(userId: String, firewallId: String, dto: UpdateFirewallRulesDto): Any {
        val firewall = firewallRepository.findById(firewallId)
            .orElseThrow { NotFoundException("Firewall", firewallId) }
        if (firewall.userId != userId) throw ForbiddenException("Access denied")
        // TODO: ContaboService.updateFirewallRules(firewall.contaboFirewallId, dto)
        firewallRuleRepository.findByFirewallId(firewallId).forEach {
            firewallRuleRepository.delete(it)
        }
        dto.rules.inbound?.forEach { rule ->
            firewallRuleRepository.save(
                com.valoracloud.api.entity.FirewallRule(
                    firewallId = firewallId,
                    protocol = rule.protocol,
                    port = rule.port,
                    portRange = rule.portRange,
                    sourceIp = rule.sourceIp,
                    sourceNet = rule.sourceNet,
                    action = rule.action,
                )
            )
        }
        return firewallRepository.findById(firewallId).get()
    }

    fun deleteFirewall(userId: String, firewallId: String) {
        val firewall = firewallRepository.findById(firewallId)
            .orElseThrow { NotFoundException("Firewall", firewallId) }
        if (firewall.userId != userId) throw ForbiddenException("Access denied")

        val assignments = firewallAssignmentRepository.findByFirewallId(firewallId)
        if (assignments.isNotEmpty()) {
            throw BadRequestException("Cannot delete firewall with active instance assignments")
        }

        if (firewall.contaboFirewallId.isBlank()) {
            throw BadRequestException("Firewall has no Contabo ID — cannot delete. Contact support.")
        }

        try {
            contabo.deleteFirewall(firewall.contaboFirewallId)
            log.info("Firewall ${firewall.id} deleted in Contabo (contaboId=${firewall.contaboFirewallId})")
        } catch (e: Exception) {
            notifications.sendCancellationFailureAlert(
                serviceType = "FIREWALL",
                resourceId = firewall.id,
                contaboId = firewall.contaboFirewallId,
                userId = firewall.userId,
                errorMessage = e.message ?: "Unknown error",
                errorStack = e.stackTraceToString(),
            )
            throw e
        }
        firewallRepository.delete(firewall)
    }

    fun assignInstanceToFirewall(userId: String, firewallId: String, serverId: String): Any {
        val firewall = firewallRepository.findById(firewallId)
            .orElseThrow { NotFoundException("Firewall", firewallId) }
        if (firewall.userId != userId) throw ForbiddenException("Access denied")

        val server = serverRepository.findById(serverId)
            .orElseThrow { NotFoundException("Server", serverId) }
        if (server.userId != userId) throw ForbiddenException("Access denied")

        if (firewallAssignmentRepository.findByFirewallIdAndServerId(firewallId, serverId) != null) {
            throw BadRequestException("Instance already assigned to this firewall")
        }

        if (firewall.contaboFirewallId.isBlank()) {
            throw BadRequestException("Firewall has no Contabo ID — cannot assign instance. Contact support.")
        }
        if (server.contaboInstanceId.isBlank()) {
            throw BadRequestException("Server has no Contabo instance ID — cannot assign to firewall. Contact support.")
        }

        contabo.assignInstanceToFirewall(firewall.contaboFirewallId, server.contaboInstanceId.toLong())
        log.info("Server ${server.id} assigned to firewall ${firewall.id} in Contabo")

        return firewallAssignmentRepository.save(
            com.valoracloud.api.entity.FirewallAssignment(
                firewallId = firewallId,
                serverId = serverId,
            )
        )
    }

    fun unassignInstanceFromFirewall(userId: String, firewallId: String, serverId: String) {
        val firewall = firewallRepository.findById(firewallId)
            .orElseThrow { NotFoundException("Firewall", firewallId) }
        if (firewall.userId != userId) throw ForbiddenException("Access denied")

        val server = serverRepository.findById(serverId)
            .orElseThrow { NotFoundException("Server", serverId) }
        if (server.userId != userId) throw ForbiddenException("Access denied")

        val assignment = firewallAssignmentRepository.findByFirewallIdAndServerId(firewallId, serverId)
            ?: throw NotFoundException("Assignment", "$firewallId/$serverId")

        if (firewall.contaboFirewallId.isBlank()) {
            throw BadRequestException("Firewall has no Contabo ID — cannot unassign instance. Contact support.")
        }

        contabo.unassignInstanceFromFirewall(firewall.contaboFirewallId, server.contaboInstanceId.toLong())
        log.info("Server ${server.id} unassigned from firewall ${firewall.id} in Contabo")

        firewallAssignmentRepository.delete(assignment)
    }

    fun getPresetRules(): Any {
        // TODO: ContaboService.getPresetRules()
        return emptyList<Any>()
    }
}
