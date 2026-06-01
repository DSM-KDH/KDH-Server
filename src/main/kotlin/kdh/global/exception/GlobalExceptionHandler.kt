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
        val errorCode = ErrorCode.BAD_REQUEST
        log.warn("Bad request: errorCode={}, message={}", errorCode, ex.message)
        return ResponseEntity.status(errorCode.status)
            .body(
                ErrorMessageResponse(
                    message = errorCode.message,
                    reason = "ILLEGAL_ARGUMENT",
                    description = ex.message
                )
            )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValidException(
        ex: MethodArgumentNotValidException
    ): ResponseEntity<ErrorMessageResponse> {
        val errorCode = ErrorCode.VALIDATION_FAILED
        val fieldError = ex.bindingResult.fieldErrors.firstOrNull()
        val reason = fieldError?.field ?: "VALIDATION_ERROR"
        val description = fieldError?.defaultMessage 
            ?: ex.bindingResult.globalErrors.firstOrNull()?.defaultMessage
            ?: "입력값 검증 실패"

        log.warn("Validation failed: errorCode={}, field={}, message={}", errorCode, reason, description)
        return ResponseEntity.status(errorCode.status)
            .body(
                ErrorMessageResponse(
                    message = errorCode.message,
                    reason = reason,
                    description = description
                )
            )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadableException(ex: HttpMessageNotReadableException): ResponseEntity<ErrorMessageResponse> {
        val errorCode = ErrorCode.INVALID_REQUEST_BODY
        val detail = ex.mostSpecificCause.message
        val description = detail ?: ex.message

        log.warn("Invalid request body: errorCode={}, message={}", errorCode, description)
        return ResponseEntity.status(errorCode.status)
            .body(
                ErrorMessageResponse(
                    message = errorCode.message,
                    reason = "HTTP_MESSAGE_NOT_READABLE",
                    description = description
                )
            )
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleMethodArgumentTypeMismatchException(
        ex: MethodArgumentTypeMismatchException
    ): ResponseEntity<ErrorMessageResponse> {
        val errorCode = ErrorCode.INVALID_REQUEST_PARAMETER
        val description = when (ex.name) {
            "date" -> "invalid date format. Use yyyy-MM-dd."
            else -> "invalid request parameter: ${ex.name}"
        }

        log.warn("Invalid request parameter: errorCode={}, name={}, value={}", errorCode, ex.name, ex.value)
        return ResponseEntity.status(errorCode.status)
            .body(
                ErrorMessageResponse(
                    message = errorCode.message,
                    reason = ex.name,
                    description = description
                )
            )
    }

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFoundException(ex: NoResourceFoundException): ResponseEntity<ErrorMessageResponse> {
        val errorCode = ErrorCode.NOT_FOUND
        log.warn("No resource found: errorCode={}, method={}, path={}", errorCode, ex.httpMethod, ex.resourcePath)
        return ResponseEntity.status(errorCode.status)
            .body(
                ErrorMessageResponse(
                    message = errorCode.message,
                    reason = "RESOURCE_NOT_FOUND",
                    description = "method=${ex.httpMethod}, path=${ex.resourcePath}"
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
