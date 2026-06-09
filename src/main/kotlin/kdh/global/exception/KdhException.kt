package kdh.global.exception

open class KdhException(
    val errorCode: ErrorCode,
    override val message: String = errorCode.message,
    override val cause: Throwable? = null,
    val reason: String? = null,
    val description: String? = null
): RuntimeException(message, cause)

class ValidationException(
    reason: String? = null,
    description: String? = null,
    cause: Throwable? = null
) : KdhException(
    errorCode = ErrorCode.VALIDATION_FAILED,
    reason = reason,
    description = description,
    cause = cause
)

class InvalidRequestException(
    reason: String? = null,
    description: String? = null,
    cause: Throwable? = null
) : KdhException(
    errorCode = ErrorCode.BAD_REQUEST,
    reason = reason,
    description = description,
    cause = cause
)

class RequestBodyReadableException(
    reason: String? = null,
    description: String? = null,
    cause: Throwable? = null
) : KdhException(
    errorCode = ErrorCode.INVALID_REQUEST_BODY,
    reason = reason,
    description = description,
    cause = cause
)

class RequestParameterMismatchException(
    reason: String? = null,
    description: String? = null,
    cause: Throwable? = null
) : KdhException(
    errorCode = ErrorCode.INVALID_REQUEST_PARAMETER,
    reason = reason,
    description = description,
    cause = cause
)

class ResourceNotFoundException(
    reason: String? = null,
    description: String? = null,
    cause: Throwable? = null
) : KdhException(
    errorCode = ErrorCode.NOT_FOUND,
    reason = reason,
    description = description,
    cause = cause
)
