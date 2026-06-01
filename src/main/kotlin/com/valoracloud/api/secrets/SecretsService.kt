package com.valoracloud.api.secrets

import com.valoracloud.api.common.exceptions.BadRequestException
import com.valoracloud.api.common.exceptions.ForbiddenException
import com.valoracloud.api.common.exceptions.NotFoundException
import com.valoracloud.api.config.SecretRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SecretsService(
    private val secretRepository: SecretRepository,
    // TODO: Inject ContaboService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun listUserSecrets(userId: String, type: String? = null): Any {
        val secrets = secretRepository.findByUserId(userId)
        return if (type != null) secrets.filter { it.type == type } else secrets
    }

    fun getSecretDetail(userId: String, secretId: String): Any {
        val secret = secretRepository.findById(secretId)
            .orElseThrow { NotFoundException("Secret", secretId) }
        if (secret.userId != userId) throw ForbiddenException("Access denied")
        return secret
    }

    fun createSecret(userId: String, dto: CreateSecretDto): Any {
        log.info("Creating ${dto.type} secret '${dto.name}' for user $userId")

        // TODO: ContaboService.createSecret(dto) → returns contaboSecretId

        return secretRepository.save(
            com.valoracloud.api.entity.Secret(
                userId = userId,
                contaboId = 0, // TODO: from Contabo
                name = dto.name,
                type = dto.type,
            )
        )
    }

    fun updateSecret(userId: String, secretId: String, dto: UpdateSecretDto): Any {
        val secret = secretRepository.findById(secretId)
            .orElseThrow { NotFoundException("Secret", secretId) }
        if (secret.userId != userId) throw ForbiddenException("Access denied")

        if (dto.name == null && dto.value == null) {
            throw BadRequestException("At least one field (name or value) must be provided")
        }

        // TODO: ContaboService.updateSecret(secret.contaboId, dto)

        dto.name?.let { secret.name = it }
        return secretRepository.save(secret)
    }

    fun deleteSecret(userId: String, secretId: String) {
        val secret = secretRepository.findById(secretId)
            .orElseThrow { NotFoundException("Secret", secretId) }
        if (secret.userId != userId) throw ForbiddenException("Access denied")

        // TODO: ContaboService.deleteSecret(secret.contaboId)
        secretRepository.delete(secret)
    }

    fun syncSecretsFromContabo(userId: String): Map<String, Int> {
        // TODO: ContaboService.listSecrets() → upsert into DB
        return mapOf("synced" to 0)
    }
}
