package com.valoracloud.api.snapshots

import com.valoracloud.api.auth.security.CurrentUser
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/servers/{serverId}/snapshots")
class SnapshotsController(
    private val snapshotsService: SnapshotsService,
) {
    @GetMapping
    fun listSnapshots(
        @CurrentUser userId: String,
        @PathVariable serverId: String,
    ) = snapshotsService.listServerSnapshots(userId, serverId)

    @GetMapping("/{snapshotId}")
    fun getSnapshot(
        @CurrentUser userId: String,
        @PathVariable serverId: String,
        @PathVariable snapshotId: String,
    ) = snapshotsService.getSnapshotDetail(userId, serverId, snapshotId)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createSnapshot(
        @CurrentUser userId: String,
        @PathVariable serverId: String,
        @Valid @RequestBody dto: CreateSnapshotDto,
    ) = snapshotsService.createSnapshot(userId, serverId, dto)

    @PatchMapping("/{snapshotId}")
    fun updateSnapshot(
        @CurrentUser userId: String,
        @PathVariable serverId: String,
        @PathVariable snapshotId: String,
        @Valid @RequestBody dto: UpdateSnapshotDto,
    ) = snapshotsService.updateSnapshot(userId, serverId, snapshotId, dto)

    @DeleteMapping("/{snapshotId}")
    fun deleteSnapshot(
        @CurrentUser userId: String,
        @PathVariable serverId: String,
        @PathVariable snapshotId: String,
    ) = snapshotsService.deleteSnapshot(userId, serverId, snapshotId)

    @PostMapping("/{snapshotId}/rollback")
    fun rollbackSnapshot(
        @CurrentUser userId: String,
        @PathVariable serverId: String,
        @PathVariable snapshotId: String,
    ) = snapshotsService.rollbackSnapshot(userId, serverId, snapshotId)
}
