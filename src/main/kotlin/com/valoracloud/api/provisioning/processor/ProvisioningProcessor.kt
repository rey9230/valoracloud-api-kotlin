package com.valoracloud.api.provisioning.processor

import com.valoracloud.api.billing.service.ProvisionJobData
import com.valoracloud.api.common.model.OrderStatus
import com.valoracloud.api.common.model.ServerStatus
import com.valoracloud.api.common.utils.EncryptionUtil
import com.valoracloud.api.config.*
import com.valoracloud.api.contabo.ContaboService
import com.valoracloud.api.entity.Server
import com.valoracloud.api.notifications.service.NotificationsService
import com.valoracloud.api.provisioning.ContaboProvisioningResolver
import com.valoracloud.api.provisioning.ProvisioningDefaults
import com.valoracloud.api.provisioning.ValoraBranding
import com.valoracloud.api.provisioning.service.ProvisioningService
import com.valoracloud.api.provisioning.service.ProvisioningTxHelper
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

/**
 * Synchronous provisioning processor. Replaces the BullMQ-based worker from the NestJS version.
 *
 * Processes orders of type COMPUTE, OBJECT_STORAGE, or DOMAIN by creating corresponding resources
 * via the Contabo API, setting up post-provision configuration, and sending notification emails
 * when complete.
 */
@Service
class ProvisioningProcessor(
        private val orderRepo: OrderRepository,
        private val serverRepo: ServerRepository,
        private val planRepo: PlanRepository,
        private val userRepo: UserRepository,
        private val objectStorageRepo: ObjectStorageRepository,
        private val domainRepo: DomainRepository,
        private val domainHandleRepo: DomainHandleRepository,
        private val contabo: ContaboService,
        private val notifications: NotificationsService,
        private val provisioningService: ProvisioningService,
        private val txHelper: ProvisioningTxHelper,
        private val serverMonitorRepo: ServerMonitorRepository,
        private val domainTldPricingRepo: DomainTldPricingRepository,
        private val addonCatalogRepo: AddonCatalogRepository,
        private val secretRepo: SecretRepository,
        @Value("\${app.encryption-key:}") private val encryptionKey: String,
        @Value("\${app.brand.domain:valoracloud.com}") private val brandDomain: String,
        @Value("\${app.provisioning.dry-run:false}") private val dryRun: Boolean,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val MAX_POLL_ATTEMPTS = 30
        private const val POLL_INTERVAL_MS = 30_000L
        private const val PASSWORD_LENGTH = 24
        private val PASSWORD_CHARS =
                "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#\$%&*".toCharArray()

        val CLOUD_INIT_BASE: String by lazy {
            javaClass.classLoader.getResource("provisioning/scripts/cloud-init.yaml")?.readText()
                    ?: DEFAULT_CLOUD_INIT
        }

        private val DEFAULT_CLOUD_INIT =
                """
#cloud-config
ssh_pwauth: true
write_files:
  - path: /etc/valora/banner.txt
    permissions: '0644'
    content: |
      Valora Cloud
  - path: /etc/update-motd.d/00-valora
    permissions: '0755'
    content: |
      #!/usr/bin/env bash
      echo "Welcome to Valora Cloud"
runcmd: []
        """.trimIndent()

        fun buildCloudInit(rootPassword: String, sshUser: String = ProvisioningDefaults.LINUX_USER): String {
            val chpasswd = "chpasswd:\n  list: |\n    $sshUser:$rootPassword\n  expire: false\n\n"
            return CLOUD_INIT_BASE.replace("#cloud-config\n", "#cloud-config\n$chpasswd")
        }

        /** Hostname the customer sees — always Valora-branded, never Contabo's vmiXXXX. */
        fun customerHostname(serverId: String, brandDomain: String): String =
                "srv-${serverId.take(8)}.$brandDomain"

        fun buildWriteFileCommand(
                filePath: String,
                content: String,
                mode: String,
                heredocId: String
        ): String {
            return """
cat <<'$heredocId' > $filePath
$content
$heredocId
chmod $mode $filePath
            """.trimIndent()
        }

        /**
         * Shell commands run on every fresh instance — first provision AND reinstall —
         * to strip ALL Contabo branding (MOTD, issue, SSH banner, profile.d) and install
         * Valora's. The customer must never see who the upstream provider is.
         * Guarded by ProvisioningBrandingTest.
         */
        fun buildPostProvisionCommands(
                hostname: String,
                regionLabel: String,
                bannerContent: String,
                motdScript: String,
        ): List<String> =
                listOf(
                        // Write Valora meta file
                        "mkdir -p /etc/valora && printf 'fqdn=%s\\nregion_label=%s\\n' '$hostname' '$regionLabel' > /etc/valora/meta",
                        // Write banner
                        buildWriteFileCommand("/etc/valora/banner.txt", bannerContent, "0644", "VLRN_BANNER"),
                        // Write MOTD
                        buildWriteFileCommand("/etc/update-motd.d/00-valora", motdScript, "0755", "VLRN_MOTD"),
                        // Set hostname
                        "hostnamectl set-hostname $hostname",
                        "sed -i 's/127.0.1.1.*/127.0.1.1 $hostname/' /etc/hosts || echo \"127.0.1.1 $hostname\" >> /etc/hosts",
                        // Wipe provider branding
                        ": > /etc/motd; : > /etc/issue; : > /etc/issue.net",
                        "rm -f /etc/profile.d/contabo* /etc/profile.d/cntb* 2>/dev/null || true",
                        // Disable all MOTD fragments except ours
                        "for f in /etc/update-motd.d/*; do [ \"\$(basename \$f)\" = \"00-valora\" ] || chmod -x \"\$f\" 2>/dev/null || true; done",
                        // Disable SSH banner
                        "sed -i 's/^#*Banner.*/Banner none/' /etc/ssh/sshd_config",
                        "grep -q '^PrintMotd' /etc/ssh/sshd_config && sed -i 's/^PrintMotd.*/PrintMotd no/' /etc/ssh/sshd_config || echo 'PrintMotd no' >> /etc/ssh/sshd_config",
                        "systemctl reload sshd 2>/dev/null || systemctl reload ssh 2>/dev/null || true",
                        // Regenerate MOTD
                        "run-parts /etc/update-motd.d/ > /run/motd.dynamic 2>/dev/null || true",
                        // Install guest agent
                        "apt-get install -y qemu-guest-agent 2>/dev/null || yum install -y qemu-guest-agent 2>/dev/null || true",
                )
    }

    // ─── Entry point ────────────────────────────────────

    /**
     * Runs asynchronously in the provisioningExecutor thread pool so the
     * webhook handler (Stripe / SHKeeper) can return HTTP 200 immediately.
     * NOT @Transactional — provisioning takes 15-25 min; holding a DB
     * connection that long would exhaust the HikariCP pool.
     */
    /**
     * Finalizes a user-initiated reinstall: polls Contabo until the instance is
     * running (max 15 min), flips the server status, cleans up the temporary
     * password secret, and emails the customer. Runs async so the reinstall
     * endpoint returns immediately.
     */
    @Async("provisioningExecutor")
    fun finalizeReinstall(serverId: String, secretId: Long?, newPassword: String) {
        val server = serverRepo.findById(serverId).orElse(null)
        if (server == null) {
            log.warn("finalizeReinstall: server $serverId not found")
            return
        }
        var ready = false
        var ipAddress: String? = server.ipAddress
        try {
            for (i in 0 until 30) {
                Thread.sleep(30_000)
                try {
                    val inst = contabo.getInstance(server.contaboInstanceId.toLong())
                    if (inst.status == "running") {
                        inst.ipConfig?.v4?.ip?.let { ipAddress = it }
                        ready = true
                        break
                    }
                } catch (_: Exception) {
                    /* poll again */
                }
            }
        } finally {
            secretId?.let {
                runCatching { contabo.deleteSecret(it) }
                        .onFailure { e -> log.warn("Could not delete reinstall secret: ${e.message}") }
            }
        }

        // Re-brand: a fresh image ships Contabo's MOTD/hostname — the customer must
        // only ever see Valora. Same post-provision pass as first-time provisioning.
        var postProvisionOk = false
        if (ready && !ipAddress.isNullOrBlank()) {
            try {
                runPostProvisioning(
                        ipAddress!!,
                        server.sshUser,
                        newPassword,
                        serverId,
                        brandDomain,
                        server.region,
                        server.os,
                )
                postProvisionOk = true
            } catch (e: Exception) {
                log.error("Post-provision after reinstall failed for $serverId: ${e.message}")
                provisioningService.logEvent(serverId, "post-provision", "error", e.message)
            }
        }

        server.status = when {
            ready && postProvisionOk -> ServerStatus.RUNNING
            ready -> ServerStatus.NEEDS_PROVISION // running but still Contabo-branded
            else -> ServerStatus.ERROR
        }
        server.provisionedAt = if (ready) Instant.now() else server.provisionedAt
        serverRepo.save(server)
        provisioningService.logEvent(
                serverId,
                "reinstall",
                if (ready) "success" else "error",
                if (ready) "Reinstall completed" else "Instance did not reach running state within 15 min",
        )
        if (ready) {
            val user = userRepo.findById(server.userId).orElse(null)
            if (user != null) {
                runCatching {
                    notifications.sendReinstallCompleteEmail(
                            email = user.email,
                            hostname = server.hostname,
                            newPassword = newPassword,
                            language = user.language,
                            userId = user.id,
                    )
                }.onFailure { e -> log.error("Failed to send reinstall complete email: ${e.message}") }
            }
        }
    }

    @Async("provisioningExecutor")
    fun provisionServer(data: ProvisionJobData) {
        val orderId = data.orderId

        try {
            log.info("Starting provisioning for order $orderId")

            // Atomic claim: verify still PAID and transition → PROVISIONING
            // (uses a separate @Transactional bean so the proxy applies)
            val order = txHelper.claimOrder(orderId)
            if (order == null) {
                log.warn("Order $orderId could not be claimed (not found, not PAID, or already in progress)")
                return
            }

            // Route to appropriate provisioning logic
            when (order.serviceType) {
                com.valoracloud.api.common.model.ServiceType.COMPUTE -> provisionCompute(data)
                com.valoracloud.api.common.model.ServiceType.OBJECT_STORAGE ->
                        provisionObjectStorage(data)
                com.valoracloud.api.common.model.ServiceType.DOMAIN -> provisionDomain(data)
                else -> throw RuntimeException("Unknown service type: ${order.serviceType}")
            }

            log.info("Provisioning complete for order $orderId")
        } catch (e: Exception) {
            log.error("Provisioning failed for order $orderId: ${e.message}", e)
            throw e
        }
    }

    // ─── Compute Provisioning ───────────────────────────
    // NOT @Transactional — long-running (Contabo poll + SSH).
    // Each serverRepo.save() / orderRepo.save() opens its own short transaction via Spring Data JPA.
    fun provisionCompute(data: ProvisionJobData) {
        val orderId = data.orderId
        val planId = data.planId
        val userId = data.userId
        val regionAddonId = data.region
        val os = data.os
        val hostname = data.hostname
        var serverId: String? = null

        try {
            log.info("Starting compute provisioning for order $orderId")

            // Get order with plan info
            val order =
                    orderRepo.findById(orderId).orElse(null)
                            ?: throw RuntimeException("Order $orderId not found")

            val plan = planRepo.findById(planId).orElse(null)
            val rootPassword = order.rootPassword?.let { decrypt(it) } ?: generatePassword()

            val displayName = hostname ?: "srv-${orderId.take(8)}"

            // Resolve Region
            val regionCatalog = addonCatalogRepo.findById(regionAddonId).orElse(null)
            val region = ContaboProvisioningResolver.resolveRegion(regionAddonId, regionCatalog)
            log.info("Resolved Region \"$regionAddonId\" → $region")

            // Resolve OS image
            val osSlug = order.os
            val uuidRegex = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)
            
            val image = if (uuidRegex.matches(osSlug)) {
                try {
                    contabo.getImage(osSlug)
                } catch (e: Exception) {
                    throw RuntimeException("No Contabo image found for ID: \"$osSlug\"", e)
                }
            } else {
                contabo.findImageBySlug(osSlug)
                        ?: throw RuntimeException(
                                "No Contabo image found for OS slug: \"$osSlug\""
                        )
            }
            
            val imageId = image.imageId
            log.info("Resolved OS \"$osSlug\" → imageId $imageId")

            // Resolve Contabo productId based on storage addon.
            // Windows uses the same productId as Linux — only the imageId differs.
            val isWindowsImage = ProvisioningDefaults.isWindows(image.name, image.osType)

            val planEntity = plan ?: throw RuntimeException("Plan not found for order $orderId")

            val storageAddon = order.addons.firstOrNull { it.startsWith("storage-") }

            val contaboPlanId = ContaboProvisioningResolver.resolveProductId(planEntity, storageAddon)

            if (storageAddon != null) {
                val storageType = ContaboProvisioningResolver.resolveStorageType(storageAddon)
                when (storageType) {
                    com.valoracloud.api.provisioning.StorageType.SSD ->
                        if (planEntity.contaboPlanIdSsd.isNullOrBlank())
                            log.warn("SSD storage selected but plan.contaboPlanIdSsd is not set — falling back to NVMe productId $contaboPlanId")
                    com.valoracloud.api.provisioning.StorageType.STORAGE ->
                        if (planEntity.contaboPlanIdStorage.isNullOrBlank())
                            log.warn("Storage addon selected but plan.contaboPlanIdStorage is not set — falling back to NVMe productId $contaboPlanId")
                    else -> Unit
                }
            }
            log.info("Contabo productId=$contaboPlanId  storageAddon=$storageAddon  windows=$isWindowsImage")

            val catalogById = addonCatalogRepo.findAll().associateBy { it.id }
            val contaboAddOns = ContaboProvisioningResolver.resolveContaboAddOns(order.addons, catalogById)
            val license = ContaboProvisioningResolver.resolveLicense(order.addons, catalogById)
            if (contaboAddOns != null) {
                log.info("Contabo addOns for order $orderId: privateNetworking=${contaboAddOns.privateNetworking != null}, backup=${contaboAddOns.backup != null}")
            }
            if (license != null) {
                log.info("Contabo license for order $orderId: $license")
            }

            // defaultUser is determined by OS type, not by order.sshUser.
            // Business rule lives in ProvisioningDefaults: Linux is ALWAYS root.
            val sshUser = ProvisioningDefaults.sshUserFor(isWindowsImage)
            log.info("defaultUser=$sshUser (windows=$isWindowsImage)")

            // Cloud-init
            val cloudInitYaml = buildCloudInit(rootPassword, sshUser)
            val cloudInitB64 = Base64.getEncoder().encodeToString(cloudInitYaml.toByteArray())

            // ── DRY RUN ──
            if (dryRun) {
                log.warn(
                        """
                    ╔══════════════════════════════════════════════════╗
                    ║  DRY-RUN — nothing was sent to Contabo          ║
                    ╚══════════════════════════════════════════════════╝
                    orderId=$orderId userId=$userId planId=$planId
                    region=$region osSlug=$osSlug imageId=$imageId
                    displayName=$displayName sshUser=$sshUser
                    rootPassword=$rootPassword
                    contaboPlanId=$contaboPlanId
                    cloudInitB64=${cloudInitB64.take(80)}...
                    """.trimIndent()
                )
                // Revert order to PAID
                order.status = OrderStatus.PAID
                orderRepo.save(order)
                log.warn("[DRY-RUN] Order $orderId reverted to PAID.")
                return
            }

            // Create Contabo secret for root password
            var secretId: Long? = null
            try {
                val secret =
                        contabo.createSecret(
                                com.valoracloud.api.contabo.ContaboCreateSecretRequest(
                                        name = "prov-${orderId.take(8)}",
                                        type =
                                                com.valoracloud.api.contabo.ContaboSecretType
                                                        .password,
                                        value = rootPassword,
                                )
                        )
                secretId = secret.secretId
            } catch (e: Exception) {
                log.warn("Could not create Contabo secret: ${e.message}")
            }

            // Contabo period: valid values are 1, 3, 6, 12 months
            val period = when (order.billingCycle) {
                3, 6, 12 -> order.billingCycle.toLong()
                else -> 1L
            }

            val sshKeyContaboIds = order.sshKeyId?.let { keyId ->
                secretRepo.findById(keyId).orElse(null)?.let { secret ->
                    if (secret.type == "ssh" && secret.contaboId > 0) {
                        listOf(secret.contaboId.toLong())
                    } else null
                }
            }
            if (order.sshKeyId != null) {
                if (sshKeyContaboIds.isNullOrEmpty()) {
                    log.warn("Order $orderId has sshKeyId=${order.sshKeyId} but no Contabo SSH secret id — instance will not receive the key")
                } else {
                    log.info("Contabo sshKeys for order $orderId: $sshKeyContaboIds")
                }
            }

            // Create instance
            val instance =
                    contabo.createInstance(
                            productId = contaboPlanId,
                            region = region,
                            imageId = imageId,
                            displayName = displayName,
                            period = period,
                            rootPassword = secretId,
                            sshKeys = sshKeyContaboIds,
                            userData = cloudInitB64,
                            defaultUser = sshUser,
                            license = license,
                            addOns = contaboAddOns,
                    )
            log.info("Cloud-init chpasswd for user=$sshUser injected (order $orderId)")

            // Store encrypted password in server record
            val encryptedPassword = encrypt(rootPassword)
            val billingCycle = order.billingCycle
            val server =
                    serverRepo.save(
                            Server(
                                    userId = userId,
                                    orderId = orderId,
                                    contaboInstanceId = instance.instanceId.toString(),
                                    hostname = displayName,
                                    os = os,
                                    region = region,
                                    sshUser = sshUser,
                                    rootPassword = encryptedPassword,
                                    contaboData = mapOf("instanceId" to instance.instanceId),
                                    expiresAt = calculateExpiry(billingCycle),
                            )
                    )
            serverId = server.id

            provisioningService.logEvent(
                    serverId,
                    "create-instance",
                    "success",
                    "Contabo instance ${instance.instanceId} created"
            )

            // Wait for instance to be ready
            provisioningService.logEvent(serverId, "wait-ready", "pending")
            val readyInstance = waitForInstance(instance.instanceId, serverId)

            // Clean up temporary secret
            if (secretId != null) {
                runCatching { contabo.deleteSecret(secretId) }.onFailure {
                    log.warn("Could not delete Contabo secret $secretId: ${it.message}")
                }
            }

            val ipAddress = readyInstance.ipConfig?.v4?.ip
            if (ipAddress != null) {
                server.ipAddress = ipAddress
                serverRepo.save(server)
            }
            provisioningService.logEvent(
                    serverId,
                    "wait-ready",
                    "success",
                    "Instance ready at $ipAddress"
            )

            // Post-provisioning SSH
            provisioningService.logEvent(serverId, "post-provision", "pending")
            var postProvisionOk = false
            try {
                runPostProvisioning(
                        ipAddress ?: "",
                        sshUser,
                        rootPassword,
                        serverId,
                        brandDomain,
                        region,
                        image.name, // Pass the actual image name instead of the slug
                )
                provisioningService.logEvent(serverId, "post-provision", "success")
                postProvisionOk = true
            } catch (e: Exception) {
                provisioningService.logEvent(serverId, "post-provision", "error", e.message)
                log.error("SSH post-provisioning failed for $serverId: ${e.message}")
                runCatching {
                    notifications.sendAdminProvisionAlert(
                            context = "provision",
                            serverId = serverId,
                            hostname = displayName,
                            ip = ipAddress ?: "",
                            region = region,
                            userId = userId,
                            errorMessage = e.message ?: "Unknown error",
                            errorStack = e.stackTraceToString(),
                    )
                }
                        .onFailure { log.error("Admin provision alert failed", it) }
            }

            // Mark server status
            server.status =
                    if (postProvisionOk) ServerStatus.RUNNING else ServerStatus.NEEDS_PROVISION
            server.provisionedAt = if (postProvisionOk) Instant.now() else null
            serverRepo.save(server)

            // Auto-create ServerMonitor (enhanced interval when monitoring addon purchased)
            val monitoringEnabled =
                    ContaboProvisioningResolver.isMonitoringEnabled(order.addons, catalogById)
            val monitorInterval = if (monitoringEnabled) 30 else 60
            runCatching {
                serverMonitorRepo.findByServerId(serverId).let { existing ->
                    if (existing == null) {
                        serverMonitorRepo.save(
                                com.valoracloud.api.entity.ServerMonitor(
                                        serverId = serverId,
                                        isActive = true,
                                        protocol = "tcp",
                                        checkPort = 22,
                                        checkIntervalSeconds = monitorInterval,
                                )
                        )
                    } else {
                        existing.isActive = true
                        existing.checkIntervalSeconds = monitorInterval
                        serverMonitorRepo.save(existing)
                    }
                }
            }

            // Bundled object storage (VPS add-on → separate Contabo object storage product)
            val bundleSpec =
                    ContaboProvisioningResolver.resolveObjectStorageBundle(order.addons, catalogById)
            if (bundleSpec != null && postProvisionOk) {
                runCatching {
                    provisionBundledObjectStorage(
                            orderId = orderId,
                            userId = userId,
                            serverId = serverId!!,
                            region = region,
                            billingCycle = billingCycle,
                            bundleSpec = bundleSpec,
                    )
                }.onFailure { e ->
                    log.warn("Bundled object storage failed for order $orderId: ${e.message}")
                    provisioningService.logEvent(
                            serverId,
                            "bundled-object-storage",
                            "error",
                            e.message,
                    )
                }
            }

            // Update order
            order.status = if (postProvisionOk) OrderStatus.ACTIVE else OrderStatus.PROVISIONING
            orderRepo.save(order)

            // Send credentials email
            if (postProvisionOk && ipAddress != null) {
                val user = userRepo.findById(userId).orElse(null)
                if (user != null) {
                    notifications.sendServerReadyEmail(
                            email = user.email,
                            ipAddress = ipAddress,
                            username = sshUser,
                            hostname = displayName,
                            language = user.language,
                            userId = userId,
                    )
                }
            }

            // Set reverse DNS (best-effort, delayed)
            if (ipAddress != null) {
                val rdnsHostname = "${serverId.take(8)}.$brandDomain"
                Thread {
                            Thread.sleep(60_000)
                            runCatching {
                                contabo.setReverseDns(instance.instanceId, ipAddress, rdnsHostname)
                            }
                                    .onFailure {
                                        log.warn("Reverse DNS failed for $serverId: ${it.message}")
                                    }
                        }
                        .start()
            }

            provisioningService.logEvent(serverId, "complete", "success")
            log.info("Provisioning complete for order $orderId, server $serverId")
        } catch (e: Exception) {
            log.error("Provisioning failed for order $orderId: ${e.message}", e)

            if (serverId != null) {
                serverRepo.findById(serverId).ifPresent { s ->
                    s.status = ServerStatus.ERROR
                    serverRepo.save(s)
                }
                provisioningService.logEvent(serverId, "provisioning", "error", e.message)
            }

            orderRepo.findById(orderId).ifPresent { o ->
                o.status = OrderStatus.FAILED
                orderRepo.save(o)
            }

            throw e
        }
    }

    // ─── Object Storage Provisioning ────────────────────

    private fun provisionBundledObjectStorage(
            orderId: String,
            userId: String,
            serverId: String,
            region: String,
            billingCycle: Int,
            bundleSpec: com.valoracloud.api.provisioning.ObjectStorageBundleSpec,
    ) {
        if (objectStorageRepo.findByServerId(serverId) != null) {
            log.info("Bundled object storage already exists for server $serverId")
            return
        }

        val displayName = "bundle-${orderId.take(8)}"
        val contaboStorage =
                contabo.createObjectStorage(
                        region,
                        bundleSpec.totalPurchasedSpaceTB,
                        displayName,
                )
        val credentials = contabo.getS3Credentials(userId)

        objectStorageRepo.save(
                com.valoracloud.api.entity.ObjectStorage(
                        userId = userId,
                        orderId = orderId,
                        serverId = serverId,
                        contaboStorageId = contaboStorage.objectStorageId,
                        status = com.valoracloud.api.common.model.ObjectStorageStatus.READY,
                        displayName = displayName,
                        region = region,
                        totalPurchasedSpaceTB = bundleSpec.totalPurchasedSpaceTB,
                        s3Endpoint = credentials.s3Url,
                        s3AccessKey = encrypt(credentials.accessKey),
                        s3SecretKey = encrypt(credentials.secretKey),
                        provisionedAt = Instant.now(),
                        expiresAt = calculateExpiry(billingCycle),
                )
        )
        provisioningService.logEvent(
                serverId,
                "bundled-object-storage",
                "success",
                "${bundleSpec.totalPurchasedSpaceTB} TB provisioned (${bundleSpec.addonId})",
        )
        log.info("Bundled object storage ${contaboStorage.objectStorageId} linked to server $serverId")
    }

    fun provisionObjectStorage(data: ProvisionJobData) {
        val orderId = data.orderId
        val userId = data.userId

        try {
            val order =
                    orderRepo.findById(orderId).orElse(null)
                            ?: throw RuntimeException("Order $orderId not found")

            val plan = planRepo.findById(order.planId ?: "").orElse(null)
            val region = order.region
            val billingCycle = order.billingCycle
            val displayName = "storage-${orderId.take(8)}"

            // Create object storage
            val contaboStorage =
                    contabo.createObjectStorage(
                            region,
                            plan?.storageTB ?: 0.25,
                            displayName,
                    )

            // Fetch S3 credentials
            val credentials = contabo.getS3Credentials(userId)

            // Encrypt credentials
            val encryptedAccessKey = encrypt(credentials.accessKey)
            val encryptedSecretKey = encrypt(credentials.secretKey)

            // Create record
            objectStorageRepo.save(
                    com.valoracloud.api.entity.ObjectStorage(
                            userId = userId,
                            orderId = orderId,
                            contaboStorageId = contaboStorage.objectStorageId,
                            status = com.valoracloud.api.common.model.ObjectStorageStatus.READY,
                            displayName = displayName,
                            region = region,
                            totalPurchasedSpaceTB = plan?.storageTB ?: 0.25,
                            s3Endpoint = credentials.s3Url,
                            s3AccessKey = encryptedAccessKey,
                            s3SecretKey = encryptedSecretKey,
                            provisionedAt = Instant.now(),
                            expiresAt = calculateExpiry(billingCycle),
                    )
            )

            // Mark order ACTIVE
            order.status = OrderStatus.ACTIVE
            orderRepo.save(order)

            // Send email
            val user = userRepo.findById(userId).orElse(null)
            if (user != null) {
                notifications.sendObjectStorageReadyEmail(
                        email = user.email,
                        displayName = displayName,
                        s3Endpoint = credentials.s3Url,
                        accessKey = credentials.accessKey,
                        secretKey = "********",
                        region = region,
                )
            }

            log.info("Object Storage provisioning complete for order $orderId")
        } catch (e: Exception) {
            log.error("Object Storage provisioning failed for order $orderId: ${e.message}", e)
            orderRepo.findById(orderId).ifPresent { o ->
                o.status = OrderStatus.FAILED
                orderRepo.save(o)
            }
            throw e
        }
    }

    // ─── Domain Provisioning ────────────────────────────

    fun provisionDomain(data: ProvisionJobData) {
        val orderId = data.orderId
        val userId = data.userId

        try {
            val order =
                    orderRepo.findById(orderId).orElse(null)
                            ?: throw RuntimeException("Order $orderId not found")

            val domain =
                    domainRepo.findByOrderId(orderId)
                            ?: throw RuntimeException("Domain not found for order $orderId")

            // Get handles
            val ownerHandle = domainHandleRepo.findById(domain.ownerHandleId).orElse(null)
            val adminHandle = domainHandleRepo.findById(domain.adminHandleId).orElse(null)
            val techHandle = domainHandleRepo.findById(domain.techHandleId).orElse(null)
            val zoneHandle = domainHandleRepo.findById(domain.zoneHandleId).orElse(null)

            if (ownerHandle == null ||
                            adminHandle == null ||
                            techHandle == null ||
                            zoneHandle == null
            ) {
                throw RuntimeException("One or more domain handles not found")
            }

            // WHOIS Privacy
            val effectiveHandles =
                    if (domain.whoisPrivacy) {
                        val privacyHandleId = contabo.getOrCreatePrivacyHandle()
                        com.valoracloud.api.contabo.ContaboDomainHandlesWire(
                                owner = privacyHandleId,
                                admin = privacyHandleId,
                                tech = privacyHandleId,
                                zone = privacyHandleId,
                        )
                    } else {
                        // Push handles to Contabo
                        val handleCache = mutableMapOf<String, String>()

                        fun resolveHandle(h: com.valoracloud.api.entity.DomainHandle): String {
                            handleCache[h.id]?.let {
                                return it
                            }

                            if (h.contaboHandleId?.isNotBlank() == true) {
                                handleCache[h.id] = h.contaboHandleId!!
                                return h.contaboHandleId!!
                            }

                            val result = contabo.createDomainHandle(toContaboHandleRequest(h))

                            // Persist Contabo handle ID
                            h.contaboHandleId = result.handleId
                            domainHandleRepo.save(h)

                            handleCache[h.id] = result.handleId
                            return result.handleId
                        }

                        val ownerCid = resolveHandle(ownerHandle)
                        val adminCid = resolveHandle(adminHandle)
                        val techCid = resolveHandle(techHandle)
                        val zoneCid = resolveHandle(zoneHandle)

                        com.valoracloud.api.contabo.ContaboDomainHandlesWire(
                                owner = ownerCid,
                                admin = adminCid,
                                tech = techCid,
                                zone = zoneCid,
                        )
                    }

            // Convert nameservers
            val nameservers =
                    domain.nameservers.mapNotNull { ns ->
                        val name = ns["name"]?.toString() ?: return@mapNotNull null
                        com.valoracloud.api.contabo.ContaboNameserver(
                                name = name,
                                ip = ns["ip"]?.toString(),
                        )
                    }

            // Register domain
            val contaboDomainResult =
                    contabo.createDomain(
                            com.valoracloud.api.contabo.ContaboCreateDomainRequest(
                                    domain = domain.domainName,
                                    authCode = domain.authCode?.let { decrypt(it) },
                                    handles = effectiveHandles,
                                    nameservers = nameservers,
                            )
                    )

            // Create DNS zone
            try {
                contabo.createDnsZone(domain.domainName)
                log.info("DNS zone created for ${domain.domainName} (order $orderId)")
            } catch (e: Exception) {
                log.warn("Failed to create DNS zone for ${domain.domainName}: ${e.message}")
            }

            // Update domain
            val registeredAt = Instant.now()
            val expiresAt = registeredAt.plusSeconds(365 * 24 * 3600) // 1 year

            domain.status = com.valoracloud.api.common.model.DomainStatus.ACTIVE
            domain.registeredAt = registeredAt
            domain.expiresAt = expiresAt
            domain.authCode = contaboDomainResult.authCode?.let { encrypt(it) }
            domainRepo.save(domain)

            // Mark order ACTIVE
            order.status = OrderStatus.ACTIVE
            orderRepo.save(order)

            // Send confirmation email
            val user = userRepo.findById(userId).orElse(null)
            if (user != null) {
                notifications.sendDomainRegisteredEmail(
                        email = user.email,
                        domainName = domain.domainName,
                        expiresAt = expiresAt.toString(),
                )
            }

            log.info("Domain provisioning complete for order $orderId")
        } catch (e: Exception) {
            log.error("Domain provisioning failed for order $orderId: ${e.message}", e)
            orderRepo.findById(orderId).ifPresent { o ->
                o.status = OrderStatus.FAILED
                orderRepo.save(o)
            }
            throw e
        }
    }

    /** Converts entity DomainHandle → Contabo API request, mapping JSON maps to typed objects. */
    private fun toContaboHandleRequest(
            h: com.valoracloud.api.entity.DomainHandle
    ): com.valoracloud.api.contabo.ContaboCreateDomainHandleRequest {
        val handleType =
                when (h.handleType.lowercase()) {
                    "organization" -> com.valoracloud.api.contabo.HandleType.ORGANIZATION
                    else -> com.valoracloud.api.contabo.HandleType.PERSON
                }
        val gender =
                when (h.gender?.lowercase()) {
                    "male" -> com.valoracloud.api.contabo.Gender.MALE
                    "female" -> com.valoracloud.api.contabo.Gender.FEMALE
                    "na" -> com.valoracloud.api.contabo.Gender.NA
                    else -> null
                }
        val phone =
                h.phone.let { p ->
                    com.valoracloud.api.contabo.ContaboHandlePhone(
                            countryCode = p["countryCode"]?.toString() ?: "",
                            areaCode = p["areaCode"]?.toString(),
                            subscriberNumber = p["subscriberNumber"]?.toString() ?: "",
                    )
                }
        val address =
                h.address.let { a ->
                    com.valoracloud.api.contabo.ContaboHandleAddress(
                            street = a["street"]?.toString() ?: "",
                            streetNumber = a["streetNumber"]?.toString() ?: "",
                            zipCode = a["zipCode"]?.toString() ?: "",
                            city = a["city"]?.toString() ?: "",
                            country = a["country"]?.toString() ?: "",
                    )
                }
        val birthInfo =
                h.birthInfo?.let { bi ->
                    com.valoracloud.api.contabo.ContaboBirthInfo(
                            date = bi["date"]?.toString() ?: "",
                            city = bi["city"]?.toString() ?: "",
                            country = bi["country"]?.toString() ?: "",
                            zipCode = bi["zipCode"]?.toString(),
                            province = bi["province"]?.toString(),
                    )
                }

        return com.valoracloud.api.contabo.ContaboCreateDomainHandleRequest(
                handleType = handleType,
                firstName = h.firstName,
                lastName = h.lastName,
                organization = h.organization,
                email = h.email,
                gender = gender,
                birthInfo = birthInfo,
                address = address,
                phone = phone,
                fax = h.fax,
        )
    }

    // ─── Helpers ────────────────────────────────────────

    private fun waitForInstance(
            instanceId: Long,
            serverId: String
    ): com.valoracloud.api.contabo.ContaboInstance {
        for (i in 1..MAX_POLL_ATTEMPTS) {
            val instance = contabo.getInstance(instanceId)
            if (instance.status == "running" && instance.ipConfig?.v4?.ip != null) {
                return instance
            }
            log.debug("Waiting for instance $instanceId (attempt $i/$MAX_POLL_ATTEMPTS)")
            Thread.sleep(POLL_INTERVAL_MS)
        }
        throw RuntimeException(
                "Instance $instanceId did not become ready within ${MAX_POLL_ATTEMPTS * POLL_INTERVAL_MS / 60_000} minutes"
        )
    }

    private fun generatePassword(): String {
        val random = SecureRandom()
        val bytes = ByteArray(PASSWORD_LENGTH)
        random.nextBytes(bytes)
        return bytes
                .map { b -> PASSWORD_CHARS[(b.toInt() and 0xFF) % PASSWORD_CHARS.size] }
                .joinToString("")
    }

    private fun resolveDefaultUser(os: String): String =
        ProvisioningDefaults.sshUserFor(ProvisioningDefaults.isWindows(os))

    private fun calculateExpiry(billingCycle: Int): Instant {
        val months = if (billingCycle > 0) billingCycle else 1
        return Instant.now().plusSeconds(months.toLong() * 30 * 24 * 3600)
    }

    private fun encrypt(plainText: String): String =
            EncryptionUtil.encrypt(plainText, encryptionKey)

    private fun decrypt(encrypted: String): String =
            EncryptionUtil.decrypt(encrypted, encryptionKey)

    // ─── Post-provisioning SSH ──────────────────────────

    private fun runPostProvisioning(
            ip: String,
            username: String,
            password: String,
            serverId: String,
            brandDomain: String,
            region: String,
            os: String?,
    ) {
        val normalizedOs = (os ?: "").lowercase()
        if (normalizedOs.contains("windows") || username.equals("Administrator", ignoreCase = true)
        ) {
            // Windows post-provisioning — uses WinRM, not SSH
            log.info("Skipping Windows post-provision for $serverId (WinRM not yet implemented)")
            return
        }

        val hostname = customerHostname(serverId, brandDomain)
        val regionLabel = resolveRegionLabel(region)
        val bannerContent = ValoraBranding.bannerContent()
        val motdScript = ValoraBranding.motdScript()

        // Use JSch for SSH
        val session =
                runCatching { connectWithRetry(ip, username, password) }.getOrElse {
                    throw RuntimeException("SSH connection failed for $serverId: ${it.message}", it)
                }

        try {
            val channel = session.openChannel("exec")
            val commands = buildPostProvisionCommands(hostname, regionLabel, bannerContent, motdScript)

            for (cmd in commands) {
                val execChannel = session.openChannel("exec") as com.jcraft.jsch.ChannelExec
                execChannel.setCommand(cmd)
                execChannel.connect(30_000)
                // Wait for completion
                while (!execChannel.isClosed) {
                    Thread.sleep(100)
                }
                if (execChannel.exitStatus != 0) {
                    log.warn("Command failed on $ip: \"$cmd\" → exit=${execChannel.exitStatus}")
                }
                execChannel.disconnect()
            }

            log.info("Post-provisioning complete for $ip")
        } finally {
            session.disconnect()
        }
    }

    private fun connectWithRetry(
            ip: String,
            username: String,
            password: String,
    ): com.jcraft.jsch.Session {
        val jsch = com.jcraft.jsch.JSch()
        var connectFailures = 0
        var authFailures = 0
        val maxConnectAttempts = 15
        val maxAuthAttempts = 20
        val retryDelayMs = 30_000L

        while (true) {
            try {
                val session = jsch.getSession(username, ip, 22)
                session.setPassword(password)
                session.setConfig("StrictHostKeyChecking", "no")
                session.setConfig("PreferredAuthentications", "password,keyboard-interactive")
                session.connect(30_000)
                return session
            } catch (e: Exception) {
                val msg = e.message?.lowercase() ?: ""
                val isAuthError = msg.contains("auth") || msg.contains("userauth_failure")

                if (isAuthError) {
                    authFailures++
                    if (authFailures >= maxAuthAttempts) throw e
                    log.warn(
                            "SSH auth attempt $authFailures/$maxAuthAttempts failed for $ip (cloud-init may still be running): ${e.message}"
                    )
                } else {
                    connectFailures++
                    if (connectFailures >= maxConnectAttempts) throw e
                    log.warn(
                            "SSH connect attempt $connectFailures/$maxConnectAttempts failed for $ip: ${e.message}"
                    )
                }
                Thread.sleep(retryDelayMs)
            }
        }
    }


    private fun resolveRegionLabel(region: String): String {
        return when (region) {
            "EU" -> "eu-de-1 · Nuremberg, Germany"
            "US-central" -> "us-central-1 · Chicago, IL"
            "US-east" -> "us-east-1 · New York, NY"
            "US-west" -> "us-west-1 · Los Angeles, CA"
            "SIN" -> "ap-sin-1 · Singapore"
            "AUS" -> "ap-aus-1 · Sydney, Australia"
            "JPN" -> "ap-jpn-1 · Tokyo, Japan"
            else -> region
        }
    }

}
