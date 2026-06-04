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
import com.valoracloud.api.facebook.CompleteRegistrationParams
import com.valoracloud.api.facebook.FacebookConversionsService
import com.valoracloud.api.facebook.FbClientContext
import com.valoracloud.api.notifications.service.NotificationsService
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
    private val notifications: NotificationsService,
    private val facebookConversions: FacebookConversionsService,
) {
    fun register(dto: RegisterDto, ctx: FbClientContext): AuthResponse {
        if (userRepo.existsByEmail(dto.email)) throw ConflictException("Email already registered")
        val user = userRepo.save(User(
            email = dto.email,
            password = passwordEncoder.encode(dto.password),
            firstName = dto.firstName,
            lastName = dto.lastName,
        ))
        notifications.sendWelcomeEmail(user.email, jwtProvider.generateVerificationToken(user.id), user.language, user.id)
        facebookConversions.trackCompleteRegistration(
            CompleteRegistrationParams(user.id, user.email, user.firstName, user.lastName),
            ctx
        )
        return buildAuthResponse(user)
    }

    fun login(dto: LoginDto, ip: String, userAgent: String): AuthResponse {
        val user = userRepo.findByEmailAndDeletedAtIsNull(dto.email)
            ?: throw UnauthorizedException("Invalid credentials")
        if (user.status == UserStatus.SUSPENDED || user.status == UserStatus.BANNED)
            throw ForbiddenException("Account ${user.status.name.lowercase()}")
        if (!passwordEncoder.matches(dto.password, user.password))
            throw UnauthorizedException("Invalid credentials")
        notifications.sendNewLoginEmail(
            email = user.email,
            signedInAt = Instant.now().toString(),
            fromIp = ip,
            location = "",
            device = userAgent,
            authMethod = "password",
            language = user.language,
            userId = user.id
        )
        return buildAuthResponse(user)
    }

    fun adminLogin(dto: LoginDto, ip: String, userAgent: String): AuthResponse {
        val user = userRepo.findByEmailAndDeletedAtIsNull(dto.email)
            ?: throw UnauthorizedException("Invalid credentials")
        if (user.role != Role.ADMIN)
            throw UnauthorizedException("Invalid credentials")
        if (user.status == UserStatus.SUSPENDED || user.status == UserStatus.BANNED)
            throw ForbiddenException("Account ${user.status.name.lowercase()}")
        if (!passwordEncoder.matches(dto.password, user.password))
            throw UnauthorizedException("Invalid credentials")
        notifications.sendNewLoginEmail(
            email = user.email,
            signedInAt = Instant.now().toString(),
            fromIp = ip,
            location = "",
            device = userAgent,
            authMethod = "password",
            language = user.language,
            userId = user.id
        )
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

    fun logout(dto: RefreshDto) {
        val stored = refreshTokenRepo.findByToken(dto.refreshToken) ?: return
        if (stored.revokedAt == null) {
            stored.revokedAt = Instant.now()
            refreshTokenRepo.save(stored)
        }
    }

    fun verifyEmail(dto: VerifyEmailDto) {
        if (!jwtProvider.validateToken(dto.token)) throw BadRequestException("Invalid or expired token")
        val userId = jwtProvider.getUserId(dto.token)
        val user = userRepo.findById(userId).orElseThrow { NotFoundException("User", userId) }
        user.emailVerified = true
        userRepo.save(user)
    }

    fun forgotPassword(dto: ForgotPasswordDto, ip: String, userAgent: String) {
        val user = userRepo.findByEmailAndDeletedAtIsNull(dto.email) ?: return // silent
        if (user.status != UserStatus.ACTIVE) return
        val resetToken = jwtProvider.generateVerificationToken(user.id)
        notifications.sendPasswordResetEmail(
            email = user.email,
            token = resetToken,
            language = user.language,
            requestedAt = Instant.now().toString(),
            fromIp = ip,
            location = "",
            device = userAgent
        )
        log.info("Password reset requested for {}", user.email)
    }

    fun resetPassword(dto: ResetPasswordDto, ip: String, userAgent: String) {
        if (!jwtProvider.validateToken(dto.token)) throw BadRequestException("Invalid or expired token")
        val userId = jwtProvider.getUserId(dto.token)
        val user = userRepo.findById(userId).orElseThrow { NotFoundException("User", userId) }
        user.password = passwordEncoder.encode(dto.newPassword)
        // Revoke all refresh tokens for security
        refreshTokenRepo.deleteByUserId(user.id)
        userRepo.save(user)
        notifications.sendPasswordChangedEmail(
            email = user.email,
            changedAt = Instant.now().toString(),
            fromIp = ip,
            location = "",
            device = userAgent,
            language = user.language,
            userId = user.id
        )
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