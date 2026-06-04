package com.valoracloud.api.provisioning.processor

import com.valoracloud.api.billing.service.ProvisionJobData
import com.valoracloud.api.common.model.OrderStatus
import com.valoracloud.api.common.model.ServerStatus
import com.valoracloud.api.common.utils.EncryptionUtil
import com.valoracloud.api.config.*
import com.valoracloud.api.contabo.ContaboService
import com.valoracloud.api.entity.Server
import com.valoracloud.api.notifications.service.NotificationsService
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

        fun buildCloudInit(rootPassword: String, sshUser: String = "admin"): String {
            val chpasswd = "chpasswd:\n  list: |\n    $sshUser:$rootPassword\n  expire: false\n\n"
            return CLOUD_INIT_BASE.replace("#cloud-config\n", "#cloud-config\n$chpasswd")
        }
    }

    // ─── Entry point ────────────────────────────────────

    /**
     * Runs asynchronously in the provisioningExecutor thread pool so the
     * webhook handler (Stripe / SHKeeper) can return HTTP 200 immediately.
     * NOT @Transactional — provisioning takes 15-25 min; holding a DB
     * connection that long would exhaust the HikariCP pool.
     */
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
        val region = data.region
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

            // Resolve Contabo productId
            val contaboPlanId =
                    plan?.contaboPlanId
                            ?: throw RuntimeException(
                                    "Plan has no contaboPlanId configured for order $orderId"
                            )
            log.info("Using Contabo productId: $contaboPlanId")

            // SSH user (use the actual image name from Contabo to determine default user)
            val sshUser = order.sshUser.ifBlank { resolveDefaultUser(image.name) }.lowercase()

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

            // Create instance
            val instance =
                    contabo.createInstance(
                            contaboPlanId,
                            region,
                            imageId,
                            displayName,
                            secretId,
                            cloudInitB64,
                            sshUser,
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

            // Auto-create ServerMonitor
            runCatching {
                serverMonitorRepo.findByServerId(serverId).let { existing ->
                    if (existing == null) {
                        serverMonitorRepo.save(
                                com.valoracloud.api.entity.ServerMonitor(
                                        serverId = serverId,
                                        isActive = true,
                                        protocol = "tcp",
                                        checkPort = 22,
                                        checkIntervalSeconds = 60,
                                )
                        )
                    } else {
                        existing.isActive = true
                        serverMonitorRepo.save(existing)
                    }
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

    private fun resolveDefaultUser(os: String): String {
        val normalized = os.lowercase()
        return if (normalized.contains("windows")) "administrator" else "root"
    }

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

        val hostname = "srv-${serverId.take(8)}.$brandDomain"
        val regionLabel = resolveRegionLabel(region)
        val bannerContent = buildBannerContent()
        val motdScript = buildMotdScript()

        // Use JSch for SSH
        val session =
                runCatching { connectWithRetry(ip, username, password) }.getOrElse {
                    throw RuntimeException("SSH connection failed for $serverId: ${it.message}", it)
                }

        try {
            val channel = session.openChannel("exec")
            val commands =
                    listOf(
                            // Write Valora meta file
                            "mkdir -p /etc/valora && printf 'fqdn=%s\\nregion_label=%s\\n' '$hostname' '$regionLabel' > /etc/valora/meta",
                            // Write banner
                            buildWriteFileCommand(
                                    "/etc/valora/banner.txt",
                                    bannerContent,
                                    "0644",
                                    "VLRN_BANNER"
                            ),
                            // Write MOTD
                            buildWriteFileCommand(
                                    "/etc/update-motd.d/00-valora",
                                    motdScript,
                                    "0755",
                                    "VLRN_MOTD"
                            ),
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

    private fun buildWriteFileCommand(
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

    private fun buildMotdScript(): String {
        val D = "$"
        return """#!/usr/bin/env bash
cream=${D}${'\u0027'}\033[38;2;243;236;223m${'\u0027'}; white=${D}${'\u0027'}\033[38;2;232;238;247m${'\u0027'}
brick=${D}${'\u0027'}\033[38;2;224;99;94m${'\u0027'};  blue=${D}${'\u0027'}\033[38;2;122;162;255m${'\u0027'}
green=${D}${'\u0027'}\033[38;2;134;239;172m${'\u0027'}; mute=${D}${'\u0027'}\033[38;2;132;144;182m${'\u0027'}
b=${D}${'\u0027'}\033[1m${'\u0027'}; r=${D}${'\u0027'}\033[0m${'\u0027'}
rule="  ${D}{mute}╶───────────────────────────────────────────────────────────────╴${D}{r}"
host=${D}(hostname -s)
fqdn=${D}([ -f /etc/valora/meta ] && grep -m1 '^fqdn=' /etc/valora/meta | cut -d= -f2 || hostname -f 2>/dev/null || hostname)
region_label=${D}([ -f /etc/valora/meta ] && grep -m1 '^region_label=' /etc/valora/meta | cut -d= -f2 || echo '—')
ip4=${D}(hostname -I 2>/dev/null | awk '{print ${D}1}')
ip6=${D}(ip -6 addr show scope global 2>/dev/null | awk '/inet6/{print ${D}2; exit}')
os=${D}(. /etc/os-release 2>/dev/null; echo "${D}{PRETTY_NAME:-Linux}")
cores=${D}(nproc 2>/dev/null || echo "?")
mem=${D}(free | awk '/Mem:/{printf "%d%%", ${D}3/${D}2*100}')
disk=${D}(df -h / | awk 'NR==2{print ${D}5" of "${D}2}')
load=${D}(cut -d" " -f1-3 /proc/loadavg); up=${D}(uptime -p | sed "s/^up //")
upd=${D}(/usr/lib/update-notifier/apt-check --human-readable 2>/dev/null | head -1)
printf '\n%s\n\n' "${D}rule"
while IFS= read -r l; do line=${D}{l/C L O U D/${D}{brick}C L O U D${D}{cream}}; printf '%s%s%s\n' "${D}b${D}cream" "${D}line" "${D}r"; done < /etc/valora/banner.txt
printf '\n   %sInfrastructure that picks up the phone.%s\n%s\n\n' "${D}mute" "${D}r" "${D}rule"
printf '   %sHOST   %s %s%s%s · %s\n' "${D}mute" "${D}r" "${D}white" "${D}host" "${D}r" "${D}fqdn"
printf '   %sREGION %s %-38s %sSTATUS%s %s● operational%s\n' "${D}mute" "${D}r" "${D}region_label" "${D}mute" "${D}r" "${D}green" "${D}r"
printf '   %sIPv4   %s %s%s%s              %sUPTIME%s %s\n' "${D}mute" "${D}r" "${D}white" "${D}ip4" "${D}r" "${D}mute" "${D}r" "${D}up"
printf '   %sIPv6   %s %s        %sLOAD  %s %s\n\n%s\n\n' "${D}mute" "${D}r" "${D}{ip6:---}" "${D}mute" "${D}r" "${D}load" "${D}rule"
printf '   %sSYSTEM %s %s · %s vCPU\n' "${D}mute" "${D}r" "${D}os" "${D}cores"
printf '   %sDISK   %s %s    %sMEM%s %s\n' "${D}mute" "${D}r" "${D}disk" "${D}mute" "${D}r" "${D}mem"
printf '   %sUPDATES%s %s%s%s\n\n%s\n\n' "${D}mute" "${D}r" "${D}green" "${D}{upd:-0 updates}" "${D}r" "${D}rule"
printf '   %spanel  %s %smy.valoracloud.com%s     %sdocs   %s %sdocs.valoracloud.com%s\n' "${D}mute" "${D}r" "${D}blue" "${D}r" "${D}mute" "${D}r" "${D}blue" "${D}r"
printf '   %sstatus %s %sstatus.valoracloud.com%s %ssupport%s %ssupport@valoracloud.com%s · 24/7\n\n%s\n\n' "${D}mute" "${D}r" "${D}blue" "${D}r" "${D}mute" "${D}r" "${D}blue" "${D}r" "${D}rule"
"""
    }

    private fun buildBannerContent(): String {
        return """██╗   ██╗ █████╗ ██╗      ██████╗ ██████╗  █████╗
██║   ██║██╔══██╗██║     ██╔═══██╗██╔══██╗██╔══██╗
██║   ██║███████║██║     ██║   ██║██████╔╝███████║   C L O U D
╚██╗ ██╔╝██╔══██║██║     ██║   ██║██╔══██╗██╔══██║
 ╚████╔╝ ██║  ██║███████╗╚██████╔╝██║  ██║██║  ██║
  ╚═══╝  ╚═╝  ╚═╝╚══════╝ ╚═════╝ ╚═╝  ╚═╝╚═╝  ╚═╝"""
    }
}