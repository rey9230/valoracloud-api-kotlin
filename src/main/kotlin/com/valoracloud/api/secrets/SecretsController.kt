package com.valoracloud.api.secrets

import com.valoracloud.api.auth.security.CurrentUser
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/secrets")
class SecretsController(
    private val secretsService: SecretsService,
) {
    @GetMapping
    fun listSecrets(
        @CurrentUser userId: String,
        @RequestParam(required = false) type: String?,
    ) = secretsService.listUserSecrets(userId, type)

    @GetMapping("/sync")
    fun syncSecrets(@CurrentUser userId: String) =
        secretsService.syncSecretsFromContabo(userId)

    @GetMapping("/{id}")
    fun getSecret(
        @CurrentUser userId: String,
        @PathVariable id: String,
    ) = secretsService.getSecretDetail(userId, id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createSecret(
        @CurrentUser userId: String,
        @Valid @RequestBody dto: CreateSecretDto,
    ) = secretsService.createSecret(userId, dto)

    @PatchMapping("/{id}")
    fun updateSecret(
        @CurrentUser userId: String,
        @PathVariable id: String,
        @Valid @RequestBody dto: UpdateSecretDto,
    ) = secretsService.updateSecret(userId, id, dto)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteSecret(
        @CurrentUser userId: String,
        @PathVariable id: String,
    ) = secretsService.deleteSecret(userId, id)
}
