package com.valoracloud.api.firewalls

import com.valoracloud.api.auth.security.CurrentUser
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/firewalls")
class FirewallsController(
    private val firewallsService: FirewallsService,
) {
    @GetMapping
    fun listFirewalls(@CurrentUser userId: String) =
        firewallsService.listUserFirewalls(userId)

    @GetMapping("/preset-rules")
    fun getPresetRules() = firewallsService.getPresetRules()

    @GetMapping("/{firewallId}")
    fun getFirewall(
        @CurrentUser userId: String,
        @PathVariable firewallId: String,
    ) = firewallsService.getFirewallDetail(userId, firewallId)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createFirewall(
        @CurrentUser userId: String,
        @Valid @RequestBody dto: CreateFirewallDto,
    ) = firewallsService.createFirewall(userId, dto)

    @PatchMapping("/{firewallId}")
    fun updateFirewall(
        @CurrentUser userId: String,
        @PathVariable firewallId: String,
        @Valid @RequestBody dto: UpdateFirewallDto,
    ) = firewallsService.updateFirewall(userId, firewallId, dto)

    @PutMapping("/{firewallId}/rules")
    fun updateFirewallRules(
        @CurrentUser userId: String,
        @PathVariable firewallId: String,
        @Valid @RequestBody dto: UpdateFirewallRulesDto,
    ) = firewallsService.updateFirewallRules(userId, firewallId, dto)

    @DeleteMapping("/{firewallId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteFirewall(
        @CurrentUser userId: String,
        @PathVariable firewallId: String,
    ) = firewallsService.deleteFirewall(userId, firewallId)

    @PostMapping("/{firewallId}/instances/{serverId}")
    @ResponseStatus(HttpStatus.CREATED)
    fun assignInstance(
        @CurrentUser userId: String,
        @PathVariable firewallId: String,
        @PathVariable serverId: String,
    ) = firewallsService.assignInstanceToFirewall(userId, firewallId, serverId)

    @DeleteMapping("/{firewallId}/instances/{serverId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unassignInstance(
        @CurrentUser userId: String,
        @PathVariable firewallId: String,
        @PathVariable serverId: String,
    ) = firewallsService.unassignInstanceFromFirewall(userId, firewallId, serverId)
}
