package com.valoracloud.api.common.dto

data class PaginationDto(
    val page: Int = 1,
    val limit: Int = 20,
) {
    val offset: Int get() = (page - 1) * limit
}

data class PaginatedResponse<T>(
    val data: List<T>,
    val total: Long,
    val page: Int,
    val limit: Int,
    val totalPages: Int,
)
