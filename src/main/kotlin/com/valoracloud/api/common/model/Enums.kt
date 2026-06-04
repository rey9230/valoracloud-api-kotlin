package com.valoracloud.api.common.model

enum class Role { CLIENT, ADMIN }

enum class OrderStatus { PENDING_PAYMENT, PAID, PROVISIONING, ACTIVE, FAILED, CANCELLED }

enum class ServerStatus { PROVISIONING, RUNNING, STOPPED, SUSPENDED, REINSTALLING, NEEDS_PROVISION, ERROR, CANCELLED }

enum class ProductType { CLOUD_VPS, CLOUD_VDS, WINDOWS_VPS, OBJECT_STORAGE, DOMAIN }

enum class ServiceType { COMPUTE, OBJECT_STORAGE, DOMAIN }

enum class PaymentMethod { STRIPE, CRYPTO }

enum class UserStatus { ACTIVE, SUSPENDED, BANNED }

enum class PlanStatus { ACTIVE, HIDDEN, ARCHIVED }

enum class ObjectStorageStatus { PROVISIONING, READY, UPGRADING, ERROR, CANCELLED }

enum class DomainStatus { PENDING, ACTIVE, EXPIRED, CANCELLED, TRANSFER_IN_PROGRESS, TRANSFER_FAILED }

enum class MonitorStatus { UP, DOWN, DEGRADED, TIMEOUT }

enum class MonitorAlertSeverity { CRITICAL, WARNING, INFO, OK }

enum class MonitorAlertType { DOWN, DEGRADED, HIGH_CPU, HIGH_RAM, HIGH_DISK, HIGH_LATENCY, SSL_EXPIRY, SSL_INVALID, RECOVERY }
