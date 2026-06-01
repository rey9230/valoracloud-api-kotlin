package com.valoracloud.api.servers

import com.valoracloud.api.auth.security.CurrentUser
import com.valoracloud.api.common.dto.PaginationDto
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/servers")
class ServersController(
    private val serversService: ServersService,
) {
    @GetMapping
    fun findAll(
        @CurrentUser userId: String,
        @Valid pagination: PaginationDto,
    ) = serversService.findByUser(userId, pagination)

    @GetMapping("/{id}")
    fun findOne(
        @PathVariable id: String,
        @CurrentUser userId: String,
    ) = serversService.findOne(id, userId)

    @PostMapping("/{id}/start")
    fun start(
        @PathVariable id: String,
        @CurrentUser userId: String,
    ) = serversService.start(id, userId)

    @PostMapping("/{id}/stop")
    fun stop(
        @PathVariable id: String,
        @CurrentUser userId: String,
    ) = serversService.stop(id, userId)

    @PostMapping("/{id}/restart")
    fun restart(
        @PathVariable id: String,
        @CurrentUser userId: String,
    ) = serversService.restart(id, userId)

    @PostMapping("/{id}/reinstall")
    fun reinstall(
        @PathVariable id: String,
        @CurrentUser userId: String,
        @Valid @RequestBody dto: ReinstallDto,
    ) = serversService.reinstall(id, userId, dto.imageId, dto.password)

    @GetMapping("/{id}/logs")
    fun getLogs(
        @PathVariable id: String,
        @CurrentUser userId: String,
    ) = serversService.getLogs(id, userId)

    @PatchMapping("/{id}/password")
    fun changePassword(
        @PathVariable id: String,
        @CurrentUser userId: String,
        @Valid @RequestBody dto: ChangePasswordDto,
    ) = serversService.changePassword(id, userId, dto.password)
}
