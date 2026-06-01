package com.valoracloud.api.auth.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegisterDto(
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank @field:Size(min = 8, max = 128) val password: String,
    @field:NotBlank val firstName: String,
    @field:NotBlank val lastName: String,
)

data class LoginDto(
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank val password: String,
)

data class RefreshDto(
    @field:NotBlank val refreshToken: String,
)

data class ForgotPasswordDto(
    @field:Email @field:NotBlank val email: String,
)

data class ResetPasswordDto(
    @field:NotBlank val token: String,
    @field:NotBlank @field:Size(min = 8, max = 128) val newPassword: String,
)

data class VerifyEmailDto(
    @field:NotBlank val token: String,
)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserDto,
)

data class UserDto(
    val id: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val role: String,
    val status: String,
    val emailVerified: Boolean,
    val language: String,
)
