package kdh.domain.routine.exception

import kdh.global.exception.ErrorCode
import kdh.global.exception.KdhException

class InvalidDietConditionException(
    message: String = ErrorCode.INVALID_DIET_CONDITION.message
) : KdhException(ErrorCode.INVALID_DIET_CONDITION, message)
