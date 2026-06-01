package com.valoracloud.api.users

import com.valoracloud.api.common.exceptions.NotFoundException
import com.valoracloud.api.config.UserRepository
import org.springframework.stereotype.Service

@Service
class UsersService(
    private val userRepository: UserRepository,
) {
    fun findById(id: String): Map<String, Any?> {
        val user = userRepository.findById(id)
            .orElseThrow { NotFoundException("User", id) }

        return mapOf(
            "id" to user.id,
            "email" to user.email,
            "firstName" to user.firstName,
            "lastName" to user.lastName,
            "role" to user.role,
            "status" to user.status,
            "emailVerified" to user.emailVerified,
            "language" to user.language,
            "createdAt" to user.createdAt,
        )
    }

    fun findByEmail(email: String) = userRepository.findByEmail(email)

    fun update(id: String, dto: UpdateUserDto): Map<String, Any?> {
        // Ensure user exists
        findById(id)

        val user = userRepository.findById(id).get()
        if (dto.firstName != null) user.firstName = dto.firstName
        if (dto.lastName != null) user.lastName = dto.lastName
        if (dto.language != null) user.language = dto.language
        userRepository.save(user)

        return findById(id)
    }
}
