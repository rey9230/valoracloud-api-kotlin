package com.valoracloud.api.tags

import com.valoracloud.api.common.exceptions.BadRequestException
import com.valoracloud.api.common.exceptions.ForbiddenException
import com.valoracloud.api.common.exceptions.NotFoundException
import com.valoracloud.api.config.TagRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class TagsService(
    private val tagRepository: TagRepository,
    // TODO: Inject ContaboService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun listUserTags(userId: String) =
        tagRepository.findByUserId(userId)

    fun getTagDetail(userId: String, tagId: String): Any {
        val tag = tagRepository.findById(tagId)
            .orElseThrow { NotFoundException("Tag", tagId) }
        if (tag.userId != userId) throw ForbiddenException("Access denied")
        return tag
    }

    fun createTag(userId: String, dto: CreateTagDto): Any {
        log.info("Creating tag '${dto.name}' for user $userId")

        // TODO: ContaboService.createTag(dto) → returns contaboTagId

        return tagRepository.save(
            com.valoracloud.api.entity.Tag(
                userId = userId,
                contaboId = 0, // TODO: from Contabo
                name = dto.name,
                color = dto.color,
            )
        )
    }

    fun updateTag(userId: String, tagId: String, dto: UpdateTagDto): Any {
        val tag = tagRepository.findById(tagId)
            .orElseThrow { NotFoundException("Tag", tagId) }
        if (tag.userId != userId) throw ForbiddenException("Access denied")

        if (dto.name == null && dto.color == null) {
            throw BadRequestException("At least one field (name or color) must be provided")
        }

        // TODO: ContaboService.updateTag(tag.contaboId, dto)

        dto.name?.let { tag.name = it }
        dto.color?.let { tag.color = it }
        return tagRepository.save(tag)
    }

    fun deleteTag(userId: String, tagId: String) {
        val tag = tagRepository.findById(tagId)
            .orElseThrow { NotFoundException("Tag", tagId) }
        if (tag.userId != userId) throw ForbiddenException("Access denied")

        // TODO: ContaboService.deleteTag(tag.contaboId)
        tagRepository.delete(tag)
    }

    fun listTagAssignments(userId: String, tagId: String): Any {
        val tag = tagRepository.findById(tagId)
            .orElseThrow { NotFoundException("Tag", tagId) }
        if (tag.userId != userId) throw ForbiddenException("Access denied")

        // TODO: ContaboService.listTagAssignments(tag.contaboId)
        return emptyList<Any>()
    }

    fun assignTag(userId: String, tagId: String, resourceType: String, resourceId: String): Any {
        val tag = tagRepository.findById(tagId)
            .orElseThrow { NotFoundException("Tag", tagId) }
        if (tag.userId != userId) throw ForbiddenException("Access denied")

        // TODO: ContaboService.createTagAssignment(tag.contaboId, resourceType, resourceId)
        return mapOf("success" to true)
    }

    fun unassignTag(userId: String, tagId: String, resourceType: String, resourceId: String) {
        val tag = tagRepository.findById(tagId)
            .orElseThrow { NotFoundException("Tag", tagId) }
        if (tag.userId != userId) throw ForbiddenException("Access denied")

        // TODO: ContaboService.deleteTagAssignment(tag.contaboId, resourceType, resourceId)
    }

    fun syncTagsFromContabo(userId: String): Map<String, Int> {
        // TODO: ContaboService.listTags() → upsert into DB
        return mapOf("synced" to 0)
    }
}
