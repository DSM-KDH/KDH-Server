package kdh.global.exception

import kdh.global.dto.ErrorMessageResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(KdhException::class)
    fun handleKdhException(ex: KdhException): ResponseEntity<ErrorMessageResponse> {
        val message = ex.errorCode.message
        return ResponseEntity.status(ex.errorCode.status).body(ErrorMessageResponse(message))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(ex: IllegalArgumentException): ResponseEntity<ErrorMessageResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorMessageResponse(ex.message ?: "bad request"))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(ex: Exception): ResponseEntity<ErrorMessageResponse> {
        val logger = LoggerFactory.getLogger(Exception::class.java)
        logger.error(ex.message)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorMessageResponse(ex.message ?: "internal server error"))
    }
}
