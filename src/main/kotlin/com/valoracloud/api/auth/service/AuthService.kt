package com.valoracloud.api.auth.service

import com.valoracloud.api.auth.dto.*
import com.valoracloud.api.auth.security.JwtProvider
import com.valoracloud.api.common.exceptions.*
import com.valoracloud.api.config.RefreshTokenRepository
import com.valoracloud.api.config.UserRepository
import com.valoracloud.api.entity.RefreshToken
import com.valoracloud.api.entity.User
import com.valoracloud.api.common.model.Role
import com.valoracloud.api.common.model.UserStatus
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional
class AuthService(
    private val userRepo: UserRepository,
    private val refreshTokenRepo: RefreshTokenRepository,
    private val jwtProvider: JwtProvider,
    private val passwordEncoder: PasswordEncoder,
    // private val notifications: NotificationsService, // TODO
    // private val facebookConversions: FacebookConversionsService, // TODO
) {
    fun register(dto: RegisterDto): AuthResponse {
        if (userRepo.existsByEmail(dto.email)) throw ConflictException("Email already registered")
        val user = userRepo.save(User(
            email = dto.email,
            password = passwordEncoder.encode(dto.password),
            firstName = dto.firstName,
            lastName = dto.lastName,
        ))
        // notifications.sendVerificationEmail(user.email, jwtProvider.generateVerificationToken(user.id))
        return buildAuthResponse(user)
    }

    fun login(dto: LoginDto): AuthResponse {
        val user = userRepo.findByEmailAndDeletedAtIsNull(dto.email)
            ?: throw UnauthorizedException("Invalid credentials")
        if (user.status == UserStatus.SUSPENDED || user.status == UserStatus.BANNED)
            throw ForbiddenException("Account ${user.status.name.lowercase()}")
        if (!passwordEncoder.matches(dto.password, user.password))
            throw UnauthorizedException("Invalid credentials")
        // notifications.sendNewLoginEmail(user)
        return buildAuthResponse(user)
    }

    fun refresh(dto: RefreshDto): AuthResponse {
        val stored = refreshTokenRepo.findByToken(dto.refreshToken)
            ?: throw UnauthorizedException("Invalid refresh token")
        if (stored.revokedAt != null || stored.expiresAt.isBefore(Instant.now()))
            throw UnauthorizedException("Token expired or revoked")
        val user = userRepo.findById(stored.userId).orElse(null)
            ?: throw UnauthorizedException("User not found")
        // Revoke old refresh token (rotation)
        stored.revokedAt = Instant.now()
        refreshTokenRepo.save(stored)
        return buildAuthResponse(user)
    }

    fun verifyEmail(dto: VerifyEmailDto) {
        if (!jwtProvider.validateToken(dto.token)) throw BadRequestException("Invalid or expired token")
        val userId = jwtProvider.getUserId(dto.token)
        val user = userRepo.findById(userId).orElseThrow { NotFoundException("User", userId) }
        user.emailVerified = true
        userRepo.save(user)
    }

    fun forgotPassword(dto: ForgotPasswordDto) {
        val user = userRepo.findByEmailAndDeletedAtIsNull(dto.email) ?: return // silent
        if (user.status != UserStatus.ACTIVE) return
        val resetToken = jwtProvider.generateVerificationToken(user.id)
        // notifications.sendPasswordResetEmail(user.email, resetToken)
        log.info("Password reset requested for {}", user.email)
    }

    fun resetPassword(dto: ResetPasswordDto) {
        if (!jwtProvider.validateToken(dto.token)) throw BadRequestException("Invalid or expired token")
        val userId = jwtProvider.getUserId(dto.token)
        val user = userRepo.findById(userId).orElseThrow { NotFoundException("User", userId) }
        user.password = passwordEncoder.encode(dto.newPassword)
        // Revoke all refresh tokens for security
        refreshTokenRepo.deleteByUserId(user.id)
        userRepo.save(user)
    }

    fun me(userId: String): UserDto =
        userRepo.findById(userId).orElseThrow { NotFoundException("User", userId) }.toUserDto()

    private fun buildAuthResponse(user: User): AuthResponse {
        val accessToken = jwtProvider.generateAccessToken(user.id, user.email, user.role.name)
        val refreshToken = refreshTokenRepo.save(RefreshToken(
            token = jwtProvider.generateRefreshToken(user.id),
            userId = user.id,
            expiresAt = Instant.now().plus(jwtProvider.refreshDuration),
        ))
        return AuthResponse(accessToken, refreshToken.token, user.toUserDto())
    }

    fun User.toUserDto() = UserDto(id, email, firstName, lastName, role.name, status.name, emailVerified, language)

    companion object {
        private val log = LoggerFactory.getLogger(AuthService::class.java)
    }
}
