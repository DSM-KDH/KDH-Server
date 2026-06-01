package kdh.domain.routine.exception

import kdh.global.exception.ErrorCode
import kdh.global.exception.KdhException

class InvalidDietConditionException(
    reason: String? = null,
    description: String? = null,
    message: String = ErrorCode.INVALID_DIET_CONDITION.message
) : KdhException(
    errorCode = ErrorCode.INVALID_DIET_CONDITION,
    message = message,
    reason = reason,
    description = description
)
