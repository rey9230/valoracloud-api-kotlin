package com.valoracloud.api.images

import com.valoracloud.api.common.exceptions.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ImagesService(
    // TODO: Inject ContaboService, Redis client
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun listAllImages(standardOnly: Boolean? = null, search: String? = null): Any {
        // TODO: Fetch from Contabo (with Redis cache), filter by standardOnly + search
        return emptyList<Any>()
    }

    fun getImageDetail(imageId: String): Any {
        // TODO: ContaboService.getImage(imageId)
        throw UnsupportedOperationException("Images not yet implemented — needs ContaboService")
    }

    fun createCustomImage(dto: CreateCustomImageDto): Any {
        // TODO: ContaboService.createCustomImage(dto)
        throw UnsupportedOperationException("Images not yet implemented — needs ContaboService")
    }

    fun updateImage(imageId: String, dto: UpdateImageDto): Any {
        // TODO: ContaboService.getImage(imageId), ContaboService.updateImage(imageId, dto)
        throw UnsupportedOperationException("Images not yet implemented — needs ContaboService")
    }

    fun deleteImage(imageId: String) {
        // TODO: ContaboService.getImage(imageId), verify custom, ContaboService.deleteImage(imageId)
        throw UnsupportedOperationException("Images not yet implemented — needs ContaboService")
    }

    fun getImageStats(): Any {
        // TODO: ContaboService.getImageStats()
        return emptyMap<String, Any>()
    }
}
