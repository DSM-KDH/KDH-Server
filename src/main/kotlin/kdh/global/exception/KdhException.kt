package kdh.global.exception

open class KdhException(
    val errorCode: ErrorCode,
    override val message: String = errorCode.message,
    override val cause: Throwable? = null
): RuntimeException(message, cause)
