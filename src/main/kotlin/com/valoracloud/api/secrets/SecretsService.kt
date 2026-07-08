package com.valoracloud.api.secrets

import com.valoracloud.api.common.exceptions.BadRequestException
import com.valoracloud.api.common.exceptions.ForbiddenException
import com.valoracloud.api.common.exceptions.NotFoundException
import com.valoracloud.api.config.SecretRepository
import com.valoracloud.api.contabo.ContaboCreateSecretRequest
import com.valoracloud.api.contabo.ContaboSecretType
import com.valoracloud.api.contabo.ContaboService
import com.valoracloud.api.contabo.ContaboUpdateSecretRequest
import com.valoracloud.api.entity.Secret
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SecretsService(
    private val secretRepository: SecretRepository,
    private val contabo: ContaboService,
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
        val contaboType = parseSecretType(dto.type)
        val cs = contabo.createSecret(
            ContaboCreateSecretRequest(
                name = dto.name,
                type = contaboType,
                value = dto.value,
            ),
        )
        return secretRepository.save(
            Secret(
                userId = userId,
                contaboId = cs.secretId.toInt(),
                name = cs.name,
                type = cs.type.name,
            ),
        )
    }

    fun updateSecret(userId: String, secretId: String, dto: UpdateSecretDto): Any {
        val secret = secretRepository.findById(secretId)
            .orElseThrow { NotFoundException("Secret", secretId) }
        if (secret.userId != userId) throw ForbiddenException("Access denied")

        if (dto.name == null && dto.value == null) {
            throw BadRequestException("At least one field (name or value) must be provided")
        }

        if (secret.contaboId > 0) {
            contabo.updateSecret(
                secret.contaboId.toLong(),
                ContaboUpdateSecretRequest(
                    name = dto.name,
                    value = dto.value,
                ),
            )
        }

        dto.name?.let { secret.name = it }
        return secretRepository.save(secret)
    }

    fun deleteSecret(userId: String, secretId: String) {
        val secret = secretRepository.findById(secretId)
            .orElseThrow { NotFoundException("Secret", secretId) }
        if (secret.userId != userId) throw ForbiddenException("Access denied")

        if (secret.contaboId > 0) {
            contabo.deleteSecret(secret.contaboId.toLong())
        }
        secretRepository.delete(secret)
    }

    fun syncSecretsFromContabo(userId: String): Map<String, Int> {
        val secrets = contabo.listSecrets()
        var synced = 0
        for (cs in secrets) {
            val existing = secretRepository.findByContaboId(cs.secretId.toInt())
            if (existing != null) {
                if (existing.userId == userId) {
                    existing.name = cs.name
                    existing.type = cs.type.name
                    secretRepository.save(existing)
                    synced++
                }
            } else {
                secretRepository.save(
                    Secret(
                        userId = userId,
                        contaboId = cs.secretId.toInt(),
                        name = cs.name,
                        type = cs.type.name,
                    ),
                )
                synced++
            }
        }
        return mapOf("synced" to synced)
    }

    private fun parseSecretType(type: String): ContaboSecretType =
        when (type.lowercase()) {
            "ssh" -> ContaboSecretType.ssh
            "password" -> ContaboSecretType.password
            else -> throw BadRequestException("Invalid secret type: $type")
        }
}
