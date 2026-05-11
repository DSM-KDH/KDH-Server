package kdh.global.exception

import kdh.global.dto.ErrorMessageResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(KdhException::class)
    fun handleKdhException(ex: KdhException): ResponseEntity<ErrorMessageResponse> {
        val message = ex.errorCode.message
        return ResponseEntity.status(ex.errorCode.status).body(ErrorMessageResponse(message))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(ex: IllegalArgumentException): ResponseEntity<ErrorMessageResponse> {
        log.warn("Bad request: {}", ex.message, ex)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorMessageResponse(ex.message ?: "bad request"))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValidException(
        ex: MethodArgumentNotValidException
    ): ResponseEntity<ErrorMessageResponse> {
        val message = ex.bindingResult.fieldErrors.firstOrNull()?.defaultMessage
            ?: ex.bindingResult.globalErrors.firstOrNull()?.defaultMessage
            ?: "validation failed"

        log.warn("Validation failed: {}", message, ex)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorMessageResponse(message))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadableException(ex: HttpMessageNotReadableException): ResponseEntity<ErrorMessageResponse> {
        val detail = ex.mostSpecificCause.message
        val message = if (detail.isNullOrBlank()) {
            "invalid request body"
        } else {
            "invalid request body: $detail"
        }

        log.warn("Invalid request body: {}", detail ?: ex.message, ex)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorMessageResponse(message))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(ex: Exception): ResponseEntity<ErrorMessageResponse> {
        log.error("Unhandled exception occurred", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorMessageResponse(ex.message ?: "internal server error"))
    }
}
