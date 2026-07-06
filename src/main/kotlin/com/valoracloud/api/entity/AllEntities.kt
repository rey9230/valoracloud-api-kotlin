package com.valoracloud.api.entity

import com.valoracloud.api.common.model.*
import com.valoracloud.api.cuid
import jakarta.persistence.*
import java.io.Serializable
import java.math.BigDecimal
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.time.Instant
import java.time.LocalDate
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.SQLRestriction
import org.hibernate.annotations.Type
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.type.SqlTypes
import org.hibernate.usertype.UserType
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener

class MonitorStatusUserType : UserType<MonitorStatus> {
    override fun getSqlType() = Types.OTHER
    override fun returnedClass() = MonitorStatus::class.java
    override fun equals(x: MonitorStatus?, y: MonitorStatus?) = x == y
    override fun hashCode(x: MonitorStatus?) = x?.hashCode() ?: 0
    override fun isMutable() = false
    override fun deepCopy(value: MonitorStatus?) = value
    override fun disassemble(value: MonitorStatus?): Serializable = value?.name ?: "UP"
    override fun assemble(cached: Serializable?, owner: Any?) = cached?.let { MonitorStatus.valueOf(it as String) }
    override fun nullSafeGet(rs: ResultSet, position: Int, session: SharedSessionContractImplementor, owner: Any?): MonitorStatus? {
        val v = rs.getString(position) ?: return null
        return MonitorStatus.valueOf(v)
    }
    override fun nullSafeSet(st: PreparedStatement, value: MonitorStatus?, index: Int, session: SharedSessionContractImplementor) {
        if (value == null) st.setNull(index, Types.OTHER)
        else st.setObject(index, value.name, Types.OTHER)
    }
}

class PaymentMethodUserType : UserType<PaymentMethod> {
    override fun getSqlType() = Types.OTHER
    override fun returnedClass() = PaymentMethod::class.java
    override fun equals(x: PaymentMethod?, y: PaymentMethod?) = x == y
    override fun hashCode(x: PaymentMethod?) = x?.hashCode() ?: 0
    override fun isMutable() = false
    override fun deepCopy(value: PaymentMethod?) = value
    override fun disassemble(value: PaymentMethod?): Serializable = value?.name ?: "STRIPE"
    override fun assemble(cached: Serializable?, owner: Any?) = cached?.let { PaymentMethod.valueOf(it as String) }
    override fun nullSafeGet(rs: ResultSet, position: Int, session: SharedSessionContractImplementor, owner: Any?): PaymentMethod? {
        val v = rs.getString(position) ?: return null
        return PaymentMethod.valueOf(v)
    }
    override fun nullSafeSet(st: PreparedStatement, value: PaymentMethod?, index: Int, session: SharedSessionContractImplementor) {
        if (value == null) st.setNull(index, Types.OTHER)
        else st.setObject(index, value.name, Types.OTHER)
    }
}

class OrderStatusUserType : UserType<OrderStatus> {
    override fun getSqlType() = Types.OTHER
    override fun returnedClass() = OrderStatus::class.java
    override fun equals(x: OrderStatus?, y: OrderStatus?) = x == y
    override fun hashCode(x: OrderStatus?) = x?.hashCode() ?: 0
    override fun isMutable() = false
    override fun deepCopy(value: OrderStatus?) = value
    override fun disassemble(value: OrderStatus?): Serializable = value?.name ?: "PENDING_PAYMENT"
    override fun assemble(cached: Serializable?, owner: Any?) = cached?.let { OrderStatus.valueOf(it as String) }
    override fun nullSafeGet(rs: ResultSet, position: Int, session: SharedSessionContractImplementor, owner: Any?): OrderStatus? {
        val v = rs.getString(position) ?: return null
        return OrderStatus.valueOf(v)
    }
    override fun nullSafeSet(st: PreparedStatement, value: OrderStatus?, index: Int, session: SharedSessionContractImplementor) {
        if (value == null) st.setNull(index, Types.OTHER)
        else st.setObject(index, value.name, Types.OTHER)
    }
}

class ServiceTypeUserType : UserType<ServiceType> {
    override fun getSqlType() = Types.OTHER
    override fun returnedClass() = ServiceType::class.java
    override fun equals(x: ServiceType?, y: ServiceType?) = x == y
    override fun hashCode(x: ServiceType?) = x?.hashCode() ?: 0
    override fun isMutable() = false
    override fun deepCopy(value: ServiceType?) = value
    override fun disassemble(value: ServiceType?): Serializable = value?.name ?: "COMPUTE"
    override fun assemble(cached: Serializable?, owner: Any?) = cached?.let { ServiceType.valueOf(it as String) }
    override fun nullSafeGet(rs: ResultSet, position: Int, session: SharedSessionContractImplementor, owner: Any?): ServiceType? {
        val v = rs.getString(position) ?: return null
        return ServiceType.valueOf(v)
    }
    override fun nullSafeSet(st: PreparedStatement, value: ServiceType?, index: Int, session: SharedSessionContractImplementor) {
        if (value == null) st.setNull(index, Types.OTHER)
        else st.setObject(index, value.name, Types.OTHER)
    }
}

class RoleUserType : UserType<Role> {
    override fun getSqlType() = Types.OTHER
    override fun returnedClass() = Role::class.java
    override fun equals(x: Role?, y: Role?) = x == y
    override fun hashCode(x: Role?) = x?.hashCode() ?: 0
    override fun isMutable() = false
    override fun deepCopy(value: Role?) = value
    override fun disassemble(value: Role?): Serializable = value?.name ?: "CLIENT"
    override fun assemble(cached: Serializable?, owner: Any?) = cached?.let { Role.valueOf(it as String) }
    override fun nullSafeGet(rs: ResultSet, position: Int, session: SharedSessionContractImplementor, owner: Any?): Role? {
        val v = rs.getString(position) ?: return null
        return Role.valueOf(v)
    }
    override fun nullSafeSet(st: PreparedStatement, value: Role?, index: Int, session: SharedSessionContractImplementor) {
        if (value == null) st.setNull(index, Types.OTHER)
        else st.setObject(index, value.name, Types.OTHER)
    }
}

class UserStatusUserType : UserType<UserStatus> {
    override fun getSqlType() = Types.OTHER
    override fun returnedClass() = UserStatus::class.java
    override fun equals(x: UserStatus?, y: UserStatus?) = x == y
    override fun hashCode(x: UserStatus?) = x?.hashCode() ?: 0
    override fun isMutable() = false
    override fun deepCopy(value: UserStatus?) = value
    override fun disassemble(value: UserStatus?): Serializable = value?.name ?: "ACTIVE"
    override fun assemble(cached: Serializable?, owner: Any?) = cached?.let { UserStatus.valueOf(it as String) }
    override fun nullSafeGet(rs: ResultSet, position: Int, session: SharedSessionContractImplementor, owner: Any?): UserStatus? {
        val v = rs.getString(position) ?: return null
        return UserStatus.valueOf(v)
    }
    override fun nullSafeSet(st: PreparedStatement, value: UserStatus?, index: Int, session: SharedSessionContractImplementor) {
        if (value == null) st.setNull(index, Types.OTHER)
        else st.setObject(index, value.name, Types.OTHER)
    }
}

class ProductTypeUserType : UserType<ProductType> {
    override fun getSqlType() = Types.OTHER
    override fun returnedClass() = ProductType::class.java
    override fun equals(x: ProductType?, y: ProductType?) = x == y
    override fun hashCode(x: ProductType?) = x?.hashCode() ?: 0
    override fun isMutable() = false
    override fun deepCopy(value: ProductType?) = value
    override fun disassemble(value: ProductType?): Serializable = value?.name ?: "CLOUD_VPS"
    override fun assemble(cached: Serializable?, owner: Any?) = cached?.let { ProductType.valueOf(it as String) }
    override fun nullSafeGet(rs: ResultSet, position: Int, session: SharedSessionContractImplementor, owner: Any?): ProductType? {
        val v = rs.getString(position) ?: return null
        return ProductType.valueOf(v)
    }
    override fun nullSafeSet(st: PreparedStatement, value: ProductType?, index: Int, session: SharedSessionContractImplementor) {
        if (value == null) st.setNull(index, Types.OTHER)
        else st.setObject(index, value.name, Types.OTHER)
    }
}

class PlanStatusUserType : UserType<PlanStatus> {
    override fun getSqlType() = Types.OTHER
    override fun returnedClass() = PlanStatus::class.java
    override fun equals(x: PlanStatus?, y: PlanStatus?) = x == y
    override fun hashCode(x: PlanStatus?) = x?.hashCode() ?: 0
    override fun isMutable() = false
    override fun deepCopy(value: PlanStatus?) = value
    override fun disassemble(value: PlanStatus?): Serializable = value?.name ?: "ACTIVE"
    override fun assemble(cached: Serializable?, owner: Any?) = cached?.let { PlanStatus.valueOf(it as String) }
    override fun nullSafeGet(rs: ResultSet, position: Int, session: SharedSessionContractImplementor, owner: Any?): PlanStatus? {
        val v = rs.getString(position) ?: return null
        return PlanStatus.valueOf(v)
    }
    override fun nullSafeSet(st: PreparedStatement, value: PlanStatus?, index: Int, session: SharedSessionContractImplementor) {
        if (value == null) st.setNull(index, Types.OTHER)
        else st.setObject(index, value.name, Types.OTHER)
    }
}

class ServerStatusUserType : UserType<ServerStatus> {
    override fun getSqlType() = Types.OTHER
    override fun returnedClass() = ServerStatus::class.java
    override fun equals(x: ServerStatus?, y: ServerStatus?) = x == y
    override fun hashCode(x: ServerStatus?) = x?.hashCode() ?: 0
    override fun isMutable() = false
    override fun deepCopy(value: ServerStatus?) = value
    override fun disassemble(value: ServerStatus?): Serializable = value?.name ?: "PROVISIONING"
    override fun assemble(cached: Serializable?, owner: Any?) = cached?.let { ServerStatus.valueOf(it as String) }
    override fun nullSafeGet(rs: ResultSet, position: Int, session: SharedSessionContractImplementor, owner: Any?): ServerStatus? {
        val v = rs.getString(position) ?: return null
        return ServerStatus.valueOf(v)
    }
    override fun nullSafeSet(st: PreparedStatement, value: ServerStatus?, index: Int, session: SharedSessionContractImplementor) {
        if (value == null) st.setNull(index, Types.OTHER)
        else st.setObject(index, value.name, Types.OTHER)
    }
}

class ObjectStorageStatusUserType : UserType<ObjectStorageStatus> {
    override fun getSqlType() = Types.OTHER
    override fun returnedClass() = ObjectStorageStatus::class.java
    override fun equals(x: ObjectStorageStatus?, y: ObjectStorageStatus?) = x == y
    override fun hashCode(x: ObjectStorageStatus?) = x?.hashCode() ?: 0
    override fun isMutable() = false
    override fun deepCopy(value: ObjectStorageStatus?) = value
    override fun disassemble(value: ObjectStorageStatus?): Serializable = value?.name ?: "PROVISIONING"
    override fun assemble(cached: Serializable?, owner: Any?) = cached?.let { ObjectStorageStatus.valueOf(it as String) }
    override fun nullSafeGet(rs: ResultSet, position: Int, session: SharedSessionContractImplementor, owner: Any?): ObjectStorageStatus? {
        val v = rs.getString(position) ?: return null
        return ObjectStorageStatus.valueOf(v)
    }
    override fun nullSafeSet(st: PreparedStatement, value: ObjectStorageStatus?, index: Int, session: SharedSessionContractImplementor) {
        if (value == null) st.setNull(index, Types.OTHER)
        else st.setObject(index, value.name, Types.OTHER)
    }
}

class DomainStatusUserType : UserType<DomainStatus> {
    override fun getSqlType() = Types.OTHER
    override fun returnedClass() = DomainStatus::class.java
    override fun equals(x: DomainStatus?, y: DomainStatus?) = x == y
    override fun hashCode(x: DomainStatus?) = x?.hashCode() ?: 0
    override fun isMutable() = false
    override fun deepCopy(value: DomainStatus?) = value
    override fun disassemble(value: DomainStatus?): Serializable = value?.name ?: "PENDING"
    override fun assemble(cached: Serializable?, owner: Any?) = cached?.let { DomainStatus.valueOf(it as String) }
    override fun nullSafeGet(rs: ResultSet, position: Int, session: SharedSessionContractImplementor, owner: Any?): DomainStatus? {
        val v = rs.getString(position) ?: return null
        return DomainStatus.valueOf(v)
    }
    override fun nullSafeSet(st: PreparedStatement, value: DomainStatus?, index: Int, session: SharedSessionContractImplementor) {
        if (value == null) st.setNull(index, Types.OTHER)
        else st.setObject(index, value.name, Types.OTHER)
    }
}

class MonitorAlertTypeUserType : UserType<MonitorAlertType> {
    override fun getSqlType() = Types.OTHER
    override fun returnedClass() = MonitorAlertType::class.java
    override fun equals(x: MonitorAlertType?, y: MonitorAlertType?) = x == y
    override fun hashCode(x: MonitorAlertType?) = x?.hashCode() ?: 0
    override fun isMutable() = false
    override fun deepCopy(value: MonitorAlertType?) = value
    override fun disassemble(value: MonitorAlertType?): Serializable = value?.name ?: "DOWN"
    override fun assemble(cached: Serializable?, owner: Any?) = cached?.let { MonitorAlertType.valueOf(it as String) }
    override fun nullSafeGet(rs: ResultSet, position: Int, session: SharedSessionContractImplementor, owner: Any?): MonitorAlertType? {
        val v = rs.getString(position) ?: return null
        return MonitorAlertType.valueOf(v)
    }
    override fun nullSafeSet(st: PreparedStatement, value: MonitorAlertType?, index: Int, session: SharedSessionContractImplementor) {
        if (value == null) st.setNull(index, Types.OTHER)
        else st.setObject(index, value.name, Types.OTHER)
    }
}

class MonitorAlertSeverityUserType : UserType<MonitorAlertSeverity> {
    override fun getSqlType() = Types.OTHER
    override fun returnedClass() = MonitorAlertSeverity::class.java
    override fun equals(x: MonitorAlertSeverity?, y: MonitorAlertSeverity?) = x == y
    override fun hashCode(x: MonitorAlertSeverity?) = x?.hashCode() ?: 0
    override fun isMutable() = false
    override fun deepCopy(value: MonitorAlertSeverity?) = value
    override fun disassemble(value: MonitorAlertSeverity?): Serializable = value?.name ?: "CRITICAL"
    override fun assemble(cached: Serializable?, owner: Any?) = cached?.let { MonitorAlertSeverity.valueOf(it as String) }
    override fun nullSafeGet(rs: ResultSet, position: Int, session: SharedSessionContractImplementor, owner: Any?): MonitorAlertSeverity? {
        val v = rs.getString(position) ?: return null
        return MonitorAlertSeverity.valueOf(v)
    }
    override fun nullSafeSet(st: PreparedStatement, value: MonitorAlertSeverity?, index: Int, session: SharedSessionContractImplementor) {
        if (value == null) st.setNull(index, Types.OTHER)
        else st.setObject(index, value.name, Types.OTHER)
    }
}

// ─────────────────────────────────────────────────────────
// Base
// ─────────────────────────────────────────────────────────
@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseEntity {
    @CreatedDate
    @Column(name = "createdAt", updatable = false, nullable = false)
    lateinit var createdAt: Instant
        protected set
    @LastModifiedDate
    @Column(name = "updatedAt", nullable = false)
    lateinit var updatedAt: Instant
        protected set
}

// ─────────────────────────────────────────────────────────
// User & Auth
// ─────────────────────────────────────────────────────────
@Entity
@Table(name = "User")
@SQLRestriction("\"deletedAt\" IS NULL")
class User(
        @Id var id: String = cuid(),
        @Column(unique = true, nullable = false) var email: String = "",
        @Column(nullable = false) var password: String = "",
        @Column(name = "firstName") var firstName: String = "",
        @Column(name = "lastName") var lastName: String = "",
        @Type(RoleUserType::class) @Column(name = "role") var role: Role = Role.CLIENT,
        @Type(UserStatusUserType::class) @Column(name = "status") var status: UserStatus = UserStatus.ACTIVE,
        @Column(name = "emailVerified") var emailVerified: Boolean = false,
        var language: String = "en",
        @Column(name = "deletedAt") var deletedAt: Instant? = null,
) : BaseEntity()

@Entity
@Table(
        name = "RefreshToken",
        indexes = [Index(columnList = "userId"), Index(columnList = "token")]
)
class RefreshToken(
        @Id var id: String = cuid(),
        @Column(unique = true) var token: String = "",
        @Column(name = "userId", nullable = false) var userId: String = "",
        @Column(name = "expiresAt", nullable = false) var expiresAt: Instant = Instant.now(),
        @Column(name = "revokedAt") var revokedAt: Instant? = null,
        @Column(name = "createdAt", updatable = false, nullable = false) var createdAt: Instant = Instant.now(),
)

// ─────────────────────────────────────────────────────────
// Plan
// ─────────────────────────────────────────────────────────
@Entity
@Table(name = "Plan", indexes = [Index(columnList = "productType")])
class Plan(
        @Id var id: String = cuid(),
        @Column(nullable = false) var name: String = "",
        @Column(nullable = false, unique = true) var slug: String = "",
        @Type(ProductTypeUserType::class)
        @Column(name = "productType", nullable = false)
        var productType: ProductType = ProductType.CLOUD_VPS,
        var description: String? = null,
        var cpu: Int = 1,
        var ram: Int = 1,
        var disk: Int = 25,
        @Column(name = "diskType") var diskType: String = "NVMe",
        var bandwidth: Int = 1,
        @Column(name = "portSpeed") var portSpeed: Int? = null,
        var snapshots: Int = 0,
        @Column(name = "price1Month", precision = 10, scale = 2)
        var price1Month: BigDecimal = BigDecimal.ZERO,
        @Column(name = "price6Months", precision = 10, scale = 2)
        var price6Months: BigDecimal = BigDecimal.ZERO,
        @Column(name = "price12Months", precision = 10, scale = 2)
        var price12Months: BigDecimal = BigDecimal.ZERO,
        @Column(name = "setup1Month", precision = 10, scale = 2)
        var setup1Month: BigDecimal = BigDecimal.ZERO,
        @Column(name = "setup6Months", precision = 10, scale = 2)
        var setup6Months: BigDecimal = BigDecimal.ZERO,
        @Column(name = "setup12Months", precision = 10, scale = 2)
        var setup12Months: BigDecimal = BigDecimal.ZERO,
        @Column(name = "priceMonthly", precision = 10, scale = 2) var priceMonthly: BigDecimal = BigDecimal.ZERO,
        @Column(name = "contaboPlanId") var contaboPlanId: String = "",
        @Column(name = "contaboPlanIdSsd") var contaboPlanIdSsd: String? = null,
        @Column(name = "contaboPlanIdStorage") var contaboPlanIdStorage: String? = null,
        @Column(name = "contaboPlanIdWindows") var contaboPlanIdWindows: String? = null,
        @Column(name = "contaboCostPrice", precision = 10, scale = 2) var contaboCostPrice: BigDecimal? = null,
        @Column(name = "marginPercent", precision = 5, scale = 2) var marginPercent: BigDecimal? = null,
        @JdbcTypeCode(SqlTypes.JSON) @Column(name = "regions", columnDefinition = "jsonb") var regions: com.fasterxml.jackson.databind.JsonNode = com.fasterxml.jackson.databind.node.NullNode.instance,
        @JdbcTypeCode(SqlTypes.JSON) @Column(name = "availableAddons", columnDefinition = "jsonb") var availableAddons: com.fasterxml.jackson.databind.JsonNode = com.fasterxml.jackson.databind.node.NullNode.instance,
        @Column(name = "storageTB") var storageTB: Double? = null,
        @Column(name = "sortOrder") var sortOrder: Int = 0,
        @Type(PlanStatusUserType::class) var status: PlanStatus = PlanStatus.ACTIVE,
)

// ─────────────────────────────────────────────────────────
// Order
// ─────────────────────────────────────────────────────────
@Entity
@Table(name = "Order", indexes = [Index(columnList = "userId"), Index(columnList = "status")])
class Order(
        @Id var id: String = cuid(),
        @Column(name = "userId", nullable = false) var userId: String = "",
        @Column(name = "planId") var planId: String? = null,
        @Type(ServiceTypeUserType::class) @Column(name = "serviceType") var serviceType: ServiceType = ServiceType.COMPUTE,
        @Type(OrderStatusUserType::class) @Column(name = "status") var status: OrderStatus = OrderStatus.PENDING_PAYMENT,
        @Type(PaymentMethodUserType::class) @Column(name = "paymentMethod") var paymentMethod: PaymentMethod = PaymentMethod.STRIPE,
        @Column(name = "stripePaymentId") var stripePaymentId: String? = null,
        @Column(name = "cryptoPaymentId") var cryptoPaymentId: String? = null,
        @Column(name = "cryptoCurrency") var cryptoCurrency: String? = null,
        @Column(name = "billingCycle") var billingCycle: Int = 1,
        @Column(name = "basePrice", precision = 10, scale = 2) var basePrice: BigDecimal = BigDecimal.ZERO,
        @Column(name = "addonsPrice", precision = 10, scale = 2) var addonsPrice: BigDecimal = BigDecimal.ZERO,
        @Column(name = "setupFee", precision = 10, scale = 2) var setupFee: BigDecimal = BigDecimal.ZERO,
        @Column(name = "totalAmount", precision = 10, scale = 2) var totalAmount: BigDecimal = BigDecimal.ZERO,
        var region: String = "EU",
        var os: String = "ubuntu-24.04",
        @JdbcTypeCode(SqlTypes.JSON) var addons: List<String> = emptyList(),
        @Column(name = "sshUser") var sshUser: String = "root",
        var hostname: String? = null,
        @Column(name = "rootPassword") var rootPassword: String? = null,
) : BaseEntity()

// ─────────────────────────────────────────────────────────
// Server
// ─────────────────────────────────────────────────────────
@Entity
@Table(name = "Server", indexes = [Index(columnList = "userId"), Index(columnList = "status")])
class Server(
        @Id var id: String = cuid(),
        @Column(name = "userId", nullable = false) var userId: String = "",
        @Column(name = "orderId", unique = true, nullable = false) var orderId: String = "",
        @Column(name = "contaboInstanceId", unique = true, nullable = false) var contaboInstanceId: String = "",
        @Type(ServerStatusUserType::class) @Column(name = "status") var status: ServerStatus = ServerStatus.PROVISIONING,
        var hostname: String = "",
        @Column(name = "ipAddress") var ipAddress: String? = null,
        @Column(name = "sshUser") var sshUser: String = "root",
        @Column(name = "rootPassword", nullable = false) var rootPassword: String = "",
        var os: String = "",
        var region: String = "EU",
        @JdbcTypeCode(SqlTypes.JSON) @Column(name = "contaboData") var contaboData: Map<String, Any>? = null,
        @Column(name = "provisionedAt") var provisionedAt: Instant? = null,
        @Column(name = "expiresAt") var expiresAt: Instant? = null,
) : BaseEntity()

@Entity
@Table(name = "ProvisioningLog", indexes = [Index(columnList = "serverId")])
class ProvisioningLog(
        @Id var id: String = cuid(),
        @Column(name = "serverId", nullable = false) var serverId: String = "",
        var step: String = "",
        var status: String = "",
        var message: String? = null,
        @Column(name = "createdAt") var createdAt: Instant = Instant.now(),
)

// ─────────────────────────────────────────────────────────
// Invoice
// ─────────────────────────────────────────────────────────
@Entity
@Table(name = "Invoice", indexes = [Index(columnList = "userId"), Index(columnList = "orderId")])
class Invoice(
        @Id var id: String = cuid(),
        @Column(name = "userId", nullable = false) var userId: String = "",
        @Column(name = "orderId", nullable = false) var orderId: String = "",
        @Column(precision = 10, scale = 2) var amount: BigDecimal = BigDecimal.ZERO,
        var currency: String = "USD",
        @Type(PaymentMethodUserType::class) @Column(name = "paymentMethod") var paymentMethod: PaymentMethod = PaymentMethod.STRIPE,
        @Column(name = "stripeInvoiceId") var stripeInvoiceId: String? = null,
        @Column(name = "cryptoTxId") var cryptoTxId: String? = null,
        @Column(name = "cryptoCurrency") var cryptoCurrency: String? = null,
        @Column(name = "cryptoAmount") var cryptoAmount: String? = null,
        @Column(name = "paidAt") var paidAt: Instant? = null,
        @Column(name = "createdAt") var createdAt: Instant = Instant.now(),
)

@Entity
@Table(name = "WebhookEvent", indexes = [Index(columnList = "externalId")])
class WebhookEvent(
        @Id var id: String = cuid(),
        @Column(name = "stripeEventId", unique = true) var stripeEventId: String? = null,
        @Column(name = "externalId") var externalId: String? = null,
        @Column(name = "eventSource") var eventSource: String = "stripe",
        @Column(name = "eventType") var eventType: String = "",
        var processed: Boolean = false,
        @Column(name = "createdAt") var createdAt: Instant = Instant.now(),
)

// ─────────────────────────────────────────────────────────
// Object Storage
// ─────────────────────────────────────────────────────────
@Entity
@Table(
        name = "ObjectStorage",
        indexes = [Index(columnList = "userId"), Index(columnList = "status")]
)
class ObjectStorage(
        @Id var id: String = cuid(),
        @Column(name = "userId", nullable = false) var userId: String = "",
        @Column(name = "orderId", nullable = false) var orderId: String = "",
        @Column(name = "serverId") var serverId: String? = null,
        @Column(name = "contaboStorageId", unique = true) var contaboStorageId: String = "",
        @Type(ObjectStorageStatusUserType::class) @Column(name = "status")
        var status: ObjectStorageStatus = ObjectStorageStatus.PROVISIONING,
        @Column(name = "displayName") var displayName: String? = null,
        var region: String = "EU",
        @Column(name = "totalPurchasedSpaceTB") var totalPurchasedSpaceTB: Double = 0.25,
        @Column(name = "usedSpaceTB") var usedSpaceTB: Double? = 0.0,
        @JdbcTypeCode(SqlTypes.JSON) @Column(name = "autoScaling") var autoScaling: Map<String, Any>? = null,
        @Column(name = "s3Endpoint") var s3Endpoint: String? = null,
        @Column(name = "s3AccessKey") var s3AccessKey: String? = null,
        @Column(name = "s3SecretKey") var s3SecretKey: String? = null,
        @Column(name = "provisionedAt") var provisionedAt: Instant? = null,
        @Column(name = "expiresAt") var expiresAt: Instant? = null,
) : BaseEntity()

// ─────────────────────────────────────────────────────────
// Domain
// ─────────────────────────────────────────────────────────
@Entity
@Table(name = "DomainHandle", indexes = [Index(columnList = "userId")])
class DomainHandle(
        @Id var id: String = cuid(),
        @Column(name = "userId", nullable = false) var userId: String = "",
        @Column(name = "contaboHandleId") var contaboHandleId: String? = null,
        @Column(name = "handleType") var handleType: String = "", // person | organization
        @Column(name = "firstName") var firstName: String = "",
        @Column(name = "lastName") var lastName: String = "",
        var organization: String? = null,
        var email: String = "",
        var gender: String? = null,
        @JdbcTypeCode(SqlTypes.JSON) @Column(name = "birthInfo") var birthInfo: Map<String, Any>? = null,
        @JdbcTypeCode(SqlTypes.JSON) var address: Map<String, Any> = emptyMap(),
        @JdbcTypeCode(SqlTypes.JSON) var phone: Map<String, Any> = emptyMap(),
        var fax: String? = null,
) : BaseEntity()

@Entity
@Table(name = "DomainTldPricing")
class DomainTldPricing(
        @Id var id: String = cuid(),
        @Column(unique = true) var tld: String = "",
        @Column(name = "registrationPrice", precision = 10, scale = 2) var registrationPrice: BigDecimal = BigDecimal.ZERO,
        @Column(name = "renewalPrice", precision = 10, scale = 2) var renewalPrice: BigDecimal = BigDecimal.ZERO,
        @Column(name = "transferPrice", precision = 10, scale = 2) var transferPrice: BigDecimal = BigDecimal.ZERO,
        var available: Boolean = true,
) : BaseEntity()

@Entity
@Table(
        name = "Domain",
        indexes =
                [
                        Index(columnList = "userId"),
                        Index(columnList = "status"),
                        Index(columnList = "domainName")]
)
class Domain(
        @Id var id: String = cuid(),
        @Column(name = "userId", nullable = false) var userId: String = "",
        @Column(name = "orderId", unique = true, nullable = false) var orderId: String = "",
        @Column(name = "tldPricingId", nullable = false) var tldPricingId: String = "",
        @Column(name = "domainName", unique = true) var domainName: String = "",
        @Column(name = "authCode") var authCode: String? = null,
        @Type(DomainStatusUserType::class) @Column(name = "status") var status: DomainStatus = DomainStatus.PENDING,
        @Column(name = "ownerHandleId", nullable = false) var ownerHandleId: String = "",
        @Column(name = "adminHandleId", nullable = false) var adminHandleId: String = "",
        @Column(name = "techHandleId", nullable = false) var techHandleId: String = "",
        @Column(name = "zoneHandleId", nullable = false) var zoneHandleId: String = "",
        @JdbcTypeCode(SqlTypes.JSON) var nameservers: List<Map<String, Any>> = emptyList(),
        @Column(name = "autoRenew") var autoRenew: Boolean = true,
        @Column(name = "whoisPrivacy") var whoisPrivacy: Boolean = false,
        @Column(name = "registeredAt") var registeredAt: Instant? = null,
        @Column(name = "expiresAt") var expiresAt: Instant? = null,
) : BaseEntity()

// ─────────────────────────────────────────────────────────
// Snapshot
// ─────────────────────────────────────────────────────────
@Entity
@Table(
        name = "Snapshot",
        indexes = [Index(columnList = "serverId"), Index(columnList = "contaboSnapshotId")]
)
class Snapshot(
        @Id var id: String = cuid(),
        @Column(name = "serverId", nullable = false) var serverId: String = "",
        @Column(name = "contaboSnapshotId", unique = true) var contaboSnapshotId: String = "",
        var name: String = "",
        var description: String? = null,
) : BaseEntity()

// ─────────────────────────────────────────────────────────
// Private Network
// ─────────────────────────────────────────────────────────
@Entity
@Table(
        name = "PrivateNetwork",
        indexes = [Index(columnList = "userId"), Index(columnList = "contaboNetworkId")]
)
class PrivateNetwork(
        @Id var id: String = cuid(),
        @Column(name = "userId", nullable = false) var userId: String = "",
        @Column(name = "contaboNetworkId", unique = true) var contaboNetworkId: String = "",
        var name: String = "",
        var description: String? = null,
        var region: String = "EU",
        @Column(name = "dataCenter") var dataCenter: String? = null,
        var cidr: String? = null,
) : BaseEntity()

@Entity
@Table(
        name = "PrivateNetworkAssignment",
        indexes = [Index(columnList = "privateNetworkId"), Index(columnList = "serverId")]
)
// (unique constraint merged via @Table)
class PrivateNetworkAssignment(
        @Id var id: String = cuid(),
        @Column(name = "privateNetworkId", nullable = false) var privateNetworkId: String = "",
        @Column(name = "serverId", nullable = false) var serverId: String = "",
        @Column(name = "ipAddress") var ipAddress: String? = null,
        @Column(name = "createdAt") var createdAt: Instant = Instant.now(),
)

// ─────────────────────────────────────────────────────────
// Firewall
// ─────────────────────────────────────────────────────────
@Entity
@Table(
        name = "Firewall",
        indexes = [Index(columnList = "userId"), Index(columnList = "contaboFirewallId")]
)
class Firewall(
        @Id var id: String = cuid(),
        @Column(name = "userId", nullable = false) var userId: String = "",
        @Column(name = "contaboFirewallId", unique = true) var contaboFirewallId: String = "",
        var name: String = "",
        var description: String? = null,
        var status: String = "active",
) : BaseEntity()

@Entity
@Table(name = "FirewallRule", indexes = [Index(columnList = "firewallId")])
class FirewallRule(
        @Id var id: String = cuid(),
        @Column(name = "firewallId", nullable = false) var firewallId: String = "",
        var protocol: String = "",
        var port: Int? = null,
        @Column(name = "portRange") var portRange: String? = null,
        @Column(name = "sourceIp") var sourceIp: String? = null,
        @Column(name = "sourceNet") var sourceNet: String? = null,
        var action: String = "allow",
        @Column(name = "createdAt") var createdAt: Instant = Instant.now(),
)

@Entity
@Table(
        name = "FirewallAssignment",
        indexes = [Index(columnList = "firewallId"), Index(columnList = "serverId")]
)
// (unique constraint merged via @Table)
class FirewallAssignment(
        @Id var id: String = cuid(),
        @Column(name = "firewallId", nullable = false) var firewallId: String = "",
        @Column(name = "serverId", nullable = false) var serverId: String = "",
        @Column(name = "createdAt") var createdAt: Instant = Instant.now(),
)

// ─────────────────────────────────────────────────────────
// VIP
// ─────────────────────────────────────────────────────────
@Entity
@Table(name = "Vip", indexes = [Index(columnList = "userId"), Index(columnList = "ip")])
class Vip(
        @Id var id: String = cuid(),
        @Column(name = "userId", nullable = false) var userId: String = "",
        @Column(unique = true) var ip: String = "",
        @Column(name = "resourceId") var resourceId: String? = null,
        @Column(name = "resourceType") var resourceType: String? = null,
        @Column(name = "ipVersion") var ipVersion: String = "v4",
        var type: String = "",
        @Column(name = "dataCenter") var dataCenter: String? = null,
        var region: String = "EU",
) : BaseEntity()

// ─────────────────────────────────────────────────────────
// Secret & Tag
// ─────────────────────────────────────────────────────────
@Entity
@Table(name = "Secret", indexes = [Index(columnList = "userId")])
class Secret(
        @Id var id: String = cuid(),
        @Column(name = "userId", nullable = false) var userId: String = "",
        @Column(name = "contaboId", unique = true) var contaboId: Int = 0,
        var name: String = "",
        var type: String = "", // ssh | password
) : BaseEntity()

@Entity
@Table(name = "Tag", indexes = [Index(columnList = "userId")])
class Tag(
        @Id var id: String = cuid(),
        @Column(name = "userId", nullable = false) var userId: String = "",
        @Column(name = "contaboId", unique = true) var contaboId: Int = 0,
        var name: String = "",
        var color: String? = null,
) : BaseEntity()

// ─────────────────────────────────────────────────────────
// Monitoring
// ─────────────────────────────────────────────────────────
@Entity
@Table(name = "ServerMonitor", indexes = [Index(columnList = "isActive")])
class ServerMonitor(
        @Id var id: String = cuid(),
        @Column(name = "serverId", unique = true, nullable = false) var serverId: String = "",
        @Column(name = "isActive") var isActive: Boolean = true,
        var protocol: String = "https",
        @Column(name = "checkUrl") var checkUrl: String? = null,
        @Column(name = "checkPort") var checkPort: Int = 80,
        @Column(name = "checkIntervalSeconds") var checkIntervalSeconds: Int = 60,
        @Column(name = "agentPort") var agentPort: Int? = null,
        @Column(name = "agentSecret") var agentSecret: String? = null,
        @Column(name = "alertThresholdPingMs") var alertThresholdPingMs: Int = 200,
        @Column(name = "alertThresholdCpuPct") var alertThresholdCpuPct: Int = 85,
        @Column(name = "alertThresholdRamPct") var alertThresholdRamPct: Int = 85,
        @Column(name = "alertThresholdDiskPct") var alertThresholdDiskPct: Int = 90,
        @Column(name = "notifyEmail") var notifyEmail: String? = null,
        @Column(name = "notifyTelegramChatId") var notifyTelegramChatId: String? = null,
        var notes: String? = null,
) : BaseEntity()

@Entity
@Table(name = "ServerCheck", indexes = [Index(columnList = "monitorId")])
class ServerCheck(
        @Id var id: String = cuid(),
        @Column(name = "monitorId", nullable = false) var monitorId: String = "",
        @Column(name = "checkedAt") var checkedAt: Instant = Instant.now(),
        @Type(MonitorStatusUserType::class) @Column(name = "status") var status: MonitorStatus = MonitorStatus.UP,
        @Column(name = "pingMs") var pingMs: Int? = null,
        @Column(name = "httpStatusCode") var httpStatusCode: Int? = null,
        @Column(name = "httpResponseTimeMs") var httpResponseTimeMs: Int? = null,
        @Column(name = "httpResponseBodySnippet") var httpResponseBodySnippet: String? = null,
        @Column(name = "tcpOpen") var tcpOpen: Boolean? = null,
        @Column(name = "sslValid") var sslValid: Boolean? = null,
        @Column(name = "sslDaysUntilExpiry") var sslDaysUntilExpiry: Int? = null,
        @Column(name = "sslIssuer") var sslIssuer: String? = null,
        @Column(name = "dnsResolved") var dnsResolved: Boolean? = null,
        @Column(name = "dnsIpResolved") var dnsIpResolved: String? = null,
        @Column(name = "cpuPercent", precision = 5, scale = 2) var cpuPercent: BigDecimal? = null,
        @Column(name = "ramPercent", precision = 5, scale = 2) var ramPercent: BigDecimal? = null,
        @Column(name = "ramUsedMb") var ramUsedMb: Int? = null,
        @Column(name = "ramTotalMb") var ramTotalMb: Int? = null,
        @Column(name = "diskPercent", precision = 5, scale = 2) var diskPercent: BigDecimal? = null,
        @Column(name = "diskUsedGb", precision = 10, scale = 2) var diskUsedGb: BigDecimal? = null,
        @Column(name = "diskTotalGb", precision = 10, scale = 2) var diskTotalGb: BigDecimal? = null,
        @Column(name = "loadAvg1m", precision = 5, scale = 2) var loadAvg1m: BigDecimal? = null,
        @Column(name = "loadAvg5m", precision = 5, scale = 2) var loadAvg5m: BigDecimal? = null,
        @Column(name = "loadAvg15m", precision = 5, scale = 2) var loadAvg15m: BigDecimal? = null,
        @Column(name = "networkInMbps", precision = 10, scale = 2) var networkInMbps: BigDecimal? = null,
        @Column(name = "networkOutMbps", precision = 10, scale = 2) var networkOutMbps: BigDecimal? = null,
        @Column(name = "openConnections") var openConnections: Int? = null,
        @Column(name = "processesCount") var processesCount: Int? = null,
        @Column(name = "errorMessage") var errorMessage: String? = null,
        @Column(name = "checkDurationMs") var checkDurationMs: Int? = null,
        @Column(name = "checkerNode") var checkerNode: String = "primary",
)

@Entity
@Table(
        name = "MonitorAlert",
        indexes = [Index(columnList = "monitorId")]
)
class MonitorAlert(
        @Id var id: String = cuid(),
        @Column(name = "monitorId", nullable = false) var monitorId: String = "",
        @Column(name = "checkId") var checkId: String? = null,
        @Type(MonitorAlertTypeUserType::class) @Column(name = "type") var type: MonitorAlertType = MonitorAlertType.DOWN,
        @Type(MonitorAlertSeverityUserType::class) @Column(name = "severity")
        var severity: MonitorAlertSeverity = MonitorAlertSeverity.CRITICAL,
        var message: String = "",
        @Column(precision = 10, scale = 2) var value: BigDecimal? = null,
        @Column(precision = 10, scale = 2) var threshold: BigDecimal? = null,
        @Column(name = "triggeredAt") var triggeredAt: Instant = Instant.now(),
        @Column(name = "resolvedAt") var resolvedAt: Instant? = null,
        @Column(name = "isResolved") var isResolved: Boolean = false,
        @Column(name = "notifiedEmail") var notifiedEmail: Boolean = false,
        @Column(name = "notifiedTelegram") var notifiedTelegram: Boolean = false,
        @Column(name = "ackAt") var ackAt: Instant? = null,
        @Column(name = "ackNote") var ackNote: String? = null,
)

@Entity
@Table(
        name = "UptimeDaily",
        uniqueConstraints = [UniqueConstraint(columnNames = ["monitorId", "date"])]
)
class UptimeDaily(
        @Id var id: String = cuid(),
        @Column(name = "monitorId", nullable = false) var monitorId: String = "",
        @Column(columnDefinition = "DATE") var date: LocalDate = LocalDate.now(),
        @Column(name = "totalChecks") var totalChecks: Int = 0,
        @Column(name = "upChecks") var upChecks: Int = 0,
        @Column(name = "downChecks") var downChecks: Int = 0,
        @Column(name = "degradedChecks") var degradedChecks: Int = 0,
        @Column(name = "uptimePercent", precision = 5, scale = 2) var uptimePercent: BigDecimal? = null,
        @Column(name = "avgPingMs", precision = 8, scale = 2) var avgPingMs: BigDecimal? = null,
        @Column(name = "avgCpuPercent", precision = 5, scale = 2) var avgCpuPercent: BigDecimal? = null,
        @Column(name = "avgRamPercent", precision = 5, scale = 2) var avgRamPercent: BigDecimal? = null,
        @Column(name = "maxPingMs") var maxPingMs: Int? = null,
        @Column(name = "maxCpuPercent", precision = 5, scale = 2) var maxCpuPercent: BigDecimal? = null,
        @Column(name = "incidentsCount") var incidentsCount: Int = 0,
        @Column(name = "totalDowntimeSeconds") var totalDowntimeSeconds: Int = 0,
        @Column(name = "firstDownAt") var firstDownAt: Instant? = null,
)

@Entity
@Table(name = "MaintenanceWindow", indexes = [Index(columnList = "monitorId")])
class MaintenanceWindow(
        @Id var id: String = cuid(),
        @Column(name = "monitorId", nullable = false) var monitorId: String = "",
        var title: String = "",
        @Column(name = "startsAt") var startsAt: Instant = Instant.now(),
        @Column(name = "endsAt") var endsAt: Instant = Instant.now(),
        @Column(name = "suppressAlerts") var suppressAlerts: Boolean = true,
        @Column(name = "createdBy") var createdBy: String? = null,
        @Column(name = "createdAt") var createdAt: Instant = Instant.now(),
)

// ─────────────────────────────────────────────────────────
// Email Log
// ─────────────────────────────────────────────────────────
@Entity
@Table(
        name = "EmailLog",
        indexes =
                [
                        Index(columnList = "userId"),
                        Index(columnList = "status"),
                        Index(columnList = "templateName"),
                        Index(columnList = "sentAt")]
)
class EmailLog(
        @Id var id: String = cuid(),
        @Column(name = "userId") var userId: String? = null,
        var to: String = "",
        @Column(name = "templateName") var templateName: String = "",
        var subject: String = "",
        var language: String = "en",
        var status: String = "",
        @Column(name = "triggeredBy") var triggeredBy: String = "",
        @JdbcTypeCode(SqlTypes.JSON) var variables: Map<String, Any> = emptyMap(),
        @Column(name = "renderedHtml") var renderedHtml: String = "",
        @Column(name = "errorMessage") var errorMessage: String? = null,
        @Column(name = "sentAt") var sentAt: Instant = Instant.now(),
)

// ─────────────────────────────────────────────────────────
// AddonCatalog
// ─────────────────────────────────────────────────────────
@Entity
@Table(name = "addon_catalog")
@EntityListeners(AuditingEntityListener::class)
class AddonCatalog(
        @Id val id: String = "",
        @Column(nullable = false) var category: String = "",
        @Column(nullable = false) var label: String = "",
        @Column(name = "contabo_value") var contaboValue: String? = null,
        @Column(name = "contabo_cost_price", precision = 10, scale = 2) var contaboCostPrice: BigDecimal? = null,
        @Column(name = "billing_type") var billingType: String = "monthly_recurring",
        @Column(name = "is_default") var isDefault: Boolean = false,
        @Column(name = "sort_order") var sortOrder: Int = 0,
        @CreatedDate @Column(name = "created_at", updatable = false) var createdAt: Instant = Instant.now(),
        @LastModifiedDate @Column(name = "updated_at") var updatedAt: Instant = Instant.now(),
)
