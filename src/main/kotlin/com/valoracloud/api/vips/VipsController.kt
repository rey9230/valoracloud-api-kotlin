package com.valoracloud.api.vips

import com.valoracloud.api.auth.security.CurrentUser
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/vips")
class VipsController(
    private val vipsService: VipsService,
) {
    @GetMapping
    fun listVips(@CurrentUser userId: String) = vipsService.listUserVips(userId)

    @GetMapping("/{ip}")
    fun getVip(
        @CurrentUser userId: String,
        @PathVariable ip: String,
    ) = vipsService.getVipDetail(userId, ip)

    @PostMapping("/sync")
    fun syncVips(@CurrentUser userId: String) = vipsService.syncVipsFromContabo(userId)

    @PostMapping("/{ip}/servers/{serverId}")
    @ResponseStatus(HttpStatus.CREATED)
    fun assignVip(
        @CurrentUser userId: String,
        @PathVariable ip: String,
        @PathVariable serverId: String,
    ): Map<String, String> {
        vipsService.assignVipToServer(userId, ip, serverId)
        return mapOf("message" to "VIP assigned successfully")
    }

    @DeleteMapping("/{ip}/servers/{serverId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unassignVip(
        @CurrentUser userId: String,
        @PathVariable ip: String,
        @PathVariable serverId: String,
    ) = vipsService.unassignVipFromServer(userId, ip, serverId)
}
