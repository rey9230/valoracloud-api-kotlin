package com.valoracloud.api.auth.security

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthFilter(
    private val jwtProvider: JwtProvider,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith("Bearer ")) {
            val token = header.substring(7)
            if (jwtProvider.validateToken(token) && jwtProvider.getTokenType(token) == "access") {
                val auth = UsernamePasswordAuthenticationToken(
                    jwtProvider.getUserId(token),
                    null,
                    listOf(SimpleGrantedAuthority("ROLE_${jwtProvider.getRole(token)}")),
                )
                auth.details = mapOf(
                    "userId" to jwtProvider.getUserId(token),
                    "email" to jwtProvider.getEmail(token),
                    "role" to jwtProvider.getRole(token),
                )
                SecurityContextHolder.getContext().authentication = auth
            }
        }
        filterChain.doFilter(request, response)
    }
}
