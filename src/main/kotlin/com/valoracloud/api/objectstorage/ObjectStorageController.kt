package com.valoracloud.api.objectstorage

import com.valoracloud.api.auth.security.CurrentUser
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/object-storage")
class ObjectStorageController(
    private val objectStorageService: ObjectStorageService,
) {
    @GetMapping
    fun listStorages(@CurrentUser userId: String) =
        objectStorageService.listUserStorages(userId)

    @GetMapping("/{id}")
    fun getStorageDetail(
        @CurrentUser userId: String,
        @PathVariable id: String,
    ) = objectStorageService.getStorageDetail(userId, id)

    @GetMapping("/{id}/credentials")
    fun getCredentials(
        @CurrentUser userId: String,
        @PathVariable id: String,
    ) = objectStorageService.getS3Credentials(userId, id)

    @PatchMapping("/{id}/upgrade")
    fun upgradeStorage(
        @CurrentUser userId: String,
        @PathVariable id: String,
        @Valid @RequestBody dto: UpgradeObjectStorageDto,
    ) = objectStorageService.upgradeStorage(userId, id, dto)

    @DeleteMapping("/{id}")
    fun cancelStorage(
        @CurrentUser userId: String,
        @PathVariable id: String,
    ) = objectStorageService.cancelStorage(userId, id)
}
