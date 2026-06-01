package com.valoracloud.api.tags

import com.valoracloud.api.auth.security.CurrentUser
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/tags")
class TagsController(
    private val tagsService: TagsService,
) {
    @GetMapping
    fun listTags(@CurrentUser userId: String) = tagsService.listUserTags(userId)

    @GetMapping("/sync")
    fun syncTags(@CurrentUser userId: String) = tagsService.syncTagsFromContabo(userId)

    @GetMapping("/{id}")
    fun getTag(
        @CurrentUser userId: String,
        @PathVariable id: String,
    ) = tagsService.getTagDetail(userId, id)

    @GetMapping("/{id}/assignments")
    fun listAssignments(
        @CurrentUser userId: String,
        @PathVariable id: String,
    ) = tagsService.listTagAssignments(userId, id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createTag(
        @CurrentUser userId: String,
        @Valid @RequestBody dto: CreateTagDto,
    ) = tagsService.createTag(userId, dto)

    @PostMapping("/{id}/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    fun assignTag(
        @CurrentUser userId: String,
        @PathVariable id: String,
        @Valid @RequestBody dto: CreateTagAssignmentDto,
    ) = tagsService.assignTag(userId, id, dto.resourceType, dto.resourceId)

    @PatchMapping("/{id}")
    fun updateTag(
        @CurrentUser userId: String,
        @PathVariable id: String,
        @Valid @RequestBody dto: UpdateTagDto,
    ) = tagsService.updateTag(userId, id, dto)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteTag(
        @CurrentUser userId: String,
        @PathVariable id: String,
    ) = tagsService.deleteTag(userId, id)

    @DeleteMapping("/{id}/assignments/{resourceType}/{resourceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unassignTag(
        @CurrentUser userId: String,
        @PathVariable id: String,
        @PathVariable resourceType: String,
        @PathVariable resourceId: String,
    ) = tagsService.unassignTag(userId, id, resourceType, resourceId)
}
