package com.valoracloud.api.images

import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/images")
class ImagesController(
    private val imagesService: ImagesService,
) {
    @GetMapping
    fun listImages(
        @RequestParam(required = false) standardOnly: String?,
        @RequestParam(required = false) search: String?,
    ): Any {
        val standardFilter = when (standardOnly) {
            "true" -> true
            "false" -> false
            else -> null
        }
        return imagesService.listAllImages(standardFilter, search)
    }

    @GetMapping("/stats")
    fun getStats() = imagesService.getImageStats()

    @GetMapping("/{imageId}")
    fun getImage(@PathVariable imageId: String) = imagesService.getImageDetail(imageId)

    @PostMapping
    fun createCustomImage(@Valid @RequestBody dto: CreateCustomImageDto) =
        imagesService.createCustomImage(dto)

    @PatchMapping("/{imageId}")
    fun updateImage(
        @PathVariable imageId: String,
        @Valid @RequestBody dto: UpdateImageDto,
    ) = imagesService.updateImage(imageId, dto)

    @DeleteMapping("/{imageId}")
    fun deleteImage(@PathVariable imageId: String): Map<String, String> {
        imagesService.deleteImage(imageId)
        return mapOf("message" to "Image deleted successfully")
    }
}
