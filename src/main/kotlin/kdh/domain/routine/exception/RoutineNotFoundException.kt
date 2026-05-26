package kdh.domain.routine.exception

import kdh.global.exception.ErrorCode
import kdh.global.exception.KdhException

class RoutineNotFoundException(
    message: String = ErrorCode.ROUTINE_NOT_FOUND.message
) : KdhException(ErrorCode.ROUTINE_NOT_FOUND, message)
