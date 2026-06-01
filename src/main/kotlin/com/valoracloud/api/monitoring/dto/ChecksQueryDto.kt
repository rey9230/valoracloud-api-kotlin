package com.valoracloud.api.monitoring.dto

data class ChecksQueryDto(
    val page: Int = 1,
    val limit: Int = 100,
    val from: String? = null,
    val to: String? = null,
    val status: String? = null,
)
