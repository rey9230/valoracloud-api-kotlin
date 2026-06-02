package com.valoracloud.api.auth.controller

import com.valoracloud.api.auth.dto.*
import com.valoracloud.api.auth.service.AuthService
import com.valoracloud.api.auth.security.CurrentUser
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/auth")
@Tag(name = "Auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody dto: RegisterDto): AuthResponse = authService.register(dto)

    @PostMapping("/login")
    fun login(@Valid @RequestBody dto: LoginDto): AuthResponse = authService.login(dto)

    @PostMapping("/admin/login")
    fun adminLogin(@Valid @RequestBody dto: LoginDto): AuthResponse = authService.adminLogin(dto)

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody dto: RefreshDto): AuthResponse = authService.refresh(dto)

    @PostMapping("/verify-email")
    fun verifyEmail(@Valid @RequestBody dto: VerifyEmailDto) = authService.verifyEmail(dto)

    @PostMapping("/forgot-password")
    fun forgotPassword(@Valid @RequestBody dto: ForgotPasswordDto) = authService.forgotPassword(dto)

    @PostMapping("/reset-password")
    fun resetPassword(@Valid @RequestBody dto: ResetPasswordDto) = authService.resetPassword(dto)

    @GetMapping("/me")
    fun me(@CurrentUser userId: String): UserDto = authService.me(userId)
}
