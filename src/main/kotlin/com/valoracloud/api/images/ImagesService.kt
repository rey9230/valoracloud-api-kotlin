package com.valoracloud.api.images

import com.valoracloud.api.common.exceptions.NotFoundException
import com.valoracloud.api.contabo.ContaboService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ImagesService(
    private val contaboService: ContaboService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun listAllImages(standardOnly: Boolean? = null, search: String? = null): Any {
        val images = contaboService.listImages()
        return images.filter { img ->
            val matchStandard = standardOnly == null || img.standardImage == standardOnly
            val matchSearch = search.isNullOrBlank() || img.name.contains(search, ignoreCase = true)
            matchStandard && matchSearch
        }
    }

    fun getImageDetail(imageId: String): Any {
        return contaboService.getImage(imageId)
    }

    fun createCustomImage(dto: CreateCustomImageDto): Any {
        return contaboService.createCustomImage(
            com.valoracloud.api.contabo.ContaboCreateCustomImageRequest(
                name = dto.name,
                description = dto.description,
                url = dto.url,
                osType = dto.osType,
                version = dto.version
            )
        )
    }

    fun updateImage(imageId: String, dto: UpdateImageDto): Any {
        return contaboService.updateImage(
            imageId,
            com.valoracloud.api.contabo.ContaboUpdateImageRequest(
                name = dto.name,
                description = dto.description
            )
        )
    }

    fun deleteImage(imageId: String) {
        contaboService.deleteImage(imageId)
    }

    fun getImageStats(): Any {
        return contaboService.getImageStats()
    }
}