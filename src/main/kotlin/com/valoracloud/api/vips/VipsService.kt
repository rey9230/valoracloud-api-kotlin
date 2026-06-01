package com.valoracloud.api.vips

import com.valoracloud.api.common.exceptions.BadRequestException
import com.valoracloud.api.common.exceptions.ForbiddenException
import com.valoracloud.api.common.exceptions.NotFoundException
import com.valoracloud.api.config.ServerRepository
import com.valoracloud.api.config.VipRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class VipsService(
    private val vipRepository: VipRepository,
    private val serverRepository: ServerRepository,
    // TODO: Inject ContaboService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun listUserVips(userId: String) = vipRepository.findByUserId(userId)

    fun getVipDetail(userId: String, ip: String): Any {
        val vip = vipRepository.findByIp(ip)
            ?: throw NotFoundException("VIP", ip)
        if (vip.userId != userId) throw ForbiddenException("Access denied")
        return vip
    }

    fun syncVipsFromContabo(userId: String): Map<String, Int> {
        // TODO: ContaboService.listVips() → upsert into DB
        return mapOf("synced" to 0)
    }

    fun assignVipToServer(userId: String, ip: String, serverId: String) {
        val vip = vipRepository.findByIp(ip)
            ?: throw NotFoundException("VIP", ip)
        if (vip.userId != userId) throw ForbiddenException("Access denied")

        val server = serverRepository.findById(serverId)
            .orElseThrow { NotFoundException("Server", serverId) }
        if (server.userId != userId) throw ForbiddenException("Access denied")

        if (vip.resourceId != null && vip.resourceType != null) {
            throw BadRequestException("VIP $ip is already assigned")
        }

        // TODO: ContaboService.assignVipToInstance(ip, "instances", server.contaboInstanceId)

        vip.resourceId = server.contaboInstanceId
        vip.resourceType = "instances"
        vipRepository.save(vip)
    }

    fun unassignVipFromServer(userId: String, ip: String, serverId: String) {
        val vip = vipRepository.findByIp(ip)
            ?: throw NotFoundException("VIP", ip)
        if (vip.userId != userId) throw ForbiddenException("Access denied")

        val server = serverRepository.findById(serverId)
            .orElseThrow { NotFoundException("Server", serverId) }
        if (server.userId != userId) throw ForbiddenException("Access denied")

        if (vip.resourceId != server.contaboInstanceId || vip.resourceType != "instances") {
            throw BadRequestException("VIP $ip is not assigned to server $serverId")
        }

        // TODO: ContaboService.unassignVipFromInstance(ip, "instances", server.contaboInstanceId)

        vip.resourceId = null
        vip.resourceType = null
        vipRepository.save(vip)
    }
}
