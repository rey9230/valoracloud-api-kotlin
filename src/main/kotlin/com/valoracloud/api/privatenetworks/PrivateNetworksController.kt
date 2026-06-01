package com.valoracloud.api.privatenetworks

import com.valoracloud.api.auth.security.CurrentUser
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/private-networks")
class PrivateNetworksController(
    private val privateNetworksService: PrivateNetworksService,
) {
    @GetMapping
    fun listPrivateNetworks(@CurrentUser userId: String) =
        privateNetworksService.listUserPrivateNetworks(userId)

    @GetMapping("/{networkId}")
    fun getPrivateNetwork(
        @CurrentUser userId: String,
        @PathVariable networkId: String,
    ) = privateNetworksService.getPrivateNetworkDetail(userId, networkId)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createPrivateNetwork(
        @CurrentUser userId: String,
        @Valid @RequestBody dto: CreatePrivateNetworkDto,
    ) = privateNetworksService.createPrivateNetwork(userId, dto)

    @PatchMapping("/{networkId}")
    fun updatePrivateNetwork(
        @CurrentUser userId: String,
        @PathVariable networkId: String,
        @Valid @RequestBody dto: UpdatePrivateNetworkDto,
    ) = privateNetworksService.updatePrivateNetwork(userId, networkId, dto)

    @DeleteMapping("/{networkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletePrivateNetwork(
        @CurrentUser userId: String,
        @PathVariable networkId: String,
    ) = privateNetworksService.deletePrivateNetwork(userId, networkId)

    @PostMapping("/{networkId}/instances/{serverId}")
    @ResponseStatus(HttpStatus.CREATED)
    fun assignInstance(
        @CurrentUser userId: String,
        @PathVariable networkId: String,
        @PathVariable serverId: String,
    ) = privateNetworksService.assignInstanceToNetwork(userId, networkId, serverId)

    @DeleteMapping("/{networkId}/instances/{serverId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unassignInstance(
        @CurrentUser userId: String,
        @PathVariable networkId: String,
        @PathVariable serverId: String,
    ) = privateNetworksService.unassignInstanceFromNetwork(userId, networkId, serverId)
}
