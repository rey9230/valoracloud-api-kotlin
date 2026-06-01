package com.valoracloud.api.objectstorage

import com.valoracloud.api.common.exceptions.NotFoundException
import com.valoracloud.api.config.ObjectStorageRepository
import org.springframework.stereotype.Service

@Service
class ObjectStorageService(
    private val objectStorageRepository: ObjectStorageRepository,
    // TODO: Inject ContaboService, EncryptionUtil
) {
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
        storage.status = com.valoracloud.api.common.model.ObjectStorageStatus.UPGRADING
        return objectStorageRepository.save(storage)
    }

    fun cancelStorage(userId: String, storageId: String): Any {
        val storage = objectStorageRepository.findByUserIdAndId(userId, storageId)
            ?: throw NotFoundException("Object Storage", storageId)

        // TODO: ContaboService.cancelObjectStorage(storage.contaboStorageId, cancelDate)

        storage.status = com.valoracloud.api.common.model.ObjectStorageStatus.CANCELLED
        return objectStorageRepository.save(storage)
    }
}
