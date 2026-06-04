package com.valoracloud.api.users

import com.valoracloud.api.auth.security.CurrentUser
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/users")
class UsersController(
    private val usersService: UsersService,
) {
    @GetMapping("/me")
    fun getProfile(@CurrentUser userId: String) = usersService.findById(userId)

    @PatchMapping("/me")
    fun updateProfile(
        @CurrentUser userId: String,
        @Valid @RequestBody dto: UpdateUserDto,
    ): Map<String, Any?> {
        usersService.update(userId, dto)
        return mapOf("success" to true)
    }
}