package com.valoracloud.api.config

import com.valoracloud.api.auth.security.JwtAuthFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class SecurityConfig(
        private val jwtAuthFilter: JwtAuthFilter,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
                .cors { it.configurationSource(corsConfigurationSource()) }
                .csrf { it.disable() }
                .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
                // Missing/expired token must be 401 (clients trigger token refresh on 401);
                // without this Spring Security 6 defaults to 403 and refresh never fires.
                .exceptionHandling {
                    it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                }
                .authorizeHttpRequests { auth ->
                    // Public
                    auth.requestMatchers(
                                    "/auth/**",
                                    "/docs/**",
                                    "/docs-json/**",
                                    "/swagger-ui/**",
                                    "/swagger-ui.html",
                                    "/v3/api-docs/**",
                                    "/health/**",
                                    "/billing/webhook",
                                    "/billing/crypto-webhook",
                                    "/actuator/health",
                                    "/billing/**",
                                    "/test/**",
                            )
                            .permitAll()
                    // GET endpoints are generally open for plans, etc.
                    auth.requestMatchers(
                                    HttpMethod.GET,
                                    "/plans/**",
                                    "/domains/tld-pricing/**",
                            )
                            .permitAll()
                    // Public POST endpoints
                    auth.requestMatchers(
                                    HttpMethod.POST,
                                    "/plans/*/quote",
                            )
                            .permitAll()
                    // Admin only
                    auth.requestMatchers("/admin/**").hasRole("ADMIN")
                    // Everything else requires auth
                    auth.anyRequest().authenticated()
                }
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun authenticationManager(authConfig: AuthenticationConfiguration): AuthenticationManager =
            authConfig.authenticationManager

    @Bean fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(12)

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration()
        config.allowedOriginPatterns =
                listOf(
                        "http://localhost:*",
                        "https://valoracloud.com",
                        "https://www.valoracloud.com",
                        "https://api.valoracloud.com",
                        "https://app.valoracloud.com",
                        "https://admin.valoracloud.com",
                        "https://ops.valoracloud.com",
                        "https://*.lovable.app",
                        "https://*.vercel.app",
                )
        config.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        config.allowedHeaders = listOf("*")
        config.allowCredentials = true
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return source
    }
}
