package com.valoracloud.api.servers

import com.valoracloud.api.common.dto.PaginationDto
import com.valoracloud.api.common.exceptions.ForbiddenException
import com.valoracloud.api.common.model.ServerStatus
import com.valoracloud.api.config.ProvisioningLogRepository
import com.valoracloud.api.config.ServerRepository
import com.valoracloud.api.contabo.ContaboService
import com.valoracloud.api.contabo.ContaboVncAccess
import com.valoracloud.api.entity.Server
import com.valoracloud.api.notifications.service.NotificationsService
import com.valoracloud.api.provisioning.processor.ProvisioningProcessor
import io.mockk.every
import io.mockk.mockk
import java.util.Optional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServersServiceTest {

    private val serverRepo = mockk<ServerRepository>()
    private val provisioningLogRepo = mockk<ProvisioningLogRepository>(relaxed = true)
    private val contabo = mockk<ContaboService>()
    private val notifications = mockk<NotificationsService>(relaxed = true)
    private val provisioningProcessor = mockk<ProvisioningProcessor>(relaxed = true)

    // encryptionKey blank → credentials returns the stored password as-is (no decrypt)
    private val service = ServersService(
        serverRepo, provisioningLogRepo, contabo, notifications, provisioningProcessor, "",
    )

    /** createdAt is a lateinit set by JPA auditing; stamp it for list-mapping tests. */
    private fun Server.stamped(): Server {
        val f = Class.forName("com.valoracloud.api.entity.BaseEntity").getDeclaredField("createdAt")
        f.isAccessible = true
        f.set(this, java.time.Instant.now())
        return this
    }

    private fun server(status: ServerStatus, userId: String = "u1") = Server(
        id = "s1",
        userId = userId,
        orderId = "o1",
        contaboInstanceId = "123",
        status = status,
        hostname = "srv-abc.valoracloud.com",
        ipAddress = "1.2.3.4",
        sshUser = "root",
        rootPassword = "PlainPass1",
        os = "ubuntu-24.04",
        region = "EU",
    )

    // ── credentials/console are gated on RUNNING (white-label protection) ──

    @Test
    fun `credentials are refused while the server is still provisioning`() {
        every { serverRepo.findById("s1") } returns Optional.of(server(ServerStatus.PROVISIONING))
        assertThrows(ForbiddenException::class.java) { service.credentials("s1", "u1") }
    }

    @Test
    fun `credentials are refused while reinstalling`() {
        every { serverRepo.findById("s1") } returns Optional.of(server(ServerStatus.REINSTALLING))
        assertThrows(ForbiddenException::class.java) { service.credentials("s1", "u1") }
    }

    @Test
    fun `credentials are returned once running`() {
        every { serverRepo.findById("s1") } returns Optional.of(server(ServerStatus.RUNNING))
        val creds = service.credentials("s1", "u1")
        assertEquals("PlainPass1", creds["rootPassword"])
        assertEquals("root", creds["sshUser"])
        assertEquals("1.2.3.4", creds["ipAddress"])
    }

    @Test
    fun `console is refused unless running`() {
        every { serverRepo.findById("s1") } returns Optional.of(server(ServerStatus.NEEDS_PROVISION))
        assertThrows(ForbiddenException::class.java) { service.console("s1", "u1") }
    }

    @Test
    fun `console returns vnc access once running`() {
        every { serverRepo.findById("s1") } returns Optional.of(server(ServerStatus.RUNNING))
        every { contabo.getVncAccess(123L) } returns ContaboVncAccess(host = "vnc.host", port = 5901, password = "vncpw")
        val out = service.console("s1", "u1")
        assertTrue(out.containsKey("vnc"))
    }

    @Test
    fun `another user cannot read credentials`() {
        every { serverRepo.findById("s1") } returns Optional.of(server(ServerStatus.RUNNING, userId = "owner"))
        assertThrows(Exception::class.java) { service.credentials("s1", "attacker") }
    }

    // ── list hides dead servers (cancelled / failed noise) ──

    @Test
    fun `findByUser hides cancelled and errored servers`() {
        val running = server(ServerStatus.RUNNING).also { it.id = "run" }.stamped()
        val stopped = server(ServerStatus.STOPPED).also { it.id = "stop" }.stamped()
        val cancelled = server(ServerStatus.CANCELLED).also { it.id = "cxl" }.stamped()
        val errored = server(ServerStatus.ERROR).also { it.id = "err" }.stamped()
        every { serverRepo.findByUserIdOrderByCreatedAtDesc("u1") } returns
            listOf(running, cancelled, stopped, errored)

        val page = service.findByUser("u1", PaginationDto())
        val ids = page.data.map { it["id"] }
        assertEquals(2, page.total)
        assertTrue(ids.containsAll(listOf("run", "stop")))
        assertFalse(ids.contains("cxl"))
        assertFalse(ids.contains("err"))
    }

    // ── reinstall guards against a concurrent reinstall ──

    @Test
    fun `reinstall is rejected when one is already in progress`() {
        every { serverRepo.findById("s1") } returns Optional.of(server(ServerStatus.REINSTALLING))
        assertThrows(ForbiddenException::class.java) {
            service.reinstall("s1", "u1", "img-uuid", "NewPass12")
        }
    }
}
