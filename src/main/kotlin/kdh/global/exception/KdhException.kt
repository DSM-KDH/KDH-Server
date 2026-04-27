package kdh.global.exception

class KdhException(
    val errorCode: ErrorCode
): RuntimeException()