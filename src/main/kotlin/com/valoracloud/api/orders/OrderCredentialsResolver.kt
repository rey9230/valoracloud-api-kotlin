package com.valoracloud.api.orders

import com.valoracloud.api.common.exceptions.BadRequestException
import com.valoracloud.api.common.exceptions.ForbiddenException
import com.valoracloud.api.common.exceptions.NotFoundException
import com.valoracloud.api.config.SecretRepository
import com.valoracloud.api.contabo.ContaboCreateSecretRequest
import com.valoracloud.api.contabo.ContaboSecretType
import com.valoracloud.api.contabo.ContaboService
import com.valoracloud.api.entity.Secret
import java.security.SecureRandom
import org.springframework.stereotype.Component

data class ResolvedCheckoutCredentials(
    val rootPassword: String,
    val sshKeyId: String?,
)

@Component
class OrderCredentialsResolver(
    private val secretRepository: SecretRepository,
    private val contabo: ContaboService,
) {
    fun resolve(userId: String, dto: CreateOrderDto): ResolvedCheckoutCredentials {
        val sshKeyId = dto.sshKeyId?.trim()?.takeIf { it.isNotEmpty() }
        val sshPublicKey = dto.sshPublicKey?.trim()?.takeIf { it.isNotEmpty() }
        val passwordSecretId = dto.passwordSecretId?.trim()?.takeIf { it.isNotEmpty() }
        val plainPassword = dto.rootPassword?.trim()?.takeIf { it.isNotEmpty() }

        if (sshKeyId != null && sshPublicKey != null) {
            throw BadRequestException("Provide either sshKeyId or sshPublicKey, not both")
        }

        val resolvedSshKeyId = when {
            sshKeyId != null -> {
                requireUserSecret(userId, sshKeyId, "ssh")
                sshKeyId
            }
            sshPublicKey != null -> createSshSecretFromPublicKey(userId, sshPublicKey, dto.sshKeyName)
            else -> null
        }

        if (passwordSecretId != null) {
            requireUserSecret(userId, passwordSecretId, "password")
        }

        val rootPassword = when {
            plainPassword != null -> plainPassword
            passwordSecretId != null -> readPasswordSecret(passwordSecretId)
            resolvedSshKeyId != null -> generateProvisioningPassword()
            else -> throw BadRequestException("Provide rootPassword, passwordSecretId, sshKeyId, or sshPublicKey")
        }

        if (rootPassword.length !in 8..72) {
            throw BadRequestException("Root password must be between 8 and 72 characters")
        }

        return ResolvedCheckoutCredentials(rootPassword = rootPassword, sshKeyId = resolvedSshKeyId)
    }

    fun resolveContaboSshKeyIds(sshKeyId: String?): List<Long>? {
        if (sshKeyId.isNullOrBlank()) return null
        val secret = secretRepository.findById(sshKeyId).orElse(null) ?: return null
        if (secret.type != "ssh" || secret.contaboId <= 0) return null
        return listOf(secret.contaboId.toLong())
    }

    private fun createSshSecretFromPublicKey(userId: String, publicKey: String, name: String?): String {
        val normalized = normalizeSshPublicKey(publicKey)
        validateSshPublicKey(normalized)
        val secretName = name?.trim()?.takeIf { it.isNotEmpty() }
            ?: "checkout-${System.currentTimeMillis()}"
        val cs = contabo.createSecret(
            ContaboCreateSecretRequest(
                name = secretName,
                type = ContaboSecretType.ssh,
                value = normalized,
            ),
        )
        val secret = secretRepository.save(
            Secret(
                userId = userId,
                contaboId = cs.secretId.toInt(),
                name = cs.name,
                type = "ssh",
            ),
        )
        return secret.id
    }

    private fun requireUserSecret(userId: String, secretId: String, expectedType: String): Secret {
        val secret = secretRepository.findById(secretId)
            .orElseThrow { NotFoundException("Secret", secretId) }
        if (secret.userId != userId) throw ForbiddenException("Access denied")
        if (secret.type != expectedType) {
            throw BadRequestException("Secret $secretId is not a $expectedType secret")
        }
        if (secret.contaboId <= 0) {
            throw BadRequestException("Secret $secretId is not linked to Contabo — recreate or sync secrets")
        }
        return secret
    }

    private fun readPasswordSecret(secretId: String): String {
        val secret = secretRepository.findById(secretId)
            .orElseThrow { NotFoundException("Secret", secretId) }
        if (secret.contaboId <= 0) {
            throw BadRequestException("Password secret is not linked to Contabo")
        }
        val contaboSecret = contabo.getSecret(secret.contaboId.toLong())
        if (contaboSecret.type != ContaboSecretType.password) {
            throw BadRequestException("Secret is not a password secret in Contabo")
        }
        return contaboSecret.value?.takeIf { it.isNotBlank() }
            ?: throw BadRequestException("Could not read password from Contabo secret")
    }

    companion object {
        private const val PASSWORD_LENGTH = 24
        private val PASSWORD_CHARS =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#\$%&*".toCharArray()
        private val SSH_PUBLIC_KEY_LINE =
            Regex("""^(?:ssh-(?:rsa|ed25519|dss)|ecdsa-sha2-nistp256|sk-(?:ssh-)?ed25519(?:@openssh\.com)?)\s+[A-Za-z0-9+/]+={0,3}(?:\s+[^\s]+)?$""")

        fun normalizeSshPublicKey(input: String): String {
            val lines = input.replace("\r\n", "\n").split("\n")
            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                return line
            }
            return input.trim()
        }

        fun validateSshPublicKey(input: String) {
            val line = normalizeSshPublicKey(input)
            if (line.length < 40 || !SSH_PUBLIC_KEY_LINE.matches(line)) {
                throw BadRequestException("Invalid SSH public key format")
            }
        }

        fun generateProvisioningPassword(): String {
            val random = SecureRandom()
            return buildString(PASSWORD_LENGTH) {
                repeat(PASSWORD_LENGTH) {
                    append(PASSWORD_CHARS[random.nextInt(PASSWORD_CHARS.size)])
                }
            }
        }
    }
}
