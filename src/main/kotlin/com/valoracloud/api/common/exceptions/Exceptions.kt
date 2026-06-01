package com.valoracloud.api.common.exceptions

import jakarta.servlet.http.HttpServletRequest
import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(AppException::class)
    fun handleAppException(
            ex: AppException,
            request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> =
            ResponseEntity.status(ex.status)
                    .body(
                            ErrorResponse(
                                    ex.status.value(),
                                    ex.message ?: "Internal error",
                                    request.requestURI ?: "",
                                    Instant.now()
                            )
                    )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(
            ex: MethodArgumentNotValidException,
            request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        val errors =
                ex.bindingResult.fieldErrors.associate {
                    it.field to (it.defaultMessage ?: "Invalid")
                }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(
                        ErrorResponse(
                                400,
                                "Validation failed",
                                request.requestURI,
                                Instant.now(),
                                errors
                        )
                )
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(
            ex: AccessDeniedException,
            request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> =
            ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ErrorResponse(403, "Access denied", request.requestURI, Instant.now()))

    @ExceptionHandler(Exception::class)
    fun handleUnknown(ex: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        log.error("Unhandled exception: ${request.method} ${request.requestURI}", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                        ErrorResponse(
                                500,
                                "Internal server error",
                                request.requestURI,
                                Instant.now()
                        )
                )
    }

    companion object {
        private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }
}

data class ErrorResponse(
        val statusCode: Int,
        val message: String,
        val path: String,
        val timestamp: Instant,
        val errors: Map<String, String>? = null,
)

open class AppException(val status: HttpStatus, message: String) : RuntimeException(message)

class NotFoundException(entity: String, id: String) :
        AppException(HttpStatus.NOT_FOUND, "$entity not found: $id")

class ConflictException(message: String) : AppException(HttpStatus.CONFLICT, message)

class BadRequestException(message: String) : AppException(HttpStatus.BAD_REQUEST, message)

class UnauthorizedException(message: String) : AppException(HttpStatus.UNAUTHORIZED, message)

class ForbiddenException(message: String) : AppException(HttpStatus.FORBIDDEN, message)
