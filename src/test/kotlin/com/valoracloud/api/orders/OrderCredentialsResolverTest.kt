package com.valoracloud.api.orders

import com.valoracloud.api.common.exceptions.BadRequestException
import com.valoracloud.api.config.SecretRepository
import com.valoracloud.api.contabo.ContaboSecret
import com.valoracloud.api.contabo.ContaboSecretType
import com.valoracloud.api.contabo.ContaboService
import com.valoracloud.api.entity.Secret
import io.mockk.every
import io.mockk.mockk
import java.util.Optional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class OrderCredentialsResolverTest {
    private val secretRepository = mockk<SecretRepository>()
    private val contabo = mockk<ContaboService>()
    private val resolver = OrderCredentialsResolver(secretRepository, contabo)

    @Test
    fun `uses plain root password when provided`() {
        val result = resolver.resolve(
            "user-1",
            CreateOrderDto(
                planId = "plan",
                billingCycle = 12,
                region = "region-eu",
                imageId = "image",
                rootPassword = "ValidPass1!",
            ),
        )
        assertEquals("ValidPass1!", result.rootPassword)
        assertEquals(null, result.sshKeyId)
    }

    @Test
    fun `generates password when ssh key selected without password`() {
        val sshSecret = Secret(userId = "user-1", contaboId = 42, name = "laptop", type = "ssh")
        sshSecret.id = "sec-ssh"
        every { secretRepository.findById("sec-ssh") } returns Optional.of(sshSecret)

        val result = resolver.resolve(
            "user-1",
            CreateOrderDto(
                planId = "plan",
                billingCycle = 12,
                region = "region-eu",
                imageId = "image",
                sshKeyId = "sec-ssh",
            ),
        )

        assertEquals("sec-ssh", result.sshKeyId)
        assertNotNull(result.rootPassword)
        assertEquals(true, result.rootPassword.length >= 8)
    }

    @Test
    fun `reads saved password secret from Contabo`() {
        val pwSecret = Secret(userId = "user-1", contaboId = 99, name = "default", type = "password")
        pwSecret.id = "sec-pw"
        every { secretRepository.findById("sec-pw") } returns Optional.of(pwSecret)
        every { contabo.getSecret(99) } returns ContaboSecret(
            secretId = 99,
            name = "default",
            type = ContaboSecretType.password,
            value = "SavedPass1!",
        )

        val result = resolver.resolve(
            "user-1",
            CreateOrderDto(
                planId = "plan",
                billingCycle = 12,
                region = "region-eu",
                imageId = "image",
                passwordSecretId = "sec-pw",
            ),
        )

        assertEquals("SavedPass1!", result.rootPassword)
    }

    @Test
    fun `creates Contabo secret when ssh public key is provided`() {
        val saved = Secret(userId = "user-1", contaboId = 77, name = "laptop", type = "ssh")
        saved.id = "sec-new"
        every {
            contabo.createSecret(
                match { it.type == ContaboSecretType.ssh && it.value.contains("ssh-ed25519") },
            )
        } returns ContaboSecret(
            secretId = 77,
            name = "checkout-1",
            type = ContaboSecretType.ssh,
        )
        every {
            secretRepository.save(
                match { it.userId == "user-1" && it.contaboId == 77 },
            )
        } returns saved

        val result = resolver.resolve(
            "user-1",
            CreateOrderDto(
                planId = "plan",
                billingCycle = 12,
                region = "region-eu",
                imageId = "image",
                sshPublicKey = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGtestkeyvaloratestkeyvaloratestkeyval user@host",
            ),
        )

        assertEquals("sec-new", result.sshKeyId)
        assertNotNull(result.rootPassword)
    }

    @Test
    fun `rejects ssh key id and public key together`() {
        assertThrows(BadRequestException::class.java) {
            resolver.resolve(
                "user-1",
                CreateOrderDto(
                    planId = "plan",
                    billingCycle = 12,
                    region = "region-eu",
                    imageId = "image",
                    sshKeyId = "sec-1",
                    sshPublicKey = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGtestkeyvaloratestkeyvaloratestkeyval user@host",
                ),
            )
        }
    }

    @Test
    fun `requires at least one auth method`() {
        assertThrows(BadRequestException::class.java) {
            resolver.resolve(
                "user-1",
                CreateOrderDto(
                    planId = "plan",
                    billingCycle = 12,
                    region = "region-eu",
                    imageId = "image",
                ),
            )
        }
    }
}
