package com.valoracloud.api.auth.security

import io.jsonwebtoken.*
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import java.time.Duration
import java.time.Instant
import java.util.*
import javax.crypto.SecretKey
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class JwtProvider(
        @Value("\${app.auth.jwt-secret}") secret: String,
        @Value("\${app.auth.access-expiry}") accessExpiry: String,
        @Value("\${app.auth.refresh-expiry}") refreshExpiry: String,
) {
    private val key: SecretKey = run {
        val encoded =
                try {
                    if (secret.isBlank()) encodeDefault(secret)
                    else {
                        Decoders.BASE64.decode(secret)
                        secret
                    }
                } catch (_: Exception) {
                    encodeDefault(secret)
                }
        Keys.hmacShaKeyFor(Decoders.BASE64.decode(encoded))
    }
    private val accessDuration: Duration = parseDuration(accessExpiry)
    val refreshDuration: Duration = parseDuration(refreshExpiry)

    private fun parseDuration(s: String): Duration =
            try {
                Duration.parse("PT${s.uppercase()}")
            } catch (_: Exception) {
                Duration.ofMinutes(15)
            }

    private fun encodeDefault(s: String): String {
        val keyBytes =
                (s.ifBlank { "default-secret-change-me" }).padEnd(64, '0').take(64).toByteArray()
        return Base64.getEncoder().encodeToString(keyBytes)
    }

    fun generateAccessToken(userId: String, email: String, role: String): String =
            buildToken(userId, email, role, accessDuration)

    fun generateRefreshToken(userId: String): String =
            Jwts.builder()
                    .subject(userId)
                    .claim("type", "refresh")
                    .issuedAt(Date())
                    .expiration(Date.from(Instant.now().plus(refreshDuration)))
                    .signWith(key)
                    .compact()

    fun generateVerificationToken(userId: String): String =
            Jwts.builder()
                    .subject(userId)
                    .claim("type", "email-verification")
                    .issuedAt(Date())
                    .expiration(Date.from(Instant.now().plus(Duration.ofHours(24))))
                    .signWith(key)
                    .compact()

    private fun buildToken(
            userId: String,
            email: String,
            role: String,
            duration: Duration
    ): String =
            Jwts.builder()
                    .subject(userId)
                    .claim("email", email)
                    .claim("role", role)
                    .claim("type", "access")
                    .issuedAt(Date())
                    .expiration(Date.from(Instant.now().plus(duration)))
                    .signWith(key)
                    .compact()

    fun validateToken(token: String): Boolean =
            try {
                parseClaims(token)
                true
            } catch (e: Exception) {
                false
            }

    fun getUserId(token: String): String = parseClaims(token).subject

    fun getEmail(token: String): String = parseClaims(token)["email"] as? String ?: ""

    fun getRole(token: String): String = parseClaims(token)["role"] as? String ?: "CLIENT"

    fun getTokenType(token: String): String = parseClaims(token)["type"] as? String ?: ""

    private fun parseClaims(token: String): Claims =
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload

    companion object {
        private val log = LoggerFactory.getLogger(JwtProvider::class.java)
    }
}
