package kdh.domain.routine.exception

import kdh.global.exception.ErrorCode
import kdh.global.exception.KdhException

class RegenerationLimitExceededException(
    message: String = ErrorCode.REGENERATION_LIMIT_EXCEEDED.message
) : KdhException(ErrorCode.REGENERATION_LIMIT_EXCEEDED, message)
