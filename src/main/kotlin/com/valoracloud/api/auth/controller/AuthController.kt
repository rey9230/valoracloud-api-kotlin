package com.valoracloud.api.auth.controller

import com.valoracloud.api.auth.dto.*
import com.valoracloud.api.auth.service.AuthService
import com.valoracloud.api.auth.security.CurrentUser
import com.valoracloud.api.facebook.FbClientContext
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/auth")
@Tag(name = "Auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody dto: RegisterDto, request: HttpServletRequest): AuthResponse {
        val ip = request.getHeader("X-Forwarded-For")?.substringBefore(",") ?: request.remoteAddr
        val userAgent = request.getHeader("User-Agent") ?: "Unknown Device"
        val fbc = request.cookies?.find { it.name == "_fbc" }?.value
        val fbp = request.cookies?.find { it.name == "_fbp" }?.value
        val ctx = FbClientContext(ip, userAgent, request.requestURL.toString(), fbc, fbp)
        return authService.register(dto, ctx)
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody dto: LoginDto, request: HttpServletRequest): AuthResponse {
        val ip = request.getHeader("X-Forwarded-For")?.substringBefore(",") ?: request.remoteAddr
        val userAgent = request.getHeader("User-Agent") ?: "Unknown Device"
        return authService.login(dto, ip, userAgent)
    }

    @PostMapping("/admin/login")
    fun adminLogin(@Valid @RequestBody dto: LoginDto, request: HttpServletRequest): AuthResponse {
        val ip = request.getHeader("X-Forwarded-For")?.substringBefore(",") ?: request.remoteAddr
        val userAgent = request.getHeader("User-Agent") ?: "Unknown Device"
        return authService.adminLogin(dto, ip, userAgent)
    }

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody dto: RefreshDto): AuthResponse = authService.refresh(dto)

    @PostMapping("/verify-email")
    fun verifyEmail(@Valid @RequestBody dto: VerifyEmailDto) = authService.verifyEmail(dto)

    @PostMapping("/forgot-password")
    fun forgotPassword(@Valid @RequestBody dto: ForgotPasswordDto, request: HttpServletRequest) {
        val ip = request.getHeader("X-Forwarded-For")?.substringBefore(",") ?: request.remoteAddr
        val userAgent = request.getHeader("User-Agent") ?: "Unknown Device"
        authService.forgotPassword(dto, ip, userAgent)
    }

    @PostMapping("/reset-password")
    fun resetPassword(@Valid @RequestBody dto: ResetPasswordDto, request: HttpServletRequest) {
        val ip = request.getHeader("X-Forwarded-For")?.substringBefore(",") ?: request.remoteAddr
        val userAgent = request.getHeader("User-Agent") ?: "Unknown Device"
        authService.resetPassword(dto, ip, userAgent)
    }

    @GetMapping("/me")
    fun me(@CurrentUser userId: String): UserDto = authService.me(userId)
}