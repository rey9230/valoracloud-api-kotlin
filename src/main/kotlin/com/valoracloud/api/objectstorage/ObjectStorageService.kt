package com.valoracloud.api.objectstorage

import com.valoracloud.api.common.exceptions.BadRequestException
import com.valoracloud.api.common.exceptions.NotFoundException
import com.valoracloud.api.common.model.ObjectStorageStatus
import com.valoracloud.api.config.ObjectStorageRepository
import com.valoracloud.api.contabo.ContaboService
import com.valoracloud.api.notifications.service.NotificationsService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class ObjectStorageService(
    private val objectStorageRepository: ObjectStorageRepository,
    private val contabo: ContaboService,
    private val notifications: NotificationsService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun listUserStorages(userId: String) =
        objectStorageRepository.findByUserIdOrderByCreatedAtDesc(userId)

    fun getStorageDetail(userId: String, storageId: String): Any {
        val storage = objectStorageRepository.findByUserIdAndId(userId, storageId)
            ?: throw NotFoundException("Object Storage", storageId)
        // TODO: If storage.status == READY, fetch latest stats from Contabo
        return storage
    }

    fun getS3Credentials(userId: String, storageId: String): Any {
        val storage = objectStorageRepository.findByUserIdAndId(userId, storageId)
            ?: throw NotFoundException("Object Storage", storageId)
        // TODO: Fetch S3 credentials from Contabo via storage.contaboStorageId
        return mapOf<String, String>(
            "accessKey" to "",
            "secretKey" to "",
            "endpoint" to "",
        )
    }

    fun upgradeStorage(userId: String, storageId: String, dto: UpgradeObjectStorageDto): Any {
        val storage = objectStorageRepository.findByUserIdAndId(userId, storageId)
            ?: throw NotFoundException("Object Storage", storageId)
        // TODO: ContaboService.upgradeObjectStorage(storage.contaboStorageId, dto)
        if (dto.totalPurchasedSpaceTB != null) {
            storage.totalPurchasedSpaceTB = dto.totalPurchasedSpaceTB
        }
        storage.status = ObjectStorageStatus.UPGRADING
        return objectStorageRepository.save(storage)
    }

    fun cancelStorage(userId: String, storageId: String): Any {
        val storage = objectStorageRepository.findByUserIdAndId(userId, storageId)
            ?: throw NotFoundException("Object Storage", storageId)

        if (storage.status == ObjectStorageStatus.CANCELLED) {
            throw BadRequestException("Object storage is already cancelled")
        }

        if (storage.contaboStorageId.isBlank()) {
            throw BadRequestException("Object storage has no Contabo ID — cannot cancel. Contact support.")
        }

        val cancelDate = endOfMonthDate()
        try {
            contabo.cancelObjectStorage(storage.contaboStorageId, cancelDate)
            log.info("Object storage ${storage.id} cancellation scheduled in Contabo for $cancelDate")
        } catch (e: Exception) {
            notifications.sendCancellationFailureAlert(
                serviceType = "OBJECT_STORAGE",
                resourceId = storage.id,
                contaboId = storage.contaboStorageId,
                userId = storage.userId,
                errorMessage = e.message ?: "Unknown error",
                errorStack = e.stackTraceToString(),
            )
            throw e
        }

        storage.status = ObjectStorageStatus.CANCELLED
        return objectStorageRepository.save(storage)
    }

    private fun endOfMonthDate(): String {
        val now = LocalDate.now()
        return now.withDayOfMonth(now.lengthOfMonth()).toString()
    }
}
