package kdh.global.exception

import kdh.global.dto.ErrorMessageResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(KdhException::class)
    fun handleKdhException(ex: KdhException): ResponseEntity<ErrorMessageResponse> {
        log.warn("Business exception: errorCode={}, message={}, reason={}, description={}", ex.errorCode, ex.message, ex.reason, ex.description)
        return ResponseEntity.status(ex.errorCode.status)
            .body(
                ErrorMessageResponse(
                    message = ex.message,
                    reason = ex.reason,
                    description = ex.description
                )
            )
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(ex: IllegalArgumentException): ResponseEntity<ErrorMessageResponse> {
        return handleKdhException(
            InvalidRequestException(
                reason = "ILLEGAL_ARGUMENT",
                description = ex.message,
                cause = ex
            )
        )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValidException(
        ex: MethodArgumentNotValidException
    ): ResponseEntity<ErrorMessageResponse> {
        val fieldErrors = ex.bindingResult.fieldErrors
        val reason = if (fieldErrors.isNotEmpty()) {
            fieldErrors.joinToString(", ") { it.field }
        } else {
            "VALIDATION_ERROR"
        }
        val description = if (fieldErrors.isNotEmpty()) {
            fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage ?: "입력값 검증 실패"}" }
        } else {
            ex.bindingResult.globalErrors.firstOrNull()?.defaultMessage ?: "입력값 검증 실패"
        }

        return handleKdhException(
            ValidationException(
                reason = reason,
                description = description,
                cause = ex
            )
        )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadableException(ex: HttpMessageNotReadableException): ResponseEntity<ErrorMessageResponse> {
        val detail = ex.mostSpecificCause.message
        val description = detail ?: ex.message

        return handleKdhException(
            RequestBodyReadableException(
                reason = "HTTP_MESSAGE_NOT_READABLE",
                description = description,
                cause = ex
            )
        )
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleMethodArgumentTypeMismatchException(
        ex: MethodArgumentTypeMismatchException
    ): ResponseEntity<ErrorMessageResponse> {
        val description = when (ex.name) {
            "date" -> "invalid date format. Use yyyy-MM-dd."
            else -> "invalid request parameter: ${ex.name}"
        }

        return handleKdhException(
            RequestParameterMismatchException(
                reason = ex.name,
                description = description,
                cause = ex
            )
        )
    }

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFoundException(ex: NoResourceFoundException): ResponseEntity<ErrorMessageResponse> {
        return handleKdhException(
            ResourceNotFoundException(
                reason = "RESOURCE_NOT_FOUND",
                description = "method=${ex.httpMethod}, path=${ex.resourcePath}",
                cause = ex
            )
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleException(ex: Exception): ResponseEntity<ErrorMessageResponse> {
        log.error("Unhandled exception occurred", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorMessageResponse(ex.message ?: "internal server error"))
    }
}
